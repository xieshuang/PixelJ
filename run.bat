@echo off
cd /d %~dp0

set JAR_PATH=target\pixelj-1.0-SNAPSHOT.jar

if exist "%JAR_PATH%" (
    echo Starting PixelJ...
    start javaw -jar "%JAR_PATH%"
) else (
    echo Building PixelJ first...
    call mvn package -q -DskipTests
    start javaw -jar "%JAR_PATH%"
)
exit
