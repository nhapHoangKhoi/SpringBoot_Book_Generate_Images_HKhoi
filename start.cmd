@echo off
REM Windows equivalent of start.sh — builds the frontend, then starts one Spring Boot process.
setlocal
cd /d "%~dp0"

REM application.properties is gitignored, so a fresh clone has none. Seed it from the example.
if not exist src\main\resources\application.properties (
  copy src\main\resources\application.example.properties src\main\resources\application.properties >nul
  echo ==^> Created application.properties from the example
)

if exist frontend (
  echo ==^> Building the frontend
  REM pushd rather than `npm --prefix`: --prefix sets the install location but still reads
  REM package.json from the current directory.
  pushd frontend
  call npm install || (popd & exit /b 1)
  call npm run build || (popd & exit /b 1)
  popd
  if exist src\main\resources\static rmdir /s /q src\main\resources\static
  mkdir src\main\resources\static
  xcopy /e /i /q frontend\dist\* src\main\resources\static\ || exit /b 1
)

echo ==^> Starting on http://localhost:8080
call mvnw.cmd spring-boot:run
