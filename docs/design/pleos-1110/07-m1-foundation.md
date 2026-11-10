# M1 공통 기반 사용법 (화면 세션용)

이슈 #144. 값 출처는 `01-inventory-and-tokens.md`·`tokens.json`. 코드는 `android/.../ui/`.

## 파일
| 파일 | 내용 |
|---|---|
| `Theme.kt` | `SitterPalette`(7색+그라데이션, 경고색), `SitterSurface`(Light/Dark 역할 색, `SitterSurface.current`), `SitterShapes`, 옛 이름 별칭 `TripColors` |
| `SitterType.kt` | Pretendard(`res/font`, OFL 1.1) + `SitterText` 10단(Display~Caption) |
| `SitterIcons.kt` | Figma 원본 SVG 29종 → `SitterIcons.*`, `SitterIcon(icon, tint)` |
| `DriveMode.kt` | `DriveMode`(정차/주행), `LocalDriveMode`, `ShellVariant`(Light·Off / Dark·Off / Light·On) |
| `AppShell.kt` | 앱 프레임(`FrameGeometry`), `ShortcutDock`, 임시 독 줄, `LocalShell` |
| `SitterGrid.kt` | `SitterCanvas`(1256x886 캔버스), 3열 레이아웃, 상단 음성줄·날씨·칩 줄, `SitterScrim` |
| `Common.kt` | `SitterButton`(Primary/Soft/Line × Regular/Large/Drive), `SitterTag`, `SitterChip`, `SitterCard`, `SitterRow`, `SitterToggle`, `VoiceButton`, `GradientCircleButton` |

## 화면을 새 디자인으로 옮기는 법
1. 화면 최상위를 `SitterCanvas { … }`로 감싼다. 안의 dp·sp는 Figma px 그대로다(창에 맞게 자동 확대, 1682x1210 창에서 약 1.31배).
2. `SitterVoiceBar`(+`SitterWeather`) → `SitterChipRow` → `SitterThreeColumns` / `SitterWideAndColumn`으로 쌓고, 3열째 맨 아래에 `ShortcutDock()`을 둔다. 코스 상세(H02·H02-1)는 독 없이 `SitterCanvas(ownsDock = false)`와 뒤로가기 버튼을 쓴다.
3. `SitterCanvas`를 쓰면 셸이 아래에 붙여 주던 임시 독 줄(음성 버튼 + 독)이 사라진다. 아직 안 옮긴 화면은 그대로 임시 독 줄이 붙는다.
4. 색은 `SitterSurface.current.*`, 글자는 `SitterText.*`, 주행 판단은 `LocalDriveMode.current.isDriving`. hex·`fontSize`·`isParked` 인자를 새로 만들지 않는다.
5. 수면(다크)은 기존 `ToneState.sleep`이 `SitterSurface.Dark`로 이어진다. 기저귀·수유·달래기 상황 톤은 11/10 디자인에 없어 옛 색을 그대로 쓰는 호환 경로로 남겨 뒀다(케어 세션이 정리).

## 결정과 한계
- 정차 정의는 `VehicleState.isParked`(P단) 한 곳. `DriveMode`는 그 값을 옮길 뿐이다 (`05` Q01이 정해지면 그곳만 수정). 차속 API가 없어 `speedKph`는 null.
- 좌측 레일은 제거. 독은 화면 3열째에 두는 것이 디자인의 뜻(H01·G01·C01~C12·D01 3열째, H02 제외). 셸 하나에 독을 고정하지 않은 이유는 열 안 위치가 화면마다 3열째 맨 아래라 화면이 그리는 편이 정확해서다.
- 높이 76 버튼은 차량 터치 규칙(76dp 이상) 때문에 Figma 72보다 4 크다.
- Dark의 보조 글자(#B0B8C1)·타일(#2A2F37)은 Figma에서 직접 확인하지 못한 추정치다.
