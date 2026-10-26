// 로컬 백엔드(docker compose)에 화면 확인용 데모 데이터를 넣는다.
// 사용: node scripts/seed.mjs
//   TSPOON_GATEWAY_URL(기본 http://localhost:8080), MAILPIT_URL(기본 http://localhost:8025),
//   ADMIN_EMAIL·ADMIN_PASSWORD(compose 에 넘긴 값과 같아야 함)
// 가입한 계정은 Mailpit 에 온 인증 메일의 링크로 인증한다. 콘텐츠가 이미 있으면 다시 넣지 않는다.

const GATEWAY = process.env.TSPOON_GATEWAY_URL ?? "http://localhost:8080";
const MAILPIT = process.env.MAILPIT_URL ?? "http://localhost:8025";
const ADMIN = {
  email: process.env.ADMIN_EMAIL ?? "admin@tspoon.local",
  password: process.env.ADMIN_PASSWORD ?? "change-me-admin-password",
};
const PASSWORD = "tspoon1234!";

async function call(method, path, body, token) {
  const res = await fetch(GATEWAY + path, {
    method,
    headers: {
      "Content-Type": "application/json",
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  const data = text ? JSON.parse(text) : null;
  if (!res.ok) throw new Error(`${method} ${path} → ${res.status} ${data?.detail ?? text}`);
  return data;
}

const login = (email, password = PASSWORD) => call("POST", "/api/users/login", { email, password });

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

// 인증 메일은 user → Kafka → notification → Mailpit 을 거쳐 비동기로 온다
async function verificationToken(email) {
  const query = encodeURIComponent(`to:"${email}" subject:"이메일 주소를 확인해 주세요"`);
  for (let i = 0; i < 30; i++) {
    const found = await (await fetch(`${MAILPIT}/api/v1/search?query=${query}`)).json();
    if (found.messages?.length) {
      const message = await (await fetch(`${MAILPIT}/api/v1/message/${found.messages[0].ID}`)).json();
      const token = message.Text.match(/\/verify-email\?token=([A-Za-z0-9_-]+)/)?.[1];
      if (token) return token;
    }
    await sleep(1000);
  }
  throw new Error(`${email} 인증 메일이 오지 않았습니다 (Mailpit ${MAILPIT})`);
}

async function ensureUser({ email, name, phone }) {
  try {
    return await login(email);
  } catch {
    await call("POST", "/api/users/register", { email, password: PASSWORD, name, phone });
    const token = await verificationToken(email);
    await call("POST", "/api/users/email-verification/confirm", { token });
    return login(email);
  }
}

// 현지 시각(한국) 문자열. 오늘 기준 days 일 뒤 hour 시
function at(days, hour, minute = 0) {
  const d = new Date();
  d.setDate(d.getDate() + days);
  const pad = (n) => String(n).padStart(2, "0");
  return `${d.getFullYear()}-${pad(d.getMonth() + 1)}-${pad(d.getDate())}T${pad(hour)}:${pad(minute)}:00`;
}

async function main() {
  const existing = await call("GET", "/api/contents?size=1");
  if (existing.page.totalElements > 0) {
    console.log("이미 데모 데이터가 있어 건너뜁니다.");
    return;
  }

  const admin = await login(ADMIN.email, ADMIN.password);
  const parent = await ensureUser({ email: "parent@tspoon.local", name: "김서연", phone: "010-1234-5678" });
  const parent2 = await ensureUser({ email: "parent2@tspoon.local", name: "최민호" });
  let teacher = await ensureUser({ email: "teacher@tspoon.local", name: "박지훈" });
  let expert = await ensureUser({ email: "expert@tspoon.local", name: "이수진" });

  await call("PATCH", `/api/admin/users/${teacher.userId}/role`, { role: "TEACHER" }, admin.accessToken);
  await call("PATCH", `/api/admin/users/${expert.userId}/role`, { role: "EXPERT" }, admin.accessToken);
  // 역할 변경은 기존 Access Token 을 무효화하므로 다시 로그인한다
  teacher = await login("teacher@tspoon.local");
  expert = await login("expert@tspoon.local");

  const children = await call("GET", "/api/users/children", undefined, parent.accessToken);
  if (children.length === 0) {
    await call("POST", "/api/users/children", { name: "민준", birthDate: "2018-04-12", grade: "ELEMENTARY_2", school: "서울한빛초등학교", interests: ["공룡", "레고"] }, parent.accessToken);
    await call("POST", "/api/users/children", { name: "하윤", birthDate: "2021-09-03", grade: "KINDERGARTEN", interests: ["그림 그리기"] }, parent.accessToken);
  }

  const contents = [
    [expert, { title: "초등 1학년, 한글 떼기보다 먼저 챙길 세 가지", description: "입학 첫 학기에 아이가 가장 힘들어하는 건 글자가 아니라 생활 리듬이에요. 등교 준비, 화장실, 도움 요청하기를 집에서 연습하는 방법을 정리했어요.", type: "ARTICLE", category: "ELEMENTARY", estimatedTime: 8, targetAge: "7~8세" }],
    [teacher, { title: "식탁에서 하는 10분 수 감각 놀이", description: "숟가락, 콩, 접시만 있으면 돼요. 더하기를 외우기 전에 '얼마나 많은지' 감을 잡는 놀이 다섯 가지를 영상으로 보여 드려요.", type: "VIDEO", category: "MATH", estimatedTime: 12, targetAge: "5~7세" }],
    [expert, { title: "아이의 '왜?'에 답하는 과학 대화법", description: "정답을 알려 주기보다 함께 추측해 보는 질문이 호기심을 오래 살려요. 상황별 대화 예시를 담았어요.", type: "ARTICLE", category: "SCIENCE", estimatedTime: 6, targetAge: "6~10세" }],
    [teacher, { title: "중학교 입학 전 자기주도 학습 루틴 만들기", description: "하루 30분, 계획-실행-돌아보기를 스스로 적어 보는 4주 학습지예요. 출력해서 냉장고에 붙여 두세요.", type: "WORKSHEET", category: "MIDDLE_SCHOOL", estimatedTime: 20, targetAge: "12~13세" }],
    [expert, { title: "그림책으로 시작하는 감정 코칭", description: "화가 난 아이에게 '그만'보다 먼저 건넬 수 있는 말이 있어요. 감정 단어를 늘려 주는 그림책 7권과 읽는 법을 소개해요.", type: "ARTICLE", category: "PARENTING", estimatedTime: 7, targetAge: "4~7세" }],
    [teacher, { title: "코딩 첫걸음, 컴퓨터 없이 하는 언플러그드 활동", description: "순서대로 명령하기, 반복 찾기, 조건 만들기를 카드 게임으로 익혀요. 형제자매와 함께 하기 좋아요.", type: "INTERACTIVE", category: "COMPUTER", estimatedTime: 15, targetAge: "8~11세" }],
    [teacher, { title: "구구단 2~9단 확인 퀴즈 30문항", description: "외운 구구단을 섞어서 물어보는 퀴즈예요. 틀린 단만 다시 연습할 수 있게 단별로 정리했어요.", type: "QUIZ", category: "MATH", estimatedTime: 10, targetAge: "8~9세" }],
    [expert, { title: "영어 그림책 소리 내어 읽기 가이드", description: "발음보다 리듬이 먼저예요. 부모가 영어에 자신이 없어도 함께 읽을 수 있는 방법과 추천 도서 목록을 PDF로 담았어요.", type: "PDF", category: "LANGUAGE", estimatedTime: 9, targetAge: "5~8세" }],
    [expert, { title: "유아기 소근육 발달 체크리스트", description: "가위질, 단추 끼우기, 블록 쌓기처럼 일상에서 확인할 수 있는 연령별 발달 지표와 놀이를 정리했어요.", type: "WORKSHEET", category: "EARLY_CHILDHOOD", estimatedTime: 5, targetAge: "3~5세" }],
  ];
  const contentIds = [];
  for (const [author, body] of contents) {
    const created = await call("POST", "/api/contents", { ...body, content: `${body.description}\n\n1. 먼저 아이와 함께 오늘 할 일을 정해요.\n2. 짧게, 자주 해요. 10분이면 충분해요.\n3. 끝나면 잘한 점 한 가지를 말로 짚어 줘요.\n\n아이마다 속도가 달라요. 비교보다 어제의 우리 아이와 견주어 봐 주세요.`, draft: false }, author.accessToken);
    contentIds.push(created.id);
  }
  await call("POST", `/api/contents/${contentIds[0]}/bookmark`, undefined, parent.accessToken);
  await call("POST", `/api/contents/${contentIds[4]}/bookmark`, undefined, parent.accessToken);
  await call("POST", `/api/contents/${contentIds[4]}/bookmark`, undefined, parent2.accessToken);

  const boards = [
    [parent2, { title: "초1 하교 후 학원 몇 개 보내세요?", content: "다음 달부터 돌봄이 끝나서 고민이에요. 태권도 하나만 보내려고 하는데 다들 어떻게 하시는지 궁금해요.", category: "QUESTION", tags: "초1,방과후" }],
    [parent, { title: "도서관 겨울방학 독서교실 다녀온 후기", content: "일주일 동안 매일 2시간이었는데 아이가 생각보다 즐거워했어요. 마지막 날 직접 만든 책을 들고 왔는데 뿌듯해하더라고요. 신청은 12월 첫 주에 열려요.", category: "REVIEW", tags: "독서,겨울방학" }],
    [teacher, { title: "받아쓰기 연습, 이렇게 해 보세요", content: "틀린 글자를 다섯 번 쓰게 하기보다, 왜 헷갈렸는지 아이가 말로 설명하게 해 보세요. 받침 규칙을 스스로 찾는 아이가 오래 기억해요.", category: "EDUCATION", tags: "받아쓰기,초등" }],
    [parent, { title: "잠자리 독립, 성공하신 분 계신가요", content: "여섯 살인데 아직 엄마 옆에서만 자려고 해요. 억지로 떼어 놓고 싶진 않은데 언제쯤 시작하는 게 좋을지 모르겠어요.", category: "PARENTING", tags: "수면,유아" }],
    [parent2, { title: "주말에 아이랑 가기 좋은 과학관 추천", content: "서울 근교로 찾고 있어요. 체험 위주로 된 곳이면 좋겠어요. 초등 2학년, 5살 둘이에요.", category: "GENERAL", tags: "주말,체험" }],
  ];
  const boardIds = [];
  for (const [author, body] of boards) boardIds.push((await call("POST", "/api/boards", body, author.accessToken)).id);

  const c1 = await call("POST", `/api/boards/${boardIds[0]}/comments`, { content: "저희는 피아노랑 태권도 두 개 해요. 처음엔 하나로 시작해서 아이가 적응하면 늘려 보세요." }, parent.accessToken);
  await call("POST", `/api/boards/${boardIds[0]}/comments`, { content: "감사해요! 하나부터 시작해 볼게요.", parentCommentId: c1.id }, parent2.accessToken);
  await call("POST", `/api/boards/${boardIds[3]}/comments`, { content: "저희는 방을 아이와 같이 꾸미면서 자연스럽게 됐어요. 좋아하는 인형을 '잠 지킴이'로 정해 줬더니 효과가 있었어요." }, parent2.accessToken);
  await call("POST", `/api/boards/${boardIds[3]}/comments`, { content: "아이가 불안해할 때 다시 와도 된다는 약속이 중요해요. 처음 2주는 문을 열어 두고 시작해 보세요." }, expert.accessToken);
  await call("POST", `/api/boards/${boardIds[1]}/comments`, { content: "좋은 정보 감사해요. 저도 신청해 볼게요!" }, parent2.accessToken);
  for (const id of [boardIds[1], boardIds[2]]) await call("POST", `/api/boards/${id}/like`, undefined, parent2.accessToken);
  await call("POST", `/api/boards/${boardIds[2]}/like`, undefined, parent.accessToken);

  const q1 = await call("POST", "/api/questions", { title: "7살 아이가 친구를 자꾸 때려요", content: "유치원에서 친구랑 놀다가 마음대로 안 되면 손이 먼저 나간대요. 집에서는 순한 편이라 더 당황스러워요. 어떻게 이야기해 주면 좋을까요?", category: "BEHAVIOR", priority: "HIGH" }, parent.accessToken);
  const q2 = await call("POST", "/api/questions", { title: "초등 저학년 영어, 학원과 집 중 어디가 나을까요", content: "파닉스는 뗐는데 다음 단계를 어떻게 해야 할지 모르겠어요.", category: "LEARNING" }, parent2.accessToken);
  await call("POST", "/api/questions", { title: "말이 늦은 편인데 언어치료를 알아봐야 할까요", content: "30개월인데 단어는 열 개 정도 말해요. 알아듣는 건 잘하는 것 같아요.", category: "DEVELOPMENT", priority: "URGENT" }, parent.accessToken);
  await call("POST", "/api/questions", { title: "태블릿 사용 시간, 하루 얼마가 적당할까요", content: "주말에 영상을 보기 시작하면 끄기가 너무 힘들어요.", category: "TECHNOLOGY" }, parent2.accessToken);
  const a1 = await call("POST", `/api/questions/${q1.id}/answers`, { content: "이 시기 아이들은 감정을 말로 꺼내는 속도보다 몸이 먼저 반응해요. 때린 행동은 단호하게 멈추되, '화가 났구나'처럼 감정에 이름을 붙여 주세요. 그다음 '이럴 땐 이렇게 말해 보자'고 대신할 말을 연습하면 좋아요." }, expert.accessToken);
  await call("POST", `/api/questions/${q1.id}/answers`, { content: "유치원 선생님과 상황을 자세히 공유해 보세요. 어떤 놀이에서 주로 일어나는지 알면 미리 도와줄 수 있어요." }, teacher.accessToken);
  await call("POST", `/api/questions/${q1.id}/answers/${a1.id}/accept`, undefined, parent.accessToken);
  await call("POST", `/api/questions/${q2.id}/answers`, { content: "파닉스 다음에는 쉬운 리더스 북을 매일 소리 내어 읽는 게 가장 효과가 좋았어요. 학원은 아이가 읽기에 자신감이 붙은 다음에 고민해도 늦지 않아요." }, teacher.accessToken);

  const events = [
    [expert, { title: "초등 입학 준비 설명회", description: "입학 전 꼭 알아야 할 학교 생활, 준비물, 첫 학기 적응법을 현직 교사와 전문가가 함께 알려 드려요. 질의응답 시간이 있어요.", type: "SEMINAR", startDate: at(21, 14), endDate: at(21, 16), registrationEndDate: at(19, 18), location: "서울 마포구 티스푼 라운지 3층", maxParticipants: 40, requirements: "예비 초등학생 학부모", contactInfo: "events@tspoon.local" }],
    [teacher, { title: "부모와 함께하는 주방 과학 실험", description: "식초와 베이킹소다, 양배추 지시약으로 산과 염기를 알아봐요. 실험 재료는 모두 준비해 드려요.", type: "WORKSHOP", startDate: at(9, 10), endDate: at(9, 12), registrationEndDate: at(7, 23, 59), location: "경기 성남시 분당구 청소년수련관", maxParticipants: 12, requirements: "초등 1~3학년과 보호자 1명" }],
    [expert, { title: "사춘기 자녀와 대화하는 법", description: "말수가 줄어든 아이와 다시 이야기를 시작하는 방법을 사례와 함께 나눠요. 온라인으로 진행해요.", type: "WEBINAR", startDate: at(5, 20), endDate: at(5, 21, 30), onlineLink: "https://meet.tspoon.local/talk", contactInfo: "events@tspoon.local" }],
    [teacher, { title: "겨울방학 코딩 캠프", description: "블록 코딩으로 나만의 게임을 만들어 보는 3일 캠프예요. 마지막 날 가족을 초대해 발표회를 열어요.", type: "CAMP", startDate: at(64, 10), endDate: at(66, 16), registrationEndDate: at(50, 18), location: "서울 강남구 티스푼 교육센터", maxParticipants: 20, requirements: "초등 3~6학년" }],
    [teacher, { title: "동네 육아 모임, 그림책 바꿔 읽기", description: "집에 있는 그림책을 두세 권 가져와 서로 바꿔 읽어요. 아이들은 옆방에서 함께 놀아요.", type: "MEETUP", startDate: at(3, 10, 30), endDate: at(3, 12), location: "서울 은평구 구립도서관 세미나실", maxParticipants: 8 }],
  ];
  const eventIds = [];
  for (const [organizer, body] of events) eventIds.push((await call("POST", "/api/events", body, organizer.accessToken)).id);
  await call("POST", `/api/events/${eventIds[1]}/registrations`, { notes: "아이 1명(초2)과 함께 가요" }, parent.accessToken);
  await call("POST", `/api/events/${eventIds[2]}/registrations`, {}, parent2.accessToken);
  await call("POST", `/api/events/${eventIds[4]}/registrations`, {}, parent2.accessToken);
  await call("POST", `/api/events/${eventIds[4]}/registrations`, {}, expert.accessToken);

  console.log("데모 데이터를 넣었어요.");
  console.log(`  학부모  parent@tspoon.local / ${PASSWORD}`);
  console.log(`  교사    teacher@tspoon.local / ${PASSWORD}`);
  console.log(`  전문가  expert@tspoon.local / ${PASSWORD}`);
}

main().catch((e) => {
  console.error(e.message);
  process.exit(1);
});
