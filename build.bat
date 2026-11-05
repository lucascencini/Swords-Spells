@echo off
rem Compile, teste et assemble Swords & Spells avec le seul JDK (17 ou plus) : ni Maven ni Gradle requis.
rem
rem   build.bat          compile + tests + build\mini-dofus.jar
rem   build.bat run      idem puis lance le jeu
rem   build.bat package  idem puis cree une application autonome (Java embarque) dans build\dist
rem
rem Lance par double-clic, la fenetre reste ouverte a la fin pour pouvoir lire les messages.
setlocal EnableDelayedExpansion
cd /d "%~dp0"

set PAUSE_AT_END=0
echo %cmdcmdline% | find /i "%~0" >nul && set PAUSE_AT_END=1

set OUT=build
set JAVAC_OPTS=-encoding UTF-8 --release 17 -Xlint:all

rem ------------------------------------------------------------ verification du JDK
where javac >nul 2>nul
if errorlevel 1 (
  echo [ERREUR] javac est introuvable : il faut un JDK ^(et pas seulement Java^), version 17 ou plus.
  echo          Installez par exemple Temurin 21 LTS : https://adoptium.net
  echo          en cochant "Set JAVA_HOME" et "Add to PATH", puis ouvrez une NOUVELLE fenetre de commande.
  goto :fail
)
set JV=
for /f "tokens=2" %%v in ('javac -version 2^>^&1') do set JV=%%v
set JMAJ=0
for /f "delims=." %%m in ("%JV%") do set JMAJ=%%m
if %JMAJ% LSS 17 (
  echo [ERREUR] javac %JV% est trop ancien : il faut un JDK 17 ou plus.
  echo          Le premier javac trouve dans le PATH est :
  where javac
  goto :fail
)
echo JDK detecte : javac %JV%

rem Les outils jar et jpackage ne sont pas toujours dans le PATH (par exemple avec le raccourci
rem "Oracle\Java\javapath", qui ne contient que java et javac) : on les cherche dans le dossier du JDK.
set JDK_HOME=
for /f "tokens=1,* delims==" %%a in ('javac -J-XshowSettings:properties -version 2^>^&1 ^| findstr /c:"java.home"') do set JDK_HOME=%%b
if defined JDK_HOME set JDK_HOME=!JDK_HOME:~1!
set JAR_EXE=jar
set JPACKAGE_EXE=jpackage
if exist "!JDK_HOME!\bin\jar.exe" (
  set JAR_EXE="!JDK_HOME!\bin\jar.exe"
  set JPACKAGE_EXE="!JDK_HOME!\bin\jpackage.exe"
) else if defined JAVA_HOME if exist "!JAVA_HOME!\bin\jar.exe" (
  set JAR_EXE="!JAVA_HOME!\bin\jar.exe"
  set JPACKAGE_EXE="!JAVA_HOME!\bin\jpackage.exe"
)

rem ------------------------------------------------------------ compilation
echo.
echo == Compilation
if exist "%OUT%\classes" rmdir /s /q "%OUT%\classes"
if exist "%OUT%\test" rmdir /s /q "%OUT%\test"
mkdir "%OUT%\classes" 2>nul
mkdir "%OUT%\test" 2>nul

rem Liste des sources entre guillemets, avec des / (supporte les chemins contenant des espaces)
(for /r src\main\java %%f in (*.java) do (
  set "p=%%f"
  echo "!p:\=/!"
)) > "%OUT%\sources.txt"
javac %JAVAC_OPTS% -d "%OUT%\classes" @"%OUT%\sources.txt"
if errorlevel 1 (
  echo [ERREUR] La compilation a echoue : voir les messages ci-dessus.
  goto :fail
)
xcopy /e /i /q /y "src\main\resources" "%OUT%\classes" >nul
if errorlevel 1 (
  echo [ERREUR] Copie des ressources impossible.
  goto :fail
)

rem ------------------------------------------------------------ tests
echo.
echo == Tests
(for /r src\test\java %%f in (*.java) do (
  set "p=%%f"
  echo "!p:\=/!"
)) > "%OUT%\test-sources.txt"
javac %JAVAC_OPTS% -cp "%OUT%\classes" -d "%OUT%\test" @"%OUT%\test-sources.txt"
if errorlevel 1 (
  echo [ERREUR] La compilation des tests a echoue : voir les messages ci-dessus.
  goto :fail
)
java -cp "%OUT%\classes;%OUT%\test" com.minidofus.AllTests
if errorlevel 1 (
  echo [ERREUR] Des tests echouent : voir les lignes ECHEC ci-dessus.
  goto :fail
)

rem ------------------------------------------------------------ JAR
echo.
echo == JAR
%JAR_EXE% --create --file "%OUT%\mini-dofus.jar" --main-class com.minidofus.Main -C "%OUT%\classes" .
if errorlevel 1 (
  echo [ERREUR] Creation du JAR impossible.
  echo          Outil utilise : %JAR_EXE%  ^(dossier du JDK : !JDK_HOME!^)
  goto :fail
)
echo OK : %OUT%\mini-dofus.jar

if /i "%~1"=="run" (
  start "" javaw -jar "%OUT%\mini-dofus.jar"
  goto :end
)
if /i "%~1"=="package" goto :package
goto :end

:package
if exist "%OUT%\dist" rmdir /s /q "%OUT%\dist"
if exist "%OUT%\jpackage-input" rmdir /s /q "%OUT%\jpackage-input"
mkdir "%OUT%\jpackage-input"
copy /y "%OUT%\mini-dofus.jar" "%OUT%\jpackage-input\" >nul
%JPACKAGE_EXE% --type app-image --name SwordsAndSpells --app-version 1.0.0 --input "%OUT%\jpackage-input" --main-jar mini-dofus.jar --main-class com.minidofus.Main --add-modules java.desktop --jlink-options "--strip-debug --no-header-files --no-man-pages --strip-native-commands" --icon packaging\icon.ico --dest "%OUT%\dist"
if errorlevel 1 (
  echo [ERREUR] jpackage a echoue.
  goto :fail
)
echo OK : application autonome dans %OUT%\dist\SwordsAndSpells : lancer SwordsAndSpells.exe
goto :end

:fail
echo.
echo La construction s'est arretee. Copiez les messages ci-dessus pour obtenir de l'aide.
if "%PAUSE_AT_END%"=="1" pause
endlocal
exit /b 1

:end
if "%PAUSE_AT_END%"=="1" pause
endlocal
exit /b 0
