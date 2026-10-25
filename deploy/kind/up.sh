#!/bin/bash
# kind 로컬 클러스터에 tSpoon 을 올린다. Eureka 없이 Kubernetes Service 로 서비스를 찾는다.
#   필요: docker, kind, kubectl
#   확인: E2E_GATEWAY=http://localhost:18080 E2E_MAILPIT=http://localhost:18025 python3 scripts/e2e.py
set -euo pipefail
cd "$(dirname "$0")/../.."

CLUSTER=tspoon
CONTEXT="kind-$CLUSTER"
SERVICES=(api-gateway user-service community-service education-service qna-service event-service notification-service search-service file-service)
# 직접 빌드하는 인프라 이미지 (OpenSearch + nori)
BUILT_INFRA=(opensearch)

if ! kind get clusters | grep -qx "$CLUSTER"; then
    kind create cluster --config deploy/kind/cluster.yaml
fi

echo "🔨 jar·이미지 빌드 (docker compose 와 같은 Dockerfile·이미지 이름)"
./gradlew bootJar
docker compose build "${SERVICES[@]}" "${BUILT_INFRA[@]}"
for image in "${SERVICES[@]}" "${BUILT_INFRA[@]}"; do
    kind load docker-image "tspoon/$image:latest" --name "$CLUSTER"
done

echo "🚀 매니페스트 적용"
kubectl kustomize --load-restrictor LoadRestrictionsNone deploy/k8s/overlays/kind | kubectl --context "$CONTEXT" apply -f -
# 이미지를 다시 넣은 경우에도 새로 뜨게 한다
kubectl --context "$CONTEXT" -n tspoon rollout restart deployment "${SERVICES[@]}"
for svc in "${SERVICES[@]}"; do
    kubectl --context "$CONTEXT" -n tspoon rollout status "deployment/$svc" --timeout=10m
done

echo ""
echo "✅ kind 클러스터에 올렸습니다"
echo "  - API Gateway : http://localhost:18080 (문서: /swagger-ui.html)"
echo "  - Mailpit     : http://localhost:18025"
echo "  - 파일 저장소 : http://localhost:18333 (S3)"
echo "  - 상태        : kubectl --context $CONTEXT -n tspoon get pods"
echo "  - 삭제        : deploy/kind/down.sh"
