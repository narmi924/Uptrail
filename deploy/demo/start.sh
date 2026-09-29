#!/bin/sh
# Starts the demo container: the proxy first, then MySQL, then Uptrail. Uptrail loads fresh sample data
# into the empty database on its first start.
set -eu

DATA=/opt/uptrail/data
SOCKET=/var/run/mysqld/mysqld.sock
mkdir -p "$DATA/documents" "$DATA/mail" /var/run/mysqld
chown mysql:mysql /var/run/mysqld

caddy run --config /opt/uptrail/Caddyfile --adapter caddyfile &

mysqld --user=mysql \
  --datadir=/var/lib/uptrail-mysql \
  --socket="$SOCKET" --pid-file=/var/run/mysqld/mysqld.pid \
  --bind-address=127.0.0.1 --port=3306 --mysqlx=OFF \
  --disable-log-bin --performance-schema=OFF \
  --innodb-buffer-pool-size=64M --innodb-redo-log-capacity=8M \
  --max-connections=40 \
  --character-set-server=utf8mb4 --collation-server=utf8mb4_0900_ai_ci \
  --init-file=/opt/uptrail/init.sql &

tries=0
until mysqladmin --socket="$SOCKET" -uroot ping >/dev/null 2>&1; do
  tries=$((tries + 1))
  if [ "$tries" -gt 240 ]; then
    echo "MySQL did not start" >&2
    exit 1
  fi
  sleep 0.5
done

# One CPU and 2 GB of memory: a small heap, the serial collector and quick compilation keep start-up short.
exec java -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -Xss512k -Xms256m -Xmx512m \
  -XX:MaxMetaspaceSize=200m -jar /opt/uptrail/uptrail.jar
