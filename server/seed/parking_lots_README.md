# 전국 주차장 표준데이터 정제 결과

## 범위와 파일

- 수집일: 2026-11-08 (Asia/Seoul)
- 사용자 추가 요청에 따라 서울·경기·인천에서 전국으로 확대했습니다. 지역 필터를 적용하지 않았습니다.
- 원본 18,994행 → 채택 17,147행 + 제외 1,847행.
- 채택 구분: 공영 16,247행, 민영 900행.
- 채택 데이터기준일자: 2026-04-08 ~ 2026-10-30. 행별 날짜를 보존했으며 수집일로 덮어쓰지 않았습니다.
- parking_lots.csv: UTF-8, BOM 없음, 헤더 포함, 25열. 빈 필드는 NULL을 의미합니다. 숫자 0은 보존합니다.
- parking_lots.sql: 동일한 25열과 값. BEGIN / 행별 INSERT … ON CONFLICT / COMMIT. 문자열 작은따옴표는 두 번 씁니다.
- rejected.csv: 원본 34열 전체와 source, source_file, source_row, region, reason, kept_source_row를 기록합니다. source_row는 헤더 제외 1부터 시작하는 원본 행 순번입니다.
- source_file은 포털 CSV 다운로드에 표시되는 파일명입니다. kept_source_row는 중복 시 선택된 최신 원본 행을 가리키며, 그 행이 별도 검증 사유로 제외되었을 수도 있습니다.
- 데이터베이스에 접속하거나 SQL을 실행하지 않았습니다.

## 공식 출처와 실제 수신 내역

- 포털 표시 원본 파일명: `전국주차장정보표준데이터.csv`
- 공식 출처 URL: https://www.data.go.kr/data/15012896/standard.do
- 공식 다운로드 메타데이터: https://www.data.go.kr/download/columList.json?pk=15012896&ext=CSV
- 공식 다운로드 구현: https://www.data.go.kr/js/biz/mvc/std/std-download-manager.js
- 포털 CSV 버튼은 고정 CSV 파일을 전송하지 않습니다. 공식 구현은 위 메타데이터를 읽고 /download/standard.json에서 10,000행씩 받은 다음 브라우저에서 CSV를 생성합니다. 이 공식 다운로드 경로와 동일한 컬럼·페이지 매개변수로 전체 데이터를 받았습니다. 수신 원본 형식은 JSON 2개이며, 제3자 가공본이나 인증키 기반 OpenAPI는 사용하지 않았습니다.
- 원본 JSON은 UTF-8로 해석했습니다. CP949/EUC-KR 바이트 원본을 받은 것은 아니므로 해당 인코딩 변환은 필요하지 않았습니다. 포털 CSV 생성 코드의 EUC-KR 재인코딩 단계를 거치지 않아 문자 손실도 피했습니다.
- 사전 용량을 공개하지 않아 사용자 동의 후 수집했습니다. 아래 크기는 실제 수신된 응답 본문 바이트 수입니다.
- 실제 수신 총량: 18,071,731바이트 (약 17.23 MiB).
- 로그인·활용 신청·가입·약관 동의·인증키 입력 없이 수집했습니다.

| 수신 원본 파일 | 크기(바이트) | 행 수 | SHA-256 |
|---|---:|---:|---|
| official_download_page_1.json | 9,558,029 | 10,000 | `c6144faf8eff6b98300dd6303341ef47b381c58cd4568fd2d789da8d5df1d8f8` |
| official_download_page_2.json | 8,513,702 | 8,994 | `0feee56ffadb2d8eec9fb4a04f4ba59186f3b460e21e289b2c822a34c1624837` |

official_download_page_1.json의 공식 수신 URL:

https://www.data.go.kr/download/standard.json?publicDataPk=15012896&colNmList=PRKPLCE_NO&colNmList=PRKPLCE_NM&colNmList=PRKPLCE_SE&colNmList=PRKPLCE_TYPE&colNmList=RDNMADR&colNmList=LNMADR&colNmList=PRKCMPRT&colNmList=FEEDING_SE&colNmList=ENFORCE_SE&colNmList=OPER_DAY&colNmList=WEEKDAY_OPER_OPEN_HHMM&colNmList=WEEKDAY_OPER_COLSE_HHMM&colNmList=SAT_OPER_OPER_OPEN_HHMM&colNmList=SAT_OPER_CLOSE_HHMM&colNmList=HOLIDAY_OPER_OPEN_HHMM&colNmList=HOLIDAY_CLOSE_OPEN_HHMM&colNmList=PARKINGCHRGE_INFO&colNmList=BASIC_TIME&colNmList=BASIC_CHARGE&colNmList=ADD_UNIT_TIME&colNmList=ADD_UNIT_CHARGE&colNmList=DAY_CMMTKT_ADJ_TIME&colNmList=DAY_CMMTKT&colNmList=MONTH_CMMTKT&colNmList=METPAY&colNmList=SPCMNT&colNmList=INSTITUTION_NM&colNmList=PHONE_NUMBER&colNmList=LATITUDE&colNmList=LONGITUDE&colNmList=PWDBS_PPK_ZONE_YN&colNmList=REFERENCE_DATE&totalCount=18994&svcTableNm=tn_pubr_prkplce_info_svc&perPage=10000&page=1

official_download_page_2.json의 공식 수신 URL:

https://www.data.go.kr/download/standard.json?publicDataPk=15012896&colNmList=PRKPLCE_NO&colNmList=PRKPLCE_NM&colNmList=PRKPLCE_SE&colNmList=PRKPLCE_TYPE&colNmList=RDNMADR&colNmList=LNMADR&colNmList=PRKCMPRT&colNmList=FEEDING_SE&colNmList=ENFORCE_SE&colNmList=OPER_DAY&colNmList=WEEKDAY_OPER_OPEN_HHMM&colNmList=WEEKDAY_OPER_COLSE_HHMM&colNmList=SAT_OPER_OPER_OPEN_HHMM&colNmList=SAT_OPER_CLOSE_HHMM&colNmList=HOLIDAY_OPER_OPEN_HHMM&colNmList=HOLIDAY_CLOSE_OPEN_HHMM&colNmList=PARKINGCHRGE_INFO&colNmList=BASIC_TIME&colNmList=BASIC_CHARGE&colNmList=ADD_UNIT_TIME&colNmList=ADD_UNIT_CHARGE&colNmList=DAY_CMMTKT_ADJ_TIME&colNmList=DAY_CMMTKT&colNmList=MONTH_CMMTKT&colNmList=METPAY&colNmList=SPCMNT&colNmList=INSTITUTION_NM&colNmList=PHONE_NUMBER&colNmList=LATITUDE&colNmList=LONGITUDE&colNmList=PWDBS_PPK_ZONE_YN&colNmList=REFERENCE_DATE&totalCount=18994&svcTableNm=tn_pubr_prkplce_info_svc&perPage=10000&page=2

## 정제·제외 기준

1. 문자열은 양끝 공백만 제거합니다. 없는 값을 추측해 채우지 않습니다. 좌표 지오코딩·좌표 보정은 하지 않았습니다.
2. 관리번호별 최신 기준일을 먼저 선택합니다. 동일 기준일이면 공식 다운로드에서 먼저 나타난 행을 유지합니다. 최신 행이 폐쇄 또는 좌표 오류이면 과거 행으로 되돌리지 않습니다. 다른 원본 행은 중복으로 기록합니다.
3. 관리번호·주차장명·위도·경도를 필수로 검사합니다. 좌표는 위도 33~39, 경도 124~132의 양끝을 포함합니다. 좌표 누락과 숫자 해석 불가를 구별합니다.
4. 주차장명·특기사항의 폐업·휴업·임시폐쇄 표시를 검사했습니다. 임시폐쇄는 현재 이용 중단으로 보고 제외했습니다. 원본에 별도 영업상태 열은 없으므로, 표시가 없는 실제 폐업 여부까지 검증할 수는 없습니다.
5. 야간 폐쇄·월요일 폐쇄는 시간/요일 제한이므로 주차장 전체의 폐업으로 취급하지 않았습니다. 정기권 미운영은 정기권 서비스 설명이며 제외하지 않았습니다. 공사중만 표기된 2행은 전체 운영 중단이 명시되지 않아 임의로 제외하지 않았습니다.
6. 숫자에서 쉼표·공백·원·분·면을 제거하고 유효한 숫자인지 검사했습니다. 100+200 등 조건별 금액은 더하거나 숫자를 이어 붙이지 않고 NULL로 뒀습니다. 분 단위 소수는 정수로 반올림하지 않고 NULL로 뒀습니다.
7. 시간은 0900, 9:00, 09:00:00을 HH:MM으로 변환합니다. 00:00~00:00 또는 23:59~23:59만으로 24시간 운영을 추측하지 않았습니다. 일부는 운영요일에서 평일이 제외돼 있습니다.
8. 특기사항에 24시간 운영/개방이 명시되면 00:00~23:59로 맞췄습니다. 관리번호 163-2-000037은 특기사항이 회원제주차장(24시간 운영)이므로, 상충하는 시간 열보다 명시 문구를 적용해 평일·토요일·공휴일을 각각 변경했습니다. 원문은 수신 데이터에 보존됩니다.
9. 모든 사유를 reason에 세미콜론으로 함께 기록합니다. 아래 주 사유는 reason의 첫 번째 항목이며 행 수 합계와 일치합니다.

## 지역별 행 수

지역은 도로명주소의 명시된 시·도 접두어를 우선하고, 없으면 지번주소를 사용했습니다. 주소 양쪽의 지역 충돌은 0건입니다. 주소 접두어 오류 3행은 같은 행의 공식 제공기관명으로 집계했습니다. 출력 주소 자체는 고치지 않았습니다.

| 지역 | 원본 행 수 | 채택 행 수 | 제외 행 수 |
|---|---:|---:|---:|
| 강원특별자치도 | 1,418 | 1,414 | 4 |
| 경기도 | 2,866 | 2,639 | 227 |
| 경상남도 | 1,408 | 1,400 | 8 |
| 경상북도 | 2,042 | 1,829 | 213 |
| 광주광역시 | 297 | 193 | 104 |
| 대구광역시 | 1,086 | 1,062 | 24 |
| 대전광역시 | 569 | 568 | 1 |
| 부산광역시 | 653 | 628 | 25 |
| 서울특별시 | 857 | 722 | 135 |
| 세종특별자치시 | 102 | 79 | 23 |
| 울산광역시 | 405 | 364 | 41 |
| 인천광역시 | 1,003 | 849 | 154 |
| 전남광주통합특별시 | 1,335 | 1,162 | 173 |
| 전라남도 | 588 | 289 | 299 |
| 전북특별자치도 | 805 | 722 | 83 |
| 제주특별자치도 | 1,657 | 1,572 | 85 |
| 충청남도 | 1,164 | 1,038 | 126 |
| 충청북도 | 739 | 617 | 122 |
| 합계 | 18,994 | 17,147 | 1,847 |

원본의 `전남광주통합특별시` 표기를 독립된 지역 그룹으로 보존했습니다. 이는 원본 주소의 분류이며 행정구역의 법적 현황을 새로 판정한 것이 아닙니다. `광주광역시`와 `전라남도`로 임의 재배분하지 않았습니다.

주소 대신 제공기관명으로 집계한 원본 행:

| 관리번호 | 원본 지번주소 | 제공기관명 | 집계 지역 |
|---|---|---|---|
| 388-2-000036 | 경상남동 양산시 유산동 168-121 | 경상남도 양산시 | 경상남도 |
| 309-2-000026 | 전븍특별자치도 정읍시 시기동 364 | 전북특별자치도 정읍시 | 전북특별자치도 |
| 309-2-000025 | 전븍특별자치도 정읍시 연지동 339-29 | 전북특별자치도 정읍시 | 전북특별자치도 |

## 제외 사유별 개수

| 사유 | 해당 사유를 포함한 행 수(중복 집계) | 주 사유 행 수(서로 배타적) |
|---|---:|---:|
| 좌표 누락 | 750 | 750 |
| 좌표 형식 오류 | 0 | 0 |
| 좌표가 한국 범위 밖 | 1 | 1 |
| 폐업 | 0 | 0 |
| 휴업 | 0 | 0 |
| 임시폐쇄 | 4 | 4 |
| 관리번호 없음 | 0 | 0 |
| 주차장명 없음 | 0 | 0 |
| 중복 | 1,116 | 1,092 |
| 합계 | 1,871 | 1,847 |

24행은 좌표 누락과 중복이 함께 있어 사유별 중복 집계 합계가 제외 행 수보다 24 큽니다.

## NULL로 정제한 해석 불가 값

아래 개수는 제외 전 전체 원본을 검사한 건수입니다. 원래부터 빈 값은 포함하지 않습니다.

| 출력 열 | 원본 값 | 건수 |
|---|---|---:|
| add_fee | 100+200 | 2 |
| add_fee | 150+300 | 9 |
| add_fee | 200+400 | 13 |
| add_minutes | 0.5 | 2 |
| add_minutes | 0.25 | 4 |
| base_minutes | 0.5 | 4 |

## 열 매핑

| 원본 열(공식 다운로드 코드) | 출력 열 | 처리 |
|---|---|---|
| 고정값 (`고정값`) | source | 고정 `std-parking` |
| 주차장관리번호 (`PRKPLCE_NO`) | source_id | 필수. 양끝 공백 제거. 최신 데이터기준일자만 유지 |
| 주차장명 (`PRKPLCE_NM`) | name | 필수. 누락 시 제외 |
| 주차장구분 (`PRKPLCE_SE`) | kind | 공영 / 민영 외 NULL |
| 주차장유형 (`PRKPLCE_TYPE`) | lot_type | 노외 / 노상 / 부설 외 NULL |
| 소재지도로명주소 (`RDNMADR`) | address_road | 원문 보존, 양끝 공백만 제거 |
| 소재지지번주소 (`LNMADR`) | address_lot | 원문 보존, 양끝 공백만 제거 |
| 위도 (`LATITUDE`) | latitude | 필수 숫자. 33 이상 39 이하 |
| 경도 (`LONGITUDE`) | longitude | 필수 숫자. 124 이상 132 이하 |
| 주차구획수 (`PRKCMPRT`) | capacity | 쉼표·원·분·면 제거 후 비음수 정수. 소수·복합식은 NULL |
| 요금정보 (`PARKINGCHRGE_INFO`) | fee_type | 무료 / 유료 / 혼합. 유료+무료는 혼합 |
| 주차기본시간 (`BASIC_TIME`) | base_minutes | 쉼표·원·분·면 제거 후 비음수 정수. 소수·복합식은 NULL |
| 주차기본요금 (`BASIC_CHARGE`) | base_fee | 쉼표·원·분·면 제거 후 비음수 정수. 소수·복합식은 NULL |
| 추가단위시간 (`ADD_UNIT_TIME`) | add_minutes | 쉼표·원·분·면 제거 후 비음수 정수. 소수·복합식은 NULL |
| 추가단위요금 (`ADD_UNIT_CHARGE`) | add_fee | 쉼표·원·분·면 제거 후 비음수 정수. 소수·복합식은 NULL |
| 1일주차권요금 (`DAY_CMMTKT`) | day_fee | 1일주차권요금 사용. 원본에 일최대요금 열은 없음 |
| 평일운영시작시각 (`WEEKDAY_OPER_OPEN_HHMM`) | weekday_open | HH:MM. 명시된 24시간 운영은 00:00~23:59 |
| 평일운영종료시각 (`WEEKDAY_OPER_COLSE_HHMM`) | weekday_close | HH:MM. 명시된 24시간 운영은 00:00~23:59 |
| 토요일운영시작시각 (`SAT_OPER_OPER_OPEN_HHMM`) | sat_open | HH:MM. 명시된 24시간 운영은 00:00~23:59 |
| 토요일운영종료시각 (`SAT_OPER_CLOSE_HHMM`) | sat_close | HH:MM. 명시된 24시간 운영은 00:00~23:59 |
| 공휴일운영시작시각 (`HOLIDAY_OPER_OPEN_HHMM`) | holiday_open | HH:MM. 명시된 24시간 운영은 00:00~23:59 |
| 공휴일운영종료시각 (`HOLIDAY_CLOSE_OPEN_HHMM`) | holiday_close | HH:MM. 명시된 24시간 운영은 00:00~23:59 |
| 전화번호 (`PHONE_NUMBER`) | phone | 실제 원본 열은 전화번호. 관리기관전화번호에 대응하며 보완하지 않음 |
| 제공기관명 (`INSTT_NM`) | provider | 제공기관명만 사용. 관리기관명으로 대체하지 않음 |
| 데이터기준일자 (`REFERENCE_DATE`) | data_date | 유효한 YYYY-MM-DD. 해석 불가 시 NULL |

## PostgreSQL 사용 조건

- parking_lots 테이블이 먼저 존재하고, (source, source_id)에 UNIQUE 제약조건 또는 고유 인덱스가 있어야 합니다. 이 SQL은 테이블을 만들지 않습니다.
- latitude/longitude에는 numeric 또는 double precision, 수량·분·요금에는 integer, data_date에는 date를 사용할 수 있습니다. 관리번호·전화번호는 선행 0이 보존되는 문자열로 저장해야 합니다.
- SQL은 트랜잭션 내 standard_conforming_strings를 on으로 지정해 원문의 백슬래시를 보존합니다. 없는 값은 인용하지 않은 NULL입니다.
- CSV를 COPY로 읽을 때 FORMAT csv, HEADER true, ENCODING 'UTF8'을 사용하고 빈 필드를 NULL로 읽습니다. 문자열 식별자·전화번호를 스프레드시트에서 숫자로 재저장하지 마세요.

## 마무리 검증

저장된 CSV와 SQL을 다시 읽어 전 행·전 열을 대조했습니다. SQL 리터럴을 별도로 파싱해 작은따옴표 이스케이프와 NULL을 복원하고 CSV와 비교했습니다. PostgreSQL 서버에서의 실행 검증은 하지 않았습니다.

| 검사 | 결과 |
|---|---:|
| CSV 데이터 행 수 | 17,147 |
| SQL INSERT 수 | 17,147 |
| 제외 행 수 | 1,847 |
| 좌표 범위 위반 | 0 |
| source_id 중복 | 0 |
| 필수 값 누락 | 0 |
| CSV와 SQL 값 불일치 | 0 |
| 최신 기준일 선택 위반 | 0 |
| 원본 대비 누락 행 | 0 |

- CSV 열 이름·순서 25개 일치, UTF-8 디코딩 성공.
- 정수·시간·날짜·허용 분류 형식 검사 통과.
- SQL 시작 BEGIN / 마지막 COMMIT 및 모든 행의 ON CONFLICT 갱신절 검사 통과.
- Artifact Tool로 완성 CSV를 추가로 가져와 17,147 데이터 행·25열과 식별 문자열을 확인했습니다.
- 공식 메타데이터 18,994행 = 수신 18,994행 = 채택 17,147행 + 제외 1,847행.

## 한계

공식 통합 파일의 전체 행을 수집했습니다. 포털 안내에 따르면 지자체 자료는 매월 초 병합되어 시차가 생길 수 있습니다. 모든 실제 주차장이나 최신 지자체 개별 수정분이 포함된다는 뜻은 아닙니다. 별도 파일의 추가 다운로드나 인증키 기반 API 신청은 하지 않았습니다.

## 출력 파일 크기와 해시

| 파일 | 크기(바이트) | SHA-256 |
|---|---:|---|
| parking_lots.csv | 4,379,671 | `30771b35d1a48a3fded6f4d43e3321fd1b7b3cd23ff0e653e5645c26cd89f451` |
| parking_lots.sql | 24,174,838 | `39dba3c60bfa966cf6d48f972a4b60484d1a6b2fbaa4bf3b196d5b0ddd277618` |
| rejected.csv | 790,371 | `d567df3ded9652c0b707eb905edd81548e623cf0acbfb92306db346d5fd4433d` |
