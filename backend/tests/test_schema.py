import json
from pathlib import Path

from app.party.contract import JoinIn, PublicState
from app.party.schema import contract_schema

SCHEMA_PATH = Path(__file__).resolve().parents[2] / "frontend" / "src" / "party" / "contract.schema.json"


def test_wire_models_use_camel_case():
    payload = {"requestId": "r", "name": "  Jess ", "avatar": {"kind": "emoji", "value": "🔥"}, "teamId": "t"}
    join = JoinIn.model_validate(payload)
    assert join.name == "Jess" and join.team_id == "t"
    assert "nightId" in PublicState.model_json_schema(mode="serialization")["properties"]


def test_schema_names_every_definition_after_its_model():
    defs = contract_schema()["$defs"]
    assert {"PublicState", "PlayerView", "ShotMoment", "JoinIn", "HostState"} <= set(defs)
    assert all(d["title"] == key.replace("-", "") for key, d in defs.items())
    assert "title" not in defs["PlayerView"]["properties"]["name"]


def test_committed_schema_matches_the_models():
    committed = json.loads(SCHEMA_PATH.read_text())
    assert committed == contract_schema(), (
        "Contract changed. Run: venv/bin/python scripts/export_schema.py && (cd ../frontend && npm run gen:types)"
    )
