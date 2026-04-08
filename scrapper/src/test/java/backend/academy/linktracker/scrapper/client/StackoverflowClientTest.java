package backend.academy.linktracker.scrapper.client;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import backend.academy.linktracker.scrapper.properties.StackoverflowProperties;
import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class StackoverflowClientTest {

    @Test
    void fetchUpdateBuildsAnswerDescriptionAndTruncatesPreview() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        StackoverflowProperties properties = new StackoverflowProperties();
        properties.setKey("test-key");
        properties.setAccessToken("test-access");

        server.expect(requestTo("https://api.stackexchange.com/2.3/questions/123?site=stackoverflow&key=test-key"))
                .andExpect(method(GET))
                .andRespond(withStatus(org.springframework.http.HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"items":[{"title":"Question title","last_activity_date":1710000000}]}
                                """));

        server.expect(
                        requestTo(
                                "https://api.stackexchange.com/2.3/questions/123/answers?site=stackoverflow&sort=creation&order=desc&pagesize=20&filter=withbody&key=test-key"))
                .andExpect(method(GET))
                .andRespond(withStatus(org.springframework.http.HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"items":[{"creation_date":1710001000,"body_markdown":"%s","owner":{"display_name":"alice"}}]}
                                """.formatted("y".repeat(260))));
        server.expect(
                        requestTo(
                                "https://api.stackexchange.com/2.3/questions/123/comments?site=stackoverflow&sort=creation&order=desc&pagesize=20&filter=withbody&key=test-key"))
                .andExpect(method(GET))
                .andRespond(withStatus(org.springframework.http.HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"items\":[]}"));

        StackoverflowClient client = new StackoverflowClient(builder, properties);
        var update = client.fetchUpdate(URI.create("https://stackoverflow.com/questions/123/title"));

        assertTrue(update.isPresent());
        assertTrue(update.orElseThrow().description().contains("Ответ на вопрос: Question title"));
        String preview = update.orElseThrow()
                .description()
                .substring(update.orElseThrow().description().indexOf("Превью: ") + 8);
        assertTrue(preview.length() <= 200);
        server.verify();
    }

    @Test
    void fetchUpdateReturnsEmptyWhenSourceUnavailable() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        StackoverflowProperties properties = new StackoverflowProperties();
        properties.setKey("test-key");

        server.expect(requestTo("https://api.stackexchange.com/2.3/questions/123?site=stackoverflow&key=test-key"))
                .andExpect(method(GET))
                .andRespond(withStatus(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE));

        StackoverflowClient client = new StackoverflowClient(builder, properties);
        var update = client.fetchUpdate(URI.create("https://stackoverflow.com/questions/123/title"));

        assertFalse(update.isPresent());
        server.verify();
    }

    @Test
    void fetchUpdateChoosesNewestCommentOverOlderAnswer() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        StackoverflowProperties properties = new StackoverflowProperties();
        properties.setKey("test-key");

        server.expect(requestTo("https://api.stackexchange.com/2.3/questions/123?site=stackoverflow&key=test-key"))
                .andExpect(method(GET))
                .andRespond(withStatus(org.springframework.http.HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"items\":[{\"title\":\"Question title\",\"last_activity_date\":1710000000}]}"));
        server.expect(
                        requestTo(
                                "https://api.stackexchange.com/2.3/questions/123/answers?site=stackoverflow&sort=creation&order=desc&pagesize=20&filter=withbody&key=test-key"))
                .andExpect(method(GET))
                .andRespond(
                        withStatus(org.springframework.http.HttpStatus.OK)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(
                                        "{\"items\":[{\"creation_date\":1710001000,\"body_markdown\":\"answer\",\"owner\":{\"display_name\":\"alice\"}}]}"));
        server.expect(
                        requestTo(
                                "https://api.stackexchange.com/2.3/questions/123/comments?site=stackoverflow&sort=creation&order=desc&pagesize=20&filter=withbody&key=test-key"))
                .andExpect(method(GET))
                .andRespond(
                        withStatus(org.springframework.http.HttpStatus.OK)
                                .contentType(MediaType.APPLICATION_JSON)
                                .body(
                                        "{\"items\":[{\"creation_date\":1710002000,\"body_markdown\":\"comment\",\"owner\":{\"display_name\":\"bob\"}}]}"));

        StackoverflowClient client = new StackoverflowClient(builder, properties);
        var update = client.fetchUpdate(URI.create("https://stackoverflow.com/questions/123/title"));

        assertTrue(update.isPresent());
        assertTrue(update.orElseThrow().description().contains("Комментарий к вопросу"));
        server.verify();
    }
}
