package juby.alert;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

public class ErrorNotifierHandler implements RequestHandler<Map<String, Object>, String> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3))
            .build();

    private static final DateTimeFormatter ISO = DateTimeFormatter
            .ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
            .withZone(ZoneOffset.UTC);

    private static final String REGION = env("AWS_REGION", "ap-northeast-2");
    private static final String LOG_GROUP = env("LOG_GROUP", "/juby/app");
    private static final String WEBHOOK_PARAM = env("SLACK_WEBHOOK_SSM_PARAM", "/juby/slack/error-webhook");
    private static final int MAX_GROUPS = Integer.parseInt(env("MAX_GROUPS", "5"));
    private static final int SLACK_TIMEOUT_SEC = Integer.parseInt(env("SLACK_TIMEOUT", "5"));
    private static final int DEEPLINK_WINDOW_SEC = Integer.parseInt(env("DEEPLINK_WINDOW_SEC", "300"));

    /** 슬랙 section 블록의 text 한도는 3000자다. 넉넉히 줄여 담는다. */
    private static final int MAX_MESSAGE_CHARS = 700;

    private static String cachedWebhook;

    /***
     * 함수 기능: CloudWatch Logs의 ERROR 로그를 슬랙으로 통보하는 Lambda 핸들러
     * 동작 흐름: decode -> messageType 검사 -> group -> buildPayload -> post (슬랙 전송)
     * @param event  트리거가 보낸 데이터 (로그 정보)
     * @param context Lamda 런타임이 주는 실행 정보
     * @return CloudWatch Logs는 비동기 호출이라 return은 사라진다. 단지 테스트용.
     */
    @Override
    public String handleRequest(Map<String, Object> event, Context context) {
        try {
            // 압축 풀기 (Map -> JsonNode)
            JsonNode payload = decode(event);

            // messageType 검사와 빈 배열 검사
            // meesageType이 CONTROL_MESSAGE(헬스 체크용)이거나 빈배열이면 skip한다.
            String messageType = payload.path("messageType").asText("");
            if (!"DATA_MESSAGE".equals(messageType)) {
                System.out.printf("[INFO] DATA_MESSAGE가 아니므로 건너뜀: %s%n", messageType);
                return "skipped:" + messageType;
            }

            JsonNode logEvents = payload.path("logEvents");
            if (!logEvents.isArray() || logEvents.isEmpty()) {
                System.out.println("[INFO] logEvents가 비어 있음");
                return "skipped:empty";
            }

            // 구독 필터는 로그를 배치로 묶어 보내는데, 장애 하나가 100번 터지면 logEvents에 100건이 들어온다.
            // 이를 그대로 슬랙에 뿌리면 채널이 마비되기에, 대표 샘플 하나로 묶어 보여준다.
            Map<String, Group> groups = group(logEvents);
            System.out.printf("[INFO] ERROR %d건 / %d종류 수신%n", logEvents.size(), groups.size());

            // buildPayload: 슬랙에 보낼 메시지를 조립한다.
            // post: Slack으로 알림을 보낸다.
            post(buildPayload(groups, logEvents.size(), payload.path("logStream").asText("-")));
            return "sent:" + logEvents.size() + "/" + groups.size();

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("알림 처리 중단됨", e);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            // 여기까지 올라온 예외는 Lambda가 실패로 기록하고 재시도한다.
            throw new IllegalStateException("알림 처리 실패: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------ 이벤트 디코딩

    /** subscription filter가 보낸 gzip + base64 {@code awslogs.data}를 푼다. */
    private static JsonNode decode(Map<String, Object> event) throws IOException {
        if (!(event.get("awslogs") instanceof Map<?, ?> wrapper)) {
            throw new IllegalArgumentException("CloudWatch Logs 이벤트가 아니다 (awslogs 키 없음)");
        }
        byte[] gzipped = Base64.getDecoder().decode(String.valueOf(wrapper.get("data")));
        try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(gzipped))) {
            return MAPPER.readTree(in.readAllBytes());
        }
    }

    /** 로그 1줄을 JsonNode로 만든다. JSON이 아니면 원문만 담아 돌려준다. */
    private static JsonNode parseMessage(String message) {
        try {
            JsonNode parsed = MAPPER.readTree(message);
            if (parsed != null && parsed.isObject()) {
                return parsed;
            }
        } catch (IOException ignored) {
            // JVM이 로깅 설정 밖에서 토한 출력(OOM 등)은 JSON이 아니다.
        }
        return MAPPER.createObjectNode().put("message", message);
    }

    /**
     * 배치 내 이벤트를 {@code error.type}(없으면 로거)으로 접는다.
     *
     * <p>subscription filter는 이벤트를 배치로 전달하므로 1회 호출에 N건이 들어온다.
     * 같은 장애가 100번 터졌을 때 슬랙에 100줄을 쏟지 않기 위한 폭주 방지 장치다.
     */
    private static Map<String, Group> group(JsonNode logEvents) {
        Map<String, Group> groups = new LinkedHashMap<>();
        for (JsonNode raw : logEvents) {
            JsonNode fields = parseMessage(raw.path("message").asText(""));
            String key = firstNonBlank(
                    fields.path("error.type").asText(""),
                    fields.path("log.logger").asText(""),
                    "UNKNOWN");

            Group existing = groups.get(key);
            if (existing == null) {
                // 수집 시각(timestamp)은 로그의 @timestamp와 달리 항상 존재한다.
                groups.put(key, new Group(key, fields, raw.path("timestamp").asLong(0L)));
            } else {
                existing.count++;
            }
        }
        return groups;
    }

    private static final class Group {
        private final String key;
        private final JsonNode fields;
        private final long timestamp;
        private int count = 1;

        private Group(String key, JsonNode fields, long timestamp) {
            this.key = key;
            this.fields = fields;
            this.timestamp = timestamp;
        }
    }

    // ------------------------------------------------------------------ 슬랙 메시지

    private static ObjectNode buildPayload(Map<String, Group> groups, int totalEvents, String logStream) {
        List<Group> shown = groups.values().stream().limit(MAX_GROUPS).toList();
        int hidden = groups.size() - shown.size();

        String environment = shown.get(0).fields.path("service.environment").asText("unknown");
        String summary = "[%s] ERROR %d건 / %d종류".formatted(environment, totalEvents, groups.size());

        ObjectNode root = MAPPER.createObjectNode();
        root.put("text", summary);
        ArrayNode blocks = root.putArray("blocks");

        ObjectNode header = blocks.addObject();
        header.put("type", "header");
        header.putObject("text")
                .put("type", "plain_text")
                .put("text", "🚨 " + summary)
                .put("emoji", true);

        for (Group group : shown) {
            blocks.addObject().put("type", "divider");
            ObjectNode section = blocks.addObject();
            section.put("type", "section");
            section.putObject("text").put("type", "mrkdwn").put("text", groupText(group));
        }

        StringBuilder footer = new StringBuilder(LOG_GROUP).append(" · ").append(logStream);
        if (hidden > 0) {
            footer.append(" · 외 ").append(hidden).append("종류 생략");
        }
        // 딥링크가 깨졌을 때 손으로 복사할 수 있도록 질의 원문을 같이 남긴다.
        String firstTrace = shown.get(0).fields.path("traceId").asText("");
        if (!firstTrace.isBlank()) {
            footer.append(" · 질의: filter traceId = \"").append(firstTrace).append('"');
        }

        ObjectNode contextBlock = blocks.addObject();
        contextBlock.put("type", "context");
        contextBlock.putArray("elements")
                .addObject()
                .put("type", "mrkdwn")
                .put("text", escape(footer.toString()));

        return root;
    }

    private static String groupText(Group group) {
        JsonNode fields = group.fields;
        String traceId = fields.path("traceId").asText("");

        StringBuilder text = new StringBuilder("*").append(escape(group.key)).append('*');
        if (group.count > 1) {
            text.append("  `+").append(group.count - 1).append("건`");
        }
        text.append("\n```").append(escape(fields.path("message").asText(""))).append("```\n");

        text.append("status: `").append(escape(valueOrDash(fields, "status"))).append('`')
                .append(" | method: `").append(escape(valueOrDash(fields, "httpMethod"))).append('`')
                .append(" | uri: `").append(escape(valueOrDash(fields, "uri"))).append('`')
                .append(" | userId: `").append(escape(valueOrDash(fields, "userId"))).append("`\n");

        if (traceId.isBlank()) {
            text.append("traceId 없음 (스케줄러 또는 요청 외 컨텍스트)");
        } else {
            text.append("traceId: `").append(escape(traceId)).append("` — <")
                    .append(insightsUrl(traceId, group.timestamp))
                    .append("|Logs Insights에서 추적>");
        }
        return text.toString();
    }

    // ------------------------------------------------------------------ 딥링크

    /** 해당 traceId의 로그를 Logs Insights에서 바로 여는 딥링크. */
    private static String insightsUrl(String traceId, long timestampMs) {
        Instant center = Instant.ofEpochMilli(timestampMs);

        // 필드는 MDC에서 올라온 것(점 없음)만 쓴다. log.level처럼 점이 박힌 ECS 고정
        // 필드는 Insights에서도 해석이 헷갈리므로 원문 @message로 대체한다.
        String query = """
                fields @timestamp, status, httpMethod, uri, @message
                | filter traceId = "%s"
                | sort @timestamp asc
                | limit 200""".formatted(traceId);

        String detail = "~(end~'" + consoleEncode(ISO.format(center.plusSeconds(DEEPLINK_WINDOW_SEC)))
                + "~start~'" + consoleEncode(ISO.format(center.minusSeconds(DEEPLINK_WINDOW_SEC)))
                + "~timeType~'ABSOLUTE~tz~'UTC"
                + "~editorString~'" + consoleEncode(query)
                + "~isLiveTail~false~queryId~''"
                + "~source~(~'" + consoleEncode(LOG_GROUP) + "))";

        return ("https://%s.console.aws.amazon.com/cloudwatch/home?region=%s"
                + "#logsV2:logs-insights$3FqueryDetail$3D%s").formatted(REGION, REGION, detail);
    }

    /**
     * CloudWatch 콘솔 해시 URL용 인코딩.
     *
     * <p>퍼센트 인코딩과 같은 규칙이지만 구분자가 {@code %}가 아니라 {@code *}다.
     * (공백 → {@code *20}, {@code "} → {@code *22}, {@code /} → {@code *2f}, 개행 → {@code *0a})
     */
    private static String consoleEncode(String value) {
        StringBuilder out = new StringBuilder();
        for (byte raw : value.getBytes(StandardCharsets.UTF_8)) {
            int b = raw & 0xFF;
            boolean safe = (b >= 'a' && b <= 'z') || (b >= 'A' && b <= 'Z') || (b >= '0' && b <= '9')
                    || b == '-' || b == '.' || b == '_';
            if (safe) {
                out.append((char) b);
            } else {
                out.append('*').append(String.format("%02x", b));
            }
        }
        return out.toString();
    }

    // ------------------------------------------------------------------ 전송

    /** 웹훅 호출. 실패하면 반드시 ERROR로 남기고, 재시도가 의미 있을 때만 예외를 올린다. */
    private static void post(ObjectNode payload) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(webhookUrl()))
                .timeout(Duration.ofSeconds(SLACK_TIMEOUT_SEC))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        MAPPER.writeValueAsString(payload), StandardCharsets.UTF_8))
                .build();

        HttpResponse<String> response;
        try {
            response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            // 타임아웃·네트워크 실패는 일시적이므로 Lambda 재시도에 맡긴다.
            System.err.println("[ERROR] 슬랙 전송 실패(네트워크/타임아웃): " + e.getMessage());
            throw e;
        }

        int status = response.statusCode();
        String body = response.body() == null ? "" : response.body().strip();
        if (status == 200 && "ok".equals(body)) {
            System.out.println("[INFO] 슬랙 전송 완료");
            return;
        }

        System.err.printf("[ERROR] 슬랙 전송 실패: status=%d body=%s%n", status, truncate(body));

        // 429/5xx만 일시적 실패다. 나머지 4xx(웹훅 폐기 등)는 재시도해도 같은 결과이고
        // Lambda 비동기 재시도가 중복 알림만 만든다.
        if (status == 429 || status >= 500) {
            throw new IOException("슬랙 전송 실패: status=" + status);
        }
    }

    /**
     * 웹훅 URL을 구해 콜드 스타트 1회만 캐시한다.
     *
     * <p>값(URL)은 비밀이므로 어떤 경로로도 로깅하지 않는다 — 파라미터 이름만 남긴다.
     */
    private static synchronized String webhookUrl() throws IOException, InterruptedException {
        if (cachedWebhook != null) {
            return cachedWebhook;
        }

        // 콘솔에서 트리거를 처음 연결할 때 IAM·KMS 설정 없이 바로 확인하기 위한 경로.
        // 영구 설정으로 둘 것이 아니라서 WARN을 남긴다.
        String direct = env("SLACK_WEBHOOK_URL", "").strip();
        if (!direct.isBlank()) {
            System.out.printf("[WARN] 웹훅 URL을 환경 변수에서 읽었다. SSM SecureString(%s)으로 옮길 것%n",
                    WEBHOOK_PARAM);
            cachedWebhook = direct;
            return cachedWebhook;
        }

        // Parameters and Secrets Lambda Extension. localhost HTTP로 SecureString을 읽으므로
        // AWS SDK를 번들하지 않아도 된다 (확장 레이어 추가 + ssm:GetParameter/kms:Decrypt 필요).
        String endpoint = "http://localhost:2773/systemsmanager/parameters/get?withDecryption=true&name="
                + URLEncoder.encode(WEBHOOK_PARAM, StandardCharsets.UTF_8);

        HttpResponse<String> response = HTTP.send(
                HttpRequest.newBuilder(URI.create(endpoint))
                        .timeout(Duration.ofSeconds(3))
                        .header("X-Aws-Parameters-Secrets-Token", env("AWS_SESSION_TOKEN", ""))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("SSM 파라미터 조회 실패: status=" + response.statusCode());
        }

        String value = MAPPER.readTree(response.body()).path("Parameter").path("Value").asText("").strip();
        if (value.isBlank()) {
            throw new IOException("SSM 파라미터 값이 비어 있다: " + WEBHOOK_PARAM);
        }

        cachedWebhook = value;
        System.out.printf("[INFO] 웹훅 URL 로드 완료 (param=%s)%n", WEBHOOK_PARAM);
        return cachedWebhook;
    }

    // ------------------------------------------------------------------ 유틸

    private static String valueOrDash(JsonNode fields, String key) {
        String value = fields.path(key).asText("");
        return value.isBlank() ? "-" : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "-";
    }

    /** 슬랙 mrkdwn에서 특수 취급되는 문자를 막는다. */
    private static String escape(String value) {
        return truncate(value).replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String truncate(String value) {
        if (value == null) {
            return "";
        }
        return value.length() <= MAX_MESSAGE_CHARS
                ? value
                : value.substring(0, MAX_MESSAGE_CHARS - 1) + "…";
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return (value == null || value.isBlank()) ? fallback : value;
    }
}
