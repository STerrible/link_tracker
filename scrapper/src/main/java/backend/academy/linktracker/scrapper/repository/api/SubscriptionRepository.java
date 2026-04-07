package backend.academy.linktracker.scrapper.repository.api;

import backend.academy.linktracker.scrapper.model.LinkResponse;
import java.net.URI;
import java.util.List;
import java.util.OptionalLong;

public interface SubscriptionRepository {

    void registerChat(long chatId);

    boolean deleteChat(long chatId);

    boolean chatExists(long chatId);

    LinkResponse addLink(long chatId, URI link, List<String> tags, List<String> filters);

    boolean hasLink(long chatId, URI link);

    LinkResponse removeLink(long chatId, URI link);

    List<LinkResponse> links(long chatId, int limit, int offset);

    long linksCount(long chatId);

    List<URI> trackedUris(int limit, int offset);

    List<Long> chatsTracking(URI link);

    OptionalLong linkId(URI link);
}
