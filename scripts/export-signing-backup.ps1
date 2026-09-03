param(
    [Parameter(Mandatory)][string]$Destination,
    [string]$JavaHome = "$env:LOCALAPPDATA\AuroraPlay\tools\jdk-21.0.12.1+1"
)
$ErrorActionPreference = 'Stop'
$repository = [IO.Path]::GetFullPath((Split-Path $PSScriptRoot -Parent)).TrimEnd('\') + '\'
$output = [IO.Path]::GetFullPath($Destination)
if ($output.StartsWith($repository, [StringComparison]::OrdinalIgnoreCase)) { throw 'Choose a private destination outside the repository.' }
if (Test-Path -LiteralPath $output) { throw 'Choose a new folder; existing backups will not be overwritten.' }
$privateDirectory = Join-Path $env:LOCALAPPDATA 'AuroraPlay\signing'
$recovery = Read-Host 'Senha de recuperação (mínimo 12 caracteres; guarde separadamente)' -AsSecureString
if ($recovery.Length -lt 12) { throw 'Use at least 12 characters.' }
$confirm = Read-Host 'Confirme a senha de recuperação' -AsSecureString
$original = (Get-Content -LiteralPath (Join-Path $privateDirectory 'production.password.dpapi') -Raw).Trim() | ConvertTo-SecureString
$pointers = @()
try {
    foreach ($value in @($recovery, $confirm, $original)) { $pointers += [Runtime.InteropServices.Marshal]::SecureStringToBSTR($value) }
    $env:AURORAPLAY_RECOVERY_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointers[0])
    if ($env:AURORAPLAY_RECOVERY_PASSWORD -cne [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointers[1])) { throw 'Passwords do not match.' }
    $env:AURORAPLAY_SIGNING_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointers[2])
    New-Item -ItemType Directory -Path $output | Out-Null
    $keytool = Join-Path $JavaHome 'bin\keytool.exe'
    foreach ($kind in @('production', 'legacy')) {
        $source = if ($kind -eq 'production') { 'production.p12' } else { 'legacy-debug.keystore' }
        $alias = if ($kind -eq 'production') { 'auroraplay' } else { 'androiddebugkey' }
        $sourceOptions = if ($kind -eq 'production') { @('-srcstorepass:env', 'AURORAPLAY_SIGNING_PASSWORD', '-srckeypass:env', 'AURORAPLAY_SIGNING_PASSWORD') } else { @('-srcstorepass', 'android', '-srckeypass', 'android') }
        & $keytool -importkeystore -srckeystore (Join-Path $privateDirectory $source) -srcalias $alias @sourceOptions -destkeystore (Join-Path $output "$kind-recovery.p12") -deststoretype PKCS12 -deststorepass:env AURORAPLAY_RECOVERY_PASSWORD -destkeypass:env AURORAPLAY_RECOVERY_PASSWORD -noprompt
        if ($LASTEXITCODE -ne 0) { throw 'Backup export failed. Do not rely on an incomplete backup.' }
    }
    Copy-Item -LiteralPath (Join-Path $privateDirectory 'auroraplay.lineage'),(Join-Path $privateDirectory 'identity.json') -Destination $output
    'Both PKCS12 files use your recovery password. Preserve the lineage and identities. Never publish these files. Verify this backup on a trusted computer before deleting the original signing folder.' | Set-Content -LiteralPath (Join-Path $output 'RECOVERY.txt')
    Write-Output "Portable encrypted signing backup created: $output"
} finally {
    foreach ($pointer in $pointers) { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer) }
    foreach ($value in @($recovery, $confirm, $original)) { $value.Dispose() }
    Remove-Item Env:AURORAPLAY_SIGNING_PASSWORD,Env:AURORAPLAY_RECOVERY_PASSWORD -ErrorAction SilentlyContinue
}
