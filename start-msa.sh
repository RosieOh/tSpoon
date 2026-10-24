#!/bin/bash
# 사용: ./start-msa.sh                 기본 스택
#       ./start-msa.sh --observability 관측성 스택(Grafana LGTM)까지 함께
set -euo pipefail

COMPOSE_FILES=(-f compose.yaml)
if [[ "${1:-}" == "--observability" ]]; then
    COMPOSE_FILES+=(-f compose.observability.yaml)
fi

echo "🚀 tSpoon MSA 교육 커뮤니티 시작하기"

echo "🔨 실행 jar 빌드..."
./gradlew bootJar

echo "🐳 컨테이너 빌드 및 시작 (헬스체크 순서대로 기동)..."
docker compose "${COMPOSE_FILES[@]}" up -d --build --wait

echo ""
echo "✅ tSpoon MSA 교육 커뮤니티가 시작되었습니다!"
echo ""
echo "📋 접속 정보:"
echo "  - API Gateway     : http://localhost:8080"
echo "  - API 문서        : http://localhost:8080/swagger-ui.html"
echo "  - Eureka Dashboard: http://localhost:8761"
if [[ ${#COMPOSE_FILES[@]} -gt 2 ]]; then
    echo "  - Grafana         : http://localhost:3000 (admin / admin) — 추적·로그·메트릭"
fi
echo ""
echo "🔍 상태 확인 : docker compose ps"
echo "🧪 E2E 점검 : python3 scripts/e2e.py"
echo "📊 로그 확인 : docker compose logs -f [service-name]"
echo "🛑 종료      : docker compose ${COMPOSE_FILES[*]} down"
