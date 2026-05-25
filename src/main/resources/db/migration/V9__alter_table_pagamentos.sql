alter table pagamento
    add column updated_at        timestamp,
    add column payment_method_id varchar(100),
    add column status_detail     varchar(100)
