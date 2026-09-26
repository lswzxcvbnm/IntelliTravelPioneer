# ============================================================
# IntelliTravel Pioneer — 一键部署脚本 (Windows PowerShell)
# 用法:
#   .\deploy.ps1                    更新全部（前端 + 后端）
#   .\deploy.ps1 -FrontendOnly      仅更新前端
#   .\deploy.ps1 -BackendOnly       仅更新后端
#   .\deploy.ps1 -BackupFirst       部署前先备份服务器上的旧文件
#   .\deploy.ps1 -Server 1.2.3.4    指定服务器 IP（覆盖默认值）
# ============================================================

param(
    [switch]$FrontendOnly,
    [switch]$BackendOnly,
    [switch]$BackupFirst,
    [string]$Server = "218.244.153.122"
)

$ErrorActionPreference = "Stop"
$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$frontendDir = Join-Path $projectRoot "planclient"
$backendDir  = Join-Path $projectRoot "intellitravel-pioneer"
$timestamp   = Get-Date -Format "yyyyMMdd_HHmmss"
$sshUser     = "root"

# -----------------------------------------------------------
# SSH 连接
# -----------------------------------------------------------
$sshCommonArgs = @("-o", "ConnectTimeout=10", "-o", "StrictHostKeyChecking=accept-new")

function Invoke-Ssh {
    param([string]$Command)
    ssh @sshCommonArgs "${sshUser}@${Server}" $Command 2>&1
}

function Invoke-Scp {
    param([string]$Source, [string]$Dest)
    scp @sshCommonArgs -r $Source "${sshUser}@${Server}:${Dest}" 2>&1
}

function Test-SshConnection {
    Write-Step "检查 SSH 连接 ${sshUser}@${Server}"

    # 先测试密钥是否已配置（免密模式）
    $testResult = ssh -o ConnectTimeout=10 -o BatchMode=yes -o StrictHostKeyChecking=accept-new "${sshUser}@${Server}" "echo SSH-OK" 2>&1

    if ($testResult -match "SSH-OK") {
        Write-OK "SSH 密钥认证 — 无需密码"
        return $true
    }

    # 密钥未配置，引导用户设置
    Write-Warn "尚未配置 SSH 密钥，每次部署需要反复输入密码"
    Write-Host ""
    Write-Host "  ⚡ 建议配置 SSH 密钥免密登录（一劳永逸，只需一次）：" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "     # 第一步：生成密钥（一路回车）"
    Write-Host "     ssh-keygen -t rsa -b 4096"
    Write-Host ""
    Write-Host "     # 第二步：上传公钥到服务器（输入一次服务器密码）"
    Write-Host "     type `$env:USERPROFILE\.ssh\id_rsa.pub | ssh ${sshUser}@${Server} `"mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys`""
    Write-Host ""
    Write-Host "     # 第三步：验证免密登录"
    Write-Host "     ssh ${sshUser}@${Server} `"echo 免密登录成功`""
    Write-Host ""

    $choice = Read-Host "  是否现在配置密钥？(y/n)"
    if ($choice -eq "y" -or $choice -eq "Y") {
        Write-Host ""
        Write-Host "  执行 ssh-keygen …" -ForegroundColor Cyan
        ssh-keygen -t rsa -b 4096
        Write-Host ""
        Write-Host "  上传公钥到服务器（需要输入一次服务器密码）…" -ForegroundColor Cyan
        $pubKeyPath = "$env:USERPROFILE\.ssh\id_rsa.pub"
        if (Test-Path $pubKeyPath) {
            Get-Content $pubKeyPath | ssh "${sshUser}@${Server}" "mkdir -p ~/.ssh && cat >> ~/.ssh/authorized_keys && chmod 600 ~/.ssh/authorized_keys"
            if ($LASTEXITCODE -eq 0) {
                Write-OK "公钥已上传，测试免密连接…"
                $verify = ssh -o BatchMode=yes "${sshUser}@${Server}" "echo SSH-OK" 2>&1
                if ($verify -match "SSH-OK") {
                    Write-OK "免密登录配置成功！继续部署…"
                    return $true
                }
            }
        }
        Write-Err "密钥配置未成功，将使用密码模式继续（每次连接需输密码）"
    }

    Write-Warn "使用密码模式（每次 ssh/scp 均需输入密码）"
    return $true
}

# -----------------------------------------------------------
# 辅助函数
# -----------------------------------------------------------
function Write-Step { Write-Host "`n==== $args ====" -ForegroundColor Cyan }
function Write-OK   { Write-Host "  ✓ $args" -ForegroundColor Green }
function Write-Warn { Write-Host "  ⚠ $args" -ForegroundColor Yellow }
function Write-Err  { Write-Host "  ✗ $args" -ForegroundColor Red }

# -----------------------------------------------------------
# 检查后端是否已经在运行
# -----------------------------------------------------------
function Test-BackendRunning {
    Write-Step "检查后端服务状态"
    $result = ssh "${sshUser}@${Server}" "sudo systemctl is-active intellitravel 2>/dev/null || echo 'inactive'" 2>&1
    if ($result -match "active") {
        Write-OK "后端服务当前运行中"
        return $true
    } else {
        Write-Warn "后端服务未运行"
        return $false
    }
}

# -----------------------------------------------------------
# 备份服务器文件
# -----------------------------------------------------------
function Backup-ServerFiles {
    Write-Step "备份服务器上的旧文件 ($timestamp)"

    # 备份前端
    $cmd = "if [ -d /var/www/intellitravel/dist ]; then cp -r /var/www/intellitravel/dist /var/www/intellitravel/dist.bak.${timestamp}; echo 'frontend-backed-up'; else echo 'frontend-skipped'; fi"
    $result = Invoke-Ssh -Command $cmd
    if ($result -match "frontend-backed-up") { Write-OK "前端已备份到 dist.bak.${timestamp}" }
    elseif ($result -match "frontend-skipped") { Write-Warn "前端目录不存在，跳过" }

    # 备份后端 JAR
    $cmd = "if [ -f /opt/intellitravel/intellitravel-pioneer-0.0.1-SNAPSHOT.jar ]; then cp /opt/intellitravel/intellitravel-pioneer-0.0.1-SNAPSHOT.jar /opt/intellitravel/intellitravel-pioneer-0.0.1-SNAPSHOT.jar.bak.${timestamp}; echo 'backend-backed-up'; else echo 'backend-skipped'; fi"
    $result = Invoke-Ssh -Command $cmd
    if ($result -match "backend-backed-up") { Write-OK "后端 JAR 已备份" }
    elseif ($result -match "backend-skipped") { Write-Warn "后端 JAR 不存在，跳过" }
}

# -----------------------------------------------------------
# 构建并部署前端
# -----------------------------------------------------------
function Deploy-Frontend {
    Write-Step "构建前端"
    Push-Location $frontendDir
    try {
        Write-Host "  npm run build ..."
        $buildOutput = npm run build 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Err "前端构建失败！"
            Write-Host $buildOutput
            exit 1
        }
    } finally { Pop-Location }
    Write-OK "前端构建完成 ($(Join-Path $frontendDir 'dist'))"

    Write-Step "上传前端到服务器"
    Write-Host "  确保目标目录存在..."
    Invoke-Ssh -Command "mkdir -p /var/www/intellitravel/dist" | Out-Null

    Write-Host "  scp planclient/dist/* → /var/www/intellitravel/dist/ ..."
    $distPath = Join-Path $frontendDir "dist\*"
    Invoke-Scp -Source $distPath -Dest "/var/www/intellitravel/dist/"
    if ($LASTEXITCODE -ne 0) {
        Write-Err "前端上传失败！"
        exit 1
    }
    Write-OK "前端已部署"
}

# -----------------------------------------------------------
# 构建并部署后端
# -----------------------------------------------------------
function Deploy-Backend {
    Write-Step "构建后端"
    Push-Location $backendDir
    try {
        Write-Host "  mvnw clean package -DskipTests ..."
        $buildOutput = .\mvnw.cmd clean package -DskipTests 2>&1
        if ($LASTEXITCODE -ne 0) {
            Write-Err "后端构建失败！"
            Write-Host $buildOutput
            exit 1
        }
    } finally { Pop-Location }
    Write-OK "后端构建完成"

    # 查找 JAR 文件
    $jarFile = Get-ChildItem -Path (Join-Path $backendDir "target") -Filter "*.jar" |
        Where-Object { $_.Name -like "*intellitravel-pioneer*" -and $_.Name -notlike "*sources*" } |
        Sort-Object LastWriteTime -Descending |
        Select-Object -First 1

    if (-not $jarFile) {
        Write-Err "找不到构建产物 JAR 文件！"
        exit 1
    }
    Write-OK "JAR 文件: $($jarFile.Name)"

    Write-Step "上传后端到服务器"
    Write-Host "  确保目标目录存在..."
    Invoke-Ssh -Command "mkdir -p /opt/intellitravel" | Out-Null

    Write-Host "  scp $($jarFile.Name) → /opt/intellitravel/ ..."
    Invoke-Scp -Source $jarFile.FullName -Dest "/opt/intellitravel/"
    if ($LASTEXITCODE -ne 0) {
        Write-Err "后端 JAR 上传失败！"
        exit 1
    }
    Write-OK "后端 JAR 已上传"

    Write-Step "重启后端服务"
    Invoke-Ssh -Command "sudo systemctl restart intellitravel"

    # 等待启动
    Start-Sleep -Seconds 5

    # 检查状态
    $status = Invoke-Ssh -Command "sudo systemctl is-active intellitravel"
    if ($status -match "active") {
        Write-OK "后端服务已重启 (active)"
    } else {
        Write-Err "后端服务可能启动失败，查看日志："
        Write-Host "  ssh ${sshUser}@${Server} 'sudo journalctl -u intellitravel -n 30'"

        # 自动显示最近日志
        Write-Step "最近 30 行日志"
        Invoke-Ssh -Command "sudo journalctl -u intellitravel -n 30 --no-pager"
    }
}

# -----------------------------------------------------------
# 健康检查
# -----------------------------------------------------------
function Invoke-HealthCheck {
    Write-Step "健康检查"
    Start-Sleep -Seconds 2

    $apiUrl = "http://${Server}/api/user/health"
    Write-Host "  测试: GET $apiUrl"
    try {
        $response = Invoke-WebRequest -Uri $apiUrl -TimeoutSec 10 -UseBasicParsing
        Write-OK "API 正常 (HTTP $($response.StatusCode))"
    } catch {
        Write-Warn "API 健康检查未通过: $($_.Exception.Message)"
        Write-Host "  可以手动测试: curl http://${Server}/api/user/health"
    }

    Write-Host "  浏览器访问: http://${Server}"
}

# -----------------------------------------------------------
# 主流程
# -----------------------------------------------------------
Write-Host ""
Write-Host "╔══════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║   IntelliTravel Pioneer — 部署脚本           ║" -ForegroundColor Cyan
Write-Host "║   服务器: ${Server}                          ║" -ForegroundColor Cyan
Write-Host "╚══════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

if ($FrontendOnly) {
    $doFrontend = $true
    $doBackend  = $false
    Write-Host "模式: 仅部署前端" -ForegroundColor Yellow
} elseif ($BackendOnly) {
    $doFrontend = $false
    $doBackend  = $true
    Write-Host "模式: 仅部署后端" -ForegroundColor Yellow
} else {
    $doFrontend = $true
    $doBackend  = $true
    Write-Host "模式: 全部部署（前端 + 后端）" -ForegroundColor Yellow
}

# SSH 连接预检（任何模式都先测试连接）
Test-SshConnection

# 部署前备份
if ($BackupFirst) { Backup-ServerFiles }

# 部署前端
if ($doFrontend) { Deploy-Frontend }

# 部署后端
if ($doBackend) { Deploy-Backend }

# 健康检查
Invoke-HealthCheck

Write-Host ""
Write-Host "╔══════════════════════════════════════════════╗" -ForegroundColor Green
Write-Host "║   部署完成 ✓                                ║" -ForegroundColor Green
Write-Host "╚══════════════════════════════════════════════╝" -ForegroundColor Green
Write-Host ""
