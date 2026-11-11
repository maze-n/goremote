package auth

import (
	"crypto/rand"
	"crypto/subtle"
	"fmt"
	"math/big"
	"sync"
	"time"
)

const (
	pairingCodeLength  = 6
	pairingCodeTTL     = 2 * time.Minute
	maxPairingAttempts = 5
)

type MemoryPairingManager struct {
	mu sync.Mutex

	code      string
	expiresAt time.Time
	attempts  int
	active    bool
}

func NewPairingManager() *MemoryPairingManager {
	return &MemoryPairingManager{}
}

// Generate creates a new six-digit pairing PIN.
// Generating a new PIN invalidates any previous PIN.
func (p *MemoryPairingManager) Generate() (string, error) {
	n, err := rand.Int(rand.Reader, big.NewInt(1_000_000))
	if err != nil {
		return "", fmt.Errorf("generate pairing code: %w", err)
	}

	code := fmt.Sprintf("%0*d", pairingCodeLength, n.Int64())

	p.mu.Lock()
	defer p.mu.Unlock()

	p.code = code
	p.expiresAt = time.Now().Add(pairingCodeTTL)
	p.attempts = 0
	p.active = true

	return code, nil
}

// VerifyAndConsume validates and consumes a pairing PIN.
// A valid PIN can only be used once.
func (p *MemoryPairingManager) VerifyAndConsume(code string) bool {
	p.mu.Lock()
	defer p.mu.Unlock()

	if !p.active {
		return false
	}

	// Check expiration.
	if time.Now().After(p.expiresAt) {
		p.reset()
		return false
	}

	// Check maximum attempts.
	if p.attempts >= maxPairingAttempts {
		p.reset()
		return false
	}

	// Validate PIN.
	if len(code) != pairingCodeLength ||
		subtle.ConstantTimeCompare([]byte(code), []byte(p.code)) != 1 {

		p.attempts++

		if p.attempts >= maxPairingAttempts {
			p.reset()
		}

		return false
	}

	// Consume PIN immediately after successful validation.
	p.reset()

	return true
}

// reset invalidates the current pairing session.
// Caller must hold the mutex.
func (p *MemoryPairingManager) reset() {
	p.code = ""
	p.expiresAt = time.Time{}
	p.attempts = 0
	p.active = false
}
