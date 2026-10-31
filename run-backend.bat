@echo off
rem -------------------------------------------------
rem run-backend.bat – start the Spring Boot backend
rem -------------------------------------------------
cd /d "%~dp0backend"
set JAVA_HOME=C:\Program Files\Java\jdk-21.0.11

where mvn >nul 2>&1
if %ERRORLEVEL% equ 0 (
    echo Starting backend using system Maven...
    mvn spring-boot:run
) else (
    echo Starting backend using Maven Wrapper...
    call "%~dp0backend\mvnw.cmd" spring-boot:run
)
