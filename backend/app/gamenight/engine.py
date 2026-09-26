"""Single-worker, server-authoritative games. Answers never enter public state before reveal."""
import asyncio
import html
import json
import random
import re
import urllib.request
import uuid
from collections import Counter
from typing import Literal

from pydantic import BaseModel, Field
from .. import models
from ..party.errors import PartyError
from .content import HOUSE, DRAW, RIFF


class HostIntent(BaseModel):
    action: Literal['start', 'next', 'reveal', 'pause', 'resume', 'lobby', 'refresh', 'fourTeams']
    mode: Literal['trivia', 'draw', 'riff'] = 'trivia'
    source: Literal['house', 'online'] = 'house'
    rounds: int = Field(default=8, ge=3, le=16)
    revision: int = -1


class PlayIntent(BaseModel):
    roundId: str
    action: Literal['answer', 'guess', 'write', 'vote', 'stroke', 'clear']
    value: str = Field(default='', max_length=120)
    points: list[tuple[float, float]] = Field(default_factory=list, max_length=120)
    color: Literal['#242331', '#f06a4f', '#7758d9', '#269f85'] = '#242331'


def normalize(s):
    return re.sub(r'[^a-z0-9]', '', s.lower())


def house_pack():
    return [dict(id=f'house-{i}', category=row[0], question=row[1], correct=row[2], options=list(row[2:]), source='HoopDreams house pack') for i, row in enumerate(HOUSE)]


def fetch_questions():
    req = urllib.request.Request('https://the-trivia-api.com/v2/questions?limit=50&difficulties=easy,medium', headers={'User-Agent': 'HoopDreams/1.0'})
    with urllib.request.urlopen(req, timeout=8) as response:
        rows = json.load(response)
    result = []
    for row in rows:
        if not isinstance(row, dict):
            continue
        question = row.get('question', {}).get('text', '')
        options = [row.get('correctAnswer', ''), *row.get('incorrectAnswers', [])]
        if not isinstance(question, str) or not question or len(question) > 145 or len(options) != 4:
            continue
        if not all(isinstance(x, str) and x and len(x) <= 70 for x in options) or len(set(options)) != 4:
            continue
        result.append(dict(id=str(row['id']), category=row.get('category', 'Trivia').replace('_', ' '), question=html.unescape(question), correct=html.unescape(options[0]), options=[html.unescape(x) for x in options], source='The Trivia API · CC BY-NC 4.0'))
    result = list({q['id']: q for q in result}.values())
    if len(result) < 8:
        raise ValueError('Not enough usable questions')
    return result


class GameNight:
    def __init__(self, service, sio):
        self.s = service
        self.sio = sio
        self.lock = asyncio.Lock()
        self.night = None
        self.data = {}
        self.refreshing = False

    def load(self):
        if self.night == self.s.night_id:
            return
        self.night = self.s.night_id
        with self.s.db() as db:
            row = db.get(models.Setting, (self.night, 'gameNight'))
            saved = json.loads(row.value_json) if row else {}
        self.data = saved if saved.get('nightId') == self.night else dict(nightId=self.night, mode=None, phase='lobby', revision=0, scores={}, bank=[], seen=[], round=0, rounds=8, roundId='', deadline=None, paused=False, remaining=0, answers={}, entries={}, votes={}, strokes=[], results=[], roster={})
        # A restart freezes an active round instead of silently consuming its clock.
        if self.data.get('deadline') and self.data['phase'] in ('answer', 'vote'):
            self.data.update(paused=True, remaining=max(1000, self.data['deadline'] - self.s.clock()), deadline=None)

    def save(self, *, control=True):
        if control:
            self.data['revision'] += 1
        self.s.save_setting('gameNight', json.dumps(self.data))

    def public(self):
        self.load()
        d = self.data
        q = d.get('question', {})
        reveal = d['phase'] in ('reveal', 'finished')
        return dict(nightId=self.night, mode=d['mode'], phase=d['phase'], revision=d['revision'], scores=d['scores'], round=d['round'], rounds=d['rounds'], roundId=d['roundId'], deadline=d['deadline'], paused=d['paused'], remaining=d['remaining'], prompt=q.get('question', '') if d['mode'] == 'trivia' else d.get('prompt', '') if d['mode'] == 'riff' or reveal else '', category=q.get('category', '') if d['mode'] == 'trivia' else '', options=q.get('options', []) if d['mode'] == 'trivia' else [], correct=q.get('correct') if reveal else None, artistId=d.get('artistId'), artistTeam=d.get('artistTeam'), strokes=d['strokes'], submitted=list(d['answers']), writtenTeams=list(d['entries']), voted=list(d['votes']), entries=[dict(id=e['id'], text=e['text'], teamId=t if reveal else None) for t, e in d['entries'].items()] if d['phase'] in ('vote', 'reveal', 'finished') else [], results=d['results'] if reveal else [], source=q.get('source', 'HoopDreams house pack'), bankCount=len(d['bank']), roster=d['roster'])

    def private(self, pid):
        self.load()
        d = self.data
        return dict(secret=d.get('prompt') if pid == d.get('artistId') and d['mode'] == 'draw' else None, answer=d['answers'].get(pid), vote=d['votes'].get(pid), ownEntry=d['entries'].get(d['roster'].get(pid), {}).get('id'))

    async def emit(self):
        await self.sio.emit('game:state', self.public())

    def participants(self):
        return {p.id: p.team_id for p in self.s.players.values() if p.team_id in self.s.teams}

    async def host(self, req):
        self.load()
        if req.action == 'refresh':
            if self.refreshing:
                raise PartyError('Questions are already loading')
            self.refreshing = True
            try:
                night = self.night
                questions = await asyncio.to_thread(fetch_questions)
                async with self.lock:
                    self.load()
                    if self.night != night:
                        raise PartyError('The night changed. Load questions again.')
                    self.data['bank'] = questions
                    self.save()
                    await self.emit()
            except (OSError, ValueError, KeyError, TypeError):
                raise PartyError('Could not load fresh questions. The offline house pack is ready to play.')
            finally:
                self.refreshing = False
            return
        async with self.lock:
            d = self.data
            if req.revision != d['revision']:
                raise PartyError('The game changed. Try again.')
            if req.action == 'fourTeams':
                from ..party.admin import PartyAdmin
                from ..party.contract import TeamUpsertIn
                for name, color in [('HOT SHOTS', '#ad91ff'), ('WILD CARDS', '#73dcb3')]:
                    if len(self.s.teams) < 4:
                        names = {t.name.casefold() for t in self.s.teams.values()}
                        if name.casefold() in names:
                            name = f'TEAM {len(self.s.teams) + 1}'
                            while name.casefold() in names:
                                name += '!'
                        await PartyAdmin(self.s).upsert_team(TeamUpsertIn(name=name, color=color))
            elif req.action == 'start':
                if d['phase'] not in ('lobby', 'finished'):
                    raise PartyError('Return to the lobby before starting another game')
                if len(set(self.participants().values())) < 2:
                    raise PartyError('Join at least two teams on phones first')
                deck = []
                if req.mode == 'trivia':
                    pool = d['bank'] if req.source == 'online' else house_pack()
                    deck = [q.copy() for q in pool if q['id'] not in d['seen']]
                    if len(deck) < req.rounds:
                        raise PartyError('Not enough unseen questions. Load fresh questions or choose the other pack.')
                    random.shuffle(deck)
                    deck = deck[:req.rounds]
                    for q in deck:
                        q['options'] = random.sample(q['options'], 4)
                d.update(mode=req.mode, round=0, rounds=req.rounds, deck=deck, drawDeck=random.sample(DRAW, len(DRAW)), riffDeck=random.sample(RIFF, len(RIFF)))
                self.next_round()
            elif req.action == 'next':
                if d['phase'] != 'reveal':
                    raise PartyError('Reveal the round first')
                if d['round'] >= d['rounds']:
                    d.update(phase='finished', deadline=None)
                else:
                    self.next_round()
            elif req.action == 'reveal':
                if d['phase'] not in ('answer', 'vote'):
                    raise PartyError('No live round')
                self.finish_phase()
            elif req.action == 'pause':
                if d['phase'] not in ('answer', 'vote') or d['paused']:
                    raise PartyError('No running timer')
                d.update(remaining=max(0, d['deadline'] - self.s.clock()), deadline=None, paused=True)
            elif req.action == 'resume':
                if not d['paused']:
                    raise PartyError('Game is not paused')
                d.update(deadline=self.s.clock() + d['remaining'], paused=False)
            elif req.action == 'lobby':
                d.update(phase='lobby', mode=None, deadline=None, paused=False, strokes=[], results=[])
            self.save()
            await self.emit()

    def next_round(self):
        d = self.data
        roster = self.participants()
        if not roster:
            raise PartyError('No players remain. Return to the lobby.')
        d.update(round=d['round'] + 1, roundId=str(uuid.uuid4()), phase='answer', paused=False, answers={}, entries={}, votes={}, strokes=[], results=[], roster=roster, guessedTeams=[], artistId=None, artistTeam=None, question={}, prompt='')
        if d['mode'] == 'trivia':
            d['question'] = d['deck'][d['round'] - 1]
            d['seen'].append(d['question']['id'])
            seconds = 30
        elif d['mode'] == 'draw':
            teams = [t.id for t in sorted(self.s.teams.values(), key=lambda t: t.sort) if t.id in roster.values()]
            if not teams:
                raise PartyError('No teams remain. Return to the lobby.')
            team = teams[(d['round'] - 1) % len(teams)]
            players = sorted((p for p in self.s.players.values() if p.team_id == team), key=lambda p: (p.created_at, p.id))
            artist = players[((d['round'] - 1) // len(teams)) % len(players)]
            d.update(artistId=artist.id, artistTeam=team, prompt=d['drawDeck'][(d['round'] - 1) % len(DRAW)])
            seconds = 75
        else:
            d['prompt'] = d['riffDeck'][(d['round'] - 1) % len(RIFF)]
            seconds = 60
        d['deadline'] = self.s.clock() + seconds * 1000

    def award(self, team, amount):
        self.data['scores'][team] = self.data['scores'].get(team, 0) + amount

    def finish_phase(self):
        d = self.data
        if d['mode'] == 'riff' and d['phase'] == 'answer' and len(d['entries']) >= 2:
            entries = list(d['entries'].items())
            random.shuffle(entries)
            d.update(entries=dict(entries), phase='vote', deadline=self.s.clock() + 25_000, paused=False)
            return
        results = []
        if d['mode'] == 'trivia':
            for team in dict.fromkeys(d['roster'].values()):
                answers = [v for p, v in d['answers'].items() if d['roster'].get(p) == team]
                answer = Counter(answers).most_common(1)[0][0] if answers else None
                points = 1000 if answer == d['question']['correct'] else 0
                self.award(team, points)
                results.append(dict(teamId=team, answer=answer or 'No answer', points=points))
        elif d['mode'] == 'draw':
            for team in dict.fromkeys(d['roster'].values()):
                points = 1000 if team in d.get('guessedTeams', []) else 500 * len(d.get('guessedTeams', [])) if team == d['artistTeam'] else 0
                self.award(team, points)
                results.append(dict(teamId=team, answer='The artist' if team == d['artistTeam'] else 'Got it!' if points else 'Next time!', points=points))
        else:
            # One effective ballot per team; majority wins, ties use earliest received vote.
            counts = Counter()
            for team in dict.fromkeys(d['roster'].values()):
                votes = [v for p, v in d['votes'].items() if d['roster'].get(p) == team]
                if votes:
                    counts[Counter(votes).most_common(1)[0][0]] += 1
            for team, entry in d['entries'].items():
                points = counts[entry['id']] * 500
                self.award(team, points)
                results.append(dict(teamId=team, answer=entry['text'], points=points))
        d.update(phase='reveal', results=results, deadline=None, paused=False)

    async def play(self, pid, req):
        async with self.lock:
            self.load()
            d = self.data
            if req.roundId != d['roundId'] or d['phase'] not in ('answer', 'vote'):
                raise PartyError('That round has ended')
            if d['paused']:
                raise PartyError('The host paused the game')
            if self.s.clock() >= d['deadline']:
                self.finish_phase()
                self.save()
                await self.emit()
                raise PartyError('Time is up')
            team = d['roster'].get(pid)
            if not team or pid not in self.s.players:
                raise PartyError('You will join the next round')
            if req.action == 'answer' and d['mode'] == 'trivia' and d['phase'] == 'answer':
                if req.value not in d['question']['options']:
                    raise PartyError('Choose one of the four answers')
                if pid in d['answers']:
                    if d['answers'][pid] == req.value:
                        return self.private(pid)
                    raise PartyError('Your answer is already locked')
                d['answers'][pid] = req.value
            elif req.action == 'write' and d['mode'] == 'riff' and d['phase'] == 'answer':
                if team in d['entries']:
                    raise PartyError('Your team already submitted')
                if not req.value.strip():
                    raise PartyError('Write an answer first')
                d['entries'][team] = dict(id=str(uuid.uuid4()), text=req.value.strip())
            elif req.action == 'vote' and d['mode'] == 'riff' and d['phase'] == 'vote':
                target = next((t for t, e in d['entries'].items() if e['id'] == req.value), None)
                if not target or target == team:
                    raise PartyError('Vote for another team')
                if pid in d['votes']:
                    raise PartyError('Your vote is already locked')
                d['votes'][pid] = req.value
            elif req.action == 'guess' and d['mode'] == 'draw':
                if team == d['artistTeam']:
                    raise PartyError('Your team is drawing this round')
                if normalize(req.value) not in (normalize(d['prompt']), normalize(d['prompt']) + 's'):
                    return dict(correct=False)
                if team not in d.setdefault('guessedTeams', []):
                    d['guessedTeams'].append(team)
                d['answers'][pid] = 'Got it!'
            elif req.action in ('stroke', 'clear') and d['mode'] == 'draw' and pid == d['artistId']:
                if req.action == 'clear':
                    d['strokes'] = []
                else:
                    if len(d['strokes']) >= 600:
                        raise PartyError('Canvas is full. Clear it to keep drawing.')
                    if not req.points or any(not (0 <= x <= 1 and 0 <= y <= 1) for x, y in req.points):
                        raise PartyError('Invalid drawing')
                    d['strokes'].append(dict(points=req.points, color=req.color))
            else:
                raise PartyError('That action is not available')
            self.save(control=False)
            await self.emit()
            return dict(**self.private(pid), correct=True)

    async def tick(self):
        async with self.lock:
            old_night = self.night
            self.load()
            if old_night != self.night:
                await self.emit()
            d = self.data
            if d['deadline'] and self.s.clock() >= d['deadline'] and d['phase'] in ('answer', 'vote'):
                self.finish_phase()
                self.save()
                await self.emit()

    async def run(self):
        while True:
            await asyncio.sleep(.5)
            await self.tick()
