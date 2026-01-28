package org.dreamhorizon.pulsealertscron.models;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CronTask {
  private Integer id;
  private String url;
  private String tenantId;
  
  /**
   * Constructor for backward compatibility.
   */
  public CronTask(Integer id, String url) {
    this.id = id;
    this.url = url;
    this.tenantId = "default";  // Default tenant for backward compatibility
  }
}

