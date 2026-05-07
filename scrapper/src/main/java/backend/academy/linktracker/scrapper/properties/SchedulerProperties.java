package backend.academy.linktracker.scrapper.properties;

import java.time.Duration;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.scheduler")
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode
public class SchedulerProperties {
    private Duration interval = Duration.ofSeconds(30);
    private int batchSize = 100;
    private int parallelism = 1;
}
