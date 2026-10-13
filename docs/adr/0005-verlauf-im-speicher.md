# ADR-0005: Verlauf und Ablage im Speicher am Kontakt, gespeichert als Differenz

Status: angenommen (13.10.2026)

Kontext: ADR-0003 legt Verlauf und Ablage in SQLite. Bis 1.0.7 lasen ~30 Stellen der App `contact.entries` / `contact.gallery` direkt; jede davon auf Datenbank-Abfragen umzubauen hätte R1a verdoppelt und viele neue Fehlerquellen geöffnet.

Entscheidung: `Contact` (v2, `:core:model`) behält `entries` und `gallery` als `@Transient`-Felder – nie im JSON-Kern. `Store` füllt sie beim Laden aus SQLite und schreibt beim Speichern nur die Unterschiede (neu/geändert/gelöscht pro Kontakt, Kontakt weg ⇒ seine Zeilen weg) in einer Transaktion. Datenbank zuerst, dann JSON atomar. Der Kontakt-Speichern-Weg der Bearbeiten-Maske übernimmt immer den aktuellen Verlauf (nie eine ältere Kopie).

Folgen: Die App arbeitet weiter mit ganzen Kontakten; Speichern schreibt den Verlauf nicht mehr komplett neu. Kaltstart lädt weiterhin den ganzen Verlauf in den Speicher – für den Anrufbildschirm gibt es deshalb den kleinen Anrufer-Kurzindex. Wenn der Verlauf sehr groß wird (Mail-Index in R5), werden Listen auf seitenweise Abfragen (`HistoryDb.forContact`, `search`) umgestellt; dafür ist die Datenbank schon vorbereitet.
