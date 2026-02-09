package main

import (
	"context"
	"log/slog"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/minichat/worker/internal/config"
	"github.com/minichat/worker/internal/consumer"
	"github.com/minichat/worker/internal/store"
)

func main() {
	cfg := config.Load()
	logger := newLogger(cfg.LogLevel)

	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	st, err := store.NewPostgresStore(ctx, cfg.DBURL, logger)
	if err != nil {
		logger.Error("failed to initialize postgres store", "error", err)
		os.Exit(1)
	}
	defer st.Close()

	worker := consumer.New(cfg, st, logger)

	for {
		if ctx.Err() != nil {
			logger.Info("worker stopped")
			return
		}

		err := worker.Run(ctx)
		if err == nil {
			logger.Info("worker exited gracefully")
			return
		}

		logger.Error("worker loop failed, retrying", "error", err, "retry_delay", cfg.RetryDelay)
		select {
		case <-ctx.Done():
			logger.Info("worker stopped while waiting for retry")
			return
		case <-time.After(cfg.RetryDelay):
		}
	}
}

func newLogger(level string) *slog.Logger {
	var slogLevel slog.Level
	switch strings.ToLower(level) {
	case "debug":
		slogLevel = slog.LevelDebug
	case "warn":
		slogLevel = slog.LevelWarn
	case "error":
		slogLevel = slog.LevelError
	default:
		slogLevel = slog.LevelInfo
	}

	handler := slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: slogLevel})
	return slog.New(handler)
}
