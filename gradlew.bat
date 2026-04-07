@rem Gradle wrapper script para Windows
@if "%DEBUG%"=="" @echo off
@rem Inicializa variaveis
set APP_HOME=%~dp0
set CLASSPATH=%APP_HOME%gradle\wrapper\gradle-wrapper.jar

@rem Detecta Java
if defined JAVA_HOME (set JAVA="%JAVA_HOME%\bin\java.exe") else (set JAVA="java.exe")

%JAVA% -classpath %CLASSPATH% org.gradle.wrapper.GradleWrapperMain %*
