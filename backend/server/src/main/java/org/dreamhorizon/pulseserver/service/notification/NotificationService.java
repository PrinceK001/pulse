package org.dreamhorizon.pulseserver.service.notification;

import io.reactivex.rxjava3.core.Maybe;
import io.reactivex.rxjava3.core.Single;
import java.util.List;
import org.dreamhorizon.pulseserver.resources.notification.models.*;

public interface NotificationService {

  Single<NotificationBatchResponseDto> sendNotification(
      Long projectId, SendNotificationRequestDto request);

  Single<NotificationBatchResponseDto> sendNotificationAsync(
      Long projectId, SendNotificationRequestDto request);

  Single<List<NotificationChannelDto>> getChannels(Long projectId);

  Maybe<NotificationChannelDto> getChannel(Long channelId);

  Single<NotificationChannelDto> createChannel(Long projectId, CreateChannelRequestDto request);

  Single<NotificationChannelDto> updateChannel(Long channelId, UpdateChannelRequestDto request);

  Single<Boolean> deleteChannel(Long channelId);

  Single<List<NotificationTemplateDto>> getTemplates(Long projectId);

  Maybe<NotificationTemplateDto> getTemplate(Long templateId);

  Single<NotificationTemplateDto> createTemplate(Long projectId, CreateTemplateRequestDto request);

  Single<NotificationTemplateDto> updateTemplate(Long templateId, UpdateTemplateRequestDto request);

  Single<Boolean> deleteTemplate(Long templateId);

  Single<NotificationLogsResponseDto> getLogs(Long projectId, int limit, int offset);

  Single<NotificationLogsResponseDto> getLogsByBatch(Long projectId, String batchId);
}
