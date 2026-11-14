@echo off
if "%~1"=="" exit /b 1
..\..\as\bin\asl -i ..\asm -g map %1.asm
if errorlevel 1 exit /b 1
..\..\as\bin\p2bin -r $-$ %1.p %1.ngp
if errorlevel 1 exit /b 1