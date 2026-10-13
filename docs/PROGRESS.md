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
- **R1a veröffentlicht als v1.0.54** (13.10.2026, Jakobs Zusage): `release-r1a` → `main` (fast-forward), CI-Run 54 grün, APK-Zertifikat geprüft (SHA-256 wie erwartet). `main` danach in `dev` zusammengeführt. Jakob wurde gebeten, vor dem Installieren „Jetzt sichern“ zu drücken.
- **R1b begonnen (auf `dev`, nicht im Release-Kandidaten):** Orbit-Leiste (Haze-Milchglas, fliegender Planet mit Lichtschweif, Haptik-Tick), Tabs Kontakte · Verlauf · Netz · Karte mit gespeichertem Zustand, Einstellungen hinter dem Zahnrad, Verlauf-Tab (3.4: chronologisch, Tagesköpfe, Arten-Chips, Suche, neue Einträge leuchten), A–Z-Leiste mit Lupen-Blase, Einstellungen in fünf Bereichen mit Statuszeile (3.13), Kontaktseite: Verlauf mit Arten-Chips samt letztem Datum + eigener Suche (3.3), Rückgängig-Leiste mit ablaufendem Ring (Papierkorb/Archivieren ohne Nachfrage), Langdruck-Vorschau mit Gleiten-und-Loslassen-Aktionen (3.2), Treffer-Satz in der Suche (1.4), Speichern morpht zum Häkchen, Zurück mit Gedächtnis (Nachglimmen), Kontaktseite: Kopf in Personenfarbe mit unscharfem Profilbild, Alter im Kopf, Archiv-Hinweis mit Zurückholen, Archiv-Moment (Farbe kehrt zurück), nur zutreffende Aktionen, Abschnittsköpfe nur angeheftet mit Hintergrund (keine dunklen Streifen mehr), lange Namen verkleinern sich (1.4), Leerzustände mit genau einer Aktion, Screenshots für leer/lang (W6).
- **R1b fünfte Sitzung (13.10.2026, auf `dev`):** Lebender Weltraum (Sterne in drei Tiefen, Parallax beim Scrollen, Kamerafahrt beim Tab-Wechsel, Nebel-Tönung je Tab). Kontaktseite nach PLAN 3.3: Reihenfolge Kopf → Aktionen → Kontakt (inkl. Links und eigene Felder mit Kopier-Knopf, Zwischenablage als „sensibel“ markiert) → angepinnte Notiz → Themen → Notizen → Geschenkideen → Fotos → Organisationen (eckig, Anruf-Knopf; Daten bleiben bis R4 Beziehungen) → Beziehungen (rund) → Anlässe → Zeile „+ Anlass · + Link · + Foto · + Notiz …“ (nur was fehlt) → Verlauf. Leere Abschnitte verschwinden; „+ Thema“/„+ Geschenkidee“ öffnen den Abschnitt mit Fokus im Eingabefeld. Arbeitsfeld führt zur Organisation (gleicher Name). Notiz und angepinnte Notiz direkt auf der Seite bearbeitbar. Bearbeiten: Links und eigene Felder für alle Kontakte. Screenshot `detail_full` (hoch, ganze Seite). Danach: Verlauf auf der Kontaktseite als Zeitleiste, die sich beim ersten Erscheinen zeichnet; Kontakt (auch die eigene Karte) als vCard oder QR-Code teilen (nur Visitenkarten-Felder, getestet); Kopf in der Farbe aus dem Profilbild (`PhotoAccent`, getestet); Rückgängig für gelöschte Einträge, entfernte Beziehungen/Anlässe und Schnell-Einträge per Wischen; E-Mail-Abschnitt nur mit verbundenem Postfach; Einstellungen: Karte „Braucht deine Aufmerksamkeit“ (Berechtigungen, Sicherung nie/zu alt, Mail-Konto mit Fehler) mit Lösung als Knopf. Review-Subagent: 0 schwere Funde, 4 kleine behoben (Datei-Export zählt als Sicherung, Rückgängig nimmt die aktuelle Eintragsfassung, vCard-Name, Nebel nur in der Zeichenphase). 106 Tests grün.
- **R1b sechste Sitzung (13.10.2026, 1 h, auf `dev`):** Mehrfachauswahl aus der Langdruck-Vorschau („Auswählen“): Kopf wird zur Auswahl-Leiste (Anzahl, Alle, Gruppe, Archivieren, als vCard exportieren), Avatar dreht sich zum Häkchen, Wischen/Langdruck aus, Auswahl folgt Suche/Filter, Rückgängig für Archivieren und den ganzen Gruppen-Dialog. Geteilter Übergang Liste → Kontakt (Avatar und Name fliegen in den Kopf; SharedTransitionLayout um NavDisplay – **auf dem Handy noch nicht gesehen**, CI kann das nicht zeigen). Profilbild zuschneiden (Kreis-Rahmen, Zoomen/Verschieben, `CropMath` getestet). Einstellungen: Startseite mit Aufmerksamkeits-Karte + fünf Bereichen mit Statuszeile, jeder Bereich eigene Seite (`Route.SettingsArea`). Review-Subagent: 0 schwere, 4 kleine Funde behoben. App-Sperre sofort / nach 1 / nach 5 Min (neues Feld `lockDelaySeconds`, Standard 0 = wie bisher, `AppLockTest`). 120 Tests grün (Run 69).
- **R1b siebte Sitzung (13.10.2026, 1 h, auf `dev`):** Spotlight (`ui/components/Spotlight.kt`, Modifier.Node in `glass()`: Licht unter dem Finger, verbraucht keine Ereignisse, erlischt beim Scrollen; Test: Klicks kommen an, Screenshot `spotlight`). Platzhalter in Endform statt Ladekreisen (`Placeholder.kt`: Start = Listenform, E-Mails = Zeilen, Mail-Text = Zeilen, neue Fotos = Kacheln, Vollbild zeigt erst das kleine Bild). Anlässe nennen den Wochentag („in 12 Tagen – ein Donnerstag“, 1.4).
- **R1b offen:** Volltextsuche über FTS statt im Speicher (optional, Leistung reicht bisher), Wow-Gate R1b (alle berührten Screens gegen W1–W9, Wow-Review-Subagent), danach Release-Kandidat R1b für Jakob.
- **Nächster Schritt:** R1b weiter (Hülle, PLAN 3.1–3.4, 3.11, 3.13). R1b erst nach Wow-Gate und Jakobs Zusage nach `main`.
- Letztes Release: **v1.0.54** (R1a). Davor v1.0.7. `dev` enthält unfertiges R1b – nicht nach `main`.

## Entscheidungen

(noch keine)

## Offene Punkte für Jakob

- Microsoft-Anmeldung (Outlook / PHOÖ): braucht eine App-Registrierung durch Jakob – bis dahin ohne.
- WhatsApp-Erkennung: nicht umsetzen, solange Jakob sie nicht ausdrücklich will.
