#!/bin/bash
# eclipse-temurin JRE 이미지에는 curl/wget 이 없으므로 bash 의 /dev/tcp 로 Actuator readiness 를 확인한다.
exec 3<>"/dev/tcp/127.0.0.1/${SERVER_PORT}" || exit 1
printf 'GET /actuator/health/readiness HTTP/1.0\r\nHost: localhost\r\n\r\n' >&3
grep -q '"status":"UP"' <&3
