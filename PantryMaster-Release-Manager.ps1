<#
.SYNOPSIS
    PantryMaster Release & Signing Manager per Windows PowerShell 5.1+.
.DESCRIPTION
    Gestisce in locale sul PC Windows la creazione della firma Release permanente (RSA 4096, JKS),
    verifica chiave e impronta SHA-256, backup cifrato AES-256-CBC + HMAC-SHA256 su doppio supporto,
    ripristino sicuro, compilazione APK/AAB Release con Gradle e verifica crittografica.
#>

[CmdletBinding()]
param()

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

# ==============================================================================
# COSTANTI DI CONFIGURAZIONE
# ==============================================================================
$Script:ExpectedAppId       = 'com.aistudio.pantrymaster.vrkqzm'
$Script:DefaultGithubRepo   = 'hgsolutionsmilano/Pantrymaster'
$Script:KeyAlias            = 'upload'
$Script:KeystoreType        = 'JKS'
$Script:KeyAlgorithm        = 'RSA'
$Script:KeySize             = 4096
$Script:KeyValidityDays     = 10000
$Script:KeyDName            = 'CN=PantryMaster Release, OU=Mobile, O=PantryMaster, L=Milano, ST=MI, C=IT'

$Script:ProjectRoot         = $PSScriptRoot
if ([string]::IsNullOrWhiteSpace($Script:ProjectRoot)) {
    $Script:ProjectRoot = (Get-Location).Path
}
$Script:SigningDir          = Join-Path $env:LOCALAPPDATA 'PantryMaster\ReleaseSigning'
$Script:KeystorePath        = Join-Path $Script:SigningDir 'my-upload-key.jks'
$Script:BackupDir           = Join-Path $Script:SigningDir 'backups'
$Script:PublicCerPath       = Join-Path $Script:SigningDir 'upload-public.cer'
$Script:LocalCertInfoPath   = Join-Path $Script:SigningDir 'RELEASE_CERTIFICATE_INFO.txt'
$Script:RepoCertInfoPath    = Join-Path $Script:ProjectRoot 'RELEASE_CERTIFICATE_INFO.txt'
$Script:DistDir             = Join-Path $Script:ProjectRoot 'dist'
$Script:VersionPropsPath    = Join-Path $Script:ProjectRoot 'version.properties'

# ==============================================================================
# UTILITY SICUREZZA E CREDENZIALI
# ==============================================================================
function ConvertFrom-SecureStringToBytes {
    param([Parameter(Mandatory = $true)][System.Security.SecureString]$SecureValue)
    $bstr = [IntPtr]::Zero
    try {
        $bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureValue)
        $plain = [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
        $bytes = [System.Text.Encoding]::UTF8.GetBytes($plain)
        return ,$bytes
    } finally {
        if ($bstr -ne [IntPtr]::Zero) {
            [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
        }
    }
}

function ConvertFrom-SecureStringTemporary {
    param([Parameter(Mandatory = $true)][System.Security.SecureString]$SecureValue)
    $bstr = [IntPtr]::Zero
    try {
        $bstr = [System.Runtime.InteropServices.Marshal]::SecureStringToBSTR($SecureValue)
        return [System.Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    } finally {
        if ($bstr -ne [IntPtr]::Zero) {
            [System.Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
        }
    }
}

function Read-ConfirmedPassword {
    param(
        [Parameter(Mandatory = $true)][string]$Prompt,
        [int]$MinLength = 8
    )
    while ($true) {
        $sec1 = Read-Host -Prompt $Prompt -AsSecureString
        if ($sec1.Length -lt $MinLength) {
            Write-Host "La password deve contenere almeno $MinLength caratteri." -ForegroundColor Yellow
            $sec1.Dispose()
            continue
        }
        $sec2 = Read-Host -Prompt "Conferma password" -AsSecureString
        $b1 = ConvertFrom-SecureStringToBytes -SecureValue $sec1
        $b2 = ConvertFrom-SecureStringToBytes -SecureValue $sec2
        $match = ($b1.Length -eq $b2.Length)
        if ($match) {
            for ($i = 0; $i -lt $b1.Length; $i++) {
                if ($b1[$i] -ne $b2[$i]) { $match = $false; break }
            }
        }
        [Array]::Clear($b1, 0, $b1.Length)
        [Array]::Clear($b2, 0, $b2.Length)
        $sec2.Dispose()
        if ($match) {
            return $sec1
        }
        $sec1.Dispose()
        Write-Host "Le password non coincidono. Riprova." -ForegroundColor Red
    }
}

function Get-FileSha256Hex {
    param([Parameter(Mandatory = $true)][string]$Path)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $stream = [System.IO.File]::OpenRead($Path)
        try {
            $hash = $sha.ComputeHash($stream)
            return ([System.BitConverter]::ToString($hash)).Replace('-', '').ToUpperInvariant()
        } finally {
            $stream.Dispose()
        }
    } finally {
        $sha.Dispose()
    }
}

function Get-BytesSha256Hex {
    param([Parameter(Mandatory = $true)][byte[]]$Data)
    $sha = [System.Security.Cryptography.SHA256]::Create()
    try {
        $hash = $sha.ComputeHash($Data)
        return ([System.BitConverter]::ToString($hash)).Replace('-', '').ToUpperInvariant()
    } finally {
        $sha.Dispose()
    }
}

# ==============================================================================
# RILEVAMENTO TOOL DI SISTEMA (JAVA, KEYTOOL, ANDROID SDK, GRADLE)
# ==============================================================================
function Ensure-SigningDirectories {
    foreach ($dir in @($Script:SigningDir, $Script:BackupDir)) {
        if (-not (Test-Path -LiteralPath $dir)) {
            New-Item -ItemType Directory -Path $dir -Force | Out-Null
        }
    }
}

function Find-Executable {
    param([Parameter(Mandatory = $true)][string]$Name)
    $cmd = Get-Command -Name $Name -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    return $null
}

function Get-JavaTooling {
    $javaExe = Find-Executable 'java'
    $keytoolExe = Find-Executable 'keytool'
    if (-not $keytoolExe -and $env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME 'bin\keytool.exe'
        if (Test-Path -LiteralPath $candidate) { $keytoolExe = $candidate }
    }
    if (-not $javaExe -and $env:JAVA_HOME) {
        $candidate = Join-Path $env:JAVA_HOME 'bin\java.exe'
        if (Test-Path -LiteralPath $candidate) { $javaExe = $candidate }
    }
    return @{
        Java    = $javaExe
        Keytool = $keytoolExe
    }
}

function Get-AndroidSdkPath {
    $candidates = @()
    if ($env:ANDROID_HOME) { $candidates += $env:ANDROID_HOME }
    if ($env:ANDROID_SDK_ROOT) { $candidates += $env:ANDROID_SDK_ROOT }
    if ($env:LOCALAPPDATA) { $candidates += (Join-Path $env:LOCALAPPDATA 'Android\Sdk') }
    foreach ($c in $candidates) {
        if ($c -and (Test-Path -LiteralPath $c)) {
            return $c
        }
    }
    return $null
}

function Get-AndroidBuildTool {
    param([Parameter(Mandatory = $true)][string]$ToolBaseName)
    $sdk = Get-AndroidSdkPath
    if (-not $sdk) { return $null }
    $btDir = Join-Path $sdk 'build-tools'
    if (-not (Test-Path -LiteralPath $btDir)) { return $null }
    $versions = Get-ChildItem -LiteralPath $btDir -Directory | Sort-Object Name -Descending
    foreach ($ver in $versions) {
        foreach ($ext in @('.bat', '.exe', '')) {
            $full = Join-Path $ver.FullName ($ToolBaseName + $ext)
            if (Test-Path -LiteralPath $full) {
                return $full
            }
        }
    }
    return $null
}

function Get-GradleRunner {
    $wrapper = Join-Path $Script:ProjectRoot 'gradlew.bat'
    if (Test-Path -LiteralPath $wrapper) {
        return $wrapper
    }
    $gradleCmd = Find-Executable 'gradle'
    if ($gradleCmd) {
        return $gradleCmd
    }
    return $null
}

# ==============================================================================
# LETTURA METADATI CERTIFICATO E SCRITTURA RELEASE_CERTIFICATE_INFO.txt
# ==============================================================================
function Get-CertificateMetadataFromKeystore {
    param(
        [Parameter(Mandatory = $true)][string]$KeystoreFile,
        [Parameter(Mandatory = $true)][System.Security.SecureString]$StorePassword
    )
    $tools = Get-JavaTooling
    if (-not $tools.Keytool) {
        throw "keytool non trovato. Installa JDK 21 o configura JAVA_HOME."
    }

    $tempCer = [System.IO.Path]::Combine([System.IO.Path]::GetTempPath(), "pm_pub_$([guid]::NewGuid().ToString('N')).cer")
    $env:PM_TMP_SP = ConvertFrom-SecureStringTemporary -SecureValue $StorePassword
    try {
        $exportOut = & $tools.Keytool -exportcert -rfc -keystore $KeystoreFile -storetype $Script:KeystoreType -alias $Script:KeyAlias -storepass:env PM_TMP_SP -file $tempCer 2>&1
        if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $tempCer)) {
            throw "Impossibile esportare il certificato pubblico (password keystore errata o alias '$($Script:KeyAlias)' assente). Dettaglio: $($exportOut -join ' ')"
        }

        $cert = New-Object System.Security.Cryptography.X509Certificates.X509Certificate2($tempCer)
        $sha256Raw = Get-BytesSha256Hex -Data $cert.RawData
        $sha256Colon = ($sha256Raw -replace '(.{2})(?=.)', '$1:')
        $keyAlg = $cert.PublicKey.Oid.FriendlyName
        $keyBits = $cert.PublicKey.Key.KeySize

        return [PSCustomObject]@{
            Alias           = $Script:KeyAlias
            Algorithm       = "$keyAlg $keyBits bit"
            Validity        = "$($cert.NotBefore.ToString('yyyy-MM-dd HH:mm:ss')) -> $($cert.NotAfter.ToString('yyyy-MM-dd HH:mm:ss')) (~$($Script:KeyValidityDays) giorni)"
            Sha256Hex       = $sha256Raw
            Sha256Colon     = $sha256Colon
            CreatedAt       = $cert.NotBefore.ToString('yyyy-MM-dd HH:mm:ss zzz')
            Subject         = $cert.Subject
            SerialNumber    = $cert.SerialNumber
            PublicCerBase64 = [Convert]::ToBase64String($cert.RawData)
        }
    } finally {
        Remove-Item Env:\PM_TMP_SP -ErrorAction SilentlyContinue
        if (Test-Path -LiteralPath $tempCer) {
            Remove-Item -LiteralPath $tempCer -Force -ErrorAction SilentlyContinue
        }
    }
}

function Write-PublicCertificateInfoFiles {
    param([Parameter(Mandatory = $true)]$Meta)
    Ensure-SigningDirectories

    # Esporta certificato pubblico (.cer) NON segreto
    $cerBytes = [Convert]::FromBase64String($Meta.PublicCerBase64)
    [System.IO.File]::WriteAllBytes($Script:PublicCerPath, $cerBytes)

    $content = @"
PANTRYMASTER - INFORMAZIONI CERTIFICATO PUBBLICO RELEASE
(File privo di segreti: NON contiene chiavi private o password)

Application ID : $($Script:ExpectedAppId)
Alias          : $($Meta.Alias)
Algoritmo      : $($Meta.Algorithm)
Validita       : $($Meta.Validity)
Data creazione : $($Meta.CreatedAt)
Impronta SHA-256 (colon): $($Meta.Sha256Colon)
Impronta SHA-256 (hex)  : $($Meta.Sha256Hex)
Identificativo certificato pubblico (Subject): $($Meta.Subject)
Numero di serie pubblico (Serial): $($Meta.SerialNumber)
"@

    [System.IO.File]::WriteAllText($Script:LocalCertInfoPath, $content, [System.Text.Encoding]::UTF8)
    [System.IO.File]::WriteAllText($Script:RepoCertInfoPath, $content, [System.Text.Encoding]::UTF8)
}

function Test-PrivateKeyAccess {
    param(
        [Parameter(Mandatory = $true)][string]$KeystoreFile,
        [Parameter(Mandatory = $true)][System.Security.SecureString]$StorePassword,
        [Parameter(Mandatory = $true)][System.Security.SecureString]$KeyPassword
    )
    # Verifica non distruttiva tramite jarsigner/Java KeyStore direttamente in memoria
    $tools = Get-JavaTooling
    if (-not $tools.Java) {
        throw "java non trovato. Configura JDK 21."
    }
    $sp = ConvertFrom-SecureStringTemporary -SecureValue $StorePassword
    $kp = ConvertFrom-SecureStringTemporary -SecureValue $KeyPassword
    try {
        # Usa il tool integrato di PowerShell/.NET se PKCS12? JKS richiede Java KeyStore:
        $env:PM_V_KS = $KeystoreFile
        $env:PM_V_SP = $sp
        $env:PM_V_KP = $kp
        $env:PM_V_AL = $Script:KeyAlias
        $tempDir = Join-Path ([System.IO.Path]::GetTempPath()) ("pm_kv_" + [guid]::NewGuid().ToString('N'))
        New-Item -ItemType Directory -Path $tempDir -Force | Out-Null
        $javaSrc = Join-Path $tempDir 'VerifyJks.java'
        @'
import java.io.FileInputStream;
import java.security.Key;
import java.security.KeyStore;
import java.security.cert.X509Certificate;

public class VerifyJks {
    public static void main(String[] args) throws Exception {
        String path = System.getenv("PM_V_KS");
        char[] sp = System.getenv("PM_V_SP").toCharArray();
        char[] kp = System.getenv("PM_V_KP").toCharArray();
        String alias = System.getenv("PM_V_AL");
        KeyStore ks = KeyStore.getInstance("JKS");
        try (FileInputStream fis = new FileInputStream(path)) {
            ks.load(fis, sp);
        }
        if (!ks.containsAlias(alias) || !ks.isKeyEntry(alias)) {
            System.err.println("Alias non trovato o non valido: " + alias);
            System.exit(2);
        }
        Key k = ks.getKey(alias, kp);
        if (k == null) {
            System.err.println("Chiave privata non leggibile.");
            System.exit(3);
        }
        X509Certificate cert = (X509Certificate) ks.getCertificate(alias);
        if (cert.getSubjectX500Principal().getName().toLowerCase().contains("cn=android debug")) {
            System.err.println("ERRORE: certificato Android Debug rilevato!");
            System.exit(4);
        }
        System.out.println("OK:" + cert.getSubjectX500Principal().getName());
    }
}
'@ | Set-Content -LiteralPath $javaSrc -Encoding ASCII
        $out = & $tools.Java $javaSrc 2>&1
        $exit = $LASTEXITCODE
        Remove-Item -LiteralPath $tempDir -Recurse -Force -ErrorAction SilentlyContinue
        if ($exit -ne 0) {
            throw "Verifica chiave privata fallita: $($out -join ' ')"
        }
        return ($out -join "`n")
    } finally {
        $sp = $null
        $kp = $null
        Remove-Item Env:\PM_V_KS -ErrorAction SilentlyContinue
        Remove-Item Env:\PM_V_SP -ErrorAction SilentlyContinue
        Remove-Item Env:\PM_V_KP -ErrorAction SilentlyContinue
        Remove-Item Env:\PM_V_AL -ErrorAction SilentlyContinue
    }
}

# ==============================================================================
# CIFRATURA / DECIFRATURA BACKUP (AES-256-CBC + HMAC-SHA256 + PBKDF2)
# ==============================================================================
function Derive-BackupKeys {
    param(
        [Parameter(Mandatory = $true)][byte[]]$PasswordBytes,
        [Parameter(Mandatory = $true)][byte[]]$Salt,
        [Parameter(Mandatory = $true)][string]$Magic
    )
    $iterations = 200000
    $kdf = $null
    try {
        if ($Magic -eq 'PMBK02') {
            $kdf = New-Object System.Security.Cryptography.Rfc2898DeriveBytes(
                $PasswordBytes,
                $Salt,
                $iterations,
                [System.Security.Cryptography.HashAlgorithmName]::SHA256
            )
        } else {
            $passStr = [System.Text.Encoding]::UTF8.GetString($PasswordBytes)
            $kdf = New-Object System.Security.Cryptography.Rfc2898DeriveBytes($passStr, $Salt, $iterations)
        }
        $keyMaterial = $kdf.GetBytes(64)
        $encKey = New-Object byte[] 32
        $macKey = New-Object byte[] 32
        [Array]::Copy($keyMaterial, 0, $encKey, 0, 32)
        [Array]::Copy($keyMaterial, 32, $macKey, 0, 32)
        [Array]::Clear($keyMaterial, 0, $keyMaterial.Length)
        return @{
            EncKey = $encKey
            MacKey = $macKey
        }
    } finally {
        if ($kdf) { $kdf.Dispose() }
    }
}

function Protect-KeystoreBackup {
    param(
        [Parameter(Mandatory = $true)][string]$SourceJksPath,
        [Parameter(Mandatory = $true)][string]$DestinationEncPath,
        [Parameter(Mandatory = $true)][System.Security.SecureString]$BackupPassword
    )
    $plainBytes = [System.IO.File]::ReadAllBytes($SourceJksPath)
    $passBytes = ConvertFrom-SecureStringToBytes -SecureValue $BackupPassword
    $salt = New-Object byte[] 16
    $iv = New-Object byte[] 16
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try {
        $rng.GetBytes($salt)
        $rng.GetBytes($iv)
    } finally {
        $rng.Dispose()
    }

    $magic = 'PMBK02'
    $keys = $null
    try {
        try {
            $keys = Derive-BackupKeys -PasswordBytes $passBytes -Salt $salt -Magic $magic
        } catch {
            $magic = 'PMBK01'
            $keys = Derive-BackupKeys -PasswordBytes $passBytes -Salt $salt -Magic $magic
        }

        $aes = [System.Security.Cryptography.Aes]::Create()
        try {
            $aes.KeySize = 256
            $aes.Mode = [System.Security.Cryptography.CipherMode]::CBC
            $aes.Padding = [System.Security.Cryptography.PaddingMode]::PKCS7
            $aes.Key = $keys.EncKey
            $aes.IV = $iv

            $ms = New-Object System.IO.MemoryStream
            try {
                $magicBytes = [System.Text.Encoding]::ASCII.GetBytes($magic)
                $ms.Write($magicBytes, 0, $magicBytes.Length)
                $ms.Write($salt, 0, $salt.Length)
                $ms.Write($iv, 0, $iv.Length)

                $encryptor = $aes.CreateEncryptor()
                try {
                    $cs = New-Object System.Security.Cryptography.CryptoStream(
                        $ms,
                        $encryptor,
                        [System.Security.Cryptography.CryptoStreamMode]::Write
                    )
                    $cs.Write($plainBytes, 0, $plainBytes.Length)
                    $cs.FlushFinalBlock()
                } finally {
                    $encryptor.Dispose()
                }

                $bodyWithoutMac = $ms.ToArray()
                $hmac = New-Object System.Security.Cryptography.HMACSHA256 -ArgumentList (, $keys.MacKey)
                try {
                    $tag = $hmac.ComputeHash($bodyWithoutMac)
                } finally {
                    $hmac.Dispose()
                }

                $fs = [System.IO.File]::Create($DestinationEncPath)
                try {
                    $fs.Write($bodyWithoutMac, 0, $bodyWithoutMac.Length)
                    $fs.Write($tag, 0, $tag.Length)
                } finally {
                    $fs.Dispose()
                }
            } finally {
                $ms.Dispose()
            }
        } finally {
            $aes.Dispose()
        }
    } finally {
        [Array]::Clear($plainBytes, 0, $plainBytes.Length)
        [Array]::Clear($passBytes, 0, $passBytes.Length)
        if ($keys) {
            [Array]::Clear($keys.EncKey, 0, $keys.EncKey.Length)
            [Array]::Clear($keys.MacKey, 0, $keys.MacKey.Length)
        }
    }
}

function Unprotect-KeystoreBackup {
    param(
        [Parameter(Mandatory = $true)][string]$BackupEncPath,
        [Parameter(Mandatory = $true)][System.Security.SecureString]$BackupPassword
    )
    $all = [System.IO.File]::ReadAllBytes($BackupEncPath)
    if ($all.Length -lt (6 + 16 + 16 + 16 + 32)) {
        throw "File backup non valido o troncato."
    }

    $magic = [System.Text.Encoding]::ASCII.GetString($all, 0, 6)
    if ($magic -ne 'PMBK02' -and $magic -ne 'PMBK01') {
        throw "Intestazione backup sconosciuta ($magic)."
    }

    $salt = New-Object byte[] 16
    $iv = New-Object byte[] 16
    [Array]::Copy($all, 6, $salt, 0, 16)
    [Array]::Copy($all, 22, $iv, 0, 16)

    $bodyLen = $all.Length - 32
    $cipherOffset = 38
    $cipherLen = $bodyLen - $cipherOffset
    $macOffset = $bodyLen

    $passBytes = ConvertFrom-SecureStringToBytes -SecureValue $BackupPassword
    $keys = $null
    try {
        $keys = Derive-BackupKeys -PasswordBytes $passBytes -Salt $salt -Magic $magic
        $hmac = New-Object System.Security.Cryptography.HMACSHA256 -ArgumentList (, $keys.MacKey)
        try {
            $expectedMac = $hmac.ComputeHash($all, 0, $bodyLen)
        } finally {
            $hmac.Dispose()
        }

        $diff = 0
        for ($i = 0; $i -lt 32; $i++) {
            $diff = $diff -bor ($expectedMac[$i] -bxor $all[$macOffset + $i])
        }
        if ($diff -ne 0) {
            throw "Verifica HMAC fallita: password backup errata o file danneggiato."
        }

        $aes = [System.Security.Cryptography.Aes]::Create()
        try {
            $aes.KeySize = 256
            $aes.Mode = [System.Security.Cryptography.CipherMode]::CBC
            $aes.Padding = [System.Security.Cryptography.PaddingMode]::PKCS7
            $aes.Key = $keys.EncKey
            $aes.IV = $iv

            $decryptor = $aes.CreateDecryptor()
            $msOut = New-Object System.IO.MemoryStream
            try {
                $msIn = New-Object System.IO.MemoryStream($all, $cipherOffset, $cipherLen)
                try {
                    $cs = New-Object System.Security.Cryptography.CryptoStream(
                        $msIn,
                        $decryptor,
                        [System.Security.Cryptography.CryptoStreamMode]::Read
                    )
                    $cs.CopyTo($msOut)
                } finally {
                    $msIn.Dispose()
                }
                return ,($msOut.ToArray())
            } finally {
                $decryptor.Dispose()
                $msOut.Dispose()
            }
        } finally {
            $aes.Dispose()
        }
    } finally {
        [Array]::Clear($passBytes, 0, $passBytes.Length)
        if ($keys) {
            [Array]::Clear($keys.EncKey, 0, $keys.EncKey.Length)
            [Array]::Clear($keys.MacKey, 0, $keys.MacKey.Length)
        }
    }
}

# ==============================================================================
# GESTIONE VERSION.PROPERTIES & BUILD GRADLE
# ==============================================================================
function Read-VersionProperties {
    if (-not (Test-Path -LiteralPath $Script:VersionPropsPath)) {
        throw "File version.properties non trovato in $($Script:ProjectRoot)."
    }
    $map = @{}
    foreach ($line in (Get-Content -LiteralPath $Script:VersionPropsPath)) {
        $trimmed = $line.Trim()
        if ([string]::IsNullOrEmpty($trimmed) -or $trimmed.StartsWith('#')) { continue }
        $idx = $trimmed.IndexOf('=')
        if ($idx -gt 0) {
            $k = $trimmed.Substring(0, $idx).Trim()
            $v = $trimmed.Substring($idx + 1).Trim()
            $map[$k] = $v
        }
    }
    return $map
}

function Save-VersionProperties {
    param([Parameter(Mandatory = $true)][hashtable]$Props)
    $repo = if ($Props.ContainsKey('GITHUB_REPO') -and $Props['GITHUB_REPO']) { $Props['GITHUB_REPO'] } else { $Script:DefaultGithubRepo }
    $lines = @(
        "VERSION_MAJOR=$($Props['VERSION_MAJOR'])",
        "VERSION_MINOR=$($Props['VERSION_MINOR'])",
        "VERSION_PATCH=$($Props['VERSION_PATCH'])",
        "VERSION_CODE=$($Props['VERSION_CODE'])",
        "GITHUB_REPO=$repo"
    )
    [System.IO.File]::WriteAllLines($Script:VersionPropsPath, $lines, [System.Text.Encoding]::ASCII)
}

function Invoke-ReleaseGradleTask {
    param(
        [Parameter(Mandatory = $true)][ValidateSet('APK', 'AAB')][string]$TargetType
    )
    if (-not (Test-Path -LiteralPath $Script:KeystorePath)) {
        throw "Keystore non trovato in $($Script:KeystorePath). Esegui prima l'opzione 1."
    }
    $gradleRunner = Get-GradleRunner
    if (-not $gradleRunner) {
        throw "Gradle non trovato (ne' gradlew.bat ne' gradle nel PATH)."
    }

    $props = Read-VersionProperties
    $curVer = "$($props['VERSION_MAJOR']).$($props['VERSION_MINOR']).$($props['VERSION_PATCH'])"
    $curCode = [int]$props['VERSION_CODE']
    Write-Host ""
    Write-Host "Versione corrente in version.properties: v$curVer (versionCode=$curCode)" -ForegroundColor Cyan
    $inc = Read-Host "Vuoi incrementare patch e versionCode prima della build? [s/N]"
    if ($inc -match '^(s|si|y|yes)$') {
        $props['VERSION_PATCH'] = ([int]$props['VERSION_PATCH'] + 1).ToString()
        $props['VERSION_CODE'] = ($curCode + 1).ToString()
        Save-VersionProperties -Props $props
        $curVer = "$($props['VERSION_MAJOR']).$($props['VERSION_MINOR']).$($props['VERSION_PATCH'])"
        $curCode = [int]$props['VERSION_CODE']
        Write-Host "Aggiornato version.properties -> v$curVer (versionCode=$curCode)" -ForegroundColor Green
    }

    $repoSlug = if ($props['GITHUB_REPO']) { $props['GITHUB_REPO'] } else { $Script:DefaultGithubRepo }
    $storePass = Read-Host -Prompt "Inserisci STORE_PASSWORD" -AsSecureString
    $useSame = Read-Host "La password della chiave '$($Script:KeyAlias)' coincide con STORE_PASSWORD? [S/n]"
    if ($useSame -match '^n') {
        $keyPass = Read-Host -Prompt "Inserisci KEY_PASSWORD" -AsSecureString
    } else {
        $keyPass = $storePass
    }

    $taskName = if ($TargetType -eq 'APK') { ':app:assembleRelease' } else { ':app:bundleRelease' }

    try {
        Write-Host "Verifica pre-build chiave privata..." -ForegroundColor Cyan
        $null = Test-PrivateKeyAccess -KeystoreFile $Script:KeystorePath -StorePassword $storePass -KeyPassword $keyPass

        $env:KEYSTORE_PATH = $Script:KeystorePath
        $env:STORE_PASSWORD = ConvertFrom-SecureStringTemporary -SecureValue $storePass
        $env:KEY_PASSWORD = ConvertFrom-SecureStringTemporary -SecureValue $keyPass

        Write-Host "Avvio Gradle $taskName per v$curVer (build $curCode)..." -ForegroundColor Cyan
        Push-Location $Script:ProjectRoot
        try {
            & $gradleRunner $taskName "-PversionCode=$curCode" "-PversionName=$curVer" "-PgithubRepo=$repoSlug" '--no-daemon'
            if ($LASTEXITCODE -ne 0) {
                throw "Compilazione Gradle fallita (exit code $LASTEXITCODE)."
            }
        } finally {
            Pop-Location
        }

        if (-not (Test-Path -LiteralPath $Script:DistDir)) {
            New-Item -ItemType Directory -Path $Script:DistDir -Force | Out-Null
        }

        if ($TargetType -eq 'APK') {
            $src = Join-Path $Script:ProjectRoot 'app\build\outputs\apk\release\app-release.apk'
            if (-not (Test-Path -LiteralPath $src)) { throw "APK Release non generato in $src" }
            $dest = Join-Path $Script:DistDir "PantryMaster-v$curVer-build$curCode.apk"
            Copy-Item -LiteralPath $src -Destination $dest -Force
            $sha = Get-FileSha256Hex -Path $dest
            "$sha  $(Split-Path -Leaf $dest)" | Set-Content -LiteralPath "$dest.sha256" -Encoding ASCII
            Write-Host "APK Release pronto: $dest" -ForegroundColor Green
            Write-Host "SHA-256 file APK  : $sha" -ForegroundColor Green
            Verify-ApkArtifact -ApkPath $dest -ExpectedVersionCode $curCode -ExpectedVersionName $curVer
        } else {
            $src = Join-Path $Script:ProjectRoot 'app\build\outputs\bundle\release\app-release.aab'
            if (-not (Test-Path -LiteralPath $src)) { throw "AAB Release non generato in $src" }
            $dest = Join-Path $Script:DistDir "PantryMaster-v$curVer-build$curCode.aab"
            Copy-Item -LiteralPath $src -Destination $dest -Force
            $sha = Get-FileSha256Hex -Path $dest
            "$sha  $(Split-Path -Leaf $dest)" | Set-Content -LiteralPath "$dest.sha256" -Encoding ASCII
            $jarsigner = Find-Executable 'jarsigner'
            if ($jarsigner) {
                & $jarsigner -verify -strict $dest | Out-Null
                if ($LASTEXITCODE -ne 0) {
                    throw "Verifica firma AAB con jarsigner fallita!"
                }
                Write-Host "Firma AAB verificata con jarsigner." -ForegroundColor Green
            }
            Write-Host "AAB Release pronto: $dest" -ForegroundColor Green
            Write-Host "SHA-256 file AAB  : $sha" -ForegroundColor Green
        }
    } finally {
        Remove-Item Env:\KEYSTORE_PATH -ErrorAction SilentlyContinue
        Remove-Item Env:\STORE_PASSWORD -ErrorAction SilentlyContinue
        Remove-Item Env:\KEY_PASSWORD -ErrorAction SilentlyContinue
        if ($storePass) { $storePass.Dispose() }
        if ($keyPass -and ($keyPass -ne $storePass)) { $keyPass.Dispose() }
    }
}

function Verify-ApkArtifact {
    param(
        [Parameter(Mandatory = $true)][string]$ApkPath,
        [int]$ExpectedVersionCode = 0,
        [string]$ExpectedVersionName = ''
    )
    if (-not (Test-Path -LiteralPath $ApkPath)) {
        throw "File APK non trovato: $ApkPath"
    }
    $apksigner = Get-AndroidBuildTool -ToolBaseName 'apksigner'
    if (-not $apksigner) {
        throw "apksigner non trovato nell'Android SDK. Verifica ANDROID_HOME o ANDROID_SDK_ROOT."
    }

    Write-Host ""
    Write-Host "=== Verifica firma crittografica (apksigner) ===" -ForegroundColor Cyan
    $verifyOut = & $apksigner verify --verbose --print-certs $ApkPath 2>&1
    $verifyOut | ForEach-Object { Write-Host $_ }
    if ($LASTEXITCODE -ne 0) {
        throw "Verifica firma APK fallita!"
    }
    $joined = $verifyOut -join "`n"
    if ($joined -match 'CN=Android Debug') {
        throw "ATTENZIONE: l'APK risulta firmato con certificato Android Debug!"
    }

    $aapt2 = Get-AndroidBuildTool -ToolBaseName 'aapt2'
    if ($aapt2) {
        Write-Host ""
        Write-Host "=== Verifica metadati manifest (aapt2) ===" -ForegroundColor Cyan
        $badging = (& $aapt2 dump badging $ApkPath 2>&1) | Select-Object -First 2
        $badging | ForEach-Object { Write-Host $_ }
        $firstLine = ($badging | Select-Object -First 1)
        if ($firstLine -notmatch "name='$([Regex]::Escape($Script:ExpectedAppId))'") {
            throw "applicationId non corrispondente (atteso $($Script:ExpectedAppId))!"
        }
        if ($ExpectedVersionCode -gt 0 -and $firstLine -notmatch "versionCode='$ExpectedVersionCode'") {
            throw "versionCode non corrispondente (atteso $ExpectedVersionCode)!"
        }
        if ($ExpectedVersionName -and $firstLine -notmatch "versionName='$ExpectedVersionName'") {
            throw "versionName non corrispondente (atteso $ExpectedVersionName)!"
        }
    } else {
        Write-Host "Avviso: aapt2 non trovato, verifica manifest saltata." -ForegroundColor Yellow
    }

    if (Test-Path -LiteralPath $Script:LocalCertInfoPath) {
        $infoText = Get-Content -LiteralPath $Script:LocalCertInfoPath -Raw
        if ($infoText -match 'Impronta SHA-256 \(hex\)\s*:\s*([A-F0-9]{64})') {
            $expectedHex = $Matches[1].ToLowerInvariant()
            if ($joined.ToLowerInvariant() -match [Regex]::Escape($expectedHex)) {
                Write-Host "CONFERMA: l'APK corrisponde esattamente al certificato Release permanente ($expectedHex)." -ForegroundColor Green
            } else {
                Write-Host "ATTENZIONE: l'impronta SHA-256 dell'APK NON coincide con RELEASE_CERTIFICATE_INFO.txt ($expectedHex)!" -ForegroundColor Red
            }
        }
    }
}

# ==============================================================================
# AZIONI DEL MENU INTERATTIVO (1..10)
# ==============================================================================
function Action-CreateKeystore {
    Ensure-SigningDirectories
    $tools = Get-JavaTooling
    if (-not $tools.Keytool) {
        throw "keytool non trovato. Installa JDK 21 e aggiungi bin al PATH."
    }

    if (Test-Path -LiteralPath $Script:KeystorePath) {
        Write-Host ""
        Write-Host "ATTENZIONE: esiste gia' un keystore in:" -ForegroundColor Red
        Write-Host "  $($Script:KeystorePath)" -ForegroundColor Yellow
        Write-Host "Sovrascriverlo renderebbe impossibile aggiornare le app gia' firmate con questa chiave." -ForegroundColor Red
        $confirm = Read-Host "Digita esattamente 'SOVRASCRIVI' per procedere (qualsiasi altro testo annulla)"
        if ($confirm -ne 'SOVRASCRIVI') {
            Write-Host "Operazione annullata. Nessun file modificato." -ForegroundColor Green
            return
        }
    }

    Write-Host ""
    Write-Host "Generazione nuovo keystore Release permanente:" -ForegroundColor Cyan
    Write-Host "  Percorso : $($Script:KeystorePath)"
    Write-Host "  Formato  : $($Script:KeystoreType)"
    Write-Host "  Alias    : $($Script:KeyAlias)"
    Write-Host "  Algoritmo: $($Script:KeyAlgorithm) $($Script:KeySize) bit"
    Write-Host "  Validita : $($Script:KeyValidityDays) giorni"
    Write-Host ""

    $storePass = Read-ConfirmedPassword -Prompt "Nuova password Keystore (STORE_PASSWORD, min 8 caratteri)" -MinLength 8
    $samePass = Read-Host "Usare la stessa password anche per la chiave '$($Script:KeyAlias)'? [S/n]"
    if ($samePass -match '^n') {
        $keyPass = Read-ConfirmedPassword -Prompt "Nuova password Chiave (KEY_PASSWORD, min 8 caratteri)" -MinLength 8
    } else {
        $keyPass = $storePass
    }

    try {
        $env:PM_GEN_SP = ConvertFrom-SecureStringTemporary -SecureValue $storePass
        $env:PM_GEN_KP = ConvertFrom-SecureStringTemporary -SecureValue $keyPass

        $genOut = & $tools.Keytool -genkeypair `
            -keystore $Script:KeystorePath `
            -storetype $Script:KeystoreType `
            -alias $Script:KeyAlias `
            -keyalg $Script:KeyAlgorithm `
            -keysize $Script:KeySize `
            -validity $Script:KeyValidityDays `
            -dname $Script:KeyDName `
            -storepass:env PM_GEN_SP `
            -keypass:env PM_GEN_KP 2>&1

        if ($LASTEXITCODE -ne 0 -or -not (Test-Path -LiteralPath $Script:KeystorePath)) {
            throw "Errore keytool durante la creazione del keystore: $($genOut -join ' ')"
        }

        $null = Test-PrivateKeyAccess -KeystoreFile $Script:KeystorePath -StorePassword $storePass -KeyPassword $keyPass
        $meta = Get-CertificateMetadataFromKeystore -KeystoreFile $Script:KeystorePath -StorePassword $storePass
        Write-PublicCertificateInfoFiles -Meta $meta

        Write-Host ""
        Write-Host "Keystore Release creato e verificato con successo!" -ForegroundColor Green
        Write-Host "Impronta SHA-256 : $($meta.Sha256Colon)" -ForegroundColor Cyan
        Write-Host "Info pubbliche   : $($Script:RepoCertInfoPath)" -ForegroundColor Cyan
        Write-Host "PROSSIMO PASSO CONSIGLIATO: esegui subito l'opzione 4 (Crea backup cifrato)!" -ForegroundColor Yellow
    } finally {
        Remove-Item Env:\PM_GEN_SP -ErrorAction SilentlyContinue
        Remove-Item Env:\PM_GEN_KP -ErrorAction SilentlyContinue
        if ($storePass) { $storePass.Dispose() }
        if ($keyPass -and ($keyPass -ne $storePass)) { $keyPass.Dispose() }
    }
}

function Action-VerifyKeystore {
    if (-not (Test-Path -LiteralPath $Script:KeystorePath)) {
        throw "Keystore non trovato in $($Script:KeystorePath)."
    }
    $storePass = Read-Host -Prompt "Inserisci STORE_PASSWORD" -AsSecureString
    $samePass = Read-Host "KEY_PASSWORD coincide con STORE_PASSWORD? [S/n]"
    if ($samePass -match '^n') {
        $keyPass = Read-Host -Prompt "Inserisci KEY_PASSWORD" -AsSecureString
    } else {
        $keyPass = $storePass
    }
    try {
        $res = Test-PrivateKeyAccess -KeystoreFile $Script:KeystorePath -StorePassword $storePass -KeyPassword $keyPass
        $meta = Get-CertificateMetadataFromKeystore -KeystoreFile $Script:KeystorePath -StorePassword $storePass
        Write-PublicCertificateInfoFiles -Meta $meta
        Write-Host ""
        Write-Host "Keystore VALIDO e chiave privata sbloccata ($res)" -ForegroundColor Green
        Write-Host "Alias     : $($meta.Alias)"
        Write-Host "Algoritmo : $($meta.Algorithm)"
        Write-Host "Validita  : $($meta.Validity)"
        Write-Host "SHA-256   : $($meta.Sha256Colon)"
    } finally {
        if ($storePass) { $storePass.Dispose() }
        if ($keyPass -and ($keyPass -ne $storePass)) { $keyPass.Dispose() }
    }
}

function Action-ShowFingerprint {
    if (-not (Test-Path -LiteralPath $Script:KeystorePath)) {
        throw "Keystore non trovato in $($Script:KeystorePath)."
    }
    $storePass = Read-Host -Prompt "Inserisci STORE_PASSWORD" -AsSecureString
    try {
        $meta = Get-CertificateMetadataFromKeystore -KeystoreFile $Script:KeystorePath -StorePassword $storePass
        Write-PublicCertificateInfoFiles -Meta $meta
        Write-Host ""
        Write-Host "=== Impronta Certificato Release ===" -ForegroundColor Cyan
        Write-Host "SHA-256 (colon) : $($meta.Sha256Colon)" -ForegroundColor Green
        Write-Host "SHA-256 (hex)   : $($meta.Sha256Hex)" -ForegroundColor Green
        Write-Host "Algoritmo       : $($meta.Algorithm)"
        Write-Host "Validita        : $($meta.Validity)"
        Write-Host "Subject         : $($meta.Subject)"
        Write-Host "Seriale         : $($meta.SerialNumber)"
        Write-Host "Aggiornato file : $($Script:RepoCertInfoPath)"
    } finally {
        if ($storePass) { $storePass.Dispose() }
    }
}

function Action-CreateEncryptedBackup {
    if (-not (Test-Path -LiteralPath $Script:KeystorePath)) {
        throw "Keystore originale non trovato in $($Script:KeystorePath)."
    }
    Ensure-SigningDirectories

    Write-Host ""
    Write-Host "Creazione backup cifrato AES-256-CBC + HMAC-SHA256" -ForegroundColor Cyan
    $backupPass = Read-ConfirmedPassword -Prompt "Password DEDICATA per il backup cifrato (min 10 caratteri)" -MinLength 10
    try {
        $stamp = (Get-Date).ToString('yyyyMMdd_HHmmss')
        $primaryEnc = Join-Path $Script:BackupDir "my-upload-key_backup_$stamp.jks.enc"

        Protect-KeystoreBackup -SourceJksPath $Script:KeystorePath -DestinationEncPath $primaryEnc -BackupPassword $backupPass

        # Verifica integrita' immediata decifrando in memoria
        $origSha = Get-FileSha256Hex -Path $Script:KeystorePath
        $decrypted = Unprotect-KeystoreBackup -BackupEncPath $primaryEnc -BackupPassword $backupPass
        $decSha = Get-BytesSha256Hex -Data $decrypted
        [Array]::Clear($decrypted, 0, $decrypted.Length)
        if ($origSha -ne $decSha) {
            Remove-Item -LiteralPath $primaryEnc -Force -ErrorAction SilentlyContinue
            throw "Verifica integrita' fallita dopo la creazione del backup!"
        }

        Write-Host "Backup primario creato e verificato: $primaryEnc" -ForegroundColor Green
        Write-Host "SHA-256 contenuto JKS verificato: $decSha" -ForegroundColor Cyan

        Write-Host ""
        $secondDir = Read-Host "Inserisci percorso della SECONDA copia su supporto separato (es. E:\PantryMasterBackup) oppure Invio per saltare"
        if (-not [string]::IsNullOrWhiteSpace($secondDir)) {
            if (-not (Test-Path -LiteralPath $secondDir)) {
                New-Item -ItemType Directory -Path $secondDir -Force | Out-Null
            }
            $secondEnc = Join-Path $secondDir "my-upload-key_backup_$stamp.jks.enc"
            Copy-Item -LiteralPath $primaryEnc -Destination $secondEnc -Force
            $dec2 = Unprotect-KeystoreBackup -BackupEncPath $secondEnc -BackupPassword $backupPass
            $dec2Sha = Get-BytesSha256Hex -Data $dec2
            [Array]::Clear($dec2, 0, $dec2.Length)
            if ($dec2Sha -ne $origSha) {
                throw "Verifica integrita' della seconda copia fallita!"
            }
            if (Test-Path -LiteralPath $Script:LocalCertInfoPath) {
                Copy-Item -LiteralPath $Script:LocalCertInfoPath -Destination (Join-Path $secondDir 'RELEASE_CERTIFICATE_INFO.txt') -Force
            }
            Write-Host "Seconda copia salvata e verificata: $secondEnc" -ForegroundColor Green
        } else {
            Write-Host "Promemoria: copia manualmente '$primaryEnc' anche su una chiavetta USB o supporto esterno offline." -ForegroundColor Yellow
        }
    } finally {
        if ($backupPass) { $backupPass.Dispose() }
    }
}

function Action-VerifyOrRestoreBackup {
    Ensure-SigningDirectories
    $backups = @(Get-ChildItem -LiteralPath $Script:BackupDir -Filter '*.jks.enc' -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending)
    Write-Host ""
    if ($backups.Count -gt 0) {
        Write-Host "Backup trovati in $($Script:BackupDir):" -ForegroundColor Cyan
        for ($i = 0; $i -lt $backups.Count; $i++) {
            Write-Host "  [$($i + 1)] $($backups[$i].Name) ($($backups[$i].LastWriteTime))"
        }
    }
    $sel = Read-Host "Inserisci numero elenco oppure percorso completo del file .jks.enc"
    $targetBackup = $null
    $idx = 0
    if ([int]::TryParse($sel, [ref]$idx) -and $idx -ge 1 -and $idx -le $backups.Count) {
        $targetBackup = $backups[$idx - 1].FullName
    } elseif (Test-Path -LiteralPath $sel) {
        $targetBackup = $sel
    } else {
        throw "Backup non trovato."
    }

    $backupPass = Read-Host -Prompt "Inserisci la password del backup" -AsSecureString
    $decrypted = $null
    try {
        $decrypted = Unprotect-KeystoreBackup -BackupEncPath $targetBackup -BackupPassword $backupPass
        $decSha = Get-BytesSha256Hex -Data $decrypted
        Write-Host "INTEGRITA' VERIFICATA! File JKS integro (SHA-256 binario: $decSha)" -ForegroundColor Green

        if (Test-Path -LiteralPath $Script:KeystorePath) {
            $curSha = Get-FileSha256Hex -Path $Script:KeystorePath
            if ($curSha -eq $decSha) {
                Write-Host "Il backup corrisponde byte-per-byte al keystore attualmente attivo." -ForegroundColor Cyan
            }
        }

        $doRestore = Read-Host "Vuoi ripristinare questo backup in $($Script:KeystorePath)? [s/N]"
        if ($doRestore -match '^(s|si|y|yes)$') {
            if (Test-Path -LiteralPath $Script:KeystorePath) {
                $safetyCopy = "$($Script:KeystorePath).pre-restore_$(Get-Date -Format 'yyyyMMdd_HHmmss')"
                Copy-Item -LiteralPath $Script:KeystorePath -Destination $safetyCopy -Force
                Write-Host "Copia di sicurezza salvata prima del ripristino: $safetyCopy" -ForegroundColor Yellow
                $confirmRestore = Read-Host "Digita 'RIPRISTINA' per confermare il ripristino"
                if ($confirmRestore -ne 'RIPRISTINA') {
                    Write-Host "Ripristino annullato." -ForegroundColor Yellow
                    return
                }
            }
            [System.IO.File]::WriteAllBytes($Script:KeystorePath, $decrypted)
            Write-Host "Keystore ripristinato con successo in $($Script:KeystorePath)." -ForegroundColor Green
        }
    } finally {
        if ($decrypted) { [Array]::Clear($decrypted, 0, $decrypted.Length) }
        if ($backupPass) { $backupPass.Dispose() }
    }
}

function Action-VerifyApkInteractive {
    $defaultApk = $null
    if (Test-Path -LiteralPath $Script:DistDir) {
        $latest = Get-ChildItem -LiteralPath $Script:DistDir -Filter '*.apk' -ErrorAction SilentlyContinue | Sort-Object LastWriteTime -Descending | Select-Object -First 1
        if ($latest) { $defaultApk = $latest.FullName }
    }
    $fallbackApk = Join-Path $Script:ProjectRoot 'app\build\outputs\apk\release\app-release.apk'
    if (-not $defaultApk -and (Test-Path -LiteralPath $fallbackApk)) {
        $defaultApk = $fallbackApk
    }

    $prompt = if ($defaultApk) { "Percorso APK da verificare [Invio = $defaultApk]" } else { "Percorso completo file APK" }
    $chosen = Read-Host $prompt
    $apkPath = if ([string]::IsNullOrWhiteSpace($chosen)) { $defaultApk } else { $chosen }
    if (-not $apkPath) {
        throw "Nessun percorso APK specificato."
    }
    Verify-ApkArtifact -ApkPath $apkPath
}

function Action-FullDiagnostics {
    Write-Host ""
    Write-Host "================ DIAGNOSTICA COMPLETA PANTRYMASTER ================" -ForegroundColor Cyan

    $tools = Get-JavaTooling
    Write-Host ("Java           : " + $(if ($tools.Java) { $tools.Java } else { 'MANCANTE' }))
    Write-Host ("Keytool        : " + $(if ($tools.Keytool) { $tools.Keytool } else { 'MANCANTE' }))

    $sdk = Get-AndroidSdkPath
    $apksigner = Get-AndroidBuildTool -ToolBaseName 'apksigner'
    $aapt2 = Get-AndroidBuildTool -ToolBaseName 'aapt2'
    $gradleRunner = Get-GradleRunner
    Write-Host ("Android SDK    : " + $(if ($sdk) { $sdk } else { 'MANCANTE' }))
    Write-Host ("apksigner      : " + $(if ($apksigner) { $apksigner } else { 'MANCANTE' }))
    Write-Host ("aapt2          : " + $(if ($aapt2) { $aapt2 } else { 'MANCANTE' }))
    Write-Host ("Gradle runner  : " + $(if ($gradleRunner) { $gradleRunner } else { 'MANCANTE' }))

    $ksExists = Test-Path -LiteralPath $Script:KeystorePath
    Write-Host ("Keystore locale: " + $(if ($ksExists) { "PRESENTE ($($Script:KeystorePath))" } else { "NON ANCORA CREATO ($($Script:KeystorePath))" }))

    $bkpCount = @(Get-ChildItem -LiteralPath $Script:BackupDir -Filter '*.jks.enc' -ErrorAction SilentlyContinue).Count
    Write-Host ("Backup cifrati : $bkpCount in $($Script:BackupDir)")

    if (Test-Path -LiteralPath $Script:VersionPropsPath) {
        $vp = Read-VersionProperties
        Write-Host ("Versione       : v$($vp['VERSION_MAJOR']).$($vp['VERSION_MINOR']).$($vp['VERSION_PATCH']) (code $($vp['VERSION_CODE']), repo=$($vp['GITHUB_REPO']))")
    }

    $buildGradlePath = Join-Path $Script:ProjectRoot 'app\build.gradle.kts'
    if (Test-Path -LiteralPath $buildGradlePath) {
        $bg = Get-Content -LiteralPath $buildGradlePath -Raw
        $hasAppId = $bg -match [Regex]::Escape($Script:ExpectedAppId)
        $hasVerifyTask = $bg -match 'verifyReleaseSigning'
        Write-Host ("App ID corretto: $hasAppId ($($Script:ExpectedAppId))")
        Write-Host ("Check Release  : $hasVerifyTask (verifyReleaseSigning)")
    }

    $git = Find-Executable 'git'
    if ($git) {
        Push-Location $Script:ProjectRoot
        try {
            $trackedSecrets = & $git ls-files '*.jks' '*.keystore' '*.p12' '*.pfx' '*.enc' '.env' 2>$null
            if ($trackedSecrets) {
                Write-Host "ALLARME GIT: file sensibili tracciati nel repo: $($trackedSecrets -join ', ')" -ForegroundColor Red
            } else {
                Write-Host "Sicurezza Git  : OK (nessun keystore, backup cifrato o .env tracciato)" -ForegroundColor Green
            }
        } finally {
            Pop-Location
        }
    }
    Write-Host "===================================================================" -ForegroundColor Cyan
}

# ==============================================================================
# LOOP MENU PRINCIPALE
# ==============================================================================
while ($true) {
    Write-Host ""
    Write-Host "========================================================" -ForegroundColor Cyan
    Write-Host "  PantryMaster Release Manager ($($Script:ExpectedAppId))" -ForegroundColor Cyan
    Write-Host "========================================================" -ForegroundColor Cyan
    Write-Host "  Keystore locale: $($Script:KeystorePath)"
    Write-Host "--------------------------------------------------------"
    Write-Host "  1. Crea nuova firma Release"
    Write-Host "  2. Verifica keystore"
    Write-Host "  3. Visualizza impronta SHA-256"
    Write-Host "  4. Crea backup cifrato"
    Write-Host "  5. Verifica backup"
    Write-Host "  6. Compila APK Release"
    Write-Host "  7. Compila AAB Release"
    Write-Host "  8. Verifica firma APK"
    Write-Host "  9. Diagnostica completa"
    Write-Host " 10. Esci"
    Write-Host "--------------------------------------------------------"

    $choice = Read-Host "Seleziona un'opzione [1-10]"
    try {
        switch ($choice.Trim()) {
            '1'  { Action-CreateKeystore }
            '2'  { Action-VerifyKeystore }
            '3'  { Action-ShowFingerprint }
            '4'  { Action-CreateEncryptedBackup }
            '5'  { Action-VerifyOrRestoreBackup }
            '6'  { Invoke-ReleaseGradleTask -TargetType 'APK' }
            '7'  { Invoke-ReleaseGradleTask -TargetType 'AAB' }
            '8'  { Action-VerifyApkInteractive }
            '9'  { Action-FullDiagnostics }
            '10' { Write-Host "Uscita."; exit 0 }
            default { Write-Host "Scelta non valida." -ForegroundColor Yellow }
        }
    } catch {
        Write-Host ""
        Write-Host "ERRORE: $($_.Exception.Message)" -ForegroundColor Red
    }
}
