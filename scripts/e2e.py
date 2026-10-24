"""API Gateway 를 거쳐 실제 컨테이너 스택을 확인하는 E2E 점검.

사용: docker compose up -d --build --wait 후
      python3 scripts/e2e.py [시나리오...]      (시나리오를 생략하면 전부)

환경변수: E2E_GATEWAY(기본 http://localhost:8080), ADMIN_EMAIL, ADMIN_PASSWORD(compose 에 넘긴 값과 같아야 함)
외부 패키지 없이 표준 라이브러리만 쓴다.
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request
import uuid

GATEWAY = os.environ.get("E2E_GATEWAY", "http://localhost:8080")
ADMIN = (os.environ.get("ADMIN_EMAIL", "admin@tspoon.local"),
         os.environ.get("ADMIN_PASSWORD", "change-me-admin-password"))
results = []


def call(method, path, body=None, token=None, headers=None):
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    req = urllib.request.Request(GATEWAY + path, data=data, method=method)
    req.add_header("Content-Type", "application/json; charset=utf-8")
    if token:
        req.add_header("Authorization", "Bearer " + token)
    for k, v in (headers or {}).items():
        req.add_header(k, v)
    try:
        with urllib.request.urlopen(req, timeout=10) as res:
            raw = res.read().decode("utf-8")
            return res.status, (json.loads(raw) if raw else None), dict(res.headers)
    except urllib.error.HTTPError as e:
        raw = e.read().decode("utf-8")
        try:
            parsed = json.loads(raw) if raw else None
        except json.JSONDecodeError:
            parsed = raw
        return e.code, parsed, dict(e.headers)


def check(name, condition, detail=""):
    results.append(condition)
    print(("  OK  " if condition else "  FAIL") + f" {name}" + (f"  ({detail})" if detail and not condition else ""), flush=True)


def wait_ready(paths, timeout=120):
    deadline = time.time() + timeout
    while time.time() < deadline:
        if all(call("GET", p)[0] not in (502, 503, 504) for p in paths):
            return True
        time.sleep(2)
    return False


def signup_login(name="회원"):
    email = f"e2e-{uuid.uuid4().hex[:8]}@tspoon.com"
    status, user, _ = call("POST", "/api/users/register", {"email": email, "password": "password123", "name": name})
    assert status == 201, (status, user)
    _, login, _ = call("POST", "/api/users/login", {"email": email, "password": "password123"})
    return user["id"], email, login["accessToken"]


def admin_token():
    status, login, _ = call("POST", "/api/users/login", {"email": ADMIN[0], "password": ADMIN[1]})
    assert status == 200, (status, login)
    return login["accessToken"]


def scenario_auth():
    print("[auth] 가입·로그인·프로필")
    uid, email, token = signup_login("E2E부모")
    s, body, _ = call("GET", "/api/users/profile", token=token)
    check("프로필 200, 한글 이름 보존", s == 200 and body["name"] == "E2E부모", f"{s} {body}")
    s, _, _ = call("GET", "/api/users/profile", token="1_1700000000000")
    check("위조 토큰 401", s == 401, s)


def scenario_roles():
    print("[roles] 초기 관리자·역할 변경")
    admin = admin_token()
    s, _, _ = call("GET", "/api/users", token=admin)
    check("관리자 전체 목록 200", s == 200, s)
    uid, email, token = signup_login()
    s, _, _ = call("GET", "/api/users", token=token)
    check("일반 회원 전체 목록 403", s == 403, s)
    s, body, _ = call("PATCH", f"/api/admin/users/{uid}/role", {"role": "TEACHER"}, token=admin)
    check("Gateway 경유 역할 변경 200", s == 200 and body["role"] == "TEACHER", f"{s} {body}")
    _, login, _ = call("POST", "/api/users/login", {"email": email, "password": "password123"})
    check("재로그인 시 새 역할", login.get("role") == "TEACHER", login)
    s, _, _ = call("PATCH", f"/api/admin/users/{uid}/status", {"status": "SUSPENDED"}, token=admin)
    s2, _, _ = call("POST", "/api/users/login", {"email": email, "password": "password123"})
    check("정지 후 로그인 403", s == 200 and s2 == 403, f"{s} {s2}")
    s, _, _ = call("PATCH", f"/api/admin/users/{uid}/role", {"role": "ADMIN"}, token=token)
    check("일반 회원의 관리자 API 403", s == 403, s)


def scenario_privacy():
    print("[privacy] 공개 프로필")
    uid, _, _ = signup_login("공개대상")
    _, _, viewer = signup_login()
    s, body, _ = call("GET", f"/api/users/{uid}", token=viewer)
    check("공개 프로필에 연락처 없음", s == 200 and "email" not in body and "phone" not in body, f"{s} {body}")


def scenario_board():
    print("[board] 게시판")
    _, _, token = signup_login("글쓴이")
    s, board, _ = call("POST", "/api/boards", {"title": "E2E 글", "content": "본문", "category": "EDUCATION"}, token=token)
    check("글 작성 201, 작성자 이름", s == 201 and board["authorName"] == "글쓴이", f"{s} {board}")
    bid = board["id"]
    call("GET", f"/api/boards/{bid}")
    s, body, _ = call("GET", f"/api/boards/{bid}")
    check("조회수 2", s == 200 and body["viewCount"] == 2, f"{s} {body}")
    s1, _, _ = call("POST", f"/api/boards/{bid}/like", token=token)
    s2, _, _ = call("POST", f"/api/boards/{bid}/like", token=token)
    check("좋아요 200 · 중복 409", (s1, s2) == (200, 409), f"{s1} {s2}")


SCENARIOS = {
    "auth": scenario_auth,
    "roles": scenario_roles,
    "privacy": scenario_privacy,
    "board": scenario_board,
}

if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    names = sys.argv[1:] or list(SCENARIOS)
    if not wait_ready(["/api/boards", "/api/users/profile"]):
        print("Gateway 라우팅 준비 안 됨")
        sys.exit(1)
    for name in names:
        SCENARIOS[name]()
    print(f"\n{sum(results)}/{len(results)} 통과")
    sys.exit(0 if all(results) else 1)
