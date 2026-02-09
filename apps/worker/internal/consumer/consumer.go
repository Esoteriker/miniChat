package consumer

import (
	"context"
	"fmt"
	"log/slog"
	"strings"

	amqp "github.com/rabbitmq/amqp091-go"

	"github.com/minichat/worker/internal/config"
	"github.com/minichat/worker/internal/event"
	"github.com/minichat/worker/internal/store"
)

type Consumer struct {
	cfg    config.Config
	store  *store.PostgresStore
	logger *slog.Logger
}

func New(cfg config.Config, st *store.PostgresStore, logger *slog.Logger) *Consumer {
	return &Consumer{cfg: cfg, store: st, logger: logger}
}

func (c *Consumer) Run(ctx context.Context) error {
	conn, err := amqp.Dial(c.cfg.RabbitURL)
	if err != nil {
		return fmt.Errorf("dial rabbitmq: %w", err)
	}
	defer conn.Close()

	ch, err := conn.Channel()
	if err != nil {
		return fmt.Errorf("open rabbitmq channel: %w", err)
	}
	defer ch.Close()

	if err := ch.Qos(c.cfg.Prefetch, 0, false); err != nil {
		return fmt.Errorf("set qos: %w", err)
	}

	if _, err := ch.QueueDeclare(c.cfg.UsageQueue, true, false, false, false, nil); err != nil {
		return fmt.Errorf("declare usage queue: %w", err)
	}
	if _, err := ch.QueueDeclare(c.cfg.AuditQueue, true, false, false, false, nil); err != nil {
		return fmt.Errorf("declare audit queue: %w", err)
	}

	usageMsgs, err := ch.Consume(c.cfg.UsageQueue, "", false, false, false, false, nil)
	if err != nil {
		return fmt.Errorf("consume usage queue: %w", err)
	}
	auditMsgs, err := ch.Consume(c.cfg.AuditQueue, "", false, false, false, false, nil)
	if err != nil {
		return fmt.Errorf("consume audit queue: %w", err)
	}

	c.logger.Info("worker consumer started",
		"usage_queue", c.cfg.UsageQueue,
		"audit_queue", c.cfg.AuditQueue,
		"prefetch", c.cfg.Prefetch,
	)

	for {
		select {
		case <-ctx.Done():
			c.logger.Info("worker consumer stopping")
			return nil
		case msg, ok := <-usageMsgs:
			if !ok {
				return fmt.Errorf("usage message channel closed")
			}
			c.handleUsage(ctx, msg)
		case msg, ok := <-auditMsgs:
			if !ok {
				return fmt.Errorf("audit message channel closed")
			}
			c.handleAudit(ctx, msg)
		}
	}
}

func (c *Consumer) handleUsage(parent context.Context, msg amqp.Delivery) {
	ctx, cancel := context.WithTimeout(parent, c.cfg.HandleTimeout)
	defer cancel()

	ev, err := event.ParseUsageEvent(msg.Body)
	if err != nil {
		c.nack(msg, err, "parse_usage_event", false)
		return
	}

	if err := c.store.HandleUsageEvent(ctx, ev); err != nil {
		c.nack(msg, err, "persist_usage_event", true)
		return
	}

	if err := msg.Ack(false); err != nil {
		c.logger.Error("ack usage_event failed", "error", err)
	}
}

func (c *Consumer) handleAudit(parent context.Context, msg amqp.Delivery) {
	ctx, cancel := context.WithTimeout(parent, c.cfg.HandleTimeout)
	defer cancel()

	ev, err := event.ParseAuditEvent(msg.Body)
	if err != nil {
		c.nack(msg, err, "parse_audit_event", false)
		return
	}

	if err := c.store.HandleAuditEvent(ctx, ev); err != nil {
		if isNonRetriableAuditError(err) {
			c.nack(msg, err, "persist_audit_event", false)
		} else {
			c.nack(msg, err, "persist_audit_event", true)
		}
		return
	}

	if err := msg.Ack(false); err != nil {
		c.logger.Error("ack audit_event failed", "error", err)
	}
}

func (c *Consumer) nack(msg amqp.Delivery, err error, operation string, requeue bool) {
	c.logger.Error("message processing failed",
		"operation", operation,
		"requeue", requeue,
		"error", err,
	)
	if nackErr := msg.Nack(false, requeue); nackErr != nil {
		c.logger.Error("nack failed", "operation", operation, "error", nackErr)
	}
}

func isNonRetriableAuditError(err error) bool {
	// audit_logs.user_id references users(id); stale/invalid historical events should be dropped.
	return strings.Contains(err.Error(), "SQLSTATE 23503")
}
