package backend.academy.linktracker.bot.service;

import backend.academy.linktracker.bot.model.LinkUpdateRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class KafkaLinkUpdateConsumer {
    private final UpdateService updateService;

    @KafkaListener(topics = "${app.kafka.topic:link-updates}", groupId = "${app.kafka.group-id:bot}")
    public void consume(LinkUpdateRequest request) {
        updateService.handleLinkUpdate(request);
    }
}
