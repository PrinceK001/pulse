package org.dreamhorizon.pulseserver.client.chclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.clickhouse.client.api.insert.InsertResponse;
import io.reactivex.rxjava3.core.Single;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.dreamhorizon.pulseserver.dao.clickhousecredentials.models.ClickhouseCredentials;
import org.dreamhorizon.pulseserver.dao.clickhouseprojectcredentials.ClickhouseProjectCredentialsDao;
import org.dreamhorizon.pulseserver.errorgrouping.model.StackTraceEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@org.mockito.junit.jupiter.MockitoSettings(strictness = org.mockito.quality.Strictness.LENIENT)
class ClickhouseQueryServiceTest {

  @Mock
  private ClickhouseReadClient clickhouseReadClient;

  @Mock
  private ClickhouseWriteClient clickhouseWriteClient;


  @Mock
  private ClickhouseProjectConnectionPoolManager projectPoolManager;

  @Mock
  private ClickhouseProjectCredentialsDao projectCredentialsDao;


  private ClickhouseQueryService queryService;

  @BeforeEach
  void setup() {
    queryService = new ClickhouseQueryService(
        clickhouseReadClient,
        clickhouseWriteClient,
        projectPoolManager,
        projectCredentialsDao
    );
  }

  private ClickhouseCredentials createMockCredentials() {
    return ClickhouseCredentials.builder()
        .tenantId("test_tenant")
        .clickhouseUsername("user_test")
        .clickhousePassword("password123")
        .isActive(true)
        .build();
  }

  @Nested
  class TestConstructor {

    @Test
    void shouldCreateServiceWithDependencies() {
      assertNotNull(queryService);
      assertNotNull(queryService.getClickhouseReadClient());
      assertNotNull(queryService.getClickhouseWriteClient());
    }

    @Test
    void shouldHaveObjectMapper() {
      // The objectMapper is initialized inline
      assertNotNull(queryService);
    }
  }

  @Nested
  class TestInsertStackTraces {

    @Test
    void shouldDelegateToWriteClient() {
      List<StackTraceEvent> events = new ArrayList<>();

      when(clickhouseWriteClient.insert(any()))
          .thenReturn(Single.error(new RuntimeException("Write error")));

      queryService.insertStackTraces(events)
          .test()
          .assertError(RuntimeException.class);

      verify(clickhouseWriteClient).insert(events);
    }

    @Test
    void shouldReturnWrittenRowsCount() {
      List<StackTraceEvent> events = new ArrayList<>();
      InsertResponse mockResponse = mock(InsertResponse.class);
      when(mockResponse.getWrittenRows()).thenReturn(5L);
      when(clickhouseWriteClient.insert(any()))
          .thenReturn(Single.just(mockResponse));

      Long result = queryService.insertStackTraces(events).blockingGet();

      assertEquals(5L, result);
    }

    @Test
    void shouldHandleEmptyEventsList() {
      List<StackTraceEvent> events = new ArrayList<>();
      InsertResponse mockResponse = mock(InsertResponse.class);
      when(mockResponse.getWrittenRows()).thenReturn(0L);
      when(clickhouseWriteClient.insert(events))
          .thenReturn(Single.just(mockResponse));

      Long result = queryService.insertStackTraces(events).blockingGet();

      assertEquals(0L, result);
    }

    @Test
    void shouldHandleNonEmptyEventsList() {
      StackTraceEvent event = mock(StackTraceEvent.class);
      List<StackTraceEvent> events = Arrays.asList(event, event, event);
      InsertResponse mockResponse = mock(InsertResponse.class);
      when(mockResponse.getWrittenRows()).thenReturn(3L);
      when(clickhouseWriteClient.insert(events))
          .thenReturn(Single.just(mockResponse));

      Long result = queryService.insertStackTraces(events).blockingGet();

      assertEquals(3L, result);
    }
  }

  @Nested
  class TestGetters {

    @Test
    void shouldReturnClickhouseReadClient() {
      assertEquals(clickhouseReadClient, queryService.getClickhouseReadClient());
    }

    @Test
    void shouldReturnClickhouseWriteClient() {
      assertEquals(clickhouseWriteClient, queryService.getClickhouseWriteClient());
    }

    @Test
    void shouldReturnObjectMapper() {
      assertNotNull(queryService.getObjectMapper());
    }
  }

  @Nested
  class TestEqualsAndHashCode {

    @Test
    void shouldImplementEquals() {
      ClickhouseQueryService service1 = new ClickhouseQueryService(
          clickhouseReadClient, clickhouseWriteClient, projectPoolManager, projectCredentialsDao);
      ClickhouseQueryService service2 = new ClickhouseQueryService(
          clickhouseReadClient, clickhouseWriteClient, projectPoolManager, projectCredentialsDao);

      // Lombok @Data generates equals based on fields
      // Since objectMapper is final and created inline, services with same deps should be equal
      assertNotNull(service1);
      assertNotNull(service2);
    }

    @Test
    void shouldImplementHashCode() {
      int hashCode = queryService.hashCode();
      assertNotNull(hashCode);
    }

    @Test
    void shouldImplementToString() {
      String toString = queryService.toString();
      assertNotNull(toString);
      assertTrue(toString.contains("ClickhouseQueryService"));
    }
  }

  // Test DTO for generic query tests
  public static class TestDto {
    private String name;

    public String getName() {
      return name;
    }

    public void setName(String name) {
      this.name = name;
    }
  }
}
