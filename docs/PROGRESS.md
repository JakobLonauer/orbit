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
  - SQLite-Speicher für Verlauf und Ablage (`store/HistoryDb`, Volltextsuche mit Treffer-Satz) und die einmalige Umstellung beim Start (`store/StoreUpgrade`: Kopie der alten Datei → Datenbank → neue Datei; nach Absturz wiederholbar ohne Doppelte) – getestet, aber noch nicht eingeschaltet.
  - Zuordnungs-Maschine (`Matcher`), `de-AT`-Formate (`AtFormat`), Backup-Verschlüsselung (`BackupCrypto`), Sichtbarkeitsregeln, Index (letzter Kontakt je Art, Suchtext, nächste Anlässe), Anrufer-Kurzindex (`CallerIndex`: Name, Bild, Rolle, Notiz, Kundennummer), Fehlerprotokoll (`util/ErrorLog`), Erinnerungs-Planer mit Sammeltext (`ReminderPlan`: „Heute: Annas 30. Geburtstag · In 7 Tagen: …“).
- **13.10.2026 (zweite Stunde):** In der App umgesetzt (auf `dev`, grün):
  - Fälligkeit komplett entfernt (3.0): kein Rhythmus, kein „fällig“, kein Pausieren/Verschieben, keine roten Glows; Gruppen und Eintragsarten ohne Rhythmus/Timer-Schalter. Datenfelder bleiben bis zur gesicherten v2-Umstellung im Speicher.
  - Kontaktseite: „zuletzt“ je Kontaktart statt Rhythmus. Orbit-Tab zeigt „Zuletzt in Kontakt“ (Heute/Woche/Monat/Länger/Noch nie) bis R1b den Verlauf-Tab bringt.
  - Widget = nächste Geburtstage (60 Tage). Tägliche Benachrichtigung nur Anlässe + Geburtstag in 7 Tagen mit Geschenkideen.
  - Archiv in den Einstellungen (3.11, Grundform): eigene Suche, Sortierung, nach Jahr gruppiert, entsättigt, Zurückholen. Archiv-Filter aus dem Filter-Sheet entfernt; Netz und Beziehungen anderer ohne Archivierte.
  - Fehlerprotokoll aktiv (Einstellungen › Fehlerprotokoll, teilen/leeren). Datumsformate app-weit de-AT.
  - Screenshot-Tests echter Screens (Orbit-Tab, Liste, Kontaktseite, Archiv) mit Testdaten – Bilder im Branch `ci-log-dev` unter `shots/`.
- **13.10.2026 (dritte Sitzung, 2 h):** R1a fertig auf `dev` (grün):
  - Speicher v2 aktiv: JSON-Kern + SQLite für Verlauf und Ablage (ADR-0005), einmalige Umstellung beim ersten Start mit Kopie `orbit.v1.bak.json`, Zeilenzahl-Prüfung, doppelte Zeilen werden zusammengeführt. Ladefehler ⇒ eigener Fehler-Bildschirm, Speichern und Sicherungen gesperrt.
  - Sicherungen: orbit.json + history.json + attachments.json; optional mit Passwort verschlüsselt (PBKDF2 + AES-256-GCM, in 1-MB-Blöcken gestreamt, Schlüssel Keystore-geschützt, Passwort nie gespeichert). Alte 1.0.7-Sicherungen lassen sich weiter einspielen.
  - Anrufer-Kurzindex wird nach jedem Speichern geschrieben (für R2).
  - Wow-Review R1a umgesetzt (Zählen der Zuletzt-Werte, nur zutreffende Aktionen, Leerzustände mit einer Aktion, keine Erklärtexte, keine abgeschnittenen Glows).
  - Zwei unabhängige Code-Reviews (Umstellung + Release) – alle bestätigten Funde behoben.
  - Bewusst verschoben: Org-Beziehungen → Zuordnungen erst in R4 (UI fehlt noch); Zuordnungs-Maschine/Index aus `:core:domain` werden in R1b/R2 in der App verdrahtet.
- **Wow-Gate R1a:** [x] berührte Screens gegen W1–W9 geprüft (Orbit-Tab, Kontaktseite, Liste, Archiv, Einstellungen) · [x] Screenshots angesehen + Wow-Review-Subagent · [x] bestätigte Funde behoben · [x] keine schwere Arbeit in der Composition neu hinzugekommen · [ ] Leer/lang/offline-Zustände als eigene Screenshots (kommt mit R1b-Screens).
- **Nächster Schritt:** Jakob entscheidet über das Release von R1a (Umstellung seiner Daten). Danach R1b (Hülle, PLAN 3.1–3.4, 3.11, 3.13).
- **Früherer nächster Schritt (R1a Rest, erledigt):** App auf `core.model` umstellen (OrbitRepository: Umstellung mit automatischer Kopie `orbit.v1.bak.json`, SQLite für Verlauf und Ablage), verschlüsseltes Backup einbauen (BackupCrypto ist fertig), Anrufer-Kurzindex beim Speichern schreiben, Token-System/Haptik/Platzhalter in den Screens. Erklärtexte, die noch übrig sind (Beziehungen-Leerzustand, „Tippen zum Bearbeiten“ u. a.), in R1b entfernen. Details zur Reihenfolge im Notizbuch.
- Letztes Release: v1.0.7 (Organisationen, ICH, Filter, Orbit-Liste, Netz, Karte, E-Mail, Fotos). `dev` ist nicht releasefähig, bis R1a fertig ist – `main` bleibt unverändert.

## Entscheidungen

(noch keine)

## Offene Punkte für Jakob

- Microsoft-Anmeldung (Outlook / PHOÖ): braucht eine App-Registrierung durch Jakob – bis dahin ohne.
- WhatsApp-Erkennung: nicht umsetzen, solange Jakob sie nicht ausdrücklich will.
