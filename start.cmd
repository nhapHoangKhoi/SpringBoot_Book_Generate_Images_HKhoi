@echo off
REM Windows equivalent of start.sh — builds the frontend, then starts one Spring Boot process.
setlocal
cd /d "%~dp0"

if exist frontend (
  echo ==^> Building the frontend
  call npm --prefix frontend install || exit /b 1
  call npm --prefix frontend run build || exit /b 1
  if exist src\main\resources\static rmdir /s /q src\main\resources\static
  mkdir src\main\resources\static
  xcopy /e /i /q frontend\dist\* src\main\resources\static\ || exit /b 1
)

echo ==^> Starting on http://localhost:8080
call mvnw.cmd spring-boot:run
