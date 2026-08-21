ALTER TABLE orders
    ADD COLUMN mp_event_version INT NULL,
    ADD COLUMN mp_event_date DATETIME(6) NULL;

CREATE INDEX idx_orders_mp_order_id ON orders (mp_order_id);
