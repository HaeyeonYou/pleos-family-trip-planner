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
| `server/` | FastAPI 서버 (`/health`, `/parse`, `/course`, `/me/*`, `/presets/*`), 테스트 180개 |
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
- **핵심 화면**:
  - 공통 UI 셸 (`351:2612`): Light/Dark, Drive On/Off, 시터노트 네이비 팔레트, 공통 Tag/Button/Row
  - H01 홈 (`352:2601`): 검색/음성 바, 날씨/지역, 8개 필터 칩, 코스 지도 미리보기, 탑승 감지 추천
  - H02 / H02-1 코스 상세 (`353:2745`, `353:3066`): 대체 장소 선택
  - C01~C12 케어 플로우 (`355:3177` ~ `356:6254`): 수유 임박/초과 알림, 정차 모드, 급한 케어
  - D01 주행 중 음성 1곳 제안 (`356:6661`)
  - 그로우 젤리 우주 B안 (`374:6875`, `374:7543`, `374:6476`): 12종 젤리 캐릭터 및 성장 규칙
- **현재 상태**: 서브에이전트가 상세 XML/디자인 컨텍스트 분석 및 `docs/development-plan-1110.md` 작성 중

### [작업 단위 3] 11/10 확정 디자인 구현 착수 (P0 / P1 로드맵)
- 플랜 문서 도출 후, 공통 UI 셸 및 홈 화면(H01) 개편부터 순차적으로 기능 브랜치 생성하여 개발 진행

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
