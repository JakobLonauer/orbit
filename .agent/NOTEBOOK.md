# AGENT NOTEBOOK — nur für Claude. Jakob liest das nicht. Dicht, ehrlich, nützlich.
# Regel: VOR der Arbeit lesen. WÄHREND der Arbeit sofort ergänzen (nicht erst am Ende). Veraltetes löschen statt stapeln.
# Format: Stichworte. "!!" = hat schon mal Zeit gekostet. "->" = Lösung.

## 0. TL;DR für eine neue Sitzung
- Spec: docs/PLAN.md (einzig verbindlich). Stand: docs/PROGRESS.md. ADRs: docs/adr/. Dieses Notizbuch: .agent/.
- Arbeiten auf `dev`. Release = merge dev→main (nur nach DoD, PLAN 8.4).
- Lokal KEIN Gradle-Build möglich (Google Maven + Maven Central über Proxy 403). Jede Kompilierprüfung = CI-Runde (~3–5 min). -> große, sorgfältige Batches; vor Push selbst gegenlesen (Imports, Scopes, Signaturen).
- K2 meldet ALLE Frontend-Fehler eines Moduls in einer Runde -> nach Fehlschlag errors.txt komplett abarbeiten, dann erst neu pushen.
- Module: `:core:model` (Datenklassen v2 + eingefrorenes v1 in `core.model.v1`), `:core:domain` (pure Logik: DateMath, Matcher, AtFormat, BackupCrypto, migration/MigrateV1), `:core:designsystem` (Motion, Haptics, Glass/Haze, Holo/AGSL), `:app`. Pure Logik IMMER nach core:domain -> per JUnit im CI testbar.
- CI-Kette: `:core:model:test :core:domain:test :app:recordRoborazziDebug assembleRelease` mit `--continue` -> eine Runde zeigt alle Fehler. Testergebnisse: `ci-log-dev:tests/*.xml` + `tests/failed.txt`, Screenshots: `ci-log-dev:shots/*.png` (mit Read ansehen!).
- Jakob: Deutsch, knapp, mechanistisch, keine Romane. Nachts keine Rückfragen. Keine KI-Features, keine Fälligkeit, keine Barrierefreiheit, Wow extrem wichtig.

## 1. Umgebung / Runbook
- Repo: JakobLonauer/orbit (Default main). Push via git funktioniert (GitHub-App + Proxy). `gh` REST ok, **GraphQL 403** -> `gh release list` geht NICHT; nimm `gh api repos/JakobLonauer/orbit/releases`.
- Andere GitHub-Repos per gh api: 403 ("GitHub access ... not enabled") -> Quellcode fremder Repos über WebFetch auf raw.githubusercontent.com lesen (ging für maplibre-native).
- CI-Status: `gh run list -R JakobLonauer/orbit -L 3`; warten: `RUN=$(gh run list -R JakobLonauer/orbit -L 1 --json databaseId -q '.[0].databaseId'); timeout 580 gh run watch $RUN -R JakobLonauer/orbit --interval 20 >/dev/null; gh run list -R JakobLonauer/orbit -L 1`. Nach Push ~20 s warten, bevor der Run auftaucht. Bash-Timeout 600000 setzen.
- Job-Logs (blob.core.windows.net) blockiert -> Workflow pusht Log in Branch `ci-log` (main) bzw. `ci-log-<branch>` (dev: `ci-log-dev`). `git fetch -q origin +ci-log-dev:refs/remotes/origin/ci-log-dev && git show origin/ci-log-dev:errors.txt`. !! Einmal sah das Log veraltet aus -> immer `meta.txt` (run-Nummer, sha) prüfen.
- Release: Workflow erstellt `v1.0.<run_number>` nur auf main und nur signiert. versionCode = run_number (dev-Runs verbrauchen Nummern, egal, bleibt monoton). **Tag-Format nie ändern** (Updates.kt: `tag.substringAfterLast('.')` = versionCode).
- APK holen: `ID=$(gh api repos/JakobLonauer/orbit/releases --jq '.[0].assets[0].id'); gh api -H "Accept: application/octet-stream" repos/JakobLonauer/orbit/releases/assets/$ID > Orbit.apk`.
- Signatur prüfen: `python3 tools/apk_cert_sha256.py Orbit.apk` -> muss `a549f16d6316207941f782b787d4a1b73f82729e50cb5399b45e6ddb1059d81f` sein (v2-Block; v1-Signatur gibt es nicht, keytool -printcert ist leer, normal).
- Signing: `signing/orbit.jks.enc` (openssl aes-256-cbc -pbkdf2 -iter 200000), Secret ORBIT_SIGNING_PASSWORD, Alias orbit. NIE anfassen. Secrets-API ist über den Proxy gesperrt (deshalb verschlüsselter Keystore im Repo).
- Commits ohne Build: `[skip ci]` in der Message.
- Scratchpad/Container sind flüchtig -> Werkzeuge und Wissen ins Repo (tools/, .agent/).
- Web: WebFetch ok für Doku; DNS-Lookup: `https://dns.google/resolve?name=X&type=MX`. Maven-Versionen: central.sonatype.com/artifact/... per WebFetch (curl 403).

## 2. Toolchain (Stand 1.0.7)
- AGP 9.3.1 mit eingebautem Kotlin (KEIN kotlin-android-Plugin), Gradle 9.5.0, Kotlin 2.4.20, compileSdk 37, minSdk 34, targetSdk 36, JVM 17 Target, Java 21 im CI.
- Compose BOM 2026.09.00, material3 1.5.0-beta01 (Expressive), Navigation3 1.2.0-rc01, Glance 1.3.0-alpha02, WorkManager 2.11.2, biometric 1.1.0, fragment 1.8.5, coroutines 1.11.0, serialization 1.11.0, MapLibre android-sdk 13.5.3, com.sun.mail:android-mail/android-activation 1.6.7.
- Opt-ins global in app/build.gradle.kts (Material3 Experimental/Expressive, Foundation, Animation, SharedTransition).
- R8 an (minify + shrinkResources). ProGuard-Regeln: kotlinx.serialization, Route-Klassen, Receiver/Worker, JavaMail (keep javax.mail/com.sun.mail/javax.activation/myjava.awt.datatransfer/org.apache.harmony + dontwarn java.awt/java.beans/javax.security/sun.security).
- Packaging-Excludes: META-INF/AL2.0, LGPL2.1, versions/9/OSGI-INF/MANIFEST.MF, LICENSE.md, NOTICE.md, LICENSE.txt, NOTICE.txt. mailcap/javamail.* NICHT ausschließen (werden gebraucht).
- Tests (seit Test-Build 13.10.): junit 4.13.2, Robolectric 4.17 (robolectric.properties: sdk=35, application=TestApp), Roborazzi 1.74.0 (Plugin + compose + junit-rule), compose ui-test-junit4 (+ debugImplementation ui-test-manifest), Haze 1.7.2. System-Property `robolectric.pixelCopyRenderMode=hardware` für alle Test-Tasks.
- Library-Modul mit AGP 9: `android.library` + compose-Plugin, `kotlin { compilerOptions }` im android-Block wie in app – kompiliert. JVM-Module: `kotlin.jvm`-Plugin geht neben AGP-9-builtin-Kotlin problemlos.
- ABI-Filter arm64-v8a + armeabi-v7a (MapLibre native) -> APK ~29 MB. Für Emulator-Tests bräuchte man x86_64 (nur in Test-Variante!).

## 3. Code-Landkarte 1.0.7 (vor dem Umbau)
- data/: Model.kt (OrbitData, Contact[kind PERSON/ORG, isMe, orgCategory, website, geo, gallery…], Group[pinned], Settings[listSort…]), Defaults.kt (Arten, Org-Kategorien, RelationKinds inkl. toOrg, me()), OrbitRepository (StateFlow, atomic write, **migrate() läuft bei JEDEM update – muss idempotent bleiben**), Mutations.
- domain/: Logic (displayName/sortKey/initials org-aware, rhythm/due – fliegt raus), ListQuery (Audience, StatusFilter, SortMode, sections), Network (BFS radial), PhoneMatcher (E.164, tail; isMe ausgeschlossen).
- geo/Geo.kt (Geocoder async API 33+, GeoPoint found=false, haversine, Maps-URLs). mail/ (MailAccounts+Secrets Keystore AES-GCM, noBackupFilesDir, Mutex; MailClient IMAP, MailCache in-memory + in-flight Deferred).
- ui/: Session (prozessweiter Filterzustand), list/ (ContactListScreen, FilterSheet), orbit/OrbitScreen (Fälligkeitsliste – wird Verlauf), network/, map/ (MapLibre), mail/MailAccountsScreen, detail/ (ContactDetailScreen, DetailSections, DetailDialogs[relationOptions], Gallery[GalleryStore], MailSection), edit/, settings/, components/ (Space.kt Designsystem, Common.kt Avatar/KindMark/orgIcon).
- Workflow: .github/workflows/build.yml (main+dev, Release nur main).

## 4. Gotchas (Kotlin/Compose/Android) — teuer gelernt
- !! `AnimatedVisibility` innerhalb eines Lambdas, das (auch weiter außen) in einem Column/Row liegt, löst zur `ColumnScope.AnimatedVisibility`-Extension auf -> DSL_SCOPE_VIOLATION. -> kleine Top-Level-Hilfsfunktion ohne Receiver, die AnimatedVisibility aufruft.
- !! LazyColumn: doppelte Keys = Absturz zur Laufzeit ("Key was already used"). Keys pro Abschnitt eindeutig präfixen; Header und Items nie gleich benennen.
- !! `pointerInput(Unit)` + State, der per `remember(key)` neu erzeugt wird -> Gesten schreiben in verwaiste States. -> State stabil halten (remember ohne Keys, per LaunchedEffect zurücksetzen) oder pointerInput korrekt keyen.
- Hit-Testing von Kindern außerhalb der Parent-Bounds unzuverlässig -> bei Pan/Zoom-Canvas Treffer selbst in Weltkoordinaten rechnen (inverse Transformation: world = (screen - pan - center)/scale + center).
- `rememberCoroutineScope` stirbt mit dem Composable (z. B. LazyColumn-Item scrollt raus) -> mehrstufige Persistenz in `withContext(NonCancellable)` oder App-Scope.
- Seiteneffekte nie in der Composition (z. B. onDismiss() direkt aufrufen) -> LaunchedEffect.
- Platzhalter wie `X::class` für Composables NIE schreiben (früher mehrfach Fehler).
- Icons mit Richtung: `Icons.AutoMirrored.Rounded.*` (ArrowBack, KeyboardArrowRight, Sort, CallMade, CallReceived, OpenInNew, Notes). Outlined-Varianten: `androidx.compose.material.icons.outlined.*`.
- M3 1.5 Expressive APIs, die funktionieren: MaterialExpressiveTheme, MotionScheme.expressive(), MaterialShapes.Cookie9Sided.toShape(), ListItem(onClick=…, shapes=ListItemDefaults.segmentedShapes(index,count)), FloatingActionButtonMenu + ToggleFloatingActionButton (checkedProgress, animateIcon), LinearWavyProgressIndicator(progress = { … }) bzw. ohne progress (indeterminate), LoadingIndicator(modifier, color), labelLargeEmphasized/titleMediumEmphasized usw.
- Text-Eingabe: TextFieldState überall (rememberTextFieldState, setTextAndPlaceCursorAtEnd, clearText), `BasicSecureTextField`, decorator als Lambda ok, OutlinedTextField(state=…).
- Dialoge im Dark-Space-Look: AlertDialog(containerColor = Space.PanelHigh). Sheets: ModalBottomSheet(containerColor, dragHandle).
- if-Ausdruck mit Lambda als `(@Composable () -> Unit)?`-Argument klappt (`action = if (x) { { … } } else null`).
- kotlinx Json kann NaN nicht -> GeoPoint(found=false) statt NaN.
- Geocoder: `getFromLocationName(addr, 1, GeocodeListener)` + suspendCancellableCoroutine + Timeout; onError -> nicht cachen (später erneut).
- Locale: bisher Locale.GERMAN -> für Jänner usw. auf `Locale.forLanguageTag("de-AT")` umstellen (PLAN W7).

## 5. MapLibre 13.5.3
- Pakete: org.maplibre.android.{MapLibre, maps.MapView/MapLibreMap/Style, camera.CameraPosition/CameraUpdateFactory, geometry.LatLng/LatLngBounds, style.expressions.Expression, style.layers.{CircleLayer,SymbolLayer,PropertyFactory}, style.sources.{GeoJsonSource,GeoJsonOptions}}.
- GeoJSON-Klassen (Feature…) nie importiert: Quelle per JSON-String (`GeoJsonSource(id, json, options)`, `setGeoJson(String)`), JSON mit kotlinx buildJsonObject. Klick: `queryRenderedFeatures(PointF, layerId).first().getStringProperty("id")` (Typ wird inferiert).
- `MapLibre.getInstance(ctx)` vor MapView. Lifecycle selbst: onCreate(null) beim Erzeugen; LifecycleEventObserver mit started/resumed-Flags; onDispose: onPause/onStop/onDestroy.
- Style: https://tiles.openfreemap.org/styles/dark ; Fonts „Noto Sans Regular/Bold“. Attribution an lassen (Margins über Leiste).
- LatLngBounds braucht ≥2 verschiedene Punkte; Null-Ausdehnung -> newLatLngZoom. Marker-Bitmaps nur für fehlende Bilder rendern (getImage == null).
- Tab-Wechsel zerstört MapView (AnimatedContent) -> PLAN 3.1: Karte am Leben halten (MapView auf HomeScreen-Ebene remembern oder SaveableStateHolder).

## 6. Mail (android-mail 1.6.7)
- imaps 993, ssl.enable, checkserveridentity, Timeouts 15 s/25 s. Gmail: \All-Ordner via `(folder as IMAPFolder).attributes`. Suche `OrTerm(FromStringTerm…)` serverseitig. FetchProfile ENVELOPE + UID.
- Text: text/plain bevorzugt, HTML via Html.fromHtml, Anhänge (disposition) überspringen.
- Keystore-Alias "orbit-mail-key", AES/GCM/NoPadding, iv:ct Base64. Konten in noBackupFilesDir/mail_accounts.json. Schreibzugriffe über Mutex (Parallel-Fehlerstatus!).
- PHOÖ-Mail = Microsoft 365 (MX phooe-at0e.mail.protection.outlook.com) -> Passwort-Login unmöglich; Microsoft-OAuth offen (Jakob muss App registrieren). Google-OAuth im Testmodus: Tokens 7 Tage -> unbrauchbar.

## 7. Review-Funde der Vergangenheit (Muster, auf die man achten muss)
- Doppelte LazyColumn-Keys; verwaiste Gesten-States; abgebrochener Import durch Scope-Tod; Filter, die eine fokussierte Entität ausblenden; Null-Ausdehnung bei Bounds; teure Neuberechnung bei jeder Datenänderung; alte Werte in Formularen nach Anbieterwechsel; negative Radien bei kleinen Fenstern (Division durch 0); fehlende Datei -> ungefangene Exception in Coroutine; gelöschte Gruppen bleiben im Filter.
- -> Bei jedem neuen Screen diese Liste als Selbstcheck durchgehen.

## 8. Plattform-Wissen für kommende Updates
- Telefon-App: ROLE_DIALER via RoleManager.createRequestRoleIntent; Activity für ACTION_DIAL Pflicht; InCallService mit BIND_INCALL_SERVICE, meta-data IN_CALL_SERVICE_UI=true; IN_CALL_SERVICE_RINGING weglassen/false -> System klingelt; null-Binding -> Telecom fällt auf vorinstallierten Dialer zurück (Absturz-Verhalten nicht dokumentiert -> defensiv!). Incoming UI über Notification (CallStyle) + fullScreenIntent; heads-up wenn Nutzer am Handy. Sideload: Dialer-Rolle ist „eingeschränkte Einstellung“ (Android 15). Emulator kann Anrufe simulieren (`adb emu gsm call`), CI-Emulator auf Standard-Runnern ohne KVM-Garantie.
- TEST-BUILD 13.10. GRÜN beim 1. Versuch (Run 8): Roborazzi auf ubuntu ok; Haze-Blur rendert in Robolectric WIRKLICH (hardware mode); AGSL-RuntimeShader rendert in Robolectric; SharedTransitionLayout + sharedElement kompiliert (Param-Name `animatedVisibilityScope`); InCallService-Probe kompiliert (onAvailableCallEndpointsChanged, Details.state, answer(VideoProfile.STATE_AUDIO_ONLY)); FTS4 + snippet() + `tokenize=unicode61 "remove_diacritics=2"` (Müller findet muller, Jänner/janner) in Robolectric sdk 35 ok -> auf echtem Gerät beim DB-Anlegen trotzdem try/catch + Fallback ohne unicode61.
- Screenshot-Test-Muster: `compose.onRoot().captureRoboImage("build/outputs/roborazzi/<name>.png")`, `@GraphicsMode(NATIVE)`, `@Config(qualifiers="w411dp-h891dp-xxhdpi")`, OrbitTheme drumherum. Fenster-Hintergrund ist hell (#FAFAFA), Theme malt keinen Hintergrund -> im Test selbst Hintergrund setzen.
- !! Glas-Probe: Haze zeigt `backgroundColor` deckend, wenn hinter der Fläche kein hazeSource-Inhalt liegt -> Glas nur über Inhalt sinnvoll; Surface(color=Transparent) braucht explizite contentColor (sonst schwarz auf dunkel).
- Nummernbereich-Normalisierung (Matcher.prefixKey): Teilnummer + "0000" normalisieren, "0000" wieder abschneiden -> E.164-Präfix aus „0732 2468“. In der App normalize = Phones.normalize(raw, iso).
- Haze (Milchglas) ab Android 12 RenderEffect – minSdk 34 passt.
- Nominatim: max 1 req/s, eigener User-Agent, kein Autocomplete, cachen, Attribution.
- Google Places API braucht Billing -> nicht nutzen; Google Maps nur per Deeplink/Teilen.

## 9. Fehler-Logbuch (neueste oben, Format: Datum · Symptom -> Ursache -> Fix)
- 2026-10-13 · Glas-Screenshot nur schwarzer Balken -> Leiste lag über leerem Bereich + Text schwarz -> Inhalt unter die Leiste, contentColor setzen.
- 2026-10-13 · Eigener Testfehler: Erwartung „vor 1 Monat“ für ago(60..89) unerreichbar (60/30=2) -> Tests rechnen, nicht raten.
- 2026-10-12 · CI DSL_SCOPE_VIOLATION NetworkScreen -> AnimatedVisibility griff ColumnScope -> Top-Level-Helper BottomPopup.
- 2026-10-12 · (Review) Absturz beim Aufklappen „Ohne Rhythmus“ -> doppelter Key h-none -> Header nur mit Titel.
- 2026-10-12 · Netz-Gesten tot nach Wurzelwechsel -> remember(keys) + pointerInput(Unit) -> State stabil.
- früher · Google-Server/Maven lokal gesperrt -> nur CI. Secrets-API gesperrt -> verschlüsselter Keystore im Repo. Job-Logs gesperrt -> ci-log-Branch.

## 10. Offene Fäden / Ideen-Parkplatz (nicht verbindlich)
- R1a-Verdrahtung (nächster großer Schritt): App von `data.*` auf `core.model.*` umstellen. Reihenfolge-Idee: (1) OrbitRepository lädt Text, `MigrateV1.schemaOf` -> bei 1: Datei nach `orbit.v1.bak.json` kopieren (nie überschreiben, falls schon da), migrieren, History+Attachments in SQLite (Transaktion, externalId UNIQUE -> INSERT OR IGNORE), DANN JSON v2 atomar schreiben. Absturz zwischendrin -> beim nächsten Start wieder schema 1 -> wiederholbar, weil INSERT OR IGNORE. (2) Typ-Aliase `typealias Contact = core.model.Contact` im data-Paket, um Import-Churn klein zu halten; dann Felder anpassen (address -> addresses, birthday -> dates, entries -> HistoryStore, gallery -> Attachments). (3) Fälligkeit (Logic rhythm/due, SortMode.DUE, Widget, Digest) entfernen.
- Fertig gebaut (noch NICHT beim App-Start aufgerufen): `store/HistoryDb` (SQLiteOpenHelper, entries+entries_fts(docid=rowid)+attachments+meta, JSON-Spalte = ganze Zeile, INSERT OR IGNORE über id/external_id, Suche `wort*`-Terme, snippet «…», member_id IS NULL außer Org-Suche, Referenzzählung Dateien) und `store/StoreUpgrade.run(file, db, newId)` (Backup-Kopie → DB → JSON atomar). Beim Verdrahten: in OrbitRepository.readFromDisk VOR dem Dekodieren aufrufen, mit App-weiter HistoryDb-Instanz (AppContainer).
- Kontakt v2 hat berechnete Props `address`, `birthday` (nicht serialisiert) -> erleichtert Umstellung der UI.
- Matcher.Match.hiddenFromLists: für Liste/Suche/Tastenfeld filtern; Anruferkennung nicht.
- Baseline-Profile bräuchten einen Emulator -> erst wenn CI-Emulator klappt.
- Feature-Module später möglich (ADR-0001).
