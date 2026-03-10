package org.dreamhorizon.pulseserver.service.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.dreamhorizon.pulseserver.dao.notification.EmailSuppressionDao;
import org.dreamhorizon.pulseserver.dao.notification.NotificationChannelDao;
import org.dreamhorizon.pulseserver.dao.notification.NotificationLogDao;
import org.dreamhorizon.pulseserver.dao.notification.NotificationTemplateDao;
import org.dreamhorizon.pulseserver.resources.notification.models.CreateChannelRequestDto;
import org.dreamhorizon.pulseserver.resources.notification.models.CreateTemplateRequestDto;
import org.dreamhorizon.pulseserver.resources.notification.models.RecipientsDto;
import org.dreamhorizon.pulseserver.resources.notification.models.SendNotificationRequestDto;
import org.dreamhorizon.pulseserver.resources.notification.models.UpdateChannelRequestDto;
import org.dreamhorizon.pulseserver.resources.notification.models.UpdateTemplateRequestDto;
import org.dreamhorizon.pulseserver.service.notification.models.ChannelType;
import org.dreamhorizon.pulseserver.service.notification.models.EmailChannelConfig;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationChannel;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationLog;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationResult;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationStatus;
import org.dreamhorizon.pulseserver.service.notification.models.NotificationTemplate;
import org.dreamhorizon.pulseserver.service.notification.provider.NotificationProvider;
import org.dreamhorizon.pulseserver.service.notification.provider.NotificationProviderFactory;
import org.dreamhorizon.pulseserver.service.notification.queue.SqsNotificationQueue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationServiceImplTest {

  private static final String PROJECT_ID = "proj-123";
  private static final Long CHANNEL_ID = 1L;
  private static final Long TEMPLATE_ID = 1L;
  private static final Long LOG_ID = 100L;

  @Mock
  NotificationChannelDao channelDao;

  @Mock
  NotificationTemplateDao templateDao;

  @Mock
  NotificationLogDao logDao;

  @Mock
  EmailSuppressionDao suppressionDao;

  @Mock
  NotificationProviderFactory providerFactory;

  @Mock
  SqsNotificationQueue notificationQueue;

  @Mock
  NotificationProvider notificationProvider;

  ObjectMapper objectMapper = new ObjectMapper();

  NotificationServiceImpl service;

  @BeforeEach
  void setUp() {
    service = new NotificationServiceImpl(
        channelDao, templateDao, logDao, suppressionDao,
        providerFactory, notificationQueue, objectMapper);
  }

  private NotificationChannel channel(ChannelType type, String config) {
    return NotificationChannel.builder()
        .id(CHANNEL_ID)
        .projectId(PROJECT_ID)
        .channelType(type)
        .name("Test Channel")
        .config(config)
        .isActive(true)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
  }

  private NotificationTemplate template(String body) {
    return NotificationTemplate.builder()
        .id(TEMPLATE_ID)
        .projectId(PROJECT_ID)
        .eventName("alert")
        .channelType(ChannelType.EMAIL)
        .version(1)
        .body(body)
        .isActive(true)
        .createdAt(Instant.now())
        .updatedAt(Instant.now())
        .build();
  }

  private NotificationLog notificationLog() {
    return NotificationLog.builder()
        .id(LOG_ID)
        .projectId(PROJECT_ID)
        .batchId("batch-1")
        .idempotencyKey("key")
        .channelType(ChannelType.EMAIL)
        .channelId(CHANNEL_ID)
        .templateId(TEMPLATE_ID)
        .recipient("user@test.com")
        .subject("Subject")
        .status(NotificationStatus.PROCESSING)
        .attemptCount(1)
        .createdAt(Instant.now())
        .build();
  }

  @Nested
  class SendNotification {

    @Test
    void shouldSendNotificationSynchronouslySuccessfully() {
      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .channelTypes(List.of(ChannelType.EMAIL))
          .eventName("alert")
          .recipients(RecipientsDto.builder().emails(List.of("user@test.com")).build())
          .params(Map.of("name", "Test"))
          .build();

      NotificationChannel channel = channel(ChannelType.EMAIL,
          "{\"fromAddress\":\"noreply@test.com\",\"fromName\":\"Pulse\"}");
      NotificationTemplate template = template("{\"subject\":\"Alert\",\"html\":\"<p>Hello {{name}}</p>\"}");

      when(channelDao.getActiveChannelByType(eq(PROJECT_ID), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.just(channel));
      when(templateDao.getTemplateByEventNameAndChannel(eq(PROJECT_ID), eq("alert"), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.just(template));
      when(suppressionDao.isEmailSuppressed(eq(PROJECT_ID), eq("user@test.com")))
          .thenReturn(Single.just(false));
      when(providerFactory.getProvider(eq(ChannelType.EMAIL)))
          .thenReturn(java.util.Optional.of(notificationProvider));
      when(logDao.insertLogIfNotExists(any())).thenReturn(Single.just(true));
      when(logDao.getLogByIdempotency(eq(PROJECT_ID), any(), eq(ChannelType.EMAIL), eq("user@test.com")))
          .thenReturn(Maybe.just(notificationLog()));
      when(notificationProvider.send(any(), any()))
          .thenReturn(Single.just(NotificationResult.builder()
              .success(true)
              .externalId("ext-123")
              .latencyMs(50)
              .build()));
      when(logDao.updateLogStatus(anyLong(), any(), anyInt(), any(), any(), any(), any(), any()))
          .thenReturn(Single.just(1));

      var result = service.sendNotification(PROJECT_ID, request).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getBatchId()).isNotNull();
      assertThat(result.getTotalRecipients()).isEqualTo(1);
      assertThat(result.getQueued()).isEqualTo(1);
      assertThat(result.getFailed()).isEqualTo(0);
      assertThat(result.getResults()).hasSize(1);
      assertThat(result.getResults().get(0).getStatus()).isEqualTo(NotificationStatus.SENT);
      assertThat(result.getResults().get(0).getRecipient()).isEqualTo("user@test.com");

      verify(notificationProvider).send(any(), eq(template));
    }

    @Test
    void shouldHandleSuppressedEmailGracefully() {
      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .channelTypes(List.of(ChannelType.EMAIL))
          .eventName("alert")
          .recipients(RecipientsDto.builder().emails(List.of("suppressed@test.com")).build())
          .build();

      NotificationChannel channel = channel(ChannelType.EMAIL,
          "{\"fromAddress\":\"noreply@test.com\"}");
      NotificationTemplate template = template("{\"subject\":\"Alert\"}");

      when(channelDao.getActiveChannelByType(eq(PROJECT_ID), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.just(channel));
      when(templateDao.getTemplateByEventNameAndChannel(eq(PROJECT_ID), eq("alert"), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.just(template));

      var result = service.sendNotification(PROJECT_ID, request).blockingGet();

      assertThat(result).isNotNull();
    }

    @Test
    void shouldSkipDuplicateNotificationByIdempotency() {
      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .channelTypes(List.of(ChannelType.SLACK))
          .eventName("alert")
          .idempotencyKey("idem-key")
          .recipients(RecipientsDto.builder().slackChannelIds(List.of("C123")).build())
          .build();

      NotificationChannel channel = channel(ChannelType.SLACK,
          "{\"accessToken\":\"xoxb-xxx\"}");
      NotificationTemplate template = template("{\"text\":\"Hello\"}");

      when(channelDao.getActiveChannelByType(eq(PROJECT_ID), eq(ChannelType.SLACK)))
          .thenReturn(Maybe.just(channel));
      when(templateDao.getTemplateByEventNameAndChannel(eq(PROJECT_ID), eq("alert"), eq(ChannelType.SLACK)))
          .thenReturn(Maybe.just(template));
      when(providerFactory.getProvider(eq(ChannelType.SLACK)))
          .thenReturn(java.util.Optional.of(notificationProvider));
      when(logDao.insertLogIfNotExists(any())).thenReturn(Single.just(false));

      var result = service.sendNotification(PROJECT_ID, request).blockingGet();

      assertThat(result.getResults().get(0).getStatus()).isEqualTo(NotificationStatus.SKIPPED);
      assertThat(result.getResults().get(0).getErrorMessage()).isEqualTo("Duplicate notification (idempotency)");
      verify(notificationProvider, never()).send(any(), any());
    }

    @Test
    void shouldReturnEmptyWhenNoChannelConfigured() {
      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .channelTypes(List.of(ChannelType.EMAIL))
          .eventName("alert")
          .recipients(RecipientsDto.builder().emails(List.of("user@test.com")).build())
          .build();

      when(channelDao.getActiveChannelByType(eq(PROJECT_ID), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.empty());

      var result = service.sendNotification(PROJECT_ID, request).blockingGet();

      assertThat(result.getTotalRecipients()).isEqualTo(0);
      assertThat(result.getResults()).isEmpty();
    }

    @Test
    void shouldThrowWhenNoTemplateFound() {
      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .channelTypes(List.of(ChannelType.EMAIL))
          .eventName("unknown-event")
          .recipients(RecipientsDto.builder().emails(List.of("user@test.com")).build())
          .build();

      NotificationChannel channel = channel(ChannelType.EMAIL, "{}");
      when(channelDao.getActiveChannelByType(eq(PROJECT_ID), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.just(channel));
      when(templateDao.getTemplateByEventNameAndChannel(eq(PROJECT_ID), eq("unknown-event"), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.empty());

      service.sendNotification(PROJECT_ID, request)
          .test()
          .assertError(e -> e.getMessage().contains("No template found"));
    }

    @Test
    void shouldReturnEmptyWhenNoProviderForChannel() {
      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .channelTypes(List.of(ChannelType.TEAMS))
          .eventName("alert")
          .recipients(RecipientsDto.builder().teamsWorkflowUrls(List.of("https://webhook.teams.com")).build())
          .build();

      NotificationChannel channel = channel(ChannelType.TEAMS, "{}");
      NotificationTemplate template = template("{\"title\":\"Alert\"}");

      when(channelDao.getActiveChannelByType(eq(PROJECT_ID), eq(ChannelType.TEAMS)))
          .thenReturn(Maybe.just(channel));
      when(templateDao.getTemplateByEventNameAndChannel(eq(PROJECT_ID), eq("alert"), eq(ChannelType.TEAMS)))
          .thenReturn(Maybe.just(template));
      when(providerFactory.getProvider(eq(ChannelType.TEAMS)))
          .thenReturn(java.util.Optional.empty());

      var result = service.sendNotification(PROJECT_ID, request).blockingGet();

      assertThat(result.getTotalRecipients()).isEqualTo(0);
      assertThat(result.getResults()).isEmpty();
    }

    @Test
    void shouldReturnEmptyRecipientsWhenRecipientsNull() {
      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .channelTypes(List.of(ChannelType.EMAIL))
          .eventName("alert")
          .recipients(null)
          .build();

      NotificationChannel channel = channel(ChannelType.EMAIL, "{}");
      NotificationTemplate template = template("{\"subject\":\"Alert\"}");

      when(channelDao.getActiveChannelByType(eq(PROJECT_ID), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.just(channel));
      when(templateDao.getTemplateByEventNameAndChannel(
          eq(PROJECT_ID), eq("alert"), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.just(template));

      var result = service.sendNotification(PROJECT_ID, request).blockingGet();

      assertThat(result.getTotalRecipients()).isEqualTo(0);
      assertThat(result.getResults()).isEmpty();
    }

    @Test
    void shouldHandlePermanentFailure() {
      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .channelTypes(List.of(ChannelType.SLACK))
          .eventName("alert")
          .recipients(RecipientsDto.builder().slackChannelIds(List.of("C123")).build())
          .build();

      NotificationChannel channel = channel(ChannelType.SLACK, "{\"accessToken\":\"xoxb\"}");
      NotificationTemplate template = template("{\"text\":\"Hello\"}");
      template.setChannelType(ChannelType.SLACK);

      when(channelDao.getActiveChannelByType(eq(PROJECT_ID), eq(ChannelType.SLACK)))
          .thenReturn(Maybe.just(channel));
      when(templateDao.getTemplateByEventNameAndChannel(
          eq(PROJECT_ID), eq("alert"), eq(ChannelType.SLACK)))
          .thenReturn(Maybe.just(template));
      when(providerFactory.getProvider(eq(ChannelType.SLACK)))
          .thenReturn(java.util.Optional.of(notificationProvider));
      when(logDao.insertLogIfNotExists(any())).thenReturn(Single.just(true));
      when(logDao.getLogByIdempotency(eq(PROJECT_ID), any(), eq(ChannelType.SLACK), eq("C123")))
          .thenReturn(Maybe.just(notificationLog()));
      when(notificationProvider.send(any(), any()))
          .thenReturn(Single.just(NotificationResult.builder()
              .success(false)
              .permanentFailure(true)
              .errorMessage("channel_not_found")
              .latencyMs(10)
              .build()));
      when(logDao.updateLogStatus(anyLong(), any(), anyInt(), any(), any(), any(), any(), any()))
          .thenReturn(Single.just(1));

      var result = service.sendNotification(PROJECT_ID, request).blockingGet();

      assertThat(result.getResults()).hasSize(1);
      assertThat(result.getResults().get(0).getStatus()).isEqualTo(NotificationStatus.PERMANENT_FAILURE);
      assertThat(result.getResults().get(0).getErrorMessage()).isEqualTo("channel_not_found");
    }

    @Test
    void shouldReturnFailedWhenProviderThrows() {
      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .channelTypes(List.of(ChannelType.SLACK))
          .eventName("alert")
          .recipients(RecipientsDto.builder().slackChannelIds(List.of("C123")).build())
          .build();

      NotificationChannel channel = channel(ChannelType.SLACK, "{}");
      NotificationTemplate template = template("{\"text\":\"Hello\"}");
      template.setChannelType(ChannelType.SLACK);

      when(channelDao.getActiveChannelByType(eq(PROJECT_ID), eq(ChannelType.SLACK)))
          .thenReturn(Maybe.just(channel));
      when(templateDao.getTemplateByEventNameAndChannel(
          eq(PROJECT_ID), eq("alert"), eq(ChannelType.SLACK)))
          .thenReturn(Maybe.just(template));
      when(providerFactory.getProvider(eq(ChannelType.SLACK)))
          .thenReturn(java.util.Optional.of(notificationProvider));
      when(logDao.insertLogIfNotExists(any())).thenReturn(Single.just(true));
      when(logDao.getLogByIdempotency(eq(PROJECT_ID), any(), eq(ChannelType.SLACK), eq("C123")))
          .thenReturn(Maybe.just(notificationLog()));
      when(notificationProvider.send(any(), any()))
          .thenReturn(Single.error(new RuntimeException("Network error")));

      var result = service.sendNotification(PROJECT_ID, request).blockingGet();

      assertThat(result.getResults()).hasSize(1);
      assertThat(result.getResults().get(0).getStatus()).isEqualTo(NotificationStatus.FAILED);
      assertThat(result.getResults().get(0).getErrorMessage()).isEqualTo("Network error");
    }

    @Test
    void shouldExtractSubjectFromTitleWhenSubjectMissing() {
      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .channelTypes(List.of(ChannelType.EMAIL))
          .eventName("alert")
          .recipients(RecipientsDto.builder().emails(List.of("user@test.com")).build())
          .build();

      NotificationTemplate templateWithTitle = template("{\"title\":\"My Title\",\"html\":\"<p>x</p>\"}");
      NotificationChannel channel = channel(ChannelType.EMAIL, "{\"fromAddress\":\"x\"}");

      when(channelDao.getActiveChannelByType(eq(PROJECT_ID), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.just(channel));
      when(templateDao.getTemplateByEventNameAndChannel(
          eq(PROJECT_ID), eq("alert"), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.just(templateWithTitle));
      when(suppressionDao.isEmailSuppressed(eq(PROJECT_ID), eq("user@test.com")))
          .thenReturn(Single.just(false));
      when(providerFactory.getProvider(eq(ChannelType.EMAIL)))
          .thenReturn(java.util.Optional.of(notificationProvider));
      when(logDao.insertLogIfNotExists(any())).thenReturn(Single.just(true));
      when(logDao.getLogByIdempotency(eq(PROJECT_ID), any(), eq(ChannelType.EMAIL), eq("user@test.com")))
          .thenReturn(Maybe.just(notificationLog()));
      when(notificationProvider.send(any(), any()))
          .thenReturn(Single.just(NotificationResult.builder().success(true).latencyMs(50).build()));
      when(logDao.updateLogStatus(anyLong(), any(), anyInt(), any(), any(), any(), any(), any()))
          .thenReturn(Single.just(1));

      var result = service.sendNotification(PROJECT_ID, request).blockingGet();

      assertThat(result.getResults()).hasSize(1);
      assertThat(result.getResults().get(0).getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void shouldExtractSubjectFromTextWhenSubjectAndTitleMissing() {
      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .channelTypes(List.of(ChannelType.SLACK))
          .eventName("alert")
          .recipients(RecipientsDto.builder().slackChannelIds(List.of("C123")).build())
          .build();

      NotificationTemplate templateWithText = template("{\"text\":\"Slack message\"}");
      templateWithText.setChannelType(ChannelType.SLACK);
      NotificationChannel channel = channel(ChannelType.SLACK, "{\"accessToken\":\"x\"}");

      when(channelDao.getActiveChannelByType(eq(PROJECT_ID), eq(ChannelType.SLACK)))
          .thenReturn(Maybe.just(channel));
      when(templateDao.getTemplateByEventNameAndChannel(
          eq(PROJECT_ID), eq("alert"), eq(ChannelType.SLACK)))
          .thenReturn(Maybe.just(templateWithText));
      when(providerFactory.getProvider(eq(ChannelType.SLACK)))
          .thenReturn(java.util.Optional.of(notificationProvider));
      when(logDao.insertLogIfNotExists(any())).thenReturn(Single.just(true));
      when(logDao.getLogByIdempotency(eq(PROJECT_ID), any(), eq(ChannelType.SLACK), eq("C123")))
          .thenReturn(Maybe.just(notificationLog()));
      when(notificationProvider.send(any(), any()))
          .thenReturn(Single.just(NotificationResult.builder().success(true).latencyMs(50).build()));
      when(logDao.updateLogStatus(anyLong(), any(), anyInt(), any(), any(), any(), any(), any()))
          .thenReturn(Single.just(1));

      var result = service.sendNotification(PROJECT_ID, request).blockingGet();

      assertThat(result.getResults()).hasSize(1);
      assertThat(result.getResults().get(0).getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void shouldHandleInvalidJsonInTemplateBodyForSubjectExtraction() {
      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .channelTypes(List.of(ChannelType.SLACK))
          .eventName("alert")
          .recipients(RecipientsDto.builder().slackChannelIds(List.of("C123")).build())
          .build();

      NotificationTemplate templateWithInvalidJson = template("{{{invalid-json-for-subject");
      templateWithInvalidJson.setChannelType(ChannelType.SLACK);
      NotificationChannel channel = channel(ChannelType.SLACK, "{\"accessToken\":\"x\"}");

      when(channelDao.getActiveChannelByType(eq(PROJECT_ID), eq(ChannelType.SLACK)))
          .thenReturn(Maybe.just(channel));
      when(templateDao.getTemplateByEventNameAndChannel(
          eq(PROJECT_ID), eq("alert"), eq(ChannelType.SLACK)))
          .thenReturn(Maybe.just(templateWithInvalidJson));
      when(providerFactory.getProvider(eq(ChannelType.SLACK)))
          .thenReturn(java.util.Optional.of(notificationProvider));
      when(logDao.insertLogIfNotExists(any())).thenReturn(Single.just(true));
      when(logDao.getLogByIdempotency(eq(PROJECT_ID), any(), eq(ChannelType.SLACK), eq("C123")))
          .thenReturn(Maybe.just(notificationLog()));
      when(notificationProvider.send(any(), any()))
          .thenReturn(Single.just(NotificationResult.builder().success(true).latencyMs(50).build()));
      when(logDao.updateLogStatus(anyLong(), any(), anyInt(), any(), any(), any(), any(), any()))
          .thenReturn(Single.just(1));

      var result = service.sendNotification(PROJECT_ID, request).blockingGet();

      assertThat(result.getResults()).hasSize(1);
      assertThat(result.getResults().get(0).getStatus()).isEqualTo(NotificationStatus.SENT);
    }

    @Test
    void shouldSendToSlackWithBothChannelIdsAndUserIds() {
      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .channelTypes(List.of(ChannelType.SLACK))
          .eventName("alert")
          .recipients(RecipientsDto.builder()
              .slackChannelIds(List.of("C123"))
              .slackUserIds(List.of("U456"))
              .build())
          .build();

      NotificationChannel channel = channel(ChannelType.SLACK, "{\"accessToken\":\"xoxb\"}");
      NotificationTemplate template = template("{\"text\":\"Hello\"}");
      template.setChannelType(ChannelType.SLACK);

      when(channelDao.getActiveChannelByType(eq(PROJECT_ID), eq(ChannelType.SLACK)))
          .thenReturn(Maybe.just(channel));
      when(templateDao.getTemplateByEventNameAndChannel(
          eq(PROJECT_ID), eq("alert"), eq(ChannelType.SLACK)))
          .thenReturn(Maybe.just(template));
      when(providerFactory.getProvider(eq(ChannelType.SLACK)))
          .thenReturn(java.util.Optional.of(notificationProvider));
      when(logDao.insertLogIfNotExists(any())).thenReturn(Single.just(true));
      NotificationLog logC123 = notificationLog();
      logC123.setRecipient("C123");
      logC123.setChannelType(ChannelType.SLACK);
      NotificationLog logU456 = notificationLog();
      logU456.setRecipient("U456");
      logU456.setChannelType(ChannelType.SLACK);
      when(logDao.getLogByIdempotency(eq(PROJECT_ID), any(), eq(ChannelType.SLACK), eq("C123")))
          .thenReturn(Maybe.just(logC123));
      when(logDao.getLogByIdempotency(eq(PROJECT_ID), any(), eq(ChannelType.SLACK), eq("U456")))
          .thenReturn(Maybe.just(logU456));
      when(notificationProvider.send(any(), any()))
          .thenReturn(Single.just(NotificationResult.builder().success(true).latencyMs(50).build()));
      when(logDao.updateLogStatus(anyLong(), any(), anyInt(), any(), any(), any(), any(), any()))
          .thenReturn(Single.just(1));

      var result = service.sendNotification(PROJECT_ID, request).blockingGet();

      assertThat(result.getTotalRecipients()).isEqualTo(2);
      assertThat(result.getResults()).extracting(org.dreamhorizon.pulseserver.resources.notification.models.NotificationResultDto::getRecipient)
          .containsExactlyInAnyOrder("C123", "U456");
    }
  }

  @Nested
  class SendNotificationAsync {

    @Test
    void shouldQueueNotificationsSuccessfully() {
      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .channelTypes(List.of(ChannelType.EMAIL))
          .eventName("alert")
          .recipients(RecipientsDto.builder().emails(List.of("user@test.com")).build())
          .build();

      NotificationChannel channel = channel(ChannelType.EMAIL, "{\"fromAddress\":\"noreply@test.com\"}");
      NotificationTemplate template = template("{\"subject\":\"Alert\"}");

      when(channelDao.getActiveChannelByType(eq(PROJECT_ID), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.just(channel));
      when(templateDao.getTemplateByEventNameAndChannel(eq(PROJECT_ID), eq("alert"), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.just(template));
      when(suppressionDao.isEmailSuppressed(eq(PROJECT_ID), eq("user@test.com")))
          .thenReturn(Single.just(false));
      when(logDao.getLogByIdempotency(eq(PROJECT_ID), any(), eq(ChannelType.EMAIL), eq("user@test.com")))
          .thenReturn(Maybe.empty());
      when(logDao.insertLog(any())).thenReturn(Single.just(LOG_ID));
      when(notificationQueue.enqueue(any())).thenReturn(Single.just("msg-123"));

      var result = service.sendNotificationAsync(PROJECT_ID, request).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getTotalRecipients()).isEqualTo(1);
      assertThat(result.getQueued()).isEqualTo(1);
      assertThat(result.getResults().get(0).getStatus()).isEqualTo(NotificationStatus.QUEUED);
      assertThat(result.getResults().get(0).getExternalId()).isEqualTo("msg-123");

      verify(notificationQueue).enqueue(any());
    }

    @Test
    void shouldSkipDuplicateWhenIdempotencyLogExists() {
      SendNotificationRequestDto request = SendNotificationRequestDto.builder()
          .channelTypes(List.of(ChannelType.EMAIL))
          .eventName("alert")
          .idempotencyKey("key")
          .recipients(RecipientsDto.builder().emails(List.of("user@test.com")).build())
          .build();

      NotificationChannel channel = channel(ChannelType.EMAIL, "{}");
      NotificationTemplate template = template("{\"subject\":\"A\"}");

      when(channelDao.getActiveChannelByType(eq(PROJECT_ID), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.just(channel));
      when(templateDao.getTemplateByEventNameAndChannel(eq(PROJECT_ID), eq("alert"), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.just(template));
      when(suppressionDao.isEmailSuppressed(eq(PROJECT_ID), eq("user@test.com")))
          .thenReturn(Single.just(false));
      when(logDao.getLogByIdempotency(eq(PROJECT_ID), any(), eq(ChannelType.EMAIL), eq("user@test.com")))
          .thenReturn(Maybe.just(NotificationLog.builder().id(LOG_ID).build()));

      var result = service.sendNotificationAsync(PROJECT_ID, request).blockingGet();

      assertThat(result.getResults().get(0).getStatus()).isEqualTo(NotificationStatus.SKIPPED);
      assertThat(result.getResults().get(0).getErrorMessage()).contains("Duplicate");
      verify(notificationQueue, never()).enqueue(any());
    }
  }

  @Nested
  class ChannelOperations {

    @Test
    void shouldGetChannels() {
      NotificationChannel ch = channel(ChannelType.EMAIL, "{\"fromAddress\":\"x\"}");
      when(channelDao.getChannelsByProject(eq(PROJECT_ID)))
          .thenReturn(Single.just(List.of(ch)));

      var result = service.getChannels(PROJECT_ID).blockingGet();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getChannelType()).isEqualTo(ChannelType.EMAIL);
      assertThat(result.get(0).getName()).isEqualTo("Test Channel");
    }

    @Test
    void shouldGetChannelById() {
      NotificationChannel ch = channel(ChannelType.SLACK, "{}");
      when(channelDao.getChannelById(eq(CHANNEL_ID))).thenReturn(Maybe.just(ch));

      var result = service.getChannel(CHANNEL_ID).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(CHANNEL_ID);
    }

    @Test
    void shouldReturnEmptyWhenChannelNotFound() {
      when(channelDao.getChannelById(eq(999L))).thenReturn(Maybe.empty());

      var result = service.getChannel(999L);

      result.test().assertNoValues().assertComplete();
    }

    @Test
    void shouldCreateChannelSuccessfully() {
      EmailChannelConfig config = new EmailChannelConfig();
      config.setFromAddress("noreply@test.com");
      CreateChannelRequestDto request = CreateChannelRequestDto.builder()
          .channelType(ChannelType.EMAIL)
          .name("New Channel")
          .config(config)
          .build();

      when(channelDao.getActiveChannelByProjectAndType(eq(PROJECT_ID), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.empty());
      NotificationChannel saved = channel(ChannelType.EMAIL, "{\"fromAddress\":\"noreply@test.com\"}");
      when(channelDao.createChannel(any())).thenReturn(Single.just(CHANNEL_ID));
      when(channelDao.getChannelById(eq(CHANNEL_ID))).thenReturn(Maybe.just(saved));

      var result = service.createChannel(PROJECT_ID, request).blockingGet();

      assertThat(result).isNotNull();
    }

    @Test
    void shouldThrowWhenCreatingDuplicateChannelType() {
      EmailChannelConfig config = new EmailChannelConfig();
      config.setFromAddress("x");
      CreateChannelRequestDto request = CreateChannelRequestDto.builder()
          .channelType(ChannelType.EMAIL)
          .name("New Channel")
          .config(config)
          .build();

      when(channelDao.getActiveChannelByProjectAndType(eq(PROJECT_ID), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.just(channel(ChannelType.EMAIL, "{}")));

      service.createChannel(PROJECT_ID, request)
          .test()
          .assertError(e -> e.getMessage().contains("already exists"));
    }

    @Test
    void shouldUpdateChannelSuccessfully() {
      UpdateChannelRequestDto request = UpdateChannelRequestDto.builder()
          .name("Updated Name")
          .build();

      NotificationChannel existing = channel(ChannelType.EMAIL, "{}");
      NotificationChannel updated = channel(ChannelType.EMAIL, "{}");
      updated.setName("Updated Name");

      when(channelDao.getChannelById(eq(CHANNEL_ID)))
          .thenReturn(Maybe.just(existing))
          .thenReturn(Maybe.just(updated));
      when(channelDao.updateChannel(eq(CHANNEL_ID), any())).thenReturn(Single.just(1));

      var result = service.updateChannel(CHANNEL_ID, request).blockingGet();

      assertThat(result.getName()).isEqualTo("Updated Name");
    }

    @Test
    void shouldThrowWhenUpdatingNonExistentChannel() {
      UpdateChannelRequestDto request = UpdateChannelRequestDto.builder().name("X").build();
      when(channelDao.getChannelById(eq(999L))).thenReturn(Maybe.empty());

      service.updateChannel(999L, request)
          .test()
          .assertError(e -> e.getMessage().contains("Channel not found"));
    }

    @Test
    void shouldUpdateChannelWithConfigAndIsActive() {
      EmailChannelConfig newConfig = new EmailChannelConfig();
      newConfig.setFromAddress("new@test.com");
      UpdateChannelRequestDto request = UpdateChannelRequestDto.builder()
          .name("Updated")
          .config(newConfig)
          .isActive(false)
          .build();

      NotificationChannel existing = channel(ChannelType.EMAIL, "{\"fromAddress\":\"old@test.com\"}");
      NotificationChannel updated = channel(ChannelType.EMAIL, "{\"fromAddress\":\"new@test.com\"}");
      updated.setName("Updated");
      updated.setIsActive(false);

      when(channelDao.getChannelById(eq(CHANNEL_ID)))
          .thenReturn(Maybe.just(existing))
          .thenReturn(Maybe.just(updated));
      when(channelDao.updateChannel(eq(CHANNEL_ID), any())).thenReturn(Single.just(1));

      var result = service.updateChannel(CHANNEL_ID, request).blockingGet();

      assertThat(result.getName()).isEqualTo("Updated");
      assertThat(result.getIsActive()).isFalse();
    }

    @Test
    void shouldGetChannelWithInvalidJsonConfig() {
      NotificationChannel ch = channel(ChannelType.EMAIL, "{{{invalid-json");
      when(channelDao.getChannelById(eq(CHANNEL_ID))).thenReturn(Maybe.just(ch));

      var result = service.getChannel(CHANNEL_ID).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getConfig()).isEqualTo("{{{invalid-json");
    }

    @Test
    void shouldDeleteChannel() {
      when(channelDao.deleteChannel(eq(CHANNEL_ID))).thenReturn(Single.just(1));

      var result = service.deleteChannel(CHANNEL_ID).blockingGet();

      assertThat(result).isTrue();
    }

    @Test
    void shouldReturnFalseWhenDeleteAffectsNoRows() {
      when(channelDao.deleteChannel(eq(CHANNEL_ID))).thenReturn(Single.just(0));

      var result = service.deleteChannel(CHANNEL_ID).blockingGet();

      assertThat(result).isFalse();
    }
  }

  @Nested
  class TemplateOperations {

    @Test
    void shouldGetTemplates() {
      NotificationTemplate t = template("{}");
      when(templateDao.getTemplatesByProject(eq(PROJECT_ID))).thenReturn(Single.just(List.of(t)));

      var result = service.getTemplates(PROJECT_ID).blockingGet();

      assertThat(result).hasSize(1);
      assertThat(result.get(0).getEventName()).isEqualTo("alert");
    }

    @Test
    void shouldGetTemplateById() {
      NotificationTemplate t = template("{}");
      when(templateDao.getTemplateById(eq(TEMPLATE_ID))).thenReturn(Maybe.just(t));

      var result = service.getTemplate(TEMPLATE_ID).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getId()).isEqualTo(TEMPLATE_ID);
    }

    @Test
    void shouldCreateTemplateSuccessfully() {
      CreateTemplateRequestDto request = CreateTemplateRequestDto.builder()
          .eventName("alert")
          .channelType(ChannelType.EMAIL)
          .body(Map.of("subject", "Hello", "html", "<p>Hi</p>"))
          .build();

      when(templateDao.getLatestVersion(eq(PROJECT_ID), eq("alert"), eq(ChannelType.EMAIL)))
          .thenReturn(Single.just(0));
      when(templateDao.createTemplate(any())).thenReturn(Single.just(TEMPLATE_ID));
      when(templateDao.getTemplateById(eq(TEMPLATE_ID))).thenReturn(Maybe.just(template("{}")));

      var result = service.createTemplate(PROJECT_ID, request).blockingGet();

      assertThat(result).isNotNull();
    }

    @Test
    void shouldUpdateTemplateSuccessfully() {
      UpdateTemplateRequestDto request = UpdateTemplateRequestDto.builder()
          .eventName("updated")
          .build();

      NotificationTemplate existing = template("{}");
      NotificationTemplate updated = template("{}");
      updated.setEventName("updated");

      when(templateDao.getTemplateById(eq(TEMPLATE_ID)))
          .thenReturn(Maybe.just(existing))
          .thenReturn(Maybe.just(updated));
      when(templateDao.updateTemplate(eq(TEMPLATE_ID), any())).thenReturn(Single.just(1));

      var result = service.updateTemplate(TEMPLATE_ID, request).blockingGet();

      assertThat(result.getEventName()).isEqualTo("updated");
    }

    @Test
    void shouldThrowWhenUpdatingNonExistentTemplate() {
      UpdateTemplateRequestDto request = UpdateTemplateRequestDto.builder().eventName("x").build();
      when(templateDao.getTemplateById(eq(999L))).thenReturn(Maybe.empty());

      service.updateTemplate(999L, request)
          .test()
          .assertError(e -> e.getMessage().contains("Template not found"));
    }

    @Test
    void shouldUpdateTemplateWithBodyAndIsActive() {
      UpdateTemplateRequestDto request = UpdateTemplateRequestDto.builder()
          .body(Map.of("subject", "New Subject", "html", "<p>Updated</p>"))
          .isActive(false)
          .build();

      NotificationTemplate existing = template("{\"subject\":\"Old\"}");
      NotificationTemplate updated = template("{\"subject\":\"New Subject\",\"html\":\"<p>Updated</p>\"}");
      updated.setIsActive(false);

      when(templateDao.getTemplateById(eq(TEMPLATE_ID)))
          .thenReturn(Maybe.just(existing))
          .thenReturn(Maybe.just(updated));
      when(templateDao.updateTemplate(eq(TEMPLATE_ID), any())).thenReturn(Single.just(1));

      var result = service.updateTemplate(TEMPLATE_ID, request).blockingGet();

      assertThat(result.getIsActive()).isFalse();
    }

    @Test
    void shouldUpdateTemplateWithStringBody() {
      UpdateTemplateRequestDto request = UpdateTemplateRequestDto.builder()
          .body("{\"subject\":\"Direct JSON\",\"html\":\"<p>x</p>\"}")
          .build();

      NotificationTemplate existing = template("{}");
      NotificationTemplate updated = template("{\"subject\":\"Direct JSON\",\"html\":\"<p>x</p>\"}");

      when(templateDao.getTemplateById(eq(TEMPLATE_ID)))
          .thenReturn(Maybe.just(existing))
          .thenReturn(Maybe.just(updated));
      when(templateDao.updateTemplate(eq(TEMPLATE_ID), any())).thenReturn(Single.just(1));

      var result = service.updateTemplate(TEMPLATE_ID, request).blockingGet();

      assertThat(result).isNotNull();
    }

    @Test
    void shouldGetTemplateWithInvalidJsonBody() {
      NotificationTemplate t = template("{{{not-valid-json");
      when(templateDao.getTemplateById(eq(TEMPLATE_ID))).thenReturn(Maybe.just(t));

      var result = service.getTemplate(TEMPLATE_ID).blockingGet();

      assertThat(result).isNotNull();
      assertThat(result.getBody()).isEqualTo("{{{not-valid-json");
    }

    @Test
    void shouldDeleteTemplate() {
      when(templateDao.deleteTemplate(eq(TEMPLATE_ID))).thenReturn(Single.just(1));

      var result = service.deleteTemplate(TEMPLATE_ID).blockingGet();

      assertThat(result).isTrue();
    }
  }

  @Nested
  class LogOperations {

    @Test
    void shouldGetLogsWithAllFields() {
      NotificationLog log = notificationLog();
      log.setSentAt(Instant.now());
      log.setLatencyMs(50);
      log.setErrorMessage(null);
      log.setExternalId("ext-123");
      when(logDao.getLogsByProject(eq(PROJECT_ID), eq(50), eq(0)))
          .thenReturn(Single.just(List.of(log)));

      var result = service.getLogs(PROJECT_ID, 50, 0).blockingGet();

      assertThat(result.getLogs()).hasSize(1);
      assertThat(result.getLogs().get(0).getRecipient()).isEqualTo("user@test.com");
      assertThat(result.getLogs().get(0).getLatencyMs()).isEqualTo(50);
      assertThat(result.getLogs().get(0).getStatus()).isEqualTo("PROCESSING");
    }

    @Test
    void shouldGetLogs() {
      NotificationLog log = notificationLog();
      when(logDao.getLogsByProject(eq(PROJECT_ID), eq(50), eq(0)))
          .thenReturn(Single.just(List.of(log)));

      var result = service.getLogs(PROJECT_ID, 50, 0).blockingGet();

      assertThat(result.getLogs()).hasSize(1);
      assertThat(result.getLogs().get(0).getRecipient()).isEqualTo("user@test.com");
    }

    @Test
    void shouldGetLogsByBatch() {
      NotificationLog log = notificationLog();
      when(logDao.getLogsByBatch(eq(PROJECT_ID), eq("batch-1")))
          .thenReturn(Single.just(List.of(log)));

      var result = service.getLogsByBatch(PROJECT_ID, "batch-1").blockingGet();

      assertThat(result.getLogs()).hasSize(1);
      assertThat(result.getLogs().get(0).getBatchId()).isEqualTo("batch-1");
    }
  }

  @Nested
  class SerializeBodyErrorHandling {

    @Mock
    ObjectMapper mockObjectMapper;

    NotificationServiceImpl serviceWithMockMapper;

    @BeforeEach
    void setUpSerializer() {
      serviceWithMockMapper = new NotificationServiceImpl(
          channelDao, templateDao, logDao, suppressionDao,
          providerFactory, notificationQueue, mockObjectMapper);
    }

    @Test
    void shouldThrowWhenConfigSerializationFails() throws Exception {
      when(mockObjectMapper.writeValueAsString(any()))
          .thenThrow(new JsonProcessingException("Invalid") {});
      EmailChannelConfig config = new EmailChannelConfig();
      config.setFromAddress("x");
      CreateChannelRequestDto request = CreateChannelRequestDto.builder()
          .channelType(ChannelType.EMAIL)
          .name("Test")
          .config(config)
          .build();
      when(channelDao.getActiveChannelByProjectAndType(eq(PROJECT_ID), eq(ChannelType.EMAIL)))
          .thenReturn(Maybe.empty());

      serviceWithMockMapper.createChannel(PROJECT_ID, request)
          .test()
          .assertError(e -> e.getMessage().contains("Invalid body format"));
    }
  }
}
