# PantryMaster

Applicazione Android nativa **offline-first** per la gestione domestica di dispensa, frigorifero, freezer, lotti con metodo FEFO, scadenze, lista della spesa, schede Info seconda casa (numeri utili, misure, password/codici e foto) e statistiche.

## Stack Tecnologico

- **Linguaggio & UI**: Kotlin, Jetpack Compose, Material Design 3 adattivo (smartphone e tablet)
- **Database**: Room (`12` entità relazionali con migrazioni esplicite `1→2→3`) + DataStore Preferences
- **Architettura**: MVVM + Repository Pattern + Hilt Dependency Injection
- **Scanner & Rete**: CameraX + ML Kit Barcode Scanning & Text Recognition (OCR offline) + Retrofit/Moshi (Open Food Facts & GitHub Releases)
- **Background**: WorkManager per promemoria scadenze (7/3/1/0 giorni) e controllo aggiornamenti

## Versionamento Progressivo e Firma Release

Le versioni base dell'app sono gestite tramite [`version.properties`](version.properties):

```properties
VERSION_MAJOR=1
VERSION_MINOR=0
VERSION_PATCH=0
VERSION_CODE=1
GITHUB_REPO=
```

Per tutta la procedura di conservazione del keystore, configurazione dei Secrets (`RELEASE_KEYSTORE_BASE64`, `STORE_PASSWORD`, `KEY_PASSWORD`, `DEBUG_KEYSTORE_BASE64`), verifica `apksigner`/`aapt2` e rilascio continuo consulta **[docs/SIGNING_AND_RELEASES.md](docs/SIGNING_AND_RELEASES.md)**.

### Workflow GitHub Actions

1. **Release Progressiva (`.github/workflows/release.yml`)**
   - Eseguibile manualmente da **Actions → Release Progressiva** (`patch`, `minor`, `major`), oppure tramite push di un tag `vX.Y.Z`.
   - Richiede obbligatoriamente i Secrets Release (nessun fallback Debug), verifica dipendenze, compila Debug, esegue i test, compila e firma Release APK + AAB, verifica crittograficamente firma (`apksigner`) e manifest (`aapt2`), verifica la continuità del certificato rispetto alla release precedente, genera checksum `.sha256` e solo dopo aggiorna tag/release.

2. **Android CI & Release (`.github/workflows/android-ci.yml`)**
   - Su Pull Request e branch `develop`: verifica dipendenze, compilazione Debug e test unitari/Robolectric.
   - Su `main`/`master` o avvio manuale: esegue tutte le verifiche CI + compilazione Release firmata, controllo `apksigner` / `aapt2` / continuità firma, checksum `.sha256` e pubblicazione GitHub Release.

## Modulo di Auto-Aggiornamento In-App

PantryMaster scarica e installa gli aggiornamenti da **GitHub Releases** (`repos/{owner}/{repo}/releases/latest`) direttamente in-app tramite `PackageInstaller`:

- Scaricamento in background dietro al popup di aggiornamento, controllo preventivo di `applicationId`, `versionCode` (no downgrade) e firma del certificato prima di proporre l'installazione.
- In caso di certificato incompatibile (`SignatureMismatch`), l'app conserva l'APK scaricato, mostra entrambi gli SHA-256 (Debug/Release) e guida al backup completo `.zip` + salvataggio APK in Download senza perdita dati.
- In **Impostazioni → Aggiornamenti e Versione** sono sempre visibili tipo di build (`Debug`/`Release`) e impronta `SHA-256` copiabile.
