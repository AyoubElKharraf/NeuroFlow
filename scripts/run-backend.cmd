@echo off
REM Start NeuroFlow backend with a JDK 17+ (Spring Boot 3 requirement)
cd /d "%~dp0..\neuroflow-backend"

REM Prefer JDK 25 / 21 / 17 under C:\Java — override stale JAVA_HOME (often JDK 11)
if exist "C:\Java\jdk-25\bin\java.exe" (
  set "JAVA_HOME=C:\Java\jdk-25"
) else if exist "C:\Java\jdk-21\bin\java.exe" (
  set "JAVA_HOME=C:\Java\jdk-21"
) else if exist "C:\Java\jdk-17\bin\java.exe" (
  set "JAVA_HOME=C:\Java\jdk-17"
)

if not defined JAVA_HOME (
  echo ERROR: Java 17+ not found. Install JDK 21/25 under C:\Java\
  exit /b 1
)

set "PATH=%JAVA_HOME%\bin;%PATH%"
echo JAVA_HOME=%JAVA_HOME%
java -version
echo.
echo Starting neuroflow-backend on http://localhost:8080 ...
mvn -DskipTests spring-boot:run
