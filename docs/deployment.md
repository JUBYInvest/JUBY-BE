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
└─────────┼──────────────────────────────────────┘
      8080│ (보안 그룹: 8080, 22만 개방. 3306은 절대 열지 않음)
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

레포의 `deploy/` 디렉터리를 EC2로 옮긴다.

```bash
mkdir -p ~/db
# 로컬에서:
#   scp -i <key.pem> deploy/docker-compose.yml deploy/low-mem.cnf \
#       deploy/.env.example deploy/backup-mysql.sh <user>@<host>:~/db/

cd ~/db
cp .env.example .env
chmod 600 .env
vi .env            # MYSQL_* 값을 실제 비밀번호로 채운다
chmod 644 low-mem.cnf   # MySQL은 world-writable 설정 파일을 무시한다

sudo docker compose up -d
sudo docker compose ps        # healthy 확인 (start_period 40s)
sudo docker compose logs -f mysql
```

`juby-net` 네트워크가 이때 생성된다. CI가 앱 컨테이너를 여기에 붙인다.

## 4. GitHub Secrets 갱신

`ENV_FILE` 시크릿에서 아래 두 항목을 수정한다.

```dotenv
# 호스트명 mysql은 juby-net 내부 DNS로 해석된다. RDS 엔드포인트가 아니다.
DB_URL=jdbc:mysql://mysql:3306/juby?serverTimezone=Asia/Seoul&characterEncoding=UTF-8

# 새 Elastic IP (또는 도메인). application-dev.yaml의 redirect-uri가 이 값을 쓴다.
BACKEND_URL=http://<ELASTIC_IP>:8080
```

`DB_USERNAME` / `DB_PASSWORD`는 `deploy/.env`의 `MYSQL_USER` / `MYSQL_PASSWORD`와
반드시 일치해야 한다. `EC2_HOST` 시크릿도 새 IP로 갱신한다.

## 5. 소셜 로그인 콜백 등록

`BACKEND_URL`을 바꿨으면 세 콘솔 모두에 콜백 URL을 등록해야 로그인이 된다.
등록값과 서버가 보내는 값이 한 글자라도 다르면 `redirect_uri_mismatch`가 난다.

| 제공자 | 등록할 Callback URL |
|---|---|
| 네이버 | `http://<ELASTIC_IP>:8080/login/oauth2/code/naver` |
| 구글 | `http://<ELASTIC_IP>:8080/login/oauth2/code/google` |
| 카카오 | `http://<ELASTIC_IP>:8080/login/oauth2/code/kakao` |

## 6. 백업 cron (필수)

RDS의 자동 백업·PITR을 포기했으므로 이게 유일한 복구 수단이다. 나중으로
미루지 말 것.

```bash
chmod +x ~/db/backup-mysql.sh
~/db/backup-mysql.sh          # 먼저 수동 실행해 성공을 확인

crontab -e
# 매일 04:00 (KST)
0 4 * * * /home/ubuntu/db/backup-mysql.sh >> /home/ubuntu/db/backup.log 2>&1
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
| 8080 | `0.0.0.0/0` | 애플리케이션 |
| 3306 | **열지 않음** | 아래 참고 |

MySQL은 봇 스캔의 1순위 타깃이다. compose에 `ports`를 넣지 않았으므로 컨테이너
포트는 `juby-net` 내부에서만 보인다. 로컬에서 DB를 들여다봐야 하면 SSH 터널을
쓴다 (이 경우에만 compose의 `127.0.0.1:3306:3306` 주석을 해제한다).

```bash
ssh -L 3306:127.0.0.1:3306 -i <key.pem> <user>@<host>
# 이후 로컬 워크벤치에서 127.0.0.1:3306 접속
```

## 운영 메모

- **재부팅 순서**: 앱과 MySQL이 동시에 뜨면서 앱이 먼저 DB에 붙으려다 실패할 수
  있다. 양쪽 모두 `restart unless-stopped`이므로 Docker가 재시작해 결국 붙는다.
  한두 번의 재시작 로그는 정상이다.
- **MySQL 컨테이너 교체**: 데이터는 `mysql-data` named volume에 있으므로
  `docker compose down && up -d`로 안전하게 갈아탈 수 있다. 단
  `docker compose down -v`의 `-v`는 **볼륨을 지운다**. 절대 쓰지 말 것.
- **HTTPS 전환 시**: 도메인 + nginx/ALB를 붙인 뒤 `BACKEND_URL`을 `https://...`로
  바꾸고, 소셜 콘솔 콜백도 갱신한다. RT 쿠키를 쓰는 인증 기능이 머지된 뒤에는
  `COOKIE_SECURE=true` / `COOKIE_SAME_SITE=None`도 함께 설정해야 한다.