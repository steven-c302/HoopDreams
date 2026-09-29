import asyncio
import pytest
from app.gamenight.engine import GameNight, HostIntent, PlayIntent
from app.party.errors import PartyError
from conftest import join

class Transport:
    async def emit(self, event, data):
        pass

@pytest.fixture
async def game(service):
    game = GameNight(service, Transport())
    game.public()
    await join(service, 'Alex', 0)
    await join(service, 'Blake', 1)
    return game

async def host(g, action, **kw):
    return await g.host(HostIntent(action=action, revision=g.data['revision'], **kw))

async def play(g, pid, action, **kw):
    return await g.play(pid, PlayIntent(roundId=g.data['roundId'], action=action, **kw))

async def test_majority_tie_lock_and_reveal_privacy(game):
    g = game
    a, b = list(g.s.players)
    c = await join(g.s, 'Casey', 0)
    await host(g, 'start')
    answer = g.data['question']['correct']
    wrong = next(x for x in g.data['question']['options'] if x != answer)
    assert g.public()['correct'] is None
    await play(g, a, 'answer', value=answer)
    await play(g, c, 'answer', value=wrong)
    await play(g, b, 'answer', value=wrong)
    with pytest.raises(PartyError, match='locked'):
        await play(g, a, 'answer', value=wrong)
    await play(g, a, 'answer', value=answer)
    await host(g, 'reveal')
    assert g.public()['correct'] == answer
    team = g.s.players[a].team_id
    assert g.data['scores'][team] == 1000
    with pytest.raises(PartyError):
        await host(g, 'reveal')
    assert g.data['scores'][team] == 1000

async def test_server_deadline_pause_resume_and_late_packets(game, clock):
    g = game
    await host(g, 'start')
    pid = next(iter(g.s.players))
    answer = g.data['question']['correct']
    await host(g, 'pause')
    clock.advance(90_000)
    await g.tick()
    assert g.data['phase'] == 'answer'
    with pytest.raises(PartyError, match='paused'):
        await play(g, pid, 'answer', value=answer)
    await host(g, 'resume')
    clock.advance(30_001)
    with pytest.raises(PartyError, match='Time is up'):
        await play(g, pid, 'answer', value=answer)
    assert g.data['phase'] == 'reveal'
    old_round = g.data['roundId']
    await host(g, 'next')
    with pytest.raises(PartyError, match='ended'):
        await g.play(pid, PlayIntent(roundId=old_round, action='answer', value=answer))

async def test_drawing_secret_auth_rotation_and_scoring(game):
    g = game
    await host(g, 'start', mode='draw')
    artist = g.data['artistId']
    guesser = next(p for p in g.s.players if p != artist)
    assert not g.public()['prompt']
    assert g.private(guesser)['secret'] is None
    secret = g.private(artist)['secret']
    assert secret
    with pytest.raises(PartyError):
        await play(g, guesser, 'stroke', points=[(.1, .2)])
    with pytest.raises(PartyError):
        await play(g, artist, 'stroke', points=[(2, .2)])
    await play(g, artist, 'stroke', points=[(.1, .2), (.3, .4)])
    assert len(g.public()['strokes']) == 1
    await play(g, guesser, 'guess', value=secret)
    await play(g, guesser, 'guess', value=secret)
    await host(g, 'reveal')
    assert g.data['scores'][g.s.players[artist].team_id] == 500
    assert g.data['scores'][g.s.players[guesser].team_id] == 1000
    await host(g, 'next')
    assert g.data['artistId'] == guesser
    assert g.data['guessedTeams'] == []
    assert g.data['strokes'] == []
    await host(g, 'reveal')
    assert g.data['scores'][g.s.players[guesser].team_id] == 1000

async def test_riff_anonymous_entries_no_self_vote_and_team_ballots(game):
    g = game
    a, b = list(g.s.players)
    c = await join(g.s, 'Casey', 0)
    await host(g, 'start', mode='riff')
    await play(g, a, 'write', value='Emergency glitter')
    with pytest.raises(PartyError, match='already submitted'):
        await play(g, c, 'write', value='Another answer')
    await play(g, b, 'write', value='A suspicious duck')
    assert g.public()['entries'] == []
    await host(g, 'reveal')
    assert g.data['phase'] == 'vote'
    assert all(e['teamId'] is None for e in g.public()['entries'])
    own = g.data['entries'][g.s.players[a].team_id]['id']
    other = g.data['entries'][g.s.players[b].team_id]['id']
    with pytest.raises(PartyError, match='another team'):
        await play(g, a, 'vote', value=own)
    await play(g, a, 'vote', value=other)
    await play(g, c, 'vote', value=other)
    await host(g, 'reveal')
    assert g.data['scores'][g.s.players[b].team_id] == 500

async def test_restore_and_new_night_isolation(game):
    g = game
    await host(g, 'start')
    pid = next(iter(g.s.players))
    await play(g, pid, 'answer', value=g.data['question']['correct'])
    restored = GameNight(g.s, Transport())
    assert restored.public()['paused']
    assert pid in restored.public()['submitted']
    await host(restored, 'reveal')
    again = GameNight(g.s, Transport())
    assert again.public()['scores'] == restored.data['scores']
    await g.s.new_night('Tomorrow')
    await again.tick()
    assert again.public()['phase'] == 'lobby'
    assert again.public()['scores'] == {}

async def test_stale_host_commands_and_answer_traffic(game):
    g = game
    await host(g, 'start')
    revision = g.data['revision']
    await play(g, next(iter(g.s.players)), 'answer', value=g.data['question']['correct'])
    await g.host(HostIntent(action='pause', revision=revision))
    with pytest.raises(PartyError, match='changed'):
        await g.host(HostIntent(action='pause', revision=revision))

async def test_empty_riff_and_finale(game):
    g = game
    await host(g, 'start', mode='riff', rounds=3)
    for _ in range(3):
        await host(g, 'reveal')
        assert g.data['phase'] == 'reveal'
        await host(g, 'next')
    assert g.data['phase'] == 'finished'

async def test_question_dedup_four_teams_and_failed_provider(game, monkeypatch):
    g = game
    await host(g, 'fourTeams')
    assert len(g.s.teams) == 4
    await host(g, 'start', rounds=16)
    ids1 = {q['id'] for q in g.data['deck']}
    for _ in range(16):
        await host(g, 'reveal')
        await host(g, 'next')
    await host(g, 'start', rounds=16)
    assert not ids1.intersection(q['id'] for q in g.data['deck'])
    def fail():
        raise OSError('offline')
    monkeypatch.setattr('app.gamenight.engine.fetch_questions', fail)
    with pytest.raises(PartyError, match='offline house pack'):
        await host(g, 'refresh')
    assert g.data['phase'] == 'answer'

async def test_sixteen_players_four_teams_simultaneous_answers(game):
    g = game
    await host(g, 'fourTeams')
    for i in range(14):
        await join(g.s, f'Guest {i}', (i + 2) % 4)
    await host(g, 'start')
    assert len(g.data['roster']) == 16
    await asyncio.gather(*(play(g, pid, 'answer', value=g.data['question']['correct']) for pid in g.s.players))
    await host(g, 'reveal')
    assert list(g.data['scores'].values()) == [1000] * 4

async def test_socket_permissions_and_sync(connect):
    screen = await connect()
    sync = await screen.call('game:sync', {})
    assert sync['ok'] and sync['state']['phase'] == 'lobby'
    assert sync['secret'] is None
    assert not (await screen.call('game:host', {'action':'start'}))['ok']
    assert not (await screen.call('game:play', {'action':'answer','roundId':'fake'}))['ok']
