package backend.academy.linktracker.scrapper.service;

import java.net.URI;
import java.util.List;

public interface LinkNotificationSender {
    void sendUpdate(long linkId, URI uri, String description, List<Long> chatIds);

    void sendFailure(URI uri, List<Long> chatIds, String reason);
}
