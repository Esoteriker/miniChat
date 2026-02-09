package event

import (
	"encoding/json"
	"fmt"

	"github.com/google/uuid"
)

type UsageEvent struct {
	Type         string `json:"type"`
	UserID       string `json:"userId"`
	GenerationID string `json:"generationId"`
	InputTokens  int    `json:"inputTokens"`
	OutputTokens int    `json:"outputTokens"`
	Model        string `json:"model"`
}

func ParseUsageEvent(body []byte) (UsageEvent, error) {
	var ev UsageEvent
	if err := json.Unmarshal(body, &ev); err != nil {
		return UsageEvent{}, err
	}
	if ev.Type != "usage_event" {
		return UsageEvent{}, fmt.Errorf("unexpected usage event type: %s", ev.Type)
	}
	if _, err := uuid.Parse(ev.UserID); err != nil {
		return UsageEvent{}, fmt.Errorf("invalid userId: %w", err)
	}
	if _, err := uuid.Parse(ev.GenerationID); err != nil {
		return UsageEvent{}, fmt.Errorf("invalid generationId: %w", err)
	}
	if ev.InputTokens < 0 || ev.OutputTokens < 0 {
		return UsageEvent{}, fmt.Errorf("tokens must be non-negative")
	}
	if ev.Model == "" {
		return UsageEvent{}, fmt.Errorf("model is required")
	}
	return ev, nil
}

type AuditEvent struct {
	Type     string          `json:"type"`
	UserID   *string         `json:"userId"`
	Action   string          `json:"action"`
	Metadata json.RawMessage `json:"metadata"`
}

func ParseAuditEvent(body []byte) (AuditEvent, error) {
	var ev AuditEvent
	if err := json.Unmarshal(body, &ev); err != nil {
		return AuditEvent{}, err
	}
	if ev.Type != "audit_event" {
		return AuditEvent{}, fmt.Errorf("unexpected audit event type: %s", ev.Type)
	}
	if ev.UserID != nil {
		if _, err := uuid.Parse(*ev.UserID); err != nil {
			return AuditEvent{}, fmt.Errorf("invalid userId: %w", err)
		}
	}
	if ev.Action == "" {
		return AuditEvent{}, fmt.Errorf("action is required")
	}
	if len(ev.Metadata) == 0 {
		ev.Metadata = json.RawMessage(`{}`)
	}
	return ev, nil
}
