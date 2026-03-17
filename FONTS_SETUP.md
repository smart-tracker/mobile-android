# Установка шрифта Geologica

## Описание
Проект использует шрифт **Geologica Light** от Google Fonts для всего интерфейса приложения:
- **Название приложения (LoginScreen)** — 32px Italic
- **Заголовки страниц регистрации** — 20px Light
- **Кнопки** ("Войти", "Создать аккаунт", "Продолжить", "Отправить код повторно") — 20px Light
- **Плейсхолдеры, цели использования, выбор пола, вторичные надписи** — 16px Light

## Предпосылки
- Все утилиты определены в `presentation/theme/SmartTrackerTheme.kt`
- TextStyle'ы уже добавлены в `SmartTrackerTypography`
- Все компоненты уже переведены на использование этих стилей

## Шаги установки

### 1. Создать папку шрифтов
✅ Папка уже создана: `app/src/main/res/font/`

### 2. Скачать файлы шрифтов

**Вариант A: Вручную через Google Fonts**
1. Откройте https://fonts.google.com/?query=geologica
2. Нажмите кнопку **"Download family"** (скачает .zip)
3. Распакуйте архив
4. В папке `static/` найдите нужные файлы:
   - `Geologica-Light.ttf` → переименуйте в `geologica_light.ttf`
   - `Geologica-Italic.ttf` (или `Geologica-LightItalic.ttf`) → переименуйте в `geologica_italic.ttf`
   - `Geologica-Regular.ttf` → переименуйте в `geologica_regular.ttf`

**Вариант B: Скачать через Python скрипт**

Создайте файл `download_fonts.py` в корне проекта:

```python
#!/usr/bin/env python3
import urllib.request
import os

fonts = {
    'geologica_light.ttf': 'https://fonts.gstatic.com/s/geologica/v2/ga7gW015dXxgMKB3iZVAPQXz4TqPg5_urA.ttf',
    'geologica_italic.ttf': 'https://fonts.gstatic.com/s/geologica/v2/ga7eW015dXxgMKB3iZXDIoVQ_R6Pg5_urA.ttf',
    'geologica_regular.ttf': 'https://fonts.gstatic.com/s/geologica/v1/ga7gW015dXxgMKB3iZVAPQUH5NyPkQ.ttf',
}

font_dir = r'app\src\main\res\font'
os.makedirs(font_dir, exist_ok=True)

for filename, url in fonts.items():
    filepath = os.path.join(font_dir, filename)
    if os.path.exists(filepath):
        print(f'✓ {filename} уже существует')
        continue
    
    print(f'⏳ Скачивается {filename}...')
    try:
        urllib.request.urlretrieve(url, filepath)
        print(f'✓ {filename} скачан успешно')
    except Exception as e:
        print(f'✗ Ошибка при скачивании {filename}: {e}')

print('\nГотово! Файлы шрифтов добавлены в app/src/main/res/font/')
```

Запустите в корне проекта:
```bash
python3 download_fonts.py
```

### 3. Проверить файлы
После скачивания в папке `app/src/main/res/font/` должны быть:
```
font/
├── geologica_light.ttf
├── geologica_italic.ttf
└── geologica_regular.ttf
```

**Важно:** Все имена файлов должны быть **lowercase** (строчные буквы), без заглавных букв и дефисов!

### 4. Скомпилировать проект
```bash
./gradlew build
```

Если нет ошибок о missing resources — всё работает!

## Использование в коде
Шрифты уже подключены через `SmartTrackerTypography`:

```kotlin
// Например, в RegisterScreen:
Text(
    text = "SmartTracker",
    style = MaterialTheme.typography.titleLarge,  // 32px italic
)

Text(
    text = "Войти",
    style = MaterialTheme.typography.labelLarge,  // 20px Light
)

Text(
    text = "Почта...",
    style = MaterialTheme.typography.bodyMedium,  // 16px Light
)
```

## Troubleshooting

### Ошибка: "Resource not found: font/geologica_light"
→ Файл `geologica_light.ttf` не добавлен в `app/src/main/res/font/`
→ Решение: скачайте файл вручную или через скрипт выше

### Ошибка при сборке: "Class not found"
→ Очистите кэш Gradle:
```bash
./gradlew clean
```

### Шрифт не применяется на устройстве
→ Убедитесь что TextStyle использует `fontFamily = geologicaFontFamily`
→ Очистите приложение: Settings → Apps → SmartTracker → Clear Cache
→ Переустановите APK

## Альтернативный вариант: встроенные шрифты
Если есть проблемы со скачиванием, можете использовать встроенные Compose-шрифты.
Обновите `SmartTrackerTheme.kt`:

```kotlin
val geologicaFontFamily = FontFamily.Default  // используется системный шрифт
```

Это временное решение — дизайн будет немного отличаться, но функционально всё будет работать.

---

**Автор:** GitHub Copilot
**Дата:** март 2026
