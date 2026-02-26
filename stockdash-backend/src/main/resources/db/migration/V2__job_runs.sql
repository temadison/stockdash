create table job_runs (
    id bigint not null auto_increment primary key,
    job_name varchar(100) not null,
    started_at timestamp not null,
    finished_at timestamp null,
    status varchar(32) not null,
    requested_count int not null default 0,
    processed_count int not null default 0,
    failed_count int not null default 0,
    skipped_count int not null default 0,
    details varchar(1024) null
);

create index idx_job_runs_job_name_started_at
    on job_runs (job_name, started_at desc);
