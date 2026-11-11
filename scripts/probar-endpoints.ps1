# Prueba los endpoints nuevos (SCRUM-24, 27, 31, 32) contra el backend desplegado.
# Uso:  powershell -ExecutionPolicy Bypass -File scripts\probar-endpoints.ps1
# La contrasena se pide oculta y no se guarda en ningun lado.

param(
    [string]$Api = "https://backend-ritech-f1oo.onrender.com",
    [string]$Email = "admin@ritech.co",
    [string]$Salida = "$PSScriptRoot\..\pruebas-endpoints"
)

$ErrorActionPreference = "Stop"
[Net.ServicePointManager]::SecurityProtocol = [Net.SecurityProtocolType]::Tls12
New-Item -ItemType Directory -Force $Salida | Out-Null
$Salida = (Resolve-Path $Salida).Path

function Llamar([string]$Ruta, [string]$Archivo = $null) {
    $url = "$Api$Ruta"
    try {
        $p = @{ Uri = $url; Headers = $script:Cabeceras; UseBasicParsing = $true; TimeoutSec = 120 }
        if ($Archivo) { $p.OutFile = Join-Path $Salida $Archivo; $p.PassThru = $true }
        $r = Invoke-WebRequest @p
        return @{ Codigo = [int]$r.StatusCode; Cuerpo = $(if ($Archivo) { "" } else { $r.Content }) }
    } catch {
        $resp = $_.Exception.Response
        if ($null -eq $resp) { return @{ Codigo = 0; Cuerpo = $_.Exception.Message } }
        $lector = New-Object System.IO.StreamReader($resp.GetResponseStream())
        return @{ Codigo = [int]$resp.StatusCode; Cuerpo = $lector.ReadToEnd() }
    }
}

$resultados = @()
function Anotar([string]$Prueba, $R, [string]$Archivo = "") {
    $ok = $R.Codigo -ge 200 -and $R.Codigo -lt 300
    $detalle = if ($ok) { $Archivo } else { $R.Cuerpo }
    if ($detalle.Length -gt 300) { $detalle = $detalle.Substring(0, 300) + "..." }
    $script:resultados += [pscustomobject]@{ Prueba = $Prueba; Codigo = $R.Codigo; OK = $(if ($ok) { "SI" } else { "NO" }); Detalle = $detalle }
    $color = if ($ok) { "Green" } else { "Red" }
    Write-Host ("[{0}] {1} -> {2}" -f $R.Codigo, $Prueba, $detalle) -ForegroundColor $color
}

# 1. Despertar el backend (plan gratis) y comprobar la base
Write-Host "Despertando el backend (puede tardar ~1 min)..." -ForegroundColor Cyan
$script:Cabeceras = @{}
$salud = Llamar "/salud"
Write-Host "Salud: $($salud.Cuerpo)"

# 2. Login
$seguro = Read-Host "Contrasena de $Email (SEED_PASSWORD de Render)" -AsSecureString
$bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($seguro)
try {
    $clave = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($bstr)
    $cuerpo = @{ email = $Email; password = $clave } | ConvertTo-Json
    $login = Invoke-RestMethod -Method Post -Uri "$Api/api/auth/login" -ContentType "application/json" -Body $cuerpo
} catch {
    Write-Host "No se pudo iniciar sesion: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
} finally {
    [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    $clave = $null; $cuerpo = $null
}
$script:Cabeceras = @{ Authorization = "Bearer $($login.token)" }
Write-Host "Sesion iniciada como $($login.usuario.email) ($($login.usuario.rol))" -ForegroundColor Green

# 3. SCRUM-31 tablero
$r = Llamar "/api/reportes/tablero"
Anotar "SCRUM-31 tablero" $r
if ($r.Codigo -eq 200) { $r.Cuerpo | Out-File -Encoding utf8 (Join-Path $Salida "tablero.json") }

# 4. SCRUM-32 exportaciones
foreach ($rep in "ordenes", "lotes", "envios") {
    Anotar "SCRUM-32 $rep xlsx" (Llamar "/api/reportes/$rep" "$rep.xlsx") "$rep.xlsx"
    Anotar "SCRUM-32 $rep csv" (Llamar "/api/reportes/$rep`?formato=csv" "$rep.csv") "$rep.csv"
}
$anio = (Get-Date).Year
Anotar "SCRUM-32 ordenes con fechas" (Llamar "/api/reportes/ordenes?formato=csv&desde=$anio-01-01&hasta=$anio-12-31" "ordenes_$anio.csv") "ordenes_$anio.csv"

# 5. SCRUM-27 factura y lista de empaque de las ordenes emitibles
$ordenes = (Llamar "/api/ordenes").Cuerpo | ConvertFrom-Json
$emitibles = @($ordenes | Where-Object { $_.estadoOrden -in "CONFIRMADA", "EN_TRANSITO", "ENTREGADA" })
Write-Host "Ordenes: $(@($ordenes).Count) en total, $($emitibles.Count) confirmadas o despues." -ForegroundColor Cyan
foreach ($o in $emitibles) {
    $n = $o.numeroOrden
    Anotar "SCRUM-27 factura $n" (Llamar "/api/ordenes/$($o.idOrden)/factura" "factura_$n.pdf") "factura_$n.pdf"
    Anotar "SCRUM-27 lista empaque $n" (Llamar "/api/ordenes/$($o.idOrden)/lista-empaque" "lista-empaque_$n.pdf") "lista-empaque_$n.pdf"
}
$borrador = @($ordenes | Where-Object { $_.estadoOrden -eq "BORRADOR" }) | Select-Object -First 1
if ($borrador) {
    $r = Llamar "/api/ordenes/$($borrador.idOrden)/factura"
    $r.Codigo = $(if ($r.Codigo -eq 422) { 200 } else { $r.Codigo })  # aqui lo correcto es que la rechace
    Anotar "SCRUM-27 rechaza factura de BORRADOR $($borrador.numeroOrden)" $r "422 esperado"
}

# 6. SCRUM-24 DDS de cada lote (422 = el lote no cumple los requisitos, se muestra el motivo)
$lotes = (Llamar "/api/lotes").Cuerpo | ConvertFrom-Json
foreach ($l in $lotes) {
    $c = $l.codigoLote
    $r = Llamar "/api/lotes/$($l.idLote)/dds" "DDS-$c.pdf"
    if ($r.Codigo -eq 422) {
        Write-Host "[422] SCRUM-24 DDS $c no se emite: $($r.Cuerpo)" -ForegroundColor Yellow
        continue
    }
    Anotar "SCRUM-24 DDS $c" $r "DDS-$c.pdf"
    if ($r.Codigo -eq 200) {
        Anotar "SCRUM-24 GeoJSON $c" (Llamar "/api/lotes/$($l.idLote)/dds/geojson" "DDS-$c.geojson") "DDS-$c.geojson"
    }
}
Get-ChildItem $Salida -Filter "DDS-*.pdf" | Where-Object { $_.Length -lt 1000 } | Remove-Item

# Resumen
Write-Host ""
$fallos = @($resultados | Where-Object { $_.OK -eq "NO" })
$resultados | Format-Table -AutoSize Prueba, Codigo, OK
Write-Host "Archivos en: $Salida" -ForegroundColor Cyan
if ($fallos.Count -eq 0) {
    Write-Host "Todo bien: $($resultados.Count) pruebas OK." -ForegroundColor Green
} else {
    Write-Host "$($fallos.Count) prueba(s) fallaron. Copia lo que sale en rojo y pasaselo a Claude." -ForegroundColor Red
}
