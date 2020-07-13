set HOME=%USERPROFILE%
call python %~dp0..\..\..\depot_tools\gsutil.py -- %*
@echo off
set saved_error=%ERRORLEVEL%
exit /b %saved_error%
