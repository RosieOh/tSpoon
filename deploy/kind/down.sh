#!/bin/bash
# kind 로컬 클러스터를 지운다 (데이터도 함께 지워진다)
set -euo pipefail
kind delete cluster --name tspoon
