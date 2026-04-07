package backend.academy.linktracker.scrapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import backend.academy.linktracker.scrapper.model.AddLinkRequest;
import backend.academy.linktracker.scrapper.model.ApiErrorResponse;
import backend.academy.linktracker.scrapper.model.ListLinksResponse;
import backend.academy.linktracker.scrapper.model.RemoveLinkRequest;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ScrapperApiTest {

    @LocalServerPort
    int port;

    @Autowired
    RestClient.Builder restClientBuilder;

    @Test
    void addAndDeleteLinkFlow() {
        RestClient client =
                restClientBuilder.baseUrl("http://localhost:" + port).build();

        ResponseEntity<Void> createChat =
                client.post().uri("/tg-chat/1").retrieve().toBodilessEntity();
        assertEquals(HttpStatus.OK, createChat.getStatusCode());

        ResponseEntity<Void> addResponse = client.post()
                .uri("/links")
                .header("Tg-Chat-Id", "1")
                .body(new AddLinkRequest(URI.create("https://github.com/user/repo"), List.of("work"), List.of()))
                .retrieve()
                .toBodilessEntity();
        assertEquals(HttpStatus.OK, addResponse.getStatusCode());

        RestClient.RequestHeadersSpec<?> listRequest = client.get().uri("/links");
        listRequest.header("Tg-Chat-Id", "1");
        ListLinksResponse links = listRequest.retrieve().body(ListLinksResponse.class);
        assertNotNull(links);
        assertEquals(1, links.size());

        ResponseEntity<Void> deleteResponse = client.method(HttpMethod.DELETE)
                .uri("/links")
                .header(HttpHeaders.CONTENT_TYPE, "application/json")
                .header("Tg-Chat-Id", "1")
                .body(new RemoveLinkRequest(URI.create("https://github.com/user/repo")))
                .retrieve()
                .toBodilessEntity();
        assertEquals(HttpStatus.OK, deleteResponse.getStatusCode());
    }

    @Test
    void duplicateChatRegistrationReturnsConflictAndApiErrorResponse() {
        RestClient client =
                restClientBuilder.baseUrl("http://localhost:" + port).build();

        ResponseEntity<Void> first = client.post().uri("/tg-chat/42").retrieve().toBodilessEntity();
        assertEquals(HttpStatus.OK, first.getStatusCode());

        ResponseEntity<ApiErrorResponse> duplicate = client.post()
                .uri("/tg-chat/42")
                .exchange((ignoredRequest, httpResponse) -> {
                    ApiErrorResponse body = httpResponse.bodyTo(ApiErrorResponse.class);
                    HttpStatus status =
                            HttpStatus.valueOf(httpResponse.getStatusCode().value());
                    return new ResponseEntity<>(body, httpResponse.getHeaders(), status);
                });

        assertEquals(HttpStatus.CONFLICT, duplicate.getStatusCode());
        assertNotNull(duplicate.getBody());
        assertEquals("409", duplicate.getBody().code());
    }
}
