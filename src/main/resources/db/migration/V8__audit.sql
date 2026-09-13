CREATE TABLE audit_log (
    id CHAR(36) NOT NULL,
    empresa_id CHAR(36) NOT NULL,
    actor_type VARCHAR(30) NOT NULL,
    actor_user_id CHAR(36) NULL,
    terminal_id CHAR(36) NULL,
    action VARCHAR(100) NOT NULL,
    entity_type VARCHAR(80) NOT NULL,
    entity_id CHAR(36) NULL,
    correlation_id VARCHAR(100) NULL,
    before_data JSON NULL,
    after_data JSON NULL,
    metadata JSON NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT fk_audit_empresa FOREIGN KEY (empresa_id) REFERENCES empresa (id),
    CONSTRAINT fk_audit_user_tenant FOREIGN KEY (actor_user_id, empresa_id)
        REFERENCES users (id, empresa_id),
    CONSTRAINT fk_audit_terminal FOREIGN KEY (terminal_id) REFERENCES terminal (id)
) ENGINE=InnoDB;

CREATE INDEX idx_audit_empresa_created ON audit_log (empresa_id, created_at);
CREATE INDEX idx_audit_entity ON audit_log (empresa_id, entity_type, entity_id, created_at);
CREATE INDEX idx_audit_correlation ON audit_log (correlation_id);
