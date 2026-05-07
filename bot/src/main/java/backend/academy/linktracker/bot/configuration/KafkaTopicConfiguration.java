package backend.academy.linktracker.bot.configuration;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
@EnableKafka
public class KafkaTopicConfiguration {

    @Bean
    NewTopic linkUpdatesTopic(@Value("${app.kafka.topic:link-updates}") String topic) {
        return TopicBuilder.name(topic).partitions(3).replicas(3).build();
    }
}
