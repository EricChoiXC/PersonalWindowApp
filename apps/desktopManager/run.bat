@echo off
REM ========================================
REM DesktopManager 一键运行脚本
REM ========================================
cd /d "%~dp0"

REM 自动检测 JAVA_HOME，未设置时尝试常见路径
if "%JAVA_HOME%"=="" (
    if exist "C:\Users\Eric\.jdks\ms-21.0.10" (
        set "JAVA_HOME=C:\Users\Eric\.jdks\ms-21.0.10"
    )
)
if not "%JAVA_HOME%"=="" (
    echo Using JAVA_HOME: %JAVA_HOME%
)

echo.
echo Building and starting DesktopManager...
echo.

call mvnw.cmd javafx:run
pause
