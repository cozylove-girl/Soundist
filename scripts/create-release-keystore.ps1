param(
    [string]$KeyAlias = "soundist-release"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$keystore = Join-Path $root "soundist-release.jks"
$properties = Join-Path $root "signing.properties"
if ((Test-Path -LiteralPath $keystore) -or (Test-Path -LiteralPath $properties)) {
    throw "Release signing files already exist; refusing to overwrite them."
}

$alphabet = "abcdefghijkmnopqrstuvwxyzABCDEFGHJKLMNPQRSTUVWXYZ23456789!@#%_-"
$bytes = New-Object byte[] 32
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$password = -join ($bytes | ForEach-Object { $alphabet[$_ % $alphabet.Length] })
$keytool = (Get-Command keytool -ErrorAction Stop).Source
& $keytool -genkeypair -v -keystore $keystore -storepass $password -keypass $password `
    -alias $KeyAlias -keyalg RSA -keysize 4096 -validity 10000 `
    -dname "CN=Soundist Android Release, OU=Mobile, O=Soundist, L=Shanghai, ST=Shanghai, C=CN"
if ($LASTEXITCODE -ne 0) { throw "keytool failed with exit code $LASTEXITCODE" }

@(
    "storeFile=soundist-release.jks"
    "storePassword=$password"
    "keyAlias=$KeyAlias"
    "keyPassword=$password"
) | Set-Content -LiteralPath $properties -Encoding ASCII
Write-Host "Created $keystore and signing.properties. Back up both files securely; losing them prevents in-place app updates."
