"""가족 정보: 아이(1~n명)와 기본 외출 멤버. 폰 웹(쿠키)과 차 앱(Bearer) 모두 쓴다.

개인정보 최소화: 아이는 애칭·태어난 달·수유 방식·간격·낮잠만, 멤버는 호칭과 운전자 여부만 둔다.
"""

import uuid
from datetime import date, datetime, timedelta, timezone
from typing import Literal

from fastapi import APIRouter, Depends, HTTPException
from pydantic import BaseModel, Field, field_validator
from sqlalchemy import func, select
from sqlalchemy.ext.asyncio import AsyncSession

from app.auth.deps import current_user, get_db
from app.models import Child, OutingMember, User

router = APIRouter(prefix="/me", tags=["family"])

MAX_CHILDREN = 6
MAX_MEMBERS = 12
KST = timezone(timedelta(hours=9))

FeedingType = Literal["breast", "formula", "mixed", "solid"]
FEEDING_LABELS = {"breast": "모유", "formula": "분유", "mixed": "혼합", "solid": "이유식 후기 · 유아식"}


def today_kst() -> date:
    return datetime.now(KST).date()


def age_months(birth_month: date, today: date | None = None) -> int:
    today = today or today_kst()
    return max(0, (today.year - birth_month.year) * 12 + today.month - birth_month.month)


class ChildIn(BaseModel):
    nickname: str = Field(..., min_length=1, max_length=30, description="애칭")
    birth_month: str = Field(..., pattern=r"^\d{4}-(0[1-9]|1[0-2])$", description="태어난 달 YYYY-MM")
    feeding_type: FeedingType | None = None
    feed_interval_minutes: int | None = Field(None, ge=60, le=360, description="수유 간격(분)")
    nap_count: int | None = Field(None, ge=0, le=4, description="하루 낮잠 횟수")

    @field_validator("nickname")
    @classmethod
    def _strip(cls, v: str) -> str:
        v = v.strip()
        if not v:
            raise ValueError("애칭을 입력해 주세요")
        return v

    @field_validator("birth_month")
    @classmethod
    def _plausible(cls, v: str) -> str:
        born = date(int(v[:4]), int(v[5:]), 1)
        if born > today_kst().replace(day=1) or born.year < today_kst().year - 18:
            raise ValueError("태어난 달을 다시 확인해 주세요")
        return v


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
        feeding_type=c.feeding_type,
        feed_interval_minutes=c.feed_interval_minutes,
        nap_count=c.nap_count,
        age_months=age_months(c.birth_month),
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
    row.feeding_type = body.feeding_type
    row.feed_interval_minutes = body.feed_interval_minutes
    row.nap_count = body.nap_count
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
