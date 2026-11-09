# 서버 API 계약: 가족 정보·수유 기록·방문 기록·공조 프리셋 3개 (이슈 #109)

폰 웹(쿠키)과 차 앱(`Authorization: Bearer <차 로그인 토큰>`)이 같은 API를 쓴다. 인증이 없으면 401. 모든 경로는 서버 주소 아래(예: `http://10.0.2.2:8000`). 요청 본문 JSON은 모르는 필드를 무시하고, 응답에 필드가 늘어도 앱이 깨지지 않게 `ignoreUnknownKeys = true`를 유지하면 된다(현재 앱 설정과 같다).

서버·웹은 PR #111~#113에서 만들었고, 차 앱(`android/`)의 연결은 이슈 #123에서 구현했다. 아래 6절에 구현 상태와 결정을 적었다.

## 1. 공통 규칙
- **id는 클라이언트가 정하는 UUID**로 보낼 수 있는 곳(`PUT /me/.../{id}`)이 있다. 같은 id로 다시 보내면 덮어쓸 뿐이라, 차가 오프라인이었다가 재전송해도 중복되지 않는다.
- 남의 데이터 id를 보내면 404 (존재 여부도 알려 주지 않는다).
- 시각은 시간대를 포함한 ISO 8601(예: `2026-11-08T12:10:00+09:00`). 날짜는 `YYYY-MM-DD`, 태어난 달은 `YYYY-MM`.
- 계정을 지우면 아래 데이터가 모두 함께 지워진다(DB 연쇄 삭제).

## 2. 가족 정보
```
GET    /me/family                    → {"children":[ChildOut...], "members":[MemberOut...]}
PUT    /me/children/{child_id}       ChildIn → ChildOut      (만들기·고치기, 아이 6명까지, 넘으면 409)
DELETE /me/children/{child_id}       204   (그 아이의 수유 기록도 삭제)
PUT    /me/members/{member_id}       MemberIn → MemberOut    (12명까지)
DELETE /me/members/{member_id}       204
```
```json
// ChildIn (필수는 nickname, birth_month. 나머지는 모두 선택값이라 예전 앱의 요청도 그대로 받는다)
{"nickname":"서아","birth_month":"2025-09","feeding_type":"mixed","feed_interval_minutes":180,"nap_count":2,
 "temperament":["animal","active"],"nap_times":"10:30–12:00 · 15:00–16:00","nap_in_car":true,
 "heat_sensitivity":"hot","motion_sickness":false,"noise_sensitivity":"sensitive","stroller_use":true}
// ChildOut = ChildIn + {"id":"…","age_months":14}
// feeding_type: breast(모유) | formula(분유) | mixed(혼합) | solid(이유식 후기) | toddler(완료기·유아식)
// feed_interval_minutes 60~360, nap_count 0~4
// temperament(성향, 중복은 합침): animal | active | quiet | shy | vehicle | music
// heat_sensitivity: hot | normal | cold (공조 권장 온도 ±1℃) · noise_sensitivity: sensitive | normal (낮잠 권장 음량)
// motion_sickness(멀미 → 코스 편도 이동 시간 20분으로 시작) · stroller_use(유모차 → 코스 조건 '유모차 길')
// 알레르기·식이 제한 같은 건강 정보는 받지 않는다. 알 수 없는 값은 422
// MemberIn
{"label":"엄마","is_driver":true,"is_default":true}   // is_default=false면 '자주 함께'
```
- 앱의 `FamilyProfile`(`Child(birthYear, birthMonth, name)`)은 `nickname`·`birth_month`로 그대로 옮길 수 있다. 서버에 아이가 생기면 차 앱은 로그인 상태에서 서버 값을 우선해도 된다.

## 3. 수유 기록
```
PUT    /me/feedings/{id}      FeedingIn → FeedingOut   (id를 정해 보내면 멱등)
POST   /me/feedings           FeedingIn → FeedingOut   (201, 서버가 id를 만듦)
GET    /me/feedings?child_id=&since=&limit=50          (최신순, 최대 200)
DELETE /me/feedings/{id}      204
GET    /me/feedings/status?child_id=                   → 다음 수유 시각
```
```json
// FeedingIn: fed_at 필수 (30일 이내, 지금+10분 이내). child_id를 비우면 첫째 아이
{"fed_at":"2026-11-08T12:10:00+09:00","kind":"formula","amount_ml":160,"source":"car"}
// kind: formula(분유)|breast(모유)|solid(이유식·간식), source: web|car
// status 응답
{"child_id":"…","nickname":"서아","interval_minutes":180,"last_fed_at":"2026-11-08T12:10:00+09:00",
 "next_due_at":"2026-11-08T15:10:00+09:00","minutes_left":142,"overdue":false}
```
- 차 음성 "방금 수유했어"는 `PUT /me/feedings/{새 UUID}`에 `fed_at=지금`, `kind`는 아이의 `feeding_type`(없으면 `formula`), `source="car"`로 보낸다. 웹 홈·케어가 `status`로 다음 수유를 보여 주고 차 앱도 같은 값을 쓰면 양쪽이 동기화된다.
- 수유 간격이 아이 정보에 없으면 서버는 180분으로 계산한다.

## 4. 방문 기록 (정책 변경: 차 안에만 → 서버)
개인정보 최소화: **장소 키·장소 이름·분야·날짜·머문 시간·아이 기분**만 받는다. 위치 좌표, 도착·출발 시각, 아이 음성, 사진은 받지 않는다(보내도 저장하지 않는다).
```
PUT    /me/visits/{id}        VisitIn → VisitOut
POST   /me/visits/bulk        {"visits":[VisitIn+id?...]} → {"saved":N}   (최대 200건, 처음 연결할 때 일괄 업로드, id가 같으면 덮어씀)
GET    /me/visits?status=&limit=50
PATCH  /me/visits/{id}        {"status":"confirmed","mood":"excited"}   (방문 확인·기분)
DELETE /me/visits/{id}  /  DELETE /me/visits (전체)
GET    /me/visits/summary     → [{"place_key","theme","date"}]   (확정된 방문, 최근 200건)
GET    /me/experience-map     → 경험맵 (웹 그로우 화면이 쓴다)
```
```json
// VisitIn
{"place_key":"tour:126508","place_name":"서울숲","theme":"nature","visited_on":"2026-11-05",
 "stayed_minutes":180,"status":"confirmed","mood":null}
// theme: nature|experience|exhibition|play|pet (app/schemas.py Theme)
// status: confirmed(확정, 기본) | pending(다녀왔는지 확인 대기) | dismissed(안 갔어요)
// mood: excited(신났어요)|normal(평범)|sleepy(졸려요)|bad(별로)
```
- **PR #90과의 관계**: 코스 추천 요청의 `visits`(`VisitSummary`: place_key·theme·date)는 그대로 둔다. `GET /me/visits/summary`가 **정확히 그 모양**이라, 차 앱은 서버 요약을 받아 `POST /course/suggest`의 `visits`에 넣거나 지금처럼 기기 값을 넣어도 된다. 서버 쪽 추천 로직은 바뀐 것이 없다.
- 장소 이름은 공공 장소 이름이다(개인을 알 수 있는 정보가 아니다). 웹 경험맵에서 장소를 보여 주려면 필요해서 받는다.

## 5. 공조 프리셋 3개와 미디어 음량 (하위 호환)
슬롯 3개: `boarding`(탑승 준비) · `nap`(낮잠) · `feeding_stop`(수유 정차).
```
GET  /presets                 → [PresetState × 3]   (슬롯 순서 고정, 보낸 적 없으면 sent=false에 기본값)
PUT  /presets/{slot}?applied_on_car=false   ClimateSettings → PresetState
GET  /presets/pending         → (예전 앱용) 슬롯에 상관없이 미적용 프리셋 중 가장 최근에 보낸 것 {id, settings, sent_at, slot}, 없으면 204
POST /presets/{id}/applied    → 204   (슬롯과 상관없이 id로 적용 완료 표시)   [변경 없음]
```
```json
// ClimateSettings: 기존 필드 + media_volume(0~100 %, null이면 음량은 건드리지 않음)
{"name":"낮잠","power":true,"temperature":23.0,"fan_speed":1,"ac":false,"air_cleaner":false,
 "driver_seat_heat":0,"passenger_seat_heat":0,"steering_wheel_heat":0,"media_volume":20}
// PresetState
{"slot":"nap","label":"낮잠","when":"수면 모드와 함께","sent":true,"id":"…","settings":{…},
 "sent_at":"…","applied_at":null,"pending":true}
```
- **하위 호환**: ① `GET /presets/pending`과 `POST /presets/{id}/applied`는 예전 앱이 그대로 쓸 수 있다. `pending`은 낮잠·수유 정차로 보낸 것도 (가장 최근에 보낸 순으로) 주고, 응답에 선택 필드 `slot`만 늘었다. ② 예전 웹 폼(`/m/preset`)과 예전 앱이 쓰던 1개는 `boarding` 슬롯이다(기존 DB 행도 `boarding`으로 이어진다). ③ `settings`에 `media_volume`이 늘었지만 앱이 `ignoreUnknownKeys=true`라 파싱이 깨지지 않는다. ④ 새 필드는 모두 선택값이다.
- 기본값: 탑승 준비 24℃·풍량 2·음량 30, 낮잠 23℃·풍량 1·음량 20, 수유 정차 24℃·풍량 1·음량 10.
- 차에서 정차 중 직접 고쳐 저장하는 것은 `PUT /presets/{slot}?applied_on_car=true` (이미 차에 적용된 값이라 대기로 남지 않는다). 마지막 수정이 이긴다.

## 5-1. 주기적 동기화 (`GET /me/sync`)
폰 웹과 차 앱이 서로의 변경을 몇 초 안에 알아채게 하는 가벼운 확인이다. 내용은 주지 않고 영역별 **변경 표시**만 준다. 쿠키(웹)와 Bearer(차) 모두 쓴다.
```
GET /me/sync → {"presets":"…","feedings":"…","visits":"…","family":"…","cars":"…","all":"…"}   (12자 표시, Cache-Control: no-store)
```
- 표시는 내용을 해시한 것이라 **고치거나 지워도 바뀌고**, 두 기기의 시계가 달라도 상관없다. 아무것도 안 바뀌면 `all`이 같다.
- 쓰는 법: 앱이 켜져 있는 동안 5~10초마다 부르고, 이전 값과 `all`이 다르면 달라진 영역만 다시 받는다 — `presets`→`GET /presets`(또는 `/presets/pending`), `feedings`→`GET /me/feedings/status`, `visits`→`GET /me/visits`, `family`→`GET /me/family`, `cars`→(웹이 차 연결을 끊으면 이 차의 토큰이 만료되어 **401**이 온다 → 로그인 화면으로).
- 폰 웹은 로그인 화면을 보는 동안 6초마다 확인하고, 바뀌면 화면을 새로 연다(입력 중이면 미룸, 화면이 보이지 않을 땐 쉼). 웹에서 프리셋을 보내면 차 앱이 이 확인으로 몇 초 안에 받고, 차에서 적용하면 웹의 '차에서 적용했어요'가 몇 초 안에 바뀐다.

## 6. 안드로이드 쪽 구현 상태와 결정 (이슈 #123)
공통: 모두 차 로그인 토큰(Bearer)으로 부르고, 로그인하지 않으면 기기에만 저장한다. 올리지 못한 것은 **안 올라간 것을 기억해 두었다가** 다음 주기(7초)에 다시 올린다. 반영 지점은 `ServerSync`(`data/ServerSync.kt`)이고, 영역별 규칙은 `VisitSync`·`FeedingSync`·`PresetSync`·`FamilySync`다. `SyncTracker`는 영역별로 "반영을 마친 표시"만 기억해, 일부 영역이 실패해도 성공한 영역은 다시 받지 않는다.

0. **주기적 확인 — 구현됨(#120)**: 차 앱이 화면에 보이는 동안 7초마다 `/me/sync`를 확인한다(`TripViewModel.runSyncLoop`). 401이면 로그인 정보를 지우고 로그인 화면으로 보낸다.
1. **방문 기록 — 구현됨**: 나들이(`role=outing`) 방문만 올린다(식사·카페는 올리지 않는다). 변환은 `VisitMapper`: `placeName→place_name`, `placeKey→place_key`(80자), `theme→theme`(서버가 모르는 분야는 null), `arrivedAt` 날짜→`visited_on`, `departedAt−arrivedAt`→`stayed_minutes`, id=`UUID.nameUUIDFromBytes("visit:$id")`. 좌표·주소·시각은 올리지 않는다. 방문이 생기거나 떠남이 기록되면(`VisitStore.revision`) 바로, 로그인 직후에는 쌓인 것을 `POST /me/visits/bulk`(200건씩, 하나뿐이면 `PUT`)로 올린다.
   - **웹에서 지운 기록을 되살리지 않는 규칙**: 서버에 올라간 적이 있는 방문(차가 기억하는 `known` 목록에 있음)이 서버 목록에서 사라졌다면 **웹에서 지운 것**이라 차에서도 지운다. 올라간 적이 없는 방문만 올린다. 서버 목록이 상한(500건)만큼 와서 잘렸을 수 있으면 "없다"는 판단은 하지 않는다. 웹에서 '안 갔어요'(dismissed)·확인 대기(pending)로 바꾼 방문은 차 목록에서 뺀다. 서버에만 있는 확정 방문(웹에서 확인한 것, 다른 차에서 올린 것)은 차에 들인다(도착 시각은 그날 정오로 둔다). 차 기록 상한으로 밀려난 옛 기록은 다시 들이지 않는다.
   - 머문 시간을 다시 올릴 때 서버의 상태·기분을 함께 보낸다(`PUT`이 상태·기분을 덮어쓰므로, 웹에서 정한 기분이 사라지지 않게).
   - 같은 가족 계정으로 다른 계정이 로그인하면 서버와 맞춘 기록(`known`·서버 아이 id)을 비운다. 차 기기에 쌓인 방문 자체는 지우지 않는다.
2. **수유 기록 — 구현됨**: "지금 수유했어요"(버튼·음성)는 차에 저장하고 `PUT /me/feedings/{UUID}`(`fed_at`=지금, `kind`=첫째 아이 수유 방식(혼합·모르면 분유), `source=car`). id는 수유 시각에서 만든 UUID라 다시 올려도 하나만 남는다. 로그인 전·오프라인에서 한 기록은 대기열에 쌓였다가 로그인하면 올라간다. 서버가 거절한 기록(4xx, 30일 가까이 지난 것)은 버린다. `feedings` 표시가 바뀌면 `GET /me/feedings/status`로 마지막 수유를 받아 홈·케어 카드에 반영한다(아직 못 올린 기록이 더 늦으면 그것을 쓴다).
3. **공조 프리셋 3개 — 구현됨**: `GET /presets`로 3칸을 받아 케어 화면에 카드 3개(탑승 준비·낮잠·수유 정차)를 보인다. 폰에서 보낸 대기 칸은 강조된다. '차에 적용'은 공조·미디어 음량을 맞추고 (대기 중이던 것이면) `POST /presets/{id}/applied`로 알린다. 정차 중 '수정' → '차에 적용하고 저장'은 차에 적용한 뒤 `PUT /presets/{slot}?applied_on_car=true`로 저장한다. 알림·저장은 실패하면 대기열에 남아 다시 올린다. 예전 `GET /presets/pending`은 더 부르지 않는다. 베이비케어 모드는 '탑승 준비' 칸이 대기 중일 때 그 값을 쓴다. 차에 하나도 적용되지 않았으면(시동 꺼짐 등) 적용 완료로 알리지 않는다.
   - **미디어 음량**: Vehicle SDK에 음량 제어가 없어 안드로이드 미디어 스트림 음량(`AudioManager`)으로 맞추고, 값이 실제로 바뀌었는지 다시 읽어 확인한다. 에뮬레이터에서는 값이 바뀌지 않아 "바꾸지 못한 항목: 음량"으로 표시된다(실제 차에서의 동작은 확인하지 못했다).
4. **가족 정보 — 구현됨**: 항목 대응과 기준은 다음과 같다.

   | 차 `FamilyProfile` | 서버 | 처리 |
   |---|---|---|
   | 아이 `name` | `nickname` | 같은 항목. 이름이 비어 있으면 "아이 1" 같은 애칭으로 올린다 |
   | 아이 생년월(`birthYear`·`birthMonth`) | `birth_month`(YYYY-MM) | 같은 항목. 서버 아이 정보가 `birth_month`를 필수로 받고 웹이 이미 저장하며, 생년월과 정확한 개월 수는 서로 계산되는 같은 정보라 가족 정보 API로는 생년월을 맞춘다. **코스 요청(`/course`)에는 지금처럼 개월 수만 보낸다(#47 결정 유지)** |
   | 첫째 아이 수유 간격(`FeedingStore`) | `feed_interval_minutes`(첫째) | 같은 항목 |
   | — | `feeding_type`, `nap_count` | 차에 항목이 없어 차는 건드리지 않고 서버 값을 그대로 지킨다(수유 방식은 수유 기록 종류를 정하는 데 읽는다) |
   | `adults`, `petAlong` | (없음) | 서버에 대응 항목이 없어 **기기에만** 둔다. 서버 `members`는 호칭 목록이라 성인 수로 쓰지 않는다 |

   - **기준은 서버**다. 마지막으로 맞춘 서버 상태(스냅샷)와 비교해 **차에서 고친 항목만** 서버에 올리고(`PUT /me/children/{id}`), 나머지는 서버 값을 받는다. 차에서 지운 아이는 서버에서도 지운다(`DELETE`, 그 아이의 수유 기록도 함께 지워진다). 웹에서 지운 아이는 차에서도 빠진다. 올리다 실패하면 서버 값을 받지 않고 멈춰, 차에서 고친 것이 서버 값으로 덮이지 않게 한다.
   - 서버에 같은 달에 태어난 아이가 이미 있으면 새로 만들지 않고 같은 아이로 이어 붙인다. 서버는 아이를 6명까지만 받아 넘는 아이는 올리지 않는다.

## 7. 개인정보·데이터 안내 문구 (폰 웹 · 차 앱 설정 공통 초안)
웹 `/m/privacy`(가입 동의 화면), MY의 삭제 버튼 문구와 **같은 기준**으로 쓴다. 차 앱 설정의 「개인정보·데이터」 카드는 아래 문구를 그대로 가져가면 된다. 실제 서버 동작을 코드로 확인한 내용이다(2026-11-09 기준).

### 7-1. 한눈에 (카드 맨 위 한 줄)
> 로그인하면 아이 정보·수유 기록·방문 기록이 서버에 저장돼 폰과 차에서 같이 보여요. 위치 좌표와 음성은 저장하지 않아요.

로그인하지 않은 차는 아무것도 서버로 보내지 않고 모두 기기에만 둔다(서버 호출이 없다).

### 7-2. 저장 위치 (표)
| 항목 | 차 기기 | 서버(로그인한 경우) |
|---|---|---|
| 생년월일 **일**까지의 원본 | 저장 | 보내지 않음. 서버에는 **태어난 연·월**만 (개월 수 계산용) |
| 아이 애칭·수유 방식·간격·낮잠 | 저장 | 저장 (폰 웹과 같은 값) |
| 아이 성향·더위추위·멀미·소리·유모차 | 저장 | 저장 |
| 기본 외출 멤버(호칭·운전자 여부) | 저장 | 저장 |
| 수유 기록 (시각·종류·양) | 저장 | 저장 |
| 방문 기록 (장소 키·이름·분야·**날짜**·머문 시간(분)·아이 기분) | 저장 | 저장 |
| 공조 프리셋 | 저장 | 저장 |
| 위치 좌표 | 방문 판단에만 사용 | **보내지 않음·저장하지 않음** |
| 방문 시각(시·분), 음성·사진 | 기기에만 | **보내지 않음** |
| 알레르기 같은 건강 정보 | 받지 않음 | 받지 않음 |

### 7-3. 서버로 가는 것 / 가지 않는 것 (문구)
- **서버로 가요**: 아이 애칭과 태어난 연·월(개월 수), 수유 방식·간격·낮잠·성향·더위추위·멀미·소리·유모차, 기본 외출 멤버, 수유 기록, 방문 기록(장소 이름·분야·날짜·머문 시간·아이 기분), 공조 프리셋.
- **서버로 가지 않아요**: 생년월일의 일, 위치 좌표, 방문 시각, 음성, 사진.
- **코스를 찾을 때만**: 현재 위치(좌표)가 그 요청에만 쓰이고 저장하지 않아요. 코스·날씨를 알려 주는 공공 데이터 서비스에도 요청에 담겨 전달돼요.

### 7-4. 지우기 — 실제로 지워지는 범위 (코드 기준)
| 동작 | 서버에서 지워지는 것 | 남는 것 |
|---|---|---|
| 웹 MY **방문 기록 삭제** (`DELETE /me/visits`) | 내 방문 기록 전부 → 경험맵이 비워짐 | 아이·수유·프리셋·계정 |
| 웹 MY **수유 기록 삭제** | 내 수유 기록 전부 | 위와 같음 |
| 아이 삭제 (`DELETE /me/children/{id}`) | 그 아이와 **그 아이의 수유 기록** | 방문 기록·다른 아이 |
| 기록 한 건 삭제 (`DELETE /me/visits/{id}`, `/me/feedings/{id}`) | 그 한 건 | — |
| **계정 삭제** (웹 MY, 비밀번호 재확인) | 계정, 폰·차에 로그인된 **모든 기기**(세션), 아이·외출 멤버, 수유·방문 기록, 공조 프리셋, 차 로그인 요청 | **차 기기 안의 로컬 기록**(생년월일 원본 등)은 그대로. 차 앱은 로그인이 끊긴다(401) |
| 차 앱에서 지우기 | (차 앱이 만들 때) 기기의 로컬 기록 | 서버 기록은 웹에서 지우거나 위 삭제 API를 호출해야 지워짐 |

- 서버 삭제는 되돌릴 수 없다(복구 없음). 웹도 같은 문구를 쓴다: "되돌릴 수 없어요".
- 「지금 N대 가는 중」 집계는 장소 키와 시각만 있고 **계정과 연결되지 않아** 계정을 지워도 집계 숫자에는 남는다.
- 서버 로그에는 요청 경로와 시각이 남는다(좌표·내용은 앱 로그에 남기지 않는다). ⚠ 웹 코스 화면은 위치를 주소(`?lat=&lng=`)로 보내서, 서버를 `uvicorn` 기본 설정(접근 로그 켜짐)으로 띄우면 그 로그에 좌표가 찍힌다. NAS 배포 때 접근 로그를 끄거나 위치를 본문으로 보내도록 고쳐야 문구가 지켜진다(후속 이슈로 남김).

### 7-5. 카드에 바로 쓸 짧은 문구
- 제목: **개인정보·데이터**
- 본문(3줄):
  1. 로그인하면 아이 정보·수유 기록·방문 기록이 서버에 저장돼 폰과 차에서 같이 봐요.
  2. 생년월일의 일, 위치 좌표, 방문 시각, 음성은 서버로 보내지 않아요.
  3. 폰 웹 MY에서 기록을 지우거나 계정을 지울 수 있어요. 차에 저장된 내용은 차에서 지워요.
- 버튼 예: 「폰에서 기록 지우기 안내 보기」(웹 MY 주소 안내), 「이 차의 기록 지우기」(차 앱 로컬)

### 7-6. Figma 문구와 달라지는 점 (디자이너 확인 대상)
Figma 차 앱 HF05(지수 페이지, 노드 `210:1031`)와 다른 곳이다. 모두 사용자 결정(방문 기록 서버 저장, 나이 서버 전송)을 따른 것이라 디자인 문구를 바꿔야 한다.

| Figma 문구 | 바꿀 문구 | 이유 |
|---|---|---|
| 생년월일·방문 기록: 차 안에만 저장 | 방문 기록은 로그인하면 서버에도 저장(7-2) | 방문 기록 서버 저장으로 정책 변경 |
| 서버 전송: "3세 이하 있음" 같은 요약만 | 아이 애칭·태어난 연·월·케어 항목, 수유·방문 기록 전송(7-3) | 폰 웹·경험맵이 실제 값을 보여 줘야 함 |
| (생년월일 원본 언급) | 생년월일의 일은 기기에만, 서버에는 태어난 연·월 | 개월 수 계산에 연·월이 필요 (정확한 나이를 서버로) |
| (위치 언급 없음) | 코스를 찾을 때만 위치가 그 요청에 쓰임 | 서버가 외부 API에 좌표를 전달함. 저장은 안 함 |
