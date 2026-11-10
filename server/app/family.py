"""가족 정보: 아이(1~n명)와 기본 외출 멤버. 폰 웹(쿠키)과 차 앱(Bearer) 모두 쓴다.

개인정보 최소화: 아이는 애칭·태어난 달과, 케어·추천에 실제로 쓰는 항목(수유 방식·간격·낮잠·성향·더위추위·멀미·소리·유모차)만,
멤버는 호칭과 운전자 여부만 둔다. 알레르기·식이 제한 같은 건강 정보는 쓰는 기능이 없어 받지 않는다.
"""

import uuid
from datetime import date, datetime, timedelta, timezone
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field, field_validator, model_validator
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth.deps import current_user, get_db
from app.models import Child, OutingMember, User

router = APIRouter(prefix="/me", tags=["family"])

MAX_CHILDREN = 6
MAX_MEMBERS = 12
KST = timezone(timedelta(hours=9))

FeedingType = Literal["breast", "formula", "mixed", "solid", "toddler"]
FEEDING_LABELS = {"breast": "모유", "formula": "분유", "mixed": "혼합", "solid": "이유식 후기", "toddler": "완료기 · 유아식"}

# 성향 태그 (온보딩 S02). 추천 코스와 경험맵의 첫 씨앗
Temperament = Literal["animal", "active", "quiet", "shy", "vehicle", "music"]
TEMPERAMENTS = {"animal": "동물 좋아함", "active": "활동적", "quiet": "조용한 편", "shy": "낯가림 있음", "vehicle": "탈것 좋아함", "music": "음악 좋아함"}
HeatSensitivity = Literal["hot", "normal", "cold"]
HEAT_LABELS = {"hot": "더위 탐", "normal": "보통", "cold": "추위 탐"}
NoiseSensitivity = Literal["sensitive", "normal"]
NOISE_LABELS = {"sensitive": "예민", "normal": "보통"}


def _has_final(name: str) -> bool | None:
    ch = name[-1:]
    return (ord(ch) - 0xAC00) % 28 != 0 if "가" <= ch <= "힣" else None


def topic(name: str) -> str:
    """서아 → 서아는, 하늘 → 하늘은 (한글이 아니면 '는')."""
    return name + ("은" if _has_final(name) else "는")


def subject(name: str) -> str:
    """서아 → 서아가, 하늘 → 하늘이."""
    return name + ("이" if _has_final(name) else "가")


def today_kst() -> date:
    return datetime.now(KST).date()


def age_months(birth_month: date, today: date | None = None, birth_date: date | None = None) -> int:
    """개월 수. 생년월일(일까지)을 알면 생일 전날까지는 한 달 덜 센다 (2025-09-03생은 2026-11-02에 13개월, 11-03에 14개월)."""
    today = today or today_kst()
    born = birth_date or birth_month
    months = (today.year - born.year) * 12 + today.month - born.month
    if birth_date is not None and today.day < birth_date.day:
        months -= 1
    return max(0, months)


def _check_born(born: date, unit: str) -> None:
    today = today_kst()
    limit = today if unit == "date" else today.replace(day=1)
    if born > limit or born.year < today.year - 18:
        raise ValueError("생년월일을 다시 확인해 주세요" if unit == "date" else "태어난 달을 다시 확인해 주세요")


class ChildIn(BaseModel):
    nickname: str = Field(..., min_length=1, max_length=30, description="애칭")
    birth_month: str | None = Field(None, pattern=r"^\d{4}-(0[1-9]|1[0-2])$", description="태어난 달 YYYY-MM. birth_date를 보내면 그 달로 맞춘다 (예전 클라이언트 호환)")
    birth_date: date | None = Field(None, description="생년월일 YYYY-MM-DD (일까지). 미래 날짜는 받지 않는다 (출산 예정일 모드 없음)")
    feeding_type: FeedingType | None = None
    feed_interval_minutes: int | None = Field(None, ge=60, le=360, description="수유 간격(분)")
    nap_count: int | None = Field(None, ge=0, le=4, description="하루 낮잠 횟수")
    temperament: list[Temperament] = Field(default_factory=list, max_length=6, description="성향 태그")
    nap_times: str | None = Field(None, max_length=40, description="평소 낮잠 시간대 (예: 10:30–12:00 · 15:00–16:00)")
    nap_in_car: bool | None = Field(None, description="차에서 잘 자는 편인지")
    heat_sensitivity: HeatSensitivity | None = Field(None, description="더위·추위 → 공조 권장 온도 ±1℃")
    motion_sickness: bool | None = Field(None, description="멀미 있음 → 코스 이동 시간을 짧게")
    noise_sensitivity: NoiseSensitivity | None = Field(None, description="소리 예민 → 낮잠 권장 음량")
    stroller_use: bool | None = Field(None, description="유모차 사용 → 코스 조건의 유모차 길")
    rear_facing_seat: bool | None = Field(None, description="카시트 뒤보기 (차 온보딩 O04 '함께 챙길 것')")

    @field_validator("nickname")
    @classmethod
    def _strip(cls, v: str) -> str:
        v = v.strip()
        if not v:
            raise ValueError("애칭을 입력해 주세요")
        return v

    @field_validator("nap_times")
    @classmethod
    def _blank_to_none(cls, v: str | None) -> str | None:
        return (v or "").strip() or None

    @field_validator("temperament")
    @classmethod
    def _unique(cls, v: list) -> list:
        return list(dict.fromkeys(v))

    @model_validator(mode="after")
    def _born(self) -> "ChildIn":
        if self.birth_date is not None:
            _check_born(self.birth_date, "date")
            self.birth_month = self.birth_date.strftime("%Y-%m")  # 일까지 알면 달은 거기서 계산한다
        elif self.birth_month is not None:
            _check_born(date(int(self.birth_month[:4]), int(self.birth_month[5:]), 1), "month")
        else:
            raise ValueError("생년월일을 입력해 주세요")
        return self


class ChildOut(ChildIn):
    id: uuid.UUID
    age_months: int


class MemberIn(BaseModel):
    label: str = Field(..., min_length=1, max_length=20, description="호칭(엄마·아빠·할머니 …)")
    is_driver: bool = False
    is_default: bool = Field(True, description="True면 기본 외출 멤버, False면 '자주 함께'")


class MemberOut(MemberIn):
    id: uuid.UUID


class Family(BaseModel):
    children: list[ChildOut]
    members: list[MemberOut]


def child_out(c: Child) -> ChildOut:
    return ChildOut(
        id=c.id,
        nickname=c.nickname,
        birth_month=c.birth_month.strftime("%Y-%m"),
        birth_date=c.birth_date,
        feeding_type=c.feeding_type,
        feed_interval_minutes=c.feed_interval_minutes,
        nap_count=c.nap_count,
        temperament=[t for t in (c.temperament or []) if t in TEMPERAMENTS],
        nap_times=c.nap_times,
        nap_in_car=c.nap_in_car,
        heat_sensitivity=c.heat_sensitivity,
        motion_sickness=c.motion_sickness,
        noise_sensitivity=c.noise_sensitivity,
        stroller_use=c.stroller_use,
        rear_facing_seat=c.rear_facing_seat,
        age_months=age_months(c.birth_month, birth_date=c.birth_date),
    )


async def list_children(db: AsyncSession, user: User) -> list[Child]:
    return list(await db.scalars(select(Child).where(Child.user_id == user.id).order_by(Child.created_at, Child.id)))


async def list_members(db: AsyncSession, user: User) -> list[OutingMember]:
    rows = await db.scalars(select(OutingMember).where(OutingMember.user_id == user.id).order_by(OutingMember.created_at, OutingMember.id))
    return list(rows)


async def _owned(db: AsyncSession, model, item_id: uuid.UUID, user: User):
    row = await db.get(model, item_id)
    if row is not None and row.user_id != user.id:
        raise HTTPException(404, "찾을 수 없어요")  # 남의 id가 있는지도 알려 주지 않는다
    return row


async def _count(db: AsyncSession, model, user: User) -> int:
    return await db.scalar(select(func.count()).select_from(model).where(model.user_id == user.id)) or 0


@router.get("/family", response_model=Family)
async def get_family(user: User = Depends(current_user), db: AsyncSession = Depends(get_db)):
    members = [MemberOut(id=m.id, label=m.label, is_driver=m.is_driver, is_default=m.is_default) for m in await list_members(db, user)]
    return Family(children=[child_out(c) for c in await list_children(db, user)], members=members)


async def save_child(db: AsyncSession, user: User, child_id: uuid.UUID, body: ChildIn) -> Child:
    row = await _owned(db, Child, child_id, user)
    if row is None:
        if await _count(db, Child, user) >= MAX_CHILDREN:
            raise HTTPException(409, f"아이는 {MAX_CHILDREN}명까지 등록할 수 있어요")
        row = Child(id=child_id, user_id=user.id)
        db.add(row)
    row.nickname = body.nickname
    row.birth_month = date(int(body.birth_month[:4]), int(body.birth_month[5:]), 1)
    if body.birth_date is not None or row.birth_date is None or row.birth_date.replace(day=1) != row.birth_month:
        # 달만 보낸 예전 클라이언트가 같은 달을 다시 보내면 이미 아는 일은 지우지 않는다
        row.birth_date = body.birth_date
    row.feeding_type = body.feeding_type
    row.feed_interval_minutes = body.feed_interval_minutes
    row.nap_count = body.nap_count
    row.temperament = body.temperament
    row.nap_times = body.nap_times
    row.nap_in_car = body.nap_in_car
    row.heat_sensitivity = body.heat_sensitivity
    row.motion_sickness = body.motion_sickness
    row.noise_sensitivity = body.noise_sensitivity
    row.stroller_use = body.stroller_use
    row.rear_facing_seat = body.rear_facing_seat
    await db.commit()
    return row


@router.put("/children/{child_id}", response_model=ChildOut)
async def put_child(child_id: uuid.UUID, body: ChildIn, user: User = Depends(current_user), db: AsyncSession = Depends(get_db)):
    """아이를 만들거나 고친다. id는 클라이언트가 정한 UUID라서 같은 요청을 다시 보내도 하나만 남는다."""
    return child_out(await save_child(db, user, child_id, body))


@router.delete("/children/{child_id}", status_code=204)
async def delete_child(child_id: uuid.UUID, user: User = Depends(current_user), db: AsyncSession = Depends(get_db)) -> None:
    """아이와 그 아이의 수유 기록을 지운다."""
    row = await _owned(db, Child, child_id, user)
    if row is None:
        raise HTTPException(404, "아이를 찾지 못했어요")
    await db.delete(row)
    await db.commit()


async def save_member(db: AsyncSession, user: User, member_id: uuid.UUID, body: MemberIn) -> OutingMember:
    row = await _owned(db, OutingMember, member_id, user)
    if row is None:
        if await _count(db, OutingMember, user) >= MAX_MEMBERS:
            raise HTTPException(409, f"멤버는 {MAX_MEMBERS}명까지 등록할 수 있어요")
        row = OutingMember(id=member_id, user_id=user.id)
        db.add(row)
    row.label = body.label.strip()
    row.is_driver = body.is_driver
    row.is_default = body.is_default
    await db.commit()
    return row


@router.put("/members/{member_id}", response_model=MemberOut)
async def put_member(member_id: uuid.UUID, body: MemberIn, user: User = Depends(current_user), db: AsyncSession = Depends(get_db)):
    row = await save_member(db, user, member_id, body)
    return MemberOut(id=row.id, label=row.label, is_driver=row.is_driver, is_default=row.is_default)


@router.delete("/members/{member_id}", status_code=204)
async def delete_member(member_id: uuid.UUID, user: User = Depends(current_user), db: AsyncSession = Depends(get_db)) -> None:
    row = await _owned(db, OutingMember, member_id, user)
    if row is None:
        raise HTTPException(404, "멤버를 찾지 못했어요")
    await db.delete(row)
    await db.commit()
