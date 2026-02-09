package store

import (
	"context"
	"encoding/json"
	"fmt"
	"log/slog"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"

	"github.com/minichat/worker/internal/event"
)

type PostgresStore struct {
	pool   *pgxpool.Pool
	logger *slog.Logger
}

func NewPostgresStore(ctx context.Context, dbURL string, logger *slog.Logger) (*PostgresStore, error) {
	cfg, err := pgxpool.ParseConfig(dbURL)
	if err != nil {
		return nil, fmt.Errorf("parse db url: %w", err)
	}
	cfg.MaxConns = 10
	cfg.MinConns = 1

	pool, err := pgxpool.NewWithConfig(ctx, cfg)
	if err != nil {
		return nil, fmt.Errorf("connect db: %w", err)
	}

	pingCtx, cancel := context.WithTimeout(ctx, 5*time.Second)
	defer cancel()
	if err := pool.Ping(pingCtx); err != nil {
		pool.Close()
		return nil, fmt.Errorf("ping db: %w", err)
	}

	return &PostgresStore{pool: pool, logger: logger}, nil
}

func (s *PostgresStore) Close() {
	s.pool.Close()
}

func (s *PostgresStore) HandleUsageEvent(ctx context.Context, ev event.UsageEvent) error {
	tx, err := s.pool.Begin(ctx)
	if err != nil {
		return fmt.Errorf("begin tx: %w", err)
	}
	defer tx.Rollback(ctx)

	_, err = tx.Exec(ctx, `
		INSERT INTO usage_events (user_id, generation_id, input_tokens, output_tokens, model)
		VALUES ($1::uuid, $2::uuid, $3, $4, $5)
	`, ev.UserID, ev.GenerationID, ev.InputTokens, ev.OutputTokens, ev.Model)
	if err != nil {
		return fmt.Errorf("insert usage_events: %w", err)
	}

	_, err = tx.Exec(ctx, `
		INSERT INTO daily_usage (user_id, day, input_tokens, output_tokens)
		VALUES ($1::uuid, CURRENT_DATE, $2, $3)
		ON CONFLICT (user_id, day)
		DO UPDATE SET
			input_tokens = daily_usage.input_tokens + EXCLUDED.input_tokens,
			output_tokens = daily_usage.output_tokens + EXCLUDED.output_tokens
	`, ev.UserID, ev.InputTokens, ev.OutputTokens)
	if err != nil {
		return fmt.Errorf("upsert daily_usage: %w", err)
	}

	if err := tx.Commit(ctx); err != nil {
		return fmt.Errorf("commit tx: %w", err)
	}

	s.logger.Info("usage_event persisted",
		"user_id", ev.UserID,
		"generation_id", ev.GenerationID,
		"input_tokens", ev.InputTokens,
		"output_tokens", ev.OutputTokens,
		"model", ev.Model,
	)
	return nil
}

func (s *PostgresStore) HandleAuditEvent(ctx context.Context, ev event.AuditEvent) error {
	metadata := ev.Metadata
	if len(metadata) == 0 {
		metadata = json.RawMessage(`{}`)
	}

	var userID any = nil
	if ev.UserID != nil {
		userID = *ev.UserID
	}

	_, err := s.pool.Exec(ctx, `
		INSERT INTO audit_logs (user_id, action, metadata_json)
		VALUES ($1::uuid, $2, $3::jsonb)
	`, userID, ev.Action, []byte(metadata))
	if err != nil {
		return fmt.Errorf("insert audit_logs: %w", err)
	}

	s.logger.Info("audit_event persisted",
		"user_id", ev.UserID,
		"action", ev.Action,
	)
	return nil
}
