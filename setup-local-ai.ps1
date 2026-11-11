$ErrorActionPreference = 'Stop'

$aiDirectory = Join-Path $PSScriptRoot 'backend\target\local-ai'
New-Item -ItemType Directory -Force -Path $aiDirectory | Out-Null

$downloads = @(
    @{
        Url = 'https://github.com/mozilla-ai/llamafile/releases/download/0.10.6/llamafile-0.10.6'
        Path = Join-Path $aiDirectory 'llamafile.exe'
        Size = 368094430L
        Sha256 = 'd579f61dcd3a306f518e6d90e599d77793ed5f09543023d09c96ad35fcfa63f0'
    },
    @{
        Url = 'https://huggingface.co/Qwen/Qwen2.5-0.5B-Instruct-GGUF/resolve/main/qwen2.5-0.5b-instruct-q4_k_m.gguf'
        Path = Join-Path $aiDirectory 'qwen2.5-0.5b-instruct-q4_k_m.gguf'
        Size = 491400032L
    }
)

foreach ($download in $downloads) {
    $valid = Test-Path $download.Path
    if ($valid) {
        $valid = (Get-Item $download.Path).Length -eq $download.Size
    }
    if ($valid -and $download.Sha256) {
        $valid = (Get-FileHash $download.Path -Algorithm SHA256).Hash -eq $download.Sha256
    }
    if ($valid) {
        Write-Output "Already downloaded and verified: $($download.Path)"
        continue
    }

    Write-Output "Downloading $($download.Url)"
    & curl.exe --fail --silent --show-error --location --retry 5 --retry-all-errors --connect-timeout 30 --continue-at - --output $download.Path $download.Url
    if ($LASTEXITCODE -ne 0) {
        throw "Download failed with exit code $LASTEXITCODE. Run this script again to resume it."
    }

    $actualSize = (Get-Item $download.Path).Length
    if ($actualSize -ne $download.Size) {
        throw "Download size check failed for $($download.Path): expected $($download.Size) bytes, got $actualSize. Run this script again to resume it."
    }
    if ($download.Sha256) {
        $actualHash = (Get-FileHash $download.Path -Algorithm SHA256).Hash
        if ($actualHash -ne $download.Sha256) {
            throw "SHA-256 check failed for $($download.Path). Delete this specific file and run the setup script again."
        }
    }
    Write-Output "Verified: $($download.Path)"
}

Write-Output 'Free local AI runtime and Qwen model are ready.'
