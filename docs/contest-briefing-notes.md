# 차량용 앱 개발 공모전 설명회 요약

출처: 통합 온라인 설명회 다시보기 (2026-10-28, 일부공개 YouTube `na6tSJ44pWc`, 42분)의 한국어 수동 자막.
화면(슬라이드)에만 나온 정보는 자막에 없으므로 공식 홈페이지(https://appdevchallenge.auto-hmg.io/) 공지에서 확인해야 함.

## 일정
| 날짜 | 내용 |
|---|---|
| 10/21 ~ 11/15 | 1차 접수 (기획서 + 전원 서명 동의서 필수, 개발 산출물 선택·가산점) |
| 11/23 | 1차 서류 결과 발표 |
| 12/3 ~ 12/4 | 2차 최종 PT (현대차 강남대로사옥 UX 스튜디오 서울), **PT 전까지 앱 개발 완료**, 현장 에뮬레이터 시연 |
| 12/18 | 최종 수상팀 발표 |
| 12/19 ~ 1월 중순 | 전시 준비 (신규 기능 개발 기간 아님) |
| 2027년 2월 | 그룹 행사에서 전시·시연·시상 |

## GitHub 저장소 제출 요건 (설명회 10:35 ~ 11:12 + [제출 가이드](https://appdevchallenge.auto-hmg.io/guide/submission))
- APK는 이메일 첨부가 아니라 **GitHub 링크로 제출**
- APK는 저장소 내 **`apk/` 폴더**에 업로드 (가이드 기준 소문자). GitHub 업로드 용량을 넘으면 **Releases**에 올리고 Release 페이지 주소를 제출
- APK 파일명: **`팀명_앱이름_release.apk`**, 실행 가능한 **release 빌드**
- 저장소 주소와 APK 파일 경로를 제출 시 함께 기재
- 개인 계정 Private 저장소의 Collaborator는 **쓰기 권한까지** 받으니 초대 전 권한 확인
- 심사용 GitHub 계정은 2026-11-04 기준 공지사항·FAQ에 아직 없음
- **README**에 포함할 것:
  - 앱 이름, 한 줄 소개
  - 실행 방법
  - 테스트 환경, 필요한 설정
  - APK 파일 위치 또는 릴리즈 링크
- **Private 저장소도 제출 가능**: 공지된 **심사용 계정을 Collaborator로 초대**하고, 마감 전에 접근 가능한지 반드시 확인
  - 심사용 계정 이름은 자막에 없음 → 홈페이지 공지 확인 필요

## 제출·수정
- 접수 양식(DOCX)이 곧 기획서. 접수양식 + 개인정보 동의서는 홈페이지에서 다운로드
- 이메일 제목 (홈페이지 기준, 설명회 구두 안내와 다름): `[차량용 앱 개발 공모전] 팀명(개인은 성명)_대표자명`
- 참가자 전원 CC
- 수정 제출: 마감 전, 처음 보낸 이메일 주소로 **전체 자료 + 서명 동의서 다시** 전송. 제목: `[수정제출] [차량용 앱 개발 공모전] 팀명(개인은 성명)_대표자명`. 마지막 제출본이 최종본.
- 1차 합격 20팀, 최종 10팀. 최종 10팀은 전시용 최종 APK 제출과 2027년 2월 행사 현장 참석이 의무

## 심사 기준
- 필수: **차량 특화성**(주행/정차 맥락, 차량 데이터·하드웨어 활용), **창의성**, **기술 활용성**(Pleos Playground SDK/API를 핵심 기능에 활용), **사업성**
- 가산점: 시연 영상 / 에뮬레이터 녹화 / 소스+APK가 든 GitHub 저장소

## 기술 환경 (42dot 세션)
- 플랫폼: **Pleos Connect** (AAOS 기반), 앱 유통은 Pleos App Market
- Android Studio, Java/Kotlin, **minSdk 28 이상, Gradle 8.0 이상** (문서의 SDK·에뮬레이터 버전도 확인)
- 시작점: Pleos Playground Docs → *Getting Started* (프로젝트 생성 → 에뮬레이터 → Vehicle SDK로 도어 상태 조회)
- SDK:
  - **Vehicle SDK**: 도어, 주행 정보, 배터리·충전, 공조 등
  - **NaviHelper SDK**: 목적지 설정, 경유지 관리, 경로 요청, 내비 정보 조회 (설명회 예시: "여행 중 방문할 장소 추천 → 차량 내비 목적지 연결")
  - **TextToSpeech / SpeechToText / FusedLocation SDK**
- 공모전 개발 중에는 권한 승인 불필요, `AndroidManifest`에 문서의 uses-permission 선언만 하면 됨 (App Market 배포 시에만 심사)
- **외부 API와 퍼블릭 네트워크 사용 가능**
- 실차 시연 없음. **Pleos Connect 에뮬레이터 + Virtual Console**(기어, 도어, 배터리 상태 변경)로 검증
- 생성형 AI 사용 가능. 단, PT 기술 Q&A에서 전체 구조·로직을 직접 설명할 수 있어야 함

## 공식 개발 문서 링크 ([개발 환경 가이드](https://appdevchallenge.auto-hmg.io/guide/development))
- [Pleos Connect 개발 환경 설정 (에뮬레이터)](https://document.pleos.ai/docs/connect/guide/getting-started/application-development/setup-connect-sdk-emulator)
- [Pleos Connect SDK 문서](https://document.pleos.ai/docs/pleos-only/connect-sdk-pleos/)
- [Vehicle SDK 튜토리얼](https://document.pleos.ai/docs/pleos-only/tutorial/vehicle-sdk)
- [Pleos Connect SDK API 참조](https://document.pleos.ai/api-reference/connect-sdk-pleos)
- [Pleos Playground](https://pleos.ai/playground)

## 기타
- 숏폼 챌린지 (선택, 가산점 없음): 30초~1분 세로 영상, 릴스/쇼츠, `#HMG차량용앱개발공모전` 포함 해시태그 2~3개 이상
- 문의: appdevchallenge@hyundai-partners.com
