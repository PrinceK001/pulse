package org.dreamhorizon.pulseserver.service.churn.models;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserChurnFeatures {
  @JsonProperty("user_id")
  private String userId;
  
  @JsonProperty("device_model")
  private String deviceModel;
  
  @JsonProperty("os_version")
  private String osVersion;
  
  @JsonProperty("app_version")
  private String appVersion;
  
  @JsonProperty("sessions_last_7_days")
  private Integer sessionsLast7Days;
  
  @JsonProperty("sessions_last_30_days")
  private Integer sessionsLast30Days;
  
  @JsonProperty("days_since_last_session")
  private Integer daysSinceLastSession;
  
  @JsonProperty("avg_session_duration")
  private Long avgSessionDuration; // milliseconds
  
  @JsonProperty("unique_screens_last_7_days")
  private Integer uniqueScreensLast7Days;
  
  @JsonProperty("crash_count_last_7_days")
  private Integer crashCountLast7Days;
  
  @JsonProperty("anr_count_last_7_days")
  private Integer anrCountLast7Days;
  
  @JsonProperty("frozen_frame_rate")
  private Double frozenFrameRate;
}

