# 백엔드 외부 API·서버·LLM 조사 — 「오늘, 어디 가지?」

- 조사일: 2026-11-05
- 범위: 차량 앱(Pleos Connect/AAOS) → 자체 서버(Synology NAS) → 외부 API 구조에 필요한 외부 API, 서버 운영, 서버 스택, LLM
- 표기: **[미확인]** = 공식 출처로 확인 못 함, **[추정]** = 조사자 추정. 수치(한도·가격)는 조사 시점 기준이며 실제 신청 화면에서 재확인 필요.
- 원칙(기획서): AI는 자연어 → 선호 조건 구조화만 담당. 운영시간·경로·에너지·일정 검증은 규칙 기반.

---

## 1. 외부 API

### 1-1. 기상청 단기예보 조회서비스 (공공데이터포털)

| 항목 | 내용 |
|---|---|
| 출처 | https://www.data.go.kr/data/15084084/openapi.do |
| 오퍼레이션 | 초단기실황, 초단기예보, 단기예보, 예보버전조회 (격자 nx/ny 좌표 기반) |
| 발급 절차 | 공공데이터포털 회원가입 → 해당 API '활용신청' → 마이페이지 > 개발계정에서 서비스키(인코딩/디코딩) 확인 |
| 승인 | 개발·운영 단계 모두 **자동승인** (즉시). 단 키가 실제 동작하기까지 수십 분~1시간가량 걸린다는 사용자 경험담 다수 **[추정]** |
| 무료 한도 | 무료. 개발계정 **10,000건/일**. 운영계정은 활용사례 등록 시 증량 신청 가능 |
| 이용 조건 | 공공누리 **제1유형**(출처표시) — 상업적 이용·변경 가능, 출처 표시 의무. 앱 내 "기상청 제공" 표기 필요 |
| 비고 | 기상청 API허브 연계 별도 목록(https://www.data.go.kr/data/15139470/openapi.do)도 있어 트래픽 정책 상이 가능. 위경도 → 격자 변환 함수 서버 구현 필요. 단기예보는 하루 8회(02·05·08·11·14·17·20·23시) 발표 → 서버 캐시 1~3시간이면 한도 걱정 거의 없음 **[추정]** |

### 1-2. 한국관광공사 TourAPI (국문 관광정보 / 무장애 여행정보 / 반려동물 동반여행)

**공통 발급·조건**

| 항목 | 내용 |
|---|---|
| 발급 절차 | 공공데이터포털에서 서비스별 '활용신청' (국문·무장애·반려동물은 **각각 따로 신청**, 서비스키는 계정 공통) |
| 승인 | 개발단계 **자동승인**(즉시), 운영단계 **심의승인** (반려동물 페이지 기준 확인, 국문·무장애는 같은 템플릿으로 **[추정]**) |
| 무료 한도 | 무료. 개발계정 **1,000건/일** (반려동물 페이지 기준. 국문·무장애 동일 **[추정]**, 무장애 페이지 검색 스니펫에서도 1,000 확인). 운영계정은 활용사례 등록 후 증량 신청 |
| 이용 조건 | 포털 표기 "이용허락범위 **제한 없음**". 단 사진은 명예훼손·인격권 침해 용도, 기업 CI/BI 이용 금지. 출처("한국관광공사") 표기 권장 **[추정: TourAPI 약관 관행]** |
| 출처 | 국문 https://www.data.go.kr/data/15101578/openapi.do · 무장애 https://www.data.go.kr/data/15101897/openapi.do · 반려동물 https://www.data.go.kr/data/15135102/openapi.do · 개요 https://studygov.kr/blog/13071-tourism-info-service/ |

**서비스별**

- **국문 관광정보(KorService2 계열)**: 위치기반·지역기반·키워드 조회, 공통/소개/반복/이미지 정보, 동기화 목록, 반려동물 동반 여행 정보 오퍼레이션 포함(15개). 1,000건/일은 후보 수집+상세 조회를 매번 호출하면 빠르게 소진 → **동기화 목록으로 시연 지역을 미리 DB에 적재**하는 방식 권장 **[추정]**.
- **무장애 여행정보**: 13개 오퍼레이션(무장애정보조회 = `detailWithTour` 계열). 대상에 "영유아가족" 명시. 약 6만 건.
- **반려동물 동반여행**: 별도 데이터셋(2024-09 등록). 지역·위치기반 조회, 동반 조건·유의사항·이용 가능 시설 제공.

**유아 편의 속성 — 실제 필드 존재 여부** (공식 매뉴얼 zip은 미열람, 오픈소스 클라이언트 코드의 응답 타입으로 확인)

| 필요 속성 | 필드 | 소속 API/오퍼레이션 | 형태 |
|---|---|---|---|
| 유모차(유아차) 대여·이용 | `stroller` | 무장애 `detailWithTour` | 자유 텍스트 (있으면 설명문, 없으면 빈 값) |
| 수유실 | `lactationroom` | 무장애 `detailWithTour` | 자유 텍스트 |
| 유아용 보조의자 | `babysparechair` | 무장애 `detailWithTour` | 자유 텍스트 |
| 영유아가족 기타 | `infantsfamilyetc` | 무장애 `detailWithTour` | 자유 텍스트 (기저귀 교환대 등이 여기 적힐 수 있음 **[추정]**) |
| 유모차 대여 정보 | `chkbabycarriage`(관광지), `chkbabycarriageculture`(문화시설), `chkbabycarriageleports`(레포츠), `chkbabycarriageshopping`(쇼핑) | 국문 소개정보(`detailIntro`) | 자유 텍스트("없음", "있음(무료)" 등) |
| 어린이 놀이방 | `kidsfacility` | 국문 소개정보 — 음식점(39) | 0/1 플래그 |
| 체험 가능 연령 | `expagerange`(관광지) 등 | 국문 소개정보 | 자유 텍스트 **[추정: 필드명 기억 기반, 미검증]** |
| **키즈존(노키즈존 여부)** | 없음 | — | **[미확인] 해당 필드 찾지 못함** |
| **기저귀 교환대 전용 필드** | 없음 | — | **[미확인] 전용 필드 없음, `infantsfamilyetc`·`restroom` 텍스트에 섞일 가능성만 있음** |

- 필드 확인 출처: https://github.com/TEAM-UNITRIP/UNITRIP-FrontEnd/blob/main/src/types/detailWithTour1.ts , https://github.com/DongJu-Na/Nadry/blob/main/client/src/uilts/usageKeys.js
- 시사점: 구조화된 불리언이 아니라 **대부분 자유 텍스트**라 서버에서 "있음/없음/정보 없음" 정규화(키워드 규칙)가 필요. 무장애 DB는 약 6만 건 중 일부 장소만 영유아 항목이 채워져 있을 것 → 시연 지역 커버리지 사전 점검 필수 **[추정]**. 식당·카페는 TourAPI 음식점의 `kidsfacility` 외엔 유아 편의 정보가 거의 없음.

### 1-3. 카카오 로컬 API (키워드·카테고리 장소 검색, 좌표↔주소)

| 항목 | 내용 |
|---|---|
| 출처 | 쿼터 https://developers.kakao.com/docs/ko/getting-started/quota · 공통 https://developers.kakao.com/docs/ko/local/common |
| 발급 절차 | 카카오 계정으로 Kakao Developers 로그인 → 앱 생성 → 앱 설정 > [카카오맵] > [사용 설정] **ON** → REST API 키를 서버에서 `Authorization: KakaoAK {키}` 헤더로 사용 |
| 승인 | 별도 심사 없이 **즉시** 사용 (사용 설정 ON만 필요) |
| 무료 한도 | 일간: 키워드 검색·카테고리 검색·주소→좌표·좌표→주소·좌표→행정구역 **각 100,000건**. 월간: 카카오맵 API **합산 3,000,000건**. **개발자 계정에서 처음 활성화한 앱에만** 무료 쿼터 제공 |
| 초과 시 | 비즈월렛 연결 + 유료 API 설정 시 종량 과금: 키워드·카테고리 검색 **2원/건**, 좌표 변환류 **0.5원/건**. 미설정 시 429 에러 |
| 이용 조건 | 공식 문서 페이지에 상업적 이용·캐시 제한·출처 표기 조항 없음 → 카카오 서비스 약관/운영정책 확인 필요 **[미확인]**. 로고는 디자인 가이드 준수 "권장" |
| 비고 | 2026-07-21부터 사용 방식·무료 쿼터 제공 방식 변경됨(문서 공지). 카테고리 코드: FD6(음식점), CE7(카페), OL7(주유소·충전소), AT4(관광명소) 등 **[추정: 기존 문서 기억]**. 응답은 이름·주소·전화·카테고리·좌표·place_url 정도이며 **영업시간·유아 편의 속성은 응답에 없음** **[추정]** → 식당·카페의 육아 편의 정보는 공백 |

### 1-4. 카카오모빌리티 길찾기 API

| 항목 | 내용 |
|---|---|
| 출처 | 가격 https://developers.kakaomobility.com/price/ · 상품 https://developers.kakaomobility.com/product/naviapi.html · 카카오 문서 https://developers.kakao.com/docs/ko/kakaonavi/common · 포럼 답변 https://devtalk.kakao.com/t/directions-api/151525 , https://devtalk.kakao.com/t/directions-api/151793 |
| 제공 기능 | 자동차 길찾기, **다중 경유지** 길찾기, 다중 출발지, **다중 목적지** 길찾기, 미래 운행정보 길찾기 |
| 발급 절차 | 카카오 로컬과 같은 Kakao Developers 앱의 REST API 키 사용 (`https://apis-navi.kakaomobility.com/v1/directions` 등). 카카오모빌리티 디벨로퍼스 콘솔 별도 이용 가능 |
| 승인 | 포럼 담당자 답변 기준 **비상업적 이용은 별도 승인·제휴 없이 무료 쿼터 내 즉시 사용**. **상업적 목적이면 무료 쿼터 내라도 사전 제휴 계약 필수**. 공모전/PT 시연은 비상업으로 볼 여지가 크나 판단 주체는 카카오모빌리티 **[추정]** → 출시 시 제휴 필요 |
| 무료 한도(일) | 자동차 길찾기 **10,000건** / 다중 경유지 **5,000건** / 다중 출발지 1,000건 / 다중 목적지 **1,000건** / 미래 운행정보 5,000건 |
| 초과 단가 | 자동차 8원 / 다중 경유지 16원 / 다중 목적지 20원 / 미래 운행정보 8원 / 다중 출발지 가격 미표기. 월 구간 초과 시 볼륨 할인 문의 |
| 경유지·목적지 최대 개수 | **[미확인]** (가격 페이지·카카오 문서에 없음. 다중 경유지 최대 30개, 다중 목적지 최대 30개라는 기억이 있으나 검증 못 함 **[추정]**) |
| 이용 조건 | 포럼 답변 중 "실시간이 아닌 임시 캐싱 등의 방법으로 API 호출 및 이용 금지" 취지 언급 있음 → **경로 결과 DB 저장·재사용 제한 가능성** (공식 약관 **[미확인]**). 캐시는 동일 요청 수 분 단위로 짧게만 두는 편이 안전 **[추정]** |
| 설계 시사점 | 후보 조합마다 호출하면 다중 목적지 1,000건/일이 병목. 직선거리 선필터 → 상위 N 조합만 다중 목적지 1회로 "현재 위치→후보들" 시간을 받고, 최종 코스 1~3개만 다중 경유지로 검증 **[추정]** |

### 1-5. 주유소 가격·위치 — 한국석유공사 오피넷 무료 오픈API

| 항목 | 내용 |
|---|---|
| 출처 | 공식 안내 https://www.opinet.co.kr/user/custapi/openApiInfo.do · 키 발급 https://www.opinet.co.kr/user/custapi/openApiNew.do · 비공식 문서 https://github.com/digitie/python-opinet-api , https://github.com/eigger/hass-opinet |
| 제공 API | 주유소 상세정보(ID), 전국/지역별 최저가 Top20, **반경내 주유소 검색**(`/api/aroundAll.do`, 반경 최대 5,000m), 전국·시도·시군구 평균가, 지역코드, 요소수 판매가 |
| 발급 절차 | 오피넷 회원가입 → 오픈API '인증키 발급' 신청 |
| 승인 | **자동승인·즉시 발급** (비공식 라이브러리 문서 기준, 공식 확인 **[미확인]**) |
| 무료 한도 | **1,500건/일** (Home Assistant 통합 문서 단일 출처, 공식 페이지에서 **[미확인]**) |
| 이용 조건 | DB 저작권은 석유공사. **수익 목적 이용은 사전 협의·허락 필요**, 게재 시 **"출처: 한국석유공사(오피넷)" 명시 의무**, 무단 변경 금지 |
| 주의 | 좌표계가 **KATEC(TM128)** → WGS84 변환(proj4) 필요. 응답에 가격·브랜드·좌표 있음. 운영시간 필드는 상세정보에 없을 가능성 **[추정]** → 24시간 여부는 정보 없음 처리. 대안: 카카오 로컬 카테고리 OL7로 위치만 보완 **[추정]** |

### 1-6. EV 충전소 — 한국환경공단 전기자동차 충전소 정보 (공공데이터포털)

| 항목 | 내용 |
|---|---|
| 출처 | https://www.data.go.kr/data/15076352/openapi.do · 메타 https://www.data.go.kr/catalog/15076352/openapi.json · 별도 공공급속 충전정보 https://www.data.go.kr/data/15156662/openapi.do |
| 오퍼레이션 | ① 충전소 정보(충전소명, 위치, 운영시간, 운영기관, 충전기 타입 등) ② 충전기 **상태(실시간)** — `period` 파라미터로 최근 1~10분 내 변경분만 조회 |
| 주요 필드 | `chgerType`(충전기 타입 코드), `stat`(상태 코드), `statUpdDt`, `useTime`("24시간 이용가능" 등), `parkingFree`(Y/N/NULL), `note`(이용제한 등) — 코드표는 활용가이드에 있음 |
| 발급 절차 | 공공데이터포털 '활용신청' (기상청과 같은 계정 서비스키) |
| 승인 | 개발단계 **자동승인** 추정(15156662 페이지 기준 개발 자동/운영 심의) **[추정]** |
| 무료 한도 | 무료. 15076352의 개발계정 트래픽은 페이지에서 **[미확인]** (15156662는 개발계정 10,000) |
| 이용 조건 | 공공누리 **제1유형**(출처표시) — 상업적 이용 가능, "한국환경공단" 출처 표기 |
| 설계 시사점 | 충전소 정보는 전국 수십만 기 규모라 **하루 1회 지역 단위로 DB 적재**, 상태는 `period=5~10` 증분 조회로 캐시 갱신 **[추정]**. 충전기 타입 코드로 차량 커넥터(DC콤보 등) 필터 가능 |

---

## 2. 서버: Synology NAS (헤놀로지 가능성 포함)

### 2-1. DSM Container Manager(Docker)로 API 서버 운영

- **요구 조건**
  - Container Manager는 **DSM 7.2 이상** 필요 (Container Manager 20.10.23 릴리스 노트). DSM 7.0/7.1은 구 'Docker' 패키지 사용. 출처: https://www.synology.com/en-us/releaseNote/ContainerManager
  - **x86_64 모델은 확실히 지원**. ARMv8 일부 저가 모델(DS223, DS420j, DS220j, DS120j 등)이 릴리스 노트로 추가됐으나 지역별 페이지 내용이 서로 달라 **본인 모델은 패키지 센터에서 직접 확인** 필요. 32bit ARM(armv7) 모델은 미지원 **[추정]**. 출처: 위 릴리스 노트, https://storagediskprices.com/synology-docker-guide/ (ARM 미지원 주장, 상충)
  - ARM NAS라면 이미지도 `linux/arm64`로 빌드해야 함(`docker buildx --platform linux/amd64,linux/arm64`). 헤놀로지는 x86이므로 문제 없음.
  - 메모리: 컨테이너 3~4개(API + PostgreSQL + Redis + 터널) 기준 **2GB 이상, 가능하면 4GB+** 권장 **[추정]**. 출처 참고: https://drfrankenstein.co.uk/docker-version-and-memory-recommendations/
- **운영 방법 (권장 흐름)** **[추정: 일반적 구성]**
  1. 패키지 센터 → Container Manager 설치
  2. 개발 PC에서 이미지 빌드 → GHCR/Docker Hub에 push (또는 NAS에 소스 복사 후 빌드)
  3. Container Manager > **프로젝트** 에서 `docker-compose.yml` 등록 (api, db, redis, tunnel 서비스)
  4. 볼륨은 `/volume1/docker/<앱>/` 아래 바인드 마운트, 재시작 정책 `unless-stopped`
  5. API 키는 `.env` 파일(공유 폴더 권한 제한)로 주입, 이미지·Git에 포함 금지
  6. DSM 작업 스케줄러로 `pg_dump` 일일 백업 → Hyper Backup으로 외부 백업

### 2-2. 외부 접속 방법 비교

| 방법 | 요구 조건 | CGNAT에서 | 장점 | 단점 |
|---|---|---|---|---|
| **DDNS + DSM 리버스 프록시 + Let's Encrypt** | 공인 IP(유동 가능), 공유기 포트포워딩 443(+80 인증용), DDNS(synology.me 등) | **불가** | DSM GUI로 완결(제어판 > 외부 액세스 > DDNS, 로그인 포털 > 고급 > 리버스 프록시, 보안 > 인증서). 무료 | 집 IP 노출, 공격 표면 증가, 가정용 회선 포트 차단 가능성 **[추정]** |
| **포트포워딩 단독** (HTTP 직접 노출) | 공인 IP, 포트포워딩 | **불가** | 가장 단순 | TLS 없음 → AAOS 앱은 기본 cleartext 차단(Android 9+)이라 예외 설정 필요, 보안 취약. 비권장 |
| **Tailscale Funnel** | Tailscale 계정(무료 플랜 포함), MagicDNS·HTTPS 활성화, ACL에 `funnel` 속성. 포트 **443/8443/10000만** | **가능** (아웃바운드 연결로 Tailscale 릴레이 경유) **[추정: 문서에 CGNAT 명시 없음, 구조상 가능]** | 도메인 불필요, 무료, TLS 자동, 집 IP 숨김 | **베타**, 주소는 `기기명.tailnet명.ts.net` 고정, **대역폭 제한(수치 비공개)**. Synology 패키지는 userspace 네트워킹이라 Funnel 동작 공식 확인 **[미확인]** → `tailscale/tailscale` 컨테이너를 사이드카로 띄우는 방식이 무난 **[추정]** |
| **Cloudflare Tunnel** (named) | Cloudflare 무료 계정 + **Cloudflare에 등록된 도메인**(연 1~2만 원 수준 **[추정]**). `cloudflared` 컨테이너 | **가능** (아웃바운드 연결) | 포트포워딩 불필요, 고정 커스텀 도메인, TLS·DDoS 보호, 무료 | 도메인 구매 필요. 대용량 비HTML 트래픽 약관 이슈는 API JSON 수준엔 무관 **[추정]** |
| **Cloudflare Quick Tunnel** (trycloudflare) | 계정·도메인 불필요 | **가능** | 1분 만에 공개 URL | **재시작마다 URL 변경**, 가동 보장 없음, 동시 요청 200 제한, SSE 미지원 → 개발용만 |

- 출처: Tailscale Funnel https://tailscale.com/kb/1223/funnel · Tailscale Synology https://github.com/tailscale/tailscale-synology · Cloudflare Quick Tunnel https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/do-more-with-tunnels/trycloudflare/
- **CGNAT 확인법** **[추정]**: 공유기 관리 화면의 WAN IP와 외부에서 본 IP(예: ifconfig.me)가 다르거나 WAN IP가 100.64.0.0/10·10.x·192.168.x 대역이면 CGNAT/이중 NAT. 국내 유선 가정 회선은 대개 공인 유동 IP지만 아파트 단지망·일부 회선은 사설 IP일 수 있음.
- **QuickConnect**는 DSM 자체 서비스(웹 UI, 파일 등) 중계용이라 **컨테이너 API 공개용으로 쓸 수 없음** **[추정]**.
- **권장**: Cloudflare Tunnel(named, 도메인 1개 구매) 1순위, 도메인 없이 가려면 Tailscale Funnel. 둘 다 CGNAT·헤놀로지에서 동작하고 공유기 설정이 필요 없음 **[추정]**.

### 2-3. 헤놀로지(Xpenology, 일반 PC + DSM)일 때 주의점 **[추정: 커뮤니티 일반론, 공식 문서 없음]**

- Synology EULA상 **비정품 하드웨어 사용은 라이선스 위반** → 공모전·발표 자료에 "Synology NAS"로 적을 때 표현 주의, 문제 소지 있으면 "자체 홈서버(Docker)"로 표기.
- 정품 시리얼/MAC이 없으면 **QuickConnect, synology.me DDNS, Synology 계정 연동 서비스가 막히거나 불안정** → DDNS 방식 대신 Cloudflare Tunnel/Tailscale이 더 적합.
- **DSM 업데이트 시 부트로더(RR/ARPL 등)가 깨져 부팅 불가**가 대표적 사고 → PT 전 4주간 DSM **자동 업데이트 끄기**.
- x86이라 Container Manager·이미지 호환성은 오히려 유리. 다만 PC 절전/BIOS 설정(전원 복구 시 자동 켜짐, Wake-on-LAN) 확인.
- 디스크·전원 이중화가 약한 경우가 많음 → DB 백업을 NAS 밖(클라우드/다른 PC)으로.
- 가능하면 DSM을 거치지 않고 같은 PC에 일반 Linux + Docker를 쓰는 것도 선택지(동일 compose 파일 재사용).

### 2-4. PT 현장(외부 네트워크의 에뮬레이터)에서 접속할 때 리스크와 백업안

**리스크** **[추정]**
- 집 정전·인터넷 장애·공유기 재부팅·DSM 자동 업데이트·NAS 재부팅으로 서버 다운 (현장에서 복구 불가)
- 동적 IP 변경 후 DDNS 반영 지연(DDNS 방식일 때)
- 현장 와이파이(학교·행사장)가 비표준 포트(8443, 10000 등)나 특정 도메인을 차단 → **443 포트 + 일반 도메인** 사용이 안전
- Tailscale Funnel 베타·대역폭 제한, Quick Tunnel URL 변경
- 외부 API 일일 한도 소진(리허설 반복 시 TourAPI 1,000건/일, 다중 목적지 1,000건/일)
- 에뮬레이터 → 서버 TLS 문제(자체 서명 인증서는 Android에서 거부)

**백업안**
1. **동일 컨테이너를 무료 클라우드 티어에 이중 배포** — 서버 URL을 앱 설정에서 전환 가능하게:
   - Oracle Cloud Always Free(ARM Ampere VM, 상시 가동) — 가입 시 카드 인증 필요 **[추정]**
   - Fly.io / Render / Railway / Google Cloud Run 무료·저가 티어 — 무료 조건·슬립(콜드스타트) 정책이 자주 바뀌므로 PT 직전 재확인 **[미확인: 이번 조사에서 최신 조건 미검증]**
2. **앱 내장 mock 모드** (가장 확실): 시연 시나리오의 서버 응답 JSON을 앱 assets에 번들, 네트워크 실패 시 자동 폴백 또는 숨은 토글. 화면에 "모의 데이터" 배지 표시(기획서의 실제/목업 구분 원칙 충족).
3. **서버 측 리플레이 캐시**: 리허설 때의 외부 API 응답을 저장해 두고 `DEMO_MODE`에서는 외부 호출 없이 재생 → API 한도·외부 장애 무관.
4. **노트북 로컬 서버**: 발표용 노트북에서 같은 compose로 서버를 띄우고 에뮬레이터는 `10.0.2.2`로 접속(현장 네트워크 불필요).
5. PT 당일 체크리스트: 헬스체크 엔드포인트(`/health`) + 외부 모니터링(UptimeRobot 등 무료) **[추정]**.

---

## 3. 1인 개발자 3~4주용 서버 스택 추천 **[추정: 조사자 판단]**

| 영역 | 1순위 | 대안 | 이유 |
|---|---|---|---|
| 언어/프레임워크 | **Python 3.12 + FastAPI** (+ Pydantic v2, httpx) | Kotlin + Ktor/Spring Boot (앱과 언어 통일), TypeScript + Fastify/NestJS | 공공 API XML/JSON 파싱·좌표 변환(pyproj)·스코어링 로직을 가장 빨리 작성. Pydantic 모델을 그대로 LLM JSON 스키마로 재사용 가능. 자동 OpenAPI 문서로 앱 쪽 연동 편함. 비동기 httpx로 외부 API 병렬 호출 |
| DB | **PostgreSQL 16 + PostGIS** | SQLite(+SpatiaLite) — 가장 단순, 단일 컨테이너 | 장소·충전소·주유소 사전 적재 후 반경 검색(`ST_DWithin`)이 핵심. 가족 프로필·방문 기록 저장. 시연 규모면 SQLite도 충분 |
| 캐시 | **Redis 7** (TTL 캐시 + API 일일 호출 카운터) | 앱 프로세스 내 메모리 캐시(cachetools) + DB 테이블 | 날씨(1~3h), 장소 상세(1일), 충전기 상태(5분), 경로(짧게) 등 TTL 차등. 호출 카운터로 한도 초과 전 차단·mock 폴백 |
| 배치 | APScheduler(앱 내) 또는 DSM 작업 스케줄러 + 스크립트 | — | TourAPI 동기화, 충전소 정보 일일 적재 |
| 배포 | Docker Compose (api, postgres, redis, cloudflared) | — | NAS·클라우드·노트북에서 동일 파일 재사용 → 2-4 백업안과 직결 |

- 권장 엔드포인트 골격: `POST /plan/parse`(LLM 구조화) → `POST /plan/courses`(규칙 기반 후보·조합·검증) → `POST /plan/recalc`(주행 중 재계산) → `GET /health`, 그리고 `DEMO_MODE` 플래그.
- 주차별: 1주 외부 API 클라이언트+적재 / 2주 필터·스코어링·경로 검증 / 3주 LLM 파싱+앱 연동 / 4주 mock·배포·리허설.

---

## 4. LLM: Claude API로 자연어 → 선호 조건 JSON 구조화

출처: Claude API 공식 스킬 문서(모델·가격 표, 2026-10-06 캐시) — 공식 가격 페이지 https://www.anthropic.com/pricing , 구조화 출력 문서 https://docs.claude.com/en/docs/build-with-claude/structured-outputs **[URL 경로는 추정, 내용은 SDK 문서 기준]**

### 4-1. 구조화 방식
- **Structured outputs (권장)**: Messages API에 `output_config: {format: {type: "json_schema", schema: {...}}}` 지정 → 응답이 스키마에 맞는 JSON으로 제한됨. Python SDK는 `client.messages.parse()`로 Pydantic 모델 검증까지 자동. (구 `output_format` 파라미터는 deprecated)
- **Strict tool use (대안)**: 도구 정의에 `strict: true` + `additionalProperties: false` + `required` 지정 → `tool_use.input`이 스키마와 정확히 일치. 주의: Sonnet 5.5는 **강제 `tool_choice`(`any`/`tool`)가 400 에러** → `auto` + 프롬프트 지시, 또는 structured outputs 사용.
- 설계 원칙(기획서 부합): 스키마는 **선호·제약만** 담음(예: `companions{infant_months[], pet}`, `themes[]`, `max_drive_minutes`, `meal_preference`, `indoor_preferred`, `must_have{nursing_room, stroller}`, `energy_stop_ok`, `unknown_fields[]`). 장소명·운영시간·충전 가능 여부 같은 사실은 스키마에 넣지 않아 LLM이 만들어낼 여지를 차단. enum을 적극 사용하고 서버에서 Pydantic 재검증 후 규칙 엔진으로 전달.
- 실패 대비: `stop_reason == "refusal"`/타임아웃 시 키워드 규칙 파서로 폴백 → 시연 중 LLM 장애에도 동작.

### 4-2. 모델 선택과 비용

| 모델 | ID | 입력 $/1M | 출력 $/1M | 용도 판단 |
|---|---|---|---|---|
| Claude Haiku 5.5 | `claude-haiku-5-5` | **$0.10** (프롬프트 100K 초과 시 $0.50) | **$0.50** (100K 초과 시 $2.50) | **1순위.** 짧은 한국어 요청 → 작은 JSON 추출은 경량 모델로 충분, 지연 짧음 |
| Claude Sonnet 5.5 | `claude-sonnet-5-5` | $2.00 | $10.00 | 모호한 다중 조건·대화형 수정("아까 거에서 카페 빼줘")에서 Haiku 품질이 부족할 때 |

- 요청 1건 가정(시스템 프롬프트+스키마 1,500 입력 토큰, 출력 300 토큰) **[추정]**:
  - Haiku 5.5 ≈ $0.0003/건 (약 0.4원) → 1,000건 ≈ $0.3
  - Sonnet 5.5 ≈ $0.006/건 (약 8원) → 1,000건 ≈ $6
  - 사고(thinking) 토큰은 출력으로 과금. Haiku 5.5는 adaptive thinking 기본 ON·effort 기본 `medium` → 단순 추출엔 `effort: "low"` 권장. 시스템 프롬프트를 고정하면 프롬프트 캐싱으로 입력 비용 추가 절감 가능(최소 캐시 길이 조건 있음).
- 3~4주 개발 + PT 기준 총비용은 **수 달러 이내** 예상 **[추정]**.

### 4-3. API 키 발급 방법
1. Claude Console(https://console.anthropic.com → 현재 platform.claude.com로 연결될 수 있음 **[추정]**)에서 계정 생성(이메일 인증, 조직 생성)
2. Billing에서 결제수단 등록 후 **선불 크레딧 구매**(최소 금액은 콘솔에서 확인 **[미확인]**)
3. API Keys > Create Key → 키(`sk-ant-...`)는 1회만 표시되므로 NAS `.env`에 저장, 앱(APK)에는 절대 포함하지 않음(서버에서만 호출)
4. 워크스페이스별 월 지출 한도(spend limit) 설정 권장
- 승인: 가입·결제 후 **즉시** 발급. 신규 계정은 낮은 rate limit 티어에서 시작 — 이 용도엔 충분 **[추정]**.

---

## 5. 요약: 사용자가 직접 해야 하는 발급·신청

| 항목 | 어디서 | 승인 소요 |
|---|---|---|
| 공공데이터포털 회원가입 + 기상청 단기예보 활용신청 | data.go.kr | 자동승인·즉시(키 활성화까지 최대 1시간 정도 **[추정]**) |
| TourAPI 국문 관광정보 / 무장애 / 반려동물 활용신청 (각각) | data.go.kr | 개발계정 자동승인·즉시 (운영 증량은 심의) |
| 한국환경공단 EV 충전소 정보 활용신청 | data.go.kr | 자동승인 **[추정]** |
| Kakao Developers 앱 생성 + 카카오맵 사용 설정 ON | developers.kakao.com | 즉시 |
| 카카오모빌리티 길찾기 | 같은 카카오 앱 키 | 비상업 즉시 / 상업적 이용 시 제휴 계약 필요 |
| 오피넷 무료 API 키 | opinet.co.kr | 즉시 **[추정]** (수익 목적은 사전 협의) |
| Anthropic Console 가입 + 크레딧 결제 + API 키 | console.anthropic.com | 즉시 |
| Cloudflare 계정 + 도메인 구매(또는 Tailscale 계정) | cloudflare.com / tailscale.com | 즉시~도메인 전파 수 시간 **[추정]** |
