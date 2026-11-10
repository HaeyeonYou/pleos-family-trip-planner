# 다음 에이전트에게 줄 프롬프트 (2026-11-10 기준)

상태: 5시간·주간 사용량 한도(Claude Pro)로 홈·케어·주행 음성 세션 3개가 중단됐다. 아래를 새 에이전트(Claude Code, Antigravity 등)에 그대로 붙여 넣는다. 한도가 풀리는 시각은 2026-11-10 17:30 UTC(한국 11/11 02:30), 주간은 11/12 11:00 UTC.

```
너는 「오늘, 어디 가지?」(Pleos 차량 앱 공모전, 레포 /Users/kwonstar/Pleos_Application, GitHub HaeyeonYou/pleos-family-trip-planner, private) 개발을 이어받는다. 1차 접수 11/15(제출 목표 11/14), PT 12/3~4(에뮬레이터 시연).

먼저 읽을 것(순서대로): docs/HANDOFF.md → docs/agent-tasks/1110-screen-sessions-plan.md(작업 방식·파일 소유·한도 점검 규칙) → docs/agent-tasks/care-c01.md, home-h01-h02.md, drive-voice-d01.md(각 작업의 상태·진행 로그·확인하지 못한 것) → docs/design/pleos-1110/README.md. 그리고 git log, gh pr list, gh issue list로 현재 상태를 확인한다.

규칙: 사용자에게 보이는 글·커밋·PR·이슈는 모두 한국어. GitHub은 개인 계정 HaeyeonYou만(푸시 전 gh auth status). 이슈 1개 → feature/<번호>-<이름> 브랜치 → PR → 즉시 병합(병합 제목 "PR #N 병합: <제목>", 일부만이면 Refs #N). 키·토큰 값은 출력 금지(루트 .env). 시연용이 아니라 실제 데이터로 동작하게 만든다. 결정은 사용자에게 한 번에 하나씩 묻는다. JDK 17(JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home). 새 코드는 TripColors가 아니라 SitterPalette/SitterSurface/SitterText와 SitterCanvas를 쓴다(docs/design/pleos-1110/07-m1-foundation.md). Figma 호출 한도(Education 플랜)가 소진돼 있을 수 있으니 docs/design/pleos-1110/assets/screens/*.png와 문서를 우선하고, Figma는 읽기 전용으로 꼭 필요할 때만 쓴다. 사용량이 한도에 가까우면(5시간 90%, 주간 95%, 컨텍스트 80%) 완성분은 PR 병합, 미완성은 WIP 푸시, 자기 지시 파일 갱신·커밋 후 멈춘다. 같은 파일을 두 에이전트가 동시에 고치지 않는다(MainActivity.kt, TripViewModel.kt, AppShell.kt, Theme.kt, Common.kt는 최소 추가만).

지금 상태와 해야 할 일(우선순위 순):
1) 케어 C01·C02·C05·C06: 구현·단위 테스트까지 끝났지만 에뮬레이터 확인 전이라 PR 없이 브랜치 feature/150-care-c01-grid(커밋 a73443d, 이슈 #150)에 있다. 그 브랜치를 받아 main을 합치고, 에뮬레이터 캡처를 docs/design/pleos-1110/assets/screens/C01~C06 이미지와 비교해 글자 넘침·크기를 고친 뒤 PR로 병합한다. 비교 이미지는 docs/screenshots/에 둔다. 이후 2차: C09~C11 급한 케어 시트, C12 경유지 완료 토스트, C08 모드 편집, C03 수유 정차, C04 수면 다크, C07 아이 선택(docs/design/pleos-1110/06-dev-plan.md P1-4~P1-9). feature/139-care-hf03(옛 디자인)는 병합하지 않는다.
2) 홈 H01(PR #153 병합됨)과 주행 음성 D01(PR #152 병합됨)은 코드만 병합됐고 에뮬레이터에서 화면을 확인하지 못했다. 확인해서 Figma(H01-home.png, D01-drive-voice.png)와 비교하고 어긋나면 고친다. D01은 에뮬레이터가 기어 변경을 막으므로 주행 상태를 임시 강제(커밋 금지)해 모양만 본다.
3) 코스 상세 H02와 대체 장소 H02-1(docs/agent-tasks/home-h01-h02.md, 06-dev-plan.md P2-2·P2-3). 대체 장소 후보 조회·교체 후 재계산 API가 서버에 있는지 먼저 확인하고, 없으면 지시 파일의 "서버에 필요한 것"에 적고 서버 작업을 별도 이슈로 나눈다.
4) 사용자 결정 대기(임의로 정하지 말고 하나씩 물을 것): 그로우 최종안(새 G01 / 현재 HF04 유지 / 젤리 B안, Q32 — 확인 전 HF04 유지), 정차 기준(P단 vs 주차 브레이크, Q01), 오른쪽 슬롯 우선순위 확정(Q03). 목록은 docs/design/pleos-1110/05-gaps-and-open-questions.md와 06-dev-plan.md 6절.
5) 모든 화면 작업이 끝나면 서버·웹·에뮬레이터를 최신 main으로 새로고침한다(서버: cd server 의 uvicorn app.main:app --host 0.0.0.0 --port 8000 --reload 가 이미 떠 있으면 자동 반영, 마이그레이션 alembic upgrade head 확인 / 앱: cd android && ./gradlew :app:installDebug 후 에뮬레이터 재실행, 로그인은 QR 화면에서 폰으로).

작업 중에는 자기 작업의 지시 파일 진행 로그와 docs/HANDOFF.md의 자기 행만 고쳐 같은 브랜치에 커밋하고, 끝나면 이슈 #24 체크리스트를 갱신한다. 끝나면 결과(PR 링크, 확인하지 못한 것, 남은 일)를 사용자에게 한국어로 보고한다.
```
