# Security Policy

ExifDrop takes security and privacy seriously. Report vulnerabilities privately; do not open a public issue.

## Reporting a Vulnerability

1. Open a private advisory: https://github.com/AkashPriyadarshii/exifdrop/security/advisories
2. Include: affected version, minimal reproduction, impact estimate.

Acknowledgment within 48 hours. Coordinated disclosure after a fix ships.

## Security Expectations

- The app declares no INTERNET permission. No metadata or file content leaves the device.
- Stripped copies live in app-private cache and purge automatically.
- The original source file is never modified.
- Metadata stripping is lossless: pixel and stream data are copied, never re-encoded, so malformed input cannot be introduced by a re-encode pass.
- Inputs are treated as hostile where they cross a trust boundary: share URIs are sanitized, filenames are tamed against traversal, and chunk sizes are bounded before read.