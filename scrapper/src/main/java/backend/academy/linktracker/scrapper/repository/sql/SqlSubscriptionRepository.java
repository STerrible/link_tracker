package backend.academy.linktracker.scrapper.repository.sql;

import backend.academy.linktracker.scrapper.model.LinkResponse;
import backend.academy.linktracker.scrapper.repository.api.SubscriptionRepository;
import java.net.URI;
import java.util.List;
import java.util.OptionalLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty(prefix = "app.database", name = "access-type", havingValue = "SQL", matchIfMissing = true)
public class SqlSubscriptionRepository implements SubscriptionRepository {

    private final JdbcTemplate jdbcTemplate;

    public SqlSubscriptionRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void registerChat(long chatId) {
        jdbcTemplate.update("insert into chats(chat_id) values (?) on conflict (chat_id) do nothing", chatId);
    }

    @Override
    public boolean deleteChat(long chatId) {
        return jdbcTemplate.update("delete from chats where chat_id = ?", chatId) > 0;
    }

    @Override
    public boolean chatExists(long chatId) {
        Long id =
                jdbcTemplate.queryForObject("select chat_id from chats where chat_id = ? limit 1", Long.class, chatId);
        return id != null;
    }

    @Override
    @Transactional
    public LinkResponse addLink(long chatId, URI link, List<String> tags, List<String> filters) {
        Long linkId = jdbcTemplate.queryForObject(
                "insert into links(url) values (?) on conflict (url) do update set url = excluded.url returning id",
                Long.class,
                link.toString());

        Long subscriptionId = jdbcTemplate.queryForObject(
                "insert into subscriptions(chat_id, link_id) values (?, ?) "
                        + "on conflict (chat_id, link_id) do update set chat_id = excluded.chat_id returning id",
                Long.class,
                chatId,
                linkId);

        for (String filter : filters) {
            jdbcTemplate.update(
                    "insert into subscription_filters(subscription_id, filter_value) values (?, ?) "
                            + "on conflict (subscription_id, filter_value) do nothing",
                    subscriptionId,
                    filter);
        }

        for (String tag : tags) {
            Long tagId = jdbcTemplate.queryForObject(
                    "insert into tags(name) values (?) on conflict(name) do update set name = excluded.name returning id",
                    Long.class,
                    tag);
            jdbcTemplate.update(
                    "insert into subscription_tags(subscription_id, tag_id) values (?, ?) "
                            + "on conflict (subscription_id, tag_id) do nothing",
                    subscriptionId,
                    tagId);
        }

        return new LinkResponse(linkId, link, tags, filters);
    }

    @Override
    public boolean hasLink(long chatId, URI link) {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from subscriptions s join links l on l.id = s.link_id where s.chat_id = ? and l.url = ?",
                Integer.class,
                chatId,
                link.toString());
        return count != null && count > 0;
    }

    @Override
    @Transactional
    public LinkResponse removeLink(long chatId, URI link) {
        List<Long> subIds = jdbcTemplate.query(
                "select s.id from subscriptions s join links l on l.id=s.link_id where s.chat_id = ? and l.url = ?",
                (rs, rowNum) -> rs.getLong(1),
                chatId,
                link.toString());
        if (subIds.isEmpty()) {
            return null;
        }
        long subscriptionId = subIds.getFirst();
        long linkId = linkId(link).orElseThrow();
        List<String> tags = findTags(subscriptionId);
        List<String> filters = findFilters(subscriptionId);
        jdbcTemplate.update("delete from subscriptions where id = ?", subscriptionId);
        return new LinkResponse(linkId, link, tags, filters);
    }

    @Override
    public List<LinkResponse> links(long chatId, int limit, int offset) {
        return jdbcTemplate.query(
                "select s.id, l.id as link_id, l.url from subscriptions s join links l on l.id=s.link_id "
                        + "where s.chat_id = ? order by l.id limit ? offset ?",
                (rs, rowNum) -> {
                    long subscriptionId = rs.getLong("id");
                    long linkId = rs.getLong("link_id");
                    URI uri = URI.create(rs.getString("url"));
                    return new LinkResponse(linkId, uri, findTags(subscriptionId), findFilters(subscriptionId));
                },
                chatId,
                limit,
                offset);
    }

    @Override
    public long linksCount(long chatId) {
        Long count =
                jdbcTemplate.queryForObject("select count(*) from subscriptions where chat_id = ?", Long.class, chatId);
        return count == null ? 0L : count;
    }

    @Override
    public List<URI> trackedUris(int limit, int offset) {
        return jdbcTemplate.query(
                "select distinct l.url from links l join subscriptions s on s.link_id = l.id order by l.id limit ? offset ?",
                (rs, rowNum) -> URI.create(rs.getString(1)),
                limit,
                offset);
    }

    @Override
    public List<Long> chatsTracking(URI link) {
        return jdbcTemplate.query(
                "select s.chat_id from subscriptions s join links l on l.id = s.link_id where l.url = ?",
                (rs, rowNum) -> rs.getLong(1),
                link.toString());
    }

    @Override
    public OptionalLong linkId(URI link) {
        Long id =
                jdbcTemplate.queryForObject("select id from links where url = ? limit 1", Long.class, link.toString());
        return id == null ? OptionalLong.empty() : OptionalLong.of(id);
    }

    private List<String> findTags(long subscriptionId) {
        return jdbcTemplate.query(
                "select t.name from tags t join subscription_tags st on st.tag_id=t.id where st.subscription_id = ?",
                (rs, rowNum) -> rs.getString(1),
                subscriptionId);
    }

    private List<String> findFilters(long subscriptionId) {
        return jdbcTemplate.query(
                "select sf.filter_value from subscription_filters sf where sf.subscription_id = ?",
                (rs, rowNum) -> rs.getString(1),
                subscriptionId);
    }
}
