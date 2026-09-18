#!/usr/bin/env bash
# EC2 내부 MySQL 컨테이너를 덤프해 로컬에 보관한다.
# RDS의 자동 백업을 포기한 대가이므로 반드시 cron에 등록할 것.
#
#   chmod +x backup-mysql.sh
#   crontab -e
#   0 4 * * * /home/ubuntu/db/backup-mysql.sh >> /home/ubuntu/db/backup.log 2>&1

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
BACKUP_DIR="${BACKUP_DIR:-$SCRIPT_DIR/backups}"
RETENTION_DAYS="${RETENTION_DAYS:-7}"
CONTAINER="${CONTAINER:-mysql}"

# compose와 같은 .env에서 자격증명을 읽는다.
set -a
# shellcheck source=/dev/null
source "$SCRIPT_DIR/.env"
set +a

mkdir -p "$BACKUP_DIR"
TARGET="$BACKUP_DIR/${MYSQL_DATABASE}-$(date +%F-%H%M).sql.gz"

# 덤프가 중간에 실패하면 잘린 파일이 정상 백업처럼 남는다. 반드시 지운다.
trap '[ $? -eq 0 ] || rm -f "$TARGET"' EXIT

# EC2 기본 사용자(ubuntu)는 docker 그룹에 속하지 않아 소켓에 접근할 수 없다.
# 접근 가능하면 그대로, 아니면 sudo를 붙인다. (cron은 TTY가 없으므로
# sudo는 NOPASSWD로 설정돼 있어야 한다)
if docker ps -q >/dev/null 2>&1; then
  DOCKER="docker"
else
  DOCKER="sudo -n docker"
fi

# --single-transaction: InnoDB를 락 없이 일관된 시점으로 덤프한다.
$DOCKER exec -e MYSQL_PWD="$MYSQL_ROOT_PASSWORD" "$CONTAINER" \
  mysqldump -u root \
    --single-transaction \
    --routines \
    --events \
    --default-character-set=utf8mb4 \
    "$MYSQL_DATABASE" \
  | gzip > "$TARGET"

# 덤프가 비어 있으면 실패로 간주해 남기지 않는다.
if [ ! -s "$TARGET" ]; then
  echo "[$(date +%F' '%T)] FAILED: empty dump, removing $TARGET" >&2
  rm -f "$TARGET"
  exit 1
fi

find "$BACKUP_DIR" -name '*.sql.gz' -mtime "+$RETENTION_DAYS" -delete

echo "[$(date +%F' '%T)] OK: $TARGET ($(du -h "$TARGET" | cut -f1))"

# S3로도 올리려면 AWS CLI 설정 후 아래 주석을 해제한다. (프리티어 5GB)
# aws s3 cp "$TARGET" "s3://$S3_BUCKET/mysql/" --storage-class STANDARD_IA