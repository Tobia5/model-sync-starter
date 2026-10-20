$ErrorActionPreference = 'Stop'
$Root = Split-Path $PSScriptRoot -Parent
$Out = Join-Path $Root 'target/offline-core'
New-Item -ItemType Directory -Force $Out | Out-Null
$Sources = Get-ChildItem "$Root/model-sync-core/src/main/java","$Root/model-sync-testkit/src/main/java" -Recurse -Filter *.java
$SourceFile = Join-Path $Out 'sources.txt'
$Lines = @($Sources.FullName | ForEach-Object { '"' + $_.Replace('\','/') + '"' })
[System.IO.File]::WriteAllLines($SourceFile, $Lines, (New-Object System.Text.UTF8Encoding($false)))
& javac --release 21 -encoding UTF-8 -d "$Out/classes" "@$SourceFile"
if ($LASTEXITCODE -ne 0) { throw 'Core compilation failed' }
& java -cp "$Out/classes" io.modelsync.testkit.CoreContracts
if ($LASTEXITCODE -ne 0) { throw 'Core tests failed' }
