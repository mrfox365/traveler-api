# Масив конфігурації: Контейнер -> Бази даних в ньому
$shardMap = @{
    "postgres_00" = @("db_0", "db_1", "db_2", "db_3")
    "postgres_01" = @("db_4", "db_5", "db_6", "db_7")
    "postgres_02" = @("db_8", "db_9", "db_a", "db_b")
    "postgres_03" = @("db_c", "db_d", "db_e", "db_f")
}

$totalCount = 0

Write-Host "Checking data distribution..." -ForegroundColor Cyan

foreach ($container in $shardMap.Keys) {
    Write-Host "--- Container: $container ---" -ForegroundColor Yellow
    foreach ($db in $shardMap[$container]) {
        # Отримуємо "сирий" вивід від докера
        $rawOutput = docker-compose exec -T $container psql -U postgres -d $db -t -c "SELECT count(*) FROM travel_plans;" 2>$null

        # Фільтруємо сміття: беремо тільки рядок, що містить цифри
        $count = $rawOutput | Where-Object { $_ -match '^\s*\d+\s*$' } | Select-Object -First 1

        # Якщо нічого не знайшли, вважаємо що 0
        if ($null -eq $count -or $count -eq "") {
            $count = "0"
        }

        # Прибираємо пробіли
        $count = $count.ToString().Trim()

        Write-Host "  Database $db : $count rows"

        # Тепер конвертація пройде успішно
        $totalCount += [int]$count
    }
}

Write-Host "`nTotal records created: $totalCount" -ForegroundColor Green