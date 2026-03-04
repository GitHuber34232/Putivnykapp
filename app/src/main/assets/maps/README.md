# Офлайн карти для Putivnyk

## АВТОМАТИЗАЦІЯ ✨

### Шлях до карти: `/data/maps` (WSL/Linux)

**Всі скрипти і файли працюють у `/data/maps`!**

## Швидкий старт (WSL/Ubuntu)

### 1. Встановити залежності

```bash
sudo apt-get update
sudo apt-get install -y default-jdk osmosis curl
```

### 2. Скопіювати kyiv.osm.pbf у /data/maps

```bash
mkdir -p /data/maps

# Автоматично з проєкту (якщо проєкт в D:\Putivnyk)
cd /шлях/до/проєкту
chmod +x scripts/copy_pbf_from_windows.sh
./scripts/copy_pbf_from_windows.sh

# Або вручну
cp /mnt/d/Putivnyk/app/src/main/assets/maps/kyiv.osm.pbf /data/maps/
```

### 3. Запустити конвертацію

```bash
chmod +x scripts/convert_to_map.sh
./scripts/convert_to_map.sh
```

**Очікуй 5-15 хвилин** залежно від розміру файлу машини

### 4. Копіювати результат назад у проєкт

```bash
# Автоматично
./scripts/copy_map_to_project.sh

# Або вручну
cp /data/maps/kyiv.map /mnt/d/Putivnyk/app/src/main/assets/maps/
```

### 5. Перевірка результату

```bash
# У Windows Explorer або VSCode відкрий:
D:\Putivnyk\app\src\main\assets\maps\kyiv.map

# Розмір має бути 20-60 МБ
```

## Інструкція створення kyiv.map (якщо немає PBF)

Детальна документація в [scripts/README.md](../../../../scripts/README.md)

### Необхідні інструменти

1. **osmosis** або **osmconvert/osmfilter** - для обробки OSM даних
2. **mapsforge-map-writer** - для конвертації в .map формат
3. **Java 11+** - для запуску mapsforge

### Повний pipeline (автоматично)

```bash
# Ubuntu/WSL - повна автоматизація
sudo apt-get install -y default-jdk osmosis curl
chmod +x scripts/*.sh
./scripts/generate_kyiv_map.sh
```

### Крок 1: Завантажити OSM дані України

```bash
# Завантажити актуальний файл України з Geofabrik
wget https://download.geofabrik.de/europe/ukraine-latest.osm.pbf
```

### Крок 2: Обрізати дані до меж Києва

#### Варіант A: Використання osmconvert (простіший)

```bash
# Встановити osmconvert (Ubuntu/Debian)
sudo apt-get install osmctools

# Обрізати до bbox Києва
# Координати bbox: min_lon, min_lat, max_lon, max_lat
# Київ приблизно: 30.2, 50.2, 30.9, 50.7
osmconvert ukraine-latest.osm.pbf -b=30.2,50.2,30.9,50.7 --complete-ways -o=kyiv.osm.pbf
```

#### Варіант B: Використання osmosis (більше контролю)

```bash
# Встановити osmosis
sudo apt-get install osmosis

# Обрізати до bbox
osmosis --read-pbf file=ukraine-latest.osm.pbf \
        --bounding-box top=50.7 left=30.2 bottom=50.2 right=30.9 completeWays=yes \
        --write-pbf file=kyiv.osm.pbf
```

### Крок 3: Конвертувати в формат Mapsforge

```bash
# Завантажити mapsforge-map-writer
wget https://github.com/mapsforge/mapsforge/releases/download/0.19.0/mapsforge-map-writer-0.19.0-jar-with-dependencies.jar

# Конвертувати PBF в MAP
java -jar mapsforge-map-writer-0.19.0-jar-with-dependencies.jar \
     --input=kyiv.osm.pbf \
     --output=kyiv.map \
     --map-start-position=50.4501,30.5234 \
     --bbox=50.2,30.2,50.7,30.9 \
     --zoom-interval-conf=10,21
```

### Крок 4: Помістити файл карти в проєкт

```bash
# Скопіювати kyiv.map в assets проєкту
cp kyiv.map app/src/main/assets/maps/kyiv.map
```

### Розміри файлів (приблизно)

- **ukraine-latest.osm.pbf**: ~1.5 GB
- **kyiv.osm.pbf**: ~50-100 MB
- **kyiv.map**: ~30-60 MB

### Альтернатива: Готові карти

Можна завантажити готові .map файли з:
- https://download.mapsforge.org/maps/
- https://ftp-stud.hs-esslingen.de/pub/Mirrors/download.mapsforge.org/maps/v5/

Або з OpenAndroMaps:
- https://www.openandromaps.org/

### Координати важливих точок Києва

```
Центр (Майдан): 50.4501, 30.5234
Софійський собор: 50.4529, 30.5145
Лавра: 50.4342, 30.5575
Залізничний вокзал: 50.4418, 30.4893
Аеропорт Бориспіль: 50.3450, 30.8947
```

### Налаштування bbox для різних зон

```bash
# Центр Києва (вужча зона)
30.45,50.42,30.60,50.48

# Київ з приміськими зонами
30.2,50.2,30.9,50.7

# Київська область
29.5,49.8,31.5,51.0
```

### Оновлення карти

OSM дані оновлюються щодня. Для актуальної карти повторіть процес з новими даними з Geofabrik.

### Troubleshooting

**Помилка: OutOfMemoryError при конвертації**
```bash
# Збільшити heap size для Java
java -Xmx4G -jar mapsforge-map-writer-*.jar ...
```

**Карта занадто велика**
- Зменшіть bbox
- Використайте менший zoom-interval (наприклад, 12,19 замість 10,21)
- Відфільтруйте непотрібні теги через osmfilter

**Карта не відображається в додатку**
- Перевірте, що файл знаходиться в `app/src/main/assets/maps/`
- Перевірте права доступу до файлу
- Перевірте логи на помилки завантаження
