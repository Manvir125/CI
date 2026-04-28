param(
    [string]$HostName = "apikewan.chpcs.local",
    [int]$Port = 443,
    [string]$TruststorePath = "",
    [string]$TruststorePassword = "changeit",
    [string]$TruststoreType = "PKCS12",
    [string]$AliasPrefix = "apikewan-prod",
    [switch]$ReplaceExisting
)

$ErrorActionPreference = "Stop"

function Get-BestTlsProtocols {
    $protocol = [System.Security.Authentication.SslProtocols]::Tls12
    if ([enum]::GetNames([System.Security.Authentication.SslProtocols]) -contains "Tls13") {
        $protocol = $protocol -bor [System.Security.Authentication.SslProtocols]::Tls13
    }
    return $protocol
}

function Get-ServerCertificateChain {
    param(
        [string]$TargetHost,
        [int]$TargetPort
    )

    $capturedChain = $null
    $capturedCertificate = $null
    $callback = [System.Net.Security.RemoteCertificateValidationCallback]{
        param($sender, $certificate, $chain, $sslPolicyErrors)
        $script:capturedChain = $chain
        if ($certificate -ne $null) {
            $script:capturedCertificate = [System.Security.Cryptography.X509Certificates.X509Certificate2]::new($certificate)
        }
        return $true
    }

    $tcpClient = [System.Net.Sockets.TcpClient]::new()
    try {
        $tcpClient.Connect($TargetHost, $TargetPort)
        $sslStream = [System.Net.Security.SslStream]::new($tcpClient.GetStream(), $false, $callback)
        try {
            $protocols = Get-BestTlsProtocols

            if ("System.Net.Security.SslClientAuthenticationOptions" -as [type]) {
                $authOptions = [System.Net.Security.SslClientAuthenticationOptions]::new()
                $authOptions.TargetHost = $TargetHost
                $authOptions.EnabledSslProtocols = $protocols
                $authOptions.CertificateRevocationCheckMode = [System.Security.Cryptography.X509Certificates.X509RevocationMode]::NoCheck
                $sslStream.AuthenticateAsClient($authOptions)
            }
            else {
                $sslStream.AuthenticateAsClient(
                    $TargetHost,
                    $null,
                    $protocols,
                    $false
                )
            }

            if ($script:capturedChain -ne $null -and $script:capturedChain.ChainElements.Count -gt 0) {
                return @($script:capturedChain.ChainElements | ForEach-Object {
                    [System.Security.Cryptography.X509Certificates.X509Certificate2]::new($_.Certificate)
                })
            }

            if ($script:capturedCertificate -ne $null) {
                $rebuiltChain = [System.Security.Cryptography.X509Certificates.X509Chain]::new()
                $rebuiltChain.ChainPolicy.RevocationMode = [System.Security.Cryptography.X509Certificates.X509RevocationMode]::NoCheck
                $rebuiltChain.ChainPolicy.VerificationFlags = [System.Security.Cryptography.X509Certificates.X509VerificationFlags]::IgnoreWrongUsage
                [void]$rebuiltChain.Build($script:capturedCertificate)

                if ($rebuiltChain.ChainElements.Count -gt 0) {
                    return @($rebuiltChain.ChainElements | ForEach-Object {
                        [System.Security.Cryptography.X509Certificates.X509Certificate2]::new($_.Certificate)
                    })
                }
            }

            if ($sslStream.RemoteCertificate -ne $null) {
                return @([System.Security.Cryptography.X509Certificates.X509Certificate2]::new($sslStream.RemoteCertificate))
            }

            throw "No se pudo capturar la cadena TLS de $TargetHost`:$TargetPort. Verifica DNS, conectividad y que el servidor presente un certificado HTTPS."
        }
        finally {
            $sslStream.Dispose()
        }
    }
    catch {
        throw "No se pudo establecer el handshake TLS con $TargetHost`:$TargetPort. $($_.Exception.Message)"
    }
    finally {
        $tcpClient.Dispose()
    }
}

function Remove-AliasIfExists {
    param(
        [string]$StorePath,
        [string]$StorePassword,
        [string]$StoreType,
        [string]$Alias
    )

    & keytool -delete `
        -alias $Alias `
        -keystore $StorePath `
        -storetype $StoreType `
        -storepass $StorePassword 2>$null | Out-Null
}

if ([string]::IsNullOrWhiteSpace($TruststorePath)) {
    $TruststorePath = Join-Path $PSScriptRoot "..\src\main\resources\certs\truststore.p12"
}

$TruststorePath = [System.IO.Path]::GetFullPath($TruststorePath)

if (-not (Test-Path $TruststorePath)) {
    throw "No existe el truststore: $TruststorePath"
}

$outputDir = Join-Path $PSScriptRoot "..\secrets\apikewan-certs"
$outputDir = [System.IO.Path]::GetFullPath($outputDir)
New-Item -ItemType Directory -Force -Path $outputDir | Out-Null

$chain = Get-ServerCertificateChain -TargetHost $HostName -TargetPort $Port
Write-Host "Cadena TLS recuperada de $HostName`:$Port -> $($chain.Count) certificado(s)"

for ($i = 0; $i -lt $chain.Count; $i++) {
    $cert = $chain[$i]
    $alias = if ($i -eq 0) { $AliasPrefix } else { "$AliasPrefix-chain-$i" }
    $fileName = if ($i -eq 0) { "$AliasPrefix-leaf.cer" } else { "$AliasPrefix-chain-$i.cer" }
    $certPath = Join-Path $outputDir $fileName

    [System.IO.File]::WriteAllBytes(
        $certPath,
        $cert.Export([System.Security.Cryptography.X509Certificates.X509ContentType]::Cert)
    )

    if ($ReplaceExisting) {
        Remove-AliasIfExists `
            -StorePath $TruststorePath `
            -StorePassword $TruststorePassword `
            -StoreType $TruststoreType `
            -Alias $alias
    }

    & keytool -importcert `
        -alias $alias `
        -file $certPath `
        -keystore $TruststorePath `
        -storetype $TruststoreType `
        -storepass $TruststorePassword `
        -noprompt

    Write-Host "Importado alias '$alias' desde $certPath"
    Write-Host "  Subject: $($cert.Subject)"
    Write-Host "  Issuer : $($cert.Issuer)"
    Write-Host "  SHA256 : $($cert.GetCertHashString([System.Security.Cryptography.HashAlgorithmName]::SHA256))"
}

Write-Host ""
Write-Host "Validacion final del truststore:"
& keytool -list `
    -keystore $TruststorePath `
    -storetype $TruststoreType `
    -storepass $TruststorePassword
