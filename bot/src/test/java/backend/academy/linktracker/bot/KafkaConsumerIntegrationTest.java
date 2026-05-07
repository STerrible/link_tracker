package backend.academy.linktracker.bot;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import backend.academy.linktracker.bot.model.LinkUpdateRequest;
import backend.academy.linktracker.bot.service.UpdateService;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(
        classes = TestBotApplication.class,
        properties = {
            "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
            "spring.kafka.consumer.auto-offset-reset=earliest"
        })
@EmbeddedKafka(partitions = 1, topics = {"link-updates"})
class KafkaConsumerIntegrationTest {

    @Autowired
    KafkaTemplate<String, LinkUpdateRequest> kafkaTemplate;

    @MockitoBean
    UpdateService updateService;

    @Test
    void shouldConsumeLinkUpdateFromKafka() {
        LinkUpdateRequest event = new LinkUpdateRequest(1L, "https://example.com", "updated", List.of(1L, 2L));
        kafkaTemplate.send("link-updates", "1", event);

        await().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> verify(updateService, timeout(1000)).handleLinkUpdate(org.mockito.ArgumentMatchers.any()));
    }
}
