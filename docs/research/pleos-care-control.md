# 케어 기능의 차량 제어 조사 (HF03 베이비케어·수면 모드, #20 #70 #71)

조사일: 2026-11-08. 근거: Vehicle SDK 2.3.2 api jar와 에뮬레이터 로그(Pleos_Connect_v2.1.0, user 10).

## SDK로 되는 것
| 기능 | SDK | 권한 | 에뮬레이터 결과 |
|---|---|---|---|
| 공조 전원·온도·풍량·에어컨 설정 | `HVAC.setHvacPower/Temperature/FanSpeed/Ac` | `CONTROL_CAR_CLIMATE` (normal) | 요청은 VHAL `setPropertyAsync onSuccess`로 받아들여짐. 속성마다 지원 구역이 달라 `checkHvac*Capabilities`로 구역을 구해 적용 |
| 시트·핸들 열선 | `Seat.setHvacSeatTemperature`, `SteeringWheel.setHvacSteeringWheelHeat` | `CONTROL_CAR_SEATS` | 적용 성공 |
| 공조 설정값 읽기 | `HVAC.getHvacPowerState/FanSpeed/Temperature` | `CAR_CLIMATE` | 전원·풍량은 읽힘. **공조가 꺼져 있으면 설정 온도는 `PropertyNotAvailableException`** |
| 경유지 추가 | `NaviHelper.addWaypoint(RequestWaypointInfo)` | `NAVI_ROUTE_SEARCH` | 안내 중일 때만. 안내 중이 아니면 앱이 목적지로 길안내를 시작 |

## SDK로 안 되는 것
- **실내(캐빈) 온도 센서가 없다.** Vehicle SDK 클래스 목록(Brake, CarInfo, Display, Door, DrivingMode, EvBattery, Fuel, Gear, HVAC, Light, Odometer, Safety, Seat, SideMirror, SteeringWheel, Tire, Toll, TurnSignal, Window, Wiper)에 센서가 없다. 그래서 HF03의 "실내 온도"는 **차 공조의 설정 온도**로 보여 주고 이름도 "차 공조 설정"이라 붙였다.
- **차량 전체 음량 제어 없음.** `Display`는 온도 표시 단위만, `EtcsVoiceGuidanceVolumeListener`는 하이패스 안내음 청취뿐이다. 미디어·내비 음량을 쓰는 API가 없다.
- **화면(다크/라이트) 테마 제어 없음.** `Display`에 테마 API가 없다. 차량 앰비언트 조명(`Light.setAmbientLightBrightness`)은 있으나 화면 테마가 아니다.
- 그래서 수면 모드는 **앱 안에서만** 처리한다: 앱 안내 음성 30%, 알림을 소리 없는 채널로, 도착 5분 전 조용히 알림, 앱 패널 다크 톤. 공조가 켜져 있으면 풍량만 가장 약하게 낮춘다(`HVAC.setHvacFanSpeed`).

## 에뮬레이터의 한계 (실차와 다를 수 있음)
- 전원 켜기 요청이 성공해도 에뮬레이터 HAL이 상태를 바꾸지 않아 `HVAC_POWER_ON`이 계속 false로 읽힌다. 그래서 앱은 적용 후 다시 읽어 확인하고, 반영되지 않았으면 "요청은 보냈지만 차가 아직 반영하지 않았어요(시동 확인)"로 알린다.
- 켜진 직후 풍량 설정이 한 번 실패해 한 번 더 시도하는 로직을 넣었다.
- 시동이 꺼져 있으면 앱 자체가 돌지 않는다 (#71 조사, PR #61).

## #71 공조 제어 방식 결정에 대한 결과
- SDK로 실제 제어가 된다(요청 성공 확인). 따라서 "읽기 전용(PX03)"으로 제한할 이유는 기술적으로 없다.
- 현재 구현: 케어 탭의 베이비케어 토글이 24°C·약한 바람·열선 끔을 적용하고, 폰에서 보낸 가족 프리셋이 있으면 그 값을 우선 쓴다. 끄면 켜기 전 설정으로 되돌린다.
- 탑승 전 원격 적용·출발 10분 전 자동 적용은 앱이 안 도는 시동 꺼짐 상태라 여전히 불가.
