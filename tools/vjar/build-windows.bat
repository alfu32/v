@echo off
setlocal

if "%~1"=="" goto usage

set TARGET=%~1
set "VSWHERE=%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe"
if not exist "%VSWHERE%" set "VSWHERE=%ProgramFiles%\Microsoft Visual Studio\Installer\vswhere.exe"
if not exist "%VSWHERE%" goto no_vs
set "VSINSTALL="
for /f "usebackq tokens=*" %%i in (`"%VSWHERE%" -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath`) do set "VSINSTALL=%%i"
if "%VSINSTALL%"=="" goto no_vs
set "VSARCH=x64"
if /i "%TARGET%"=="windows-aarch64" set "VSARCH=arm64"
call "%VSINSTALL%\VC\Auxiliary\Build\vcvarsall.bat" %VSARCH%
if errorlevel 1 exit /b 1

if not exist vc\v_win.c git clone --filter=blob:none --quiet https://github.com/vlang/vc vc
if errorlevel 1 exit /b 1

clang -std=c99 -municode -g -w -o v_win_bootstrap.exe vc\v_win.c -ladvapi32 -lws2_32 -Wl,-stack=33554432
if errorlevel 1 exit /b 1

v_win_bootstrap.exe -new-compiler -no-parallel -gc none -cc clang -o v_stage.exe cmd/v
if errorlevel 1 exit /b 1

v_stage.exe -new-compiler -prod -cc clang -o v.exe cmd/v
if errorlevel 1 exit /b 1

if not exist build\native\%TARGET% mkdir build\native\%TARGET%
v.exe -new-compiler -prod -cc clang -shared -d jar -o build/native/%TARGET%/v cmd/v
if errorlevel 1 exit /b 1

cl /nologo /LD /I"%JAVA_HOME%\include" /I"%JAVA_HOME%\include\win32" ^
	/Itools\vjar\src tools\vjar\src\v_jni.c ^
	/Fe:build\native\%TARGET%\v_jni.dll
if errorlevel 1 exit /b 1

del /q v_jni.exp v_jni.lib 2>nul
goto done

:usage
echo usage: build-windows.bat ^<target^>
exit /b 2

:no_vs
echo Visual Studio with C tools was not found
exit /b 1

:done
endlocal
