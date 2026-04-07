package backend.academy.linktracker.scrapper.repository.orm;

import backend.academy.linktracker.scrapper.model.LinkResponse;
import backend.academy.linktracker.scrapper.repository.api.SubscriptionRepository;
import backend.academy.linktracker.scrapper.repository.orm.entity.ChatEntity;
import backend.academy.linktracker.scrapper.repository.orm.entity.LinkEntity;
import backend.academy.linktracker.scrapper.repository.orm.entity.SubscriptionEntity;
import backend.academy.linktracker.scrapper.repository.orm.entity.TagEntity;
import backend.academy.linktracker.scrapper.repository.orm.jpa.ChatJpaRepository;
import backend.academy.linktracker.scrapper.repository.orm.jpa.LinkJpaRepository;
import backend.academy.linktracker.scrapper.repository.orm.jpa.SubscriptionJpaRepository;
import backend.academy.linktracker.scrapper.repository.orm.jpa.TagJpaRepository;
import java.net.URI;
import java.util.List;
import java.util.OptionalLong;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@ConditionalOnProperty(prefix = "app.database", name = "access-type", havingValue = "ORM")
public class OrmSubscriptionRepository implements SubscriptionRepository {

    private final ChatJpaRepository chats;
    private final LinkJpaRepository links;
    private final SubscriptionJpaRepository subscriptions;
    private final TagJpaRepository tags;

    public OrmSubscriptionRepository(
            ChatJpaRepository chats,
            LinkJpaRepository links,
            SubscriptionJpaRepository subscriptions,
            TagJpaRepository tags) {
        this.chats = chats;
        this.links = links;
        this.subscriptions = subscriptions;
        this.tags = tags;
    }

    @Override
    public void registerChat(long chatId) {
        chats.save(new ChatEntity(chatId));
    }

    @Override
    public boolean deleteChat(long chatId) {
        if (!chats.existsById(chatId)) {
            return false;
        }
        chats.deleteById(chatId);
        return true;
    }

    @Override
    public boolean chatExists(long chatId) {
        return chats.existsById(chatId);
    }

    @Override
    @Transactional
    public LinkResponse addLink(long chatId, URI link, List<String> tagValues, List<String> filters) {
        ChatEntity chat = chats.getReferenceById(chatId);
        LinkEntity linkEntity =
                links.findByUrl(link.toString()).orElseGet(() -> links.save(new LinkEntity(link.toString())));
        SubscriptionEntity subscription = new SubscriptionEntity(chat, linkEntity);
        subscription.getFilters().addAll(filters);
        for (String tagName : tagValues) {
            TagEntity tag = tags.findByName(tagName).orElseGet(() -> tags.save(new TagEntity(tagName)));
            subscription.getTags().add(tag);
        }
        SubscriptionEntity saved = subscriptions.save(subscription);
        return toResponse(saved);
    }

    @Override
    public boolean hasLink(long chatId, URI link) {
        return subscriptions.findByChatChatIdAndLinkUrl(chatId, link.toString()).isPresent();
    }

    @Override
    @Transactional
    public LinkResponse removeLink(long chatId, URI link) {
        var found = subscriptions.findByChatChatIdAndLinkUrl(chatId, link.toString());
        if (found.isEmpty()) {
            return null;
        }
        SubscriptionEntity subscription = found.orElseThrow();
        LinkResponse response = toResponse(subscription);
        subscriptions.delete(subscription);
        return response;
    }

    @Override
    @Transactional(readOnly = true)
    public List<LinkResponse> links(long chatId, int limit, int offset) {
        int page = offset / limit;
        return subscriptions.findAllByChatChatId(chatId, PageRequest.of(page, limit)).stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    public long linksCount(long chatId) {
        return subscriptions.countByChatChatId(chatId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<URI> trackedUris(int limit, int offset) {
        int page = offset / limit;
        return subscriptions.findDistinctSubscriptions(PageRequest.of(page, limit)).stream()
                .map(s -> URI.create(s.getLink().getUrl()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<Long> chatsTracking(URI link) {
        return subscriptions.findAllByLinkUrl(link.toString()).stream()
                .map(subscription -> subscription.getChat().getChatId())
                .toList();
    }

    @Override
    public OptionalLong linkId(URI link) {
        return links.findByUrl(link.toString()).map(LinkEntity::getId).stream()
                .mapToLong(Long::longValue)
                .findFirst();
    }

    private LinkResponse toResponse(SubscriptionEntity entity) {
        return new LinkResponse(
                entity.getLink().getId(),
                URI.create(entity.getLink().getUrl()),
                entity.getTags().stream().map(TagEntity::getName).toList(),
                List.copyOf(entity.getFilters()));
    }
}
