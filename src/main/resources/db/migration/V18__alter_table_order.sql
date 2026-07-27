ALTER TABLE orders
    ADD COLUMN mp_order_id     VARCHAR(255),
    ADD COLUMN mp_type         VARCHAR(50),
    ADD COLUMN mp_user_id      VARCHAR(50),
    ADD COLUMN mp_status       VARCHAR(50),
    ADD COLUMN mp_status_detail VARCHAR(50),
    ADD COLUMN mp_terminal_id  VARCHAR(100);
