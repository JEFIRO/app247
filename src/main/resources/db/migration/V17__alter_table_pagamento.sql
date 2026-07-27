alter table pagamento
    add column source_paiment varchar(16) not null,
        modify column tipo varchar(16) null;