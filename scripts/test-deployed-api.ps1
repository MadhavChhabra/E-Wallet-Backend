# End-to-end smoke test for the deployed E-Wallet backend.
# Usage: .\scripts\test-deployed-api.ps1
#        .\scripts\test-deployed-api.ps1 -BaseUrl https://springpay.duckdns.org

param(
    [string]$BaseUrl = "https://springpay.duckdns.org"
)

$Api = "$BaseUrl/api/v1"
$script:Results = @()

function Test-Api {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Url,
        [object]$Body = $null,
        [string]$AuthToken = $null,
        [int[]]$ExpectStatus = @(200)
    )

    $headers = @{
        "Content-Type" = "application/json"
        "Accept"       = "application/json"
    }
    if ($AuthToken) { $headers["Authorization"] = "Bearer $AuthToken" }

    try {
        $params = @{
            Uri             = $Url
            Method          = $Method
            Headers         = $headers
            UseBasicParsing = $true
            ErrorAction     = "Stop"
        }
        if ($null -ne $Body) {
            $params.Body = ($Body | ConvertTo-Json -Depth 6)
        }

        $resp = Invoke-WebRequest @params
        $content = $resp.Content | ConvertFrom-Json
        $pass = $ExpectStatus -contains $resp.StatusCode
        $script:Results += [PSCustomObject]@{
            Test   = $Name
            HTTP   = $resp.StatusCode
            Pass   = $pass
            Detail = $content.message
        }
        return $content
    }
    catch {
        $status = if ($_.Exception.Response) { [int]$_.Exception.Response.StatusCode } else { 0 }
        $msg = $_.Exception.Message
        if ($_.Exception.Response) {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $errBody = $reader.ReadToEnd()
            try {
                $parsed = $errBody | ConvertFrom-Json
                $msg = $parsed.message
                if ($parsed.errorCode) { $msg = "$msg ($($parsed.errorCode))" }
            }
            catch { }
        }
        $pass = $ExpectStatus -contains $status
        $script:Results += [PSCustomObject]@{
            Test   = $Name
            HTTP   = $status
            Pass   = $pass
            Detail = $msg
        }
        return $null
    }
}

function Invoke-WithRetry {
    param([scriptblock]$Action, [int]$MaxAttempts = 3)
    for ($i = 1; $i -le $MaxAttempts; $i++) {
        try { return & $Action } catch {
            if ($i -eq $MaxAttempts) { throw }
            Start-Sleep -Seconds 2
        }
    }
}

Write-Host "Testing backend at $BaseUrl" -ForegroundColor Cyan

$health = Invoke-WithRetry { Invoke-RestMethod "$BaseUrl/actuator/health" }
Write-Host "Health: $($health.status)"

$demo = Invoke-WithRetry { Test-Api "Demo login" POST "$Api/auth/demo" @{} $null @(200) }
$token = $demo.data.token
$userId = $demo.data.id
$iban = $null

$ts = Get-Date -Format "MMddHHmmss"
$signupUser = "t$ts"
$signupEmail = "t$ts@test.com"

Test-Api "Signup" POST "$Api/auth/signup" @{
    firstName = "Test"
    lastName  = "User"
    username  = $signupUser
    email     = $signupEmail
    password  = "TestPass123"
    roles     = @("ROLE_USER")
} $null @(201) | Out-Null

$login = Test-Api "Login" POST "$Api/auth/login" @{
    username = $signupUser
    password = "TestPass123"
} $null @(200)

Test-Api "Refresh token" POST "$Api/auth/refresh-token" $null $demo.data.refreshToken @(200) | Out-Null
Test-Api "Users /me" GET "$Api/users/me" $null $token @(200) | Out-Null

$accounts = Test-Api "List bank accounts" GET "$Api/bank-accounts" $null $token @(200)
if ($accounts.data.content) {
    $iban = $accounts.data.content[0].iban
    Test-Api "Get account by IBAN" GET "$Api/bank-accounts/iban/$iban" $null $token @(200) | Out-Null

    if ($accounts.data.content.Count -ge 2) {
        $from = $accounts.data.content[0].iban
        $to = $accounts.data.content[1].iban
        Test-Api "Transfer" POST "$Api/bank-accounts/transfer" @{
            fromBankAccountIban = $from
            toBankAccountIban   = $to
            amount              = 1
            description         = "API smoke test"
            typeId              = 1
        } $token @(201) | Out-Null
    }
}

if ($iban) {
    Test-Api "Add funds" POST "$Api/bank-accounts/add-funds" @{
        fromBankAccountIban = $iban
        toBankAccountIban   = $iban
        amount              = 10
        description         = "Deposit"
        typeId              = 2
    } $token @(201) | Out-Null

    Test-Api "Withdraw" POST "$Api/bank-accounts/withdraw-funds" @{
        fromBankAccountIban = $iban
        toBankAccountIban   = $iban
        amount              = 1
        description         = "Withdraw"
        typeId              = 3
    } $token @(201) | Out-Null
}

Test-Api "Transactions (current user)" GET "$Api/transactions" $null $token @(200) | Out-Null
Test-Api "Transactions (by user id)" GET "$Api/transactions/users/$userId" $null $token @(200) | Out-Null
Test-Api "List cards" GET "$Api/cards" $null $token @(200) | Out-Null
Test-Api "Create card" POST "$Api/cards" @{
    cardHolderName = "Api Test"
    cardNumber     = "4111111111111111"
    expiryDate     = "12/28"
    cvv            = "123"
} $token @(201) | Out-Null

if ($iban) {
    $order = Test-Api "Razorpay order" POST "$Api/payments/razorpay/order" @{
        amount            = 100
        toBankAccountIban = $iban
    } $token @(201)
    if ($order) {
        Write-Host "Razorpay order: $($order.data.orderId)" -ForegroundColor DarkGray
    }
}

Test-Api "Google OAuth (invalid token)" POST "$Api/auth/oauth/google" @{ idToken = "invalid" } $null @(400, 401) | Out-Null
Test-Api "Password reset request" POST "$Api/auth/password-reset/request" @{ email = $signupEmail } $null @(200) | Out-Null
Test-Api "Logout" POST "$Api/auth/logout" $null $token @(200) | Out-Null

Write-Host ""
$script:Results | Format-Table -AutoSize
$passed = ($script:Results | Where-Object Pass).Count
$total = $script:Results.Count
Write-Host "Passed $passed / $total" -ForegroundColor $(if ($passed -eq $total) { "Green" } else { "Yellow" })

if ($passed -ne $total) { exit 1 }
