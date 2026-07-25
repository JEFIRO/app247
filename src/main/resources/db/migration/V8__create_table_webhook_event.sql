create table webhook_event
(
    id          varchar(100) primary key,
    event_id    varchar(100) not null,
    received_at timestamp    not null,

    empresa_id  varchar(36)  NOT NULL,
    FOREIGN KEY (empresa_id)
        REFERENCES empresa (id)
);