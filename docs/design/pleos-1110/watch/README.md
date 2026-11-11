# Figma 변경 감시 (11/10 디자인)

디자이너가 Figma를 계속 고치고 있어, 페이지 `종합 정리`(`130:5`) 전체의 변경을 주기적으로 확인해 기록한다(2026-11-11 섹션 `351:2611`에서 넓힘). 이슈 #157, 지시 파일 `docs/agent-tasks/157-figma-watch.md`.

화면 단위는 페이지 최상위 노드와 모든 섹션의 자식이다. 피드백 반영 섹션(`369:11525`)이 원본 섹션의 복사본이라 같은 이름 화면이 두 번 나오므로, 기록과 세션 전달에는 `코드@섹션id`(예: `H01@369:11525`)를 쓴다. 화면 코드는 이름 앞부분(H01, C05, O04-1, S02-1, G01-B, Lib …, `Note · C05`는 C05)에서 읽는다.

| 화면 코드 | 전달 세션 |
|---|---|
| G01·G03·G01-B·G02-B | `1111-grow-jelly` |
| C01~C12 | `child-mode`, `1111-feeding-cleanup` |
| H01·H02·H02-1·D01·Lib·Type·Tile | `1111-shell-polish` |
| O·S02·K·HF·발표 슬라이드·기타 | 통제 세션만 (통제 세션은 늘 전체 요약을 받는다) |

| 파일 | 내용 |
|---|---|
| `CHANGELOG.md` | 사람이 읽는 변경 기록. 최신이 위. 화면별(`H01@351:2611`, `O01` …)로 추가·삭제·변경 노드와 문구·크기·색 변화, 관련 세션 |
| `snapshot.json` | 마지막 정규화 스냅샷. 노드 한 줄씩이라 `git diff`로도 바로 읽힌다 |
| `state.json` | 마지막으로 본 Figma 버전, 확인 시각, 달별 Tier 1 호출 수 |

## 실행

```bash
python3 tools/figma_watch.py check        # 주기 확인 (바뀌었을 때만 트리 조회)
python3 tools/figma_watch.py baseline     # 처음 한 번: 기준선 저장
python3 -m unittest discover -s tools/tests -v
```

마지막 줄 요약 JSON의 `status`: `unchanged`(파일 그대로), `section_unchanged`(파일의 다른 곳만 바뀜), `changed`(기록 추가됨, `sessions`에 전달할 세션과 화면 코드), `baseline`, `no_token`, `rate_limited`, `tier1_cap`, `api_error`.

## 호출 비용

| 단계 | API | 등급 | 언제 |
|---|---|---|---|
| 1 | `GET /v1/files/:key/meta` (안 되면 `/versions?page_size=1`) | Tier 3 (Tier 2) | 매번 |
| 2 | `GET /v1/files/:key/nodes?ids=130:5&geometry=omit` | **Tier 1** | 파일 버전이 바뀐 때만 (응답 약 23MB, 스냅샷 약 5MB) |

Figma 공식 문서 기준 Tier 1은 View·Collab 좌석이면 **월 20회까지**, Dev·Full 좌석은 분당 10~20회다. 스크립트가 Tier 1을 **월 20회**로 스스로 막는다(2026-11-11 사용자 결정으로 15 → 20. 좌석이 View·Collab이면 이것이 Figma 한도와 같아 429가 날 수 있다)(`--tier1-cap`, `--force`로 무시). 파일 버전은 다른 페이지를 고쳐도 바뀌므로, 트리를 받았는데 이 페이지는 그대로일 수 있다(`section_unchanged`, 이름은 주기 작업과 맞추려고 유지).

## 같은 계정으로 로그인한 다른 PC에 영향 없이 보는 방법

| 방법 | 다른 PC에 보이는 것 | 판단 |
|---|---|---|
| **REST API + 개인 액세스 토큰(읽기 전용 범위)** | 없음. 파일을 편집기로 열지 않으므로 아바타·커서·선택·뷰포트가 생기지 않는다 | **추천.** 이 스크립트가 쓰는 방식 |
| Figma MCP (데스크톱 앱 로컬 서버) | 이 PC 데스크톱 앱에서 파일을 열어야 하고, 연 동안 다른 PC 화면에 같은 계정 아바타가 하나 더 보인다. 선택은 PC마다 따로라 다른 PC의 선택을 바꾸지 않는다 | 주기 확인에 부적합 (앱을 켜 둬야 함) |
| Figma MCP (원격 서버) | 편집기를 열지 않으므로 아바타는 없을 것으로 추정. 다만 Education 플랜 MCP 호출 한도가 빠듯하다 | 꼭 필요할 때만 |
| 브라우저 시크릿 창으로 로그인 | 파일을 여는 동안 아바타가 보인다. 새 기기 로그인 알림 메일이 갈 수 있다 | 눈으로 볼 때만 |

토큰 발급(사용자가 직접): Figma → 왼쪽 위 계정 메뉴 → Settings → Security → Personal access tokens → Generate new token. 범위는 `file_content:read`, `file_metadata:read`, `file_versions:read`만(전부 읽기 전용) 고르고, 값은 레포 루트 `.env`의 `FIGMA_TOKEN=`에 넣는다. 채팅에 붙여 넣지 않는다. 토큰을 만들어도 다른 PC의 로그인은 끊기지 않는다.

## 확인하지 못한 것

- 사용자 계정의 좌석 종류(Full/Dev인지 View/Collab인지)와 Education(`student`) 플랜의 실제 REST 한도. 429가 나면 응답의 `X-Figma-Rate-Limit-Type`·`Retry-After`가 출력되니 그 값으로 판단한다.
- REST 조회가 "최근 본 파일" 목록에 남는지 (남지 않을 것으로 추정).
- 원격 MCP가 다른 PC에 흔적을 남기지 않는지 (추정).
