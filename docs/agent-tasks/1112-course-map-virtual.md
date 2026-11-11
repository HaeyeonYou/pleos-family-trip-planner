# 작업 지시·기록: 코스 지도를 가상 배경 + 경로선으로 바꾸기

- 상태: **대기** · 세션명 `course-map-virtual` · 이슈: (세션이 만든다)
- 공통 방식·파일 소유 규칙: [1110-screen-sessions-plan.md](1110-screen-sessions-plan.md)

## 1. 목표와 완료 조건
사용자 결정(2026-11-12): 코스 미리보기 지도는 실제 지도가 아니어도 된다. **가상 배경 위에 출발지부터 목적지까지 경로선(폴리라인)으로 이어 주면 된다.** 코스 타임라인 옆 지도도 같다.
- 대상 화면: 홈 H01 1열 "추천 코스 미리보기" 카드(`HomeScreen.kt:183`), 코스 상세 H02 오른쪽 지도(`CourseOverview.kt:330`).
- 코스 진행 H03 지도(`GuideScreen.kt:323`)도 같은 `CourseMap`을 쓴다. **제안: 셋 다 같은 가상 지도로 통일**(같은 컴포저블이라 따로 두는 쪽이 더 일이 많다). 세션은 이대로 하고 PR에 적는다.
- 완료 조건
  - 세 화면 모두 카카오맵 없이 새 셸 토큰(Sitter*)으로 그린 가상 배경 + 경로선 + 번호 핀(출발지 표시 포함)이 나온다.
  - 경로선은 서버가 준 실제 도로 경로 `course.path`(위경도 목록)를 비율대로 그린다. path가 비면 출발지→장소 순서 직선으로 잇는다.
  - 카드 크기(홈은 꽉 채움, 상세·진행은 높이 330dp)에 맞게 여백을 두고 맞춤(비율 유지, 찌그러뜨리지 않음).
  - 위의 탑승 카드·"추천 코스 미리보기" 배지 등 지도 위 오버레이는 그대로.
  - 단위 테스트(좌표 → 화면 비율 변환, path 없을 때 대체)와 에뮬레이터 캡처(`docs/screenshots/`)가 PR에 있다.

## 2. 범위와 파일
- 고침: `ui/CourseMap.kt`(가상 지도로 교체), `ui/CourseMapLayer.kt`(기존 간이 지도 — path 경로선 지원으로 넓혀 재사용 권장).
- 정리(제안): 카카오맵이 더는 안 쓰이면 `FamilyTripApp.kt`의 `KakaoMapSdk.init`, `build.gradle.kts`의 `KAKAO_NATIVE_APP_KEY`·`libs.kakao.map` 의존성, `libs.versions.toml` 항목을 지운다. 앱에 외부 키가 하나도 안 남게 된다. 다른 곳에서 쓰면 남기고 PR에 적는다.
- 건드리지 않음: 서버(`course.path`는 그대로 받는다), `guide/`, 케어·그로우·음성. 공유 파일(MainActivity·TripViewModel·AppShell·Theme·Common)은 손대지 않는다.

## 3. 참고
- 디자인: `docs/design/pleos-1110/assets/screens/H01-home.png`, `H02-course-detail.png`, `H03-course-arrived.png`(배경·선 색감 참고. 실제 지도 타일은 따라 하지 않는다).
- 데이터: `data/CourseStop`, `data/LatLng`, `Course.path`.

## 4. 주의
- **에뮬레이터 공유 중**: 메인 세션의 마지막 통합 테스트(`1111-final-test-before-nas.md`) 에이전트가 기본 앱(`io.github.haeyeonyou.familytrip`)으로 흐름을 캡처하고 있다. 코드·단위 테스트를 먼저 하고, 에뮬레이터는 `appIdSuffix`를 붙인 앱으로만 쓰며 기본 앱은 지우거나 덮지 않는다.

## 5. 진행 로그
| 날짜 | 한 일 | 다음 단계 |
|---|---|---|
| 2026-11-12 | 지시 파일 작성(메인 세션) | 세션 시작 → 이슈·브랜치 |

## 6. 확인하지 못한 것
- (세션이 채운다)
