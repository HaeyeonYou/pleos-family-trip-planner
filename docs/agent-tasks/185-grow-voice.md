# 185 그로우 음성 명령

이슈 #185 · 브랜치 `feature/185-grow-voice` · 담당: Claude Code 세션(grow-voice)

## 1. 목표와 완료 조건

그로우(경험 우주) 화면에서 음성으로 기간·분류를 거르고, 다시 갈 곳을 고르고, 다녀온 곳으로 다시 안내받는다.

- 서버 `/voice/interpret`가 그로우 의도 3개를 돌려준다. Haiku가 실패하면 그로우 규칙으로 해석한다.
- 앱이 의도를 받아 그로우 탭으로 가서 거르기·팝업·안내를 한다. 실제 방문 기록(VisitStore)으로 계산한다.
- 그로우 음성 줄 예시를 G01 문구 "지난달 간 곳 보여줘"로 되돌린다.
- 주행 중: 버튼은 쓰고 키보드만 막는다(입력창은 주차 중에만, 기존 VoicePanel 그대로). 안내를 시작하는 명령은 D01 1곳 카드("좋아 · 안내"/"아니요", 8초)로 되묻는다.
- 서버·앱 단위 테스트, 에뮬레이터에서 실제 방문 기록으로 확인.

## 2. 지원 명령 (결정)

| 동작 | 말 예시 | 인자 | 앱이 하는 일 |
|---|---|---|---|
| `grow_show` | "지난달 간 곳 보여줘", "이번 달 어디 갔어?", "자연 간 곳 보여줘", "공룡 별 보여줘", "지난달 물놀이 간 곳", "경험 우주 전체 보여줘" | `period`: this_month / last_month (없으면 전체 기간), `target`: 12분류(art, science, nature, animal, water, move, music, book, food, friend, make, travel) 또는 없음 | 그로우 탭 → 기간·분류 거르기. 분류가 있으면 분류 팝업. 답: 그 기간·분류에 다녀온 곳 수와 이름(최대 3곳), 분류면 성장 단계 |
| `grow_revisit` | "다시 가볼 곳 알려줘", "또 갈 만한 데 있어?" | 없음 | 그로우 탭. 정차 중: "다시 가볼까요?" 후보 중 좌표가 있는 2곳을 번호 후보로("1번으로 가줘" → 목적지 안내). 주행 중: 1위 1곳 D01 카드 |
| `grow_go` | "○○ 다시 가자", "○○ 또 가고 싶어" | `place`: 화면 상태로 보낸 다녀온 곳 이름 중 하나 | 그로우 탭에서 장소 팝업. 정차 중: 바로 목적지 안내. 주행 중: D01 카드로 되묻기. 좌표가 없고 저장 코스가 있으면 코스 화면 |

- 기간은 이번 달·지난달만. "작년", "3월" 같은 다른 기간은 unknown(되묻기)으로 둔다.
- 아이별 거르기: 방문 기록(Visit)에 아이 구분이 없어 하지 않는다. "민준이가 간 곳"은 아이 이름을 무시하고 전체로 본다.
- 장소 이름: 앱이 `screen.places`(최근 순 다녀온 곳 최대 30개)를 보내고, 서버는 그 목록에 있는 이름만 받는다(없는 이름은 unknown).
- 분류 판정은 `grow/JellyModel.kt`의 `experiences()`/`classify()`만 쓴다(서버 12분류 이전은 다른 세션 몫).
- 서버 규칙 해석(source="rule")은 그로우 의도만 다룬다. 다른 동작은 지금처럼 fallback(unknown).

## 3. 범위와 파일

- 서버: `server/app/voice.py`, 새 `server/app/voice_rules.py`, `server/tests/test_voice.py`(+ 저장본 응답)
- 앱: `voice/VoiceIntent.kt`·`VoiceController.kt`·`VoiceHost.kt`·`DriveProposal.kt`·`VoiceCommand.kt`(예시 문장), 새 `grow/GrowVoice.kt`, `data/TripApi.kt`(VoiceScreen·VoiceInterpretation 필드), `ui/ExperienceScreen.kt`·`ExperienceUniverse.kt`(거르기), `ui/DrivingVoiceCard.kt`, `MainActivity.kt`(VoiceHost 구현에 최소 추가)
- 건드리지 않음: `JellyModel.classify` 규칙, 서버 family.py·온보딩

## 4. 참고

- G01 디자인 `docs/agent-tasks/164-grow-g01.md`, D01 `docs/agent-tasks/drive-voice-d01.md`
- 음성 흐름 #70 (`server/app/voice.py` 머리말)

## 5. 진행 로그

| 날짜 | 한 일 | 다음 |
|---|---|---|
| 2026-11-11 | 이슈·브랜치·지시서, 명령 목록 결정 | 서버 → 앱 → 에뮬레이터 |
| 2026-11-11 | 서버: `voice.py`에 grow_show·grow_revisit·grow_go, `screen.places`(최대 30개·60자), 장소는 목록 안 이름만(앞부분만 말해도 맞춤), `voice_rules.py` 규칙 해석(Haiku 실패 시 source="rule"). 실제 Haiku 응답 저장본 6건 + 검증·규칙 테스트, 서버 210개 통과. 실제 호출 1.2~1.9초 | 앱 |
| 2026-11-11 | 앱: `grow/GrowVoice.kt`(답 문장·후보·장소 목록), Step.GrowShow/GrowRevisit/GrowGo, `GrowVoiceBus`로 그로우 화면 거르기·팝업, GrowFilter를 분류+달 조합으로, "지난달" 칩(음성으로 골랐을 때), D01 카드에 목적지 안내(`destination`, "좋아 · 안내", 젤리 아이콘), 음성 후보 고르면 목적지 안내. 음성 줄 예시 G01 "지난달 간 곳 보여줘". 로컬 규칙의 "이번 달"이 2번 고르기로 잡히던 버그 수정. 단위 테스트 256개 통과 | 에뮬레이터 |
| 2026-11-11 | 에뮬레이터(`.growvoice`, 이 브랜치 서버 8185, 방문 27건 = G01 세션 기록 22건 + 실제 장소 좌표 5건): 정차 중 "지난달 간 곳 보여줘" → 그로우 탭·지난달 칩·"지난달에는 14곳…", "다시 가볼 곳 알려줘" → 후보 2곳 → 1번 → K현대미술관 경로 요청·내비 화면. 주행(D) 중 "다시 가볼 곳" → D01 카드 "2주 전에 간 K현대미술관이에요. 다시 안내할까요?" 확인 후 거절, 기어 P로 되돌림. 화면 `docs/screenshots/185-grow-voice-*.png` | PR 병합 |

## 6. 확인하지 못한 것

- "○○ 다시 가자"(grow_go)는 서버(실제 Haiku·규칙)와 앱 매핑 단위 테스트로만 확인했다. 에뮬레이터에 한글 입력 수단이 없고 문장 버튼에도 장소 이름이 없어 앱 화면 흐름(장소 팝업 → 안내)은 보지 못했다. 같은 안내·D01 코드를 "다시 가볼 곳"에서 확인했다.
- 실제 음성(STT)으로 말해 보지는 못했다(에뮬레이터 마이크 없음). 문장 버튼이 같은 `submit`을 탄다.
- D01 "좋아 · 안내"를 눌러 주행 중 안내로 바꾸는 것은 누르지 않았다(정차 중 같은 `routeTo`는 확인).
- 확인 중 정차 상태에서 시작된 K현대미술관 안내가 공유 에뮬레이터 내비에 남아 있을 수 있다.
- 빌드 주의: `-PserverUrl`을 바꿔 다시 빌드할 때 증분 dex에 옛 BuildConfig가 남아 옛 주소로 요청한 일이 있었다. 주소를 바꾸면 `clean` 후 빌드한다.
