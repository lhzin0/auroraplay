param(
    [switch]$SkipBuild,
    [string]$JavaHome = "$env:LOCALAPPDATA\AuroraPlay\tools\jdk-21.0.12.1+1",
    [string]$SdkRoot = "$env:LOCALAPPDATA\Android\Sdk"
)
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = $JavaHome
$repository = Split-Path $PSScriptRoot -Parent
$privateDirectory = Join-Path $env:LOCALAPPDATA 'AuroraPlay\signing'
$identity = Get-Content -LiteralPath (Join-Path $privateDirectory 'identity.json') -Raw | ConvertFrom-Json
$signer = Join-Path $SdkRoot 'build-tools\36.0.0\apksigner.bat'
$buildConfig = Get-Content -LiteralPath (Join-Path $repository 'app\build.gradle.kts') -Raw
$version = [regex]::Match($buildConfig, 'versionName = "([0-9.]+)"').Groups[1].Value
$versionCode = [int][regex]::Match($buildConfig, 'versionCode = ([0-9]+)').Groups[1].Value
if (!$version -or !$versionCode) { throw 'Version missing.' }
Push-Location $repository
try {
    if (!$SkipBuild) {
        & .\gradlew.bat :app:assembleRelease
        if ($LASTEXITCODE -ne 0) { throw 'Release build failed.' }
    }
    $unsigned = Join-Path $repository 'app\build\outputs\apk\release\app-release-unsigned.apk'
    $outputDirectory = Join-Path $repository 'build\release'
    New-Item -ItemType Directory -Path $outputDirectory -Force | Out-Null
    $fileName = "AuroraPlay-$version.apk"
    $apk = Join-Path $outputDirectory $fileName
    $password = (Get-Content -LiteralPath (Join-Path $privateDirectory 'production.password.dpapi') -Raw).Trim() | ConvertTo-SecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($password)
    try {
        $env:AURORAPLAY_SIGNING_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer)
        & $signer sign --out $apk --ks (Join-Path $privateDirectory 'legacy-debug.keystore') --ks-key-alias androiddebugkey --ks-pass pass:android --key-pass pass:android --next-signer --ks (Join-Path $privateDirectory 'production.p12') --ks-key-alias auroraplay --ks-pass env:AURORAPLAY_SIGNING_PASSWORD --key-pass env:AURORAPLAY_SIGNING_PASSWORD --lineage (Join-Path $privateDirectory 'auroraplay.lineage') --rotation-min-sdk-version 28 --v4-signing-enabled false --debuggable-apk-permitted false $unsigned
        if ($LASTEXITCODE -ne 0) { throw 'Release signing failed.' }
    } finally {
        Remove-Item Env:AURORAPLAY_SIGNING_PASSWORD -ErrorAction SilentlyContinue
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer)
        $password.Dispose()
    }
    foreach ($api in @(24, 27, 28, 32, 33, 36)) {
        $result = & $signer verify --min-sdk-version $api --max-sdk-version $api --print-certs $apk
        if ($LASTEXITCODE -ne 0) { throw "Signature invalid for API $api" }
        $digest = [regex]::Match(($result -join "`n"), 'Signer #1 certificate SHA-256 digest: ([a-f0-9]+)').Groups[1].Value
        $expected = if ($api -lt 28) { $identity.legacyCertificateSha256 } else { $identity.productionCertificateSha256 }
        if ($digest -ne $expected) { throw "Unexpected signing identity for API $api" }
    }
    $hash = (Get-FileHash -LiteralPath $apk -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $fileName" | Set-Content -LiteralPath (Join-Path $outputDirectory 'SHA256SUMS.txt')
    [ordered]@{
        applicationId = 'com.auroraplay.iptv'; minSdk = 24
        version = $version; versionCode = $versionCode; publishedAt = (Get-Date -Format 'yyyy-MM-dd'); minAndroid = '7.0'
        fileName = $fileName; downloadUrl = "./downloads/$fileName"; sizeBytes = (Get-Item -LiteralPath $apk).Length; sha256 = $hash
        notes = @('Atualizações pelo GitHub: consulta diária e download automático no Wi-Fi.', 'Progresso nas notificações, cancelamento e instalação quando você escolher.', 'Verificação de integridade, versão, identificador e certificado do APK antes de instalar.', 'Backup protegido por senha e compatibilidade com os arquivos antigos.')
    } | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $outputDirectory 'release.json') -Encoding utf8
    Write-Output "Verified release: $apk"
    Write-Output "SHA-256: $hash"
} finally { Pop-Location }
