# Налаштовуємо консоль на UTF-8 (про всяк випадок)
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8

# Список контейнерів і баз
$containers = @("postgres_00", "postgres_01", "postgres_02", "postgres_03")
$databases = @()
0..9 | ForEach-Object { $databases += "db_$_" }
"a","b","c","d","e","f" | ForEach-Object { $databases += "db_$_" }

$grandTotal = 0

Write-Host "Scanning cluster for distributed data..." -ForegroundColor Cyan
Write-Host "-----------------------------------------------------------------------"
Write-Host ("{0,-10} | {1,-15} | {2,-10} | {3,-10}" -f "DATABASE", "CONTAINER", "ROWS", "SIZE")
Write-Host "-----------------------------------------------------------------------"

foreach ($db in $databases) {
    foreach ($container in $containers) {
        # SQL запит
        $sql = "SELECT count(*) || '|' || pg_size_pretty(pg_database_size('$db')) FROM travel_plans;"

        # Виконуємо команду
        $rawOutput = docker-compose exec -T $container psql -U postgres -d $db -t -A -q -c $sql 2>&1

        # Перетворюємо в рядок безпечно
        if ($null -eq $rawOutput) { $cleanOutput = "" }
        else { $cleanOutput = $rawOutput.ToString().Trim() }

        # Логіка визначення результату
        if ($cleanOutput -match '^(\d+)\|(.+)$') {
            # Варіант 1: Дані знайдено
            $rows = $Matches[1]
            $size = $Matches[2]

            if ([int]$rows -gt 0) {
                $grandTotal += [int]$rows
                Write-Host ("{0,-10} | {1,-15} | {2,-10} | {3,-10}" -f $db, $container, $rows, $size) -ForegroundColor Green
            }
        }
        elseif ($cleanOutput -match "does not exist") {
            # Варіант 2: Таблиці немає - ігноруємо
        }
    }
}

Write-Host "-----------------------------------------------------------------------"
Write-Host "TOTAL RECORDS CLUSTER-WIDE: $grandTotal" -ForegroundColor Cyan