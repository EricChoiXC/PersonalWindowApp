# ============================================================
# 桌面管家 安装包构建脚本
# 流程: Maven 编译 → jpackage 生成运行时镜像 → Inno Setup 打包
# ============================================================

param(
    [string]$JdkHome = $(
        if ($env:JAVA_HOME) { $env:JAVA_HOME }
        elseif (Test-Path "C:\Program Files\Java\jdk-22") { "C:\Program Files\Java\jdk-22" }
        else { throw "未找到 JDK，请设置 JAVA_HOME 或传入 -JdkHome" }
    ),
    [string]$IsccPath = "C:\Program Files (x86)\Inno Setup 6\ISCC.exe",
    [string]$MavenHome = $null,
    [string]$Version = $null
)

$ErrorActionPreference = "Stop"
$scriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$projectRoot = Resolve-Path "$scriptDir\.."
$appDir = "$projectRoot\apps\desktopManager"
$targetDir = "$appDir\target"
$inputDir = "$targetDir\jpackage-input"
$distDir = "$targetDir\dist\DesktopManager"
$issFile = "$scriptDir\desktopManager-setup.iss"
$setupExe = "$scriptDir\DesktopManager-Setup-{0}.exe"

# ---------- 1. 验证工具 ----------

$javaExe = "$JdkHome\bin\java.exe"
$jpackageExe = "$JdkHome\bin\jpackage.exe"

if (-not $MavenHome) {
    $ideaMaven = "C:\Program Files\JetBrains\IntelliJ IDEA 2024.1.4\plugins\maven\lib\maven3"
    if (Test-Path "$ideaMaven\bin\mvn.cmd") {
        $MavenHome = $ideaMaven
    }
}
$mvnCmd = if ($MavenHome) { "$MavenHome\bin\mvn.cmd" } else { "mvn" }

foreach ($tool in @($javaExe, $jpackageExe)) {
    if (-not (Test-Path $tool)) {
        Write-Error "工具未找到: $tool"
        exit 1
    }
}

if ($MavenHome) {
    if (-not (Test-Path "$MavenHome\bin\mvn.cmd")) {
        Write-Error "Maven 未找到于: $MavenHome\bin\mvn.cmd"
        exit 1
    }
} elseif (-not (Get-Command "mvn" -ErrorAction SilentlyContinue)) {
    Write-Error "Maven 未找到，请安装 Maven 或通过 -MavenHome 指定路径"
    exit 1
}

if (-not (Test-Path $issFile)) {
    Write-Error "Inno Setup 脚本未找到: $issFile"
    exit 1
}

if (-not (Test-Path $IsccPath)) {
    Write-Warning "ISCC.exe 未找到 ($IsccPath)，跳过安装包编译。安装包文件将不会被生成。"
    $canBuildInstaller = $false
} else {
    $canBuildInstaller = $true
}

# ---------- 2. 读取版本号 ----------

if (-not $Version) {
    [xml]$pom = Get-Content "$appDir\pom.xml"
    $Version = $pom.project.version
}

Write-Host "===== 桌面管家 安装包构建 =====" -ForegroundColor Cyan
Write-Host "  版本: $Version"
Write-Host "  JDK:  $JdkHome"
Write-Host "  ISCC: $IsccPath" -ForegroundColor $(if ($canBuildInstaller) { "White" } else { "Yellow" })
Write-Host ""

# ---------- 3. Maven 编译 ----------

Write-Host "[1/3] Maven 编译..." -ForegroundColor Yellow
Push-Location $appDir
try {
    & $mvnCmd clean package -DskipTests
    if ($LASTEXITCODE -ne 0) { throw "Maven 编译失败" }
} finally {
    Pop-Location
}

# 查找主 JAR
$mainJar = Get-ChildItem -Path $targetDir -Filter "desktopManager-*.jar" |
    Where-Object { $_.Name -notmatch "sources|javadoc" } |
    Sort-Object LastWriteTime -Descending |
    Select-Object -First 1

if (-not $mainJar) {
    Write-Error "主 JAR 文件未找到（desktopManager-*.jar）"
    exit 1
}
Write-Host "  主 JAR: $($mainJar.Name)" -ForegroundColor Green

# ---------- 4. jpackage 生成运行时镜像 ----------

Write-Host "[2/3] jpackage 生成运行时镜像..." -ForegroundColor Yellow

if (Test-Path $distDir) {
    Remove-Item -Recurse -Force $distDir
}

if (Test-Path $inputDir) {
    Remove-Item -Recurse -Force $inputDir
}
New-Item -ItemType Directory -Path $inputDir | Out-Null

Copy-Item -LiteralPath $mainJar.FullName -Destination $inputDir

$libDir = "$targetDir\lib"
if (Test-Path $libDir) {
    Get-ChildItem -LiteralPath $libDir -Filter "*.jar" | Where-Object {
        $_.Name -notmatch "^(junit-|mockito-|byte-buddy|objenesis|opentest4j|apiguardian)"
    } | ForEach-Object {
        Copy-Item -LiteralPath $_.FullName -Destination $inputDir
    }
}

$jpackageArgs = @(
    "--type", "app-image",
    "--name", "DesktopManager",
    "--app-version", $Version,
    "--input", $inputDir,
    "--main-jar", $mainJar.Name,
    "--main-class", "com.personal.windows.desktopmanager.DesktopManagerApplication",
    "--dest", "$targetDir\dist",
    "--java-options", "--module-path=`$APPDIR",
    "--java-options", "--add-modules=javafx.controls,javafx.fxml"
)

Write-Host "  jpackage $($jpackageArgs -join ' ')"
& $jpackageExe @jpackageArgs
if ($LASTEXITCODE -ne 0) { throw "jpackage 生成失败" }

# 清理临时输入目录
Remove-Item -Recurse -Force $inputDir

Write-Host "  运行时镜像已生成: $distDir" -ForegroundColor Green

# ---------- 5. Inno Setup 编译安装包 ----------

if ($canBuildInstaller) {
    Write-Host "[3/3] Inno Setup 编译安装包..." -ForegroundColor Yellow

    # 更新 ISS 中的版本号和源路径
    $issContent = Get-Content $issFile -Raw
    $issContent = $issContent -replace '#define MyAppVersion ".*"', "#define MyAppVersion ""$Version"""
    $issContent = $issContent -replace '\{#MyAppSourceDir\}', "$targetDir\dist"
    $tempIssFile = [System.IO.Path]::GetTempFileName() + ".iss"
    Set-Content -Path $tempIssFile -Value $issContent -Encoding UTF8

    try {
        & $IsccPath $tempIssFile
        if ($LASTEXITCODE -ne 0) { throw "Inno Setup 编译失败" }
    } finally {
        Remove-Item $tempIssFile -ErrorAction SilentlyContinue
    }

    $expectedOutput = $setupExe -f $Version
    if (Test-Path $expectedOutput) {
        Write-Host "  安装包已生成: $expectedOutput" -ForegroundColor Green
    } else {
        Write-Warning "安装包可能未生成到预期位置: $expectedOutput"
    }
} else {
    Write-Host "[3/3] 跳过安装包编译（ISCC.exe 未配置）" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "===== 构建完成 =====" -ForegroundColor Cyan
