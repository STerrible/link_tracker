create table chats (
    chat_id bigint primary key
);

create table links (
    id bigserial primary key,
    url text not null unique,
    last_seen_updated_at timestamptz not null default '1970-01-01 00:00:00+00'
);

create table subscriptions (
    id bigserial primary key,
    chat_id bigint not null references chats(chat_id) on delete cascade,
    link_id bigint not null references links(id) on delete cascade,
    filters text[] not null default '{}',
    unique(chat_id, link_id)
);

create table tags (
    id bigserial primary key,
    name text not null unique
);

create table subscription_tags (
    subscription_id bigint not null references subscriptions(id) on delete cascade,
    tag_id bigint not null references tags(id) on delete cascade,
    primary key(subscription_id, tag_id)
);

create table subscription_filters (
    subscription_id bigint not null references subscriptions(id) on delete cascade,
    filter_value text not null,
    primary key(subscription_id, filter_value)
);

create index idx_subscriptions_chat_id on subscriptions(chat_id);
create index idx_subscriptions_link_id on subscriptions(link_id);
create index idx_links_url on links(url);
