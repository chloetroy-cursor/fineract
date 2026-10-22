#!/usr/bin/env python3
"""Cursor beforeSubmitPrompt DLP hook (stdlib only).

Denies prompts that look like they contain a US SSN (###-##-####) or a
10-16 digit account/card number. Deny semantics follow
https://cursor.com/docs/hooks and the official cursor/cookbook
sensitive-prompt-guard example:

  Input:  {"prompt": "...", "conversation_id": "...", "generation_id": "...",
           "model": "...", "hook_event_name": "beforeSubmitPrompt"}
  Allow:  {"continue": true}
  Deny:   {"continue": false, "user_message": "<shown to the user>"}

Always prints valid JSON and exits 0. A parse failure fail-opens
({"continue": true}) so a bad payload cannot lock the demo; hooks.json
sets failClosed: true so a crash, timeout, or invalid JSON from this
process still blocks submission.
"""

from __future__ import annotations

import json
import re
import sys
from typing import Any

# US SSN as ###-##-####, not part of a longer digit run.
SSN_RE = re.compile(r"(?<!\d)\d{3}-\d{2}-\d{4}(?!\d)")
# 10-16 digits, optional single space or hyphen separators (cards / account nos).
ACCOUNT_OR_CARD_RE = re.compile(r"(?<!\d)(?:\d[ \-]?){9,15}\d(?!\d)")

ALLOW = {"continue": True}


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
        sys.stdout.write('{"continue":true}\n')
        sys.stdout.flush()


def _prompt_text(data: dict[str, Any]) -> str:
    value = data.get("prompt")
    return value if isinstance(value, str) else ""


def _findings(prompt: str) -> list[str]:
    found: list[str] = []
    if SSN_RE.search(prompt):
        found.append("US Social Security number (###-##-####)")
    # Do not also flag an SSN as an account number: strip SSN-shaped spans first.
    remainder = SSN_RE.sub(" ", prompt)
    if ACCOUNT_OR_CARD_RE.search(remainder):
        found.append("10-16 digit account or card number")
    return found


def _deny(findings: list[str]) -> dict[str, Any]:
    kinds = " and ".join(findings)
    message = (
        f"This prompt was blocked because it appears to contain a {kinds}. "
        "Remove or replace the sensitive value with fictional or masked data "
        "before resubmitting. No text was sent to the model."
    )
    return {"continue": False, "user_message": message}


def main() -> int:
    data = _read_payload()
    try:
        findings = _findings(_prompt_text(data))
        _emit(_deny(findings) if findings else ALLOW)
    except Exception:
        _emit(ALLOW)
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception:
        _emit(ALLOW)
        raise SystemExit(0)
