package backend.academy.linktracker.scrapper.service;

import backend.academy.linktracker.scrapper.model.AddLinkRequest;
import backend.academy.linktracker.scrapper.model.LinkResponse;
import backend.academy.linktracker.scrapper.model.ListLinksResponse;
import backend.academy.linktracker.scrapper.repository.api.SubscriptionRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class ScrapperService {

    private final SubscriptionRepository repository;

    public void registerChat(long chatId) {
        if (repository.chatExists(chatId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Chat already exists");
        }
        repository.registerChat(chatId);
    }

    public void deleteChat(long chatId) {
        if (!repository.deleteChat(chatId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found");
        }
    }

    public LinkResponse addLink(long chatId, AddLinkRequest request) {
        ensureChatExists(chatId);
        if (repository.hasLink(chatId, request.link())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Link already tracked");
        }

        List<String> tags = request.tags() == null ? List.of() : request.tags();
        List<String> filters = request.filters() == null ? List.of() : request.filters();
        return repository.addLink(chatId, request.link(), tags, filters);
    }

    public LinkResponse removeLink(long chatId, java.net.URI link) {
        ensureChatExists(chatId);
        LinkResponse removed = repository.removeLink(chatId, link);
        if (removed == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Link not found");
        }
        return removed;
    }

    public ListLinksResponse listLinks(long chatId) {
        ensureChatExists(chatId);
        var links = repository.links(chatId, 100, 0);
        return new ListLinksResponse(links, (int) repository.linksCount(chatId));
    }

    private void ensureChatExists(long chatId) {
        if (!repository.chatExists(chatId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Chat not found");
        }
    }
}
