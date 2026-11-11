"""20km를 넘는 반경을 여러 중심점으로 나눠 찾는 규칙 (#250). 좌표 계산이라 가짜 검색 함수로 확인한다."""

from dataclasses import dataclass

from app.course import wide_area
from app.course.wide_area import API_MAX_RADIUS_M, gather, search_centers

ORIGIN = (37.4979, 127.0276)


@dataclass(frozen=True)
class P:
    id: str
    latitude: float
    longitude: float
    distance_m: float = 0.0


def test_up_to_20km_is_one_search_with_same_radius():
    assert search_centers(*ORIGIN, 5_000) == [(*ORIGIN, 5_000)]
    assert search_centers(*ORIGIN, 20_000) == [(*ORIGIN, 20_000)]


def test_wider_radius_adds_ring_of_20km_searches():
    c30 = search_centers(*ORIGIN, 30_000)
    c50 = search_centers(*ORIGIN, 50_000)
    assert len(c30) == 7 and len(c50) == 9
    assert all(r == API_MAX_RADIUS_M for _, _, r in c50)
    # 고리 중심점은 출발지에서 (20 + 50) / 2 = 35km
    ring = [wide_area._haversine_m(*ORIGIN, lat, lng) for lat, lng, _ in c50[1:]]
    assert all(abs(d - 35_000) < 50 for d in ring)


def test_ring_covers_points_between_centers():
    # 50km 반경 안의 점은 (고리 바깥 끝 일부를 빼고) 어느 중심점의 20km 안에 든다
    centers = search_centers(*ORIGIN, 50_000)
    for bearing in range(0, 360, 5):
        for dist in (5_000, 19_000, 25_000, 35_000, 45_000):
            lat, lng = wide_area._offset(*ORIGIN, dist, bearing / 57.2958)
            assert any(wide_area._haversine_m(c[0], c[1], lat, lng) <= c[2] for c in centers), (bearing, dist)


def test_gather_merges_dedupes_refilters_and_sorts_by_origin_distance():
    near = P("a", 37.50, 127.03, 999)  # 출발지 근처 (거리는 다시 잰다)
    far_lat, far_lng = wide_area._offset(*ORIGIN, 40_000, 0)
    far = P("b", far_lat, far_lng)
    outside_lat, outside_lng = wide_area._offset(*ORIGIN, 54_000, 0)
    outside = P("c", outside_lat, outside_lng)
    calls = []

    def fetch(lat, lng, r):
        calls.append((lat, lng, r))
        return [near, far, outside] if len(calls) > 1 else [near]

    found = gather(*ORIGIN, 50_000, fetch, key=lambda p: p.id)
    assert len(calls) == 9
    assert [p.id for p in found] == ["a", "b"]  # 겹침 제거, 반경 밖 제외, 가까운 순
    assert found[0].distance_m < 1_000 and abs(found[1].distance_m - 40_000) < 50


def test_gather_skips_failed_ring_center_but_not_origin():
    def fetch(lat, lng, r):
        if (lat, lng) != ORIGIN:
            raise RuntimeError("down")
        return [P("a", 37.50, 127.03)]

    assert [p.id for p in gather(*ORIGIN, 40_000, fetch, key=lambda p: p.id)] == ["a"]
    assert [p.id for p in gather(*ORIGIN, 10_000, fetch, key=lambda p: p.id)] == ["a"]
