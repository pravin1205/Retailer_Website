@echo off
REM Marketly Backend Startup Script
REM Uses Java 21 from IntelliJ — no space before & is critical for JAVA_HOME

echo Starting Marketly services with Java 21...

start "identity-service :8081"     cmd /k "set JAVA_HOME=C:\Users\Pravin.n\.jdks\ms-21.0.11&cd /d d:\Retailer_Website\backend\identity-service&mvn spring-boot:run"
timeout /t 15 /nobreak > nul

start "tenant-service :8082"       cmd /k "set JAVA_HOME=C:\Users\Pravin.n\.jdks\ms-21.0.11&cd /d d:\Retailer_Website\backend\tenant-service&mvn spring-boot:run"
timeout /t 5 /nobreak > nul

start "product-service :8083"      cmd /k "set JAVA_HOME=C:\Users\Pravin.n\.jdks\ms-21.0.11&cd /d d:\Retailer_Website\backend\product-service&mvn spring-boot:run"
timeout /t 5 /nobreak > nul

start "customer-service :8084"     cmd /k "set JAVA_HOME=C:\Users\Pravin.n\.jdks\ms-21.0.11&cd /d d:\Retailer_Website\backend\customer-service&mvn spring-boot:run"
timeout /t 5 /nobreak > nul

start "order-service :8085"        cmd /k "set JAVA_HOME=C:\Users\Pravin.n\.jdks\ms-21.0.11&cd /d d:\Retailer_Website\backend\order-service&mvn spring-boot:run"
timeout /t 5 /nobreak > nul

start "notification-service :8086" cmd /k "set JAVA_HOME=C:\Users\Pravin.n\.jdks\ms-21.0.11&cd /d d:\Retailer_Website\backend\notification-service&mvn spring-boot:run"
timeout /t 5 /nobreak > nul

start "analytics-service :8087"    cmd /k "set JAVA_HOME=C:\Users\Pravin.n\.jdks\ms-21.0.11&cd /d d:\Retailer_Website\backend\analytics-service&mvn spring-boot:run"
timeout /t 10 /nobreak > nul

start "api-gateway :8080"          cmd /k "set JAVA_HOME=C:\Users\Pravin.n\.jdks\ms-21.0.11&cd /d d:\Retailer_Website\backend\api-gateway&mvn spring-boot:run"

echo.
echo All 8 windows opened. Wait ~90s for full startup.
echo   Frontend : http://localhost:3000
echo   Gateway  : http://localhost:8080
echo   Swagger  : http://localhost:8081/swagger-ui.html
