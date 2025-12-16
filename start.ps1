# 1. Зупиняємо і чистимо все (включно з томами, щоб гарантувати чистий старт)
Write-Host "Cleaning up old containers and volumes..." -ForegroundColor Yellow
docker-compose down -v

# 2. Збираємо і запускаємо у фоні
Write-Host "Building and starting containers..." -ForegroundColor Cyan
docker-compose up -d --build

# 3. Чекаємо трохи, поки контейнер створиться (навіть якщо він впаде, файлова система буде доступна)
Start-Sleep -Seconds 3

# 4. Заливаємо файл конфігурації в том
Write-Host "Copying mapping.json to shared volume..." -ForegroundColor Cyan
docker cp sharding-config/mapping.json code-app-1:/config/mapping.json

# 5. Перезапускаємо додаток, щоб він підхопив файл
Write-Host "Restarting App to apply config..." -ForegroundColor Cyan
docker-compose restart app

Write-Host "System started successfully!" -ForegroundColor Green