# Ali_Lien Daily Market Bot

Автоматическая ежедневная публикация карточки курсов в Telegram-канал Ali_Lien.

## Что публикуется

- BTC
- ETH
- SOL
- ETHW
- USD / EUR / CNY к рублю
- фирменная карточка Ali_Lien в тёмно-золотом стиле

## Telegram Secrets

В GitHub нужно добавить два секрета:

```text
TELEGRAM_BOT_TOKEN
TELEGRAM_CHAT_ID
```

`TELEGRAM_CHAT_ID` для публичного канала:

```text
@AliLienASIC
```

## Где добавить Secrets

GitHub repository → Settings → Secrets and variables → Actions → New repository secret

## Ручной запуск

GitHub repository → Actions → Daily Ali_Lien Market Update → Run workflow

## Автозапуск

Сейчас стоит ежедневный запуск:

```text
06:30 UTC = 09:30 Moscow time
```

Файл расписания:

```text
.github/workflows/daily_market_update.yml
```

## Локальный тест без публикации

```bash
pip install -r requirements.txt
DRY_RUN=1 python market_update.py
```
