package backend.academy.linktracker.bot;

import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import backend.academy.linktracker.bot.model.LinkUpdateRequest;
import backend.academy.linktracker.bot.service.UpdateService;
import java.time.Duration;
import java.util.List;
import java.util.Properties;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.springframework.kafka.support.serializer.JsonSerializer;

@SpringBootTest(classes = TestBotApplication.class)
@Testcontainers
class KafkaConsumerIntegrationTest {

    @Container
    static KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("apache/kafka-native:4.1.1"));

    @MockBean
    UpdateService updateService;

    @DynamicPropertySource
    static void kafkaProps(DynamicPropertyRegistry registry) {
        registry.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
    }

    @Test
    void shouldConsumeLinkUpdateFromKafka() {
        Properties props = new Properties();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class.getName());

        try (KafkaProducer<String, LinkUpdateRequest> producer = new KafkaProducer<>(props)) {
            LinkUpdateRequest event = new LinkUpdateRequest(1L, "https://example.com", "updated", List.of(1L, 2L));
            producer.send(new ProducerRecord<>("link-updates", "1", event));
            producer.flush();
        }

        await().atMost(Duration.ofSeconds(10)).untilAsserted(() -> verify(updateService, timeout(1000)).handleLinkUpdate(org.mockito.ArgumentMatchers.any()));
    }
}
