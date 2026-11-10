# 상대 차 시뮬레이터 (partner-sim)

PC 한 대와 에뮬레이터 하나로 커플 기능을 시험할 때, 이 도구가 **상대방 차** 역할을 합니다.
에뮬레이터 앱이 내 차, 이 CLI가 연인의 차입니다.

## 준비

1. Node 22 이상 (Node 24 권장)
2. 프로젝트 맨 위 `local.properties`에 앱과 같은 값을 넣습니다.
   ```
   SUPABASE_URL=https://<프로젝트>.supabase.co
   SUPABASE_ANON_KEY=<anon 키>
   ```
3. 이 폴더에서 `npm install`

처음 명령을 실행하면 익명 사용자로 로그인하고, 세션을 `.session.json`에 저장합니다.
다음 실행부터는 같은 사용자로 이어집니다. 처음부터 다시 하려면 이 파일을 지우세요.
`--session 다른파일.json`을 주면 또 다른 차가 됩니다. (세션 파일은 git에 올라가지 않습니다)

## 명령

| 명령 | 하는 일 |
|---|---|
| `profile <이름> <차번호>` | 이름과 번호판 등록 (예: `profile 민지 34나5678`) |
| `request <차번호>` | 그 번호의 차에 연결 요청 |
| `accept <애칭>` | 받은 요청 수락, 요청한 차 번호판에 붙일 애칭 |
| `decline` | 받은 요청 거절 |
| `nickname <애칭> [first-met YYYY-MM-DD]` | 상대 번호판 애칭, 처음 만난 날 변경 |
| `answer <글> [--audio 파일.m4a]` | 지금 질문에 답하기 |
| `send <조건묶음> <조건값> <글> [옵션]` | 메시지 남기기 |
| `open <메시지 id>` | 받은 메시지를 열고 들은 것으로 표시 |
| `drive on` / `drive off` | 운전 중 표시 |
| `distance <km>` | 오늘 달린 거리 더하기 |
| `status` | 연결 상태, 지금 질문, 상대 답 공개 여부, 안 읽은 메시지 |

`send` 조건묶음과 조건값:

- `vehicle`: wiper, headlight, seat_heater, aircon, window
- `time`: dawn, morning, noon, afternoon, evening, night, 또는 직접 정한 범위 `custom:HHMM-HHMM` (예: `custom:1800-1930`, 자정을 넘기면 `custom:2300-0130`)
- `arrival`: destination, parked
- `first_start`: first_start
- `place`: place (이때 `--lat`, `--lng` 필요, `--place-name`, `--radius`는 선택)

그 밖의 옵션: `--opens-from 2026-11-07T09:00:00+09:00` (이 시각 전에는 열리지 않음),
`--audio 파일.m4a --audio-ms 5200` (음성 첨부와 길이)

## 시험 순서 예시

```bash
# 상대 차 등록
node index.mjs profile 민지 34나5678

# 앱(내 차)에서 34나5678로 연결 요청을 보낸 뒤
node index.mjs status
node index.mjs accept 곰돌이

# 앱에서 질문에 답한 뒤, 상대도 답하면 서로의 답이 열림
node index.mjs answer "처음 봤을 때 웃는 게 예뻤어"

# 와이퍼를 켜면 열리는 메시지
node index.mjs send vehicle wiper "비 오니까 천천히 와"

# 앱의 상대 운전 중 표시 확인
node index.mjs drive on
```

음성 파일은 비공개 버킷 `voice`의 `<couple_id>/<uuid>.m4a`에 올라갑니다 (audio/mp4).
