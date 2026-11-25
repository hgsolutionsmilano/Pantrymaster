# PantryMaster

Applicazione Android nativa **offline-first** per la gestione domestica di dispensa, frigorifero, freezer, lotti con metodo FEFO, scadenze, lista della spesa, schede Info seconda casa (numeri utili, misure, password/codici e foto) e statistiche.

## Stack Tecnologico

- **Linguaggio & UI**: Kotlin, Jetpack Compose, Material Design 3 adattivo (smartphone e tablet)
- **Database**: Room (`12` entità relazionali con migrazioni `1→2→3`) + DataStore Preferences
- **Architettura**: MVVM + Repository Pattern + Hilt Dependency Injection
- **Scanner & Rete**: CameraX + ML Kit Barcode Scanning & Text Recognition (OCR offline) + Retrofit/Moshi (Open Food Facts & GitHub Releases)
- **Background**: WorkManager per promemoria scadenze (7/3/1/0 giorni) e controllo aggiornamenti

## Firma Release Permanente e Compilazione Locale (Windows)

Le chiavi private e le password di firma **non vengono mai salvate nel repository né su GitHub**.
Tutte le operazioni di generazione chiave, backup cifrato AES-256, compilazione Release firmata (`APK`/`AAB`) e verifica crittografica (`apksigner`, `aapt2`) sono gestite localmente su Windows tramite:

```powershell
powershell -ExecutionPolicy Bypass -File .\PantryMaster-Release-Manager.ps1
```

- Keystore permanente: `%LOCALAPPDATA%\PantryMaster\ReleaseSigning\my-upload-key.jks` (RSA 4096 bit, alias `upload`, validità 10.000 giorni).
- Gradle (`app/build.gradle.kts`) valida keystore, alias e password tramite `verifyReleaseSigning` prima di ogni build Release e non effettua mai fallback sulla chiave Debug.
- Per la guida completa su inizializzazione, backup su doppio supporto, ripristino e strategia di migrazione dalla `1.0.15`, consulta **[docs/SIGNING_AND_RELEASES.md](docs/SIGNING_AND_RELEASES.md)**.

## GitHub Actions (Solo Controlli Codice e Test)

- `.github/workflows/android-ci.yml`: verifica assenza di segreti nel repo, controllo dipendenze, `assembleDebug`, compilazione Kotlin Release (`compileReleaseKotlin`) e test unitari/Robolectric (`testDebugUnitTest`).
- `.github/workflows/release.yml`: controlli pre-release CI senza chiavi private né pubblicazione di APK firmati con chiavi temporanee o Debug.

## Modulo di Auto-Aggiornamento In-App

PantryMaster verifica le nuove release su GitHub (`hgsolutionsmilano/Pantrymaster`), scarica l'APK in background e controlla `applicationId`, `versionCode` e firma del certificato prima dell'installazione via `PackageInstaller`. In caso di certificato incompatibile (`SignatureMismatch`), guida l'utente al backup completo `.zip` e al salvataggio dell'APK già scaricato.
