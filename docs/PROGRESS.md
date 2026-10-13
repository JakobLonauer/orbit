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

- **13.10.2026 (Tagsitzung, ~1 h):** Test-Build abgeschlossen, alle riskanten Bausteine grün (CI-Run 8, erster Versuch): InCallService-Probe, Haze-Milchglas (Blur rendert sogar im Screenshot-Test), geteilte Übergänge, Roborazzi-Screenshots, SQLite FTS4 inkl. `snippet()` und umlaut-unabhängiger Suche, AGSL-Holo-Shader.
- R1a begonnen – reine, getestete Bausteine in `:core:*` (noch nicht in der App verdrahtet):
  - Datenmodell v2 (`core/model`) und eingefrorenes Format 1.0.7 (`core/model/v1`).
  - Umstellung 1.0.7 → v2 (`core/domain/migration/MigrateV1`) mit Testdaten im alten Format: Geburtstag/Jahrestage → Anlässe, Webseite → erster Link, Fotos → Ablage, Verlauf → SQLite-Zeilen, Org-Beziehungen → Zuordnungen, Fälligkeit/Rhythmus entfernt.
  - Zuordnungs-Maschine (`Matcher`), `de-AT`-Formate (`AtFormat`), Backup-Verschlüsselung (`BackupCrypto`), Sichtbarkeitsregeln, Index (letzter Kontakt je Art, Suchtext, nächste Anlässe).
- **Nächster Schritt (R1a Rest):** App auf `core.model` umstellen (OrbitRepository: Umstellung mit automatischer Kopie `orbit.v1.bak.json`, SQLite für Verlauf und Ablage), Fälligkeit aus App/Widget/Benachrichtigung entfernen, Archiv-Logik in Listen, verschlüsseltes Backup einbauen, Fehlerprotokoll, Token-System/Haptik/Platzhalter in den Screens. Details zur Reihenfolge im Notizbuch.
- Letztes Release: v1.0.7 (Organisationen, ICH, Filter, Orbit-Liste, Netz, Karte, E-Mail, Fotos). `dev` ist nicht releasefähig, bis R1a fertig ist – `main` bleibt unverändert.

## Entscheidungen

(noch keine)

## Offene Punkte für Jakob

- Microsoft-Anmeldung (Outlook / PHOÖ): braucht eine App-Registrierung durch Jakob – bis dahin ohne.
- WhatsApp-Erkennung: nicht umsetzen, solange Jakob sie nicht ausdrücklich will.
