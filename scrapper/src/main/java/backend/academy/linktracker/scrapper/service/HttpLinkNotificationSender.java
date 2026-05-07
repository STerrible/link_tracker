package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.client.BotClient;
import backend.academy.linktracker.scrapper.model.LinkUpdateRequest;
import java.net.URI;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.notification", name = "transport", havingValue = "HTTP")
public class HttpLinkNotificationSender implements LinkNotificationSender {

    private final BotClient botClient;

    @Override
    public void sendUpdate(long linkId, URI uri, String description, List<Long> chatIds) {
        botClient.sendUpdate(new LinkUpdateRequest(linkId, uri.toString(), description, chatIds));
    }

    @Override
    public void sendFailure(URI uri, List<Long> chatIds, String reason) {
        if (chatIds.isEmpty()) {
            return;
        }
        botClient.sendUpdate(new LinkUpdateRequest(0L, uri.toString(), "Ошибка обработки ссылки: " + reason, chatIds));
    }
}
