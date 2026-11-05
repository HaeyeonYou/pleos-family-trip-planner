import time
from collections.abc import Callable, Hashable
from typing import TypeVar

T = TypeVar("T")


class TtlCache:
    """작은 메모리 캐시. TourAPI 개발계정 한도(하루 1,000건)를 아끼려고 쓴다."""

    def __init__(self, ttl_seconds: float):
        self._ttl = ttl_seconds
        self._items: dict[Hashable, tuple[float, object]] = {}

    def get_or_load(self, key: Hashable, load: Callable[[], T]) -> T:
        now = time.monotonic()
        hit = self._items.get(key)
        if hit and now - hit[0] < self._ttl:
            return hit[1]  # type: ignore[return-value]
        value = load()
        self._items[key] = (now, value)
        return value
