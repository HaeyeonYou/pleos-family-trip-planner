# 인계 문서: 오늘, 어디 가지? (Pleos 차량용 앱 공모전)

마지막 갱신: 2026-11-10. Antigravity 및 후속 AI 세션에서 작업을 즉시 이어받을 수 있도록 최신 상태와 작업 단위를 정리한 문서입니다.

---

## 0. 먼저 지킬 것 (사용자 선호와 전역 규칙)

| 항목 | 내용 |
|---|---|
| **새 세션 시작** | 새 대화 세션이나 다른 AI 에이전트(Claude Code, Antigravity, Cursor 등)가 시작할 때 **`docs/HANDOFF.md`와 `GEMINI.md`를 최우선으로 정독**하고 현재 작업 단위와 진행 상태를 확인 |
| **언어** | 사용자에게 보이는 글은 **모두 한국어**. 작업 중간의 진행 안내도 한국어로. 코드·식별자는 영어, **커밋 메시지·이슈·PR·라벨은 한국어** |
| **GitHub 계정** | 개인 프로젝트. **개인 계정 `HaeyeonYou`만 사용.** 회사(APR) 계정이나 조직에는 절대 만들거나 push하지 않음. push 전에 `gh auth status`로 계정 확인 |
| **git 사용자 정보** | 전역 설정이 아니라 이 레포에만 설정되어 있음 (`user.name HaeyeonYou`, noreply 이메일) |
| **작업 흐름** | **기능별 GitHub 이슈 → `feature/<이슈번호>-<이름>` (또는 `docs/...`) 브랜치 → PR(`Closes #번호`)로 main 병합.** PR은 기록용이라 리뷰 없이 바로 병합(작업자 1명). **커밋 메시지, 이슈, PR, 라벨은 한국어로 작성** |
| **커밋/push/병합** | 작업 단위가 끝나면 브랜치 커밋 → push → PR → **바로 병합**까지 진행 (`gh pr merge --merge --subject "PR #N 병합: <제목>" --body "<브랜치> 브랜치"`). **일부만 끝낸 이슈는 `Refs #N`만** |
| **서브에이전트 운용** | 서브에이전트 호출은 신중하게 판단하며, 단순 검색/조사/포맷팅 등 경량 작업에는 **더 저렴하고 빠른 모델(`flash`, `flash_lite`)을 적극 설정** |
| **컨텍스트 한도 (/compact)** | **대화 컨텍스트가 150K 이상 쌓이기 전에 꼭 기억해야 할 내용과 미완료 작업을 `docs/HANDOFF.md`에 기록하고 커밋한 후 `/compact` 실행** |
| **장시간 작업 알림** | 빌드, 에뮬레이터 시뮬레이션, 대규모 데이터 처리 등 장시간 백그라운드 태스크는 주기적으로 상태 리마인드 알림 확인 |
| **결정 사항 질문** | 여러 결정을 한 번에 묻지 말고 **하나씩** 질문 |
| **키·비밀값** | 채팅에 붙여 넣게 하지 말고 루트 `.env`에 넣게 안내. 출력할 때도 값은 숨김 |
| **공개 전환** | 레포는 Private. 나중에 Public으로 바꿀 예정이며, 그 전에 `docs/planning-analysis.md`를 제거해야 함 (`git filter-repo`) |

---

## 1. 프로젝트 개요

- **공모전**: 현대자동차그룹 차량용 앱 개발 공모전. 플랫폼은 Pleos Connect(AAOS 기반)
  - 홈페이지: https://appdevchallenge.auto-hmg.io/
- **앱**: 「오늘, 어디 가지?」 (시터노트). 영유아 동반 가족용 원데이 트립 플래너
  - 식사 → 나들이 → 카페 → 귀가 코스를 차량 위치, 배터리, 날씨, 장소 정보로 설계
  - 주행 중 플랜 변경, 도착 기반 '아이 경험 맵', 재추천
- **기획서 원본**: `/Users/kwonstar/Downloads/오늘_어디가지_패밀리_트립_플래너_기획안.pdf`
- **레포**: https://github.com/HaeyeonYou/pleos-family-trip-planner (Private), 로컬 경로 `/Users/kwonstar/Pleos_Application`
- **팀 구성**: 팀, 일반 부문. **사용자는 개발 담당.**

### 주요 일정 (오늘 기준 2026-11-10)
| 날짜 | 내용 |
|---|---|
| **11/15(일)** | 1차 접수 마감 (계획상 11/14 제출 완료 목표). 기획서 필수, 시연 영상과 GitHub(소스 + APK) 가산점 |
| 11/23 | 1차 결과 발표 |
| **12/3~4** | PT. 에뮬레이터로 실제 동작 시연 |
| 12/18 | 최종 발표 |

---

## 2. 문서 지도 (레포 안)

| 파일 | 내용 |
|---|---|
| `GEMINI.md` / `AGENTS.md` | **에이전트 전역 프로젝트 지침**: 세션 진입 시 HANDOFF 필독, 한국어 원칙, 서브에이전트 모델 최적화, 150K /compact 규칙 |
| `docs/HANDOFF.md` | **인계 문서 본문**: 전체 현황, 진행 중인 작업 단위, 규칙, 설정 |
| `docs/meeting-2026-11-08.md` | 11/8 중간 회의 자료: 기능 현황, Q&A, API 및 비용 정리, 시연 가이드 |
| `docs/settings-hf05.md` | HF05 설정 화면 상세 명세: 선호 조건 코스 가점 반영, 데이터 전체 삭제 |
| `docs/research/web-server-contract.md` | 서버-웹-앱 API 계약: 가족 정보, 수유, 방문 기록, 공조 프리셋 3개, /me/sync |
| `docs/research/web-mobile-screens.md` | 모바일 웹(`/m`) 화면 상세 (Figma 수진 MOBILE 09) |
| `server/` | FastAPI 서버 (`/health`, `/parse`, `/course`, `/me/*`, `/presets/*`), 테스트 187개 |
| `android/` | 차량 앱 (Kotlin + Compose, minSdk 28, compileSdk 35) |
| `tools/geo_drive.py` | 에뮬레이터 주행 시뮬레이션 |
| `.env.example` | 필요한 환경 변수 키 목록 (실제 키는 `.env`에만 보관) |

---

## 3. 확정된 아키텍처 및 기술 스택

| 항목 | 결정 내용 |
|---|---|
| **차량 앱** | Kotlin + Jetpack Compose. Pleos SDK (`Vehicle`, `NaviHelper`, `FusedLocation`). 카카오맵 SDK 2.15.2 |
| **서버** | Python FastAPI, PostgreSQL 17, SQLAlchemy Async, Alembic. 배포: SA6400 NAS Docker 예정 |
| **AI** | 조건 파싱 및 음성 명령: Claude Haiku 5.5 (`claude-haiku-5-5`), 실패 시 규칙 기반 fallback |
| **디자인 기준** | **피그마 지수 페이지 HF 01~06** 및 **11/10 종합 정리 확정안 (`351:2611`)** |
| **JDK 환경** | **JDK 17 필수** (`JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home`) |

---

## 4. 최근 완료 내역 (11/8 ~ 11/9 머지 완료 PR)

- **PR #102 / #104**: 이동 중 지연 알림(HF06) 및 도착 감지 시동 상태 보존
- **PR #107 ~ #113**: 폰 웹 로그인 후 화면 및 서버 API 2단계 (가족, 수유, 방문기록, 프리셋 3슬롯)
- **PR #114 ~ #120**: 웹-차 앱 데이터 실시간 동기화 (`/me/sync` 주기적 폴링 루프)
- **PR #121 ~ #124**: 폰 웹 온보딩 S01~S03 및 가입 동의 UI 반영
- **PR #125 ~ #127 / #135 ~ #138**: 개인정보 안내 문구 정정 (코스 추천 시 위치는 서버 미저장)
- **PR #129 / #130**: 경험 우주 Figma HF04 그로우 전면 개편
- **PR #131**: 차 앱-서버 방문/수유/프리셋/가족 양방향 동기화 연결
- **PR #132 / #133**: 케어 화면 공조 프리셋 HF03 v2 UI 반영 (베이비케어 카드 내 작은 카드 2개 + 편집 팝업)
- **PR #134**: 설정 화면 HF05 및 선호 조건 코스 추천 반영, 데이터 전체 삭제 구현

---

## 5. 현재 진행 중인 작업 단위 (Active Work Units)

새로운 세션에서는 아래 순서대로 작업 단위를 이어받아 진행합니다:

### [작업 단위 1] 케어 화면 나머지 Figma HF03 v2 반영 (`feature/139-care-screen-hf03`)
- **브랜치 / 경로**: `feature/139-care-screen-hf03` (로컬 Git Worktree: `.claude/worktrees/bold-raman-54ba41`)
- **현재 구현 상태**:
  - `FeedingTimerCard.kt`: 신규 수유 타이머 카드 Compose 컴포넌트 작성 완료
  - `FrequentCarePlaces.kt`: 방문 기록 기반 자주 가는 케어 장소 Compose 컴포넌트 작성 완료
  - `CareController.kt`: 기저귀 교환대 음성 전용 분리, `frequent` 장소 갱신 로직, 수유 갱신 처리
  - `VisitStore.kt`: `CARE_AMENITIES` 상수 추가
  - `FigmaIcons.kt`: `Alert`, `Timer`, `Star`, `StarFilled` 아이콘 경로 추가
- **남은 작업**:
  1. `CareScreen.kt` 레이아웃에 `FeedingTimerCard` 및 `FrequentCarePlaces` 연결
  2. 에뮬레이터 확인 및 빌드 검증 (`JAVA_HOME` OpenJDK 17 사용)
  3. 커밋 → push → PR 생성 및 `main` 병합

### [작업 단위 2] 11/10 종합 정리 확정 디자인 분석 및 개발 플랜 (`docs/development-plan-1110.md`)
- **대상**: Figma `xeLwkzhH8tnsazQdOriGU2` / `종합 정리` 페이지 / `351:2611` 섹션 (`11/10 · Pleos 공통 UI + 지수ver`)
- **결과 문서**: [docs/development-plan-1110.md](file:///Users/kwonstar/Pleos_Application/docs/development-plan-1110.md) (작성 완료)
- **핵심 화면 및 분석 완료 내역**:
  - **공통 UI 셸 (`351:2612`)**: 1256×886 앱 윈도우 규격, 앱 영역 한정 Dim 오버레이 원칙, 네이버 Cue 계열 시터노트 팔레트(`#4D55F0`, `#1F80FF`, `#8A1EE6` 등), Pretendard 10단 위계, 공통 Tag/Button/Row/VoiceBtn
  - **H01 홈 (`352:2601`)**: 3열 그리드(지도+프로액티브 / 3코스 리스트 / 타일+최근방문+독), 검색/음성 바, 날씨/지역, 8종 표준 필터 칩, 탑승 감지 프로액티브 카드
  - **H02 / H02-1 코스 상세 (`353:2745`, `353:3066`)**: 5대 핵심 케어 태그, 타임라인 인라인 액션, H02-1 우측 3열 인라인 대체 장소 슬라이드 시트
  - **C01~C12 케어 플로우 (`355:3177` ~ `356:6254`)**: 3열 기본 + 우측 가변 슬롯(392×370). C01 지연, C02 첫기록, C03 수유정차, C04 낮잠다크, C05 임박, C06 초과, C07 아이선택, C08 모드편집, C09~C11 급한케어(수유실 실패 시 정차공간 3곳 Fallback), C12 경유지완료 토스트
  - **D01 주행 중 음성 1곳 제안 (`356:6661`)**: 주행 안전 모드, 최적 1곳만 음성+대형버튼 제안, **8초 안전 타이머 후 자동 닫힘**
  - **그로우 젤리 우주 B안 (`374:6875`, `374:7543`, `374:6476`, `371:6514`)**: 12개 카테고리 ↔ 12종 젤리 캐릭터, 3단계 성장 진화(방울→몽글→탄생), MBTI식 조합 유형, 10분 이상 체류 집계, 비어있는 별 추천
- **현재 상태**: ✅ **분석 및 플랜 작성 완료**

### [작업 단위 3] 11/10 확정 디자인 구현 착수 (P0 / P1 / P2 로드맵)
- **Phase 0 (P0 - 디자인 시스템 & 셸)**: `Theme.kt`(Cue 팔레트, Pretendard 10단), `AppShell.kt`(1256×886 3열 그리드), `Common.kt`(Tag, Button, Row)
- **Phase 1 (P1 - 핵심 화면 및 주행 인터랙션)**: `HomeScreen.kt`(3열, 8칩, 프로액티브), `CourseScreen.kt`(5태그, H02-1 대체 시트), `CareScreen.kt`(가변 슬롯 C01~C12), `VoicePanel.kt`(D01 1곳 제안 + 8초 타이머)
- **Phase 2 (P2 - 게이미피케이션 & 고도화)**: `ExperienceUniverse.kt`(젤리 캐릭터 12종 렌더링, MBTI 유형 조합), C07 멀티 차일드 전환

### [작업 단위 4] M1 디자인 토큰 & 공통 셸 (`feature/144-m1-shell-tokens`)
| 상태 | 브랜치 | PR | 지시·기록 파일 |
|---|---|---|---|
| ✅ 완료 (2026-11-10) | `feature/144-m1-shell-tokens` | #145 (이슈 #144) | [docs/agent-tasks/m1-shell-tokens.md](docs/agent-tasks/m1-shell-tokens.md) |

- 화면 세션이 알아야 할 것: 토큰 `SitterPalette`/`SitterSurface`/`SitterText`, 셸 API `SitterCanvas`·`ShortcutDock`·`LocalDriveMode`, 독 규칙(3열째, 코스 상세 제외). 사용법은 `docs/design/pleos-1110/07-m1-foundation.md`.

---

## 6. 로컬 실행 및 빌드 명령어

1. **에뮬레이터 부팅**:
   ```bash
   ~/Library/Android/sdk/emulator/emulator -avd Pleos_Connect_v2.1.0 -no-snapshot-load
   ```
2. **서버 실행**:
   ```bash
   cd server && .venv/bin/uvicorn app.main:app --host 0.0.0.0 --port 8000
   ```
3. **앱 빌드 및 설치 (JDK 17)**:
   ```bash
   cd android && JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home ./gradlew :app:assembleDebug && adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```
4. **서버 테스트 실행**:
   ```bash
   cd server && .venv/bin/pytest -q
   ```

---

## 7. 커밋 및 PR 규칙

- 커밋 메시지는 **한국어**로 작성:
  ```
  docs: 11/10 에이전트 인계 문서 및 프로젝트 운영 지침 갱신
  
  - 새 세션 진입 시 HANDOFF/GEMINI.md 필독 지침 명시
  - 서브에이전트 모델 최적화 및 150K /compact 규칙 추가
  - 11/8~11/9 머지 완료 PR 및 현재 3대 작업 단위(케어 v2, 11/10 플랜, 신규 UI) 정리
  
  Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>
  ```
- PR 제목: `PR #번호 병합: <제목>`

### [작업 단위 5~7] 11/10 화면 세션 3개 (홈·케어·주행 음성)
작업 방식·파일 소유·토큰 한도 점검 규칙은 [docs/agent-tasks/1110-screen-sessions-plan.md](docs/agent-tasks/1110-screen-sessions-plan.md). 세션은 자기 행과 자기 지시 파일만 고친다.

| 작업 | 상태 | 브랜치·PR | 지시·기록 파일 |
|---|---|---|---|
| 홈 H01 · 코스 상세 H02 · 대체 장소 H02-1 | ✅ 완료(2026-11-10): H01·H02·H02-1·경유지 편집(H02-1 안에서)·서버 #160 값 반영 | #149 · PR #153, #158, #161, #173 | [home-h01-h02.md](docs/agent-tasks/home-h01-h02.md) |
| 케어 C01~C12 | ✅ 12화면 모두 에뮬레이터 확인·병합 (2026-11-10). 1차 PR #156, 2차 C09~C12 #166 · C08 #169 · C03 #171 · C04 #172 · C07 #174. 남은 일: 주행 중 전환·실차 확인 (적용 시점은 #168 완료) | #150 | [care-c01.md](docs/agent-tasks/care-c01.md) |
| 주행 중 음성 1곳 제안 D01 | ✅ 에뮬레이터 모양 확인·보정 병합(2026-11-10), 실차 확인 남음 | #151 · PR #152, #158 | [drive-voice-d01.md](docs/agent-tasks/drive-voice-d01.md) |
| 그로우 G01 경험 우주 + 젤리 12종 성장 | ✅ 에뮬레이터 확인·병합, 원본 젤리 그림 반영(#181, 2026-11-11). 서버 12분류·그로우 음성 명령은 다른 세션 | #164 · PR #170, #181 | [164-grow-g01.md](docs/agent-tasks/164-grow-g01.md) |
| 그로우 음성 명령 (기간·분류 보기, 다시 가볼 곳, ○○ 다시 가자) | ✅ 서버 의도·규칙 해석, 앱 연결, 에뮬레이터 정차·주행(D01) 확인 (2026-11-11). 남은 일: grow_go 화면 흐름·실제 STT 확인 | #185 · PR #189 | [185-grow-voice.md](docs/agent-tasks/185-grow-voice.md) |
| Figma 11/10 디자인 변경 감시 (주기 확인·기록·세션 전달) | 🔶 3시간 주기 감시 중, 2026-11-11 감시 범위를 페이지 `종합 정리` 전체로 넓힘(기준선 재저장). 온보딩 O01~O06 정리 → [1110-onboarding-o01.md](docs/agent-tasks/1110-onboarding-o01.md). 감사 뒤 변경 대조 → [delta](docs/audit/2026-11-11-figma-delta-after-audit.md), 지시서 [1111-course-progress](docs/agent-tasks/1111-course-progress.md)·[1111-home-fuel](docs/agent-tasks/1111-home-fuel.md) | #157 · `feature/157-figma-watch` | [157-figma-watch.md](docs/agent-tasks/157-figma-watch.md) |
| 온보딩 O01~O06 (차 앱·서버 생년월일 일·카시트·케어 장소 API·웹 S02) | ✅ 에뮬레이터 확인·병합(2026-11-11). QO5(아이별 모드 값)는 임시로 가족 공통 | #184 · PR #187, #190 | [1110-onboarding-o01.md](docs/agent-tasks/1110-onboarding-o01.md) |
| 케어 모드 값 아이별 (QO5: 서버 `child_id`·웹, 앱 O05·C07·C08·공조) | ✅ 완료(2026-11-11): 서버·웹 아이별 프리셋, 앱 O05 카드별 값·바꾸기, C07·C08·공조가 고른 아이 값, 에뮬레이터 아이 2명 확인. 서버 반영 시 `alembic upgrade head`. #168 적용 시점은 아래 행 | #192 · PR #193, #194 | [192-child-care-mode.md](docs/agent-tasks/192-child-care-mode.md) |
| 케어 모드 적용 시점 (C08 탑승하면 바로·출발 시각·직접 실행, 서버 `apply_at`) | ✅ 완료(2026-11-11): 서버·웹·앱 C08 같은 값, 탑승(앱 실행) 때 자동 켜기 에뮬레이터 확인, 출발 시각=고른 코스 출발 시각(단위 테스트만). 서버 반영 시 `alembic upgrade head` | #168 · PR #200, #203 | [168-preset-apply-at.md](docs/agent-tasks/168-preset-apply-at.md) |
| 서버: 대체 후보 우회 시간·거리, 재계산 뒤 나들이 후보 | ✅ 완료 (2026-11-10), 앱 반영은 home-finish 몫 | #160 · `feature/160-server-detour` | [160-server-detour.md](docs/agent-tasks/160-server-detour.md) |
| 서버: 그로우 젤리 12분류 판정·앱/웹 반영 | ✅ 완료 (2026-11-11). 코스 장소·방문 기록에 `jelly`, 앱 `classify` 서버 값 우선, 웹 그로우 12분류. 서버 반영 시 `alembic upgrade head` 필요 | #186 · `feature/186-server-jelly12` | [186-server-jelly12.md](docs/agent-tasks/186-server-jelly12.md) |
| 전체 감사: Figma(11/10) ↔ 실제 앱 (기능·디자인 부족분) | ✅ 완료(2026-11-11, main `dc6e85c` 기준): 필수 8·좋음 31·나중 21. 상위: 그로우 최신 '경험 젤리' 안, 앱 아이콘, 날씨 아이콘, 첫 실행 알림 띠, 음성 패널. 11/11 오후 Figma 페이지 전체 재조회(6절): G01 경험 젤리·C05·C06 문구·새 G03·H01 반경 안 → 지시서 3개 | #196 · PR #197 · 페이지 재조회 #201 | [2026-11-11-full-audit.md](docs/agent-tasks/2026-11-11-full-audit.md) · [감사 문서](docs/audit/2026-11-11-figma-vs-app.md) |
| 그로우 G01 경험 젤리 + G03 월별 캘린더 (감사 G-1·G-4 필수) | ✅ 완료(2026-11-11): 경험 젤리 궤도·추천 3줄(QF1 ③ 근처 검색 1회)·월별 보기 카드·G03 캘린더(월·올해)·분야 카드·날짜 탭, 실제 방문 기록·에뮬레이터 확인. 남은 일: 탭 재선택 시 G01 복귀, 실제 STT 확인 | #216 · PR #222 | [1111-grow-jelly.md](docs/agent-tasks/1111-grow-jelly.md) |
| 첫인상 다듬기: 앱 아이콘·날씨 아이콘·알림 띠·음성 패널·"경험 나무" 문구 (감사 O-2·H-1·O-1·D-3·H-2 필수) | ✅ 완료(shell-polish 세션) | `feature/214-shell-polish` · 이슈 #214 · PR #220 | [1111-shell-polish.md](docs/agent-tasks/1111-shell-polish.md) |
| 수유 표시를 아이 월령으로 나누기 (QD1: 12개월 이하 수유 · 초과 유아기 C05-T1, 감사 H-12 필수) | ✅ 완료(2026-11-11): 경계 상수 하나(앱·서버 12), 유아기 케어 화면(공조 직접 조작·가까운 화장실 3곳·빠른 모드 2개), 유아 자동 적용·C08에서 수유 정차 제외, H02·설정·음성·웹 같은 규칙. 남은 일: 온보딩 수유 간격 되살릴지, 지연 알림 후보를 유아에게 화장실로 | #217 · PR #221 · `feature/217-feeding-by-age` | [1111-feeding-cleanup.md](docs/agent-tasks/1111-feeding-cleanup.md) |
| 홈 H01 주행 가능 거리 타일 + 연료 부족 시 주유소·충전소 자동 경유지 (감사 P-4 필수, QP1·QP4) | ✅ 완료(2026-11-11): 서버 판정·자동 넣기·`/course/fuel`, 앱 연료 타일·[여기 들르기], 유아의자 문구, 에뮬레이터 확인. QP2 기준은 디자이너 답 대기(상수 하나). P-5 반경 50km 슬라이더는 서버 검색 반경 20km 상한 때문에 보류 | #215 · PR #223 · `feature/215-home-fuel` | [1111-home-fuel.md](docs/agent-tasks/1111-home-fuel.md) |
| 코스 진행 H03 도착·H03-1 마지막 장소·H04 코스 종료 (감사 P-1~P-3 필수) | 🔶 진행 중: 앱·서버(`/me/trip`) 구현과 단위 테스트 끝, 에뮬레이터 캡처 중 | #218 · `feature/218-course-progress` | [1111-course-progress.md](docs/agent-tasks/1111-course-progress.md) |

- **이어받는 법:** [docs/agent-tasks/next-agent-prompt.md](docs/agent-tasks/next-agent-prompt.md)의 프롬프트를 새 에이전트에 그대로 붙여 넣는다.
