@echo off
rem -------------------------------------------------
rem run-backend.bat – start the Spring Boot backend
rem -------------------------------------------------
rem Change to the directory that contains mvnw.cmd
cd /d "%~dp0"

rem Call the wrapper using the absolute path (handles spaces)
call "%~dp0mvnw.cmd" spring-boot:run
