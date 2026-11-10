# Arranca el servidor escuchando en todas las redes para que celulares y otras laptops puedan entrar.
# Uso:
#   .\iniciar-en-red.ps1
#   .\iniciar-en-red.ps1 -Navegador "http://192.168.1.50:5055"
param(
    # Origen (o lista separada por comas) que se permite por CORS, para probar desde el navegador del celular.
    [string]$Navegador = ""
)

$ErrorActionPreference = "Stop"
Set-Location -Path $PSScriptRoot

$env:BIND_ADDRESS = "0.0.0.0"
$puerto = if ($env:PORT) { $env:PORT } else { "8080" }

if ($Navegador) {
    $env:ORIGENES_PERMITIDOS = $Navegador
    Write-Host "Navegador permitido (ORIGENES_PERMITIDOS): $Navegador"
}

Write-Host ""
Write-Host "RECUERDA: permite el puerto $puerto en el Firewall de Windows o los celulares no podran conectarse." -ForegroundColor Yellow
Write-Host "  Como administrador:" -ForegroundColor Yellow
Write-Host "  New-NetFirewallRule -DisplayName `"Brilliant $puerto`" -Direction Inbound -Protocol TCP -LocalPort $puerto -Action Allow -Profile Private" -ForegroundColor Yellow
Write-Host "  (la red Wi-Fi debe estar marcada como privada)" -ForegroundColor Yellow
Write-Host ""

# Se usa el jar solo si es más nuevo que el código; si no, se arrancaría una versión vieja.
$jar = Join-Path $PSScriptRoot "target\servidor-cuentas.jar"
$usarJar = $false
if (Test-Path $jar) {
    $fechaJar = (Get-Item $jar).LastWriteTime
    $ultimoCambio = Get-ChildItem -Path (Join-Path $PSScriptRoot "src\main"), (Join-Path $PSScriptRoot "pom.xml") -Recurse -File |
        Sort-Object LastWriteTime -Descending | Select-Object -First 1
    if ($ultimoCambio.LastWriteTime -le $fechaJar) {
        $usarJar = $true
    } else {
        Write-Host "El jar de target\ es mas viejo que el codigo ($($ultimoCambio.Name)); se ignora."
    }
}

if ($usarJar) {
    Write-Host "Arrancando con el jar: $jar"
    & java -jar $jar
} else {
    Write-Host "Arrancando con .\mvnw.cmd spring-boot:run"
    & (Join-Path $PSScriptRoot "mvnw.cmd") spring-boot:run
}
exit $LASTEXITCODE
