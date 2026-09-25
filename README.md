# HoopDreams 🏀

Dashboard for HoopDreams game & drinking nights. Pull a game out of the hat (Trivia, Pictionary, Charades, and more), then play it live in person.

## Stack

- **Frontend**: React + TypeScript (Vite)
- **Backend**: Python (FastAPI)
- **Database**: SQLite (via SQLAlchemy)

## Running locally

### Backend

```bash
cd backend
python3 -m venv venv
venv/bin/pip install -r requirements.txt
venv/bin/uvicorn app.main:app --reload --port 8000
```

This seeds the game list on first run and creates `backend/hoopdreams.db`.

### Frontend

```bash
cd frontend
npm install
npm run dev -- --port 5180
```

Open the printed URL (defaults to `http://localhost:5180`). The frontend expects the API at `http://localhost:8000` (see `frontend/.env`).

## How it works

- The hat holds every active game from the `games` table.
- Tapping the hat draws a random game that hasn't come up yet this round (`POST /api/draw`), logs it to the `draws` table, and shows it as a card.
- Once every game has been drawn, the next tap reshuffles the hat automatically.
- "Recently drawn" pulls the last few draws from `GET /api/history`.
- Add or edit games directly in `backend/app/seed_data.py` (only used to seed an empty database) or the `games` table.
