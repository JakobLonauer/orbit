# Orbit

Persönliches Beziehungs-CRM für Android 14–16. Eigene Kontaktliste, getrennt vom Handy-Adressbuch, mit Verlauf, Erinnerungen, Beziehungsnetz, Karte und E-Mails.

## Installieren

1. Am Handy unter **Releases** die neueste `Orbit-1.0.x.apk` öffnen und herunterladen.
2. Die Datei antippen. Android fragt einmalig, ob der Browser bzw. die Dateien-App Apps installieren darf: erlauben.
3. Updates kommen danach direkt in der App (Einstellungen → Nach Updates suchen).

Falls Android den Zugriff auf SMS oder Anrufliste sperrt: App-Info → ⋮ → **Eingeschränkte Einstellungen zulassen**.

## Funktionen

- Kontakte mit mehreren Nummern und Mails, Adresse, Geburtstag, Jahrestagen, Firma, „Wie kennengelernt“, Notizen und Foto
- Gruppen mit Melde-Rhythmus (kürzester zählt), pro Kontakt überschreibbar, Pausieren und Archiv
- Verlauf: Anrufe (ab Mindestdauer) und SMS automatisch, dazu eigene Einträge und frei definierbare Eintragsarten
- „Nächstes Mal ansprechen“ als Benachrichtigung während des Anrufs, nach dem Anruf eine Notiz direkt in der Benachrichtigung
- Tägliche Sammel-Benachrichtigung (fällig, Geburtstage, Jahrestage) und Nachfrage zu geplanten Treffen
- **Menschen und Organisationen**: Firmen, Praxen, Ämter … als eigene Art (eckiges Avatar, Gebäude-Symbol). Sie sind überall ausgeblendet, bis du sie im Filter einblendest
- **ICH** als fester Eintrag (umbenennbar), oben in der Liste und Mitte des Netzes
- Filter, Gruppen und Sortierung (Name, zuletzt Kontakt, Fälligkeit, neu, Häufigkeit) hinter einem Button, angepinnte Gruppen als Schnellfilter
- Orbit als Liste: überfällig, nächste 7 Tage, später, ohne Rhythmus, dazu Geburtstage
- **Netz**: Beziehungen als Graph um eine frei wählbare Mitte, Tiefe 1–4
- **Karte**: alle Adressen auf einer dunklen Karte (MapLibre + OpenFreeMap, OpenStreetMap-Daten), Entfernungen, Route in Google Maps
- **E-Mails**: beliebig viele IMAP-Postfächer (Gmail, GMX, web.de, iCloud …); im Kontakt die letzten Mails von allen Adressen der Person
- **Fotos**: eigener Bilder-Ordner pro Kontakt, getrennt vom Profilbild
- Homescreen-Widget, Vorschläge für unbekannte Nummern
- Sperre per Biometrie, wöchentliches ZIP-Backup in einen Ordner deiner Wahl, Update per Tipp

## Technik

Kotlin 2.4 · Jetpack Compose (BOM 2026.09) · Material 3 Expressive 1.5 · Navigation 3 · Glance · WorkManager · kotlinx.serialization · MapLibre Native · Jakarta Mail (Android).

Wie die Funktionen geplant wurden (Interpretation, Abgleich mit anderen CRMs, zwei Iterationen): [docs/PLAN.md](docs/PLAN.md).
Gebaut wird automatisch per GitHub Actions bei jedem Push auf `main`; jede Version landet als Release mit signierter APK.
