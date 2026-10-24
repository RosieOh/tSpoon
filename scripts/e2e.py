"""API Gateway 를 거쳐 실제 컨테이너 스택을 확인하는 E2E 점검.

사용: docker compose up -d --build --wait 후
      python3 scripts/e2e.py [시나리오...]      (시나리오를 생략하면 전부)

환경변수: E2E_GATEWAY(기본 http://localhost:8080), E2E_MAILPIT(기본 http://localhost:8025), ADMIN_EMAIL, ADMIN_PASSWORD(compose 에 넘긴 값과 같아야 함)
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
MAILPIT = os.environ.get("E2E_MAILPIT", "http://localhost:8025")
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
    s, body, headers = call("GET", "/api/users/profile", token="1_1700000000000")
    check("위조 토큰 401 (ProblemDetail)", s == 401 and headers.get("Content-Type", "").startswith("application/problem+json")
          and body.get("status") == 401, f"{s} {headers.get('Content-Type')} {body}")


def scenario_roles():
    print("[roles] 초기 관리자·역할 변경·정지")
    admin = admin_token()
    s, _, _ = call("GET", "/api/users", token=admin)
    check("관리자 전체 목록 200", s == 200, s)
    uid, email, token = signup_login()
    s, _, _ = call("GET", "/api/users", token=token)
    check("일반 회원 전체 목록 403", s == 403, s)
    s, _, _ = call("PATCH", f"/api/admin/users/{uid}/role", {"role": "ADMIN"}, token=token)
    check("일반 회원의 관리자 API 403", s == 403, s)

    s, body, _ = call("PATCH", f"/api/admin/users/{uid}/role", {"role": "TEACHER"}, token=admin)
    check("Gateway 경유 역할 변경 200", s == 200 and body["role"] == "TEACHER", f"{s} {body}")
    s, _, _ = call("GET", "/api/users/profile", token=token)
    check("역할 변경 전 토큰은 Gateway 에서 401", s == 401, s)
    _, login, _ = call("POST", "/api/users/login", {"email": email, "password": "password123"})
    check("재로그인 시 새 역할", login.get("role") == "TEACHER", login)

    s, _, _ = call("PATCH", f"/api/admin/users/{uid}/status", {"status": "SUSPENDED"}, token=admin)
    s2, _, _ = call("GET", "/api/users/profile", token=login["accessToken"])
    s3, _, _ = call("POST", "/api/users/token/refresh", {"refreshToken": login["refreshToken"]})
    s4, _, _ = call("POST", "/api/users/login", {"email": email, "password": "password123"})
    check("정지 후 기존 토큰 401 · 재발급 401 · 로그인 403", (s, s2, s3, s4) == (200, 401, 401, 403), f"{s} {s2} {s3} {s4}")


def scenario_tokens():
    print("[tokens] 재발급·재사용 감지·로그아웃")
    _, email, _ = signup_login()
    _, login, _ = call("POST", "/api/users/login", {"email": email, "password": "password123"})
    check("Access 30분 · Refresh 14일", login["expiresIn"] == 1800 and login["refreshExpiresIn"] == 1209600, login)

    s, refreshed, _ = call("POST", "/api/users/token/refresh", {"refreshToken": login["refreshToken"]},
                           token="expired-access-token-attached-by-client")
    check("재발급 200 (헤더의 낡은 토큰은 무시)", s == 200 and refreshed["refreshToken"] != login["refreshToken"], f"{s} {refreshed}")
    s, _, _ = call("GET", "/api/users/profile", token=refreshed["accessToken"])
    check("새 Access Token 으로 프로필 200", s == 200, s)

    s1, _, _ = call("POST", "/api/users/token/refresh", {"refreshToken": login["refreshToken"]})
    s2, _, _ = call("POST", "/api/users/token/refresh", {"refreshToken": refreshed["refreshToken"]})
    check("쓴 Refresh 재사용 401 → 계열 전체 폐기 401", (s1, s2) == (401, 401), f"{s1} {s2}")

    _, login2, _ = call("POST", "/api/users/login", {"email": email, "password": "password123"})
    s, _, _ = call("POST", "/api/users/logout", {"refreshToken": login2["refreshToken"]}, token=login2["accessToken"])
    s2, body, _ = call("GET", "/api/users/profile", token=login2["accessToken"])
    s3, _, _ = call("POST", "/api/users/token/refresh", {"refreshToken": login2["refreshToken"]})
    check("로그아웃 204 → 그 Access 401(Gateway) · Refresh 401", (s, s2, s3) == (204, 401, 401), f"{s} {s2} {s3} {body}")


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
    check("생성 시각에 시간대(Z)", board["createdAt"].endswith("Z"), board["createdAt"])
    s, page, _ = call("GET", "/api/boards?size=1")
    check("목록에 페이지 정보", s == 200 and len(page["content"]) == 1 and page["page"]["size"] == 1, f"{s} {page}")
    s1, _, _ = call("POST", f"/api/boards/{bid}/like", token=token)
    s2, _, _ = call("POST", f"/api/boards/{bid}/like", token=token)
    check("좋아요 200 · 중복 409", (s1, s2) == (200, 409), f"{s1} {s2}")

    s, comment, _ = call("POST", f"/api/boards/{bid}/comments", {"content": "댓글입니다"}, token=token)
    s2, _, _ = call("POST", f"/api/boards/{bid}/comments", {"content": "답글입니다", "parentCommentId": comment["id"]}, token=token)
    s3, comments, _ = call("GET", f"/api/boards/{bid}/comments")
    check("댓글 201 · 답글 201 · 답글이 댓글 아래로", (s, s2, s3) == (201, 201, 200)
          and comments[0]["replies"][0]["content"] == "답글입니다", f"{s} {s2} {s3} {comments}")
    _, after, _ = call("GET", f"/api/boards/{bid}")
    check("게시글 댓글 수 2", after["commentCount"] == 2, after)


def promote(role):
    """관리자가 새 회원의 역할을 바꾸고, 그 회원이 재발급받은 (새 역할이 담긴) 토큰을 돌려준다."""
    uid, email, _ = signup_login(f"{role}회원")
    s, _, _ = call("PATCH", f"/api/admin/users/{uid}/role", {"role": role}, token=admin_token())
    assert s == 200, s
    _, login, _ = call("POST", "/api/users/login", {"email": email, "password": "password123"})
    return uid, login["accessToken"]


def scenario_education():
    print("[education] 교사 콘텐츠·북마크 (역할 변경 → 다른 서비스 권한)")
    _, parent = signup_login("학부모")[::2]
    content = {"title": "E2E 교육 콘텐츠", "description": "설명", "type": "ARTICLE", "category": "MATH", "draft": False}
    s, _, _ = call("POST", "/api/contents", content, token=parent)
    check("학부모의 콘텐츠 작성 403", s == 403, s)

    _, teacher = promote("TEACHER")
    s, created, _ = call("POST", "/api/contents", content, token=teacher)
    check("교사로 바뀐 회원의 콘텐츠 작성 201", s == 201, f"{s} {created}")
    cid = created["id"]
    s, page, _ = call("GET", "/api/contents?category=MATH")
    check("비로그인 목록에 노출", s == 200 and any(c["id"] == cid for c in page["content"]), s)

    s1, _, _ = call("POST", f"/api/contents/{cid}/bookmark", token=parent)
    s2, mine, _ = call("GET", "/api/contents/bookmarks/me", token=parent)
    check("북마크 200 · 내 북마크에 노출", s1 == 200 and s2 == 200 and mine["content"][0]["id"] == cid, f"{s1} {s2}")


def scenario_qna():
    print("[qna] 질문·전문가 답변·채택")
    _, _, parent = signup_login("질문하는부모")
    s, question, _ = call("POST", "/api/questions", {"title": "E2E 질문", "content": "내용", "category": "LEARNING"}, token=parent)
    _, expert = promote("EXPERT")
    s2, answer, _ = call("POST", f"/api/questions/{question['id']}/answers", {"content": "전문가 답변"}, token=expert)
    check("질문 201 · 답변 201 · 답변 유형 EXPERT", (s, s2) == (201, 201) and answer["type"] == "EXPERT", f"{s} {s2} {answer}")
    s, detail, _ = call("POST", f"/api/questions/{question['id']}/answers/{answer['id']}/accept", token=parent)
    s2, _, _ = call("POST", f"/api/questions/{question['id']}/answers/{answer['id']}/accept", token=parent)
    check("채택 200 → 답변 완료 · 다시 채택 409", s == 200 and detail["status"] == "ANSWERED" and s2 == 409, f"{s} {s2}")


def scenario_events():
    print("[events] 행사 등록·정원·취소")
    import datetime
    start = (datetime.datetime.now(datetime.timezone(datetime.timedelta(hours=9))) + datetime.timedelta(days=7)).replace(microsecond=0, tzinfo=None)
    _, teacher = promote("TEACHER")
    event = {"title": "E2E 세미나", "description": "설명", "type": "SEMINAR", "maxParticipants": 1,
             "startDate": start.isoformat(), "endDate": (start + datetime.timedelta(hours=2)).isoformat()}
    s, created, _ = call("POST", "/api/events", event, token=teacher)
    check("교사의 행사 등록 201", s == 201, f"{s} {created}")
    eid = created["id"]
    _, _, first = signup_login("첫신청")
    _, _, second = signup_login("둘째신청")
    s1, _, _ = call("POST", f"/api/events/{eid}/registrations", {}, token=first)
    s2, body, _ = call("POST", f"/api/events/{eid}/registrations", {}, token=second)
    check("첫 신청 201 · 정원 마감 409", (s1, s2) == (201, 409), f"{s1} {s2} {body}")
    s3, _, _ = call("DELETE", f"/api/events/{eid}/registrations/me", token=first)
    s4, _, _ = call("POST", f"/api/events/{eid}/registrations", {}, token=second)
    _, detail, _ = call("GET", f"/api/events/{eid}")
    check("취소 204 → 자리 반환 → 둘째 신청 201 · 다시 FULL", (s3, s4) == (204, 201) and detail["status"] == "FULL", f"{s3} {s4} {detail.get('status')}")


def scenario_notifications():
    print("[notifications] 관리자 공지 → 알림함·이메일")
    admin = admin_token()
    uid, _, token = signup_login("알림받는회원")
    s, _, _ = call("POST", "/api/admin/notifications",
                   {"recipients": [{"userId": uid}], "title": "E2E 공지", "content": "내용", "channel": "IN_APP"}, token=admin)
    s2, count, _ = call("GET", "/api/notifications/unread-count", token=token)
    s3, read, _ = call("PATCH", "/api/notifications/read-all", token=token)
    check("앱 내 알림 201 → 안 읽은 1 → 모두 읽음 1", (s, s2) == (201, 200) and count["count"] == 1 and read["updated"] == 1,
          f"{s} {s2} {count} {read}")

    subject = f"E2E 메일 {uuid.uuid4().hex[:6]}"
    s, _, _ = call("POST", "/api/admin/notifications",
                   {"recipients": [{"userId": uid, "email": "e2e@tspoon.com"}], "title": subject, "content": "본문", "channel": "EMAIL"},
                   token=admin)
    delivered = False
    for _ in range(20):
        with urllib.request.urlopen(MAILPIT + "/api/v1/messages", timeout=5) as r:
            messages = json.loads(r.read().decode("utf-8")).get("messages", [])
        if any(m["Subject"] == "[티스푼] " + subject for m in messages):
            delivered = True
            break
        time.sleep(1)
    check("이메일 알림 201 → Mailpit 에 도착", s == 201 and delivered, f"{s} {delivered}")


def scenario_docs():
    print("[docs] Gateway 통합 API 문서")
    s, config, _ = call("GET", "/v3/api-docs/swagger-config")
    names = [u["name"] for u in (config or {}).get("urls", [])]
    check("Swagger UI 에 서비스 6개 문서", s == 200 and len(names) == 6, f"{s} {names}")
    s, docs, _ = call("GET", "/docs/user-service/v3/api-docs")
    check("Gateway 경유 user-service 문서", s == 200 and "/api/users/login" in docs.get("paths", {}), s)
    s, docs, _ = call("GET", "/docs/community-service/v3/api-docs")
    check("Gateway 경유 community-service 문서", s == 200 and "/api/boards" in docs.get("paths", {}), s)


SCENARIOS = {
    "auth": scenario_auth,
    "roles": scenario_roles,
    "tokens": scenario_tokens,
    "privacy": scenario_privacy,
    "board": scenario_board,
    "education": scenario_education,
    "qna": scenario_qna,
    "events": scenario_events,
    "notifications": scenario_notifications,
    "docs": scenario_docs,
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
