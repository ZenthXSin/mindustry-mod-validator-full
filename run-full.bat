@echo off
REM Run the full-environment mod validator on Windows (with GPU).
REM Usage: run-full.bat <mod-path> [--json] [--output <file>]

java -jar "%~dp0\build\libs\mindustry-mod-validator-full-1.0.0-all.jar" %*
