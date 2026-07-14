@echo off
REM Cert Tool launcher (Windows).
REM
REM Usage: cert-tool.bat
REM
REM Locates the runnable JAR next to this script, picks the JavaFX classifier
REM JARs matching Windows from lib\, and launches the app via the Java module
REM path so JavaFX loads as named modules (which is what its internal
REM LauncherImpl requires).

setlocal

set SCRIPT_DIR=%~dp0
set JAR=%SCRIPT_DIR%app-0.1.0-SNAPSHOT.jar
set LIB=%SCRIPT_DIR%lib

if not exist "%JAR%" (
    echo Error: %JAR% not found. Run `mvnw -pl app package` first.
    exit /b 1
)

set JFX_JARS=%LIB%\javafx-base-17.0.11-win.jar;%LIB%\javafx-controls-17.0.11-win.jar;%LIB%\javafx-graphics-17.0.11-win.jar;%LIB%\javafx-fxml-17.0.11-win.jar

java ^
    --module-path "%JFX_JARS%" ^
    --add-modules javafx.base,javafx.controls,javafx.graphics,javafx.fxml ^
    -jar "%JAR%" %*
