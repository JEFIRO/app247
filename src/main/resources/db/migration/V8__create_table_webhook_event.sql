create table webhook_event
(
    id          varchar(100) primary key,
    event_id    varchar(100) not null,
    received_at timestamp    not null
);