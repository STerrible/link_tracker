package backend.academy.linktracker.scrapper.repository.orm.jpa;

import backend.academy.linktracker.scrapper.repository.orm.entity.SubscriptionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SubscriptionJpaRepository extends JpaRepository<SubscriptionEntity, Long> {
    Optional<SubscriptionEntity> findByChatChatIdAndLinkUrl(long chatId, String url);

    List<SubscriptionEntity> findAllByChatChatId(long chatId, Pageable pageable);

    long countByChatChatId(long chatId);

    @Query("select distinct s from SubscriptionEntity s")
    List<SubscriptionEntity> findDistinctSubscriptions(Pageable pageable);

    List<SubscriptionEntity> findAllByLinkUrl(String url);
}
