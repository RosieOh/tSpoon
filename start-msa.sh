#!/bin/bash

echo "🚀 tSpoon MSA 교육 커뮤니티 시작하기"

# PostgreSQL 시작
echo "📊 PostgreSQL 데이터베이스 시작..."
docker-compose up -d postgres

# 잠시 대기
echo "⏳ 데이터베이스 준비 대기 중..."
sleep 10

# 모든 서비스 빌드 (멀티 프로젝트)
echo "🔨 모든 마이크로서비스 빌드..."
./gradlew build -x test

# 모든 서비스 시작
echo "🎯 모든 마이크로서비스 시작..."
docker-compose up -d

echo "✅ tSpoon MSA 교육 커뮤니티가 시작되었습니다!"
echo ""
echo "📋 서비스 접속 정보:"
echo "  - API Gateway: http://localhost:8080"
echo "  - Eureka Server: http://localhost:8761"
echo "  - User Service: http://localhost:8081"
echo "  - Community Service: http://localhost:8082"
echo "  - Education Service: http://localhost:8083"
echo "  - QnA Service: http://localhost:8084"
echo "  - Event Service: http://localhost:8085"
echo "  - Notification Service: http://localhost:8086"
echo ""
echo "🔍 서비스 상태 확인:"
echo "  docker-compose ps"
echo ""
echo "📊 로그 확인:"
echo "  docker-compose logs -f [service-name]"
