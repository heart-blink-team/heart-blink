# 하트깜빡이 (HeartBlink)

연인 두 사람의 차를 이어 주는 Pleos Connect 차량 앱입니다. 상대가 운전 중이면 홈 화면의 하트와 상대 차의 하트 후미등이 깜빡이고, 정해 둔 순간(와이퍼를 켤 때, 회사에 도착했을 때, 아침 첫 시동 등)에 상대가 남긴 목소리 메시지가 열립니다. 둘이 함께 답하는 오늘의 질문과 기록도 담았습니다.

## 구성

| 폴더 | 내용 |
|---|---|
| `app/src/main/java/kr/heartblink/car/ui/theme` | 피그마 변수(색 13개, 두 모드)와 글자 스타일, 피그마 픽셀 단위 배치(`DesignScale`) |
| `app/src/main/java/kr/heartblink/car/ui/component` | 공통 부품: 헤더, 툴바, 버튼, 입력칸, 칩, 손그림 차, 그림자 |
| `app/src/main/java/kr/heartblink/car/ui/screen` | 피그마 "하트깜빡이" 페이지의 화면 01~24 |
| `app/src/main/assets/figma` | 피그마에서 내보낸 손그림 SVG |
| `app/src/main/res/font` | Noto Sans KR, Jua, Gaegu (SIL Open Font License, `licenses/`) |

## 빌드

- JDK 21로 Gradle을 실행합니다(코드 대상은 Java 17).
- Android SDK 35, Pleos Connect 에뮬레이터 또는 실차에서 실행합니다.

```bash
./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 화면 바로 열기

화면 번호는 피그마 화면 이름 앞의 번호와 같습니다.

```bash
adb shell am start -n kr.heartblink.car/.MainActivity --es screen 05
```

## 화면 크기

Pleos 차량 앱 창은 1682 x 1210 px입니다. `DesignScale`이 창 너비에 맞춰 1dp를 피그마 1px로 맞추므로, 화면 코드는 피그마 수치를 그대로 씁니다.
