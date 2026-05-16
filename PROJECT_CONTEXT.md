# Chess Service Context

## What it does
Сервис загружает партии с Lichess, считает performance, показывает партии в таблице, позволяет смотреть партию на доске и анализировать ошибки.

## Main modules
- import: загрузка партий
- analysis: расчёт метрик
- web: JTE pages
- persistence: PostgreSQL entities/repositories

## Current deployment
- VPS
- Docker Compose
- app + postgres
- manual deploy

## Development principles
- Простота важнее архитектурной красоты
- Не усложнять инфраструктуру
- Код должен быть понятен детям/начинающим пользователям на UI-уровне
- Цель сервиса: объяснять шахматные ошибки человеческим языком
