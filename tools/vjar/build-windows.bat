@echo off
setlocal

if "%~1"=="" (
	 echo usage: build-windows.bat <target>
	 exit /b 2
)

set TARGET=%~1
call makev.bat -msvc
if errorlevel 1 exit /b %errorlevel%

for /f "usebackq tokens=*" %%i in (`"%ProgramFiles(x86)%\Microsoft Visual Studio\Installer\vswhere.exe" -latest -products * -requires Microsoft.VisualStudio.Component.VC.Tools.x86.x64 -property installationPath`) do set VSINSTALL=%%i
if not defined VSINSTALL (
	 echo Visual Studio with C tools was not found
	 exit /b 1
)
if /i "%TARGET%"=="windows-aarch64" (set VSARCH=arm64) else (set VSARCH=x64)
call "%VSINSTALL%\VC\Auxiliary\Build\vcvarsall.bat" %VSARCH%
if errorlevel 1 exit /b %errorlevel%

if not exist build\native\%TARGET% mkdir build\native\%TARGET%
v.exe -new-compiler -prod -cc clang -shared -d jar -o build/native/%TARGET%/v cmd/v
if errorlevel 1 exit /b %errorlevel%

cl /nologo /LD /I"%JAVA_HOME%\include" /I"%JAVA_HOME%\include\win32" ^
	/Itools\vjar\src tools\vjar\src\v_jni.c ^
	/Fe:build\native\%TARGET%\v_jni.dll
if errorlevel 1 exit /b %errorlevel%

del /q v_jni.exp v_jni.lib 2>nul
endlocal
