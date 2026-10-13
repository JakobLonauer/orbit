# Fortschritt der Umsetzung (Gesamtplan v3)

Wird von jeder nächtlichen Sitzung gelesen und am Ende aktualisiert. Neueste Nacht oben.

## Arbeitsweise

- Entwicklung auf Branch `dev`. Push auf `dev` baut per CI ohne Release; Log im Branch `ci-log-dev`.
- Erst wenn ein Update fertig, grün gebaut und per Review geprüft ist: `dev` nach `main` mergen → CI baut und veröffentlicht das signierte Release (Log im Branch `ci-log`).
- Maßgeblich ist allein `docs/PLAN.md` (Rangfolge: Datensicherheit → Wow-Charta → Funktionen). `PLAN-history.md` ist nur Hintergrund.
- Pro Release eine Wow-Gate-Checkliste (PLAN 1.5) hier abhaken.
- Architektur/Engineering nach PLAN Kapitel 8 und ADRs in `docs/adr/`.
- Eigenes Notizbuch für Erfahrungen, Fehler und Kniffe: `.agent/NOTEBOOK.md` – vor der Arbeit lesen, während der Arbeit ergänzen.

## Stand

- Nächster Schritt: Test-Build der riskanten Bausteine (InCallService, Haze, geteilte Übergänge, Roborazzi-Screenshot-Tests, SQLite FTS4, AGSL-Shader) auf `dev`, danach R1a.
- Letztes Release: v1.0.7 (Organisationen, ICH, Filter, Orbit-Liste, Netz, Karte, E-Mail, Fotos).

## Entscheidungen

(noch keine)

## Offene Punkte für Jakob

- Microsoft-Anmeldung (Outlook / PHOÖ): braucht eine App-Registrierung durch Jakob – bis dahin ohne.
- WhatsApp-Erkennung: nicht umsetzen, solange Jakob sie nicht ausdrücklich will.
