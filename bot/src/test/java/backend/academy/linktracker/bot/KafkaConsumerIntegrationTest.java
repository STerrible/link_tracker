package backend.academy.linktracker.bot;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import backend.academy.linktracker.bot.model.LinkUpdateRequest;
import backend.academy.linktracker.bot.service.UpdateService;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.ContainerTestUtils;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@DirtiesContext
@SpringBootTest(
    classes = BotApplication.class,
    properties = {
        "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
        "spring.kafka.consumer.auto-offset-reset=earliest",
        "spring.kafka.consumer.group-id=bot-test-group",

        "spring.kafka.consumer.key-deserializer=org.apache.kafka.common.serialization.StringDeserializer",
        "spring.kafka.consumer.value-deserializer=org.springframework.kafka.support.serializer.JsonDeserializer",
        "spring.kafka.consumer.properties.spring.json.trusted.packages=*",
        "spring.kafka.consumer.properties.spring.json.value.default.type=backend.academy.linktracker.bot.model.LinkUpdateRequest",

        "spring.kafka.listener.missing-topics-fatal=false"
    })
@EmbeddedKafka(
    partitions = 1,
    topics = "link-updates",
    bootstrapServersProperty = "spring.kafka.bootstrap-servers")
@Import(KafkaConsumerIntegrationTest.KafkaTestProducerConfig.class)
class KafkaConsumerIntegrationTest {

    @Autowired
    private KafkaTemplate<String, LinkUpdateRequest> kafkaTemplate;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @MockitoBean
    private UpdateService updateService;

    @BeforeEach
    void waitForKafkaListenerAssignment() {
        Collection<MessageListenerContainer> containers =
            kafkaListenerEndpointRegistry.getListenerContainers();

        assertFalse(
            containers.isEmpty(),
            "No @KafkaListener containers were registered. Check that the Kafka listener class is scanned by BotApplication."
        );

        containers.forEach(container ->
            ContainerTestUtils.waitForAssignment(container, 1)
        );
    }

    @Test
    void shouldConsumeLinkUpdateFromKafka() throws Exception {
        LinkUpdateRequest event = new LinkUpdateRequest(
            1L,
            "https://example.com",
            "updated",
            List.of(1L, 2L)
        );

        kafkaTemplate
            .send("link-updates", "1", event)
            .get(10, TimeUnit.SECONDS);

        kafkaTemplate.flush();

        verify(updateService, timeout(10_000))
            .handleLinkUpdate(any(LinkUpdateRequest.class));
    }

    @TestConfiguration
    static class KafkaTestProducerConfig {

        @Bean
        KafkaTemplate<String, LinkUpdateRequest> kafkaTemplate(
            EmbeddedKafkaBroker embeddedKafkaBroker
        ) {
            Map<String, Object> props = KafkaTestUtils.producerProps(embeddedKafkaBroker);

            props.put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class
            );

            props.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                JsonSerializer.class
            );

            props.put(
                JsonSerializer.ADD_TYPE_INFO_HEADERS,
                false
            );

            return new KafkaTemplate<>(
                new DefaultKafkaProducerFactory<>(props)
            );
        }
    }
}
