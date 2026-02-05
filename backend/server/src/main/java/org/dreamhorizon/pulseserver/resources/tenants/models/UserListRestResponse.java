package org.dreamhorizon.pulseserver.resources.tenants.models;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Response for listing users in a Firebase tenant.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserListRestResponse {
  private List<UserRestResponse> users;
  private String nextPageToken;
}

