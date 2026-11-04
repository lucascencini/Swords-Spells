@echo off
rem Compile, teste et assemble Mini Dofus avec le seul JDK (17 ou plus) : ni Maven ni Gradle requis.
rem
rem   build.bat          compile + tests + build\mini-dofus.jar
rem   build.bat run      idem puis lance le jeu
rem   build.bat package  idem puis cree une application autonome (Java embarque) dans build\dist
setlocal EnableDelayedExpansion
cd /d "%~dp0"

where javac >nul 2>nul
if errorlevel 1 (
  echo [ERREUR] javac introuvable. Installez un JDK 17 ou plus recent, par exemple :
  echo          https://adoptium.net  ^(Temurin 21 LTS^)
  exit /b 1
)

set OUT=build
set JAVAC_OPTS=-encoding UTF-8 --release 17 -Xlint:all

echo == Compilation
if exist "%OUT%\classes" rmdir /s /q "%OUT%\classes"
if exist "%OUT%\test" rmdir /s /q "%OUT%\test"
mkdir "%OUT%\classes" "%OUT%\test" 2>nul

rem Liste des sources entre guillemets, avec des / (supporte les chemins contenant des espaces)
(for /r src\main\java %%f in (*.java) do (
  set "p=%%f"
  echo "!p:\=/!"
)) > "%OUT%\sources.txt"
javac %JAVAC_OPTS% -d "%OUT%\classes" @"%OUT%\sources.txt" || exit /b 1
xcopy /e /i /q /y "src\main\resources" "%OUT%\classes" >nul

echo == Tests
(for /r src\test\java %%f in (*.java) do (
  set "p=%%f"
  echo "!p:\=/!"
)) > "%OUT%\test-sources.txt"
javac %JAVAC_OPTS% -cp "%OUT%\classes" -d "%OUT%\test" @"%OUT%\test-sources.txt" || exit /b 1
java -Dstdout.encoding=UTF-8 -cp "%OUT%\classes;%OUT%\test" com.minidofus.AllTests || exit /b 1

echo == JAR
jar --create --file "%OUT%\mini-dofus.jar" --main-class com.minidofus.Main -C "%OUT%\classes" . || exit /b 1
echo OK : %OUT%\mini-dofus.jar

if /i "%~1"=="run" (
  start "" javaw -jar "%OUT%\mini-dofus.jar"
)

if /i "%~1"=="package" (
  if exist "%OUT%\dist" rmdir /s /q "%OUT%\dist"
  if exist "%OUT%\jpackage-input" rmdir /s /q "%OUT%\jpackage-input"
  mkdir "%OUT%\jpackage-input"
  copy /y "%OUT%\mini-dofus.jar" "%OUT%\jpackage-input\" >nul
  jpackage --type app-image --name MiniDofus --app-version 1.0.0 ^
    --input "%OUT%\jpackage-input" --main-jar mini-dofus.jar --main-class com.minidofus.Main ^
    --add-modules java.desktop ^
    --jlink-options "--strip-debug --no-header-files --no-man-pages --strip-native-commands" ^
    --icon packaging\icon.ico --dest "%OUT%\dist" || exit /b 1
  echo OK : application autonome dans %OUT%\dist\MiniDofus ^(lancer MiniDofus.exe^)
)
endlocal
