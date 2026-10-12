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
