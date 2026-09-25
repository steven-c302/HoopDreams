"""JSON Schema export of the wire contract, consumed by json-schema-to-typescript."""
from typing import Any

from pydantic.json_schema import models_json_schema

from .contract import WIRE_MODELS


def contract_schema() -> dict[str, Any]:
    _, schema = models_json_schema(WIRE_MODELS, title="HoopContract")
    _clean(schema, is_definition=True)
    for key, definition in schema.get("$defs", {}).items():
        definition["title"] = key.replace("-", "")  # PartySettings-Input -> PartySettingsInput
    return schema


def _clean(node: Any, *, is_definition: bool) -> None:
    """Drop pydantic's per-field titles; otherwise json2ts emits an alias type for every field."""
    if isinstance(node, dict):
        if not is_definition:
            node.pop("title", None)
        for key, value in node.items():
            if key == "$defs":
                for definition in value.values():
                    _clean(definition, is_definition=True)
            elif key == "properties":
                for prop in value.values():
                    _clean(prop, is_definition=False)
            else:
                _clean(value, is_definition=False)
    elif isinstance(node, list):
        for item in node:
            _clean(item, is_definition=False)
