"""주차장 CSV를 parking_lots 표에 넣는다. 같은 파일을 여러 번 넣어도 안전하다 (source, source_id 기준 덮어쓰기).

    cd server
    .venv/bin/python scripts/load_parking.py data/parking/parking_lots.csv [--dry-run]

- 열 이름·순서는 표 정의와 같아야 한다. 다르면 고치려 하지 않고 오류로 끝낸다.
- 숫자·시간·날짜 형식이 틀린 행은 건너뛰고 이유를 출력한다. 마지막에 개수를 요약한다.
- UTF-8(BOM 포함)과 CP949 파일을 읽는다.
- 접속 주소는 app/config.py의 database_url (.env의 DATABASE_URL). --database-url로 바꿀 수 있다.
"""

import argparse
import asyncio
import csv
import sys
from dataclasses import dataclass, field
from datetime import date
from pathlib import Path

# python scripts/load_parking.py 로 실행해도 app 패키지를 찾도록 server/ 를 경로에 넣는다.
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from sqlalchemy.dialects.postgresql import insert  # noqa: E402
from sqlalchemy.ext.asyncio import create_async_engine  # noqa: E402
from sqlalchemy.sql import func  # noqa: E402

from app.config import get_settings  # noqa: E402
from app.models import ParkingLot  # noqa: E402

COLUMNS = [
    "source", "source_id", "name", "kind", "lot_type", "address_road", "address_lot",
    "latitude", "longitude", "capacity", "fee_type", "base_minutes", "base_fee", "add_minutes", "add_fee",
    "day_fee", "weekday_open", "weekday_close", "sat_open", "sat_close", "holiday_open", "holiday_close",
    "phone", "provider", "data_date",
]
REQUIRED = ["source", "source_id", "name", "latitude", "longitude"]
INT_COLUMNS = ["capacity", "base_minutes", "base_fee", "add_minutes", "add_fee", "day_fee"]
TIME_COLUMNS = ["weekday_open", "weekday_close", "sat_open", "sat_close", "holiday_open", "holiday_close"]
CHOICES = {"kind": {"공영", "민영"}, "lot_type": {"노외", "노상", "부설"}, "fee_type": {"무료", "유료", "혼합"}}
# 대한민국 좌표 범위 (제주·울릉도·독도 포함). 위도·경도가 뒤바뀐 행도 여기서 걸러진다.
LAT_RANGE = (32.5, 39.0)
LNG_RANGE = (123.5, 132.5)
BATCH = 1000


class FormatError(Exception):
    """파일 전체가 규격과 맞지 않을 때 (헤더 등)."""


@dataclass
class LoadResult:
    rows: list[dict] = field(default_factory=list)
    skipped: list[tuple[int, str]] = field(default_factory=list)  # (줄 번호, 이유)
    duplicates: int = 0  # 파일 안에서 같은 (source, source_id)가 또 나와 앞 행을 대체한 수
    total: int = 0


def read_text(path: Path) -> str:
    raw = path.read_bytes()
    try:
        return raw.decode("utf-8-sig")
    except UnicodeDecodeError:
        try:
            return raw.decode("cp949")
        except UnicodeDecodeError as e:
            raise FormatError(f"{path.name}: UTF-8도 CP949도 아닙니다 ({e})") from e


def _time(value: str) -> str:
    h, sep, m = value.partition(":")
    if not sep or len(h) != 2 or len(m) != 2 or not (h + m).isdigit():
        raise ValueError(f"시간 형식은 HH:MM 이어야 합니다 ({value!r})")
    hh, mm = int(h), int(m)
    if not (0 <= mm <= 59 and (hh <= 23 or (hh == 24 and mm == 0))):
        raise ValueError(f"시간 범위가 틀렸습니다 ({value!r})")
    return value


def parse_row(raw: dict[str, str]) -> dict:
    """CSV 한 행을 검증해 DB에 넣을 값으로 바꾼다. 틀리면 ValueError(이유)."""
    row: dict = {k: (v or "").strip() or None for k, v in raw.items()}
    for col in REQUIRED:
        if row[col] is None:
            raise ValueError(f"{col} 이(가) 비어 있습니다")
    for col in ("latitude", "longitude"):
        try:
            row[col] = float(row[col])
        except ValueError:
            raise ValueError(f"{col} 이(가) 숫자가 아닙니다 ({row[col]!r})") from None
    if not LAT_RANGE[0] <= row["latitude"] <= LAT_RANGE[1]:
        raise ValueError(f"위도가 국내 범위를 벗어났습니다 ({row['latitude']})")
    if not LNG_RANGE[0] <= row["longitude"] <= LNG_RANGE[1]:
        raise ValueError(f"경도가 국내 범위를 벗어났습니다 ({row['longitude']})")
    for col in INT_COLUMNS:
        if row[col] is not None:
            try:
                row[col] = int(row[col])
            except ValueError:
                raise ValueError(f"{col} 이(가) 정수가 아닙니다 ({row[col]!r})") from None
            if row[col] < 0:
                raise ValueError(f"{col} 이(가) 음수입니다 ({row[col]})")
    for col, allowed in CHOICES.items():
        if row[col] is not None and row[col] not in allowed:
            raise ValueError(f"{col} 은(는) {'/'.join(sorted(allowed))} 중 하나여야 합니다 ({row[col]!r})")
    for col in TIME_COLUMNS:
        if row[col] is not None:
            row[col] = _time(row[col])
    if row["data_date"] is not None:
        try:
            row["data_date"] = date.fromisoformat(row["data_date"])
        except ValueError:
            raise ValueError(f"data_date 는 YYYY-MM-DD 여야 합니다 ({row['data_date']!r})") from None
    return row


def parse_csv(path: Path) -> LoadResult:
    reader = csv.reader(read_text(path).splitlines())
    header = next(reader, None)
    if header is None:
        raise FormatError(f"{path.name}: 비어 있는 파일입니다")
    header = [h.strip() for h in header]
    if header != COLUMNS:
        missing = [c for c in COLUMNS if c not in header]
        extra = [c for c in header if c not in COLUMNS]
        detail = f"없는 열 {missing}, 모르는 열 {extra}" if missing or extra else "열 순서가 규격과 다릅니다"
        raise FormatError(f"{path.name}: 헤더가 규격과 다릅니다 — {detail}\n규격 순서: {','.join(COLUMNS)}")

    result = LoadResult()
    seen: dict[tuple[str, str], int] = {}
    for lineno, cells in enumerate(reader, start=2):
        if not any(c.strip() for c in cells):
            continue
        result.total += 1
        if len(cells) != len(COLUMNS):
            result.skipped.append((lineno, f"열 개수가 {len(cells)}개입니다 (규격 {len(COLUMNS)}개)"))
            continue
        try:
            row = parse_row(dict(zip(COLUMNS, cells)))
        except ValueError as e:
            result.skipped.append((lineno, str(e)))
            continue
        key = (row["source"], row["source_id"])
        if key in seen:  # 한 INSERT 안에서 같은 키가 두 번 나오면 PostgreSQL이 거부하므로 나중 행이 앞 행을 대체한다
            result.rows[seen[key]] = row
            result.duplicates += 1
        else:
            seen[key] = len(result.rows)
            result.rows.append(row)
    return result


async def upsert(rows: list[dict], database_url: str) -> int:
    engine = create_async_engine(database_url)
    try:
        async with engine.begin() as conn:  # 한 트랜잭션: 중간에 실패하면 아무것도 들어가지 않는다
            for i in range(0, len(rows), BATCH):
                stmt = insert(ParkingLot).values(rows[i : i + BATCH])
                update = {c: stmt.excluded[c] for c in COLUMNS if c not in ("source", "source_id")}
                update["updated_at"] = func.now()
                await conn.execute(stmt.on_conflict_do_update(index_elements=["source", "source_id"], set_=update))
    finally:
        await engine.dispose()
    return len(rows)


def summary(path: Path, result: LoadResult, loaded: int | None) -> str:
    lines = [f"{path.name}: 데이터 행 {result.total}개 중 {len(result.rows)}개 사용, {len(result.skipped)}개 건너뜀"]
    if result.duplicates:
        lines.append(f"  파일 안에서 같은 주차장이 또 나와 나중 행으로 대체: {result.duplicates}개")
    lines.append(f"  DB에 반영: {loaded}개" if loaded is not None else "  --dry-run: DB에는 쓰지 않았습니다")
    return "\n".join(lines)


def main(argv: list[str] | None = None) -> int:
    ap = argparse.ArgumentParser(description="주차장 CSV를 parking_lots 표에 넣는다")
    ap.add_argument("csv", type=Path, help="parking_lots.csv 경로 (보통 server/data/parking/)")
    ap.add_argument("--dry-run", action="store_true", help="검증만 하고 DB에는 쓰지 않는다")
    ap.add_argument("--database-url", help="기본은 .env의 DATABASE_URL")
    args = ap.parse_args(argv)

    if not args.csv.is_file():
        print(f"오류: 파일이 없습니다 — {args.csv}", file=sys.stderr)
        return 2
    try:
        result = parse_csv(args.csv)
    except FormatError as e:
        print(f"오류: {e}", file=sys.stderr)
        return 2
    for lineno, reason in result.skipped:
        print(f"건너뜀 {lineno}번째 줄: {reason}")

    loaded = None
    if not args.dry_run and result.rows:
        loaded = asyncio.run(upsert(result.rows, args.database_url or get_settings().database_url))
    print(summary(args.csv, result, loaded))
    return 0


if __name__ == "__main__":
    sys.exit(main())
