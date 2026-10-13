# ADR-0001: Moderate Modularisierung

Status: angenommen (13.10.2026)

Kontext: Ein großes App-Modul vermischt UI, Logik und Plattform; Logik ist schwer isoliert testbar. Volle Feature-Modularisierung (Now-in-Android-Stil) bedeutet viel Gradle-Boilerplate und mehr CI-Runden ohne lokales Bauen.

Entscheidung: `:core:model` und `:core:domain` als reine Kotlin/JVM-Module (schnelle Tests, Android-Abhängigkeiten unmöglich), `:core:designsystem` als Android-Library, alles Übrige in `:app` mit Feature-Paketen.

Folgen: Logik ist ohne Android testbar; Designsystem wiederverwendbar; spätere Feature-Module bleiben möglich.
