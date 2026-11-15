# 하트깜빡이 (HeartBlink)

연인의 차에 목소리 메시지를 숨겨 두면, 상대가 와이퍼를 켜거나 집에 도착하는 순간 차에서 열리는 커플 차량 앱입니다.

상대가 운전 중이면 홈 화면의 하트와 상대 차의 하트 후미등이 깜빡이고, 정해 둔 순간(와이퍼를 켤 때, 회사에 도착했을 때, 아침 첫 시동 등)에 상대가 남긴 목소리 메시지가 열립니다. 둘이 함께 답하는 오늘의 질문과 기록도 담았습니다.

## APK

- 위치: [`apk/경영과컴퓨터_하트깜빡이_release.apk`](apk/경영과컴퓨터_하트깜빡이_release.apk)
- release 빌드이며, 실차(arm64-v8a)와 Pleos Connect 에뮬레이터(x86_64)용 라이브러리를 담았습니다.
- 서버(Supabase) 주소가 들어 있어 설치만 하면 바로 씁니다.

## 시연 영상

- [`docs/heartblink_demo.mp4`](docs/heartblink_demo.mp4) (2분 18초, Pleos Connect 에뮬레이터 화면 녹화)
- 에뮬레이터 화면 녹화에는 소리가 담기지 않아, 차가 말하는 안내(Pleos TextToSpeech)는 자막으로 보여 주고 메시지 목소리는 앱이 재생한 녹음 파일을 그대로 넣었습니다.

## 주요 기능

| 기능 | 내용 |
|---|---|
| 순간에 열리는 메시지 | 장소, 차량 기능(와이퍼, 전조등, 열선 시트, 에어컨, 창문), 그날 첫 시동, 도착(길안내 목적지, 고른 곳에 주차), 시간대 중 하나를 조건으로 남깁니다. 모든 조건에 "언제부터"를 정할 수 있어 기념일 메시지를 미리 남겨 둘 수 있습니다. |
| 주행 중 안전한 순간 | 주행 중에는 화면이 잠기고, 내비 안내 음성, 300m 안의 회전, 속도 급변, 큰 핸들 조작, 방향지시등, 브레이크가 없는 순간을 기다렸다가 "지금 들을까요, 주차하고 볼까요?"라고 먼저 묻습니다. |
| 오늘의 질문 | 하루에 하나(새벽 4시 기준), 둘 다 답해야 서로의 답이 열립니다. 주행 중에는 목소리로 답합니다. |
| 함께 달리는 중 | 상대가 운전 중이면 하트가 깜빡이고, 둘 다 운전 중이면 음성으로 알려 줍니다. |
| 기록 | 남긴 메시지, 받은 메시지, 오늘의 질문을 모아 봅니다. |

## 실행 방법

1. Pleos Connect 에뮬레이터를 켜고 APK를 설치합니다.

   ```bash
   adb install -r apk/경영과컴퓨터_하트깜빡이_release.apk
   ```

2. 앱 목록에서 하트깜빡이를 엽니다. 처음 실행할 때 마이크, 차량 속도 읽기, 위치 권한을 허용합니다.
3. 01 화면에서 이름과 차량 번호를 등록하고, 연인의 차량 번호로 연결 요청을 보냅니다.
   - 차가 한 대뿐이면 `tools/partner-sim`(연인 역할 시뮬레이터)으로 상대 차를 대신합니다. 사용법은 [tools/partner-sim/README.md](tools/partner-sim/README.md)에 있습니다.
4. 차량 신호는 에뮬레이터의 Virtual Console로 넣습니다(`adb forward tcp:8088 tcp:8088` 후 http://127.0.0.1:8088/). 기어(P, D), 와이퍼, 전조등, 핸들, 방향지시등 등을 바꾸면 메시지가 열리는 모습을 볼 수 있습니다.

## 테스트 환경

| 항목 | 값 |
|---|---|
| 에뮬레이터 | Pleos Connect Emulator, 시스템 이미지 "Pleos Connect v2.1.0 APIs" (Android 14, API 34, x86_64) |
| Android Emulator | 37.2.12.0 |
| 화면 규격 | 디스플레이 2560 x 1440 (160 dpi), 앱 창 1682 x 1210 px (왼쪽은 시스템 드라이빙 뷰) |
| 최소, 대상 SDK | minSdk 28, targetSdk 35 |
| Pleos SDK | Vehicle 2.3.2, NaviHelper 3.0.5, FusedLocation 1.1.3, TextToSpeech 3.3.1, SpeechToText 3.3.1 |
| 차량 속성 | `PERF_VEHICLE_SPEED` (권한 `android.car.permission.CAR_SPEED`) |
| 서버 | Supabase (익명 로그인, 행 수준 보안, RPC, Realtime, Storage) |

## 직접 빌드할 때 필요한 설정

1. Supabase 프로젝트를 만들고 익명 로그인을 켭니다.
2. SQL Editor에서 `supabase/migrations`의 파일을 이름 순서대로 실행한 뒤 `supabase/seed.sql`을 실행합니다.
3. `local.properties`에 `SUPABASE_URL`, `SUPABASE_ANON_KEY`를 넣습니다(저장소에는 올리지 않습니다).
4. JDK 21로 빌드합니다(코드 대상은 Java 17).

```bash
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

## 구성

| 폴더 | 내용 |
|---|---|
| `app/src/main/java/kr/heartblink/car/app` | 차량 신호(`CarSignals`), 내비(`NaviWatch`), 메시지 열기(`MessageOpener`), 주행 중 음성 대화(`DriveTalk`), 로그인과 커플 상태(`Session`) |
| `app/src/main/java/kr/heartblink/car/data` | Supabase 연결과 테이블 모델 |
| `app/src/main/java/kr/heartblink/car/ui/theme` | 피그마 변수(색 13개, 두 모드)와 글자 스타일, 피그마 픽셀 단위 배치(`DesignScale`) |
| `app/src/main/java/kr/heartblink/car/ui/component` | 공통 부품: 헤더, 툴바, 버튼, 입력칸, 칩, 손그림 차, 그림자, 휠 피커 |
| `app/src/main/java/kr/heartblink/car/ui/screen` | 피그마 "하트깜빡이" 페이지의 화면 01~24 |
| `app/src/main/assets/figma` | 피그마에서 내보낸 손그림 SVG |
| `app/src/main/res/font` | Noto Sans KR, Jua, Gaegu (SIL Open Font License, `licenses/`) |
| `supabase` | 데이터베이스 구조, 함수, 질문 목록 |
| `tools/partner-sim` | 연인 차 역할을 하는 명령줄 시뮬레이터 |

## 화면 바로 열기 (debug 빌드)

화면 번호는 피그마 화면 이름 앞의 번호와 같습니다.

```bash
adb shell am start -n kr.heartblink.car/.MainActivity --es screen 05
```

## 화면 크기

Pleos 차량 앱 창은 1682 x 1210 px입니다. `DesignScale`이 창 너비에 맞춰 1dp를 피그마 1px로 맞추므로, 화면 코드는 피그마 수치를 그대로 씁니다.
