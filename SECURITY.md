# Security Policy

## Supported Versions

c3kit-jig is pre-1.0. Security fixes target the latest release tag on the `main` branch. Older tags receive no backports.

## Reporting a Vulnerability

Please report security issues privately via [GitHub's "Report a vulnerability"](https://github.com/cleancoders/c3kit-jig/security/advisories/new) workflow, or by emailing the maintainer at gina@cleancoders.com. Do not open a public issue for security-sensitive reports.

The CLI installer downloads and executes a Babashka uberscript. If you find an issue with the installer's verification of release artifacts (signature, checksum, or transport), please report it through the channels above.

We aim to acknowledge reports within 5 business days.
