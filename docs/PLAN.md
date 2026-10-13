# Orbit – Gesamtplan (verbindlich, Stand 13.10.2026)

Dieses Dokument ist die einzige verbindliche Spezifikation. Die Entstehung (Recherche, Iterationen, ältere Pläne) steht in `PLAN-history.md` und ist nur Hintergrund. Fortschritt: `PROGRESS.md`.

Rangfolge bei Zielkonflikten: **1. Datensicherheit → 2. Wow (Kapitel 1) → 3. Funktionsumfang.** Lieber eine Funktion weniger als eine ohne Wow.

---

## 1. Wow-Charta

**Ziel:** Wer Orbit zum ersten Mal in der Hand hat, denkt „Heilige Kuh, ist das poliert“. Selbst im Zeitalter von KI soll man spüren: Hier hat jemand an alles gedacht. Wow entsteht aus drei Schichten – wenigen großen **Signature-Momenten**, einer **durchgängigen Bewegungs-, Licht- und Haptik-Sprache** und **hunderten kleinen Details**. Nicht aus Feature-Masse.

### 1.1 Gesetze (gelten für jeden Screen)

| # | Gesetz |
|---|---|
| W1 | **Jede Berührung antwortet** – sichtbar (Licht, Bewegung) und spürbar (Haptik), in unter 100 ms. |
| W2 | **Nichts springt.** Keine Layout-Sprünge, keine harten Schnitte. Größe, Inhalt und Zahlen ändern sich animiert. |
| W3 | **Kontinuität.** Elemente reisen zwischen Screens (geteilte Übergänge), statt zu verschwinden und neu aufzutauchen. |
| W4 | **Ein großer Moment pro Screen** – perfekt statt viele halbe. |
| W5 | **Nur zeigen, was zutrifft.** Dual-SIM nur mit zwei SIMs, Bluetooth nur mit Gerät, Abschnitte nur mit Inhalt. |
| W6 | **Jeder Zustand ist gestaltet:** leer, lädt, Fehler, offline, ein Eintrag, tausend Einträge, sehr langer Name, kein Foto. |
| W7 | **Sprache präzise und österreichisch:** `de-AT` (Jänner), richtige Einzahl/Mehrzahl („1 Tag“, „2 Tage“), relative Zeiten, die live aktuell bleiben, Wochentag bei nahen Terminen. Kurz, für Profis, keine Erklärtexte. |
| W8 | **Tempo ist Wow:** Kaltstart bis Liste < 400 ms, Suche pro Tastendruck < 16 ms, Scrollen ohne Ruckler, Anrufbildschirm sofort. |
| W9 | **Ein Token-System:** Bewegung schnell 150 / mittel 250 / betont 350 ms mit Expressive-Federn; Abstände 4/8 dp; feste Radien, Glow-Stufen und Haptik-Typen. Keine Einzellösungen. |

Sensor-Effekte (Neigung) aus im Energiesparmodus. Keine Barrierefreiheits-Anpassungen.

### 1.2 Durchgehende Systeme

- **Lebender Weltraum:** Sternenfeld in drei Tiefenebenen mit Parallax beim Scrollen und leicht beim Neigen; jeder Tab hat seine eigene Nebel-Tönung. Ein Tab-Wechsel ist eine kurze Kamerafahrt – die Sterne gleiten seitlich, der Inhalt blendet über.
- **Orbit-Leiste:** Der Indikator des aktiven Tabs ist ein leuchtender Planet, der beim Wechsel im Bogen mit Lichtschweif zum neuen Tab fliegt.
- **Echtes Milchglas** (Haze) hinter Leiste, Köpfen und Sheets.
- **Spotlight:** Glasflächen leuchten unter dem Finger radial auf und klingen weich ab.
- **Haptik-Choreografie:** Haptik sitzt exakt auf dem Höhepunkt der Animation – Einrasten, Schwelle, Bestätigen, Ablehnen, feine Ticks.
- **Geteilte Übergänge überall:** Bild Liste → Kontaktkopf, Kachel → Organisation, Foto → Vollbild, Datumskachel → Anlass, Benachrichtigung → Anrufbildschirm.
- **Zurück mit Gedächtnis:** Zurück-Geste mit Vorschau; Rückkehr an exakt dieselbe Scrollposition, die berührte Zeile glimmt kurz nach.
- **Typografie in Bewegung:** große Titel schrumpfen beim Scrollen in die Kopfzeile, Zahlen rollen, Tabellenziffern überall, wo gezählt wird.
- **Rückgängig statt Nachfragen:** Leiste mit ablaufendem Zeitring.
- **Platzhalter in exakter Endform** statt Ladekreisen.

### 1.3 Signature-Moment je Screen

| Screen | Moment |
|---|---|
| Start | Ring zeichnet sich, Planet schwingt ein und **wird nahtlos zum Indikator der Orbit-Leiste**. |
| Kontakte | A–Z-Leiste mit Lupen-Blase und Tick je Buchstabe; **Überziehen nach unten öffnet den Schnellzugriff (Portale) wie eine Schublade**. |
| Kontaktseite | Kopf in der Farbe der Person (Palette aus dem Profilbild, Organisationen blau), Profilbild unscharf als Hintergrund; **der Verlauf ist eine Zeitleiste, deren Linie sich beim Scrollen zeichnet**; „Zuletzt“-Chips zählen hoch. |
| Verlauf-Tab | Ein neuer Eintrag (z. B. gerade beendeter Anruf) **gleitet oben mit kurzem Leuchten ein**. |
| Anlässe | **Jahresrad**: das Jahr als Umlaufbahn, Anlässe als Punkte, heute als wandernder Planet; Geburtstag heute: einmaliger Sternenregen; „Gratuliert“: Sternenfunke. |
| Netz | Knoten **federn physikalisch** an ihre Plätze; der Weg zur Mitte leuchtet auf; ferne Ringe leicht unscharf (Tiefenschärfe). |
| Karte | **Kamerafahrt im Bogen** (raus – rüber – rein) zum Kontakt; Marker ploppen federnd; ein Cluster platzt beim Antippen in seine Mitglieder auf. |
| Anrufbildschirm | **Annehmen, indem du den Planeten in die Sonne ziehst** (große Trefferfläche, Haptik beim Einrasten), Ablehnen durch Wegschnippen; Sternenhimmel mit Neigung; Anruferfarbe; nach dem Auflegen gleitet eine Notizzeile mit offener Tastatur hoch. |
| Organisation | Portal-Kacheln mit echten Logos; „geöffnet“-Punkt pulsiert; Mitarbeiter „Nur hier“ als **Satelliten-Reihe**. |
| Archiv | Kontakte als **ferne, entsättigte Sterne**; „Zurückholen“ gibt die Farbe zurück und lässt das Bild in die Liste fliegen. |
| Fotos | Vollbild **wächst aus der Kachel** und fällt beim Runterwischen zurück, Hintergrund blendet mit dem Finger aus. |
| Einstellungen | Statuszeilen mit Live-Indikatoren; die Achtung-Karte glimmt; Schalter morphen mit Haptik. |
| Ich-Karte | Visitenkarte mit QR und **Holo-Schimmer, der sich beim Neigen bewegt** (AGSL-Shader + Lagesensor). |
| Erster Start | **„Dein Orbit füllt sich“** – ausgewählte Handy-Kontakte fliegen als Bilder in die Umlaufbahn um dich und ordnen sich zur Liste. |
| Widgets | Gleiche Sprache, echte Vorschauen im Auswahlmenü. |

### 1.4 Details-Katalog („daran hat jemand gedacht“) – Mindestumfang

- „Annas 30er ist in 12 Tagen – ein Donnerstag.“
- Suche markiert Treffer und zeigt den Satz aus Notiz oder Verlauf.
- Eingefügte Nummer in der Suche → „gehört zu Arzt Müller“.
- Öffnungszeiten: „schließt in 40 Min“, Farbe wechselt kurz vor Schluss.
- Kopieren zeigt „Kopiert“; Kunden-/Matrikelnummer mit Kopier-Knopf, auch im Anrufbildschirm.
- Lange Namen werden sauber verkleinert statt abgeschnitten.
- Tastatur: Enter springt ins nächste Feld bzw. speichert; richtige Tastaturart je Feld.
- Verpasste Anrufe als Punkt am Verlauf-Tab.
- Geburtstags-Benachrichtigung mit Foto, „Gratulieren“ mit eigener Vorlage und „Gratuliert ✓“ direkt in der Benachrichtigung.
- Benachrichtigungen gebündelt, mit Orbit-Farbe und passenden Aktionen; laufender Anruf als Chip in der Statusleiste.
- Animierter Startbildschirm, Themen-Icon.
- Tablet/Foldable: automatisch zweispaltig (Liste | Kontakt).
- Offline: Karte und Mails sagen knapp, was fehlt, und holen es nach, sobald Netz da ist.

### 1.5 Wow-Gate (Bedingung für jedes Release)

1. Alle Signature-Momente und Systeme, die das Update berührt, sind umgesetzt und flüssig.
2. Jeder berührte Screen ist gegen W1–W9 geprüft; Checkliste in `PROGRESS.md` abgehakt.
3. Screenshots aller Zustände (leer, normal, voll, lange Texte, offline) wurden angesehen. Ein **Wow-Review-Subagent** bewertet sie gegen diese Charta („Würde ein anspruchsvoller Nutzer wow sagen? Was fehlt, was wirkt billig?“); bestätigte Funde sind behoben.
4. Kein Ruckeln: keine schwere Arbeit in der Composition, stabile Keys, Bilder vorab skaliert.
5. **Zeitbudget: etwa ein Drittel jedes Updates gehört Wow und Feinschliff.**

---

## 2. Grundsätze

1. **Nichts Intelligentes, keine KI.** Orbit tut nur, was du auslöst, oder folgt festen, sichtbaren Regeln („Nummer exakt gleich“, „Nummer beginnt mit …“, „Adresse endet auf @firma.at“).
2. **Keine Fälligkeit.** Kein Rhythmus, kein „fällig“, kein Drängeln. Orbit schreibt weiter mit (Anrufe, SMS, E-Mails, eigene Einträge) – sichtbar im Verlauf.
3. **Organisationen sind keine Menschen.** Standardmäßig überall ausgeblendet; Zuordnungen Mensch ↔ Organisation sind strikt getrennt von Beziehungen Mensch ↔ Mensch.
4. **Für Profis gebaut.** Keine Barrierefreiheits-Arbeit, keine Tutorials, keine Erklärtexte, kein „Was ist neu“-Popup (nur eine Zeile in den Einstellungen). Dichte Oberfläche, Gesten, Langdruck-Menüs.
5. **Daten gehen nie verloren.** Jede Umstellung sichert vorher automatisch, ist getestet; Backups verschlüsselt.
6. Design „Dark Space“ (feste Farben im Objekt `Space`, Space Grotesk + Inter, Glas, Glow). Alle Texte Deutsch, Du-Form.

---

## 3. Funktionen

### 3.1 Hülle und Navigation
- Untere Leiste: **Kontakte · Verlauf · Anlässe · Netz · Karte**. Einstellungen hinter dem **Zahnrad** oben rechts in der Kontaktliste.
- Tabs behalten Scrollposition, Suche und Zustand; die Karte bleibt geladen.

### 3.2 Kontaktliste
- Wie bisher: Suche, Filter-Sheet (Anzeigen: Menschen/Alle/Organisationen, Gruppen mit Pin, **Organisation**, Sortierung), angepinnte Gruppen als Chips, ICH-Karte oben, Hinweis „N Treffer bei Organisationen“.
- Sortierung: Name · Zuletzt Kontakt · Am längsten her · Neu hinzugefügt · Häufigkeit. Kein „Fällig“.
- Schnellzugriff-Leiste mit angepinnten Portalen/Links (siehe 3.7).
- Mehrfachauswahl per Langdruck: Gruppe, Archivieren, Exportieren.
- Langdruck auf Kontakt: Vorschau-Karte mit letzten drei Einträgen und Aktionen.

### 3.3 Kontaktseite
- Reihenfolge: Kopf (mit „Zuletzt: Anruf vor 3 Tagen“, Alter) → Schnellaktionen → Kontaktdaten → angepinnte Notiz, Themen, Notizen → Organisationen (Zuordnungen, eckige Kacheln mit Anruf-Knopf) → Beziehungen (Menschen, rund) → Anlässe → Fotos → **Verlauf ganz unten**.
- Über dem Verlauf Filter-Chips je Art mit letztem Datum: `Alle` `Anruf · vor 3 T` `Treffen · vor 2 M` `E-Mail · vor 5 T` `SMS · vor 1 W`.
- Leere Abschnitte verschwinden; unten eine Zeile `+ Anlass · + Link · + Foto · + Notiz`.
- Profilbild zuschneiden; Kontakt als vCard/QR teilen.
- Themen optional mit Datum → Benachrichtigung an dem Tag.

### 3.4 Verlauf
- Tab „Verlauf“: alle Einträge aller (sichtbaren) Kontakte chronologisch, Filter nach Art, Suche, Orgs per Schalter; Anrufe inkl. unbekannter Nummern mit Rückruf.
- Ein Treffen mit mehreren Personen ist **ein** Eintrag bei allen (`sharedId`).
- Schnelles Rückdatieren (gestern, letzte Woche); Notiz auch zu automatischen Einträgen.
- Abgleich Anrufe/SMS wie bisher; E-Mails siehe 3.9.

### 3.5 Anlässe und Geburtstage
- Anlässe pro Kontakt: Arten Geburtstag (max. einer), Hochzeitstag, Jahrestag, Kennenlerntag, Namenstag, Gedenktag, eigene Bezeichnung; Jahr optional („Jahr unbekannt“); jährlich oder einmalig; **gemeinsam mit** einem zweiten Kontakt (einmal gespeichert, bei beiden sichtbar, eine Erinnerung); Erinnerung am Tag / 1 / 3 / 7 / 14 Tage vorher (mehrfach); Notiz; Pin.
- Tab „Anlässe“ mit drei Reitern: **Geburtstage** (Standard) · Angepinnt · Alle Anlässe; Jahresrad oben.
- Geburtstage: alle Menschen mit Geburtstag (ohne Archiv, Organisationen, Mitarbeiter), ab heute fortlaufend; Abschnitte Gerade vorbei (7 Tage) · Heute · Diese Woche · Diesen Monat · Monate; Zeile mit Datumskachel und Wochentag, „wird 31“, Bezug zu heute, Status Gratuliert/Geschenk; unten „N Kontakte ohne Geburtstag“ mit Eingabe in der Zeile.
- Abhaken ohne Fenster: Gratuliert per Tipp/Wisch, automatisch beim Gratulieren-Knopf, aus der Benachrichtigung; Geschenk als aufklappende Textzeile mit Chips „mit …“ und Datum; beides Verlaufseinträge pro Jahr; Geschenkideen antippen = verschenkt; Geschenk-Historie im Kontakt; Schalter blendet Abhaken aus.
- Filter (Art, Gruppe, Zeitraum, nur runde Jahreszahlen, Orgs) und Sortierung (nächste zuerst, Kalender, Name, Jahre) für „Angepinnt“/„Alle“.
- Gratulations-Vorlage pro Kontakt (Du/Sie, Sprache). Tägliche Benachrichtigung nur noch mit Anlässen; optional Übernahme in den Handy-Kalender.

### 3.6 Organisationen
- Benannte Nummern und Adressen (Zentrale, Empfang, Termine, Fax, Notfall, eigene), Standardnummer.
- **Zuordnungen** (getrennt von Beziehungen): Mensch → Organisation mit Rolle aus beiden Blickrichtungen (Hausarzt/Patient, Arbeitgeber/Mitarbeiter:in, Kollegin, Uni/Studierende, Bank, Versicherung, Verein, eigene). Personen-Seite: „Hausarzt: Arzt Müller“ mit Anruf-Knopf, „Auch im COOL Lab: Anna, Tom“. Auch für ICH. Bestehende Org-Beziehungen werden zu Zuordnungen.
- Org-Seite mit zwei Abschnitten: **Aus deinen Kontakten** (echte Menschen per Zuordnung) und **Nur hier** (versteckte Mitarbeiter).
- **Versteckte Mitarbeiter:** Name, Rolle, Foto, Durchwahl, Mail, dauerhafte Notiz, eigene Gespräche (Org-Einträge mit `memberId`), „zuletzt gesprochen“. Nie in Liste, Suche, Verlauf-Tab, Anlässen, Netz, Karte, Tastenfeld-Suche. Gespräch eintragen in einer Zeile mit Mitarbeiter-Chip. „Zu eigenem Kontakt machen“ / „Nur in der Organisation führen“.
- **Regeln** (selbst eingeschaltet): Nummernbereich (Präfix) und E-Mail-Domain gehören zur Organisation; Verlauf zeigt „Anruf · Empfang“. Nach Anruf von unbekannter Durchwahl: „Mitarbeiter zuordnen?“.
- Eigene Felder (Kundennummer, Matrikelnummer …) mit Kopier-Knopf; Öffnungszeiten mit Live-Anzeige; Logo der Webseite als Bild (bestätigen); Kategorie „Bildung“.

### 3.7 Portale und Links
- Kachel-Raster unter dem Org-Kopf: Titel, Adresse, Logo, Notiz (z. B. „Login: k12345678“, nie Passwörter); öffnen im Orbit-Browserfenster (Custom Tabs, Chrome-Anmeldungen bleiben), wahlweise Chrome oder installierte App; Langdruck: verschieben, bearbeiten, anpinnen, „Auf Startbildschirm“.
- Vorlagen Bildung: JKU – KUSSS `kusss.jku.at`, Moodle `moodle.jku.at`, myJKU `my.jku.at`; PH OÖ – PH-Online `ph-online.ac.at` (PH OÖ wählen), Moodle-Adresse selbst einfügen; Bibliothek, Mensa, Webseite.
- Angepinnte Links als Schnellzugriff in der Kontaktliste und als Widget.
- Auch bei Personen (LinkedIn, Instagram).

### 3.8 Teilen-Eingang
- Aus Google Maps: Ort teilen → neue Organisation oder zu bestehender; Maps-Link gespeichert („In Google Maps öffnen“ führt exakt zum Ort), Koordinaten per Geocoder.
- Aus Chrome: Seite teilen → als Link zu einer Organisation/Person (Titel und Adresse übernommen).
- Bilder/PDFs → Ordner eines Kontakts; Text → Notiz oder Thema.
- Optional „Daten ergänzen“ aus OpenStreetMap (Webseite, Telefon, Öffnungszeiten), jeder Wert einzeln bestätigt, Nominatim-Regeln einhalten.

### 3.9 E-Mail v2
- Abgleich nur von Neuem: pro Konto und Ordner UIDVALIDITY + letzte UID; Hintergrund-Abgleich (nur mit Netz, Wiederholung mit wachsender Pause) holt nur Kopfzeilen und ordnet lokal zu; erster Abgleich 12 Monate; neue Adresse → einmalige Suche (Gmail: X-GM-RAW `from:(…) OR to:(…)`).
- Ordner: Gmail nur „Alle Nachrichten“; sonst Posteingang, Gesendet, Archiv (SPECIAL-USE), Ordnerliste einen Tag gecacht.
- Eine Verbindung pro Abgleich, COMPRESS, ID-Befehl.
- Mails werden **Verlaufseinträge**, gebündelt pro Tag und Richtung; Text beim Antippen; „Antworten“ öffnet die Mail-App; Bild-Anhänge in den Ordner übernehmen; Zuordnung inkl. Mitarbeiter und Domain-Regel; Status „zuletzt abgeglichen vor 2 Std.“. Der eigene Mail-Abschnitt entfällt.
- App-Passwörter wie bisher; Microsoft (Outlook, Microsoft 365) nur nach App-Registrierung durch Jakob.

### 3.10 Fotos und Dateien
- Ablage global: Fotos und PDFs einmal gespeichert, mehreren Kontakten zuordenbar (Referenzzählung).
- Fotos-Seite: alle Bilder nach Aufnahmedatum (EXIF), Monatsüberschriften mit Scrubber; im Kontakt die neuesten 6 + „Alle“.
- Fotos an Einträge hängen (Vorschau im Verlauf); teilen hinein (aus der Galerie) und hinaus; „Originalgröße behalten“; Speicheranzeige.

### 3.11 Archiv
- Archivierte Kontakte behalten alles und erscheinen **nirgends** (Liste, Suche, Tabs, Benachrichtigungen, Beziehungen anderer, Tastenfeld-Suche). Ausnahme Anrufer-Erkennung: Name mit Archiv-Hinweis und „Zurückholen“, Anruf landet still im Verlauf.
- Einstellungen → Archiv: Suche nur im Archiv, Sortierung (Name, archiviert am, zuletzt Kontakt), Gruppierung nach Archivierjahr; Karte mit entsättigtem Bild, letzter Rolle/Firma, „archiviert März 2026 · zuletzt 2025 · 12 Notizen · 34 Einträge“.
- Archivierten Kontakt öffnen: volle Seite, oben „Seit 3 Jahren im Archiv · Zurückholen“, Block „Was du weißt“ (Zusammenstellung eigener Einträge). Archivieren per Menü/Mehrfachauswahl mit Rückgängig; Organisationen samt Mitarbeitern.

### 3.12 Orbit als Telefon-App
- Standard-Telefon-App (ROLE_DIALER, InCallService); Jakob aktiviert sie selbst; Klingelton bleibt beim System; Rückfall auf die vorinstallierte Telefon-App, falls Orbit nicht gebunden werden kann.
- Eingehend: Foto groß in Anruferfarbe, Name, Rolle („Arzt Müller · Hausarzt von Papa“, „Liles · Herr Mayer“), zuletzt, offene Themen, anstehender Anlass, Kundennummer; unbekannt: „3 Anrufe in 2 Wochen“; archiviert: Hinweis + Zurückholen.
- Laufend: Halten, Stumm, Lautsprecher/Bluetooth/Kopfhörer, Tastenfeld (DTMF), Anklopfen, Konferenz, Themen abhaken, Notiz, Timer; Näherungssensor; laufender Anruf als Chip.
- Danach: Notizzeile → Verlaufseintrag; „Mitarbeiter zuordnen?“ bei unbekannter Durchwahl.
- Wählen: Tastenfeld mit T9-Suche (ohne Archiv und versteckte Mitarbeiter), Dual-SIM pro Kontakt, Mailbox.
- Ablehnen mit SMS-Vorlage, „Später zurückrufen“ (15 Min / 1 Std), Nummern sperren.
- Benachrichtigung im Anruf-Stil, Vollbild bei gesperrtem Handy.
- **Anrufer-Kurzindex** (Kapitel 4) für sofortige Anzeige bei kaltem Start.

### 3.13 Einstellungen
- Startseite: Karte **„Braucht deine Aufmerksamkeit“** nur bei Bedarf (Berechtigung, Backup, Mail-Konto, Update); darunter fünf Bereiche mit Statuszeile:
  - **Mitschreiben** („Anrufe ab 60 s · 2 Mail-Konten“): Mindestdauer, SMS, E-Mail-Konten, unbekannte Nummern, jetzt abgleichen, Berechtigungen, Telefon-App.
  - **Benachrichtigungen** („Anlässe um 9:00“): Uhrzeit, Themen im Anruf, Notiz nach Anruf, Nachfrage nach Treffen.
  - **Kontakte** („5 Gruppen · Vorname zuerst“): Gruppen, Eintragsarten, Namensreihenfolge, **Archiv**, Papierkorb, Abhaken bei Geburtstagen an/aus.
  - **Sicherheit & Backup** („Sperre an · Backup vor 3 Tagen“): App-Sperre (sofort / 1 / 5 Min), Backup-Ordner, automatisch, jetzt sichern (verschlüsselt mit Passwort), exportieren (ZIP, Excel, vCard), wiederherstellen, Speicher-Übersicht.
  - **Über Orbit** („Version 1.x · aktuell“): Updates, kurze Änderungszeile, Fehlerprotokoll teilen.

### 3.14 Komfort
- Schnelleinstellungs-Kachel „Notiz zum letzten Anruf“, Favoriten-Widget, Anlässe-Widget, App-Shortcuts.
- Mehrere Handy-Kontakte auf einmal übernehmen (Organisation per fester Regel „Firma ohne Personennamen“); Hinweis „Diese Nummer gibt es schon“.
- ICH-Karte mit QR; Export Excel/vCard.

---

## 4. Architektur

- **Speicher aufteilen:** `orbit.json` für den Kern (Kontakte, Organisationen, Mitarbeiter, Zuordnungen, Beziehungen, Anlässe, Links, Einstellungen). Verlauf, Mail-Index und Ablage in **SQLite** (`android.database.sqlite`, ohne Codegenerator): `entries` mit eindeutiger `externalId`, Index (Kontakt, Zeit), Volltextsuche **FTS4** mit `snippet()`.
- **Anrufer-Kurzindex:** bei jedem Speichern eine kleine Datei Nummer → Name, Bild, Rolle, Notiz-Anfang, Kundennummer, Kennzeichen archiviert/Mitarbeiter.
- **Zuordnungs-Maschine:** exakte Nummer → Mitarbeiter-Durchwahl → längster Nummernbereich → Mail exakt → Domain; Ergebnis mit Kennzeichen; Listen/Suche filtern, Anrufer-Erkennung nicht.
- **Index** pro Datenänderung: letzter Kontakt je Art, Suchtext, nächste Anlässe.
- **Planer** für alle Benachrichtigungen; **Teilen-Eingang**; **Ablage** mit Referenzzählung; **UI-Bausteine** inkl. Wow-Systeme aus 1.2 als wiederverwendbare Komponenten.
- **Datenmodell v2** in einer Umstellung mit automatischer Sicherung: entfällt `rhythmDays`, `paused`, `snoozedUntil`, `remindedDueDay`, Gruppen-Rhythmus, `resetsTimer`; `birthday`/`anniversaries` → `dates`; `website` → erster Link; `gallery` → Ablage; Org-Beziehungsarten → Zuordnungen. Neu: `dates`, `links`, `fields`, `members`, `numberPrefixes`, `emailDomains`, `mapsUrl`, `pinnedNote`, `OrgLink`, Eintrag `memberId`/`attachmentIds`/`sharedId`/Mail-Verweis.
- **Backup** exportiert SQLite-Tabellen als JSON in die verschlüsselte ZIP.

---

## 5. Reihenfolge

| Update | Inhalt | Wow-Schwerpunkte (zusätzlich zu 1.1/1.2 auf allen berührten Screens) |
|---|---|---|
| Test-Build (ohne Release) | riskante Bausteine einmal kompilieren: InCallService, Haze, geteilte Übergänge, Roborazzi, FTS4, AGSL | – |
| R1a Fundament | Speicher v2 + Umstellung, Index, Zuordnungs-Maschine, Fälligkeit raus, Archiv-Logik, verschlüsseltes Backup, Fehlerprotokoll, Test-/Screenshot-System, `de-AT`-Formate | Token-System (W9), Haptik-Typen, Platzhalter |
| R1b Hülle | 3.1–3.4, 3.11, 3.13 | Lebender Weltraum, Orbit-Leiste, Milchglas, Spotlight, Start-Moment, Kontaktseite-Moment, Verlauf-Moment, Archiv-Moment, Zurück mit Gedächtnis, Rückgängig-Ring |
| R2 Telefon | 3.12 | Anrufbildschirm-Moment, Anruf-Chip, Benachrichtigungen |
| R3 Anlässe | 3.5 | Jahresrad, Sternenregen/-funke, Datumskachel → Anlass |
| R4 Organisationen | 3.6–3.8 | Kachel → Organisation, Satelliten-Reihe, Live-Öffnungszeiten, Schnellzugriff-Schublade, Netz-Federn und Leuchtweg |
| R5 E-Mail | 3.9 | Mails klappen weich auf, Wellen-Indikator, Offline-Zustände |
| R6 Fotos & Dateien | 3.10 | Foto wächst aus der Kachel, Monats-Scrubber |
| R7 Komfort & Abschluss | 3.14, Erst-Start, Karte-Kamerafahrt | Ich-Karte Holo, „Dein Orbit füllt sich“, Widgets, zweispaltig; **Feinschliff-Durchgang über alle Screens gegen die Charta** |

Zeit gesamt: **26–34 h** (Wow-Budget je Update etwa ein Drittel).

---

## 6. Qualitätssicherung

- Unit-Tests (Logik, Umstellung mit Daten im alten Format, Zuordnung, Planer) und Roborazzi-Screenshot-Tests aller Screens in allen Zuständen mit festen Testdaten; Release nur, wenn alles grün ist.
- Screenshots landen im Log-Branch und werden vor jedem Release angesehen.
- Vor jedem Release: Code-Review-Subagent (Laufzeitfehler, Datenverlust, Logik) **und** Wow-Review-Subagent (gegen Kapitel 1). Bestätigte Funde beheben.
- Entwicklung auf `dev` (Build ohne Release); fertige Updates nach `main` → signiertes Release.

## 7. Offene Entscheidungen (Jakob)

- Microsoft-Anmeldung (Outlook, Microsoft 365/PHOÖ): braucht eine App-Registrierung – bis dahin nicht umsetzen.
- WhatsApp-Erkennung: nicht umsetzen, solange nicht ausdrücklich gewünscht.
