from app.party import ledger
from app.party.config import PartySettings
from app.party.ledger import ShotRec

MIN = 60_000
S = PartySettings()


def shot(sid, drinker, at, voided=None):
    return ShotRec(id=sid, drinker_id=drinker, logged_by_id=None, request_id=sid, source="manual",
                   created_at=at, voided_at=voided)


def test_live_counts_skip_voided_shots_and_unknown_players():
    shots = [shot("a", "jess", 1), shot("b", "jess", 2, voided=3), shot("c", "sam", 4), shot("d", "gone", 5)]
    assert ledger.live_counts(shots, ["jess", "sam", "alex"]) == {"jess": 1, "sam": 1, "alex": 0}


def test_team_totals_sum_members_and_include_empty_teams():
    counts = {"jess": 3, "sam": 2, "alex": 1}
    team_of = {"jess": "home", "sam": "away", "alex": "home"}
    assert ledger.team_totals(counts, team_of, ["home", "away", "bench"]) == {"home": 4, "away": 2, "bench": 0}


def test_sole_leader_needs_a_strict_lead_above_zero():
    assert ledger.sole_leader({"home": 4, "away": 2}) == "home"
    assert ledger.sole_leader({"home": 3, "away": 3}) is None
    assert ledger.sole_leader({"home": 0, "away": 0}) is None
    assert ledger.sole_leader({}) is None


def test_ranked_uses_competition_ranking_and_first_to_reach_breaks_ties():
    counts = {"jess": 5, "sam": 3, "alex": 5, "kim": 0}
    last_at = {"jess": 200, "sam": 50, "alex": 100, "kim": None}
    assert ledger.ranked(counts, last_at) == [("alex", 1), ("jess", 1), ("sam", 3), ("kim", 4)]


def test_streak_heating_up_is_two_shots_in_twenty_minutes():
    now = 100 * MIN
    assert ledger.streak_status([now - 25 * MIN, now - 1 * MIN], now, S) is None
    assert ledger.streak_status([now - 19 * MIN, now - 1 * MIN], now, S) == "heating"


def test_streak_on_fire_is_three_in_thirty_minutes_and_cools_after_thirty():
    base = 100 * MIN
    times = [base, base + 10 * MIN, base + 29 * MIN]
    assert ledger.streak_status(times, base + 29 * MIN, S) == "fire"
    assert ledger.streak_status(times, base + 29 * MIN + 30 * MIN, S) == "fire"
    assert ledger.streak_status(times, base + 29 * MIN + 31 * MIN, S) is None
    assert ledger.streak_status([base, base + 10 * MIN, base + 31 * MIN], base + 31 * MIN, S) == "heating"


def test_streak_with_no_shots_is_none():
    assert ledger.streak_status([], 0, S) is None


def test_party_milestones_cover_the_list_then_every_hundred():
    assert ledger.party_milestones_crossed(0, 24, S) == []
    assert ledger.party_milestones_crossed(24, 25, S) == [25]
    assert ledger.party_milestones_crossed(23, 27, S) == [25]
    assert ledger.party_milestones_crossed(49, 101, S) == [50, 100]
    assert ledger.party_milestones_crossed(199, 200, S) == [200]
    assert ledger.party_milestones_crossed(200, 200, S) == []


def test_player_milestones_are_every_fifth_shot():
    assert [n for n in range(0, 21) if ledger.is_player_milestone(n, S)] == [5, 10, 15, 20]
    assert not ledger.is_player_milestone(5, PartySettings(player_milestone_every=0))
