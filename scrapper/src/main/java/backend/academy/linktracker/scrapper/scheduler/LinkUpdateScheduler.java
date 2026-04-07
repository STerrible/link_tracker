package backend.academy.linktracker.scrapper.scheduler;

import backend.academy.linktracker.scrapper.client.BotClient;
import backend.academy.linktracker.scrapper.client.LinkSourceClient;
import backend.academy.linktracker.scrapper.model.LinkUpdateRequest;
import backend.academy.linktracker.scrapper.repository.api.SubscriptionRepository;
import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class LinkUpdateScheduler {

    private static final Logger log = LoggerFactory.getLogger(LinkUpdateScheduler.class);

    private final SubscriptionRepository repository;
    private final List<LinkSourceClient> clients;
    private final BotClient botClient;
    private final Map<URI, Instant> lastSeenByUri = new ConcurrentHashMap<>();

    @Scheduled(fixedDelayString = "${app.scheduler.interval:30s}")
    public void checkUpdates() {
        int page = 0;
        int pageSize = 100;
        List<URI> uris;
        Set<URI> activeUris = new HashSet<>();
        do {
            uris = repository.trackedUris(pageSize, page * pageSize);
            activeUris.addAll(uris);
            for (URI trackedUri : uris) {
                Optional<Instant> updatedAt = clients.stream()
                        .map(client -> client.fetchUpdatedAt(trackedUri))
                        .flatMap(Optional::stream)
                        .max(Comparator.naturalOrder());

                if (updatedAt.isEmpty()) {
                    continue;
                }

                Instant currentUpdatedAt = updatedAt.orElseThrow();
                AtomicBoolean shouldSendUpdate = new AtomicBoolean(false);
                lastSeenByUri.compute(trackedUri, (uri, lastSeen) -> {
                    Instant knownLastSeen = lastSeen == null ? Instant.EPOCH : lastSeen;
                    if (currentUpdatedAt.isAfter(knownLastSeen)) {
                        shouldSendUpdate.set(true);
                        return currentUpdatedAt;
                    }
                    return knownLastSeen;
                });
                if (!shouldSendUpdate.get()) {
                    continue;
                }

                List<Long> chats = repository.chatsTracking(trackedUri);
                if (chats.isEmpty()) {
                    continue;
                }

                long linkId = repository.linkId(trackedUri).orElseThrow();
                botClient.sendUpdate(
                        new LinkUpdateRequest(linkId, trackedUri.toString(), "Обнаружены изменения", chats));
                log.atInfo()
                        .addKeyValue("link", trackedUri)
                        .addKeyValue("linkId", linkId)
                        .addKeyValue("chats", chats.size())
                        .log("scheduled_update_sent");
            }
            page++;
        } while (!uris.isEmpty());
        lastSeenByUri.keySet().retainAll(activeUris);
    }
}
