# Orbit – Gesamtplan (verbindlich, Stand 15.10.2026)

Dieses Dokument ist die einzige verbindliche Spezifikation. Die Entstehung (Recherche, Iterationen, ältere Pläne) steht in `PLAN-history.md` und ist nur Hintergrund. Fortschritt: `PROGRESS.md`.

Rangfolge bei Zielkonflikten (R1–R7): **1. Datensicherheit → 2. Wow (Kapitel 1) → 3. Funktionsumfang.** Lieber eine Funktion weniger als eine ohne Wow.

**Ab Version 3.0 gilt Kapitel 9** (Rangfolge 9.1: Daten und Anrufe gehen nie verloren → Funktionalität und Wow gleichrangig → Backup/Datenschutz nachrangig).

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
| W6 | **Jeder Zustand ist gestaltet:** leer, lädt, Fehler, offline, ein Eintrag, tausend Einträge, sehr langer Name, kein Foto. Leerzustand = ein kurzer Satz + genau eine Aktion. |
| W7 | **Sprache präzise und österreichisch:** `de-AT` (Jänner), richtige Einzahl/Mehrzahl („1 Tag“, „2 Tage“), relative Zeiten, die live aktuell bleiben, Wochentag bei nahen Terminen. Kurz, für Profis, keine Erklärtexte. |
| W8 | **Tempo ist Wow:** Kaltstart bis Liste < 400 ms, Suche pro Tastendruck < 16 ms, Scrollen ohne Ruckler, Anrufbildschirm sofort. |
| W9 | **Ein Token-System:** Bewegung schnell 150 / mittel 250 / betont 350 ms mit Expressive-Federn; **keine Animation länger als 400 ms** – einzige Ausnahmen sind Sequenzen ohne direkte Berührungs-Antwort: Start-Moment, „Dein Orbit füllt sich“, Einzeichnen des Jahresrads beim Öffnen, Sternenregen, Kamerafahrt der Karte; Abstände 4/8 dp; feste Radien, Glow-Stufen und Haptik-Typen. Keine Einzellösungen. |

Im Energiesparmodus laufen keine Sensor-Effekte (Neigung) und keine Dauer-Animationen (Parallax, Pulsieren). Keine Barrierefreiheits-Anpassungen.

### 1.2 Durchgehende Systeme

- **Lebender Weltraum:** Sternenfeld in drei Tiefenebenen mit Parallax beim Scrollen und leicht beim Neigen; jeder Tab hat seine eigene Nebel-Tönung. Ein Tab-Wechsel ist eine kurze Kamerafahrt – die Sterne gleiten seitlich, der Inhalt blendet über.
- **Orbit-Leiste:** Der Indikator des aktiven Tabs ist ein leuchtender Planet, der beim Wechsel im Bogen mit Lichtschweif zum neuen Tab fliegt.
- **Echtes Milchglas** (Haze) hinter Leiste, Köpfen und Sheets.
- **Spotlight:** Glasflächen leuchten unter dem Finger radial auf und klingen weich ab.
- **Haptik-Choreografie:** Haptik sitzt exakt auf dem Höhepunkt der Animation – Einrasten, Schwelle, Bestätigen, Ablehnen, feine Ticks.
- **Geteilte Übergänge überall:** Bild Liste → Kontaktkopf, Kachel → Organisation, Foto → Vollbild, Datumskachel → Anlass, Benachrichtigung → Anrufbildschirm.
- **Zurück mit Gedächtnis:** Zurück-Geste mit Vorschau; Rückkehr an exakt dieselbe Scrollposition, die berührte Zeile glimmt kurz nach.
- **Typografie in Bewegung:** große Titel schrumpfen beim Scrollen in die Kopfzeile, Zahlen rollen, Tabellenziffern überall, wo gezählt wird.
- **Physik statt Animation:** Wisch-Aktionen mit Widerstand, die federnd zurückschnappen; Knöpfe verformen sich beim Drücken (Expressive Shape-Morph); „Speichern“ morpht zum Häkchen, dann schließt sich die Seite.
- **Rückgängig statt Nachfragen:** Leiste mit ablaufendem Zeitring (nach Löschen, Archivieren, Eintragen, Wischen).
- **Berechtigungen erst, wenn du die Funktion einschaltest**, mit einem Satz warum – nie gesammelt beim Start.
- **Platzhalter in exakter Endform** statt Ladekreisen.

### 1.3 Signature-Moment je Screen

| Screen | Moment |
|---|---|
| Start | Ring zeichnet sich, Planet schwingt ein und **wird nahtlos zum Indikator der Orbit-Leiste**. |
| Kontakte | A–Z-Leiste mit Lupen-Blase und Tick je Buchstabe; die Schnellzugriff-Leiste steht oben, sobald ein Link angepinnt ist – **Überziehen nach unten öffnet sie zur Schublade mit allen angepinnten Links in großen Kacheln**. |
| Kontaktseite | Kopf in der Farbe der Person (Palette aus dem Profilbild, Organisationen blau), Profilbild unscharf als Hintergrund; **der Verlauf ist eine Zeitleiste, deren Linie sich beim Scrollen zeichnet**; „Zuletzt“-Chips zählen hoch. |
| Verlauf-Tab | Ein neuer Eintrag (z. B. gerade beendeter Anruf) **gleitet oben mit kurzem Leuchten ein**. |
| Anlässe | **Jahresrad**: das Jahr als Umlaufbahn, Anlässe als Punkte (Winkel = Tag im Jahr), heute als wandernder Planet; beim Öffnen zeichnet sich der Ring und die Punkte erscheinen nacheinander; Antippen eines Monats springt in die Liste; Geburtstag heute: einmaliger Sternenregen; „Gratuliert“: Sternenfunke; die Geschenkzeile klappt federnd auf. |
| Netz | Beim Wechsel der Mitte **gleiten und federn die Knoten** an ihre neuen Plätze, statt neu aufzuploppen; Antippen dimmt den Rest und lässt den **Weg zur Mitte aufleuchten**; ferne Ringe leicht unscharf (Tiefenschärfe). |
| Karte | **Kamerafahrt im Bogen** (raus – rüber – rein) zum Kontakt; Marker ploppen federnd; ein Cluster platzt beim Antippen in seine Mitglieder auf. |
| Anrufbildschirm | **Annehmen, indem du den Planeten in die Sonne ziehst** (große Trefferfläche, Haptik beim Einrasten), Ablehnen durch Wegschnippen; Sternenhimmel mit Neigung; Anruferfarbe; nach dem Auflegen gleitet eine Notizzeile mit offener Tastatur hoch. |
| Organisation | Portal-Kacheln mit echten Logos; „geöffnet“-Punkt pulsiert; Mitarbeiter „Nur hier“ als **Satelliten-Reihe**. |
| Archiv | Kontakte als **ferne, entsättigte Sterne**; „Zurückholen“ gibt die Farbe zurück und lässt das Bild in die Liste fliegen. |
| Fotos | Vollbild **wächst aus der Kachel** und fällt beim Runterwischen zurück, Hintergrund blendet mit dem Finger aus. |
| Einstellungen | Statuszeilen mit Live-Indikatoren; die Achtung-Karte glimmt; Schalter morphen mit Haptik. |
| Ich-Karte | Visitenkarte mit QR und **Holo-Schimmer, der sich beim Neigen bewegt** (AGSL-Shader + Lagesensor). |
| Erster Start | Zwei Schritte: ICH (Name, Foto) → Handy-Kontakte auswählen. Dann **„Dein Orbit füllt sich“** – die ausgewählten Kontakte fliegen als Bilder in die Umlaufbahn um dich und ordnen sich zur Liste. |
| Widgets | Gleiche Sprache, echte Vorschauen im Auswahlmenü. |

### 1.4 Details-Katalog („daran hat jemand gedacht“) – Mindestumfang

- „Annas 30er ist in 12 Tagen – ein Donnerstag.“
- Suche markiert Treffer und zeigt den Satz aus Notiz oder Verlauf.
- Eingefügte Nummer in der Suche → „gehört zu Arzt Müller“ (bei der Durchwahl eines versteckten Mitarbeiters nur die Organisation; archivierte Kontakte nie).
- Öffnungszeiten: „schließt in 40 Min“, Farbe wechselt kurz vor Schluss.
- Kopieren zeigt „Kopiert“; Kunden-/Matrikelnummer mit Kopier-Knopf, auch im Anrufbildschirm.
- Lange Namen werden sauber verkleinert statt abgeschnitten.
- Tastatur: Enter springt ins nächste Feld bzw. speichert; richtige Tastaturart je Feld.
- Verpasste Anrufe als Punkt am Verlauf-Tab.
- Geburtstags-Benachrichtigung mit Foto, „Gratulieren“ (öffnet WhatsApp, sonst SMS, mit der eigenen Vorlage vorausgefüllt) und „Gratuliert ✓“ direkt in der Benachrichtigung.
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
4. **Für Profis gebaut.** Keine Barrierefreiheits-Arbeit, keine Tutorials, keine Erklärtexte, kein „Was ist neu“-Popup (nur eine Zeile in den Einstellungen). Dichte Oberfläche, Gesten, Langdruck-Menüs. Die vorhandenen Hinweistexte aus 1.0.7 (Filter-Sheet, E-Mail-Konten, Gruppen u. a.) werden entfernt oder auf einen Satz gekürzt.
5. **Daten gehen nie verloren.** Jede Umstellung sichert vorher automatisch, ist getestet; Backups wahlweise verschlüsselt (Jakob 15.10.: Verschlüsselung freiwillig).
6. Design „Dark Space“ (feste Farben im Objekt `Space`, Space Grotesk + Inter, Glas, Glow). Alle Texte Deutsch, Du-Form.

---

## 3. Funktionen

### 3.0 Bestand aus Version 1.0.7 – bleibt, mit Anpassungen

**Bleibt:** Anruf- und SMS-Abgleich (Mindestdauer, SMS pro Tag gebündelt) · Vorschläge für unbekannte Nummern (anlegen als Mensch oder Organisation) · eigene Einträge und frei definierbare Eintragsarten · Gruppen (Farbe, Pin) · Themen „Nächstes Mal ansprechen“ inkl. Benachrichtigung während des Anrufs · Notiz nach dem Anruf per Benachrichtigung · Treffen planen (Kalender-Eintrag, Nachfrage am Tag danach) · Geschenkideen · „Wie kennengelernt“, Notizen, Favoriten · Papierkorb (30 Tage) · App-Sperre per Biometrie/PIN · Backup-ZIP, wöchentlich in einen Ordner · In-App-Updates · Onboarding · **ICH** (automatisch, umbenennbar, nicht löschbar, Mitte des Netzes, Bezugspunkt für Entfernungen; eigene und gemeinsame Anlässe mit ICH – „Ich & Anna“ – erinnern normal) · Filter-Sheet mit angepinnten Gruppen · Organisationen als eigene Art (eckiges Bild, Gebäude-Symbol, standardmäßig ausgeblendet, Treffer-Hinweis in der Suche) · **Netz-Tab** (Mitte wählbar, Tiefe 1–4, Pan/Zoom, Auswahlkarte) · **Karte-Tab** (MapLibre + OpenFreeMap dark, Geocoder-Cache, Entfernung zu ICH, „In der Nähe“, Google Maps/Route) · E-Mail-Konten per IMAP mit App-Passwort, Passwort im Android-Keystore, nicht im Backup · Fotos-Ordner pro Kontakt (wird zur Ablage, 3.10).

**Anpassungen:**
- Netz und Karte zeigen keine archivierten Kontakte und keine versteckten Mitarbeiter. Im Netz erscheinen Zuordnungen zu Organisationen nur bei eingeschaltetem Org-Schalter, als eigene blaue Linie, getrennt von Beziehungen.
- Eintragsarten verlieren „setzt Timer zurück“; Gruppen verlieren den Rhythmus.
- Das bestehende Homescreen-Widget wird zum **Anlässe-Widget**: statt „fällig“ die nächsten angepinnten Anlässe; ohne angepinnte Anlässe (auch vor R3) die nächsten Geburtstage. Favoriten bekommen ein eigenes Widget (3.14). Die tägliche Benachrichtigung enthält nur noch Anlässe.
- Der E-Mail-Abschnitt im Kontakt entfällt (Mails stehen im Verlauf, 3.9).
- Der Status-Filter „Archiv“ verschwindet aus dem Filter-Sheet (Archiv nur noch in den Einstellungen, 3.11).

**Entfällt komplett:** Rhythmus, „fällig“, Pausieren, „Um eine Woche verschieben“, Orbit-Tab als Fälligkeitsliste, Filter und Sortierung „Fällig“, Anzeige „N fällig“, roter Glow für Fällige, Fälligkeit im Widget und in der Benachrichtigung.

### 3.1 Hülle und Navigation
- Untere Leiste: **Kontakte · Verlauf · Anlässe · Netz · Karte** (der Orbit-Tab wird zum Verlauf-Tab, der Einstellungs-Tab entfällt). Einstellungen hinter dem **Zahnrad** oben rechts in der Kontaktliste. Ab 3.0-C ohne Netz (9.5 C5).
- Tabs behalten Scrollposition, Suche und Zustand; die Karte bleibt geladen.

### 3.2 Kontaktliste
- Wie bisher: Suche, Filter-Sheet (Anzeigen: Menschen/Alle/Organisationen, Gruppen mit Pin, **Organisation**, Sortierung), angepinnte Gruppen als Chips, ICH-Karte oben, Hinweis „N Treffer bei Organisationen“.
- Sortierung: Name · Zuletzt Kontakt · Am längsten her · Neu hinzugefügt · Häufigkeit. Kein „Fällig“.
- Schnellzugriff-Leiste mit angepinnten Portalen/Links (siehe 3.7).
- **Langdruck auf Kontakt:** Vorschau-Karte mit den letzten drei Einträgen und Aktionen; Loslassen schließt sie, Loslassen über einer Aktion führt sie aus. Die Aktion „Auswählen“ startet die **Mehrfachauswahl** (Gruppe zuweisen, Archivieren, Exportieren).

### 3.3 Kontaktseite
- Reihenfolge: Kopf (mit „Zuletzt: Anruf vor 3 Tagen“, Alter) → Schnellaktionen → Kontaktdaten → angepinnte Notiz, Themen, Notizen → Fotos → Organisationen (Zuordnungen, eckige Kacheln mit Anruf-Knopf) → Beziehungen (Menschen, rund) → Anlässe → **Verlauf ganz unten**.
- Über dem Verlauf Filter-Chips je Art mit letztem Datum: `Alle` `Anruf · vor 3 T` `Treffen · vor 2 M` `E-Mail · vor 5 T` `SMS · vor 1 W`; daneben eine Suche nur im Verlauf dieses Kontakts.
- **Eigene Felder** (Bezeichnung + Wert, z. B. Kundennummer, Versicherungsnummer, Matrikelnummer) bei Menschen und Organisationen, jeweils mit Kopier-Knopf.
- Leere Abschnitte verschwinden; unten eine Zeile `+ Anlass · + Link · + Foto · + Notiz`.
- Profilbild zuschneiden; Kontakt als vCard/QR teilen.
- Ist eine Person einer Organisation zugeordnet, führt ihr Firmen-/Arbeitsfeld per Tipp zur Organisation.
- Bis R3 zeigt die neue Kontaktseite Geburtstag und Jahrestage wie in 1.0.7; ab R1b zeigt sie die umgestellten Zuordnungen schlicht (Organisation, Rolle, Anruf-Knopf), die volle Gestaltung kommt mit R4. Der Anlässe-Tab erscheint in der Leiste ab R3 (W5: nur zeigen, was es schon gibt).
- Themen optional mit Datum → Benachrichtigung an dem Tag.

### 3.4 Verlauf
- Tab „Verlauf“: alle Einträge aller (sichtbaren) Kontakte chronologisch, Filter nach Art, Suche, Orgs per Schalter; Anrufe inkl. unbekannter Nummern mit Rückruf.
- Ein Treffen mit mehreren Personen ist **ein** Eintrag bei allen (`sharedId`).
- Gespräche mit versteckten Mitarbeitern erscheinen im Verlauf-Tab nur als „Gespräch · <Organisation>“, ohne Mitarbeiternamen und ohne Notiz.
- Schnelles Rückdatieren (gestern, letzte Woche); Notiz auch zu automatischen Einträgen.
- Abgleich Anrufe/SMS wie bisher; E-Mails siehe 3.9.

### 3.5 Anlässe und Geburtstage
- Anlässe pro Kontakt: Arten Geburtstag (max. einer), Hochzeitstag, Jahrestag, Kennenlerntag, Namenstag, Gedenktag, eigene Bezeichnung; Jahr optional („Jahr unbekannt“); jährlich oder einmalig; **gemeinsam mit** einem zweiten Kontakt (einmal gespeichert, bei beiden sichtbar, eine Erinnerung); Erinnerung keine / am Tag / 1 / 3 / 7 / 14 Tage vorher (mehrfach wählbar) – Standard für Geburtstage (neu und umgestellt): am Tag + 7 Tage vorher mit Geschenkideen, sonst am Tag; Notiz; Pin.
- Tab „Anlässe“ mit drei Reitern: **Geburtstage** (Standard) · Angepinnt · Alle Anlässe; Jahresrad oben.
- Geburtstage: alle Menschen mit Geburtstag (ohne Archiv, Organisationen, Mitarbeiter), ab heute fortlaufend; Abschnitte Gerade vorbei (7 Tage) · Heute · Diese Woche · Diesen Monat · Monate; Zeile mit Datumskachel und Wochentag, „wird 31“, Bezug zu heute, Status Gratuliert/Geschenk; unten „N Kontakte ohne Geburtstag“ mit Eingabe in der Zeile.
- Abhaken ohne Fenster: Gratuliert per Tipp auf das Häkchen oder Wisch nach rechts, automatisch beim Gratulieren-Knopf, aus der Benachrichtigung. Geschenk: die Zeile klappt zu einer Textzeile „Was?“ auf, darunter Chips mit den zuletzt Mitschenkenden und ein Datums-Chip (vorbelegt mit dem Geburtstag); Enter speichert und klappt zu. Beides sind Verlaufseinträge („Gratuliert“, „Geschenk“) mit Anlass, Jahr und Mitschenkenden; Status gilt pro Jahr. Geschenkideen antippen = in diesem Jahr verschenkt; Geschenk-Historie im Kontakt („2025: Buch (mit Tom) · 2024: Gutschein“). Schalter in den Einstellungen blendet das Abhaken komplett aus.
- Für „Angepinnt“/„Alle“: Filter-Button wie in der Kontaktliste. **Filter:** Art (mehrfach), Gruppe, Zeitraum 7 / 30 / 90 Tage / ganzes Jahr, „Nur runde“ (Jahreszahl durch 5 teilbar), Organisationen einblenden. **Sortierung:** Nächste zuerst (Standard; Abschnitte Heute · Diese Woche · Dieser Monat · Später), Kalender Jänner–Dezember (Monatsüberschriften), Name, Jahre (höchste zuerst); jede umkehrbar.
- Gratulations-Vorlage pro Kontakt (Du/Sie, Sprache); „Gratulieren“ öffnet WhatsApp, sonst SMS, mit der Vorlage vorausgefüllt. Tägliche Benachrichtigung nur noch mit Anlässen, gesammelt („Heute: Annas 30. Geburtstag · In 7 Tagen: Hochzeitstag Anna & Tom“), „Anrufen“ nur, wenn die Sammel-Benachrichtigung genau eine Person betrifft; Vorlauf-Erinnerungen zeigen die Geschenkideen der Person; optional Übernahme in den Handy-Kalender.
- Im Kontakt: Abschnitt „Anlässe“ mit Art, Datum, Zusatz („wird 30“), Countdown, Nadel, „Alle anpinnen“; Hinzufügen-Dialog mit Art-Chips, Datum, Jahr optional, Wiederholung, Erinnerungen, „gemeinsam mit“, Notiz, Anpinnen.
- Auch Organisationen können Anlässe haben (z. B. einmalig „Vertrag endet“).
- Einmalige Anlässe verschwinden nach dem Tag aus „nächste“, bleiben aber im Kontakt. Der 29. Februar wird in Nicht-Schaltjahren am 28. angezeigt.
- Zeilen in „Angepinnt“/„Alle“: Datumskachel, Name, Art, „wird 30“/„5. Hochzeitstag“, Countdown, Nadel; heute hervorgehoben mit Anrufen/WhatsApp; Antippen öffnet den Kontakt, Langdruck pinnt/bearbeitet.
- Umstellung: `birthday` → Anlass „Geburtstag“; `anniversaries` → Anlässe, Art aus der Bezeichnung (Hochzeitstag, Namenstag, Jahrestag, Kennenlerntag), sonst eigene Bezeichnung.

### 3.6 Organisationen
- Benannte Nummern und Adressen (Zentrale, Empfang, Termine, Fax, Notfall, eigene), Standardnummer.
- **Zuordnungen** (getrennt von Beziehungen): Mensch → Organisation mit Rolle aus beiden Blickrichtungen (Hausarzt, Zahnarzt, Facharzt / Patient:in · Arbeitgeber / Mitarbeiter:in · Kolleg:in · Vorgesetzte:r · Projektleitung · Schule/Uni / Schüler:in/Studierende · Bank · Versicherung · Verein / Mitglied · eigene Bezeichnung). Personen-Seite: „Hausarzt: Arzt Müller“ mit Anruf-Knopf, „Auch im COOL Lab: Anna, Tom“. Auch für ICH. Bestehende Org-Beziehungen werden zu Zuordnungen.
- Org-Seite mit zwei Abschnitten: **Aus deinen Kontakten** (echte Menschen per Zuordnung) und **Nur hier** (versteckte Mitarbeiter).
- **Versteckte Mitarbeiter:** Name, Rolle, Foto, Durchwahl, Mail, dauerhafte Notiz, eigene Gespräche (Org-Einträge mit `memberId`), „zuletzt gesprochen“. Nie in Liste, Suche, Verlauf-Tab, Anlässen, Netz, Karte, Tastenfeld-Suche. Gespräch eintragen in einer Zeile mit Mitarbeiter-Chip; ein neuer Mitarbeiter lässt sich dabei mit einem Feld anlegen. Jeder Mitarbeiter hat eine eigene Seite mit allen Gesprächen mit ihm. Ab vielen Mitarbeitern hat „Nur hier“ eine eigene Suche, die nur diese Organisation durchsucht. „Zu eigenem Kontakt machen“ (neuer Kontakt oder „Mit bestehendem Kontakt zusammenführen“; Gespräche und Notiz wandern mit, die Zuordnung zur Organisation entsteht automatisch) / „Nur in der Organisation führen“.
- **Regeln** (selbst eingeschaltet): Nummernbereich (Präfix) und E-Mail-Domain gehören zur Organisation; Verlauf zeigt „Anruf · Empfang“. Nach Anruf von unbekannter Durchwahl: „Mitarbeiter zuordnen?“.
- Eigene Felder (3.3); Öffnungszeiten mit Live-Anzeige; Logo der Webseite als Bild (bestätigen); Kategorie „Bildung“.

### 3.7 Portale und Links
- Kachel-Raster mit **3 Kacheln pro Reihe** unter dem Org-Kopf: Titel, Adresse, Logo der Seite (einmal geladen, gespeichert; ohne Logo ein passendes Symbol), Notiz (z. B. „Login: k12345678“, nie Passwörter). Öffnen standardmäßig im Orbit-Browserfenster (Custom Tabs, Chrome-Anmeldungen bleiben, Zurück führt zu Orbit); **pro Link wählbar** stattdessen Chrome oder eine installierte App (wird nur gestartet). Langdruck: verschieben, bearbeiten, anpinnen, „Auf Startbildschirm“.
- Vorlagen erscheinen bei Kategorie „Bildung“: **JKU** – KUSSS `kusss.jku.at`, Moodle `moodle.jku.at`, myJKU `my.jku.at`, Bibliothek, Mensa, Webseite; **PH OÖ** – PH-Online `ph-online.ac.at` (PH OÖ wählen), Moodle (Adresse selbst einfügen), Webseite.
- Angepinnte Links: Schnellzugriff-Leiste oben in der Kontaktliste (nur sichtbar, wenn mindestens ein Link angepinnt ist – dann auch bei ausgeblendeten Organisationen), Überziehen öffnet sie zur Schublade (1.3); zusätzlich als Widget.
- Auch bei Personen (LinkedIn, Instagram).

### 3.8 Teilen-Eingang
- Aus Google Maps: Ort teilen → neue Organisation oder zu bestehender; Maps-Link gespeichert („In Google Maps öffnen“ führt exakt zum Ort), Koordinaten per Geocoder.
- Aus Chrome: Seite teilen → als Link zu einer Organisation/Person (Titel und Adresse übernommen).
- Bilder/PDFs → Ordner eines Kontakts; Text → Notiz oder Thema.
- Optional „Daten ergänzen“ aus OpenStreetMap (Webseite, Telefon, Öffnungszeiten), nur auf Knopfdruck, jeder Wert einzeln bestätigt. Nominatim-Regeln: höchstens eine Anfrage pro Sekunde, eigener User-Agent, kein Autocomplete, Ergebnisse cachen, Quelle wird angezeigt.

### 3.9 E-Mail v2
- Abgleich beim Öffnen eines Kontakts und alle 6 Stunden im Hintergrund.
- Abgleich nur von Neuem: pro Konto und Ordner UIDVALIDITY + letzte UID; Hintergrund-Abgleich (nur mit Netz, Wiederholung mit wachsender Pause) holt nur Kopfzeilen und ordnet lokal zu; erster Abgleich 12 Monate; neue Adresse → einmalige Suche (Gmail: X-GM-RAW `from:(…) OR to:(…)`).
- Ordner: Gmail nur „Alle Nachrichten“; sonst Posteingang, Gesendet, Archiv (SPECIAL-USE), Ordnerliste einen Tag gecacht.
- Eine Verbindung pro Abgleich und Konto (wiederverwendet), COMPRESS, ID-Befehl.
- Mails werden **Verlaufseinträge**, gebündelt pro Tag und Richtung („3 E-Mails empfangen“ mit den Betreffzeilen). Lokal gespeichert werden nur Betreff, Datum, Richtung und Mail-Verweis, nie der Text; den Text lädt Orbit beim Antippen. Die Kontaktseite zeigt sofort den lokalen Stand (auch offline), der Abgleich beim Öffnen läuft dahinter. „Antworten“ öffnet die Mail-App mit Empfänger und „Re: Betreff“; Bild-Anhänge in den Ordner übernehmen; Zuordnung inkl. Mitarbeiter und Domain-Regel; Status „zuletzt abgeglichen vor 2 Std.“. Der eigene Mail-Abschnitt entfällt.
- Neue Adresse oder neuer Kontakt: einmalige Suche über die letzten 12 Monate.
- Höchstens eine Verbindung pro Konto gleichzeitig; Port 993 mit implizitem TLS und Zertifikatsprüfung, Zeitlimits 15 s Verbindung / 25 s Lesen bleiben.
- App-Passwörter wie bisher; Microsoft (Outlook, Microsoft 365) nur nach App-Registrierung durch Jakob.

### 3.10 Fotos und Dateien
- Ablage global: Fotos und PDFs einmal gespeichert, mehreren Kontakten zuordenbar (Referenzzählung).
- Fotos-Seite: alle Bilder nach Aufnahmedatum (EXIF), Monatsüberschriften mit Scrubber; im Kontakt die neuesten 6 + „Alle“.
- Fotos an Einträge hängen (Vorschau im Verlauf); teilen hinein (aus der Galerie) und hinaus; ins Handy zurückspeichern (Galerie bzw. Downloads).
- Standard: Fotos werden auf 2048 px (lange Kante) verkleinert; „Originalgröße behalten“ optional; Speicheranzeige.

### 3.11 Archiv
- Archivierte Kontakte behalten alles und erscheinen **nirgends** (Liste, Suche, Tabs, Benachrichtigungen, Beziehungen anderer, Tastenfeld-Suche). Ausnahme Anrufer-Erkennung: Name mit Archiv-Hinweis und „Zurückholen“, Anruf landet still im Verlauf.
- Einstellungen → Archiv: Suche nur im Archiv, Sortierung (Name, archiviert am, zuletzt Kontakt), Gruppierung nach Archivierjahr; Karte mit entsättigtem Bild, letzter Rolle/Firma, „archiviert März 2026 · zuletzt 2025 · 12 Notizen · 34 Einträge“.
- Archivierten Kontakt öffnen: volle Seite, oben „Seit 3 Jahren im Archiv · Zurückholen“, Block „Was du weißt“: angepinnte Notiz, letzte Notizen, Beziehungen und Anlässe auf einen Blick (reine Zusammenstellung). Archivieren per Menü/Mehrfachauswahl mit Rückgängig; Organisationen samt Mitarbeitern.

### 3.12 Orbit als Telefon-App
- Standard-Telefon-App (ROLE_DIALER, InCallService); Jakob aktiviert sie selbst unter Einstellungen → Mitschreiben; beim Aktivieren ein Satz: ggf. „Eingeschränkte Einstellungen zulassen“ nötig, Extras der Google-Telefon-App (Spam-Warnung, Anruf-Filter, visuelle Mailbox) entfallen, Rückwechsel jederzeit über die Standard-Apps. Klingelton bleibt beim System; Rückfall auf die vorinstallierte Telefon-App, falls Orbit nicht gebunden werden kann.
- **Ohne aktivierte Telefon-App-Rolle:** Anrufer-Info als Benachrichtigung beim Klingeln (Name, Rolle, zuletzt, offene Themen, Kundennummer) über den bestehenden Anruf-Empfänger, plus die bisherigen Themen- und Notiz-Benachrichtigungen.
- Eingehend: Foto groß in Anruferfarbe, Name, Rolle („Arzt Müller · Hausarzt von Papa“, „Liles Servicecenter · Herr Mayer (Kundenberatung)“), Notiz-Anfang (bei Mitarbeitern ihre dauerhafte Notiz), zuletzt, offene Themen, anstehender Anlass, Kundennummer; unbekannt: „3 Anrufe in 2 Wochen“; archiviert: Hinweis + Zurückholen.
- Laufend: Halten, Stumm, Lautsprecher/Bluetooth/Kopfhörer, Tastenfeld (DTMF), Anklopfen, Konferenz, Themen abhaken, Notiz, Timer; Näherungssensor; laufender Anruf als Chip.
- Danach: Notizzeile → Verlaufseintrag; „Mitarbeiter zuordnen?“ bei unbekannter Durchwahl; nach Anruf von unbekannter Nummer „Als Kontakt anlegen“ / „Als Organisation anlegen“.
- **Notrufe** laufen immer über das Telefonsystem; das Orbit-Tastenfeld wählt Notrufnummern direkt, ohne Umweg.
- Fällt Android auf die vorinstallierte Telefon-App zurück, zeigt Orbit danach eine Benachrichtigung.
- **App-Sperre blockiert Anrufe nie:** Anrufbildschirm, Tastenfeld und Notruf funktionieren ohne Entsperren der Orbit-Sperre. Auf dem Sperrbildschirm des Handys zeigt der Anrufbildschirm nur Name, Bild und Rolle; Notizen, Themen und Kundennummer erst, wenn das Handy entsperrt ist. Kontaktseite, Verlauf und Notizen außerhalb des Anrufs verlangen weiter die Orbit-Sperre.
- Wählen: Tastenfeld mit T9-Suche (ohne Archiv und versteckte Mitarbeiter), Dual-SIM pro Kontakt, Mailbox.
- Ablehnen mit SMS-Vorlage, „Später zurückrufen“ (15 Min / 1 Std), Nummern sperren.
- Benachrichtigung im Anruf-Stil, Vollbild bei gesperrtem Handy.
- **Anrufer-Kurzindex** (Kapitel 4) für sofortige Anzeige bei kaltem Start.

### 3.13 Einstellungen
- Startseite: Karte **„Braucht deine Aufmerksamkeit“** nur bei Bedarf (fehlende Berechtigung, Backup gescheitert oder noch nie gemacht, Mail-Konto mit Fehler, Update verfügbar); darunter fünf Bereiche mit Statuszeile:
  - **Mitschreiben** („Anrufe ab 60 s · 2 Mail-Konten“): Mindestdauer, SMS, E-Mail-Konten, unbekannte Nummern, jetzt abgleichen, Berechtigungen, Telefon-App.
  - **Benachrichtigungen** („Anlässe um 9:00“): Uhrzeit, Themen im Anruf, Notiz nach Anruf, Nachfrage nach Treffen, Treffen am Morgen (siehe unten).
  - **Kontakte** („5 Gruppen · Vorname zuerst“): Gruppen, Eintragsarten, Namensreihenfolge, **Archiv**, Papierkorb, Abhaken bei Geburtstagen an/aus.
  - **Sicherheit & Backup** („Sperre an · Backup vor 3 Tagen“): App-Sperre (sofort / 1 / 5 Min), Backup-Ordner, automatisch, jetzt sichern (verschlüsselt mit Passwort), exportieren (ZIP, Excel, vCard), wiederherstellen, Speicher-Übersicht.
  - **Über Orbit** („Version 1.x · aktuell“): Updates, kurze Änderungszeile, Fehlerprotokoll teilen.

### 3.13a Benachrichtigungen (Planer)
- Anlässe mit Vorlauf (3.5) · Themen mit Datum (3.3) · **am Morgen eines geplanten Treffens:** offene Themen, letzte Notiz, Partner/Kinder aus den Beziehungen · Nachfrage nach Treffen am Tag danach · Backup fehlgeschlagen · „Später zurückrufen“ (3.12).

### 3.14 Komfort
- Schnelleinstellungs-Kachel „Notiz zum letzten Anruf“, Favoriten-Widget (Anrufen/WhatsApp mit einem Tipp), Anlässe-Widget, App-Shortcuts (Neuer Kontakt, Verlauf, Top-Favoriten dynamisch).
- Mehrere Handy-Kontakte auf einmal übernehmen (Organisation per fester Regel „Firma ohne Personennamen“).
- „Diese Nummer gibt es schon“ erscheint beim Speichern jedes Kontakts und beim Import (feste Regel: gleiche normalisierte Nummer).
- ICH-Karte mit QR (enthält eine vCard, die jede Kamera-App lesen kann); Export Excel (Kontakte und Verlauf) und vCard (Kontakte).

---

## 4. Architektur

- **Speicher aufteilen:** `orbit.json` für den Kern (Kontakte, Organisationen, Mitarbeiter, Zuordnungen, Beziehungen, Anlässe, Links, Einstellungen). Verlauf, Mail-Index und Ablage in **SQLite** (`android.database.sqlite`, ohne Codegenerator): `entries` mit eindeutiger `externalId`, Index (Kontakt, Zeit), Volltextsuche **FTS4** mit `snippet()`.
- **Anrufer-Kurzindex:** bei jedem Speichern eine kleine Datei Nummer → Name, Bild, Rolle, Notiz-Anfang, Kundennummer, Kennzeichen archiviert/Mitarbeiter.
- **Zuordnungs-Maschine:** exakte Nummer → Mitarbeiter-Durchwahl → längster Nummernbereich → Mail exakt → Domain; Ergebnis mit Kennzeichen; Listen/Suche filtern, Anrufer-Erkennung nicht.
- **Index** pro Datenänderung: letzter Kontakt je Art, Suchtext, nächste Anlässe.
- **Volltextsuche und Nummern-Hinweis schließen Daten versteckter Mitarbeiter und archivierter Kontakte aus** (eigene Suche nur im Archiv bzw. in der Organisation).
- **Planer** für alle Benachrichtigungen; **Teilen-Eingang**; **Ablage** mit Referenzzählung; **UI-Bausteine** inkl. Wow-Systeme aus 1.2 als wiederverwendbare Komponenten.
- **Datenmodell v2** in einer Umstellung mit automatischer Sicherung: entfällt `rhythmDays`, `paused`, `snoozedUntil`, `remindedDueDay`, Gruppen-Rhythmus, `resetsTimer`; `birthday`/`anniversaries` → `dates`; `website` → erster Link; `gallery` → Ablage; Org-Beziehungsarten → Zuordnungen. Neu: `dates` (Art, Bezeichnung, Tag, Monat, Jahr optional, Wiederholung jährlich/einmalig, angepinnt, Erinnerungen, gemeinsam mit, Notiz), Einträge „Gratuliert“/„Geschenk“ mit Anlass, Jahr und Mitschenkenden, `links`, `fields`, `members`, `numberPrefixes`, `emailDomains`, `mapsUrl`, `pinnedNote`, `OrgLink`, Eintrag `memberId`/`attachmentIds`/`sharedId`/Mail-Verweis.
- **Backup** exportiert SQLite-Tabellen als JSON und alle Dateien der Ablage (Fotos, PDFs) in die ZIP; verschlüsselt mit einem Schlüssel aus deinem Passwort (PBKDF2-HMAC-SHA256 mit Salt, AES-256-GCM). Für automatische Backups wird der abgeleitete Schlüssel – nie das Passwort – durch den Android-Keystore geschützt gespeichert; Wiederherstellen fragt immer das Passwort. Mail-Passwörter nie im Backup.

---

## 5. Reihenfolge

| Update | Inhalt | Wow-Schwerpunkte (zusätzlich zu 1.1/1.2 auf allen berührten Screens) |
|---|---|---|
| Test-Build (ohne Release) | riskante Bausteine einmal kompilieren: InCallService, Haze, geteilte Übergänge, Roborazzi, FTS4, AGSL | – |
| R1a Fundament | Speicher v2 + Umstellung, Index, Zuordnungs-Maschine, Fälligkeit raus (inkl. Widget und Benachrichtigung, 3.0), Archiv-Logik, verschlüsseltes Backup, Fehlerprotokoll, Test-/Screenshot-System, `de-AT`-Formate | Token-System (W9), Haptik-Typen, Platzhalter |
| R1b Hülle | 3.1–3.4, 3.11, 3.13 (Leiste zunächst mit vier Tabs, Anlässe-Tab ab R3) | Lebender Weltraum, Orbit-Leiste, Milchglas, Spotlight, Start-Moment, Kontakte-Moment (A–Z-Leiste), Vorschau-Karte per Langdruck, Kontaktseite-Moment, Verlauf-Moment, Archiv-Moment, Zurück mit Gedächtnis, Rückgängig-Ring, Speichern → Häkchen, Suche mit Treffer-Satz |
| R2 Telefon | 3.12 inkl. Anrufer-Info ohne Telefon-App-Rolle | Anrufbildschirm-Moment, Anruf-Chip, Benachrichtigungen |
| R3 Anlässe | 3.5, 3.13a | Jahresrad, Sternenregen/-funke, Datumskachel → Anlass, Geburtstags-Benachrichtigung mit Foto und Gratulieren |
| R4 Organisationen | 3.6–3.8 | Kachel → Organisation, Satelliten-Reihe, Live-Öffnungszeiten, Schnellzugriff-Schublade, Netz-Federn und Leuchtweg, „gehört zu …“ in der Suche, Homescreen-Shortcuts mit Logo |
| R5 E-Mail | 3.9 | Mails klappen weich auf, Wellen-Indikator, Offline-Zustände |
| R6 Fotos & Dateien | 3.10 | Foto wächst aus der Kachel, Monats-Scrubber |
| R7 Komfort & Abschluss | 3.14, Erst-Start, Karte-Kamerafahrt | Ich-Karte Holo, „Dein Orbit füllt sich“, Widgets, zweispaltig; **Feinschliff-Durchgang über alle Screens gegen die Charta** |

Zeit gesamt: **26–34 h** (Wow-Budget je Update etwa ein Drittel).

**Alle Updates R1a–R7 sind veröffentlicht (v1.0.226, 15.10.2026).** Danach Version 3.0 (Kapitel 9, Reihenfolge 9.8):

| Update | Inhalt | Wow-Schwerpunkte |
|---|---|---|
| 3.0-A Telefonbuch | 9.3 (A0–A13) | Geist-Bauteil, Haptik-Vokabular, „N im Telefonbuch“ zählt hoch, Felder leuchten beim Übernehmen, Weitergeben-Karte |
| 3.0-B Anruf-Autopilot | 9.4 (B1–B12) | Anrufer materialisiert, Tastenfeld spielt sich selbst, Rückruf-Stapel dehnt sich zum Anruf, Dranbleiben-Ring |
| 3.0-C Heute & Live | 9.5 (C1–C5) | Heute-Reihe mit Sternenstaub, Live-Insel wächst, Spät-dran-Rasten |
| 3.0-D Akten & Meine Dienste | 9.6 (D1–D8) | Vertrags-Bahn, Ausweis mit Holo, Ankommen-Karte |
| 3.0-E Erfassen, Faden, Datenqualität, Automatiken | 9.7 (E1–E24) | Chips lösen sich aus der Orbit-Zeile, Mitternachts-Rad, Karten verschmelzen |

---

## 6. Qualitätssicherung

- Unit-Tests (Logik, Umstellung mit Daten im alten Format, Zuordnung, Planer) und Roborazzi-Screenshot-Tests aller Screens in allen Zuständen mit festen Testdaten; Release nur, wenn alles grün ist.
- Screenshots landen im Log-Branch und werden vor jedem Release angesehen.
- Vor jedem Release: Code-Review-Subagent (Laufzeitfehler, Datenverlust, Logik) **und** Wow-Review-Subagent (gegen Kapitel 1). Bestätigte Funde beheben.
- Entwicklung auf `dev` (Build ohne Release); fertige Updates nach `main` → signiertes Release.

## 7. Offene Entscheidungen (Jakob)

- Microsoft-Anmeldung (Outlook, Microsoft 365/PHOÖ): braucht eine App-Registrierung – bis dahin nicht umsetzen.
- WhatsApp-Erkennung: nicht umsetzen, solange nicht ausdrücklich gewünscht.
- 3.0: die ohne Rückfrage getroffenen Entscheidungen stehen in 9.9 – Jakob kann jede ändern.
- Plattform: Google verlangt ab 2027 eine Entwickler-Verifizierung auch für Apps außerhalb des Play Store; rechtzeitig ein (kostenloses) Konto anlegen und den bestehenden Signaturschlüssel registrieren.

---

## 8. Architektur- und Engineering-Standards (verbindlich)

Ziel: moderner, wartbarer Aufbau nach aktuellem Android- und Software-Engineering-Stand – ohne Technik, die in dieser Umgebung riskant ist (kein Codegenerator wegen AGP 9 / KSP-Risiko). Grundsatzentscheidungen stehen als ADRs in `docs/adr/`; wer davon abweicht, schreibt ein neues ADR.

### 8.1 Schichten und Module
- **Unidirektionaler Datenfluss:** UI (Compose) → State-Holder (ViewModel je Screen, `StateFlow<UiState>`, unveränderliche UiState-Klassen) → Domain (reine Kotlin-Funktionen/Use-Cases) → Data (Repositories über JSON-Store und SQLite). Keine Geschäftslogik und keine schweren Berechnungen in Composables.
- **Gradle-Module** (ADR-0001): `:core:model` (reines Kotlin, Datenklassen + Serialisierung), `:core:domain` (reines Kotlin: Zuordnungs-Maschine, Index, Listen-/Anlass-/Verlaufslogik, Planer-Regeln, Datums-Mathematik), `:core:designsystem` (Android-Library: Space-Tokens, Bewegungs-/Haptik-Tokens, Komponenten, Wow-Systeme aus 1.2), `:app` (Features, Data, Plattform: Telecom, Sync, Mail, Geo, Benachrichtigungen, Widgets). Abhängigkeiten nur nach innen; `:core:*` kennt `:app` nie.
- **Feature-Pakete** in `:app`: `feature/<name>/` mit Screen, ViewModel, UiState; Plattform-Code unter `platform/<name>/`.
- **DI** (ADR-0002): manuelle Konstruktor-Injektion über einen `AppContainer`; keine Hilt/KSP.
- **Persistenz** (ADR-0003): Kern als JSON-Dokument (atomar geschrieben), Verlauf/Mail-Index/Ablage in SQLite (`android.database.sqlite`, Schema-Version per `PRAGMA user_version`, jede Migration getestet), FTS4. Ein Schreiber pro Speicher, Transaktionen für Mehrfach-Änderungen.

### 8.2 Laufzeit-Qualität
- **Nebenläufigkeit:** strukturierte Coroutines; App-weiter Scope für Schreibvorgänge; mehrstufige Persistenz unter `NonCancellable`; Hintergrundarbeit nur per WorkManager mit Netz-Bedingung und exponentiellem Backoff; Telecom-Pfade ohne Blockieren des Main-Threads.
- **Fehlerbehandlung:** an Grenzen (I/O, Netz, Telecom) typisierte Ergebnisse statt Exceptions nach oben; nie still verschlucken – lokales Ringpuffer-Log (1–2 MB) + Absturzbericht, teilbar in „Über Orbit“.
- **Performance-Budgets** aus W8 sind Abnahmekriterien; stabile Keys, `@Immutable`-UiState, Bilder vorab skaliert, Berechnungen auf `Dispatchers.Default`, Startzeit per Log-Messpunkt überwachen.
- **Sicherheit:** Berechtigungen erst bei Bedarf, minimal; Geheimnisse nur im Android-Keystore; Backups verschlüsselt; keine Secrets im Repo; nur TLS.

### 8.3 Tests und Prüfungen
- **Testpyramide:** viele JVM-Unit-Tests für `:core:domain` und alle Datenumstellungen (Fixtures im alten Format, inkl. echter 1.0.7-Struktur); tabellengetriebene Tests für die Zuordnungs-Maschine; Eigenschaftstests für Datums-Mathematik (29. Februar, Jahreswechsel, Zeitzonen); Robolectric + Roborazzi-Screenshots für jeden Screen in allen Zuständen aus 1.1/W6 mit festen Testdaten.
- **Statische Prüfung:** detekt (mit Compose-Regeln, Baseline für Altbestand) und Android Lint als CI-Berichte; neue Funde in neuem Code beheben. Architektur-Regeln (Abhängigkeitsrichtung) als Test.
- **CI:** baut, testet, erzeugt Screenshots und Berichte bei jedem Push; Release nur von `main` und nur, wenn alles grün ist.

### 8.4 Prozess
- **Definition of Done je Update:** Build grün · Tests grün · Wow-Gate (1.5) · Code-Review- und Wow-Review-Subagent ohne offene bestätigte Funde · `PROGRESS.md` aktualisiert · ADR bei Architekturentscheidung · Agent-Notizbuch (`.agent/`) aktualisiert.
- **Zweige:** `dev` als Integrationszweig (Build ohne Release), fertige Updates nach `main` (signiertes Release). Kleine, thematische Commits im Stil `feat(anlaesse): Jahresrad` / `fix(telefon): …` / `refactor(data): …`, Deutsch.
- **Riskantes hinter Schaltern:** Telefon-App-Rolle und neue Hintergrund-Abgleiche sind opt-in bzw. abschaltbar.
- **Release-Tag-Format `v1.0.<run>` nie ändern** – der In-App-Updater liest die Versionsnummer aus dem Tag.
- **Signatur und Update-Weg** (Keystore, Signing-Konfiguration, Updater, Release-Workflow) werden nicht verändert.
- **Agent-Notizbuch:** `.agent/NOTEBOOK.md` zu Beginn jeder Sitzung lesen und bei jedem neuen Problem sofort ergänzen (Symptom → Ursache → Fix); nur für den Agenten.

---

## 9. Version 3.0 (verbindlich ab 15.10.2026)

**Auftrag (Jakob, 15.10.2026, wörtlich):** „DAS PRIMÄRE ZIEL IST UND BLEIBT IMMER, NUR DAS HANDY, MEIN PERSÖNLICHES KONTAKTE MANAGMENT SYSTEM (KEIN SOZIALES UNIVERSUM), PRAGMATISMUS, DATENSCHUTZ IST MIR EGAL, BACKUP FUNKTIONEN UND SICHERUNG IST NUR SEKUNDÄR, DIE FUNKTIONALITÄT UND WOW EFFEKT SIND KING“ – danach: „okay upgrade auf 3.0 jetzt“.

Die Vorschläge 1.5 und 2.0 aus dem Chat waren nur Pläne; dieses Kapitel fasst 1.5 + 2.0 (nach Jakobs Korrektur bereinigt) + 3.0 zu **einer** Spezifikation zusammen. Alles aus Kapitel 1–8 gilt weiter, soweit dieses Kapitel nichts anderes sagt.

**Leitidee:** *Das Telefonbuch, das mitschreibt – und die lästigen Dinge vor deinen Augen selbst erledigt.* Orbit wird das Telefonbuch des ganzen Handys und ein **sichtbarer Autopilot nach festen Regeln**: wählt das Hotline-Menü, ruft die besetzte Ordination so lange an, bis sie frei ist, erkennt den unbekannten Anrufer, gratuliert um Mitternacht – und zeigt dabei jeden Schritt. Ein Tipp übernimmt.

### 9.1 Was für 3.0 gilt

- **Rangfolge 3.0:** zuerst die zwei harten Regeln – *Daten gehen nie verloren* (jede Umstellung legt beim ersten Start automatisch eine Kopie an und ist mit Tests inkl. Daten im alten Format abgesichert) und *kein Anruf geht verloren* (Telefon-App, Anruf-Filter und Autopilot schaltet Jakob selbst ein; im Zweifel klingelt es normal). Danach sind **Funktionalität und Wow gleichrangig King**. Backup-Funktionen und Datenschutz sind nachrangig.
- **Erlaubt (Datenschutz egal):** Online-Abfragen (Herold, Feiertage, Logos, Gravatar), Standort im Hintergrund (Ankommen), Schreiben ins System-Telefonbuch und in den Kalender, die Rollen Anruf-Filter und Telefon-App. Berechtigungen weiterhin erst beim Einschalten der Funktion.
- **Nur das Handy:** kein Tablet-, Laptop-, Uhr- oder Sync-Ausbau (zweispaltig auf Foldables bleibt).
- **Kein soziales Universum:** Orbit bleibt Liste + Akte. Netz verlässt die Leiste (C5).
- **Weiter gültig:** keine KI (nur Jakobs Auslöser oder feste, sichtbare Regeln), keine Fälligkeit/kein Drängeln, keine Microsoft-Anmeldung, kein WhatsApp-Mitlesen, Organisationen standardmäßig ausgeblendet, versteckte Mitarbeiter nur in ihrer Organisation, Archiv nirgends außer Anrufer-Erkennung, Profi-Oberfläche ohne Erklärtexte (Erklärungen im Handbuch, E22), Deutsch/Du/de-AT, Bewegungs-Tokens 150/250/350 ms (max. 400 ms außer Sequenzen ohne Berührungs-Antwort).
- **Datenmodell:** neue Felder nur optional mit Standardwert (Schema bleibt 3, alte Daten lesen sich unverändert, Test mit Daten im alten Format). Muss doch umgestellt werden: Schema 4 nach dem Muster aus R4 (Kopie `orbit.v3.bak.json`, reine wiederholbare Umstellung, Tests).

### 9.2 Das verbindende Prinzip (Abnahmekriterium jeder 3.0-Funktion)

- **Eine Karte, drei Größen:** *Chip* (Statusleiste, Live-Update, Insel) · *Karte* (Heute, Widget, Sperrbildschirm, Benachrichtigung, Stapel) · *Akte* (Kontaktseite, Anrufbildschirm). Gleiche Anatomie überall: Planet (wer) · eine Zahl (wann/wie viel) · eine Hauptaktion. Übergänge sind Wachsen/Schrumpfen derselben Fläche.
- **Ein Lebenslauf:** *Geist* (Vorschlag nach fester Regel, gestrichelt, am Zielort; Halten zeigt Regel/Quelle, Tipp übernimmt, Wischen verwirft) → *Karte* (steht an) → *Live* (läuft, Schritt für Schritt sichtbar) → *Beleg* (Verlaufseintrag mit Stempel der Regel, z. B. „⚙ Dranbleiben“).
- **Eine Live-Insel:** höchstens eine gleichzeitig; Vorrang Anruf/Makro > Dranbleiben > Ankommen > Treffen > geplante Nachricht. In der App Glaskapsel oben, außerhalb Live-Update (ab Android 16, sonst laufende Benachrichtigung) mit derselben Anatomie.
- **Licht-Code:** Personenfarbe = wer · Teal = läuft · Orange = Frist/Kosten · Coral = Abwehr · gestrichelt = Geist.
- **Stille Oberfläche (Null-Text):** auf dem Bildschirm stehen nur Daten und Aktionen; keine Erklärsätze, keine Toasts – Bestätigung am Ort (Morph, Häkchen, Ring).
- **Eine Bediensprache:** Tippen = öffnen/ausführen · Halten = Vorschau/Quelle · Wischen = Schnellaktion (rechts = anrufen/positiv, links = erledigt/weg) · Ziehen = zuordnen/einstellen. Hauptaktionen im unteren Drittel (Daumenzone).
- **Haptik-Vokabular:** feste, benannte Muster in `:core:designsystem` (Tick, Einrasten, Bestätigen, Abwehr, Materialisieren, Takt); neue Funktionen nutzen nur diese.
- **Choreografie ist Abnahmekriterium:** Jede Funktion mit „Moment“ ist erst fertig, wenn der Moment (Ablauf, ms, Haptik) so umgesetzt ist.

### 9.3 3.0-A – Orbit ist das Telefonbuch des Handys

- **A0 Fundament:** Bauteil *Geist* (gestrichelter Rand, langsam wandernder Strich, Inhalt halb transparent; Tipp = Rand zieht sich durch, Inhalt füllt sich, 250 ms, Haptik Bestätigen; Halten = Quelle; Wischen = weg) und Haptik-Vokabular (9.2). Alle 3.0-Vorschläge nutzen den Geist.
- **A1 Orbit im Telefonbuch des Handys** (Schalter in Einstellungen › Kontakte, einmalig als Geist angeboten): Orbit schreibt alle sichtbaren Menschen und Organisationen (nie Archiv, Papierkorb, versteckte Mitarbeiter) als eigenes Konto „Orbit“ ins Android-Telefonbuch: Name mit Titel, Rufname, Nummern mit Bezeichnung, Mails, Adressen, Firma/Rolle, Geburtstag, Foto, Favorit = markiert. Dadurch Namen und Fotos in WhatsApp, Signal, Messages, Gmail, Systemsuche und Google-Telefon. Orbit bleibt die Wahrheit: Änderungen fließen nur Orbit → Telefonbuch; was andere Apps neu anlegen, erscheint als Geist „Neu im Telefonbuch“ (Übernehmen = Import mit Duplikat-Hinweis). Ausschalten entfernt das Konto samt seinen Einträgen; Einträge anderer Konten verändert Orbit nie. *Moment:* Statuszeile zählt hoch „214 im Telefonbuch“. *Technik:* AccountAuthenticator + leerer SyncAdapter (`com.android.contacts`, `contacts.xml`), `Settings.UNGROUPED_VISIBLE=1`, RawContacts mit `SOURCE_ID` = Kontakt-ID, Differenz-Abgleich in Batches mit `CALLER_IS_SYNCADAPTER`, entprellt nach jedem Speichern und beim Start.
- **A2 „Kontakt hinzufügen“ landet in Orbit:** Jede App, die einen Kontakt anlegen oder ergänzen will (Telefon, Messages, WhatsApp, Gmail, Kamera beim vCard-QR), kann Orbits Bearbeiten-Seite öffnen, vorausgefüllt; „Zu bestehendem“ wählt zuerst den Kontakt. vCard-Dateien öffnen Orbits Import (damit ist der QR-Kartentausch aus 1.5 erledigt: eigene Ich-Karte zeigen, fremde mit der Kamera scannen). *Moment:* übergebene Felder leuchten beim Öffnen kurz teal. *Technik:* `ACTION_INSERT`/`ACTION_INSERT_OR_EDIT` (contact, raw_contact), `ContactsContract.Intents.Insert`-Extras, `ACTION_VIEW` für `text/vcard`/`text/x-vcard`.
- **A3 Orbit-Zeilen in der System-Kontaktkarte:** „In Orbit öffnen“, wichtigste eigene Felder („Kundennummer K-12345“), bei Organisationen mit Makro „Makro wählen“. *Technik:* eigene MIME-Typen in `contacts.xml`, Activity mit passendem Intent-Filter.
- **A4 Markieren → Orbit:** im Textauswahl-Menü jeder App „Orbit“: markierte Nummer/Mail/Adresse/Webadresse → Sheet mit „gehört zu …“ (Treffer, ohne Archiv/Mitarbeiter-Namen) · „Neuer Kontakt“ · „Zu … hinzufügen“; Text ohne Muster → „Notiz zu …“. *Technik:* `ACTION_PROCESS_TEXT`, feste Muster.
- **A5 Personen im Teilen-Menü:** die meistgenutzten Menschen direkt im System-Teilen; Foto/PDF landet in ihrem Ordner, Link als Link, Text als Notiz – ohne Zwischenschritt. *Technik:* Sharing-Shortcuts (share-target, long-lived, Person), Teilen-Eingang mit vorgewählter Person.
- **A6 Ordner pro Person in jedem Datei-Dialog:** „Orbit“ im Dateiauswahl-Dialog (Gmail-Anhang, Uploads): Menschen/Organisationen mit Dateien als Ordner, darin Fotos und PDFs. *Technik:* `DocumentsProvider` nur lesend, ohne Archiv/Papierkorb/versteckte Mitarbeiter.
- **A7 WhatsApp und Signal mit einem Tipp:** Chat, Anruf, Video – auf der Kontaktseite (Halten auf WhatsApp zeigt Chat/Anruf/Video), im Favoriten-Widget und im Rückruf-Stapel; nur wenn die App die Nummer kennt (W5). Nichts wird mitgelesen. *Technik:* Datenzeilen, die WhatsApp/Signal selbst im Telefonbuch anlegen (`vnd.com.whatsapp.profile`, `…voip.call`, `…video.call`; Signal `vnd.org.thoughtcrime.securesms.contact`/`.call`), `ACTION_VIEW` auf die Datenzeile.
- **A8 Klingel-Signatur und Durchklingeln:** Klingelton pro Person oder Gruppe (Person vor Gruppe); Schalter „Favoriten klingeln durch“: markierte Kontakte und Wiederholungsanrufe kommen bei „Nicht stören“ durch. Vibrationsmuster pro Person ist nicht möglich (System vibriert). *Technik:* `CUSTOM_RINGTONE`/`STARRED` an Orbits Telefonbuch-Einträgen (braucht A1), Klingelton-Auswahl des Systems, `NotificationManager.setNotificationPolicy` (Zugriff „Nicht stören“).
- **A9 Nummern-Schublade:** Schnelleinstellungs-Kachel „Nummern“ – über jeder App gleitet eine Schublade mit deinen Nummern hoch (eigene Felder der ICH-Karte: SV-Nr., Polizzen, Matrikel, IBAN, Kennzeichen, Zählernummer; dazu Kundennummern deiner Dienste). Tipp kopiert (sensibel), Schublade schließt mit Häkchen; Halten = Diktier-Ansicht (B7).
- **A10 Titel, Anrede, Rufname** als Felder (Mag.a, Dr.in, DI, Ing.; Sie/Du; „Hansi“) – Anzeige, Suche, Telefonbuch, Anrufbildschirm und Textbausteine nutzen sie.
- **A11 Kölner Phonetik + Rufnamen in Suche und Tastenfeld:** „Maier“ findet Mayr/Meier/Mayer, „Höller“ Hoeller; exakte Treffer vorne, phonetische dahinter. *Technik:* `core/domain` Kölner Phonetik, im Index vorberechnet.
- **A12 Weitergeben mit Feldwahl:** Teilen auf der Kontaktseite → Felder antippen → als Text, vCard oder QR. *Moment:* gewählte Felder fliegen in eine kleine Karte, die zum Teilen wird.
- **A13 Orbit-Kalender:** eigener Kalender „Orbit“ im Handy-Kalender mit Anlässen und Fristen (Schalter; Ausschalten löscht ihn). *Technik:* Kalender unter dem Orbit-Konto, Differenz-Abgleich.

### 9.4 3.0-B – Anrufe mit Autopilot

- **B1 Anrufer materialisiert:** unbekannte Nummer → sofort offline Ort und Art („Linz · Festnetz“, „Mobil · Deutschland“, „0800 · gratis“, „0900 · teuer“), parallel Herold-Abfrage (Cache, 1,5 s Zeitlimit, sonst nachgereicht): Name, Branche, Adresse. Nach dem Auflegen Geist „Als Organisation anlegen“ mit allen Daten. Gilt im Anrufbildschirm, in der Anrufer-Info und im Verlauf bei unbekannten Nummern. *Moment:* Visitenkarte in Endform mit Scanlicht, der Treffer schärft sich zu Name/Branche, Haptik Materialisieren. *Technik:* libphonenumber + Geocoder offline; Herold-Webseite mit festen Mustern, höchstens eine Abfrage je Nummer und Woche, abschaltbar.
- **B2 Nummernkunde & Kostenwächter:** Mehrwert-/Shared-Cost-Nummern und mögliche Ping-Anrufe (unbekannt, Ausland, verpasst) sind orange markiert; Wählen verlangt einen zweiten Tipp. Durchwahl einer Organisation mit Nummernregel zeigt „Zentrale · Durchwahl 1234“ (Mitarbeiter-Geist).
- **B3 Rückruf-Stapel:** verpasste Anrufe (ohne späteren Kontakt mit der Nummer), „Später zurückrufen“ und Mailbox-SMS als Kartenstapel – aus Heute, Verlauf-Tab und Benachrichtigung „3 Rückrufe“. Rechts wischen = anrufen, links = erledigt (Rückgängig). Endet ein Kalendertermin (Status beschäftigt) und der Stapel ist nicht leer, erscheint die Benachrichtigung. *Moment:* Karte nach rechts dehnt sich zum Anrufbildschirm; nach dem Auflegen schrumpft sie zurück, die Notizzeile gleitet heraus, die Karte fliegt als Beleg in den Verlauf, die nächste rückt nach.
- **B4 Hotline-Makros:** einmal selbst durchwählen = aufgenommen: bei Anrufen an Organisationen merkt sich Orbit getippte Tasten und Pausen; danach Geist „Als Makro speichern“ („1 · 4 s · 3 · Kundennummer · #“ – Ziffern, die einem eigenen Feld entsprechen, werden zum Baustein). Bearbeiten als Chips (Taste, Pause ziehen, „Warten auf Weiter“, Feld). *Moment:* das Tastenfeld spielt sich selbst – Lichtspur über den Tasten, Segmentleiste für Pausen, die Ziffern der Kundennummer springen als Funken in die Tasten, bei „Warten“ pulsiert „Weiter“; jede Berührung übernimmt. *Technik:* als Telefon-App `playDtmfTone`/`stopDtmfTone` mit eigener Zeitsteuerung (Ton ≥ 200 ms); ohne Telefon-App Wählstring mit „,“ (Pause) und „;“ (Warten). Der DTMF-Ton im Tastenfeld darf nicht zu früh enden.
- **B5 Dranbleiben:** „Ordination Huber · um 7:30 anrufen“ (aus Meine Dienste, Telefonzeit oder Halten auf Anrufen). Zur Zeit Vollbild-Karte wie ein Wecker; bei besetzt oder Bandansage (Gegenseite legt binnen 20 s auf) wählt Orbit nach 15 s neu (max. 30 Versuche, jederzeit abbrechbar); frei → Vibration, Anrufbildschirm vorne. *Moment:* Live-Chip „Versuch 7 · 07:31“, Ring zählt die 15 s herunter. *Technik:* Wahlwiederholung nur als Telefon-App (DisconnectCause, Dauer); exakter Wecker; ohne Telefon-App nur die Vollbild-Erinnerung mit Anrufen.
- **B6 Warteschleife:** im Gespräch ein Tipp: Lautsprecher an, Bildschirm dunkel (Wartezeit groß), Live-Update „Warteschleife · 12:40“; ans Ohr heben schaltet zurück. Nur als Telefon-App.
- **B7 Im Gespräch:** **Diktier-Ansicht** (Kundennummer, IBAN, Polizze riesig in Vierergruppen, Buchstaben nach österreichischer Buchstabiertafel; auch per Halten auf jedes eigene Feld) · **Tastenfeld schreibt mit** (im Gespräch getippte Ziffernfolgen ab 4 Stellen landen als Chip „Vorgang 123456“ in der Notiz und an der Organisation) · **Familie im Blick** (Partner, Kinder mit Alter, naher Geburtstag) · **Anrede** („Sie · Frau Mag.a Huber“). Im Anrufbildschirm; ohne Telefon-App über die Aktion „Diktieren“ der Anrufer-Info.
- **B8 Abwehr ohne Verlust:** Schalter „Anruf-Filter“: feste Regeln, nie abweisen, nur stumm – „Unbekannt nachts (22–7)“, „Nummernserie“, „Land“, „Mehrwert“. Stummgeschaltete Anrufe stehen im Verlauf als dünne Coral-Spur mit Regel-Stempel. *Technik:* `CallScreeningService` mit `setSilenceCall`, `setDisallowCall(false)` immer; Rolle Anruf-Filter (ersetzt Googles Spam-Schutz).
- **B9 Dual-SIM-Regeln:** Gruppe → SIM (Person vor Gruppe); Farbrand der klingelnden SIM; Rückruf über die SIM, auf der der Anruf kam. Nur mit zwei SIMs sichtbar.
- **B10 Wählregeln:** pro Kontakt „Nummer unterdrücken“ (#31#); im Ausland nationale Nummern automatisch mit +43.
- **B11 Anruf-Poster:** pro Person Vollbild-Foto mit Name in eigener Farbe im Anrufbildschirm (Standard = Profilbild). Nur als Telefon-App.
- **B12 Bewegungsgesten (einschaltbar):** Kontakt offen + ans Ohr = anrufen; Klingeln + Umdrehen = stumm; im Gespräch vom Ohr auf den Tisch = Lautsprecher. Nur im Vordergrund bzw. während eines Anrufs, nie im Energiesparmodus.

### 9.5 3.0-C – Heute & Live

- **C1 Heute:** Kartenreihe oben in der Kontaktliste, identisch als Widget (Startbildschirm; Sperrbildschirm, wo Android es erlaubt): Anlässe heute, Treffen heute, Rückrufe, Dranbleiben, Fristen (≤ 14 Tage), Themen mit Datum, „Heute vor N Jahren“ (höchstens eine Karte), Geister („Neu im Telefonbuch“, Duplikate). Leer = unsichtbar. *Moment:* Karten gleiten gestaffelt ein (40 ms Versatz); eine erledigte Karte zerfällt in Sternenstaub, die Reihe schließt federnd.
- **C2 Live-Insel und Live-Updates:** für laufenden Anruf (ersetzt den Anruf-Chip), Makro, Dranbleiben, Treffen ab Beginn, geplante Nachricht ab 10 Min vorher. Tippen öffnet die Akte. *Moment:* die Insel wächst aus dem Punkt oben (250 ms), Inhalte wechseln per Morph. *Technik:* eine Live-Quelle mit Vorrang (9.2); `ProgressStyle` + `setRequestPromotedOngoing` ab API 36.
- **C3 Spät dran ziehen:** läuft ein Treffen oder steht es in ≤ 30 Min an, in der Insel die Minutenzahl nach unten ziehen – rastet bei +5/+10/+15 (Haptik je Raste), Loslassen schickt „Bin ca. 10 Min später da“ per SMS bzw. bereitet WhatsApp vor; Rückgängig-Ring 5 s.
- **C4 Eine Karte, drei Größen:** Bauteile Chip/Karte/Akten-Kopf mit gleicher Anatomie und geteilten Übergängen.
- **C5 Leiste ohne Netz:** Leiste = Kontakte · Verlauf · Anlässe · Karte. Netz öffnet sich aus der Kontaktseite (Beziehungen → Netz, Person in der Mitte) und aus der ICH-Karte.

### 9.6 3.0-D – Akten & Meine Dienste

- **D1 Meine Dienste:** in Kontakte unter Heute eine Kachelreihe aus deinen Zuordnungen (ICH → Hausarzt, Zahnarzt, Apotheke, Hausverwaltung, Werkstatt, Versicherung, Bank …), auch bei ausgeblendeten Organisationen: Logo/Planet, Rolle, Live-Status („offen bis 12“, „Telefon ab 7:30“, „Urlaub bis 15.8. → Dr. Mayr“), Anruf-Knopf; Halten: Dranbleiben, Makro, Ausweis.
- **D2 Telefonzeiten, Feiertage, Urlaub:** Telefonzeiten getrennt von Öffnungszeiten (gleiche Schreibweise); österreichische Feiertage (offline berechnet) und Schulferien OÖ (OpenHolidays, einmal jährlich, Cache) zählen mit („Feiertag · zu“); „Urlaub bis …“ mit Vertretung überlagert beides.
- **D3 Verträge & Vertrags-Bahn:** Verträge an Organisationen (Bezeichnung, Nummer, Beginn, Mindestlaufzeit, Verlängerung, Kündigungsfrist, Preis, Dokumente); die Frist „Kündigen bis …“ läuft als Anlass mit Vorlauf (Heute, Benachrichtigung, Orbit-Kalender). *Moment:* Laufzeit als Ring, Kündigungsfenster orange, in der Mitte die Tage bis zur Frist („−77“); den Planeten aus dem Ring ziehen öffnet die Kündigungsmail aus dem Baustein.
- **D4 Ausweis:** Halten auf eine Kunden-/Mitgliedsnummer → Vollbild mit voller Helligkeit, Nummer riesig, Barcode (Code 128) oder QR, Holo-Schimmer.
- **D5 Gewerk & Auftrag:** Kategorie „Handwerk“ mit Gewerk; Eintragsart „Auftrag“ mit Betrag, Sternen und Rechnungs-PDF; Suche „Installateur“ → „Huber GmbH · Therme 05/2025 · 340 € · ★★★★“.
- **D6 Verliehen & Schulden:** pro Person Dinge (verliehen/geliehen, seit) und Geld (wer schuldet wem); Zahl-QR (EPC) mit deiner IBAN für die Bank-App; erledigt per Wisch mit Beleg.
- **D7 WLAN einer Organisation:** „WLAN speichern“ übernimmt das verbundene Netz (Passwort einmal eintippen), „Verbinden“ mit einem Tipp, QR zum Weitergeben.
- **D8 Ankommen (Schalter, aus):** nur bei Orten mit Inhalt (offene Themen, Ausweis, Mitarbeiter, WLAN): Geofence 100 m oder bekanntes WLAN → die Insel erscheint, die Karte wächst heraus; beim Verlassen Beleg „Besuch 14:05–14:40“. *Technik:* Geofencing der Play-Dienste, Hintergrund-Standort erst beim Einschalten.

### 9.7 3.0-E – Erfassen, Faden, Datenqualität, Automatiken

- **E1 Orbit-Zeile:** das Suchfeld versteht feste Muster und zeigt sie als Chips: Person („Anna“), Art („Kaffee“, „Treffen“, „Anruf“, Eintragsarten), Zeit („morgen 15 Uhr“, „gestern“, „Fr 9:30“, „in 2 Wochen“), Notiz/Ort („+Wohnung“), Gruppe („#Uni“). Enter legt an (Eintrag oder geplantes Treffen). Ohne Muster bleibt es Suche, mit Operatoren `#gruppe`, `ist:org`, `hat:geburtstag`, `ort:Linz`. *Moment:* erkannte Teile lösen sich als Chips aus dem Text (Tick je Chip); Enter: die Chips fliegen in die Kontaktzeile, die kurz aufleuchtet. *Technik:* Parser rein in `core/domain`, tabellengetrieben getestet.
- **E2 Faden:** die Zeitleiste der Kontaktseite wird zum Faden – alles in einem Strom (Anrufe, SMS mit Text, Mails, Treffen, Notizen, Belege), unten eine Antwortzeile: SMS (geht direkt), Mail (Mail-App mit Empfänger und Betreff), Notiz; neue SMS/Anrufe erscheinen live. Keine SMS-App-Rolle.
- **E3 Geister aus SMS und Signaturen:** unbekannte Nummer/Mail/Adresse in einer SMS oder geöffneten Mail eines Kontakts → gestricheltes Feld in seiner Akte; Halten zeigt die Quelle, Tipp übernimmt.
- **E4 Zur Zeit senden – Mitternachts-Rad:** Senden halten → Zeit-Rad um den Daumen, magnetische Raste auf 00:00 des nächsten Anlasses, sonst 5-Min-Raster; SMS geht zur Zeit selbst; WhatsApp/Signal: zur Zeit eine Benachrichtigung mit fertigem Text, ein Tipp öffnet den Chat. *Moment:* die Nachricht fliegt in eine kleine Uhr, deren Zeiger auf die Zielzeit laufen.
- **E5 Doppelstern-Zusammenführen:** Paare nach festen Regeln (gleiche Nummer, gleiche Mail, gleicher Name + Geburtstag) tragen einen Doppelstern; Zusammenführen mit Feldwahl je Konflikt; alles wandert mit, Rückgängig möglich. *Moment:* Kneifen bzw. Karte auf Karte ziehen verschmilzt beide; Zähler „142 + 37 → 179 Einträge“.
- **E6 Textbausteine und Serien-Nachricht:** Bausteine mit Platzhaltern ({Vorname}, {Rufname}, {Anrede}, {Kundennummer}, {Vertragsnummer}, {Datum}) überall, wo Text entsteht; Serien-Nachricht aus der Mehrfachauswahl (SMS einzeln, WhatsApp nacheinander).
- **E7 Geschenke-Board:** Idee → gekauft → verschenkt, mit Preis und Budget je Person/Anlass; Ziehen zwischen den Spalten; „verschenkt“ schreibt den Geschenk-Eintrag.
- **E8 Themen-Stapel und „Besprochen“:** alle offenen Themen als Stapel (rechts = anrufen/schreiben, links = erledigt); im Anruf oder Treffen abgehakte Themen werden zum Verlaufs-Text „Besprochen: …“.
- **E9 Kalender-Brücke:** Termine mit einem Kontakt als Teilnehmer (Mail) oder mit seinem Namen im Titel erscheinen danach als Geist „Treffen mit Anna“; Tipp = Treffen, Fotos aus dem Zeitfenster als Vorschlag.
- **E10 Gruppenfoto mit Namens-Pins und „Neue Gesichter“:** Gesichter antippen (keine Erkennung), Kontakt wählen → Profilbild-Zuschnitt; Lernkarten der zuletzt angelegten Menschen (Foto → Name).
- **E11 Dokument-Scan ohne Texterkennung:** Kamera → vier Ecken ziehen → entzerrtes PDF in den Ordner.
- **E12 Sprachnotiz:** Halten auf „+ Notiz“ nimmt auf, hängt am Eintrag, Wellenform beim Abspielen.
- **E13 @-Erwähnungen** in Notizen mit Rückverweis auf der anderen Seite.
- **E14 Tote Daten:** fehlgeschlagene SMS und unzustellbare Mails markieren Nummer/Adresse orange.
- **E15 Smart-Gruppen:** gespeicherte Suchen der Orbit-Zeile als Chips neben den Gruppen.
- **E16 Echte Bilder:** Gravatar/Webseiten-Symbol als Geist, nur ab 192 px.
- **E17 Automatiken:** Einstellungen › Automatiken: alle eingebauten und eigenen Automatiken mit Schalter, Zähler und Stempel am Beleg. Vorlagen: „Gruppe → SIM“, „Gruppe → Klingelton“, „Nummernserie → Organisation“, „Unbekannt nachts → stumm“, „Verpasster Anruf von <Gruppe> während Termin → SMS mit Rückrufzeit“, „Termin endet → Rückruf-Stapel“.
- **E18 Person-Widget:** Foto, Anrufen/WhatsApp, nächster Anlass, zuletzt.
- **E19 Kneifen als zweite Achse:** Verlauf/Faden: Zeit-Zoom Tag → Woche → Monat → Jahr („KW 41 · 5 Anrufe · 2 Treffen“); Kontaktliste: Dichte.
- **E20 Ziehen & Ablegen:** Kontakt auf Gruppen-Chip = zuordnen; Foto/PDF aus einer anderen App auf eine Kontaktzeile = ablegen.
- **E21 Predictive Back** überall; die Kontaktseite schrumpft zur Zeile.
- **E22 Handbuch:** Einstellungen › Über Orbit › Handbuch (Gesten, Regeln, Automatiken) statt Erklärtexten; Quelle `docs/HANDBUCH.md`.
- **E23 Änderungsprotokoll 30 Tage:** Feld alt → neu je Kontakt, einzeln zurücknehmbar.
- **E24 Event-Modus (leicht):** „Event Hochzeit Tom“ in der Orbit-Zeile startet für den Tag eine Schnellerfassung (Foto + Name + ein Satz, „kennengelernt bei …“ automatisch).

### 9.8 Reihenfolge 3.0

Je Stufe ein Release nach der Definition of Done (8.4) mit Wow-Gate (1.5) inkl. der Momente aus 9.2–9.7. Innerhalb einer Stufe nach Alltagsnutzen:

| Stufe | Inhalt (Reihenfolge) |
|---|---|
| 3.0-A Telefonbuch | A0, A10, A11, A1, A8, A7, A2, A4, A9, A12, A5, A3, A6, A13 |
| 3.0-B Anruf-Autopilot | B1, B2, B3, B4, B7, B8, B5, B6, B9, B10, B11, B12 |
| 3.0-C Heute & Live | C1, C2, C3, C4, C5 |
| 3.0-D Akten & Meine Dienste | D1, D2, D4, D3, D5, D6, D7, D8 |
| 3.0-E Erfassen, Faden, Datenqualität, Automatiken | E1, E2, E4, E6, E8, E9, E5, E7, E3, E17, E10–E16, E18–E24 |

### 9.9 Entscheidungen 3.0 (15.10.2026, ohne Rückfrage getroffen – Jakob kann jede ändern)

- Herold per Webseite: ja, abschaltbar, mit Cache. tellows: nein (nur Partner-Schnittstelle).
- Visitenkarten-Texterkennung: nein (wäre KI) → Gruppenfoto-Pins, Dokument-Scan ohne Texterkennung.
- WhatsApp-Mitlesen bleibt draußen. SMS-App-Rolle: nicht geplant (RCS für Apps unzugänglich; Senden geht ohne Rolle).
- Anruf-Filter-Rolle ersetzt Googles Spam-Schutz → nur auf Jakobs Schalter.
- Themen im Treffen abhaken = Teil von E8. Event-Modus nur leicht und zuletzt (E24).
- Gestrichen aus 1.5/2.0: Universum als Start, drei Räume, Laptop/Tablet/Uhr/Sync, Ereignis-Protokoll als Fundament, Jahresrückblick, Nachtuhr, Radialmenü, Karte mit Zeitregler.

### 9.10 Nicht möglich

Gesprächsaufnahme; Kundennummer automatisch „wenn die Hotline fragt“ (Apps hören den Gesprächston nicht); RCS-/WhatsApp-Inhalte lesen oder senden; Vibrationsmuster pro Person.
