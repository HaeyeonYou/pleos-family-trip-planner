"""회원·세션·차 QR 로그인, 가족 정보·수유·방문 기록 테이블."""

import uuid
from datetime import date, datetime

from sqlalchemy import BigInteger, Boolean, Date, Double, ForeignKey, Index, Integer, String, Text, UniqueConstraint, func
from sqlalchemy.dialects.postgresql import JSONB
from sqlalchemy.orm import Mapped, mapped_column, relationship

from app.db import Base, utcnow


class User(Base):
    __tablename__ = "users"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    login_id: Mapped[str] = mapped_column(String(20), unique=True)
    password_hash: Mapped[str] = mapped_column(String(200))
    display_name: Mapped[str] = mapped_column(String(30))
    created_at: Mapped[datetime] = mapped_column(default=utcnow)
    # 개인정보 수집·이용 안내에 동의한 시각 (웹 가입 때 받는다. 그 전에 만든 계정은 비어 있다)
    agreed_at: Mapped[datetime | None] = mapped_column(default=None)


class Session(Base):
    """로그인 세션 하나 = 기기 하나 (폰 웹, 차). 토큰은 해시만 저장한다."""

    __tablename__ = "sessions"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    token_hash: Mapped[str] = mapped_column(String(64), unique=True)
    # web: 폰 브라우저, car: 차 앱
    kind: Mapped[str] = mapped_column(String(10))
    device_name: Mapped[str] = mapped_column(String(60), default="")
    created_at: Mapped[datetime] = mapped_column(default=utcnow)
    last_used_at: Mapped[datetime] = mapped_column(default=utcnow)
    expires_at: Mapped[datetime]
    revoked_at: Mapped[datetime | None] = mapped_column(default=None)

    user: Mapped[User] = relationship(lazy="joined")


class CarLoginRequest(Base):
    """차 화면 QR 로그인 요청. 폰에서 승인하면 차가 폴링으로 세션을 받는다 (한 번만)."""

    __tablename__ = "car_login_requests"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    # QR 주소에 들어가는 코드 (폰이 쓴다)
    code: Mapped[str] = mapped_column(String(16), unique=True)
    # 차만 아는 폴링 비밀값의 해시
    poll_hash: Mapped[str] = mapped_column(String(64))
    device_name: Mapped[str] = mapped_column(String(60), default="")
    # pending → approved → consumed (차가 토큰을 받음)
    status: Mapped[str] = mapped_column(String(10), default="pending")
    user_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), default=None)
    created_at: Mapped[datetime] = mapped_column(default=utcnow)
    expires_at: Mapped[datetime]


class ClimatePreset(Base):
    """폰에서 보낸 공조 프리셋. 사용자·슬롯(탑승 준비·낮잠·수유 정차)마다 지금 보낸 것 하나를 두고, 차가 적용하면 applied_at을 채운다."""

    __tablename__ = "climate_presets"
    __table_args__ = (UniqueConstraint("user_id", "slot"),)

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"))
    # boarding(탑승 준비) / nap(낮잠) / feeding_stop(수유 정차). 예전 앱이 쓰던 1개짜리는 boarding이다.
    slot: Mapped[str] = mapped_column(String(20), default="boarding", server_default="boarding")
    # 설정 값은 app/presets.py의 ClimateSettings 모양 그대로 둔다 (항목이 늘어도 테이블은 그대로)
    settings: Mapped[dict] = mapped_column(JSONB)
    sent_at: Mapped[datetime] = mapped_column(default=utcnow)
    applied_at: Mapped[datetime | None] = mapped_column(default=None)


class Child(Base):
    """아이 정보. 필수는 애칭·태어난 달이고 나머지는 케어 알림과 추천에 쓰는 최소 항목이다."""

    __tablename__ = "children"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    nickname: Mapped[str] = mapped_column(String(30))
    # 태어난 달의 1일로 저장 (생일은 개월 수 계산에만 쓴다). birth_date가 있으면 그 달로 맞춘다 (예전 클라이언트 호환)
    birth_month: Mapped[date] = mapped_column(Date)
    # 생년월일 (일까지, 차 온보딩 O04·웹 S02). 예전에 달만 받은 아이는 비어 있다
    birth_date: Mapped[date | None] = mapped_column(Date, default=None)
    # breast(모유) / formula(분유) / mixed(혼합) / solid(이유식 후기·유아식)
    feeding_type: Mapped[str | None] = mapped_column(String(10), default=None)
    feed_interval_minutes: Mapped[int | None] = mapped_column(Integer, default=None)
    nap_count: Mapped[int | None] = mapped_column(Integer, default=None)
    # 성향 태그 (app/family.py TEMPERAMENTS 키 목록)
    temperament: Mapped[list] = mapped_column(JSONB, default=list, server_default="[]")
    # 평소 낮잠 시간대 (예: "10:30–12:00 · 15:00–16:00")와 차에서 잘 자는지
    nap_times: Mapped[str | None] = mapped_column(String(40), default=None)
    nap_in_car: Mapped[bool | None] = mapped_column(Boolean, default=None)
    # 더위·추위: hot(더위 탐) / normal / cold(추위 탐) → 공조 권장 온도 보정
    heat_sensitivity: Mapped[str | None] = mapped_column(String(8), default=None)
    # 멀미 있음 → 코스 추천 이동 시간을 짧게
    motion_sickness: Mapped[bool | None] = mapped_column(Boolean, default=None)
    # sensitive(예민) / normal → 낮잠 프리셋 권장 음량
    noise_sensitivity: Mapped[str | None] = mapped_column(String(10), default=None)
    # 유모차 사용 → 코스 조건의 '유모차 길' 기본 선택
    stroller_use: Mapped[bool | None] = mapped_column(Boolean, default=None)
    # 카시트 뒤보기 (차 온보딩 O04 '함께 챙길 것')
    rear_facing_seat: Mapped[bool | None] = mapped_column(Boolean, default=None)
    created_at: Mapped[datetime] = mapped_column(default=utcnow)


class OutingMember(Base):
    """기본 외출 멤버(아이 말고 함께 나가는 사람). 일정마다 바꾸는 것은 일정 저장이 생기면 따로 둔다."""

    __tablename__ = "outing_members"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    label: Mapped[str] = mapped_column(String(20))
    is_driver: Mapped[bool] = mapped_column(Boolean, default=False)
    # True면 기본 외출 멤버, False면 '자주 함께'
    is_default: Mapped[bool] = mapped_column(Boolean, default=True)
    created_at: Mapped[datetime] = mapped_column(default=utcnow)


class FeedingLog(Base):
    """수유 기록. 시각·종류·양만 둔다."""

    __tablename__ = "feeding_logs"
    __table_args__ = (Index("ix_feeding_logs_user_id_fed_at", "user_id", "fed_at"),)

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"))
    child_id: Mapped[uuid.UUID | None] = mapped_column(ForeignKey("children.id", ondelete="CASCADE"), default=None)
    fed_at: Mapped[datetime]
    # formula(분유) / breast(모유) / solid(이유식·간식)
    kind: Mapped[str] = mapped_column(String(10))
    amount_ml: Mapped[int | None] = mapped_column(Integer, default=None)
    # web / car (차 앱·음성)
    source: Mapped[str] = mapped_column(String(10), default="web")
    created_at: Mapped[datetime] = mapped_column(default=utcnow)


class VisitRecord(Base):
    """방문 기록. 개인정보 최소화: 장소 키·이름·분야·날짜·머문 시간·아이 기분만 받고, 위치 좌표와 음성은 받지 않는다."""

    __tablename__ = "visit_records"
    __table_args__ = (Index("ix_visit_records_user_id_visited_on", "user_id", "visited_on"),)

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"))
    place_key: Mapped[str] = mapped_column(String(80), default="")
    place_name: Mapped[str] = mapped_column(String(60))
    # nature / experience / exhibition / play / pet (app/schemas.py Theme)
    theme: Mapped[str | None] = mapped_column(String(12), default=None)
    # 장소 분류 원문 (관광공사 cat3·카카오 category_name 등). 젤리 12분류 판정 근거 (#186). 예전 기록은 빈 값
    category: Mapped[str] = mapped_column(String(120), default="", server_default="")
    visited_on: Mapped[date] = mapped_column(Date)
    stayed_minutes: Mapped[int | None] = mapped_column(Integer, default=None)
    # confirmed(방문 확정) / pending(다녀왔는지 확인 대기) / dismissed(안 갔어요)
    status: Mapped[str] = mapped_column(String(10), default="confirmed")
    # excited / normal / sleepy / bad (아이 기분 한 번 탭)
    mood: Mapped[str | None] = mapped_column(String(10), default=None)
    created_at: Mapped[datetime] = mapped_column(default=utcnow)


class CarePlace(Base):
    """자주 가는 케어 장소. 차가 도착 기록으로 고른 곳을 올리고, 다른 차가 QR로 연결할 때 내려받는다.

    개인정보 최소화: 장소 키·이름·확인된 시설·방문 수·마지막 방문 날짜만 둔다. 위치 좌표는 받지 않는다
    (차는 이름으로 `care/nearby` 후보와 맞춰 좌표를 찾는다).
    """

    __tablename__ = "care_places"
    __table_args__ = (UniqueConstraint("user_id", "place_name", name="uq_care_places_user_id_place_name"),)

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    user_id: Mapped[uuid.UUID] = mapped_column(ForeignKey("users.id", ondelete="CASCADE"), index=True)
    place_key: Mapped[str] = mapped_column(String(80), default="")
    place_name: Mapped[str] = mapped_column(String(60))
    # 방문하며 확인된 시설 (nursing_room / stroller / pet)
    amenities: Mapped[list] = mapped_column(JSONB, default=list, server_default="[]")
    visit_count: Mapped[int] = mapped_column(Integer, default=0)
    last_visited_on: Mapped[date | None] = mapped_column(Date, default=None)
    updated_at: Mapped[datetime] = mapped_column(default=utcnow)


class CourseStart(Base):
    """'코스 시작'을 누른 기록. '지금 N대 가는 중' 집계용이라 장소 키와 시각만 둔다 (개인 식별 정보 없음)."""

    __tablename__ = "course_starts"

    id: Mapped[uuid.UUID] = mapped_column(primary_key=True, default=uuid.uuid4)
    place_key: Mapped[str] = mapped_column(String(80))
    started_at: Mapped[datetime] = mapped_column(default=utcnow)

    __table_args__ = (Index("ix_course_starts_place_key_started_at", "place_key", "started_at"),)


class ParkingLot(Base):
    """주차장 (전국주차장정보표준데이터 등). 미리 받은 파일을 server/scripts/load_parking.py 또는 SQL로 넣는다."""

    __tablename__ = "parking_lots"
    __table_args__ = (
        UniqueConstraint("source", "source_id"),
        Index("ix_parking_lots_latitude_longitude", "latitude", "longitude"),
    )

    id: Mapped[int] = mapped_column(BigInteger, primary_key=True, autoincrement=True)
    # 출처 구분과 그 출처의 관리번호
    source: Mapped[str] = mapped_column(Text, server_default="std-parking")
    source_id: Mapped[str] = mapped_column(Text)
    name: Mapped[str] = mapped_column(Text)
    kind: Mapped[str | None] = mapped_column(Text)  # 공영 / 민영
    lot_type: Mapped[str | None] = mapped_column(Text)  # 노외 / 노상 / 부설
    address_road: Mapped[str | None] = mapped_column(Text)
    address_lot: Mapped[str | None] = mapped_column(Text)
    latitude: Mapped[float] = mapped_column(Double)
    longitude: Mapped[float] = mapped_column(Double)
    capacity: Mapped[int | None] = mapped_column(Integer)
    fee_type: Mapped[str | None] = mapped_column(Text)  # 무료 / 유료 / 혼합
    base_minutes: Mapped[int | None] = mapped_column(Integer)
    base_fee: Mapped[int | None] = mapped_column(Integer)
    add_minutes: Mapped[int | None] = mapped_column(Integer)
    add_fee: Mapped[int | None] = mapped_column(Integer)
    day_fee: Mapped[int | None] = mapped_column(Integer)
    # HH:MM
    weekday_open: Mapped[str | None] = mapped_column(Text)
    weekday_close: Mapped[str | None] = mapped_column(Text)
    sat_open: Mapped[str | None] = mapped_column(Text)
    sat_close: Mapped[str | None] = mapped_column(Text)
    holiday_open: Mapped[str | None] = mapped_column(Text)
    holiday_close: Mapped[str | None] = mapped_column(Text)
    phone: Mapped[str | None] = mapped_column(Text)
    provider: Mapped[str | None] = mapped_column(Text)
    data_date: Mapped[date | None] = mapped_column(Date)
    created_at: Mapped[datetime] = mapped_column(server_default=func.now())
    updated_at: Mapped[datetime] = mapped_column(server_default=func.now())
