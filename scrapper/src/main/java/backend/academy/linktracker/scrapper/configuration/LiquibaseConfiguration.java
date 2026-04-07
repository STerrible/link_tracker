package backend.academy.linktracker.scrapper.configuration;

import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "spring.liquibase", name = "enabled", havingValue = "true", matchIfMissing = true)
public class LiquibaseConfiguration {
    private static final String DEFAULT_CHANGE_LOG = "classpath:/migrations/changelog-master.yaml";

    @Bean
    public SpringLiquibase springLiquibase(
            DataSource dataSource,
            @Value("${spring.liquibase.change-log:" + DEFAULT_CHANGE_LOG + "}") String changeLog) {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog(changeLog);
        return liquibase;
    }

    @Bean
    public static BeanFactoryPostProcessor liquibaseBeforeJpaPostProcessor() {
        return beanFactory -> {
            if (beanFactory.containsBeanDefinition("entityManagerFactory")) {
                beanFactory.getBeanDefinition("entityManagerFactory").setDependsOn("springLiquibase");
            }
        };
    }
}
