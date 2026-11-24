# PantryMaster

Applicazione Android nativa **offline-first** per la gestione domestica di dispensa, frigorifero, freezer, lotti con metodo FEFO, scadenze, lista della spesa, ricette e statistiche.

## Stack Tecnologico

- **Linguaggio & UI**: Kotlin, Jetpack Compose, Material Design 3 adattivo (smartphone e tablet)
- **Database**: Room (`12` entità relazionali) + DataStore Preferences
- **Architettura**: MVVM + Repository Pattern + Hilt Dependency Injection
- **Scanner & Rete**: CameraX + ML Kit Barcode Scanning + Retrofit/Moshi (Open Food Facts & GitHub Releases)
- **Background**: WorkManager per promemoria scadenze (7/3/1/0 giorni) e controllo aggiornamenti

## Versionamento Progressivo e GitHub Actions

Le versioni dell'app sono gestite in modo centralizzato tramite [`version.properties`](version.properties):

```properties
VERSION_MAJOR=1
VERSION_MINOR=0
VERSION_PATCH=0
VERSION_CODE=1
GITHUB_REPO=
```

### Workflow configurati

1. **Android CI (`.github/workflows/android-ci.yml`)**
   - Eseguito su push (`main`, `master`, `develop`), Pull Request e avvio manuale.
   - Calcola automaticamente una versione progressiva per ogni build (`versionCode = BASE_CODE + github.run_number`).
   - Esegue i test unitari/Robolectric (`gradle :app:testDebugUnitTest`), compila l'APK e pubblica artefatti e report.

2. **Release Progressiva (`.github/workflows/release.yml`)**
   - Eseguibile manualmente da **Actions → Release Progressiva** scegliendo l'incremento (`patch`, `minor`, `major`), oppure tramite push di un tag `vX.Y.Z`.
   - Incrementa automaticamente `version.properties`, crea il commit e il tag Git, esegue i test e pubblica una **GitHub Release** allegando l'APK (e l'AAB se è configurata la firma release).
   - **Secrets opzionali per firma Release**:
     - `RELEASE_KEYSTORE_BASE64`: file `.jks` codificato in Base64
     - `STORE_PASSWORD`: password del keystore
     - `KEY_PASSWORD`: password della chiave (`alias = upload`)

## Modulo di Auto-Aggiornamento

PantryMaster include un modulo integrato di verifica aggiornamenti basato sulle **GitHub Releases** (`repos/{owner}/{repo}/releases/latest`):

- Controllo automatico periodico in background (WorkManager con vincolo di rete) e all'avvio dell'app (configurabile).
- Confronto semantico delle versioni con gestione di release notes, dimensione dell'asset `.apk`, rinvio o esclusione della singola versione.
- Apertura diretta della release / asset `.apk` nel browser di sistema tramite `Intent.ACTION_VIEW` (senza permessi invasivi).
- Il repository GitHub viene impostato automaticamente da `GITHUB_REPOSITORY` durante la compilazione in GitHub Actions, oppure è configurabile in qualsiasi momento da **Impostazioni → Aggiornamenti e Versione**.
