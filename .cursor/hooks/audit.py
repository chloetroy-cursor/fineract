#!/usr/bin/env python3
"""Cursor project audit hook (stdlib only).

Observes beforeSubmitPrompt, beforeShellExecution, afterFileEdit, and stop.
Always prints valid JSON and exits 0 so a logging failure cannot block the
agent. Field names follow https://cursor.com/docs/hooks (also mirrored in
the official cursor/cookbook hooks examples):

  Input (common): conversation_id, generation_id, model, hook_event_name
  Input (event):  prompt | command | file_path | status
  Output:
    beforeSubmitPrompt   -> {"continue": true}
    beforeShellExecution -> {"permission": "allow"}
    afterFileEdit, stop  -> {}
"""

from __future__ import annotations

import json
import sys
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

AUDIT_LOG = Path.home() / "kanini-audit.jsonl"
MAX_PREVIEW_CHARS = 240


def _now_iso() -> str:
    return datetime.now(timezone.utc).isoformat(timespec="milliseconds").replace("+00:00", "Z")


def _read_payload() -> dict[str, Any]:
    try:
        raw = sys.stdin.read()
        if not raw or not raw.strip():
            return {}
        data = json.loads(raw)
        return data if isinstance(data, dict) else {}
    except Exception:
        return {}


def _emit(payload: dict[str, Any]) -> None:
    try:
        sys.stdout.write(json.dumps(payload, separators=(",", ":")) + "\n")
        sys.stdout.flush()
    except Exception:
        sys.stdout.write("{}\n")
        sys.stdout.flush()


def _first_str(data: dict[str, Any], *keys: str) -> str | None:
    for key in keys:
        value = data.get(key)
        if isinstance(value, str) and value:
            return value
    return None


def _truncate(value: str | None) -> str | None:
    if not value:
        return None
    normalized = " ".join(value.split())
    if len(normalized) <= MAX_PREVIEW_CHARS:
        return normalized
    return normalized[:MAX_PREVIEW_CHARS] + "..."


def _infer_event(data: dict[str, Any]) -> str:
    named = _first_str(data, "hook_event_name")
    if named:
        return named
    if isinstance(data.get("prompt"), str):
        return "beforeSubmitPrompt"
    if isinstance(data.get("command"), str):
        return "beforeShellExecution"
    if isinstance(data.get("file_path"), str):
        return "afterFileEdit"
    if isinstance(data.get("status"), str):
        return "stop"
    return "unknown"


def _response_for(event: str) -> dict[str, Any]:
    if event == "beforeSubmitPrompt":
        return {"continue": True}
    if event == "beforeShellExecution":
        return {"permission": "allow"}
    return {}


def _record(data: dict[str, Any], event: str) -> dict[str, Any]:
    record: dict[str, Any] = {"timestamp": _now_iso(), "event": event}

    conversation_id = _first_str(data, "conversation_id")
    if conversation_id:
        record["conversation_id"] = conversation_id
    generation_id = _first_str(data, "generation_id")
    if generation_id:
        record["generation_id"] = generation_id
    model = _first_str(data, "model", "model_id")
    if model:
        record["model"] = model

    prompt = _truncate(_first_str(data, "prompt"))
    if prompt is not None:
        record["prompt"] = prompt
    command = _truncate(_first_str(data, "command"))
    if command is not None:
        record["command"] = command
    file_path = _first_str(data, "file_path")
    if file_path:
        record["file_path"] = file_path
    status = _first_str(data, "status")
    if status:
        record["status"] = status

    return record


def _append(record: dict[str, Any]) -> None:
    try:
        with AUDIT_LOG.open("a", encoding="utf-8") as handle:
            handle.write(json.dumps(record, separators=(",", ":")) + "\n")
    except Exception:
        return


def main() -> int:
    data = _read_payload()
    event = _infer_event(data)
    try:
        _append(_record(data, event))
    except Exception:
        pass
    _emit(_response_for(event))
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception:
        _emit({})
        raise SystemExit(0)
