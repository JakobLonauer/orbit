# ADR-0004: dev-Zweig und Releases nur von main

Status: angenommen (13.10.2026)

Kontext: Jeder grüne Build auf `main` wird sofort als signiertes Update an Jakobs Handy verteilt; unfertige Stände dürfen nicht hinaus.

Entscheidung: Entwicklung auf `dev` (CI baut, kein Release, Log in `ci-log-dev`). Merge nach `main` erst nach Definition of Done (PLAN 8.4). Tag-Format `v1.0.<run_number>` bleibt, weil der Updater die Versionsnummer daraus liest.
