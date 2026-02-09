package config

import "os"

type Config struct {
	RabbitURL string
	DBURL     string
	LogLevel  string
}

func Load() Config {
	return Config{
		RabbitURL: getEnv("RABBITMQ_URL", "amqp://guest:guest@localhost:5672/"),
		DBURL:     getEnv("DATABASE_URL", "postgres://minichat:minichat@localhost:5432/minichat?sslmode=disable"),
		LogLevel:  getEnv("WORKER_LOG_LEVEL", "info"),
	}
}

func getEnv(key string, fallback string) string {
	if val, ok := os.LookupEnv(key); ok && val != "" {
		return val
	}
	return fallback
}
