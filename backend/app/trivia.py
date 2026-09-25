import asyncio
import html
import random

import httpx

OPENTDB_BASE = "https://opentdb.com"
DIFFICULTIES = ["easy", "medium", "hard"]

_session_token: str | None = None


class TriviaError(Exception):
    pass


async def _get_session_token(client: httpx.AsyncClient) -> str:
    global _session_token
    if _session_token is None:
        resp = await client.get(f"{OPENTDB_BASE}/api_token.php", params={"command": "request"})
        resp.raise_for_status()
        _session_token = resp.json()["token"]
    return _session_token


async def _reset_session_token(client: httpx.AsyncClient) -> None:
    global _session_token
    if _session_token:
        await client.get(
            f"{OPENTDB_BASE}/api_token.php",
            params={"command": "reset", "token": _session_token},
        )


def _decode(text: str) -> str:
    return html.unescape(text)


async def _get_with_retry(client: httpx.AsyncClient, url: str, params: dict) -> dict:
    """OpenTDB allows one request per IP every 5 seconds and returns HTTP 429
    (plain text, not JSON) if that's exceeded — retry a couple of times."""
    last_error: Exception | None = None
    for attempt in range(3):
        if attempt > 0:
            await asyncio.sleep(5)
        try:
            resp = await client.get(url, params=params)
            if resp.status_code == 429:
                last_error = TriviaError("rate limited by OpenTDB")
                continue
            resp.raise_for_status()
            return resp.json()
        except (httpx.HTTPError, ValueError) as exc:
            last_error = exc
    raise TriviaError(str(last_error))


async def fetch_question(difficulty: str | None = None) -> dict:
    chosen_difficulty = difficulty if difficulty in DIFFICULTIES else random.choice(DIFFICULTIES)

    async with httpx.AsyncClient(timeout=10) as client:
        token = await _get_session_token(client)
        params = {
            "amount": 1,
            "type": "multiple",
            "difficulty": chosen_difficulty,
            "token": token,
        }
        data = await _get_with_retry(client, f"{OPENTDB_BASE}/api.php", params)

        if data["response_code"] == 4:
            # session token has served every question for this filter — reset and retry once
            await _reset_session_token(client)
            data = await _get_with_retry(client, f"{OPENTDB_BASE}/api.php", params)

        if data["response_code"] != 0 or not data["results"]:
            raise TriviaError(f"OpenTDB returned response_code {data['response_code']}")

        raw = data["results"][0]

    answers = [
        {"text": _decode(raw["correct_answer"]), "correct": True},
        *[{"text": _decode(a), "correct": False} for a in raw["incorrect_answers"]],
    ]
    random.shuffle(answers)

    return {
        "category": _decode(raw["category"]),
        "difficulty": raw["difficulty"],
        "question": _decode(raw["question"]),
        "answers": answers,
    }
