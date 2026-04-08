package backend.academy.linktracker.scrapper.scheduler;

import backend.academy.linktracker.scrapper.client.LinkSourceClient;
import backend.academy.linktracker.scrapper.client.LinkSourceUpdate;
import backend.academy.linktracker.scrapper.properties.SchedulerProperties;
import backend.academy.linktracker.scrapper.repository.api.SubscriptionRepository;
import backend.academy.linktracker.scrapper.service.LinkNotificationSender;
import java.net.URI;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class LinkUpdateScheduler implements DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(LinkUpdateScheduler.class);

    private final SubscriptionRepository repository;
    private final List<LinkSourceClient> clients;
    private final LinkNotificationSender notificationSender;
    private final SchedulerProperties schedulerProperties;
    private final Map<URI, Instant> lastSeenByUri = new ConcurrentHashMap<>();
    private final ExecutorService executor;

    public LinkUpdateScheduler(
            SubscriptionRepository repository,
            List<LinkSourceClient> clients,
            LinkNotificationSender notificationSender,
            SchedulerProperties schedulerProperties) {
        this.repository = repository;
        this.clients = clients;
        this.notificationSender = notificationSender;
        this.schedulerProperties = schedulerProperties;
        this.executor = Executors.newFixedThreadPool(Math.max(1, schedulerProperties.getParallelism()));
    }

    @Scheduled(fixedDelayString = "${app.scheduler.interval:30s}")
    public void checkUpdates() {
        int page = 0;
        int pageSize = Math.max(1, schedulerProperties.getBatchSize());
        List<URI> uris;
        Set<URI> activeUris = new HashSet<>();
        do {
            uris = repository.trackedUris(pageSize, page * pageSize);
            activeUris.addAll(uris);
            if (schedulerProperties.getParallelism() <= 1) {
                uris.forEach(this::processUri);
            } else {
                var tasks = uris.stream()
                        .map(uri -> java.util.concurrent.CompletableFuture.runAsync(() -> processUri(uri), executor))
                        .toList();
                tasks.forEach(java.util.concurrent.CompletableFuture::join);
            }
            page++;
        } while (!uris.isEmpty());
        lastSeenByUri.keySet().retainAll(activeUris);
    }

    private void processUri(URI trackedUri) {
        try {
            Optional<LinkSourceUpdate> latestUpdate = clients.stream()
                    .map(client -> client.fetchUpdate(trackedUri))
                    .flatMap(Optional::stream)
                    .max(Comparator.comparing(LinkSourceUpdate::updatedAt));
            if (latestUpdate.isEmpty()) {
                return;
            }

            LinkSourceUpdate update = latestUpdate.orElseThrow();
            Instant previous = lastSeenByUri.getOrDefault(trackedUri, Instant.EPOCH);
            if (!update.updatedAt().isAfter(previous)) {
                return;
            }
            lastSeenByUri.put(trackedUri, update.updatedAt());

            List<Long> chats = repository.chatsTracking(trackedUri);
            if (chats.isEmpty()) {
                return;
            }
            long linkId = repository.linkId(trackedUri).orElseThrow();
            notificationSender.sendUpdate(linkId, trackedUri, update.description(), chats);
            log.atInfo()
                    .addKeyValue("link", trackedUri)
                    .addKeyValue("chats", chats.size())
                    .log("scheduled_update_sent");
        } catch (RuntimeException exception) {
            List<Long> chats = repository.chatsTracking(trackedUri);
            notificationSender.sendFailure(
                    trackedUri, chats, exception.getClass().getSimpleName());
            log.atWarn().addKeyValue("link", trackedUri).setCause(exception).log("scheduled_update_failed");
        }
    }

    @Override
    public void destroy() throws Exception {
        executor.shutdown();
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }
    }
}
