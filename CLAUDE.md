# 감정단어 (MoodWords) — 작업 안내

Wear OS(갤럭시워치) 앱. 심박 측정 → 질문 2개 → 감정 단어와 뜻 → 기록.
사용자는 **폰만으로** 작업함. 컴퓨터·Android Studio 없음. 답변은 한국어, 폰 화면에 맞게 짧게.

## 빌드와 배포 (반드시 이 방식)
- Claude 작업 환경에서는 Google 서버(dl.google.com, maven.google.com)가 막혀 있어 **로컬 빌드 불가**. 시도하지 말 것.
- `main`에 push → GitHub Actions(`.github/workflows/build-apk.yml`)가 서명된 release APK 빌드 → Releases에 `build-<run_number>` 태그로 `MoodWords.apk` 업로드.
- 빌드 확인은 공개 API로: `https://api.github.com/repos/GilbertGo/moodwords/actions/runs` (인증 불필요).
- 빌드 실패 시 원인은 check-run annotations로 확인: `.../check-runs/<job_id>/annotations`. 워크플로가 `e:` 와 `What went wrong` 줄을 주석으로 올리도록 되어 있음.
- 커밋 후 반드시 push.

## 서명 (절대 바꾸지 말 것)
- 키 파일: `signing/release.p12` (alias `moodwords`), 비밀번호는 GitHub Secret `KEYSTORE_PASSWORD`. 비밀번호는 사용자 메일에 보관됨. 코드·채팅에 다시 쓰지 말 것.
- 키를 바꾸면 기존 설치본을 덮어쓸 수 없어 사용자가 앱을 지워야 하고 기록이 사라짐.

## 버전과 앱 내 업데이트
- `versionCode` = `GITHUB_RUN_NUMBER`, `versionName` = `1.<번호>`. 직접 올리지 말 것.
- `Updater.kt`가 앱 실행 시 Releases 최신 태그를 확인 → 홈 화면에 "새 버전 설치" 버튼 → 워치에서 다운로드 후 시스템 설치 확인 1번.
- 따라서 코드를 고치고 push만 하면 사용자는 워치에서 바로 업데이트 가능. Wear Installer 재설치 안내는 서명 문제 등 예외 상황에만.

## 처음 설치 / 재설치가 필요할 때 (Wear Installer 2)
- 워치: 설정 > 개발자 옵션 > ADB 디버깅, 무선 디버깅 켜기.
- Wear Installer 2 IP 칸에는 **연결 포트**(무선 디버깅 첫 화면), 페어링에는 "새 기기 등록"의 **코드 + 페어링 포트**(서로 다른 번호).
- APK는 폰 브라우저로 Releases에서 받기. 소스 zip 폴더와 헷갈리지 않게 안내.

## 코드 구조
- `EmotionWords.kt` 에너지×기분 3×3, 28개 단어와 뜻, 심박→에너지 추정
- `HeartRate.kt` Health Services 심박 Flow (Guava 의존성 필요)
- `MoodStore.kt` SharedPreferences 기록, 안정 심박 학습
- `MainActivity.kt` 화면: home → measure → energy → valence → result → history
- `Updater.kt` 업데이트 확인·설치 (PackageInstaller)
