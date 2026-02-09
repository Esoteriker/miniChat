package config

import (
	"os"
	"strconv"
	"time"
)

type Config struct {
	RabbitURL     string
	DBURL         string
	LogLevel      string
	UsageQueue    string
	AuditQueue    string
	Prefetch      int
	RetryDelay    time.Duration
	HandleTimeout time.Duration
}

func Load() Config {
	retrySeconds := getEnvInt("WORKER_RETRY_SECONDS", 5)
	handleTimeoutSeconds := getEnvInt("WORKER_HANDLE_TIMEOUT_SECONDS", 10)

	return Config{
		RabbitURL:     getEnv("RABBITMQ_URL", "amqp://guest:guest@localhost:5672/"),
		DBURL:         getEnv("DATABASE_URL", "postgres://minichat:minichat@localhost:5432/minichat?sslmode=disable"),
		LogLevel:      getEnv("WORKER_LOG_LEVEL", "info"),
		UsageQueue:    getEnv("WORKER_USAGE_QUEUE", "usage_event"),
		AuditQueue:    getEnv("WORKER_AUDIT_QUEUE", "audit_event"),
		Prefetch:      getEnvInt("WORKER_PREFETCH", 20),
		RetryDelay:    time.Duration(retrySeconds) * time.Second,
		HandleTimeout: time.Duration(handleTimeoutSeconds) * time.Second,
	}
}

func getEnv(key string, fallback string) string {
	if val, ok := os.LookupEnv(key); ok && val != "" {
		return val
	}
	return fallback
}

func getEnvInt(key string, fallback int) int {
	val := getEnv(key, "")
	if val == "" {
		return fallback
	}
	parsed, err := strconv.Atoi(val)
	if err != nil {
		return fallback
	}
	return parsed
}
