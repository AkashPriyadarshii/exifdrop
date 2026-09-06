# Contributing to ExifDrop

Contributions are welcome. Open an issue before large changes.

## Workflow

1. Branch off main: `feat/` or `fix/`.
2. Keep changes minimal. YAGNI: no speculative features or abstractions.
3. Add or update tests for your change.
4. Run the test suite locally. The full suite must pass.
5. Update docs and CHANGELOG.md in the same commit.
6. Open a PR. State what changed and how it was tested.

## Ground rules

This repo has a CLAUDE.md that agents and maintainers follow. Read it before touching the strip path. Non-negotiables:

- Never re-encode image pixels. Lossless strip only.
- Never touch the source file. Write to app-private cache.
- Never add a stale or CVE'd dependency.
- Never add the INTERNET permission.

## Standards

- Smallest diff that solves the problem.
- Delete over add. No dead code.
- Boring over clever. Explicit code beats magic.
- Stdlib over new dependencies. No dependency for what a few lines do.

## Code of Conduct

All contributors agree to CODE_OF_CONDUCT.md.