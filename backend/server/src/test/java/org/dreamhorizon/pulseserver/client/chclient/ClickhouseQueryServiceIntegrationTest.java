package org.dreamhorizon.pulseserver.client.chclient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.clickhouse.client.api.insert.InsertResponse;
import io.reactivex.rxjava3.core.Single;
import java.util.Arrays;
import java.util.List;
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
class ClickhouseQueryServiceIntegrationTest {

  @Mock
  private ClickhouseReadClient clickhouseReadClient;

  @Mock
  private ClickhouseWriteClient clickhouseWriteClient;

  @Mock
  private ClickhouseTenantConnectionPoolManager poolManager;

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

  @Nested
  class TestInsertOperations {

    @Test
    void shouldInsertStackTracesSuccessfully() {
      StackTraceEvent event1 = mock(StackTraceEvent.class);
      StackTraceEvent event2 = mock(StackTraceEvent.class);
      List<StackTraceEvent> events = Arrays.asList(event1, event2);

      InsertResponse mockResponse = mock(InsertResponse.class);
      when(mockResponse.getWrittenRows()).thenReturn(2L);
      when(clickhouseWriteClient.insert(events)).thenReturn(Single.just(mockResponse));

      Long result = queryService.insertStackTraces(events).blockingGet();

      assertEquals(2L, result);
    }
  }


}
