# 작업 지시·기록: 원격 로그 수집 (#261)

- 상태: **완료**(앱→NAS 실제 전송은 미확인) · 브랜치 `feature/261-remote-logs` · 이슈 #261 · 앞선 작업 [15-nas-deploy.md](15-nas-deploy.md)

## 1. 목표와 완료 조건
사용자 요청(2026-11-12): 서버·앱 로그를 Sentry처럼 원격으로 모아 시놀로지 저장공간에 남기기. 방식 **A(자체 수집)**로 결정(GlitchTip·Sentry self-hosted는 하지 않음).
- 서버: `LOG_DIR`가 있으면 `server.log`를 날짜별로 돌려 쓰고 `LOG_KEEP_DAYS`(기본 30일) 뒤 지운다.
- 서버: `POST /logs/app`로 앱 크래시·오류 묶음을 받아 `app-YYYY-MM-DD.jsonl`로 저장. 로그인 토큰이 있으면 user_id만 붙인다. 크기·개수·빈도 제한.
- 앱: 처리되지 않은 예외(크래시)와 `Log.w` 지점의 오류를 기기 파일 큐에 모았다가 다음 실행·주기적으로 전송.
- NAS: compose가 `/volume1/docker/pleos/logs`를 붙인다(File Station `docker/pleos/logs`). Docker 자체 로그는 크기 제한.

## 2. 결정
- 개인정보 제외: 위치 좌표·이름·입력 문장은 보내지 않는다. 기기 모델·OS·앱 버전·설치 id(무작위)·태그·메시지·스택만. 클라이언트 IP는 저장하지 않는다.
- 같은 오류가 짧은 시간에 반복되면 기기에서 묶어 `count`로 보낸다(오프라인 주행 중 네트워크 오류 폭주 방지).

## 3. 진행 로그
| 날짜 | 한 일 | 다음 단계 |
|---|---|---|
| 2026-11-12 | 이슈·지시서 작성 | 서버 구현 |
| 2026-11-12 | 서버: `app/logs.py` 파일 로그(`LOG_DIR`, 자정 회전, `LOG_KEEP_DAYS`), `app/applogs.py` `POST /logs/app`(50건·길이 제한, 설치별 분당 12회, 토큰 있으면 user_id, IP 미저장, 지난 파일 삭제). 테스트 `tests/test_applogs.py` 4개, 전체 293 통과 | 앱 |
| 2026-11-12 | 앱: `data/AppLog.kt`(크래시 처리기, `LogQueue` 파일 큐·반복 묶기·좌표 지우기, 업로드), 기존 `Log.w` 34곳을 `AppLog.w`로, `FamilyTripApp`에서 init·5분 주기 전송, `TripApi.sendLogs`. `LogQueueTest` 4개 포함 단위 테스트 전체 통과, assembleDebug 성공 | 배포 |
| 2026-11-12 | compose: `/volume1/docker/pleos/logs:/logs`, `TZ=Asia/Seoul`, Docker 로그 10MB×3. 배포 스크립트가 로그 폴더를 먼저 만든다(Synology Docker는 바인드 폴더를 자동으로 만들지 않아 첫 시도에서 api가 1~2분 못 떴음). 외부 주소로 시험 묶음 전송 → `app-2026-11-12.jsonl`·`server.log` 생성 확인 | PR |

## 4. 확인하지 못한 것
- 앱에서 실제 크래시를 내고 다음 실행 때 NAS까지 오는지는 확인하지 못했다. 에뮬레이터 앱은 기본 빌드(Mac 로컬 서버)이고, 로컬 서버가 main을 반영해야 `/logs/app`이 생긴다. NAS로 확인하려면 `-PserverUrl=https://pleos.kwonstar.synology.me/` 빌드가 필요(로그인 다시 해야 함).
- File Station 화면에서 폴더가 보이는지는 사용자 확인이 필요하다(권한은 `drwxrwxrwx+`, 파일 소유자 root).
- 로그를 볼 웹 화면(관리자 페이지)은 만들지 않았다.
