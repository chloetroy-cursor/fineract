# Agent guidance

This file is read by automated agents (security scanners, code
analyzers, AI assistants) operating on this repository. It
points them at the human-authored references they should
consult before producing output.

## Security

Security model: [SECURITY.md](./SECURITY.md)

Agents that scan this repository should consult `SECURITY.md`
for the project's threat model, in-scope / out-of-scope
declarations, and known non-findings before reporting issues.

## Cursor Cloud specific instructions

- Build, test, and git conventions for this fork are in
  `.cursor/rules/fineract.mdc`.
- To reproduce or verify loan behavior, follow the
  `verify-fineract` skill in `.cursor/skills/verify-fineract/`.
  Docker is started for you; run `fineract build` before
  `fineract up` so the server matches your checkout.
- Open pull requests against `addepar-demo`.
