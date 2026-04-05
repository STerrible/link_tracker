package backend.academy.linktracker.scrapper.repository.orm.entity;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "subscriptions")
public class SubscriptionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "chat_id", referencedColumnName = "chat_id")
    private ChatEntity chat;

    @ManyToOne(optional = false)
    @JoinColumn(name = "link_id")
    private LinkEntity link;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "subscription_filters", joinColumns = @JoinColumn(name = "subscription_id"))
    @Column(name = "filter_value", nullable = false)
    private List<String> filters = new ArrayList<>();

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "subscription_tags",
            joinColumns = @JoinColumn(name = "subscription_id"),
            inverseJoinColumns = @JoinColumn(name = "tag_id"))
    private List<TagEntity> tags = new ArrayList<>();

    public SubscriptionEntity() {}

    public SubscriptionEntity(ChatEntity chat, LinkEntity link) {
        this.chat = chat;
        this.link = link;
    }

    public Long getId() {
        return id;
    }

    public ChatEntity getChat() {
        return chat;
    }

    public LinkEntity getLink() {
        return link;
    }

    public List<String> getFilters() {
        return filters;
    }

    public List<TagEntity> getTags() {
        return tags;
    }
}
