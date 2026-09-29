"""Write the wire contract as JSON Schema for frontend type generation.

Usage (from backend/):  venv/bin/python scripts/export_schema.py
Then (from frontend/):  npm run gen:types
"""
import json
import sys
from pathlib import Path

BACKEND = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(BACKEND))

from app.party.schema import contract_schema  # noqa: E402

OUT = BACKEND.parent / "frontend" / "src" / "party" / "contract.schema.json"


def main() -> None:
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(contract_schema(), indent=2, sort_keys=True, ensure_ascii=False) + "\n")
    print(f"wrote {OUT.relative_to(BACKEND.parent)}")


if __name__ == "__main__":
    main()
