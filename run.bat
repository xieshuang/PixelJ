@echo off
cd /d %~dp0
start cmd /c "mvn javafx:run"
exit
