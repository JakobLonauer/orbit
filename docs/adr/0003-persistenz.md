# ADR-0003: JSON-Kern plus SQLite für wachsende Daten

Status: angenommen (13.10.2026)

Kontext: Bis 1.0.7 liegt alles in einem JSON-Dokument, das bei jeder Änderung komplett geschrieben und beim Kaltstart komplett gelesen wird. Verlauf (Anrufe, SMS-Texte, Mails) wächst über Jahre auf zehntausende Einträge; als Telefon-App muss Orbit beim Klingeln sofort reagieren.

Entscheidung: Kern (Kontakte, Organisationen, Mitarbeiter, Zuordnungen, Beziehungen, Anlässe, Links, Einstellungen) bleibt JSON. Verlauf, Mail-Index und Ablage in SQLite über `android.database.sqlite` (ohne Room), versioniert per `PRAGMA user_version`, FTS4 für Volltextsuche mit `snippet()`. Zusätzlich ein kleiner Anrufer-Kurzindex als Datei. Backups exportieren alles als JSON.

Folgen: Einfügen statt Neuschreiben, schneller Kaltstart, Treffer-Sätze in der Suche; eigene, getestete Migrationen nötig.
