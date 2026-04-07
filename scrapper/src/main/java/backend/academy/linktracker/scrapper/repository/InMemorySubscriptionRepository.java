package backend.academy.linktracker.scrapper.repository;

import backend.academy.linktracker.scrapper.model.LinkResponse;
import backend.academy.linktracker.scrapper.repository.api.SubscriptionRepository;
import java.net.URI;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public class InMemorySubscriptionRepository implements SubscriptionRepository {

    private final Set<Long> chats = ConcurrentHashMap.newKeySet();
    private final Map<Long, Map<URI, StoredLink>> byChat = new ConcurrentHashMap<>();
    private final AtomicLong idGenerator = new AtomicLong(1);

    @Override
    public void registerChat(long chatId) {
        chats.add(chatId);
        byChat.computeIfAbsent(chatId, ignored -> new ConcurrentHashMap<>());
    }

    @Override
    public boolean deleteChat(long chatId) {
        boolean existed = chats.remove(chatId);
        byChat.remove(chatId);
        return existed;
    }

    @Override
    public boolean chatExists(long chatId) {
        return chats.contains(chatId);
    }

    @Override
    public LinkResponse addLink(long chatId, URI link, List<String> tags, List<String> filters) {
        Map<URI, StoredLink> links = byChat.computeIfAbsent(chatId, ignored -> new ConcurrentHashMap<>());
        StoredLink candidate =
                new StoredLink(idGenerator.getAndIncrement(), link, List.copyOf(tags), List.copyOf(filters));
        StoredLink previous = links.putIfAbsent(link, candidate);
        return (previous == null ? candidate : previous).toResponse();
    }

    @Override
    public boolean hasLink(long chatId, URI link) {
        Map<URI, StoredLink> links = byChat.get(chatId);
        return links != null && links.containsKey(link);
    }

    @Override
    public LinkResponse removeLink(long chatId, URI link) {
        Map<URI, StoredLink> links = byChat.get(chatId);
        if (links == null) {
            return null;
        }
        StoredLink removed = links.remove(link);
        return removed == null ? null : removed.toResponse();
    }

    @Override
    public List<LinkResponse> links(long chatId, int limit, int offset) {
        Map<URI, StoredLink> links = byChat.get(chatId);
        if (links == null) {
            return List.of();
        }
        return links.values().stream()
                .skip(offset)
                .limit(limit)
                .map(StoredLink::toResponse)
                .toList();
    }

    @Override
    public long linksCount(long chatId) {
        Map<URI, StoredLink> links = byChat.get(chatId);
        return links == null ? 0 : links.size();
    }

    @Override
    public List<URI> trackedUris(int limit, int offset) {
        return allTrackedUris().stream().skip(offset).limit(limit).toList();
    }

    public Set<URI> allTrackedUris() {
        Set<URI> uris = ConcurrentHashMap.newKeySet();
        byChat.values().forEach(map -> uris.addAll(map.keySet()));
        return uris;
    }

    public Collection<StoredLink> allLinks() {
        List<StoredLink> links = new ArrayList<>();
        byChat.values().forEach(map -> links.addAll(map.values()));
        return links;
    }

    @Override
    public List<Long> chatsTracking(URI link) {
        List<Long> result = new ArrayList<>();
        byChat.forEach((chatId, links) -> {
            if (links.containsKey(link)) {
                result.add(chatId);
            }
        });
        return result;
    }

    @Override
    public OptionalLong linkId(URI link) {
        return byChat.values().stream()
                .map(links -> links.get(link))
                .filter(java.util.Objects::nonNull)
                .mapToLong(StoredLink::id)
                .findFirst();
    }

    public static final class StoredLink {
        private final long id;
        private final URI uri;
        private final List<String> tags;
        private final List<String> filters;
        private volatile Instant lastSeenUpdatedAt = Instant.EPOCH;

        private StoredLink(long id, URI uri, List<String> tags, List<String> filters) {
            this.id = id;
            this.uri = uri;
            this.tags = Collections.unmodifiableList(tags);
            this.filters = Collections.unmodifiableList(filters);
        }

        public URI uri() {
            return uri;
        }

        public long id() {
            return id;
        }

        public Instant lastSeenUpdatedAt() {
            return lastSeenUpdatedAt;
        }

        public void lastSeenUpdatedAt(Instant updatedAt) {
            this.lastSeenUpdatedAt = updatedAt;
        }

        public LinkResponse toResponse() {
            return new LinkResponse(id, uri, tags, filters);
        }
    }
}
