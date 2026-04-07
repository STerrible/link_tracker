package backend.academy.linktracker.scrapper;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import backend.academy.linktracker.scrapper.repository.api.SubscriptionRepository;
import backend.academy.linktracker.scrapper.repository.orm.OrmSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@TestPropertySource(properties = "app.database.access-type=ORM")
class OrmAccessTypeSelectionTest {

    @Autowired
    private SubscriptionRepository repository;

    @Test
    void ormBeanSelected() {
        assertInstanceOf(OrmSubscriptionRepository.class, repository);
    }
}
