.PHONY: build run clean stop

# Обычный запуск (демон остается в памяти для скорости)
run:
	./gradlew bootRun

# Сборка без тестов (когда нужно просто проверить, что компилируется)
build:
	./gradlew build -x test

# Полная очистка, если Gradle "заглючил" или нужно освободить RAM
clean:
	./gradlew clean
	./gradlew --stop

# Отдельная команда для остановки, если закончили работу
stop:
	./gradlew --stop
	-pkill -f 'java.*my-utility-app' || true

docker-build:
	docker compose build app

docker-up:
	docker compose up -d

docker-logs:
	docker compose logs -f app

docker-ps:
	docker compose ps

deploy:
	git pull
	docker compose build app
	docker compose up -d
	docker compose ps
