package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.model.LinkUpdateRequest;
import backend.academy.linktracker.scrapper.properties.NotificationProperties;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.notification", name = "transport", havingValue = "KAFKA", matchIfMissing = true)
public class KafkaLinkNotificationSender implements LinkNotificationSender {
    private final KafkaTemplate<String, LinkUpdateRequest> kafkaTemplate;
    private final NotificationProperties notificationProperties;

    @Override
    public void sendUpdate(long linkId, URI uri, String description, List<Long> chatIds) {
        kafkaTemplate.send(notificationProperties.getTopic(), Long.toString(linkId), new LinkUpdateRequest(linkId, uri.toString(), description, chatIds));
    }

    @Override
    public void sendFailure(URI uri, List<Long> chatIds, String reason) {
        if (chatIds.isEmpty()) {
            return;
        }
        sendUpdate(0L, uri, "Ошибка обработки ссылки: " + reason, chatIds);
    }
}
