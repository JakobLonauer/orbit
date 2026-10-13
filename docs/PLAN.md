# Orbit – Ausbauplan (Oktober 2026)

Arbeitsweise laut Auftrag: vorläufiger Plan → Abgleich mit Best Practices und anderen CRMs → Plan anpassen → zweimal iterieren → umsetzen. Bei Zweifeln entscheidet Claude.

---

## Plan v1 – Interpretation der Wünsche

| # | Wunsch | Interpretation v1 |
|---|---|---|
| 1 | Nummern wie Krankenhaus, Arzt, Arbeitsstelle, Unternehmen als „nicht echte Person“ | Neues Feld `kind` am Kontakt: **Mensch** oder **Geschäft**. Kein Gruppen-Label. |
| 2 | Geschäfte nur auf Wunsch in Suchen sichtbar | Alle Listen und Suchen blenden Geschäfte aus. Ein Schalter „Geschäfte anzeigen“ im Filter-Menü holt sie zurück. |
| 3 | Sofort, aber dezent erkennbar | Geschäfte bekommen ein eckiges Avatar mit Gebäude-Symbol, Menschen bleiben rund. |
| 4 | Gruppen und alle Filter hinter einem kleinen Button, außer angepinnte Gruppen | Filter-Icon neben der Suche öffnet ein Bottom-Sheet (Status, Gruppen, Anzeigen, Sortierung). Gruppen lassen sich pinnen, nur gepinnte erscheinen als Chips. |
| 5 | Orbit als Liste statt Grafik | Orbit-Tab wird eine Liste, gruppiert nach überfällig, bald fällig, im Rhythmus und ohne Rhythmus. |
| 6 | Beziehungsgraph mit Tiefe bis 4 | Neuer Tab „Netz“: Graph der Beziehungen ab einer Wurzel-Person, Tiefe 1–4 wählbar. |
| 7 | „ICH“ als automatischer Eintrag, umbenennbar | Fester Kontakt mit `isMe`, wird bei der Migration angelegt. Er ist umbenennbar, Wurzel des Netzes, Ziel von Beziehungen und bekommt keine Erinnerungen. |
| 8 | E-Mail-Konten verknüpfen, im Kontakt die letzten Mails der Person sehen | Beliebig viele Konten in den Einstellungen. Im Kontakt sammelt ein Abschnitt „E-Mails“ alle Mails von allen Adressen der Person aus allen Konten. Technik: noch offen (Gmail-API oder IMAP). |
| 9 | Kontaktliste/Anrufliste sortieren | Sortierung im Filter-Menü: Name, zuletzt Kontakt, Fälligkeit, neu hinzugefügt. |
| 10 | Bessere Google-Maps-Integration, Adressen grafisch zueinander | Neuer Tab „Karte“ mit allen Kontakten als Marker. Technik: Google Maps SDK. |
| 11 | Bilder-Ordner pro Kontakt, getrennt vom Profilbild | Abschnitt „Fotos“ im Kontakt mit Raster, Mehrfachauswahl und Vollbild-Ansicht. |

Offene Fragen v1: Begriff für „Geschäft“, technische Wege für E-Mail und Karte, Graph-Layout, was genau mit „Anrufliste“ gemeint ist.

---

## Abgleich 1 – Was andere Apps und Leitfäden machen

| Thema | Befund | Quelle |
|---|---|---|
| Person vs. Firma | Apple Kontakte behandelt eine Karte mit Firmenname ohne Personennamen als **Firma** (eigener Schalter, anderes Symbol). Business-CRMs (HubSpot, Salesforce) führen Firmen als eigenes Objekt neben Kontakten und verknüpfen beide. vCard nutzt dafür das Feld `ORG`. | macmost.com, vCard RFC 6350 |
| Persönliche CRMs | Monica legt beim Anlegen des Kontos automatisch einen Kontakt an, „der dich im System darstellt“, und hat Fotos/Dokumente pro Kontakt. Firmen als eigener Typ fehlen dort. | docs.monicahq.com |
| Filter verstecken | SAP Fiori (Android) „Filter Feedback Bar“: Volles Filter-Menü hinter einem Button mit Zähler, nur aktive Filter und wenige feste Schnellfilter als Chips. Ohne Schnellfilter ist die Leiste unsichtbar, bis ein Filter aktiv ist. Eine geänderte Sortierung erscheint als Chip ganz links. | sap.com Fiori Design Android |
| Beziehungsnetz | Aurea CRM „Relationship Network“: aktueller Datensatz in der Mitte, jede Ebene = ein Grad Abstand, Farbe pro Ebene, jeder Datensatz nur einmal (Querverbindungen gestrichelt), Antippen wählt aus, Doppeltippen öffnet. | help.aurea.com |
| E-Mail | Gmail: App-Passwörter für private Konten weiter möglich (2-Schritt-Verifizierung nötig, nicht für Workspace). Gmail-API bräuchte ein eigenes Google-Cloud-Projekt mit OAuth und Prüfung („restricted scope“). Outlook.com: Basic Auth seit 16.09.2024 abgeschaltet, nur OAuth. GMX/web.de/iCloud/Yahoo: IMAP mit (App-)Passwort. | support.google.com/accounts/answer/185833, Microsoft Tech Community |
| Karte | Google Maps SDK braucht API-Schlüssel mit Abrechnungskonto. MapLibre Native ist Open Source und ohne Schlüssel nutzbar. OpenFreeMap liefert kostenlose Vektor-Kacheln ohne Schlüssel, inklusive eines dunklen Stils (`styles/dark`). Namensnennung OpenStreetMap/OpenMapTiles nötig. | openfreemap.org, github.com/maplibre/maplibre-native |

## Plan v2 – angepasst nach Abgleich 1

1. **Begriff:** „Organisation“ statt „Geschäft“. Passt auf Firma, Arbeitgeber, Praxis, Krankenhaus, Behörde, Verein. Unterart als Kategorie: Firma · Arbeit · Gesundheit · Behörde · Dienstleister · Verein · Sonstiges. Wie bei Apple und vCard ist der **Firmenname der Anzeigename**, das Positionsfeld wird zu „Abteilung / Ansprechpartner“.
2. **Ausblenden:** Organisationen sind in Kontaktliste, Suche, Orbit-Liste, Netz und Karte ausgeblendet. Umschalter „Anzeigen: Menschen · Alle · Organisationen“ im Filter-Menü. Er gilt nur bis zum Schließen der App, damit man nicht vergisst, dass er an ist. Bei einer Suche erscheint unten ein Hinweis „3 Treffer bei Organisationen“, der sie mit einem Tipp einblendet.
3. **Erkennbarkeit:** Organisationen haben ein abgerundet-eckiges Avatar mit Kategorie-Symbol statt Initialen und ein kleines Gebäude-Symbol hinter dem Namen. Menschen bleiben rund.
4. **Filter:** Muster „Filter Feedback Bar“: Filter-Button mit Zähler neben der Suche öffnet ein Bottom-Sheet (Anzeigen, Status, Gruppen, Sortierung). Darunter nur gepinnte Gruppen und aktive Filter als Chips. Ist nichts gepinnt und nichts aktiv, verschwindet die Zeile.
5. **Orbit als Liste:** Abschnitte Überfällig · Diese Woche · Später · Ohne Rhythmus, darüber Geburtstage der nächsten 14 Tage. Jede Zeile mit Fortschrittsbalken und Direktaktion.
6. **Netz:** Radiales Layout nach Aurea-Muster: Wurzel in der Mitte, Ringe = Ebenen 1–4, Farbe je Ebene, jede Person einmal, Querverbindungen gestrichelt. Tippen wählt aus, zweites Tippen öffnet den Kontakt, „Als Mitte“ wechselt die Wurzel. Zoomen und Verschieben mit zwei Fingern.
7. **ICH:** Wie Monica und Apples „Meine Karte“: automatisch angelegter Kontakt, oben in der Kontaktliste, umbenennbar, nicht löschbar, keine Erinnerungen, Standard-Mitte im Netz und Bezugspunkt für Entfernungen auf der Karte.
8. **E-Mail:** IMAP mit App-Passwort, beliebig viele Konten, Vorlagen für Gmail, GMX, web.de, iCloud, Yahoo und „Anderer Server“. Outlook nur mit Hinweis. Passwörter mit Android-Keystore verschlüsselt, getrennt von den Daten, nicht im Backup.
9. **Sortierung:** Name · Zuletzt Kontakt · Fälligkeit · Zuletzt hinzugefügt · Häufigkeit, jeweils auf- oder absteigend.
10. **Karte:** MapLibre + OpenFreeMap „dark“ statt Google Maps SDK. Adressen werden mit dem Android-Geocoder in Koordinaten umgewandelt und gespeichert. Avatare als Marker. Google Maps bleibt für „Öffnen“ und „Route“ per Link.
11. **Fotos:** Wie Monica ein eigener Foto-Bereich pro Kontakt: Mehrfachauswahl ohne Speicher-Berechtigung, Raster, Vollbild mit Wischen und Zoom, Teil des Backups.

## Abgleich 2 – Gegenprüfung von Plan v2

- **Tabs:** Mit Netz und Karte gibt es 5 Ziele. Material empfiehlt 3–5 Ziele in der Navigationsleiste, 5 ist also noch in Ordnung.
- **„Anrufliste sortieren“:** Orbit hat keine eigene Anrufliste. Die Sortierung „Zuletzt Kontakt“ zeigt deshalb in jeder Zeile Art und Zeit des letzten Kontakts (Anruf ein/aus, SMS, Treffen). Damit wird die Liste selbst zur sortierbaren Anrufliste, ohne zusätzlichen Tab.
- **Erinnerungen bei Organisationen:** Gruppen-Rhythmen gelten nicht für Organisationen. Ein selbst gesetzter Rhythmus (z. B. Zahnarzt alle 6 Monate) funktioniert aber.
- **Anrufe mit Organisationen** werden weiter protokolliert („wann zuletzt beim Arzt angerufen“).
- **Import aus dem Handy:** Ein Handykontakt mit Firma, aber ohne Personennamen, wird automatisch zur Organisation (Apple-Heuristik). Unbekannte Nummern lassen sich direkt „als Organisation“ anlegen.
- **Beziehungen zu Organisationen:** neue Arten „arbeitet bei / Arbeitgeber von“, „Patient:in bei / behandelt“, „Kunde bei / betreut“, „Mitglied bei / hat Mitglied“.
- **Karte:** Der Aufbau „wo sie sich zueinander befinden“ wird ergänzt um die Entfernung zu ICH im Kontakt und auf der Karte. Mehrere Personen an derselben Adresse werden zu einem Marker mit Zahl gebündelt.
- **E-Mail-Suche:** Nur INBOX zu durchsuchen verpasst archivierte Gmail-Nachrichten. Deshalb zusätzlich den Ordner mit dem IMAP-Attribut `\All` bzw. `\Archive`. Doppelte Mails werden per Message-ID zusammengeführt. Nur Kopfzeilen werden geladen, der Text erst beim Antippen.
- **APK-Größe:** MapLibre bringt native Bibliotheken mit. Das APK enthält nur arm64-v8a und armeabi-v7a, das deckt alle aktuellen Handys ab.

## Plan v3 – umgesetzt

Plan v2 mit den Ergänzungen aus Abgleich 2. Technische Eckpunkte:

| Bereich | Umsetzung |
|---|---|
| Daten | `Contact.kind` (PERSON/ORG), `orgCategory`, `isMe`, `website`, `geo` (Cache), `gallery`; `Group.pinned`. Migration legt ICH an. |
| Liste | Filter-Sheet, Pin-Chips, Sortierung, ICH-Karte, Org-Hinweis bei Suche |
| Orbit | Liste in Abschnitten statt Grafik |
| Netz | Neuer Tab, radiales BFS-Layout, Tiefe 1–4 |
| Karte | Neuer Tab, MapLibre + OpenFreeMap dark, Geocoder-Cache, Google-Maps-Links |
| E-Mail | Konten in Einstellungen, IMAP (`com.sun.mail:android-mail`), Abschnitt im Kontakt |
| Fotos | Abschnitt „Fotos“ im Kontakt, Vollbild-Pager, im Backup enthalten |

---

# Gesamtplan v2 (13. Oktober 2026)

Ergebnis aus den Gesprächen nach Version 1.0.7, drei Meta-Iterationen.

## Grundsätze

1. **Nichts Intelligentes, keine KI.** Orbit tut nur, was du auslöst oder was einer festen, sichtbaren Regel folgt (z. B. „Nummer exakt gleich“, „Nummer beginnt mit …“, „Adresse endet auf @firma.at“).
2. **Keine Fälligkeit.** Kein Rhythmus, kein „fällig“, kein Drängeln. Orbit schreibt weiter mit (Anrufe, SMS, E-Mails, eigene Einträge); sichtbar im Verlauf.
3. **Organisationen sind keine Menschen.** Standardmäßig ausgeblendet; ihre Verknüpfungen zu Menschen („Zuordnungen“) sind bewusst getrennt von Beziehungen zwischen Menschen.
4. **Qualität vor Menge.** Jede Funktion bekommt Leerzustand, Rückgängig, Haptik, Animation.
5. **Für Profis gebaut.** Keine Barrierefreiheits-Arbeit, keine Tutorials, keine Erklärtexte. Dichte Oberfläche, kurze Texte, Gesten und Langdruck-Menüs statt Hilfen.

## Datenmodell v2 (eine einzige Umstellung, mit automatischer Sicherung vorher)

| Bereich | Neu | Entfällt / wird umgewandelt |
|---|---|---|
| Kontakt | `dates` (Anlässe), `links`, `fields` (eigene Felder), `members` (Ansprechpartner, nur Orgs), `numberPrefixes`, `emailDomains`, `mapsUrl`, `pinnedNote` | `rhythmDays`, `paused`, `snoozedUntil`, `remindedDueDay`; `birthday` + `anniversaries` → `dates`; `website` → erster Link |
| Zuordnung (neu, eigene Liste) | `OrgLink(personId, orgId, role, note)` – z. B. Papa → Hausarzt → „Arzt Müller“ | bisherige Org-Beziehungsarten (works_at, patient, client, member) → Zuordnungen |
| Beziehung | nur noch Mensch ↔ Mensch | – |
| Eintrag | `memberId` (mit wem bei der Org), `attachmentIds`, `sharedId` (ein Treffen mit mehreren Personen), Mail-Verweis | – |
| Ablage (neu, global) | `Attachment(file, mime, takenAt, contactIds)` – Fotos und PDFs, einmal gespeichert, mehreren Kontakten zuordenbar | `gallery` → Ablage |
| Gruppe / Eintragsart | – | `rhythmDays`, `resetsTimer` |

## Gemeinsame Bausteine (zuerst, damit jede Funktion sie nur nutzt)

1. **Index**: pro Datenänderung einmal vorberechnet – letzter Kontakt je Art, Suchtext, nächste Anlässe, Zuordnungs-Tabellen.
2. **Zuordnungs-Maschine**: Nummer → Kontakt/Ansprechpartner (exakt, dann längster Nummernbereich), Mail-Adresse → Kontakt (exakt, dann Domain-Regel). Genutzt von Anruf-/SMS-/Mail-Abgleich, Anrufer-Info, Doppelte-Nummer-Hinweis.
3. **Ablage** für Fotos und Dateien mit Referenzzählung.
4. **Planer** für alle Benachrichtigungen (Anlässe mit Vorlauf, Themen mit Datum, Treffen am Morgen, Nachfrage nach Treffen, Backup-Fehler).
5. **Teilen-Eingang**: eine Stelle für Text, Links, Google-Maps-Orte, Bilder, PDFs → „Wohin damit?“.
6. **UI-Bausteine**: Kacheln, generisches Filter-Sheet, Rückgängig-Leiste, Leerzustände, Abschnittsköpfe.

## Releases

| Release | Inhalt |
|---|---|
| R1 + R2 (zusammen) | Datenmodell v2, verschlüsseltes Backup, Fehlerprotokoll, Tempo, Tab-Zustand, Rückgängig · neue Leiste Kontakte · Verlauf · Anlässe · Netz · Karte, Einstellungen hinter Zahnrad, neue Kontaktseite mit Verlauf unten und Filtern, Anlässe-Tab, Planer |
| R2b Orbit als Telefon-App | Standard-Telefon-App (ROLE_DIALER, InCallService): eigener Anrufbildschirm (eingehend, laufend, Anklopfen, Konferenz), Halten, Stumm, Lautsprecher/Bluetooth, Tastenfeld, Wählen mit T9-Suche, Dual-SIM, Benachrichtigung im Anruf-Stil mit Vollbild bei gesperrtem Handy, Näherungssensor; Klingelton bleibt beim System; Rückfall auf die vorinstallierte Telefon-App, falls Orbit nicht startet |
| R3 Organisationen | Zuordnungen, Ansprechpartner, benannte Nummern/Adressen, Regeln, Links/Portale mit Vorlagen, Schnellzugriff, Teilen aus Google Maps/Chrome, eigene Felder, Öffnungszeiten |
| R4 E-Mail v2 | Abgleich nur von Neuem, Gesendet-Ordner, Mails im Verlauf, Antworten, Anhänge, Status; Microsoft-Anmeldung, falls App-Registrierung vorhanden |
| R5 Galerie & Dateien | Ablage, Fotos-Seite, Aufnahmedatum, Fotos an Einträgen, mehrere Kontakte pro Foto, teilen rein/raus |
| R6 Komfort | Anrufer-Info, Schnelleinstellungs-Kachel, Widgets, Shortcuts, Mehrfachauswahl, Mehrfach-Import, QR-Karte, Export Excel/vCard |

## Verwandte Kleinigkeiten (nicht genannt, aber dazugehörig)

- Zuordnungen: „Papas Hausarzt anrufen“ mit einem Tipp in Papas Kontakt; in der Org „Papa und Mama gehen hierher“; Rollen aus beiden Blickrichtungen; auch für dich selbst (Hausarzt, Unis).
- Verlauf: ein Treffen mit mehreren Personen als ein Eintrag; schnelles Rückdatieren (gestern, letzte Woche); verpasste Anrufe mit Rückruf; Suche im Verlauf; Notiz zu automatischen Einträgen.
- Anlässe: Gratulieren-Knopf mit eigener Textvorlage; Geschenke pro Jahr abhaken (keine Doppelgeschenke); Alter im Kopf; optional in den Handy-Kalender.
- Organisationen: Öffnungszeiten mit „jetzt geöffnet / schließt um 18:00“; Logo der Webseite als Bild (bestätigen); Kopier-Knöpfe (Kunden-, Matrikelnummer).
- Kontakte: Profilbild zuschneiden; angepinnte Notiz oben; Kontakt als vCard/QR teilen.
- Einstellungen: Speicher-Übersicht; Sperre sofort / nach 1 / 5 Minuten.

## Wow durch Ausarbeitung (v2)

Regel: höchstens ein großer Moment pro Screen, jede Animation unter 400 ms, Sensor-Effekte aus im Energiesparmodus. Keine Barrierefreiheits-Anpassungen, keine Erklärtexte, kein „Was ist neu“-Popup (nur kurz in den Einstellungen).

**Signature-Momente**
- Anlässe-Tab: **Jahresrad** – das Jahr als Umlaufbahn, Anlässe als Punkte, heute als leuchtender Planet; Monat antippen springt in die Liste.
- Erster Start: ausgewählte Handy-Kontakte **fliegen als Bilder in deinen Orbit** um dich (die Sonne), dann wird daraus die Liste.
- Netz: Wechsel der Mitte **morpht** die Knoten an ihre neuen Plätze; Auswahl dimmt den Rest und lässt den **Weg zur Mitte aufleuchten**.
- Fotos: Vollbild **wächst aus der Kachel** und fällt beim Runterwischen in sie zurück.
- Ich-Karte: Visitenkarte mit QR und **Holo-Schimmer, der sich beim Neigen bewegt** (AGSL-Shader + Lagesensor).
- Echte **Glas-Unschärfe** hinter Leiste und Köpfen (Haze, Android 12+).
- Kontaktseite **nimmt die Farbe der Person an** (Palette aus dem Profilbild, Organisationen blau).

**Gefühl**
- Geteilte Übergänge (Bild Liste → Kopf, Kachel → Organisation), Zurück-Geste mit Vorschau.
- Haptik-Sprache: Tick an Schwellen, Bestätigen, Ablehnen, sanfte Ticks beim A–Z-Scrubber und Monatswechsel.
- Federnde Wisch-Aktionen, Knöpfe verformen sich beim Drücken (Expressive Shape-Morph), Speichern morpht zum Häkchen.
- Lange drücken auf Kontakt: **Vorschau-Karte** mit letzten Einträgen und Aktionen.
- Zahlen in Tabellenziffern, Zähler rollen statt zu springen.

**Details zum zweiten Hinsehen**
- „Annas 30er ist in 12 Tagen – ein Donnerstag.“
- Suche markiert Treffer und zeigt den Satz aus Notiz oder Verlauf.
- Öffnungszeiten: „schließt in 40 Min“, Farbe wechselt kurz vor Schluss.
- Eingefügte Nummer in der Suche: „gehört zu Arzt Müller“.
- Geburtstags-Benachrichtigung mit Foto und „Gratulieren“ mit deiner Textvorlage.
- Animierter Startbildschirm, Themen-Icon, Widget-Vorschauen im Auswahlmenü.
- Tablet/Foldable: automatisch zweispaltig (Liste | Kontakt).
- Kopieren zeigt „Kopiert“, lange Namen werden sauber verkleinert statt abgeschnitten.

**Wow pro Update (eingewoben, nicht nachträglich)**

| Update | Wow-Anteile |
|---|---|
| R1+R2 | Übergang Liste → Kontaktkopf, Zurück-Geste mit Vorschau, Milchglas-Leiste und -Köpfe, Haptik-Sprache, A–Z-Leiste mit Ticks, Jahresrad, Kontaktseite in Personenfarbe, rollende Zahlen, Vorschau-Karte per Langdruck, Speichern-zu-Häkchen, Rückgängig mit Zeitring, Suche mit Treffer-Satz, „ein Donnerstag“, animierter Startbildschirm, Themen-Icon |
| R2b | Anrufbildschirm in der Farbe des Anrufers mit Foto, Rolle („Hausarzt von Papa“), letztem Kontakt, offenen Themen und anstehendem Anlass; Annehmen per Wisch mit Haptik; während des Gesprächs Themen abhaken und Notiz tippen; laufender Anruf als Chip in der Statusleiste |
| R3 | Übergang Kachel → Organisation, Portal-Kacheln mit Logo und Druck-Morph, Öffnungszeiten live, eingefügte Nummer → „gehört zu …“, Netz-Morph und leuchtender Weg, Homescreen-Shortcuts mit Logo |
| R4 | Platzhalter statt Ladekreis, Mails klappen im Verlauf weich auf, Wellen-Indikator beim Aktualisieren, „zuletzt abgeglichen vor 2 Std.“ |
| R5 | Foto wächst aus der Kachel und fällt zurück, Runterwischen zum Schließen, Monats-Scrubber mit Ticks |
| R6 | Ich-Karte mit Holo-Schimmer, Start „Dein Orbit füllt sich“, Geburtstags-Benachrichtigung mit Foto und Gratulieren, Widget-Vorschauen, zweispaltig auf Tablet/Foldable |
| Abschluss | Feinschliff über alles: Abstände, Timings, Ruckler, Texte, Screenshot-Durchsicht aller Screens |

## Qualitätssicherung

- Unit-Tests (Logik, Umstellung, Zuordnung, Planer) und Screenshot-Tests aller Screens mit festen Testdaten im CI; Release nur, wenn alles grün ist.
- Screenshots landen im Branch `ci-log`, damit sie vor jedem Release angesehen werden können.
- Unabhängige Code-Prüfung durch einen zweiten Agenten vor jedem Release.

## Offene Entscheidungen

- Microsoft-Anmeldung (Outlook, Microsoft 365/PHOÖ): braucht eine einmalige App-Registrierung durch dich.
- WhatsApp-Erkennung: optional, standardmäßig aus.
