from app.parsing.rule_based import RuleBasedParser
from app.schemas import Theme

parser = RuleBasedParser()


def test_family_with_pet_indoor_and_return_time():
    c = parser.parse("4살이랑 강아지 데리고 비 와도 갈 만한 곳, 6시 전 귀가")
    assert c.child_ages == [4]
    assert c.with_pet is True
    assert Theme.PET in c.themes
    assert c.indoor_only is True
    assert c.return_by_hour == 18


def test_infant_needs_and_drive_limit():
    c = parser.parse("18개월 아기랑 수유실 있는 박물관, 1시간 이내")
    assert c.child_ages == [0]
    assert c.needs == ["nursing_room"]
    assert Theme.EXHIBITION in c.themes
    assert c.max_drive_minutes == 60


def test_shopping_is_play_theme():
    c = parser.parse("비 오는 날 아이랑 쇼핑하면서 놀 곳")
    assert Theme.PLAY in c.themes


def test_negated_need_is_ignored():
    c = parser.parse("5살 7살 아이랑 체험, 유모차 필요 없고 수유실은 있으면 좋겠어")
    assert c.child_ages == [5, 7]
    assert c.needs == ["nursing_room"]


def test_empty_request_gives_defaults():
    c = parser.parse("어디 가지")
    assert c.child_ages == []
    assert c.themes == []
    assert c.return_by_hour is None


def test_interests_from_likes():
    c = parser.parse("공룡이랑 기차 좋아하는 5살, 티라노 보러 가고 싶어")
    assert c.interests == ["공룡", "기차"]


def test_interests_need_a_liking_phrase():
    c = parser.parse("강아지 데리고 산책할 공원, 4살 아이는 책 읽는 걸 좋아해")
    assert c.interests == ["책"]
    assert c.with_pet is True


def test_word_inside_another_word_is_not_an_interest():
    assert parser.parse("산책 좋아해").interests == []
