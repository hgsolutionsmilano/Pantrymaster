# Guida alla Firma Release Permanente, Backup Cifrato e Migrazione — PantryMaster

Questa guida descrive il sistema permanente di firma locale per **PantryMaster** (`applicationId = com.aistudio.pantrymaster.vrkqzm`) progettato affinché:
- la chiave privata Release venga creata e custodita **solo sul PC Windows dell'autore** (mai inviata a GitHub, Google AI Studio, cloud esterni o file di progetto);
- **GitHub Actions** esegua esclusivamente verifiche del codice (Debug build, compilazione Kotlin Release, test automatici e controllo che nessun segreto sia presente nel repository);
- tutte le build Release future abbiano **esattamente la stessa identità crittografica**.

---

## 1. Dove viene generato e conservato il Keystore originale

Lo script `PantryMaster-Release-Manager.ps1` (nella root del progetto) genera un singolo keystore permanente con questi parametri fissi:
- **Percorso:** `%LOCALAPPDATA%\PantryMaster\ReleaseSigning\my-upload-key.jks`
- **Formato:** `JKS`
- **Alias:** `upload`
- **Algoritmo:** `RSA 4096 bit`
- **Validità:** `10.000` giorni

Gradle (`app/build.gradle.kts`) legge `KEYSTORE_PATH`, `STORE_PASSWORD` e `KEY_PASSWORD` esclusivamente da variabili d'ambiente in memoria durante la build ed esegue il task `verifyReleaseSigning` che **blocca immediatamente** la build Release se il keystore manca, se le password sono errate o se punta a un certificato `CN=Android Debug`.

### Nota sulla firma Debug in CI (nessun `debug.keystore.base64` richiesto)
PantryMaster è un'app **offline-first** priva di `google-services.json`, Firebase Auth/App Check o SDK esterni vincolati all'impronta SHA-1/SHA-256 Debug: **non esiste alcuna dipendenza applicativa da un certificato Debug stabile**. Su GitHub Actions (`android-ci.yml` e `release.yml`), i runner non dipendono da `debug.keystore.base64` né da GitHub Secrets: usano lo standard `~/.android/debug.keystore` o un `debug.keystore` temporaneo generato sul runner solo per compilazione e test Debug, e poi rimosso.

---

## 2. Inizializzazione in un unico comando (Windows PowerShell 5.1+)

1. Clona o aggiorna il repository sul tuo PC Windows (`git pull`).
2. Apri **Windows PowerShell** nella cartella principale del progetto ed esegui:
   ```powershell
   powershell -ExecutionPolicy Bypass -File .\PantryMaster-Release-Manager.ps1
   ```
3. Nel menu interattivo esegui in ordine:
   - **Opzione 9 (Diagnostica completa):** verifica JDK 21, `keytool`, Android SDK (`apksigner`, `aapt2`) e Gradle.
   - **Opzione 1 (Crea nuova firma Release):** genera `my-upload-key.jks`, richiede le password in modo protetto (`AsSecureString`), crea `RELEASE_CERTIFICATE_INFO.txt` (solo dati pubblici) e non sovrascrive mai una chiave esistente senza doppia conferma esplicita.
   - **Opzione 4 (Crea backup cifrato):** cifra `my-upload-key.jks` con **AES-256-CBC + HMAC-SHA256 + PBKDF2** e verifica l'integrità su due supporti separati (cartella locale `backups\` + chiavetta USB / disco esterno offline).

---

## 3. Backup cifrato, conservazione su due supporti e ripristino

- **Copia 1 (locale):** `%LOCALAPPDATA%\PantryMaster\ReleaseSigning\backups\my-upload-key_backup_<timestamp>.jks.enc`
- **Copia 2 (supporto separato obbligatorio):** unità esterna/USB cifrata offline (richiesta direttamente dall'opzione 4).
- **File informativo pubblico (zero segreti):** `RELEASE_CERTIFICATE_INFO.txt` contiene esclusivamente alias, algoritmo, validità, impronta SHA-256, data di creazione e identificativo pubblico del certificato.
- **Ripristino su nuovo PC:** installa JDK 21 e Android SDK, avvia `PantryMaster-Release-Manager.ps1` e scegli **Opzione 5 (Verifica backup)** indicando il file `.jks.enc` e la password del backup. Lo script verifica HMAC e integrità e ripristina il keystore solo previa conferma esplicita (salvando prima una copia di sicurezza se un file è già presente).

---

## 4. Verifica crittografica e compilazione Release locale

Nel menu di `PantryMaster-Release-Manager.ps1`:
- **Opzione 6 (`Compila APK Release`):** compila `:app:assembleRelease`, salva l'APK firmato in `.\dist\PantryMaster-v<ver>-build<code>.apk` con relativo `.sha256` ed esegue automaticamente `apksigner verify --verbose --print-certs` e `aapt2 dump badging`.
- **Opzione 7 (`Compila AAB Release`):** compila `:app:bundleRelease` e verifica la firma con `jarsigner -verify -strict`.
- **Opzione 8 (`Verifica firma APK`):** verifica qualsiasi APK e controlla che l'impronta SHA-256 coincida con `RELEASE_CERTIFICATE_INFO.txt`.

Per creare una Release futura su GitHub:
1. Compila e verifica localmente l'APK (Opzione 6).
2. Crea il tag/release su GitHub allegando l'APK firmato localmente (e il file `.sha256`). Tutte le versioni firmate con lo stesso `my-upload-key.jks` si aggiorneranno direttamente in-app senza perdere dati.

---

## 5. Analisi versione 1.0.15 e strategia di migrazione dati sicura

### Verifica forense sugli APK distribuiti su GitHub
Analisi eseguita con `apksigner verify --verbose --print-certs` e `aapt2 dump badging`:
- **PantryMaster `v1.0.15` (`build 16`):** `applicationId = com.aistudio.pantrymaster.vrkqzm`, firmato con certificato **`CN=Android Debug`** (`SHA-256: eb35bc9fe31cb9f3e13e53cbc9e4ef1f43af00106508d40d622bdba5d8e635d9`, RSA 2048-bit).
- **PantryMaster `v1.0.16` (`build 17`):** `applicationId = com.aistudio.pantrymaster.vrkqzm`, firmato con un **diverso** certificato **`CN=Android Debug`** (`SHA-256: 44b661aece6de9157878548d9169db29fb936050785e172e66d7fccf68c38420`, RSA 2048-bit).

### Perché l'aggiornamento diretto (e la key rotation v3) NON è possibile rispetto a `1.0.15`
1. Il nuovo keystore permanente `my-upload-key.jks` (RSA 4096-bit) ha inevitabilmente una **impronta SHA-256 diversa** da `eb35bc9fe31cb9f3e13e53cbc9e4ef1f43af00106508d40d622bdba5d8e635d9`.
2. La **rotazione della chiave Android (APK Signature Scheme v3)** richiede crittograficamente di possedere ancora la **chiave privata originale** usata per firmare `v1.0.15` per generare la `SigningCertificateLineage` firmata dalla vecchia chiave — e non è tecnicamente né raccomandato né sicuro basare una catena di produzione su una chiave effimera `CN=Android Debug` ormai assente.
3. Pertanto Android **rifiuterà sempre** l'aggiornamento in-place sopra `1.0.15` (`STATUS_FAILURE_CONFLICT`).

### Procedura di migrazione senza perdita dati (già integrata in PantryMaster)
Prima di installare per la prima volta la nuova Release permanente al posto di `1.0.15` (non disinstallare prima del backup!):
1. Apri PantryMaster `1.0.15` sul telefono → **Impostazioni → Backup e Dati Offline → Backup completo (.zip)**.
   - Questo esporta tutte le 12 tabelle Room (prodotti, lotti, movimenti, categorie, posizioni, liste spesa, **schede Info seconda casa**, promemoria, impostazioni), le immagini locali e il tema scelto. *(Nota: eventuali personalizzazioni minori in DataStore come titolo Home personalizzato o URL repo custom possono essere reimpostate rapidamente dopo il ripristino).*
2. Verifica che il file `pantrymaster_backup.zip` sia stato salvato correttamente (es. in Download o su cloud personale).
3. Solo dopo aver verificato il backup `.zip`, disinstalla `1.0.15` e installa il nuovo APK firmato con `my-upload-key.jks`.
4. Apri la nuova versione → **Impostazioni → Backup e Dati Offline → Ripristina da backup (.zip)** e seleziona `pantrymaster_backup.zip`.
5. Da questo momento in poi tutte le versioni future compilate con `PantryMaster-Release-Manager.ps1` si aggiorneranno direttamente in-app senza disinstallazione.
