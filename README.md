# PantryMaster

Applicazione Android nativa **offline-first** per la gestione domestica di dispensa, frigorifero, freezer, lotti con metodo FEFO, scadenze, lista della spesa, schede Info seconda casa (numeri utili, misure, password/codici e foto) e statistiche.

## Stack Tecnologico

- **Linguaggio & UI**: Kotlin, Jetpack Compose, Material Design 3 adattivo (smartphone e tablet)
- **Database**: Room + DataStore Preferences
- **Architettura**: MVVM + Repository Pattern + Hilt Dependency Injection
- **Scanner & Rete**: CameraX + ML Kit Barcode Scanning & Text Recognition (OCR offline) + Retrofit/Moshi (Open Food Facts & GitHub Releases)
- **Background**: WorkManager per promemoria scadenze (7/3/1/0 giorni) e controllo aggiornamenti

## Versionamento Progressivo e Firma Coerente (GitHub Actions)

Le versioni dell'app sono gestite in modo centralizzato tramite [`version.properties`](version.properties):

```properties
VERSION_MAJOR=1
VERSION_MINOR=0
VERSION_PATCH=0
VERSION_CODE=1
GITHUB_REPO=
```

### Come evitare per sempre errori di "firma digitale diversa"

Android richiede che ogni aggiornamento abbia esattamente lo stesso certificato di firma della versione installata sul telefono. I workflow CI/Release verificano automaticamente la firma SHA-256 rispetto alla release precedente pubblicata su GitHub e bloccano eventuali downgrade da Release a Debug o cambi accidentali di keystore.

Per mantenere una firma **identica e permanente** su tutte le build GitHub Actions, imposta in **Settings → Secrets and variables → Actions**:

1. **Firma Release permanente (consigliata):**
   - `RELEASE_KEYSTORE_BASE64`: file `.jks` codificato in Base64 (`base64 -w 0 my-upload-key.jks`)
   - `STORE_PASSWORD`: password del keystore
   - `KEY_PASSWORD`: password della chiave con `alias = upload`
2. **Opzionale (per build Debug stabili):**
   - `DEBUG_KEYSTORE_BASE64`: ha la precedenza sul file `debug.keystore.base64` del repository, così eventuali rigenerazioni locali non cambiano la chiave Debug della CI.

Se stai migrando intenzionalmente da una vecchia build Debug alla prima build Release (o cambiando keystore), puoi avviare manualmente il workflow con `allow_signature_change = true`. Sul dispositivo basterà fare un backup ZIP da **Impostazioni**, installare l'APK nuovo una sola volta dopo aver disinstallato il vecchio e ripristinare il backup.

### Workflow configurati

1. **Android CI & Release (`.github/workflows/android-ci.yml`)**
   - Eseguito su push (`main`, `master`, `develop`), Pull Request e avvio manuale.
   - Calcola automaticamente una versione semantica progressiva (`1.0.<patch+run_number>`, `versionCode` sempre superiore all'ultima release).
   - Esegue i test, verifica i Secrets di firma e la coerenza del certificato rispetto alla release precedente e pubblica la **GitHub Release** con il file `.apk`.

2. **Release Progressiva (`.github/workflows/release.yml`)**
   - Eseguibile manualmente da **Actions → Release Progressiva** (`patch`, `minor`, `major`), oppure tramite push di un tag `vX.Y.Z`.
   - Incrementa `version.properties`, crea commit e tag, verifica la coerenza della firma e pubblica `.apk` (e `.aab` in modalità Release).

## Modulo di Auto-Aggiornamento In-App

PantryMaster scarica e installa gli aggiornamenti da **GitHub Releases** (`repos/{owner}/{repo}/releases/latest`) direttamente all'interno dell'app tramite `PackageInstaller`:

- Scaricamento in background dietro al popup di aggiornamento e verifica della firma prima dell'installazione.
- In caso di firma diversa tra l'APK scaricato e l'app installata, il popup mostra entrambi i certificati (Debug/Release + SHA-256) e guida al salvataggio rapido del backup `.zip` e dell'APK già scaricato.
- In **Impostazioni → Aggiornamenti e Versione** sono sempre visibili il tipo di build (`Debug`/`Release`) e l'impronta `SHA-256` copiabile con un tocco.
