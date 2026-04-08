package backend.academy.linktracker.scrapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import backend.academy.linktracker.scrapper.client.LinkSourceClient;
import backend.academy.linktracker.scrapper.client.LinkSourceUpdate;
import backend.academy.linktracker.scrapper.model.LinkUpdateRequest;
import backend.academy.linktracker.scrapper.properties.SchedulerProperties;
import backend.academy.linktracker.scrapper.repository.InMemorySubscriptionRepository;
import backend.academy.linktracker.scrapper.scheduler.LinkUpdateScheduler;
import backend.academy.linktracker.scrapper.service.LinkNotificationSender;
import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class LinkUpdateSchedulerTest {

    @Test
    void schedulerSendsSingleUpdateForSameLinkTrackedByMultipleChats() {
        InMemorySubscriptionRepository repository = new InMemorySubscriptionRepository();
        URI link = URI.create("https://github.com/user/repo");

        repository.registerChat(1L);
        repository.registerChat(2L);
        repository.addLink(1L, link, List.of(), List.of());
        repository.addLink(2L, link, List.of(), List.of());

        StubLinkSourceClient sourceClient = new StubLinkSourceClient(Instant.parse("2024-01-01T00:00:00Z"));
        RecordingNotificationSender notificationSender = new RecordingNotificationSender();
        SchedulerProperties properties = new SchedulerProperties();
        LinkUpdateScheduler scheduler =
                new LinkUpdateScheduler(repository, List.of(sourceClient), notificationSender, properties);

        scheduler.checkUpdates();
        scheduler.checkUpdates();

        assertEquals(1, notificationSender.calls.get());
        assertEquals(1L, notificationSender.lastUpdate.id());
        assertEquals(List.of(1L, 2L), notificationSender.lastUpdate.tgChatIds());
    }

    @Test
    void schedulerProcessesLinksInBatches() {
        InMemorySubscriptionRepository repository = new InMemorySubscriptionRepository();
        repository.registerChat(1L);
        for (int i = 0; i < 5; i++) {
            repository.addLink(1L, URI.create("https://github.com/owner/repo-" + i), List.of(), List.of());
        }

        RecordingNotificationSender notificationSender = new RecordingNotificationSender();
        SchedulerProperties properties = new SchedulerProperties();
        properties.setBatchSize(2);
        LinkUpdateScheduler scheduler = new LinkUpdateScheduler(
                repository, List.of(new StubLinkSourceClient(Instant.parse("2026-01-01T00:00:00Z"))), notificationSender, properties);

        scheduler.checkUpdates();

        assertEquals(5, notificationSender.calls.get());
    }

    @Test
    void schedulerIsolatesErrorsForBrokenLinkAndContinues() {
        InMemorySubscriptionRepository repository = new InMemorySubscriptionRepository();
        repository.registerChat(1L);
        URI broken = URI.create("https://github.com/owner/broken");
        URI healthy = URI.create("https://github.com/owner/healthy");
        repository.addLink(1L, broken, List.of(), List.of());
        repository.addLink(1L, healthy, List.of(), List.of());

        RecordingNotificationSender notificationSender = new RecordingNotificationSender();
        SchedulerProperties properties = new SchedulerProperties();
        LinkUpdateScheduler scheduler =
                new LinkUpdateScheduler(repository, List.of(new SelectiveFailClient(broken)), notificationSender, properties);

        scheduler.checkUpdates();

        assertEquals(1, notificationSender.calls.get());
        assertEquals(1, notificationSender.failures.size());
        assertTrue(notificationSender.failures.getFirst().contains("broken"));
    }

    private static final class StubLinkSourceClient implements LinkSourceClient {

        private final Instant updatedAt;

        private StubLinkSourceClient(Instant updatedAt) {
            this.updatedAt = updatedAt;
        }

        @Override
        public Optional<LinkSourceUpdate> fetchUpdate(URI uri) {
            return Optional.of(new LinkSourceUpdate(updatedAt, "test update"));
        }
    }

    private static final class RecordingNotificationSender implements LinkNotificationSender {

        private final AtomicInteger calls = new AtomicInteger();
        private final CopyOnWriteArrayList<String> failures = new CopyOnWriteArrayList<>();
        private LinkUpdateRequest lastUpdate;

        @Override
        public void sendUpdate(long linkId, URI uri, String description, List<Long> chatIds) {
            calls.incrementAndGet();
            lastUpdate = new LinkUpdateRequest(linkId, uri.toString(), description, chatIds);
        }

        @Override
        public void sendFailure(URI uri, List<Long> chatIds, String reason) {
            failures.add(uri.toString() + "|" + reason);
        }
    }

    private static final class SelectiveFailClient implements LinkSourceClient {
        private final URI brokenUri;
        private final java.util.Map<URI, Instant> updates = new ConcurrentHashMap<>();

        private SelectiveFailClient(URI brokenUri) {
            this.brokenUri = brokenUri;
        }

        @Override
        public Optional<LinkSourceUpdate> fetchUpdate(URI uri) {
            if (uri.equals(brokenUri)) {
                throw new RuntimeException("forced failure");
            }
            return Optional.of(new LinkSourceUpdate(
                    updates.computeIfAbsent(uri, key -> Instant.parse("2026-01-01T00:00:00Z")),
                    "ok"));
        }
    }
}
