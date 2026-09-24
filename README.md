# RightKnight

RightKnight — веб-сервис для работы со своими шахматными партиями на [Lichess](https://lichess.org/). Он загружает партии, показывает историю игр и рассчитывает performance за выбранный период. Долгосрочная цель проекта — помогать игроку находить повторяющиеся ошибки и выбирать направления для тренировки.

**Работающий сервис:** [rightknight.org](https://rightknight.org/).

## Что работает сейчас

- Регистрация и вход, привязка имени пользователя Lichess к профилю.
- Загрузка партий Lichess в PostgreSQL и обновление истории игр.
- Страница `/games`: последние 50 партий, поиск по названию дебюта, фильтр по цвету и просмотр партии на доске. При поиске по дебюту выборка не ограничена последними 50 партиями.
- Страница `/performance`: performance за период, отдельно белыми и чёрными, с фильтрами по режиму и рейтинговым партиям.
- Разбор PGN при импорте: ходы SAN/UCI, позиции FEN и время на ход при наличии данных часов.
- Раздел `/admin` для пользователей с ролью администратора: статистика, пользователи, партии и журнал активности.

Серверный анализ ходов с помощью Stockfish и хранение его результатов находятся в разработке. На странице просмотра партии доступна оценка позиции локальным движком в браузере; персональные рекомендации пока не реализованы.

## Стек

Java 21, Spring Boot 3, Gradle, JTE, PostgreSQL, Flyway, Spring Security, [chariot](https://github.com/tors42/chariot) для Lichess и [chesslib](https://github.com/bhlangonijr/chesslib) для разбора партий. Приложение и база данных запускаются отдельными контейнерами через Docker Compose.

## Быстрый запуск через Docker Compose

Понадобятся Docker и плагин Docker Compose. Из корня репозитория:

```bash
git clone https://github.com/ppeter777/RightKnight.git
cd RightKnight
cat > .env <<'EOF'
POSTGRES_DB=rightknight
POSTGRES_USER=rightknight_user
POSTGRES_PASSWORD=replace-with-a-strong-password
SPRING_PROFILES_ACTIVE=prod
EOF
docker compose up -d --build
docker compose ps
```

Откройте [http://localhost:8080](http://localhost:8080), зарегистрируйтесь и укажите имя пользователя Lichess на главной странице. Затем перейдите в «Games» или «Performance». При первом открытии данные могут загружаться некоторое время; большой период загружается в фоне.

Контейнер приложения слушает только `127.0.0.1:8080` на хосте. На VPS для публичного доступа нужен отдельно настроенный обратный прокси. Данные PostgreSQL хранятся в томе `postgres_data`; `docker compose down` не удаляет этот том.

```bash
docker compose logs -f app  # логи приложения
docker compose down         # остановить контейнеры
```

Файл `.env` не добавляется в Git. Миграции Flyway выполняются при запуске приложения.

## Разработка

Для локального запуска без контейнера приложения нужны JDK 21 и PostgreSQL. Профиль `dev` настроен на `localhost:5433`, базу `rightknight` и пользователя `rightknight_user`; параметры находятся в `src/main/resources/application-dev.properties`. Создайте базу и согласуйте её адрес и учётные данные с этим файлом, затем выполните:

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

В текущем `docker-compose.yml` порт базы наружу не опубликован, поэтому одного `docker compose up -d db` для запуска профиля `dev` на хосте недостаточно. Для разработки с базой в Docker можно опубликовать её порт `5433:5432` в локальном `docker-compose.override.yml` (он исключён из Git).

```bash
./gradlew test             # тесты
./gradlew build            # сборка с тестами
make build                # сборка без тестов
```

Серверный анализ Stockfish требует отдельного исполняемого файла по пути `stockfish.path` из `src/main/resources/application.properties`; Dockerfile не включает этот бинарник. Для работы текущих страниц он не требуется.

## Код и планы

Основной код находится в `src/main/java/dev/rightknight`, шаблоны — в `src/main/jte`, миграции — в `src/main/resources/db/migration`. Текущий план развития анализа описан в [`docs/roadmap-analysis.md`](docs/roadmap-analysis.md), а замысел сервиса — в [`Vision2_0.md`](Vision2_0.md).
