# Setup Guide

Verified on Windows 11 with: JDK 21, Maven 3.9.16, PostgreSQL 18,
Python 3.12, Android SDK (API 35), Gradle 8.14.2.

## 0. Tool paths used during development (adjust for your machine)

| Tool | Path used here |
|------|----------------|
| JDK 21 | `D:\Defect\.tools\jdk21\jdk-21.0.12.1+1` |
| Maven | `D:\Defect\.tools\maven\apache-maven-3.9.16\bin` |
| PostgreSQL psql | `C:\Program Files\PostgreSQL\18\bin\psql.exe` |
| Android SDK | `%LOCALAPPDATA%\Android\Sdk` |
| Gradle | `C:\Users\Lenovo\.gradle\wrapper\dists\gradle-8.14.2-bin\...` |

PowerShell for backend work:

```powershell
$env:JAVA_HOME = "D:\Defect\.tools\jdk21\jdk-21.0.12.1+1"
$env:Path = "D:\Defect\.tools\maven\apache-maven-3.9.16\bin;$env:Path"
```

## 1. PostgreSQL databases

`database/create_databases.sql` is idempotent. It creates the role
`qualityinspect` / `qualityinspect` and two UTF8 databases:
`qualityinspect` (app) and `qualityinspect_test` (tests).

```powershell
& "C:\Program Files\PostgreSQL\18\bin\psql.exe" -U postgres -f D:\Defect\QualityInspectAI\database\create_databases.sql
```

Schema is applied by Flyway on backend startup (V1–V5).

## 2. Environment

```powershell
Copy-Item .env.example .env   # then edit values; .env is git-ignored
```

Minimum required values:

| Variable | Example | Notes |
|----------|---------|-------|
| `DB_URL` | `jdbc:postgresql://localhost:5432/qualityinspect` | |
| `DB_USER` / `DB_PASSWORD` | `qualityinspect` / `qualityinspect` | |
| `APP_JWT_SECRET` | 32+ random chars | HS256; changing it invalidates tokens |
| `APP_BOOTSTRAP_ENABLED` | `true` | **false in production** |
| `APP_BOOTSTRAP_PASSWORD` | `ChangeMe!qm1` | used for the first QM account |
| `STORAGE_DIR` | `./storage` | private image evidence directory |
| `ML_SERVICE_URL` | `http://localhost:8000` | backend → ML |
| `GENAI_PROVIDER` | `none` | `none` = labelled deterministic summaries |

## 3. ML service

```powershell
cd D:\Defect\QualityInspectAI\ml-service
python -m venv .venv; .\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
python -m uvicorn app.main:app --host 0.0.0.0 --port 8000
```

Health: `GET http://localhost:8000/health`. The trained model
(`artifacts/model.pt`, v0.1.0) loads automatically. To retrain:
`python training/prepare_dataset.py && python training/train.py`.

## 4. Backend

```powershell
cd D:\Defect\QualityInspectAI\backend
$env:APP_BOOTSTRAP_ENABLED = "true"
$env:APP_BOOTSTRAP_PASSWORD = "ChangeMe!qm1"
$env:STORAGE_DIR = "D:\Defect\QualityInspectAI\backend\storage"
.\mvnw.cmd spring-boot:run
```

Health: `GET http://localhost:8080/api/v1/health` (~8–12 s to come up).
Swagger UI: `http://localhost:8080/swagger-ui.html`.

### First login

```powershell
# 1. Bootstrap the single initial QUALITY_MANAGER (single-use)
Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/auth/bootstrap `
  -ContentType application/json `
  -Body '{"username":"qmadmin","password":"ChangeMe!qm1","displayName":"QM Admin"}'

# 2. Login (field is "username", NOT "email")
$login = Invoke-RestMethod -Method Post -Uri http://localhost:8080/api/v1/auth/login `
  -ContentType application/json `
  -Body '{"username":"qmadmin","password":"ChangeMe!qm1"}'
$token = $login.accessToken
```

Use `$headers = @{ Authorization = "Bearer $token" }` on subsequent calls.
Tokens survive backend restarts (JWT secret from config); if you see 401,
re-login.

## 5. Android APK

```powershell
$env:JAVA_HOME = "D:\Defect\.tools\jdk21\jdk-21.0.12.1+1"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$env:ANDROID_SDK_ROOT = "$env:LOCALAPPDATA\Android\Sdk"
# AGP 8.7.3 requires Gradle 8.x, NOT 9.x
& "C:\Users\Lenovo\.gradle\wrapper\dists\gradle-8.14.2-bin\2pb3mgt1p815evrl3weanttgr\gradle-8.14.2\bin\gradle.bat" assembleDebug --no-daemon
```

APK output: `android-app/app/build/outputs/apk/debug/app-debug.apk` (~7.5 MB).
Install on a device/emulator: `adb install app-debug.apk`.
In the app settings, point the base URL at your machine's LAN IP
(`http://<your-ip>:8080`); the emulator can use `http://10.0.2.2:8080`.

## 6. Tests

```powershell
# Backend: 29 unit + 10 RBAC integration (needs qualityinspect_test reachable)
cd backend; .\mvnw.cmd test

# ML service: 8 tests
cd ml-service; pytest
```

## 7. Stopping things

- Backend/ML are plain processes: kill the Java/Python process.
- If ports 8080/8000 are stuck: `Get-Process java, python | Stop-Process -Force`.
