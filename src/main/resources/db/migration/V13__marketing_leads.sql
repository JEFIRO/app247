CREATE TABLE marketing_lead (
    id CHAR(36) NOT NULL,
    nome VARCHAR(100) NOT NULL,
    empresa VARCHAR(120) NULL,
    email VARCHAR(160) NOT NULL,
    telefone VARCHAR(30) NOT NULL,
    cidade VARCHAR(100) NULL,
    estado VARCHAR(40) NULL,
    quantidade_unidades INT NULL,
    quantidade_terminais INT NULL,
    ja_opera_mercado_autonomo VARCHAR(30) NULL,
    mensagem VARCHAR(2000) NOT NULL,
    utm_source VARCHAR(160) NULL,
    utm_medium VARCHAR(160) NULL,
    utm_campaign VARCHAR(160) NULL,
    utm_content VARCHAR(160) NULL,
    utm_term VARCHAR(160) NULL,
    landing_page VARCHAR(500) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'NEW',
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id)
) ENGINE=InnoDB;

CREATE INDEX idx_marketing_lead_status_created
    ON marketing_lead (status, created_at);
CREATE INDEX idx_marketing_lead_email_created
    ON marketing_lead (email, created_at);
CREATE INDEX idx_marketing_lead_phone_created
    ON marketing_lead (telefone, created_at);
