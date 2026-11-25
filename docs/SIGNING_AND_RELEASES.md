# Guida alla Firma APK, Keystore e Release — PantryMaster

Questa guida descrive la gestione della chiave di firma crittografica per **PantryMaster** (`applicationId = com.aistudio.pantrymaster.vrkqzm`) in modo da garantire aggiornamenti in-app continui senza mai costringere gli utenti a disinstallare l'app o rischiare la perdita dei dati locali.

---

## 1. Identità crittografica dell'applicazione

Android consente l'aggiornamento diretto di un'app già installata **solo** se:
1. `applicationId` coincide (`com.aistudio.pantrymaster.vrkqzm`).
2. `versionCode` del nuovo APK è maggiore o uguale a quello installato.
3. Il **certificato di firma (SHA-256)** coincide con quello dell'APK installato.

Configurazione Gradle (`app/build.gradle.kts`, `signingConfigs.release`):
- `storeFile`: letto da variabile d'ambiente `KEYSTORE_PATH` (default locale `my-upload-key.jks`).
- `storePassword`: letto da `STORE_PASSWORD`.
- `keyAlias`: `upload`.
- `keyPassword`: letto da `KEY_PASSWORD`.

> **Regola critica:** Non generare mai una nuova chiave di firma se esiste già una chiave con cui gli utenti hanno installato PantryMaster. In caso di perdita del keystore originale non è possibile aggiornare in-place le installazioni esistenti.

---

## 2. Dove conservare e come fare il backup sicuro del Keystore

Il file `.jks` (`my-upload-key.jks`) e le relative password **NON devono mai essere caricati nel repository Git** (sono già esclusi in `.gitignore`).

Conserva almeno **due copie offline cifrate** in luoghi indipendenti:
1. **Password Manager** (es. Bitwarden / 1Password / KeePassXC): allega il file `my-upload-key.jks`, annota `STORE_PASSWORD`, `KEY_PASSWORD`, `keyAlias = upload` e l'impronta `SHA-256` del certificato.
2. **Supporto offline di backup** (es. unità USB cifrata o archivio cloud personale cifrato fuori da GitHub).

Verifica di poter leggere il backup prima di archiviarlo:
```bash
keytool -list -v -keystore my-upload-key.jks -alias upload
```
Annota il valore **SHA256** mostrato nella sezione del certificato.

---

## 3. Configurazione dei GitHub Secrets

Nel repository GitHub apri **Settings → Secrets and variables → Actions → Repository secrets** e imposta:

| Nome Secret | Contenuto richiesto | Obbligatorio |
|---|---|---|
| `RELEASE_KEYSTORE_BASE64` | File `my-upload-key.jks` codificato in Base64 su una sola riga | **Sì (per ogni Release)** |
| `STORE_PASSWORD` | Password del keystore `.jks` | **Sì (per ogni Release)** |
| `KEY_PASSWORD` | Password della chiave privata con alias `upload` | **Sì (per ogni Release)** |
| `DEBUG_KEYSTORE_BASE64` | File `debug.keystore` codificato in Base64 (per stabilità build Debug CI) | Consigliato |

Per generare la stringa Base64 senza interruzioni di riga:
- **Linux:**
  ```bash
  base64 -w 0 my-upload-key.jks
  ```
- **macOS:**
  ```bash
  base64 -i my-upload-key.jks | tr -d '\n'
  ```
- **Windows (PowerShell):**
  ```powershell
  [Convert]::ToBase64String([IO.File]::ReadAllBytes("my-upload-key.jks"))
  ```

> I workflow di GitHub Actions rimuovono automaticamente eventuali spazi/a-capo dal Base64, non stampano mai password o chiavi nei log ed eliminano il file `.jks` temporaneo al termine del job (`if: always()`).

---

## 4. Ripristino della configurazione su un nuovo ambiente locale

Se devi compilare una Release firmata su un nuovo computer:
1. Recupera `my-upload-key.jks`, `STORE_PASSWORD` e `KEY_PASSWORD` dal tuo Password Manager / backup cifrato.
2. Posiziona `my-upload-key.jks` nella root del progetto (oppure imposta `KEYSTORE_PATH`).
3. Esporta le variabili d'ambiente nella shell prima di compilare:
   ```bash
   export KEYSTORE_PATH="$(pwd)/my-upload-key.jks"
   export STORE_PASSWORD="<la-tua-store-password>"
   export KEY_PASSWORD="<la-tua-key-password>"
   ./gradlew assembleRelease
   ```
   *(In questo ambiente usa `gradle :app:assembleRelease` senza `./gradlew`).*

---

## 5. Verifica crittografica del certificato e dei metadati APK

Prima di distribuire qualsiasi APK Release, verifica sempre firma, `applicationId` e `versionCode`:

```bash
# Verifica firma APK (v1/v2/v3) e stampa SHA-256 del certificato
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose --print-certs app/build/outputs/apk/release/app-release.apk

# Verifica packageId, versionCode e versionName
"$ANDROID_HOME/build-tools/36.0.0/aapt2" dump badging app/build/outputs/apk/release/app-release.apk | head -n 1
```

In app puoi anche controllare in qualsiasi momento l'impronta SHA-256 installata sul telefono aprendo **Impostazioni → Aggiornamenti e Versione**, dove il pannello mostra `Build: Release` (o `Debug`) e la `Firma SHA-256` copiabile.

---

## 6. Procedura per creare una nuova Release senza cambiare firma

1. Verifica che su GitHub siano configurati `RELEASE_KEYSTORE_BASE64`, `STORE_PASSWORD` e `KEY_PASSWORD`.
2. Vai su **Actions → Release Progressiva → Run workflow**:
   - Scegli `bump_type` (`patch`, `minor` o `major`).
   - Lascia `allow_signature_change = false` (predefinito).
3. Il workflow esegue automaticamente in sequenza:
   - Controllo integrità keystore Release (fallisce subito se mancante o se è un certificato Debug)
   - Verifica dipendenze, `assembleDebug` e `testDebugUnitTest`
   - Compilazione e firma `assembleRelease` e `bundleRelease`
   - Verifica crittografica con `apksigner verify --print-certs`
   - Confronto automatico con la firma della release precedente pubblicata su GitHub (blocca qualsiasi cambio certificato non intenzionale)
   - Verifica `applicationId`, `versionCode` e `versionName` con `aapt2`
   - Generazione checksum `.sha256`, commit/tag `vX.Y.Z`, upload artifact e pubblicazione GitHub Release.

> **Nota sulla migrazione unica da Debug a Release:** Se sul telefono era installata una vecchia versione firmata Debug e pubblichi la prima versione firmata con il keystore Release definitivo, l'app rileverà automaticamente `Firma digitale diversa` e guiderà l'utente a:
> 1. Esportare il **backup completo (.zip)** di dispensa, foto e schede Info.
> 2. Salvare il nuovo APK già scaricato in **Download**.
> 3. Disinstallare la vecchia versione Debug, installare il nuovo APK Release e ripristinare il backup da **Impostazioni**.
> Tutte le versioni Release successive useranno lo stesso certificato e si aggiorneranno direttamente in-app senza alcuna disinstallazione.
