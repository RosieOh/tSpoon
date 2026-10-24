#!/bin/bash
set -euo pipefail

echo "🚀 tSpoon MSA 교육 커뮤니티 시작하기"

echo "🔨 실행 jar 빌드..."
./gradlew bootJar

echo "🐳 컨테이너 빌드 및 시작 (헬스체크 순서대로 기동)..."
docker compose up -d --build --wait

echo ""
echo "✅ tSpoon MSA 교육 커뮤니티가 시작되었습니다!"
echo ""
echo "📋 접속 정보:"
echo "  - API Gateway     : http://localhost:8080"
echo "  - Eureka Dashboard: http://localhost:8761"
echo ""
echo "🔍 상태 확인 : docker compose ps"
echo "📊 로그 확인 : docker compose logs -f [service-name]"
echo "🛑 종료      : docker compose down"
