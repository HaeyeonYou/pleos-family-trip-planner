"""주차장: 적재 스크립트(검증·멱등)와 근처 조회. 테스트 DB(pleos_test)를 쓴다.

데이터는 tests/data/parking_sample.csv — 실제 데이터가 아니라 규격에 맞춰 만든 **샘플**(source=sample-std-parking, 이름 앞 [샘플])이다.
실제 파일(server/data/parking/)이 생기면 그 일부를 재생하는 테스트를 더하면 된다.
"""

import asyncio
import os
from datetime import date
from pathlib import Path

import httpx
import pytest
from sqlalchemy import func, select

from app.config import get_settings
from app.db import Base, Database
from app.main import app
from app.models import ParkingLot
from app.sources import parking
from scripts import load_parking
from scripts.load_parking import COLUMNS, FormatError, parse_csv, parse_row, upsert

TEST_DB = os.environ.get("TEST_DATABASE_URL", "postgresql+asyncpg://localhost/pleos_test")
SAMPLE = Path(__file__).parent / "data" / "parking_sample.csv"
GURI_CITY_HALL = (37.5943, 127.1296)
MONDAY, SATURDAY, SUNDAY = date(2026, 11, 9), date(2026, 11, 14), date(2026, 11, 15)


def run(scenario):
    async def main():
        db = Database(get_settings(), url=TEST_DB)
        async with db.engine.begin() as conn:
            await conn.run_sync(Base.metadata.drop_all)
            await conn.run_sync(Base.metadata.create_all)
        app.state.db = db
        try:
            await scenario(db)
        finally:
            await db.close()

    asyncio.run(main())


async def load_sample(path: Path = SAMPLE) -> int:
    return await upsert(parse_csv(path).rows, TEST_DB)


def write_csv(path: Path, *lines: str, encoding: str = "utf-8", header: str | None = None) -> Path:
    path.write_bytes(("\n".join([header or ",".join(COLUMNS), *lines]) + "\n").encode(encoding))
    return path


def line(**over) -> str:
    base = dict.fromkeys(COLUMNS, "")
    base.update(source="t", source_id="1", name="테스트 주차장", latitude="37.5", longitude="127.1")
    base.update(over)
    return ",".join(base[c] for c in COLUMNS)


# ---- 적재 스크립트: 파일 검증 ----

def test_sample_file_is_valid_and_marked_as_sample():
    result = parse_csv(SAMPLE)
    assert len(result.rows) == 20 and not result.skipped
    assert all(r["source"] == "sample-std-parking" and r["name"].startswith("[샘플]") for r in result.rows)


def test_bad_rows_are_skipped_with_reasons(tmp_path):
    path = write_csv(
        tmp_path / "p.csv",
        line(source_id="1"),
        line(source_id="2", latitude=""),  # 좌표 누락
        line(source_id="3", latitude="abc"),
        line(source_id="4", latitude="127.1", longitude="37.5"),  # 위도·경도 뒤바뀜
        line(source_id="5", capacity="열두개"),
        line(source_id="6", base_fee="-100"),
        line(source_id="7", weekday_open="9시"),
        line(source_id="8", weekday_open="25:00"),
        line(source_id="9", data_date="2026/09/30"),
        line(source_id="10", kind="사설"),
        line(source_id="", name="관리번호 없음"),
        "t,11,열 개수가 모자람",
        line(source_id="12", kind="공영", fee_type="유료", weekday_open="09:00", weekday_close="24:00", data_date="2026-09-30"),
    )
    result = parse_csv(path)
    assert [r["source_id"] for r in result.rows] == ["1", "12"]
    reasons = dict(result.skipped)
    assert len(result.skipped) == 11 and result.total == 13
    assert "latitude" in reasons[3] and "숫자" in reasons[4] and "범위" in reasons[5]
    assert "capacity" in reasons[6] and "음수" in reasons[7] and "HH:MM" in reasons[8]
    assert "시간 범위" in reasons[9] and "YYYY-MM-DD" in reasons[10] and "공영/민영" in reasons[11]
    assert "source_id" in reasons[12] and "열 개수" in reasons[13]


def test_empty_cells_become_null_and_numbers_are_converted():
    row = parse_row(dict(zip(COLUMNS, line(capacity="35", base_fee="1000", data_date="2026-09-30").split(","))))
    assert row["capacity"] == 35 and row["base_fee"] == 1000 and row["data_date"] == date(2026, 9, 30)
    assert row["kind"] is None and row["phone"] is None and row["latitude"] == 37.5


def test_header_mismatch_is_an_error_not_silently_fixed(tmp_path):
    swapped = ",".join(["name", "source_id", *COLUMNS[2:]])
    with pytest.raises(FormatError, match="순서"):
        parse_csv(write_csv(tmp_path / "a.csv", header=swapped))
    renamed = ",".join(COLUMNS).replace("capacity", "spaces")
    with pytest.raises(FormatError, match="없는 열 \\['capacity'\\].*모르는 열 \\['spaces'\\]"):
        parse_csv(write_csv(tmp_path / "b.csv", header=renamed))


def test_reads_utf8_bom_and_cp949(tmp_path):
    for enc in ("utf-8-sig", "cp949"):
        result = parse_csv(write_csv(tmp_path / f"{enc}.csv", line(name="구리 한강공원 주차장"), encoding=enc))
        assert result.rows[0]["name"] == "구리 한강공원 주차장", enc


def test_duplicate_key_in_file_keeps_the_later_row(tmp_path):
    result = parse_csv(write_csv(tmp_path / "d.csv", line(name="앞"), line(name="뒤")))
    assert [r["name"] for r in result.rows] == ["뒤"] and result.duplicates == 1


def test_cli_dry_run_and_errors(tmp_path, capsys):
    assert load_parking.main([str(SAMPLE), "--dry-run"]) == 0
    out = capsys.readouterr().out
    assert "20개 사용, 0개 건너뜀" in out and "dry-run" in out
    assert load_parking.main([str(tmp_path / "없음.csv")]) == 2
    bad = tmp_path / "bad.csv"
    bad.write_text("a,b\n1,2\n", encoding="utf-8")
    assert load_parking.main([str(bad)]) == 2
    assert "헤더가 규격과 다릅니다" in capsys.readouterr().err


# ---- 적재: DB ----

def test_load_is_idempotent_and_updates_in_place(tmp_path):
    async def scenario(db):
        assert await load_sample() == 20
        assert await load_sample() == 20  # 같은 파일을 다시 넣어도 늘지 않는다
        async with db.sessions() as s:
            assert await s.scalar(select(func.count()).select_from(ParkingLot)) == 20
            created = await s.scalar(select(ParkingLot.created_at).where(ParkingLot.source_id == "SAMPLE-001"))

        changed = write_csv(tmp_path / "c.csv", line(source="sample-std-parking", source_id="SAMPLE-001", name="[샘플] 이름 바뀜", capacity="999"))
        await load_sample(changed)
        async with db.sessions() as s:
            lot = await s.scalar(select(ParkingLot).where(ParkingLot.source_id == "SAMPLE-001"))
            assert (lot.name, lot.capacity) == ("[샘플] 이름 바뀜", 999)
            assert lot.created_at == created and lot.updated_at > lot.created_at
            assert await s.scalar(select(func.count()).select_from(ParkingLot)) == 20

    run(scenario)


# ---- 근처 조회 ----

def test_nearby_filters_radius_and_sorts_by_distance():
    async def scenario(db):
        await load_sample()
        async with db.sessions() as s:
            lots = await parking.nearby(s, *GURI_CITY_HALL, radius_m=3000, limit=10, today=MONDAY)
            names = [p.name for p in lots]
            assert names[0] == "[샘플] 구리시청 공영주차장" and lots[0].distance_m < 5
            assert [p.distance_m for p in lots] == sorted(p.distance_m for p in lots)
            assert all(p.distance_m <= 3000 for p in lots)
            assert "[샘플] 성남시청 공영주차장" not in names and "[샘플] 서울광장 공영주차장" not in names  # 반경 밖
            assert {"[샘플] 구리 한강시민공원 주차장", "[샘플] 구리 롯데마트 주차장"} <= set(names)

            wide = await parking.nearby(s, *GURI_CITY_HALL, radius_m=20000, limit=30)
            assert len(wide) > len(lots)
            assert len(await parking.nearby(s, *GURI_CITY_HALL, radius_m=20000, limit=2)) == 2
            assert await parking.nearby(s, 35.1, 129.0, radius_m=5000) == []  # 부산: 샘플 없음

    run(scenario)


def test_fee_and_hours_text():
    async def scenario(db):
        await load_sample()
        async with db.sessions() as s:
            by = {p.name: p for p in await parking.nearby(s, *GURI_CITY_HALL, radius_m=20000, limit=30, today=MONDAY)}
            assert by["[샘플] 구리시청 공영주차장"].fee_summary == "30분 1,000원·추가 10분 500원·일 최대 10,000원"
            assert by["[샘플] 구리시청 공영주차장"].hours == "평일 07:00~22:00"
            assert by["[샘플] 구리 한강시민공원 주차장"].fee_summary == "무료"
            assert by["[샘플] 구리 한강시민공원 주차장"].hours == "평일 24시간"
            assert by["[샘플] 구리 동구릉 주차장"].fee_summary == "120분 2,000원·추가 30분 500원"
            assert by["[샘플] 성남 중앙공원 주차장"].fee_summary == "60분 무료·추가 10분 300원·일 최대 5,000원"
            assert by["[샘플] 구리 한강시민공원 주차장"].address == "경기도 구리시 장자대로 267"

            sat = {p.name: p.hours for p in await parking.nearby(s, *GURI_CITY_HALL, radius_m=1000, today=SATURDAY)}
            sun = {p.name: p.hours for p in await parking.nearby(s, *GURI_CITY_HALL, radius_m=1000, today=SUNDAY)}
            assert sat["[샘플] 구리시청 공영주차장"] == "토요일 07:00~22:00"
            assert sun["[샘플] 구리시청 공영주차장"] == "일요일·공휴일 운영시간 정보 없음"  # 일요일 시간이 비어 있음

    run(scenario)


def test_fee_summary_without_data():
    assert parking.fee_summary(ParkingLot(fee_type="유료")) == "요금 정보 없음"
    assert parking.fee_summary(ParkingLot(fee_type=None, base_minutes=30, base_fee=1000)) == "30분 1,000원"


def test_parking_api():
    async def scenario(db):
        await load_sample()
        async with httpx.AsyncClient(transport=httpx.ASGITransport(app=app), base_url="http://test") as client:
            res = await client.get("/places/parking", params={"lat": GURI_CITY_HALL[0], "lng": GURI_CITY_HALL[1], "radius_m": 2000, "limit": 2})
            assert res.status_code == 200
            body = res.json()
            assert len(body) == 2 and body[0]["name"] == "[샘플] 구리시청 공영주차장"
            assert set(body[0]) >= {"name", "kind", "address", "distance_m", "capacity", "fee_summary", "hours", "latitude", "longitude"}
            assert (await client.get("/places/parking", params={"lat": 95, "lng": 127})).status_code == 422
            assert (await client.get("/places/parking", params={"lat": 37.5, "lng": 127, "radius_m": 10})).status_code == 422

    run(scenario)
