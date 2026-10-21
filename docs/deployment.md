# EC2 단일 인스턴스 배포 (MySQL 내장)

RDS 대신 EC2 내부에 MySQL 컨테이너를 두고, 애플리케이션 컨테이너와 같은
Docker 네트워크로 연결하는 구성이다. 대상은 `t3.micro` (2 vCPU / **1 GiB RAM**).

```
┌─ EC2 t3.micro (1GiB + swap 2GB) ──────────────┐
│                                                │
│  ┌──────────────┐  juby-net  ┌──────────────┐  │
│  │ juby (8080)  │◀──────────▶│ mysql (3306) │  │
│  │ --restart    │            │ volume:      │  │
│  │ CI가 교체     │            │ mysql-data   │  │
│  └──────────────┘            └──────────────┘  │
│         ▲                                      │
│    8080 │ (호스트 내부에서만 접근)                  │
│  ┌──────┴───────┐                               │
│  │ nginx (443)  │ TLS 종료 → 8080으로 평문 전달     │
│  └──────────────┘                               │
└─────────┼──────────────────────────────────────┘
       443│ (보안 그룹: 443, 80, 22만 개방. 8080·3306은 열지 않음)
```

## 전제: 고정 IP

**가장 먼저 Elastic IP를 할당해 인스턴스에 붙일 것.** 붙이지 않으면
stop/start마다 퍼블릭 IP가 바뀌고, 그때마다 네이버·구글·카카오 개발자 콘솔의
콜백 URL을 전부 다시 등록해야 한다.

---

## 1. 스왑 2GB 활성화

1 GiB로는 JVM과 MySQL이 동시에 뜨지 않는다. 스왑은 **OOM 방지용 안전망**이고,
상시 사용하는 메모리로 여겨선 안 된다.

```bash
sudo fallocate -l 2G /swapfile
sudo chmod 600 /swapfile
sudo mkswap /swapfile
sudo swapon /swapfile
echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab

# 기본값 60이면 JVM 힙이 스왑으로 밀려나 응답이 초 단위로 튄다.
echo 'vm.swappiness=10' | sudo tee -a /etc/sysctl.conf
sudo sysctl -p

free -h   # Swap 2.0Gi 확인
```

## 2. Docker 설치

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl
sudo install -m 0755 -d /etc/apt/keyrings
sudo curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
sudo chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] \
  https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list > /dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-compose-plugin
```

> EC2에서 Gradle 빌드를 하지 말 것. 1 GiB에서는 빌드만으로 OOM이 난다.
> 빌드는 GitHub Actions가 하고 EC2는 이미지를 pull 하기만 한다.

## 3. MySQL 컨테이너 기동

명령을 실행하는 위치가 두 곳이다. 섞으면 "파일이 없다"는 에러가 난다.

- **[로컬]** 내 PC, 레포 루트
- **[EC2]** ssh로 접속한 상태

### 3-1. [EC2] 디렉터리 생성

```bash
mkdir -p ~/db     # MySQL 운영 파일(compose·설정·백업·.env)이 사는 곳
```

### 3-2. [로컬] 파일 전송

```bash
scp -i <key.pem> deploy/docker-compose.yml deploy/low-mem.cnf deploy/backup-mysql.sh \
    <user>@<ELASTIC_IP>:~/db/
```

`.env.example`은 전송하지 않는다. 점으로 시작하는 파일은 셸 글로브(`deploy/*`)에
매칭되지 않아 조용히 누락되고, 어차피 실제 비밀번호를 담아야 하므로 EC2에서
직접 만드는 편이 낫다.

접속이 안 되면 scp 대신 ssh로 먼저 키를 검증할 것. `Identity file ... not
accessible` 다음 줄의 `Permission denied (publickey)`는 별개 원인이 아니라
키를 못 찾아 생긴 결과다. 키 경로에 공백이 있으면 따옴표로 감싼다.

### 3-3. [EC2] `.env` 작성

compose는 **자기와 같은 디렉터리의 `.env`를 자동으로 읽어**
`docker-compose.yml`의 `${MYSQL_*}` 자리를 채운다. 항목 목록은
`deploy/.env.example` 참고.

```bash
cd ~/db
cat > .env <<'EOF'
MYSQL_ROOT_PASSWORD=루트비밀번호
MYSQL_DATABASE=juby
MYSQL_USER=juby
MYSQL_PASSWORD=앱비밀번호
EOF
chmod 600 .env
cat .env      # 4줄이 제대로 들어갔는지 확인
```

비밀번호에 `$`를 넣지 말 것. compose가 변수 참조로 해석한다.

### 3-4. [EC2] 설정 파일 권한

```bash
chmod 644 low-mem.cnf
```

MySQL은 world-writable 설정 파일을 **조용히 무시한다.** 기동은 정상이지만
튜닝만 적용되지 않아 원인을 찾기 어렵다.

### 3-5. [EC2] 기동

```bash
cd ~/db      # compose는 현재 디렉터리의 docker-compose.yml을 읽는다
sudo docker compose up -d
```

이 한 줄이 하는 일:

1. `mysql:8.0` 이미지 pull (최초 1회)
2. **`juby-net` 네트워크 생성** — CI가 앱 컨테이너를 여기에 붙인다
3. **`juby_mysql-data` 볼륨 생성** — 실제 데이터가 사는 곳
4. 최초 기동이면 DB를 초기화하고 `.env` 값으로 DB와 계정을 만든다 (30~60초)

### 3-6. [EC2] 검증

```bash
sudo docker compose ps                        # Up (healthy)가 될 때까지 대기
sudo docker compose logs mysql | tail -20     # "ready for connections" 확인
sudo docker network ls | grep juby-net
sudo docker volume ls  | grep mysql-data
sudo docker compose exec mysql mysql -u juby -p -e "SHOW DATABASES;"
```

마지막 명령에서 `juby` 데이터베이스가 보이면 3단계 완료다.

> **함정**: `.env`의 `MYSQL_*` 값은 볼륨이 비어 있는 **최초 초기화 때만** 적용된다.
> 비밀번호를 틀린 채 `up -d` 한 뒤 `.env`를 고치고 다시 올려도 바뀌지 않는다.
> 실제 데이터가 없는 이 단계에서만 `sudo docker compose down -v`로 볼륨을 지우고
> 다시 하면 된다. (`-v`는 데이터 삭제다. 운영 중에는 절대 금지)

## 4. GitHub Secrets 갱신

`ENV_FILE` 시크릿에서 아래 항목을 수정한다.

```dotenv
# 호스트명 mysql은 juby-net 내부 DNS로 해석된다. RDS 엔드포인트가 아니다.
DB_URL=jdbc:mysql://mysql:3306/juby?serverTimezone=Asia/Seoul&characterEncoding=UTF-8

# 백엔드 공개 주소. nginx가 TLS를 종료하므로 https + 도메인이다. 포트는 붙이지 않는다.
# application-dev.yaml의 세 소셜 redirect-uri가 이 값을 쓴다.
BACKEND_URL=https://<도메인>

# 프론트 주소. 프론트 배포 전까지는 개발자 로컬 dev 서버를 그대로 쓴다.
FRONTEND_URL=http://localhost:5173
```

`FRONTEND_URL`은 `application-dev.yaml`에서 **두 곳**에 쓰인다. 하나만 보고 판단하지 말 것.

| 쓰이는 곳 | 요구 형태 |
|---|---|
| `app.oauth2.redirect-uri` / `failure-redirect-uri` | 뒤에 `/oauth2/callback`이 붙는다 |
| `app.cors.allowed-origins` | **origin 그대로** (`scheme://host[:port]`, path 없음) |

> **함정**: `BACKEND_URL` / `FRONTEND_URL`은 **기본값이 없다.** `ENV_FILE`에서 빠지면
> 플레이스홀더를 못 채워 앱이 기동 실패한다. (값이 틀린 채 뜨는 것보다 낫기 때문에 의도한 동작이다)

> **함정**: CORS origin은 문자 단위 완전 일치다. `FRONTEND_URL`이 `http://localhost:5173`인 동안
> 개발자가 `http://127.0.0.1:5173`으로 접속하면 **다른 origin**이라 API 호출이 전부 차단된다.
> Vite는 5173이 점유되어 있으면 말없이 5174로 올라가므로 이때도 같이 깨진다.

`DB_USERNAME` / `DB_PASSWORD`는 `deploy/.env`의 `MYSQL_USER` / `MYSQL_PASSWORD`와
반드시 일치해야 한다. `EC2_HOST` 시크릿도 새 IP로 갱신한다.

## 5. 소셜 로그인 콜백 등록

`BACKEND_URL`을 바꿨으면 세 콘솔 모두에 콜백 URL을 등록해야 로그인이 된다.
등록값과 서버가 보내는 값이 한 글자라도 다르면 `redirect_uri_mismatch`가 난다.
스킴(`http` → `https`)이나 포트 유무도 "한 글자 다름"에 해당한다.

| 제공자 | 등록할 Callback URL |
|---|---|
| 네이버 | `https://<도메인>/login/oauth2/code/naver` |
| 구글 | `https://<도메인>/login/oauth2/code/google` |
| 카카오 | `https://<도메인>/login/oauth2/code/kakao` |

## 6. 백업 cron (필수)

RDS의 자동 백업·PITR을 포기했으므로 이게 유일한 복구 수단이다. 나중으로
미루지 말 것.

### 6-1. 호스트 시간대를 KST로

**EC2 기본 시간대는 UTC다.** cron은 호스트 시간대로 돌기 때문에 이 상태에서
`0 4 * * *`는 04:00 KST가 아니라 **13:00 KST**에 실행된다. 앱 컨테이너
(`TZ=Asia/Seoul`)·MySQL과 시간 해석을 통일해 두는 편이 헷갈리지 않는다.

```bash
sudo timedatectl set-timezone Asia/Seoul
sudo systemctl restart cron     # 변경된 TZ를 cron에 반영
timedatectl | head -2
```

### 6-2. 수동 실행으로 검증

```bash
chmod +x ~/db/backup-mysql.sh
~/db/backup-mysql.sh
```

`OK: /home/ubuntu/db/backups/juby-....sql.gz` 가 출력되면 정상이다. 앱을 아직
배포하지 않았다면 테이블이 0개인 덤프가 나오는데, 스키마가 없는 것뿐이라
문제가 아니다 (`ddl-auto: update`가 앱 최초 기동 때 테이블을 만든다).

스크립트는 `docker` 소켓에 접근할 수 있으면 그대로, 아니면 `sudo`를 붙여
실행한다. EC2 기본 사용자 `ubuntu`는 docker 그룹에 속하지 않으므로 후자가 된다.
cron에는 TTY가 없으니 sudo가 NOPASSWD여야 한다 (Ubuntu AMI 기본값).

### 6-3. cron 등록

`crontab -e`로 직접 편집해도 되지만, 아래처럼 하면 재실행해도 중복 등록되지
않는다.

```bash
( crontab -l 2>/dev/null | grep -v 'backup-mysql.sh' || true
  echo '# JUBY MySQL 일일 백업 - 매일 04:00 KST (호스트 TZ = Asia/Seoul)'
  echo '0 4 * * * /home/ubuntu/db/backup-mysql.sh >> /home/ubuntu/db/backup.log 2>&1'
) | crontab -

crontab -l      # 등록 확인
```

### 6-4. cron 환경에서 동작하는지 확인

cron은 PATH가 `/usr/bin:/bin`뿐이고 TTY도 없어서, 손으로 실행할 때는 되던
스크립트가 cron에서만 실패하는 일이 흔하다. 하루 기다리지 말고 지금 확인한다.

```bash
env -i PATH=/usr/bin:/bin HOME=/home/ubuntu SHELL=/bin/sh \
  /bin/sh -c '/home/ubuntu/db/backup-mysql.sh >> /home/ubuntu/db/backup.log 2>&1'
cat ~/db/backup.log
```

복구:

```bash
gunzip < ~/db/backups/juby-2026-09-18-0400.sql.gz \
  | sudo docker exec -i -e MYSQL_PWD=<root_pw> mysql mysql -u root juby
```

여유가 되면 스크립트 하단의 S3 업로드 주석을 해제하고, 추가로 EBS 스냅샷도
주기적으로 잡아 둔다. 인스턴스가 죽으면 인스턴스 안의 백업도 같이 죽는다.

---

## 메모리 예산

| 구성 | 기본 설정 | 튜닝 후 |
|---|---|---|
| OS + sshd + dockerd | ~200 MB | ~200 MB |
| MySQL 8.0 | ~450 MB | ~280 MB (`deploy/low-mem.cnf`) |
| JVM (Boot + Security + JPA + Spring AI + Pinecone) | ~700 MB | ~450 MB (`JAVA_TOOL_OPTIONS`) |
| **합계** | **~1350 MB** ✗ | **~930 MB** |

튜닝 값의 위치:

- MySQL → `deploy/low-mem.cnf` (`performance_schema=OFF`만으로 ~150 MB 절약)
- JVM → `.github/workflows/dev-deploy.yml`의 `JAVA_TOOL_OPTIONS`
  (1 vCPU에서는 G1보다 `UseSerialGC`가 오버헤드가 작다)
- 커넥션 풀 → `application-dev.yaml`의 `spring.datasource.hikari.maximum-pool-size: 5`

여유가 930/1024로 빡빡하다. 메모리 부족 징후가 보이면 확인할 것:

```bash
free -h
sudo docker stats --no-stream
dmesg -T | grep -i 'killed process'   # OOM killer 흔적
```

배포가 조용히 실패하면 대개 OOM이다. 그때는 t3.small(2 GiB)로 올리는 것이
튜닝을 더 짜내는 것보다 낫다.

## 보안 그룹

| 포트 | 소스 | 비고 |
|---|---|---|
| 22 | 내 IP만 | `0.0.0.0/0` 금지 |
| 443 | `0.0.0.0/0` | nginx가 TLS를 종료하는 유일한 공개 입구 |
| 80 | `0.0.0.0/0` | HTTPS 리다이렉트 + 인증서 갱신(ACME)용 |
| 8080 | **열지 않음** | 아래 참고 |
| 3306 | **열지 않음** | 아래 참고 |

**8080을 공개하지 않는다.** 외부 트래픽은 반드시 nginx(443)를 거쳐야 한다. 두 가지 이유다.

1. nginx가 붙이는 `X-Real-IP` / `X-Request-ID` / `X-Forwarded-*` 헤더를 신뢰할 수 있는 근거가
   "우회 경로가 없다"는 사실뿐이다. 8080이 열려 있으면 클라이언트가 직접 8080으로 치면서 이 헤더를
   **위조**할 수 있고, 그러면 `MdcLoggingFilter`가 기록하는 `clientIp`와 `traceId`가 거짓이 된다
   (`CLAUDE.md`의 Logging 참고). `traceId`는 특히 위조되면 모든 요청에 같은 값을 보내 요청 간 구분을
   무력화하거나, 남의 `traceId`를 사칭해 로그를 섞을 수 있다.
2. TLS를 우회해 평문으로 API를 호출할 수 있게 된다.

더 조이려면 compose의 `ports`를 `127.0.0.1:8080:8080`으로 바꾼다. nginx가 같은 호스트에 있으므로
프록시는 그대로 동작하고, 보안 그룹이 나중에 잘못 열리거나 같은 서브넷의 다른 호스트가 접근하는
경우까지 막힌다. (Docker는 포트를 publish할 때 호스트 방화벽(ufw)보다 앞단의 iptables 규칙을
직접 넣으므로, 호스트 방화벽만 믿으면 안 된다. 보안 그룹은 EC2 바깥이라 영향받지 않는다.)

MySQL은 봇 스캔의 1순위 타깃이다. compose에 `ports`를 넣지 않았으므로 컨테이너
포트는 `juby-net` 내부에서만 보인다. 로컬에서 DB를 들여다봐야 하면 SSH 터널을
쓴다 (이 경우에만 compose의 `127.0.0.1:3306:3306` 주석을 해제한다).

```bash
ssh -L 3306:127.0.0.1:3306 -i <key.pem> <user>@<host>
# 이후 로컬 워크벤치에서 127.0.0.1:3306 접속
```

## 로그 수집 (CloudWatch Logs)

앱 컨테이너의 stdout을 Docker `awslogs` 로그 드라이버로 CloudWatch Logs에 직접 적재한다.
컨테이너를 교체해도(`up -d app`) 이전 로그가 남는다. 설정은 `docker-compose.yml`의 `app` 서비스
`logging` 블록에 있다 (override 파일로 분리하지 않았다 — `app` 서비스 자체가 배포 전용 정의라
로컬에서 이 경로를 타지 않기 때문이다).

```
 client ──▶ nginx(443) ──▶ app container → stdout (ECS JSON 1줄)
                                   │ docker log driver: awslogs
                                   ▼
                      CloudWatch Logs  /juby/app  (retention 14일)
```

### 인증은 EC2 인스턴스 역할로만 된다

로그 드라이버는 컨테이너가 아니라 **dockerd(호스트 데몬)가 실행**한다. 따라서 `env_file: .env`에
`AWS_ACCESS_KEY_ID` 같은 걸 넣어도 **전혀 영향이 없다.** 인스턴스 역할에 아래 권한이 있어야 한다.

| 권한 | 필요한 이유 |
|---|---|
| `logs:CreateLogStream` | 스트림 `app` 생성 |
| `logs:PutLogEvents` | 로그 전송 |
| `logs:DescribeLogStreams` | 시퀀스 토큰 조회 |
| `logs:CreateLogGroup` | compose에 `awslogs-create-group: "true"`가 있는 동안 필요 |

권한이 없으면 **컨테이너가 기동 자체에 실패**한다. 드라이버 초기화 단계에서 터지기 때문에 앱 로그도
남지 않으므로, 배포 직후 컨테이너가 안 뜨면 `docker inspect`의 상태 메시지를 먼저 볼 것.

### 로그 그룹과 보존기간

로그 그룹 `/juby/app`은 **retention 14일**로 지정되어 있다.

> **함정**: `awslogs-create-group: "true"`로 자동 생성된 로그 그룹은 보존기간이 **"Never expire"** 가
> 되어 요금이 무한히 누적된다. CloudWatch Logs 비용 사고 1순위 원인이고, 콘솔에 경고가 뜨지 않는다.
> 지금은 retention이 걸린 그룹이 이미 있어서 이 옵션이 `ResourceAlreadyExists`로 무시되는 no-op이지만,
> **`awslogs-group` 값을 바꾸면(오타·환경 추가·`tag:` 전환) Never expire 그룹이 조용히 새로 생긴다.**
> 그룹 이름을 건드렸으면 반드시 retention을 다시 걸 것.

```bash
# 현재 보존기간 확인 (retention 필드가 없으면 Never expire다)
aws logs describe-log-groups --region ap-northeast-2 \
  --log-group-name-prefix /juby/app \
  --query "logGroups[].{group:logGroupName,retention:retentionInDays,bytes:storedBytes}"

# 다시 거는 법 (기존 그룹에 소급 적용된다. 그룹을 새로 만들 필요 없다)
aws logs put-retention-policy --region ap-northeast-2 \
  --log-group-name /juby/app --retention-in-days 14
```

### ⚠️ `docker logs`가 더 이상 동작하지 않는다

`awslogs`는 기본 `json-file` 드라이버를 **대체**하므로, EC2에서 `sudo docker logs app`은 빈 출력이 된다.
배포 직후 기동 실패를 확인하던 수단이 사라졌다. 대체 수단은 다음 세 가지다.

```bash
# 1) 컨테이너가 떴는지 / 왜 죽었는지 (로그 드라이버와 무관하게 동작)
sudo docker inspect app --format '{{.State.Status}} {{.State.ExitCode}} {{.State.Error}}'

# 2) 실시간 로그 tail
aws logs tail /juby/app --follow --region ap-northeast-2

# 3) 특정 요청의 전 로그 복원 (traceId 조인)
aws logs start-query --region ap-northeast-2 \
  --log-group-name /juby/app \
  --start-time $(date -d '30 min ago' +%s) --end-time $(date +%s) \
  --query-string 'fields @timestamp, message | filter traceId = "<ID>" | sort @timestamp asc'
```

`mode: non-blocking`은 CloudWatch API가 느려지거나 끊겼을 때 **로그를 버리고 앱을 계속 돌리기 위한
것**이다. 기본값 `blocking`이면 stdout 쓰기가 막혀 애플리케이션 스레드가 그대로 멈춘다
(로그 유실 < 서비스 중단). 즉 **로그가 일부 비어 있을 수 있다는 게 정상 동작**이다.

### nginx access log와 traceId 맞추기

nginx가 `X-Request-ID: $request_id`를 넘기고 앱이 그 값을 `traceId`로 쓰므로, **nginx access log에도
`$request_id`를 찍으면 같은 요청을 양쪽에서 같은 키로 조회할 수 있다.** 기본 `combined` 포맷에는
없으니 추가해야 한다.

```nginx
log_format juby '$remote_addr - $request_id "$request" '
                '$status $body_bytes_sent '
                'rt=$request_time urt=$upstream_response_time';

access_log /var/log/nginx/access.log juby;
```

이게 있어야 진단되는 것들:

| nginx | 앱 | 실제 상황 |
|---|---|---|
| `502` | 로그 없음 | 앱이 죽었거나(OOM) 배포 중 기동 전 — 앱 로그에는 흔적이 없다 |
| `504` | `200 (75000ms)` | 앱은 성공했다고 믿지만 사용자는 실패를 봤다 (KIS/OpenAI 지연) |
| `499` | `200 (12000ms)` | 사용자가 기다리다 떠났다. 앱 지표상으론 정상 |
| `rt=3.0` | `latencyMs=30` | 코드가 아니라 큐잉·커넥션·느린 클라이언트 전송 문제 |

`$request_time`(요청 수신 ~ 응답 전송 완료)과 앱의 `latencyMs`(필터 구간) 차이가 곧 앱 바깥에서
쓴 시간이다. 설정 확인은 `sudo nginx -T | grep -nE 'log_format|access_log|request_id'`.

> 운영 로그에 **8자 traceId**가 보이면 nginx가 세팅한 값(32자 hex)이 아니라 앱의 폴백이 탄 것이고,
> 그건 **nginx를 우회해 8080으로 직접 들어온 요청**이라는 뜻이다. `clientIp=UNKNOWN`과 같은
> 이상 징후다 (보안 그룹상 나타나지 않아야 정상). 로컬은 nginx가 없으므로 8자가 정상이다.

### 로컬에서는 `app` 서비스를 띄우지 않는다

`docker-compose.yml`의 `app` 서비스는 이미지·`.env`·`SPRING_PROFILES_ACTIVE: dev`가 모두 배포 전용이라
로컬에서 기동할 수 있는 구성이 아니다. 로컬은 MySQL만 컨테이너로 띄우고 앱은 `./gradlew bootRun`으로
돌린다 (`local` 프로파일 = 평문 로그, awslogs 경로를 타지 않음).

```bash
docker compose up -d mysql     # 서비스 이름 없이 up -d 하면 app까지 올리려다 실패한다
```

## 운영 메모

- **재부팅 순서**: 앱과 MySQL이 동시에 뜨면서 앱이 먼저 DB에 붙으려다 실패할 수
  있다. 양쪽 모두 `restart unless-stopped`이므로 Docker가 재시작해 결국 붙는다.
  한두 번의 재시작 로그는 정상이다.
- **MySQL 컨테이너 교체**: 데이터는 `mysql-data` named volume에 있으므로
  `docker compose down && up -d`로 안전하게 갈아탈 수 있다. 단
  `docker compose down -v`의 `-v`는 **볼륨을 지운다**. 절대 쓰지 말 것.
- **HTTPS 구성 (완료)**: EC2 호스트의 nginx가 443을 받아 TLS를 종료하고 앱 컨테이너의
  8080으로 평문 전달한다. nginx는 `X-Forwarded-Proto` / `X-Forwarded-Host` / `X-Real-IP` /
  `X-Forwarded-For` / `X-Request-ID`를 붙여 넘긴다. 뒤 두 개는 **덮어쓰기**라서 신뢰할 수 있다
  (`X-Real-IP = $remote_addr`, `X-Request-ID = $request_id`). `X-Forwarded-For`는
  `$proxy_add_x_forwarded_for`로 **append**되어 클라이언트가 보낸 가짜 값이 맨 앞에 남으므로
  로깅에 쓰지 않는다 — 자세한 내용은 아래 "로그 수집" 절과 `CLAUDE.md`의 Logging 참고.
- **알려진 증상 — Swagger UI 서버 주소가 `http://`로 보인다**: nginx가 헤더를 보내는 것만으로는
  부족하고, 앱이 그 헤더를 **반영하려면** `server.forward-headers-strategy: FRAMEWORK`가 필요하다.
  현재 미설정(=`NONE`)이라 앱은 자기가 `http://localhost:8080`으로 서비스된다고 믿고,
  springdoc이 `request.getScheme()`으로 만드는 절대 URL이 `http://`로 나온다.
  인증 플로우는 `BACKEND_URL` 절대값을 쓰므로 영향받지 않는다.
  로깅 쪽은 `X-Real-IP`를 **헤더에서 직접** 읽으므로 이 설정과 무관하다
  (`MdcLoggingFilter`, `CLAUDE.md`의 Logging 참고). 그래서 급한 사안이 아니라 별도 이슈로 분리해 둔다.
  켤 때는 "8080이 열려 있지 않다"가 전제다 — 보안 그룹 절 참고.
- **RT 쿠키 속성**: `app.cookie.secure: true` / `same-site: None`으로
  `application-dev.yaml`에 **고정**되어 있다. env로 바꾸는 값이 아니다.
  프론트와 백엔드가 서로 다른 site이므로 `SameSite=None`이 필수이고, `None`은 `Secure`를
  요구하므로 HTTPS가 전제다. 둘 중 하나만 틀려도 에러 없이 쿠키만 사라진다.
- **프론트 배포 시 할 일**:
  1. `ENV_FILE`의 `FRONTEND_URL`을 실제 도메인으로 교체 (CORS origin으로도 쓰이므로 path 없이).
  2. RT 쿠키는 크로스 사이트 = **서드파티 쿠키**다. Chrome 일반 모드는 통과하지만
     시크릿 모드·Safari(ITP)·Firefox(Total Cookie Protection)에서는 차단되어
     `POST /api/auth/reissue`가 실패할 수 있다. 설정 오류가 아니라 브라우저 정책이다.
     프론트를 백엔드와 **같은 등록 도메인** 아래 두면(`app.example.io` / `api.example.io`)
     같은 site가 되어 `SameSite=Lax`로 되돌릴 수 있고 이 문제가 사라진다.
     도메인을 정할 때 함께 결정할 것.