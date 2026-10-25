"""API Gateway 를 거쳐 실제 컨테이너 스택을 확인하는 E2E 점검.

사용: docker compose up -d --build --wait 후
      python3 scripts/e2e.py [시나리오...]      (시나리오를 생략하면 전부)

환경변수: E2E_GATEWAY(기본 http://localhost:8080), E2E_MAILPIT(기본 http://localhost:8025),
         E2E_PLATFORM(compose | kubernetes — 서비스를 멈췄다 켜는 시나리오에서 씀), ADMIN_EMAIL, ADMIN_PASSWORD(compose 에 넘긴 값과 같아야 함)
외부 패키지 없이 표준 라이브러리만 쓴다.
"""
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
import uuid

GATEWAY = os.environ.get("E2E_GATEWAY", "http://localhost:8080")
MAILPIT = os.environ.get("E2E_MAILPIT", "http://localhost:8025")
PLATFORM = os.environ.get("E2E_PLATFORM", "compose")  # compose | kubernetes (kind)
ADMIN = (os.environ.get("ADMIN_EMAIL", "admin@tspoon.local"),
         os.environ.get("ADMIN_PASSWORD", "change-me-admin-password"))
results = []


def call(method, path, body=None, token=None, headers=None, retry=True):
    """Gateway 의 요청 제한(429 RATE_LIMITED)에 걸리면 올바른 클라이언트처럼 Retry-After 만큼 기다렸다 다시 보낸다."""
    for _ in range(5):
        status, parsed, response_headers = send(method, path, body, token, headers)
        limited = status == 429 and isinstance(parsed, dict) and parsed.get("code") == "RATE_LIMITED"
        if not (retry and limited):
            break
        time.sleep(int(header(response_headers, "Retry-After") or "1"))
    return status, parsed, response_headers


def header(headers, name):
    return next((v for k, v in (headers or {}).items() if k.lower() == name.lower()), None)


def send(method, path, body=None, token=None, headers=None):
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


def mailpit(path):
    with urllib.request.urlopen(MAILPIT + path, timeout=5) as r:
        return json.loads(r.read().decode("utf-8"))


def latest_mail_text(address, subject, timeout=30):
    """Mailpit 에서 그 주소로 온 가장 최근 메일 본문. 메일은 Kafka 를 거쳐 비동기로 오므로 기다린다."""
    deadline = time.time() + timeout
    query = urllib.parse.quote(f'to:"{address}" subject:"{subject}"')
    while time.time() < deadline:
        found = mailpit(f"/api/v1/search?query={query}").get("messages", [])
        if found:
            return mailpit(f"/api/v1/message/{found[0]['ID']}")["Text"]
        time.sleep(1)
    return None


def verification_token(email):
    text = latest_mail_text(email, "이메일 주소를 확인해 주세요") or ""
    match = re.search(r"/verify-email\?token=([A-Za-z0-9_-]+)", text)
    return match.group(1) if match else None


def signup_login(name="회원"):
    email = f"e2e-{uuid.uuid4().hex[:8]}@tspoon.com"
    status, user, _ = call("POST", "/api/users/register", {"email": email, "password": "password123", "name": name})
    assert status == 201, (status, user)
    token = verification_token(email)
    status, _, _ = call("POST", "/api/users/email-verification/confirm", {"token": token})
    assert status == 204, (status, email, token)
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


def scenario_email_verification():
    print("[email-verification] 가입 → 인증 메일 → 인증 → 로그인")
    email = f"e2e-{uuid.uuid4().hex[:8]}@tspoon.com"
    credentials = {"email": email, "password": "password123"}
    s, user, _ = call("POST", "/api/users/register", {**credentials, "name": "인증회원"})
    s2, body, _ = call("POST", "/api/users/login", credentials)
    check("가입 201 → 인증 전 로그인 403 (code EMAIL_NOT_VERIFIED)",
          s == 201 and not user["emailVerified"] and s2 == 403 and body.get("code") == "EMAIL_NOT_VERIFIED", f"{s} {s2} {body}")
    token = verification_token(email)
    check("user → Kafka → notification → Mailpit 인증 메일 도착", token is not None, email)
    s, _, _ = call("POST", "/api/users/email-verification/confirm", {"token": token})
    s2, _, _ = call("POST", "/api/users/login", credentials)
    s3, _, _ = call("POST", "/api/users/email-verification/confirm", {"token": token})
    check("인증 204 → 로그인 200 · 같은 링크 재사용 400", (s, s2, s3) == (204, 200, 400), f"{s} {s2} {s3}")
    s, _, _ = call("POST", "/api/users/email-verification/resend", {"email": f"nobody-{uuid.uuid4().hex[:8]}@tspoon.com"})
    check("가입하지 않은 이메일 재발송도 202", s == 202, s)


def scenario_password():
    print("[password] 비밀번호 재설정·변경")
    _, email, _ = signup_login("비번회원")
    _, login, _ = call("POST", "/api/users/login", {"email": email, "password": "password123"})
    s, _, _ = call("POST", "/api/users/password-reset/request", {"email": email})
    text = latest_mail_text(email, "비밀번호 재설정 안내") or ""
    match = re.search(r"/reset-password\?token=([A-Za-z0-9_-]+)", text)
    check("재설정 요청 202 → 재설정 메일 도착", s == 202 and match is not None, f"{s} {text[:80]}")
    s, _, _ = call("POST", "/api/users/password-reset/confirm",
                   {"token": match.group(1) if match else "missing", "newPassword": "reset-pass-456"})
    s2, _, _ = call("POST", "/api/users/login", {"email": email, "password": "password123"})
    s3, relogin, _ = call("POST", "/api/users/login", {"email": email, "password": "reset-pass-456"})
    s4, _, _ = call("GET", "/api/users/profile", token=login["accessToken"])
    check("재설정 204 → 옛 비밀번호 401 · 새 비밀번호 200 · 기존 토큰 401(Gateway)", (s, s2, s3, s4) == (204, 401, 200, 401),
          f"{s} {s2} {s3} {s4}")
    s, changed, _ = call("PUT", "/api/users/password", {"currentPassword": "reset-pass-456", "newPassword": "change-pass-789"},
                         token=relogin["accessToken"])
    s2, _, _ = call("GET", "/api/users/profile", token=relogin["accessToken"])
    s3, _, _ = call("GET", "/api/users/profile", token=(changed or {}).get("accessToken"))
    check("변경 200 → 이전 토큰 401 · 응답의 새 토큰 200", (s, s2, s3) == (200, 401, 200), f"{s} {s2} {s3}")
    check("비밀번호 변경 알림 메일 도착", latest_mail_text(email, "비밀번호가 변경되었습니다") is not None, email)


def scenario_login_lock():
    print("[login-lock] 연속 로그인 실패 잠금")
    _, email, _ = signup_login("잠금회원")
    statuses = [call("POST", "/api/users/login", {"email": email, "password": "wrong-password"})[0] for _ in range(5)]
    s, body, headers = call("POST", "/api/users/login", {"email": email, "password": "password123"})
    check("5번 틀린 뒤에는 올바른 비밀번호도 429 (code LOGIN_LOCKED, Retry-After)",
          statuses == [401] * 5 and s == 429 and body.get("code") == "LOGIN_LOCKED" and int(header(headers, "Retry-After") or 0) > 0,
          f"{statuses} {s} {body}")


def scenario_rate_limit():
    print("[rate-limit] Gateway IP 별 요청 제한")
    results = [call("POST", "/api/users/password-reset/request", {"email": f"flood-{i}@tspoon.com"}, retry=False)
               for i in range(60)]
    limited = [r for r in results if r[0] == 429]
    status, body, headers = limited[0] if limited else (None, {}, {})
    check("한꺼번에 몰리면 Gateway 가 429 (ProblemDetail, code RATE_LIMITED, Retry-After)",
          bool(limited) and body.get("code") == "RATE_LIMITED" and header(headers, "Retry-After") == "1"
          and (header(headers, "Content-Type") or "").startswith("application/problem+json"), f"{len(limited)}/60 {body}")
    s, _, _ = call("GET", "/api/boards")
    check("제한 경로가 아닌 API 는 그대로 200", s == 200, s)
    time.sleep(16)  # 뒤이어 도는 시나리오가 한도에 걸리지 않게 토큰이 다시 찰 때까지 기다린다


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


def scenario_children():
    print("[children] 자녀 정보")
    _, _, token = signup_login("자녀부모")
    _, _, other = signup_login()
    s, child, _ = call("POST", "/api/users/children",
                       {"name": "김하나", "birthDate": "2017-03-02", "grade": "ELEMENTARY_3", "interests": ["독서", "코딩"]},
                       token=token)
    check("자녀 등록 201", s == 201 and child["interests"] == ["독서", "코딩"], f"{s} {child}")
    s, updated, _ = call("PUT", f"/api/users/children/{child['id']}",
                         {"name": "김하나", "grade": "ELEMENTARY_4", "interests": ["과학"]}, token=token)
    s2, _, _ = call("DELETE", f"/api/users/children/{child['id']}", token=other)
    _, children, _ = call("GET", "/api/users/children", token=token)
    check("수정 200 · 다른 회원의 삭제 404", s == 200 and s2 == 404 and children[0]["grade"] == "ELEMENTARY_4",
          f"{s} {s2} {children}")
    s, _, _ = call("DELETE", f"/api/users/children/{child['id']}", token=token)
    _, children, _ = call("GET", "/api/users/children", token=token)
    check("본인 삭제 204 → 목록 비어 있음", s == 204 and children == [], f"{s} {children}")


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


def scenario_moderation():
    print("[moderation] 신고 → 자동 숨김 → 관리자 기각")
    admin = admin_token()
    _, _, writer = signup_login("신고당한회원")
    _, board, _ = call("POST", "/api/boards", {"title": "광고 글", "content": "본문", "category": "GENERAL"}, token=writer)
    reporters = [signup_login(f"신고자{i}")[2] for i in range(5)]
    statuses = [call("POST", f"/api/boards/{board['id']}/reports", {"reason": "SPAM"}, token=t)[0] for t in reporters]
    s, _, _ = call("GET", f"/api/boards/{board['id']}")
    check("다섯 명이 신고 201 → 게시글 자동 숨김 404", statuses == [201] * 5 and s == 404, f"{statuses} {s}")
    s, pending, _ = call("GET", "/api/admin/reports/boards?size=100", token=admin)
    target = next((t for t in (pending or {}).get("content", []) if t["targetId"] == board["id"]), None)
    check("Gateway 경유 관리자 신고 목록 (5건, HIDDEN)",
          s == 200 and target is not None and target["reportCount"] == 5 and target["targetStatus"] == "HIDDEN", f"{s} {target}")
    s, _, _ = call("POST", f"/api/admin/reports/boards/{board['id']}/dismiss", token=admin)
    s2, _, _ = call("GET", f"/api/boards/{board['id']}")
    check("기각 204 → 다시 공개 200", (s, s2) == (204, 200), f"{s} {s2}")
    s, _, _ = call("GET", "/api/admin/reports/questions", token=admin)
    s2, _, _ = call("GET", "/api/admin/reports/boards", token=writer)
    check("질문 신고 목록은 qna-service 로 200 · 일반 회원 403", (s, s2) == (200, 403), f"{s} {s2}")


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
    delivered = latest_mail_text("e2e@tspoon.com", subject, timeout=20) is not None
    check("이메일 알림 201 → Mailpit 에 도착", s == 201 and delivered, f"{s} {delivered}")


def wait_until(condition, timeout=30):
    """Kafka 를 거쳐 비동기로 반영되는 결과를 기다린다."""
    deadline = time.time() + timeout
    while time.time() < deadline:
        if condition():
            return True
        time.sleep(1)
    return False


def scenario_rename():
    print("[rename] 이름 변경 → 다른 서비스에 복사해 둔 작성자 이름")
    _, email, token = signup_login("옛이름")
    _, board, _ = call("POST", "/api/boards", {"title": "이름 바꿀 글", "content": "본문", "category": "GENERAL"}, token=token)
    _, question, _ = call("POST", "/api/questions", {"title": "이름 바꿀 질문", "content": "내용", "category": "LEARNING"}, token=token)
    s, _, _ = call("PUT", "/api/users/profile", {"name": "새이름"}, token=token)
    s2, _, _ = call("GET", "/api/users/profile", token=token)
    check("이름 변경 200 → 옛 이름이 담긴 토큰 401(Gateway)", (s, s2) == (200, 401), f"{s} {s2}")
    synced = wait_until(lambda: call("GET", f"/api/boards/{board['id']}")[1].get("authorName") == "새이름"
                        and call("GET", f"/api/questions/{question['id']}")[1].get("authorName") == "새이름")
    check("user → Kafka → 게시판·Q&A 의 예전 글 작성자 이름도 새이름", synced, f"{board['id']} {question['id']}")
    _, login, _ = call("POST", "/api/users/login", {"email": email, "password": "password123"})
    s, created, _ = call("POST", "/api/boards", {"title": "새 글", "content": "본문", "category": "GENERAL"}, token=login["accessToken"])
    check("다시 받은 토큰으로 쓴 글은 새 이름", s == 201 and created["authorName"] == "새이름", f"{s} {created}")


def unread_count(token, at_least, timeout=30):
    """알림은 Kafka 를 거쳐 비동기로 만들어지므로 기다리며 확인한다."""
    deadline = time.time() + timeout
    count = None
    while time.time() < deadline:
        s, body, _ = call("GET", "/api/notifications/unread-count", token=token)
        count = body.get("count") if s == 200 and isinstance(body, dict) else None
        if count is not None and count >= at_least:
            return count
        time.sleep(1)
    return count


def scenario_domain_events():
    print("[domain-events] 댓글·답변·채택 → Kafka → 알림")
    _, _, writer = signup_login("글쓴부모")
    _, _, commenter = signup_login("댓글부모")
    _, board, _ = call("POST", "/api/boards", {"title": "알림 받을 글", "content": "본문", "category": "GENERAL"}, token=writer)
    call("POST", f"/api/boards/{board['id']}/comments", {"content": "응원합니다"}, token=commenter)
    check("남의 글 댓글 → 글쓴이 알림 1", unread_count(writer, 1) == 1, unread_count(writer, 1, timeout=1))
    _, mine, _ = call("GET", "/api/notifications", token=writer)
    check("알림 제목·이동 경로", mine["content"][0]["title"] == "내 게시글에 댓글이 달렸습니다"
          and mine["content"][0]["actionUrl"] == f"/boards/{board['id']}", mine["content"][:1])

    _, _, asker = signup_login("질문부모")
    _, question, _ = call("POST", "/api/questions", {"title": "알림 질문", "content": "내용", "category": "LEARNING"}, token=asker)
    _, expert = promote("EXPERT")
    _, answer, _ = call("POST", f"/api/questions/{question['id']}/answers", {"content": "전문가 답변"}, token=expert)
    call("POST", f"/api/questions/{question['id']}/answers/{answer['id']}/accept", token=asker)
    check("답변 → 질문자 알림 · 채택 → 답변자 알림", unread_count(asker, 1) == 1 and unread_count(expert, 1) == 1,
          f"{unread_count(asker, 1, 1)} {unread_count(expert, 1, 1)}")


KUBECTL = ["kubectl", "--context", "kind-tspoon", "-n", "tspoon"]


def stop_notification_service():
    if PLATFORM == "kubernetes":
        subprocess.run(KUBECTL + ["scale", "deployment/notification-service", "--replicas=0"], check=True, capture_output=True)
        subprocess.run(KUBECTL + ["wait", "--for=delete", "pod", "-l", "app=notification-service", "--timeout=120s"],
                       check=True, capture_output=True)
    else:
        subprocess.run(["docker", "compose", "stop", "notification-service"], check=True, capture_output=True)


def start_notification_service():
    if PLATFORM == "kubernetes":
        subprocess.run(KUBECTL + ["scale", "deployment/notification-service", "--replicas=1"], check=True, capture_output=True)
        subprocess.run(KUBECTL + ["rollout", "status", "deployment/notification-service", "--timeout=300s"],
                       check=True, capture_output=True)
    else:
        subprocess.run(["docker", "compose", "start", "notification-service"], check=True, capture_output=True)
        subprocess.run(["docker", "compose", "up", "-d", "--wait", "notification-service"], check=True, capture_output=True)


def scenario_outbox_resilience():
    """알림 서비스가 멈춘 동안의 이벤트도 다시 켜면 알림이 된다 (#20 완료 기준). docker compose 를 직접 다룬다."""
    print("[outbox-resilience] 알림 서비스 중단 → 댓글 → 재시작")
    _, _, writer = signup_login("중단중글쓴이")
    _, _, commenter = signup_login("중단중댓글")
    _, board, _ = call("POST", "/api/boards", {"title": "중단 중 댓글", "content": "본문", "category": "GENERAL"}, token=writer)

    stop_notification_service()
    try:
        s, _, _ = call("POST", f"/api/boards/{board['id']}/comments", {"content": "멈춘 사이 댓글"}, token=commenter)
        check("알림 서비스가 멈춰도 댓글 작성 201", s == 201, s)
    finally:
        start_notification_service()
    check("재시작 뒤 놓친 알림 도착", unread_count(writer, 1, timeout=90) == 1, unread_count(writer, 1, 1))


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
    "email-verification": scenario_email_verification,
    "password": scenario_password,
    "login-lock": scenario_login_lock,
    "roles": scenario_roles,
    "tokens": scenario_tokens,
    "privacy": scenario_privacy,
    "children": scenario_children,
    "board": scenario_board,
    "education": scenario_education,
    "qna": scenario_qna,
    "events": scenario_events,
    "notifications": scenario_notifications,
    "domain-events": scenario_domain_events,
    "moderation": scenario_moderation,
    "rename": scenario_rename,
    "docs": scenario_docs,
    "rate-limit": scenario_rate_limit,  # 한도를 소진하므로 마지막에
}

# 컨테이너를 멈췄다 켜는 시나리오는 이름을 직접 줄 때만 돈다
OPT_IN_SCENARIOS = {
    "outbox-resilience": scenario_outbox_resilience,
}

if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    SCENARIOS.update({name: OPT_IN_SCENARIOS[name] for name in sys.argv[1:] if name in OPT_IN_SCENARIOS})
    names = sys.argv[1:] or [name for name in SCENARIOS if name not in OPT_IN_SCENARIOS]
    if not wait_ready(["/api/boards", "/api/users/profile"]):
        print("Gateway 라우팅 준비 안 됨")
        sys.exit(1)
    for name in names:
        SCENARIOS[name]()
    print(f"\n{sum(results)}/{len(results)} 통과")
    sys.exit(0 if all(results) else 1)
