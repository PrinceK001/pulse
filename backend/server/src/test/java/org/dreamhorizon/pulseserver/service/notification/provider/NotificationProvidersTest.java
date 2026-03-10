package org.dreamhorizon.pulseserver.service.notification.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Single;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.ext.web.client.HttpRequest;
import io.vertx.rxjava3.ext.web.client.HttpResponse;
import io.vertx.rxjava3.ext.web.client.WebClient;
import java.util.Map;
import org.dreamhorizon.pulseserver.config.NotificationConfig;
import org.dreamhorizon.pulseserver.service.notification.TemplateService;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationMessage;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationTemplate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class NotificationProvidersTest {

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

  @Mock
  WebClient webClient;

  @Mock
  HttpRequest<Buffer> httpRequest;

  @Mock
  HttpResponse<Buffer> httpResponse;

  @Mock
  TemplateService templateService;

  @Mock
  NotificationConfig notificationConfig;

  @BeforeEach
  void setUp() {
    when(templateService.renderText(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));
    when(templateService.renderJson(anyString(), any())).thenAnswer(inv -> inv.getArgument(0));
  }

  @Nested
  class TeamsProviderTests {

    org.dreamhorizon.pulseserver.service.notification.provider.TeamsNotificationProvider provider;

    @BeforeEach
    void setUp() {
      provider = new org.dreamhorizon.pulseserver.service.notification.provider.TeamsNotificationProvider(
          webClient, OBJECT_MAPPER, templateService);
    }

    @Test
    void shouldReturnTeamsChannelType() {
      assertThat(provider.getChannelType()).isEqualTo(ChannelType.TEAMS);
    }

    @Test
    void shouldReturnErrorWhenChannelConfigInvalid() throws Exception {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.TEAMS)
          .channelConfig("invalid-json")
          .recipient("https://webhook.teams.com")
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body("{\"title\":\"Alert\",\"text\":\"Hello\"}")
          .build();

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.getErrorMessage()).contains("Invalid Teams channel configuration");
      assertThat(result.isPermanentFailure()).isTrue();
    }

    @Test
    void shouldReturnErrorWhenRecipientEmpty() throws Exception {
      String configJson = "{\"type\":\"TEAMS\",\"workflowUrl\":\"https://webhook.teams.com\"}";

      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.TEAMS)
          .channelConfig(configJson)
          .recipient("")
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body("{\"title\":\"Alert\"}")
          .build();

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.getErrorMessage()).contains("Teams webhook URL not provided");
      assertThat(result.isPermanentFailure()).isTrue();
    }

    @Test
    void shouldSendSuccessfullyWithPlaintTextBody() throws Exception {
      String configJson = "{\"type\":\"TEAMS\",\"workflowUrl\":\"https://webhook.teams.com\"}";
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.TEAMS)
          .channelConfig(configJson)
          .recipient("https://webhook.teams.com")
          .params(Map.of())
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body("Plain text message")
          .build();

      when(webClient.postAbs(eq("https://webhook.teams.com"))).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(200);
      when(httpResponse.bodyAsString()).thenReturn("ok");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.getExternalId()).startsWith("teams-");
    }

    @Test
    void shouldSendSuccessfullyWithJsonBody() throws Exception {
      String configJson = "{\"type\":\"TEAMS\",\"workflowUrl\":\"https://webhook.teams.com\"}";
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.TEAMS)
          .channelConfig(configJson)
          .recipient("https://webhook.teams.com")
          .params(Map.of("name", "Test"))
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body("{\"title\":\"Alert\",\"text\":\"Hello {{name}}\"}")
          .build();

      when(webClient.postAbs(eq("https://webhook.teams.com"))).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(200);
      when(httpResponse.bodyAsString()).thenReturn("ok");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldReturnPermanentFailureOn400() throws Exception {
      String configJson = "{\"type\":\"TEAMS\",\"workflowUrl\":\"https://webhook.teams.com\"}";
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.TEAMS)
          .channelConfig(configJson)
          .recipient("https://webhook.teams.com")
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body("text")
          .build();

      when(webClient.postAbs(eq("https://webhook.teams.com"))).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(400);
      when(httpResponse.bodyAsString()).thenReturn("Bad Request");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.isPermanentFailure()).isTrue();
    }

    @Test
    void shouldReturnErrorWhenRecipientNull() throws Exception {
      String configJson = "{\"type\":\"TEAMS\",\"workflowUrl\":\"https://webhook.teams.com\"}";

      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.TEAMS)
          .channelConfig(configJson)
          .recipient(null)
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body("{\"title\":\"Alert\"}")
          .build();

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.getErrorMessage()).contains("Teams webhook URL not provided");
      assertThat(result.isPermanentFailure()).isTrue();
    }

    @Test
    void shouldReturnErrorOnHttpFailure() throws Exception {
      String configJson = "{\"type\":\"TEAMS\",\"workflowUrl\":\"https://webhook.teams.com\"}";
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.TEAMS)
          .channelConfig(configJson)
          .recipient("https://webhook.teams.com")
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body("text")
          .build();

      when(webClient.postAbs(eq("https://webhook.teams.com"))).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.error(new RuntimeException("Connection refused")));

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.getErrorMessage()).contains("HTTP request failed");
      assertThat(result.isPermanentFailure()).isFalse();
    }

    @Test
    void shouldReturnNonPermanentFailureOn500() throws Exception {
      String configJson = "{\"type\":\"TEAMS\",\"workflowUrl\":\"https://webhook.teams.com\"}";
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.TEAMS)
          .channelConfig(configJson)
          .recipient("https://webhook.teams.com")
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body("text")
          .build();

      when(webClient.postAbs(eq("https://webhook.teams.com"))).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(500);
      when(httpResponse.bodyAsString()).thenReturn("Internal Server Error");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.isPermanentFailure()).isFalse();
    }

    @Test
    void shouldSendWithAdaptiveCardBody() throws Exception {
      String configJson = "{\"type\":\"TEAMS\",\"workflowUrl\":\"https://webhook.teams.com\"}";
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.TEAMS)
          .channelConfig(configJson)
          .recipient("https://webhook.teams.com")
          .params(Map.of())
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body("{\"type\":\"AdaptiveCard\",\"body\":[{\"type\":\"TextBlock\",\"text\":\"Alert\"}],\"$schema\":\"http://adaptivecards.io/schemas/adaptive-card.json\",\"version\":\"1.4\"}")
          .build();

      when(webClient.postAbs(eq("https://webhook.teams.com"))).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(200);
      when(httpResponse.bodyAsString()).thenReturn("1");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldSendWithBodyContainingTypeKey() throws Exception {
      String configJson = "{\"type\":\"TEAMS\",\"workflowUrl\":\"https://webhook.teams.com\"}";
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.TEAMS)
          .channelConfig(configJson)
          .recipient("https://webhook.teams.com")
          .params(Map.of())
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body("{\"type\":\"Container\",\"body\":[{\"type\":\"TextBlock\",\"text\":\"Hello\"}]}")
          .build();

      when(webClient.postAbs(eq("https://webhook.teams.com"))).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(200);
      when(httpResponse.bodyAsString()).thenReturn("1");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldSendWithSimpleCardWithEmptyText() throws Exception {
      String configJson = "{\"type\":\"TEAMS\",\"workflowUrl\":\"https://webhook.teams.com\"}";
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.TEAMS)
          .channelConfig(configJson)
          .recipient("https://webhook.teams.com")
          .params(Map.of())
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body("{\"title\":\"Alert only\"}")
          .build();

      when(webClient.postAbs(eq("https://webhook.teams.com"))).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(200);
      when(httpResponse.bodyAsString()).thenReturn("1");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldHandleTemplateParseFailureWithTextCardFallback() throws Exception {
      String configJson = "{\"type\":\"TEAMS\",\"workflowUrl\":\"https://webhook.teams.com\"}";
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.TEAMS)
          .channelConfig(configJson)
          .recipient("https://webhook.teams.com")
          .params(Map.of())
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body("{\"title\":\"Alert\",\"text\":{{broken")
          .build();

      when(webClient.postAbs(eq("https://webhook.teams.com"))).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.statusCode()).thenReturn(200);
      when(httpResponse.bodyAsString()).thenReturn("1");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
    }
  }

  @Nested
  class SlackProviderTests {

    org.dreamhorizon.pulseserver.service.notification.provider.SlackNotificationProvider provider;

    @BeforeEach
    void setUp() {
      provider = new org.dreamhorizon.pulseserver.service.notification.provider.SlackNotificationProvider(
          webClient, OBJECT_MAPPER, templateService);
    }

    @Test
    void shouldReturnSlackChannelType() {
      assertThat(provider.getChannelType()).isEqualTo(ChannelType.SLACK);
    }

    @Test
    void shouldReturnErrorWhenChannelConfigInvalid() {
      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig("invalid")
          .recipient("C123")
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body("{\"text\":\"Hi\"}")
          .build();

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.getErrorMessage()).contains("Invalid Slack channel configuration");
      assertThat(result.isPermanentFailure()).isTrue();
    }

    @Test
    void shouldReturnErrorWhenAccessTokenMissing() throws Exception {
      String configJson = "{\"type\":\"SLACK\",\"accessToken\":\"\"}";

      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(configJson)
          .recipient("C123")
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body("{\"text\":\"Hi\"}")
          .build();

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.getErrorMessage()).contains("Slack access token not configured");
      assertThat(result.isPermanentFailure()).isTrue();
    }

    @Test
    void shouldSendSuccessfullyWithPlainText() throws Exception {
      String configJson = "{\"type\":\"SLACK\",\"accessToken\":\"xoxb-token\"}";

      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(configJson)
          .recipient("C123")
          .params(Map.of())
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body("Hello world")
          .build();

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.bodyAsString()).thenReturn("{\"ok\":true,\"ts\":\"123.456\"}");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.getExternalId()).isEqualTo("123.456");
    }

    @Test
    void shouldReturnErrorWhenSlackApiReturnsError() throws Exception {
      String configJson = "{\"type\":\"SLACK\",\"accessToken\":\"xoxb-token\"}";

      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(configJson)
          .recipient("C123")
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body("Hi")
          .build();

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.bodyAsString()).thenReturn("{\"ok\":false,\"error\":\"channel_not_found\"}");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.getErrorMessage()).contains("channel_not_found");
      assertThat(result.isPermanentFailure()).isTrue();
    }

    @Test
    void shouldReturnErrorWhenSlackApiReturnsTransientError() throws Exception {
      String configJson = "{\"type\":\"SLACK\",\"accessToken\":\"xoxb-token\"}";

      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(configJson)
          .recipient("C123")
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body("Hi")
          .build();

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.bodyAsString()).thenReturn("{\"ok\":false,\"error\":\"ratelimited\"}");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.isPermanentFailure()).isFalse();
    }

    @Test
    void shouldReturnErrorWhenSlackApiReturnsUnknownError() throws Exception {
      String configJson = "{\"type\":\"SLACK\",\"accessToken\":\"xoxb-token\"}";

      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(configJson)
          .recipient("C123")
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body("Hi")
          .build();

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.bodyAsString()).thenReturn("{\"ok\":false}");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.getErrorMessage()).contains("Unknown error");
    }

    @Test
    void shouldReturnErrorWhenSlackResponseNotParseable() throws Exception {
      String configJson = "{\"type\":\"SLACK\",\"accessToken\":\"xoxb-token\"}";

      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(configJson)
          .recipient("C123")
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body("Hi")
          .build();

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.bodyAsString()).thenReturn("not-json");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.getErrorMessage()).contains("Failed to parse Slack response");
      assertThat(result.isPermanentFailure()).isFalse();
    }

    @Test
    void shouldReturnSuccessWithoutTsInResponse() throws Exception {
      String configJson = "{\"type\":\"SLACK\",\"accessToken\":\"xoxb-token\"}";

      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(configJson)
          .recipient("C123")
          .params(Map.of())
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body("Hello")
          .build();

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.bodyAsString()).thenReturn("{\"ok\":true}");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
      assertThat(result.getExternalId()).isNull();
    }

    @Test
    void shouldReturnErrorOnHttpFailure() throws Exception {
      String configJson = "{\"type\":\"SLACK\",\"accessToken\":\"xoxb-token\"}";

      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(configJson)
          .recipient("C123")
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body("Hi")
          .build();

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.error(new RuntimeException("Connection refused")));

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isFalse();
      assertThat(result.getErrorMessage()).contains("HTTP request failed");
      assertThat(result.getErrorMessage()).contains("Connection refused");
      assertThat(result.isPermanentFailure()).isFalse();
    }

    @Test
    void shouldIncludeBotNameAndIconEmojiInPayload() throws Exception {
      String configJson =
          "{\"type\":\"SLACK\",\"accessToken\":\"xoxb-token\",\"botName\":\"PulseBot\",\"iconEmoji\":\":robot_face:\"}";

      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(configJson)
          .recipient("C123")
          .params(Map.of())
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body("Hello")
          .build();

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.bodyAsString()).thenReturn("{\"ok\":true,\"ts\":\"1.0\"}");

      var result = provider.send(message, template).blockingGet();
      assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldSendWithJsonBodyContainingBlocks() throws Exception {
      String configJson = "{\"type\":\"SLACK\",\"accessToken\":\"xoxb-token\"}";

      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(configJson)
          .recipient("C123")
          .params(Map.of("name", "World"))
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body("{\"text\":\"Hi {{name}}\",\"blocks\":[{\"type\":\"section\",\"text\":{\"type\":\"mrkdwn\",\"text\":\"Hello\"}}]}")
          .build();

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.bodyAsString()).thenReturn("{\"ok\":true,\"ts\":\"2.0\"}");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldSendWithJsonBodyAsArray() throws Exception {
      String configJson = "{\"type\":\"SLACK\",\"accessToken\":\"xoxb-token\"}";

      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(configJson)
          .recipient("C123")
          .params(Map.of())
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body("[{\"type\":\"section\",\"text\":{\"type\":\"plain_text\",\"text\":\"Alert\"}}]")
          .build();

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.bodyAsString()).thenReturn("{\"ok\":true,\"ts\":\"3.0\"}");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldSendWithJsonBodyTextOnlyNoBlocks() throws Exception {
      String configJson = "{\"type\":\"SLACK\",\"accessToken\":\"xoxb-token\"}";

      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(configJson)
          .recipient("C123")
          .params(Map.of())
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body("{\"text\":\"Simple text message\"}")
          .build();

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.bodyAsString()).thenReturn("{\"ok\":true,\"ts\":\"4.0\"}");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldSendWithJsonBodyNoTextUsesRender() throws Exception {
      String configJson = "{\"type\":\"SLACK\",\"accessToken\":\"xoxb-token\"}";

      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(configJson)
          .recipient("C123")
          .params(Map.of())
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body("{\"other\":\"value\"}")
          .build();

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.bodyAsString()).thenReturn("{\"ok\":true,\"ts\":\"5.0\"}");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldHandleTemplateParseFailureWithPlainTextFallback() throws Exception {
      String configJson = "{\"type\":\"SLACK\",\"accessToken\":\"xoxb-token\"}";

      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(configJson)
          .recipient("C123")
          .params(Map.of())
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body("{\"text\":{{invalid json")
          .build();

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.bodyAsString()).thenReturn("{\"ok\":true,\"ts\":\"6.0\"}");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldExtractFallbackTextFromBlocksWithObjectText() throws Exception {
      String configJson = "{\"type\":\"SLACK\",\"accessToken\":\"xoxb-token\"}";

      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(configJson)
          .recipient("C123")
          .params(Map.of())
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body("{\"blocks\":[{\"type\":\"section\",\"text\":{\"type\":\"mrkdwn\",\"text\":\"Fallback from block\"}}]}")
          .build();

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.bodyAsString()).thenReturn("{\"ok\":true,\"ts\":\"7.0\"}");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
    }

    @Test
    void shouldExtractFallbackTextFromBlocksWithTextualText() throws Exception {
      String configJson = "{\"type\":\"SLACK\",\"accessToken\":\"xoxb-token\"}";

      NotificationMessage message = NotificationMessage.builder()
          .projectId("proj-1")
          .channelType(ChannelType.SLACK)
          .channelConfig(configJson)
          .recipient("C123")
          .params(Map.of())
          .build();
      NotificationTemplate template = NotificationTemplate.builder()
          .body("{\"blocks\":[{\"type\":\"section\",\"text\":\"plain text\"}]}")
          .build();

      when(webClient.postAbs(anyString())).thenReturn(httpRequest);
      when(httpRequest.putHeader(anyString(), anyString())).thenReturn(httpRequest);
      when(httpRequest.timeout(anyLong())).thenReturn(httpRequest);
      when(httpRequest.rxSendJsonObject(any())).thenReturn(Single.just(httpResponse));
      when(httpResponse.bodyAsString()).thenReturn("{\"ok\":true,\"ts\":\"8.0\"}");

      var result = provider.send(message, template).blockingGet();

      assertThat(result.isSuccess()).isTrue();
    }
  }

  @Nested
  class EmailProviderTests {

    @Test
    void shouldReturnEmailChannelType() {
      when(notificationConfig.getRegion()).thenReturn("us-east-1");
      org.dreamhorizon.pulseserver.service.notification.provider.EmailNotificationProvider provider =
          new org.dreamhorizon.pulseserver.service.notification.provider.EmailNotificationProvider(
              OBJECT_MAPPER, templateService, notificationConfig);

      assertThat(provider.getChannelType()).isEqualTo(ChannelType.EMAIL);
    }
  }
}
