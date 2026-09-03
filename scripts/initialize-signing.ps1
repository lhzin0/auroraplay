param(
    [string]$JavaHome = 'C:\Program Files\Android\Android Studio\jbr',
    [string]$SdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
)
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = $JavaHome
$repository = Split-Path $PSScriptRoot -Parent
$privateDirectory = Join-Path $env:LOCALAPPDATA 'AuroraPlay\signing'
$signer = Join-Path $SdkRoot 'build-tools\36.0.0\apksigner.bat'
$keytool = Join-Path $JavaHome 'bin\keytool.exe'
$oldApk = Join-Path $repository 'website\downloads\AuroraPlay-1.32.0.apk'
$productionStore = Join-Path $privateDirectory 'production.p12'
$passwordFile = Join-Path $privateDirectory 'production.password.dpapi'
$legacyStore = Join-Path $privateDirectory 'legacy-debug.keystore'
$lineage = Join-Path $privateDirectory 'auroraplay.lineage'
if (Test-Path -LiteralPath (Join-Path $privateDirectory 'identity.json')) {
    Write-Output 'Signing identity already exists. It was not replaced.'
    exit 0
}
if (!(Test-Path -LiteralPath $oldApk)) { throw 'The original 1.32.0 APK is required to verify the migration identity.' }
New-Item -ItemType Directory -Path $privateDirectory -Force | Out-Null
# Only this Windows account and SYSTEM can read the private key material.
$sid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
$acl = Get-Acl -LiteralPath $privateDirectory
$alreadyPrivate = $acl.AreAccessRulesProtected -and @($acl.Access | Where-Object {
    $_.IdentityReference.Translate([System.Security.Principal.SecurityIdentifier]).Value -notin @($sid.Value, 'S-1-5-18')
}).Count -eq 0
if (!$alreadyPrivate) {
$acl.SetAccessRuleProtection($true, $false)
foreach ($existing in @($acl.Access)) { $acl.RemoveAccessRuleSpecific($existing) }
foreach ($principal in @($sid, [System.Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
    $rule = [System.Security.AccessControl.FileSystemAccessRule]::new($principal, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
    $acl.AddAccessRule($rule)
}
Set-Acl -LiteralPath $privateDirectory -AclObject $acl
}
if (!(Test-Path -LiteralPath $legacyStore)) {
    Copy-Item -LiteralPath (Join-Path $env:USERPROFILE '.android\debug.keystore') -Destination $legacyStore
}
if (!(Test-Path -LiteralPath $passwordFile)) {
    if (Test-Path -LiteralPath $productionStore) { throw 'Production key exists without its protected password. Restore the password; never replace the key.' }
    $random = [byte[]]::new(48)
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    try { $rng.GetBytes($random) } finally { $rng.Dispose() }
    $secret = ConvertTo-SecureString ([Convert]::ToBase64String($random)) -AsPlainText -Force
    $random.Clear()
    ConvertFrom-SecureString $secret | Set-Content -LiteralPath $passwordFile
}
$protected = (Get-Content -LiteralPath $passwordFile -Raw).Trim() | ConvertTo-SecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($protected)
try {
    $env:AURORAPLAY_SIGNING_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
    if (!(Test-Path -LiteralPath $productionStore)) {
        & $keytool -genkeypair -keystore $productionStore -storetype PKCS12 -alias auroraplay -keyalg RSA -keysize 3072 -sigalg SHA256withRSA -validity 10000 -dname 'CN=AuroraPlay' -storepass:env AURORAPLAY_SIGNING_PASSWORD -keypass:env AURORAPLAY_SIGNING_PASSWORD -noprompt
        if ($LASTEXITCODE -ne 0) { throw 'Key generation failed.' }
    }
    if (!(Test-Path -LiteralPath $lineage)) {
        & $signer rotate --out $lineage --old-signer --ks $legacyStore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --set-rollback false --new-signer --ks $productionStore --ks-key-alias auroraplay --ks-pass env:AURORAPLAY_SIGNING_PASSWORD --key-pass env:AURORAPLAY_SIGNING_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'Signing rotation failed.' }
    }
    # A temporary signed copy validates the complete lineage against the shipped APK.
    $validationApk = Join-Path $privateDirectory 'identity-check.apk'
    & $signer sign --out $validationApk --ks $legacyStore --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --next-signer --ks $productionStore --ks-key-alias auroraplay --ks-pass env:AURORAPLAY_SIGNING_PASSWORD --key-pass env:AURORAPLAY_SIGNING_PASSWORD --lineage $lineage --rotation-min-sdk-version 28 --v4-signing-enabled false $oldApk
    if ($LASTEXITCODE -ne 0) { throw 'Identity verification signing failed.' }
    function Get-CertificateDigest([string]$Apk, [int]$Api) {
        $output = & $signer verify --min-sdk-version $Api --max-sdk-version $Api --print-certs $Apk
        if ($LASTEXITCODE -ne 0) { throw 'APK signature verification failed.' }
        $match = [regex]::Match(($output -join "`n"), 'Signer #1 certificate SHA-256 digest: ([a-f0-9]+)')
        if (!$match.Success) { throw 'Certificate digest missing.' }
        return $match.Groups[1].Value
    }
    $legacyDigest = Get-CertificateDigest $oldApk 24
    if ((Get-CertificateDigest $validationApk 24) -ne $legacyDigest) { throw 'Legacy key does not match the distributed APK. Stop migration.' }
    $productionDigest = Get-CertificateDigest $validationApk 28
    if ($productionDigest -eq $legacyDigest) { throw 'Production key did not rotate.' }
    [ordered]@{ legacyCertificateSha256 = $legacyDigest; productionCertificateSha256 = $productionDigest; rotationMinSdk = 28; createdAt = (Get-Date).ToString('o') } |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $privateDirectory 'identity.json')
    Remove-Item -LiteralPath $validationApk
    Write-Output "Signing identity created and verified. Private files: $privateDirectory"
} finally {
    Remove-Item Env:AURORAPLAY_SIGNING_PASSWORD -ErrorAction SilentlyContinue
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
    $protected.Dispose()
}
