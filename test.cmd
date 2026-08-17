@echo off
REM Windows equivalent of test.sh — runs both suites and writes test-report.txt.
setlocal
cd /d "%~dp0"

echo ============================================================= > test-report.txt
echo BACKEND - JUnit 5 + Mockito (Spring Boot) >> test-report.txt
echo ============================================================= >> test-report.txt
call mvnw.cmd test >> test-report.txt 2>&1
set BACKEND=%ERRORLEVEL%

if exist frontend (
  echo ============================================================= >> test-report.txt
  echo FRONTEND - Vitest + React Testing Library >> test-report.txt
  echo ============================================================= >> test-report.txt
  pushd frontend
  call npm test >> ..\test-report.txt 2>&1
  set FRONTEND=%ERRORLEVEL%
  popd
) else (
  set FRONTEND=0
)

type test-report.txt
echo backend exit=%BACKEND%  frontend exit=%FRONTEND%
exit /b %BACKEND%
