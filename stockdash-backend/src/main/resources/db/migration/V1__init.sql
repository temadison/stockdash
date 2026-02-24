create table accounts (
    id bigint not null auto_increment primary key,
    name varchar(255) not null,
    constraint uk_accounts_name unique (name)
);

create table trade_transactions (
    id bigint not null auto_increment primary key,
    account_id bigint not null,
    trade_date date not null,
    symbol varchar(255) not null,
    type varchar(255) not null,
    quantity numeric(19,6) not null,
    price numeric(19,6) not null,
    fee numeric(19,6) not null,
    constraint fk_trade_transactions_account
        foreign key (account_id) references accounts (id),
    constraint uk_trade_transactions_natural_key
        unique (account_id, trade_date, symbol, type, quantity, price, fee)
);

create table daily_close_prices (
    id bigint not null auto_increment primary key,
    symbol varchar(255) not null,
    price_date date not null,
    close_price numeric(19,6) not null,
    constraint uk_daily_close_prices_symbol_date unique (symbol, price_date)
);

create index idx_trade_transactions_trade_date_id
    on trade_transactions (trade_date, id);

create index idx_trade_transactions_symbol_type_trade_date_id
    on trade_transactions (symbol, type, trade_date, id);

create index idx_daily_close_prices_symbol_price_date
    on daily_close_prices (symbol, price_date);
