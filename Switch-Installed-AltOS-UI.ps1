param(
    [string]$InstallDir = (Join-Path $env:ProgramFiles 'AltusMetrum'),
    [switch]$Restore,
    [switch]$Elevated
)

$ErrorActionPreference = 'Stop'
$log = Join-Path $env:TEMP 'AltOS-UI-Switch-result.txt'

try {
    if (-not $Elevated) {
        Remove-Item -LiteralPath $log -ErrorAction SilentlyContinue
        $identity = [Security.Principal.WindowsIdentity]::GetCurrent()
        $admin = New-Object Security.Principal.WindowsPrincipal -ArgumentList $identity
        if (-not $admin.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) {
            Write-Host 'Please approve the Windows administrator prompt to update the installed AltOS.'
            $arguments = '-NoProfile -ExecutionPolicy Bypass -File "{0}" -InstallDir "{1}" -Elevated' -f $PSCommandPath, $InstallDir
            if ($Restore) { $arguments += ' -Restore' }
            $process = Start-Process -FilePath (Join-Path $PSHOME 'powershell.exe') -Verb RunAs -ArgumentList $arguments -Wait -PassThru
            if (Test-Path -LiteralPath $log) { Get-Content -LiteralPath $log }
            exit $process.ExitCode
        }
    }

    $installed = Join-Path $InstallDir 'altosui-fat.jar'
    $backup = Join-Path $InstallDir 'altosui-fat.jar.original-backup'
    $original = Join-Path $PSScriptRoot 'altosui-fat.jar'
    $redesigned = Join-Path $PSScriptRoot 'altosui-redesigned.jar'

    if (-not (Test-Path -LiteralPath $installed -PathType Leaf)) {
        throw "AltOS was not found at $installed. Check the installation folder."
    }
    if (-not (Test-Path -LiteralPath $original -PathType Leaf) -or
        -not (Test-Path -LiteralPath $redesigned -PathType Leaf)) {
        throw 'Keep this script in the extracted AltOS-Modern-UI folder with both JAR files.'
    }
    if (-not (Test-Path -LiteralPath (Join-Path $InstallDir 'altoslib_14.jar') -PathType Leaf) -or
        -not (Test-Path -LiteralPath (Join-Path $InstallDir 'altosuilib_14.jar') -PathType Leaf)) {
        throw 'This installer is for AltOS 1.9.22 with the version 14 desktop libraries.'
    }

    $originalHash = (Get-FileHash -LiteralPath $original -Algorithm SHA256).Hash
    $newHash = (Get-FileHash -LiteralPath $redesigned -Algorithm SHA256).Hash
    $installedHash = (Get-FileHash -LiteralPath $installed -Algorithm SHA256).Hash

    if ($installedHash -ne $originalHash -and $installedHash -ne $newHash) {
        Add-Type -AssemblyName System.IO.Compression.FileSystem
        $archive = [System.IO.Compression.ZipFile]::OpenRead($installed)
        try {
            if ($null -eq $archive.GetEntry('altosui/AltosDesktopTheme.class')) {
                throw 'The installed JAR is neither original AltOS nor an earlier redesigned UI. Nothing was changed.'
            }
        } finally { $archive.Dispose() }
    }

    if ($Restore) {
        if (-not (Test-Path -LiteralPath $backup -PathType Leaf)) {
            throw 'No original backup was found. The installed AltOS file was left unchanged.'
        }
        if ((Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash -ne $originalHash) {
            throw 'The backup does not match the original AltOS 1.9.22 JAR. Nothing was changed.'
        }
        if ($installedHash -eq $originalHash) {
            $result = 'The original AltOS UI is already installed.'
        } else {
            $tempFile = Join-Path $InstallDir 'altosui-fat.jar.restoring'
            $swapBackup = Join-Path $InstallDir ('altosui-fat.jar.swap-' + [guid]::NewGuid().ToString('N'))
            try {
                Copy-Item -LiteralPath $backup -Destination $tempFile -Force
                [System.IO.File]::Replace($tempFile, $installed, $swapBackup)
            } finally {
                Remove-Item -LiteralPath $tempFile -ErrorAction SilentlyContinue
            }
            if ((Get-FileHash -LiteralPath $installed -Algorithm SHA256).Hash -ne $originalHash) {
                throw 'Restoring the original AltOS JAR could not be verified.'
            }
            Remove-Item -LiteralPath $swapBackup -ErrorAction SilentlyContinue
            $result = 'Original AltOS UI restored. Open your usual AltOS shortcut.'
        }
    } else {
        if (Test-Path -LiteralPath $backup -PathType Leaf) {
            if ((Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash -ne $originalHash) {
                throw 'An existing backup does not match this AltOS installation. Nothing was changed.'
            }
        } elseif ($installedHash -eq $originalHash) {
            Copy-Item -LiteralPath $installed -Destination $backup
            if ((Get-FileHash -LiteralPath $backup -Algorithm SHA256).Hash -ne $originalHash) {
                throw 'The original JAR backup could not be verified. Nothing was changed.'
            }
        } else {
            throw 'The installed AltOS JAR differs from the original and has no backup. Nothing was changed.'
        }

        if ($installedHash -eq $newHash) {
            $result = 'The redesigned UI is already installed. Open your usual AltOS shortcut.'
        } else {
            $tempFile = Join-Path $InstallDir 'altosui-fat.jar.installing'
            $swapBackup = Join-Path $InstallDir ('altosui-fat.jar.swap-' + [guid]::NewGuid().ToString('N'))
            try {
                Copy-Item -LiteralPath $redesigned -Destination $tempFile -Force
                if ((Get-FileHash -LiteralPath $tempFile -Algorithm SHA256).Hash -ne $newHash) {
                    throw 'The redesigned JAR could not be copied completely.'
                }
                [System.IO.File]::Replace($tempFile, $installed, $swapBackup)
            } finally {
                Remove-Item -LiteralPath $tempFile -ErrorAction SilentlyContinue
            }
            if ((Get-FileHash -LiteralPath $installed -Algorithm SHA256).Hash -ne $newHash) {
                throw 'The installed JAR could not be verified. The original backup is still available.'
            }
            Remove-Item -LiteralPath $swapBackup -ErrorAction SilentlyContinue
            $result = 'Redesigned UI installed. Open your usual AltOS shortcut.'
        }
    }

    $result | Set-Content -LiteralPath $log
    if (-not $Elevated) { Write-Host $result }
    exit 0
} catch {
    $message = 'AltOS UI change failed: ' + $_.Exception.Message
    $message | Set-Content -LiteralPath $log
    Write-Host $message
    exit 1
}
