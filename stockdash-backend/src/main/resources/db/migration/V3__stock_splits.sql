create table stock_splits (
    id bigint not null auto_increment primary key,
    symbol varchar(255) not null,
    split_date date not null,
    split_ratio numeric(19,6) not null,
    constraint uk_stock_splits_symbol_date unique (symbol, split_date)
);

create index idx_stock_splits_symbol_split_date
    on stock_splits (symbol, split_date);
