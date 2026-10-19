# 감정단어 (MoodWords) — 작업 안내

Wear OS(갤럭시워치) 앱 + 안드로이드 폰 앱(워치 없는 사람용, 심박 대신 에너지 질문). 워치: 심박 측정 → 질문 3개(에너지 확인, 기분, 무엇 때문에) → 감정 단어와 뜻 → 기록.
사용자는 **폰만으로** 작업함. 컴퓨터·Android Studio 없음. 답변은 한국어, 폰 화면에 맞게 짧게.

## 빌드와 배포 (반드시 이 방식)
- Claude 작업 환경에서는 Google 서버(dl.google.com, maven.google.com)가 막혀 있어 **로컬 빌드 불가**. 시도하지 말 것.
- `main`에 push → GitHub Actions(`.github/workflows/build-apk.yml`)가 서명된 release APK 2개 빌드 → 워치: `build-<N>` 태그(최신 릴리스) `MoodWords.apk`, 폰: `phone-<N>` 태그(pre-release) `MoodWords-phone.apk`. 폰 릴리스를 latest로 만들지 말 것(구버전 워치 앱이 releases/latest를 봄).
- 빌드 확인은 공개 API로: `https://api.github.com/repos/GilbertGo/moodwords/actions/runs` (인증 불필요).
- 빌드 실패 시 원인은 check-run annotations로 확인: `.../check-runs/<job_id>/annotations`. 워크플로가 `e:` 와 `What went wrong` 줄을 주석으로 올리도록 되어 있음.
- 커밋 후 반드시 push.

## 서명 (절대 바꾸지 말 것)
- 키 파일: `signing/release.p12` (alias `moodwords`), 비밀번호는 GitHub Secret `KEYSTORE_PASSWORD`. 비밀번호는 사용자 메일에 보관됨. 코드·채팅에 다시 쓰지 말 것.
- 키를 바꾸면 기존 설치본을 덮어쓸 수 없어 사용자가 앱을 지워야 하고 기록이 사라짐.

## 버전과 앱 내 업데이트
- `versionCode` = `GITHUB_RUN_NUMBER`, `versionName` = `1.<번호>`. 직접 올리지 말 것.
- 업데이트는 **앱을 열 때 안내 창에서만** (사용자 요청으로 백그라운드 자동 설치 꺼짐. `AutoUpdate.cancelBackground`가 예전 예약 작업을 취소). 다시 켜지 말 것. Android 12+에서 `setRequireUserAction(NOT_REQUIRED)` + UPDATE_PACKAGES_WITHOUT_USER_ACTION 권한으로, 앱이 스스로 한 번 설치한 뒤부터는 확인 없이 조용히 업데이트. ADB/Wear Installer로 깐 직후 첫 자체 업데이트만 '설치' 확인 1번.
- 앱을 열면 새 버전 안내 창(🎁 새 버전이 있어요 · 바뀐 점 · 지금→새 · 업데이트/나중에). 바뀐 점은 릴리스 본문 = 커밋 메시지의 '- ' 항목(최대 5개). **커밋 메시지에 사용자에게 보일 짧은 한국어 '- ' 항목을 쓸 것.**
- 따라서 코드를 고치고 push만 하면 사용자는 워치에서 바로 업데이트 가능. Wear Installer 재설치 안내는 서명 문제 등 예외 상황에만.

## 처음 설치 / 재설치가 필요할 때 (Wear Installer 2)
- 워치: 설정 > 개발자 옵션 > ADB 디버깅, 무선 디버깅 켜기.
- Wear Installer 2 IP 칸에는 **연결 포트**(무선 디버깅 첫 화면), 페어링에는 "새 기기 등록"의 **코드 + 페어링 포트**(서로 다른 번호).
- APK는 폰 브라우저로 Releases에서 받기. 소스 zip 폴더와 헷갈리지 않게 안내.

## 코드 구조
- 모듈: `app`(워치), `phone`(폰, Material3), `shared`(단어 사전·기록·업데이트, 패키지 com.example.moodwords)
- `shared/src/main/assets/emotions.json` 감정 단어 1,260개. 필드: w 단어, m 뜻(해요체 한 문장), e HIGH/MID/LOW, v GOOD/NEUTRAL/BAD, c 원인 태그(PEOPLE/WORK/SELF/BODY/ANY), r 흔함 1~3, (선택) s 논문 표기·p 쾌·a 활성화. 단어 추가 시 기존과 중복 금지
- 연구 근거: 박인조·민경환(2005) 434개 평정. 쾌 ≥4.5 GOOD, ≤3.5 BAD / 활성화 ≥4.8 HIGH, ≤3.8 LOW. 친숙성 3 미만 단어는 사전 뜻 기준 위치 유지. 429개 연결, 그중 425개가 점수 그대로 배치(4개는 사전 뜻 유지). 나머지 831개는 AI 분류 — EmotionWord.basis(RESEARCH/RESEARCH_EXCEPTION/AI)로 결과 화면에 반드시 표시, 후보 칩은 연구 단어에 • 홈 3×3 예시 단어는 emotions.json의 실제 칸과 맞출 것
- emotions.json 추가 필드: pm 감성 한 줄(앱 창작), f 감정 갈래 26개(JOY…CONFUSION)
- `shared/src/main/assets/quotes.json` 갈래별 글귀 171개: f 갈래, t 한국어, o 원문, a 저자, k 작품, y 연도, tr 앱 번역 여부. **저작권 만료작만**(1929년 이전 발표·고전·경전·속담). 윤동주·이육사·백석·현대 작가·가사 금지. 출처 확인 안 된 것 넣지 말 것. `Quotes.kt`
- `EmotionWords.kt` JSON 로드, 칸+원인으로 후보 정렬(원인 일치 → 흔한 말 순), 심박→에너지 추정
- `HeartRate.kt` Health Services 심박 Flow (Guava 의존성 필요)
- `Motion.kt` 가속도 센서 0.5초 창: movement(큰 동작 RMS, >0.8 이동 중)·tremor(약 3Hz 고역 RMS). 측정은 가만히 있는 시간 15초(최대 30초), 움직이는 동안 심박 버림. 떨림은 평소값(MoodStore.restingTremor) 대비 1.6배↑ 많음 → 에너지 한 단계 올림. 기록 6번째 필드에 tremor 저장
- `MoodStore.kt` SharedPreferences 기록, 안정 심박 학습
- `MainActivity.kt` 화면: home → measure → energy → valence → cause → result → history
- `Weekly.kt` 주간 요약(최근 7일, 이전 주 이동): 기분·에너지 비율, 많이 고른 감정, 시간대(아침5–11/낮11–17/저녁17–22/밤), 원인, 한 줄 정리(숫자 사실만, 조언 없음).
- **위기·자살 관련 단어, 상담전화 번호를 앱에 넣지 말 것 (사용자 결정). 기록 수치로 사용자의 상태를 판단하는 문구도 금지**
- `Guide.kt` 앱 소개(목적·사용법·요령·좋은 점·알아두세요). 첫 실행 시 표시, 홈에서 다시 보기
- 기록 7번째 필드 = cause
- `Updater.kt` 업데이트 확인·설치 (Channel WATCH/PHONE, PackageInstaller)
- `phone/.../PhoneActivity.kt` 홈(3×3 지도) → 질문 3개 → 결과(근거 표시) → 기록, 연구 근거 화면
