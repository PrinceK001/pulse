import { useState } from "react";
import { Box, Text, Button, Loader, ScrollArea, Badge } from "@mantine/core";
import {
  IconX,
  IconTag,
  IconList,
  IconLink,
  IconFileText,
  IconClock,
  IconHash,
} from "@tabler/icons-react";
import dayjs from "dayjs";
import utc from "dayjs/plugin/utc";
import localizedFormat from "dayjs/plugin/localizedFormat";
import { FlameChartNode, getColorForPulseType } from "../../utils/flameChartTransform";
import { useGetSpanDetails } from "../../../../hooks/useGetSpanDetails";
import classes from "./DetailsSidebar.module.css";

/**
 * Format pulseType for display (capitalize, replace underscores/hyphens with spaces)
 */
function formatPulseType(pulseType: string | undefined): string {
  if (!pulseType) return "Unknown";
  return pulseType
    .split(/[_-]/)
    .map(word => word.charAt(0).toUpperCase() + word.slice(1).toLowerCase())
    .join(" ");
}

dayjs.extend(utc);
dayjs.extend(localizedFormat);

interface DetailsSidebarProps {
  item: FlameChartNode | null;
  onClose: () => void;
}

type TabType = "attributes" | "events" | "links";

/**
 * Format duration for display
 */
function formatDuration(ms: number): string {
  if (ms < 1) {
    return `${(ms * 1000).toFixed(0)}µs`;
  }
  if (ms < 1000) {
    return `${ms.toFixed(2)}ms`;
  }
  return `${(ms / 1000).toFixed(2)}s`;
}

/**
 * Format timestamp to human readable with milliseconds in local timezone
 */
function formatTimestamp(timestamp: string | undefined): string {
  if (!timestamp) return "—";
  // Parse as UTC then convert to local time
  const dt = dayjs.utc(timestamp).local();
  if (!dt.isValid()) return "—";
  return dt.format("MMM D, YYYY HH:mm:ss.SSS");
}

/**
 * Get status badge class
 */
function getStatusClass(statusCode: string): string {
  const status = statusCode?.toLowerCase();
  if (status === "error") return classes.statusError;
  if (status === "ok") return classes.statusOk;
  return classes.statusUnset;
}

export function DetailsSidebar({ item, onClose }: DetailsSidebarProps) {
  const [activeTab, setActiveTab] = useState<TabType>("attributes");

  const isLog = item?.type === "log" || item?.type === "orphan-log";
  const isException = item?.type === "exception";
  const isOrphan = item?.type?.includes("orphan") || false;

  // Determine dataType for fetching details
  const getDataType = (): "TRACES" | "LOGS" | "EXCEPTIONS" => {
    if (isException) return "EXCEPTIONS";
    if (isLog) return "LOGS";
    return "TRACES";
  };

  // Fetch detailed attributes on demand
  const { data: details, isLoading } = useGetSpanDetails({
    dataType: getDataType(),
    traceId: item?.traceId || "",
    spanId: item?.spanId || "",
    timestamp: item?.metadata?.timestamp || "",
    groupId: item?.metadata?.groupId || "",
    enabled: !!item,
  });

  if (!item) return null;

  const handleBackdropClick = (e: React.MouseEvent) => {
    if (e.target === e.currentTarget) {
      onClose();
    }
  };

  // Get attributes based on type - merge API response with pre-fetched metadata attributes
  
  // For resource attributes: prefer API response, fallback to pre-fetched from metadata
  const apiResourceAttributes = details && "resourceAttributes" in details ? details.resourceAttributes : {};
  const metadataResourceAttributes = (item.metadata?.resourceAttributes as Record<string, string>) || {};
  const resourceAttributes = Object.keys(apiResourceAttributes).length > 0 
    ? apiResourceAttributes 
    : metadataResourceAttributes;
  
  // For main attributes (log/span/exception attributes): prefer API response, fallback to pre-fetched
  const apiMainAttributes = isException
    ? (details && "logAttributes" in details ? details.logAttributes : {})
    : isLog
      ? (details && "logAttributes" in details ? details.logAttributes : {})
      : (details && "spanAttributes" in details ? details.spanAttributes : {});
  const metadataMainAttributes = isLog 
    ? ((item.metadata?.logAttributes as Record<string, string>) || {})
    : {};
  
  // Build simple metadata attributes from item.metadata (excluding internal/complex fields)
  const simpleMetadataAttributes: Record<string, string> = {};
  if (item.metadata) {
    const excludeKeys = ["timestamp", "pulseType", "logAttributes", "resourceAttributes"]; // These are shown elsewhere or handled separately
    for (const [key, value] of Object.entries(item.metadata)) {
      if (!excludeKeys.includes(key) && value !== undefined && value !== null && value !== "" && typeof value !== "object") {
        simpleMetadataAttributes[key] = String(value);
      }
    }
  }
  
  // Merge: API attributes take precedence, then pre-fetched attributes, then simple metadata
  const mainAttributes = { 
    ...simpleMetadataAttributes, 
    ...metadataMainAttributes, 
    ...apiMainAttributes 
  };
  
  // Exception-specific details from API
  const exceptionDetails = isException && details && "exceptionStackTrace" in details ? details : null;
  
  const events = !isLog && !isException && details && "events" in details ? details.events : [];
  const links = !isLog && !isException && details && "links" in details ? details.links : [];

  const renderAttributeList = (attrs: Record<string, string>, emptyMessage: string) => {
    const entries = Object.entries(attrs);
    if (entries.length === 0) {
      return <Box className={classes.emptyAttributes}>{emptyMessage}</Box>;
    }

    return (
      <Box className={classes.attributeList}>
        {entries.map(([key, value]) => (
          <Box key={key} className={classes.attributeItem}>
            <Text className={classes.attributeKey}>{key}</Text>
            <Text className={classes.attributeValue}>{String(value)}</Text>
          </Box>
        ))}
      </Box>
    );
  };

  const renderEvents = () => {
    if (!events || events.length === 0) {
      return <Box className={classes.emptyAttributes}>No events attached to this span</Box>;
    }

    return (
      <Box>
        {events.map((event, idx) => (
          <Box key={idx} className={classes.eventItem}>
            <Box className={classes.eventHeader}>
              <Text className={classes.eventName}>{event.name}</Text>
              <Text className={classes.eventTimestamp}>{event.timestamp}</Text>
            </Box>
            {Object.keys(event.attributes).length > 0 && (
              <Box className={classes.attributeList}>
                {Object.entries(event.attributes).map(([key, value]) => (
                  <Box key={key} className={classes.attributeItem}>
                    <Text className={classes.attributeKey}>{key}</Text>
                    <Text className={classes.attributeValue}>{String(value)}</Text>
                  </Box>
                ))}
              </Box>
            )}
          </Box>
        ))}
      </Box>
    );
  };

  const renderLinks = () => {
    if (!links || links.length === 0) {
      return <Box className={classes.emptyAttributes}>No links attached to this span</Box>;
    }

    return (
      <Box>
        {links.map((link, idx) => (
          <Box key={idx} className={classes.linkItem}>
            <Text className={classes.linkTraceId}>TraceId: {link.traceId}</Text>
            <Text className={classes.linkSpanId}>SpanId: {link.spanId}</Text>
            {Object.keys(link.attributes).length > 0 && (
              <Box className={classes.attributeList} style={{ marginTop: 8 }}>
                {Object.entries(link.attributes).map(([key, value]) => (
                  <Box key={key} className={classes.attributeItem}>
                    <Text className={classes.attributeKey}>{key}</Text>
                    <Text className={classes.attributeValue}>{String(value)}</Text>
                  </Box>
                ))}
              </Box>
            )}
          </Box>
        ))}
      </Box>
    );
  };

  return (
    <>
      <Box className={classes.backdrop} onClick={handleBackdropClick} />
      <Box className={classes.sidebar}>
        {/* Header */}
        <Box className={classes.header}>
          <Box className={classes.headerTitle}>
            <Box className={classes.titleIndicator} />
            <Text className={classes.title}>
              {isException ? "Exception Details" : isLog ? "Log Details" : "Span Details"}
            </Text>
          </Box>
          <Button
            variant="subtle"
            size="xs"
            className={classes.closeButton}
            onClick={onClose}
          >
            <IconX size={18} />
          </Button>
        </Box>

        {/* Content */}
        <ScrollArea className={classes.content}>
          {/* Basic Info */}
          <Box className={classes.section}>
            <Box className={classes.sectionHeader}>
              <IconFileText size={16} className={classes.sectionIcon} />
              <Text className={classes.sectionTitle}>Overview</Text>
            </Box>

            <Box className={classes.metaGrid}>
              <Box className={classes.metaItem} style={{ gridColumn: "1 / -1" }}>
                <Text className={classes.metaLabel}>Name</Text>
                <Text className={classes.metaValue}>{item.name}</Text>
              </Box>

              <Box className={classes.metaItem}>
                <Text className={classes.metaLabel}>Type</Text>
                <Badge 
                  className={classes.typeBadge}
                  style={{ 
                    backgroundColor: getColorForPulseType(item.metadata?.pulseType || ""),
                    color: "#ffffff",
                  }}
                >
                  {formatPulseType(item.metadata?.pulseType)}
                </Badge>
              </Box>

              {!isLog && item.metadata?.statusCode && (
                <Box className={classes.metaItem}>
                  <Text className={classes.metaLabel}>Status</Text>
                  <Badge className={`${classes.statusBadge} ${getStatusClass(item.metadata.statusCode)}`}>
                    {item.metadata.statusCode}
                  </Badge>
                </Box>
              )}

              {/* Duration - only for spans */}
              {!isLog && (
                <Box className={classes.metaItem}>
                  <Text className={classes.metaLabel}>
                    <IconClock size={12} style={{ marginRight: 4 }} />
                    Duration
                  </Text>
                  <Text className={classes.metaValue}>{formatDuration(item.duration)}</Text>
                </Box>
              )}

              <Box className={classes.metaItem}>
                <Text className={classes.metaLabel}>Start Offset</Text>
                <Text className={classes.metaValue}>{formatDuration(item.start)}</Text>
              </Box>

              {/* Timestamp for logs */}
              {isLog && item.metadata?.timestamp && (
                <Box className={classes.metaItem} style={{ gridColumn: "1 / -1" }}>
                  <Text className={classes.metaLabel}>
                    <IconClock size={12} style={{ marginRight: 4 }} />
                    Timestamp
                  </Text>
                  <Text className={classes.metaValueMono}>
                    {formatTimestamp(item.metadata.timestamp)}
                  </Text>
                </Box>
              )}

              {/* Start Time and End Time for spans */}
              {!isLog && item.metadata?.timestamp && (
                <>
                  <Box className={classes.metaItem} style={{ gridColumn: "1 / -1" }}>
                    <Text className={classes.metaLabel}>
                      <IconClock size={12} style={{ marginRight: 4 }} />
                      Start Time
                    </Text>
                    <Text className={classes.metaValueMono}>
                      {formatTimestamp(item.metadata.timestamp)}
                    </Text>
                  </Box>
                  <Box className={classes.metaItem} style={{ gridColumn: "1 / -1" }}>
                    <Text className={classes.metaLabel}>
                      <IconClock size={12} style={{ marginRight: 4 }} />
                      End Time
                    </Text>
                    <Text className={classes.metaValueMono}>
                      {(() => {
                        // Parse as UTC, add duration, then convert to local time
                        const startTime = dayjs.utc(item.metadata.timestamp);
                        if (!startTime.isValid()) return "—";
                        // Duration is in milliseconds, add to start time and convert to local
                        const endTime = startTime.add(item.duration, "milliseconds").local();
                        return endTime.format("MMM D, YYYY HH:mm:ss.SSS");
                      })()}
                    </Text>
                  </Box>
                </>
              )}

              <Box className={classes.metaItem} style={{ gridColumn: "1 / -1" }}>
                <Text className={classes.metaLabel}>
                  <IconHash size={12} style={{ marginRight: 4 }} />
                  Trace ID
                </Text>
                <Text className={classes.metaValueMono}>
                  {item.traceId}
                </Text>
              </Box>

              <Box className={classes.metaItem} style={{ gridColumn: "1 / -1" }}>
                <Text className={classes.metaLabel}>Span ID</Text>
                <Text className={classes.metaValueMono}>
                  {item.spanId}
                </Text>
              </Box>

              {item.parentSpanId && (
                <Box className={classes.metaItem} style={{ gridColumn: "1 / -1" }}>
                  <Text className={classes.metaLabel}>Parent Span ID</Text>
                  <Text className={classes.metaValueMono}>
                    {item.parentSpanId}
                  </Text>
                </Box>
              )}

              {item.metadata?.serviceName && (
                <Box className={classes.metaItem}>
                  <Text className={classes.metaLabel}>Service</Text>
                  <Text className={classes.metaValue}>{item.metadata.serviceName}</Text>
                </Box>
              )}

              {item.metadata?.spanKind && (
                <Box className={classes.metaItem}>
                  <Text className={classes.metaLabel}>Kind</Text>
                  <Text className={classes.metaValue}>{item.metadata.spanKind}</Text>
                </Box>
              )}

              {isLog && item.metadata?.severityText && (
                <Box className={classes.metaItem}>
                  <Text className={classes.metaLabel}>Severity</Text>
                  <Text className={classes.metaValue}>{item.metadata.severityText}</Text>
                </Box>
              )}
            </Box>

            {/* Log Body */}
            {isLog && item.metadata?.body && (
              <Box style={{ marginTop: 16 }}>
                <Text className={classes.metaLabel} mb={4}>Body</Text>
                <Box className={classes.bodyContent}>
                  {item.metadata.body}
                </Box>
              </Box>
            )}

            {/* Exception Details */}
            {isException && (
              <Box mt="md">
                {/* Exception Type */}
                {(exceptionDetails?.exceptionType || item.metadata?.exceptionType) && (
                  <Box className={classes.exceptionSection}>
                    <Text className={classes.exceptionSectionLabel}>Exception Type</Text>
                    <Text className={classes.exceptionType}>
                      {exceptionDetails?.exceptionType || item.metadata?.exceptionType}
                    </Text>
                  </Box>
                )}
                
                {/* Exception Message */}
                {(exceptionDetails?.exceptionMessage || item.metadata?.exceptionMessage) && (
                  <Box className={classes.exceptionSection}>
                    <Text className={classes.exceptionSectionLabel}>Message</Text>
                    <Text className={classes.exceptionMessage}>
                      {exceptionDetails?.exceptionMessage || item.metadata?.exceptionMessage}
                    </Text>
                  </Box>
                )}
                
                {/* Screen & Interactions Row */}
                {((exceptionDetails?.screenName || item.metadata?.screenName) || 
                  (exceptionDetails?.interactions && exceptionDetails.interactions.length > 0)) && (
                  <Box className={classes.exceptionSection}>
                    <Text className={classes.exceptionSectionLabel}>Context</Text>
                    <Box className={classes.infoCard}>
                      {(exceptionDetails?.screenName || item.metadata?.screenName) && (
                        <Box className={classes.infoItem} mb="xs">
                          <Text className={classes.infoLabel}>Screen</Text>
                          <Text className={classes.screenBadge}>
                            {exceptionDetails?.screenName || item.metadata?.screenName}
                          </Text>
                        </Box>
                      )}
                      {exceptionDetails?.interactions && exceptionDetails.interactions.length > 0 && (
                        <Box className={classes.infoItem}>
                          <Text className={classes.infoLabel}>Active Interactions</Text>
                          <Box className={classes.interactionsList}>
                            {exceptionDetails.interactions.map((interaction, idx) => (
                              <Text key={idx} className={classes.interactionBadge}>
                                {interaction}
                              </Text>
                            ))}
                          </Box>
                        </Box>
                      )}
                    </Box>
                  </Box>
                )}
                
                {/* Device/App Info - Grid Layout */}
                {exceptionDetails && (exceptionDetails.platform || exceptionDetails.osVersion || 
                  exceptionDetails.deviceModel || exceptionDetails.appVersion || 
                  exceptionDetails.sdkVersion || exceptionDetails.userId) && (
                  <Box className={classes.exceptionSection}>
                    <Text className={classes.exceptionSectionLabel}>Device & App</Text>
                    <Box className={classes.infoCard}>
                      <Box className={classes.infoGrid}>
                        {exceptionDetails.platform && (
                          <Box className={classes.infoItem}>
                            <Text className={classes.infoLabel}>Platform</Text>
                            <Text className={classes.infoValue}>{exceptionDetails.platform}</Text>
                          </Box>
                        )}
                        {exceptionDetails.osVersion && (
                          <Box className={classes.infoItem}>
                            <Text className={classes.infoLabel}>OS Version</Text>
                            <Text className={classes.infoValue}>{exceptionDetails.osVersion}</Text>
                          </Box>
                        )}
                        {exceptionDetails.deviceModel && (
                          <Box className={classes.infoItem}>
                            <Text className={classes.infoLabel}>Device</Text>
                            <Text className={classes.infoValue}>{exceptionDetails.deviceModel}</Text>
                          </Box>
                        )}
                        {exceptionDetails.appVersion && (
                          <Box className={classes.infoItem}>
                            <Text className={classes.infoLabel}>App Version</Text>
                            <Text className={classes.infoValue}>{exceptionDetails.appVersion}</Text>
                          </Box>
                        )}
                        {exceptionDetails.sdkVersion && (
                          <Box className={classes.infoItem}>
                            <Text className={classes.infoLabel}>SDK Version</Text>
                            <Text className={classes.infoValue}>{exceptionDetails.sdkVersion}</Text>
                          </Box>
                        )}
                        {exceptionDetails.userId && (
                          <Box className={classes.infoItem}>
                            <Text className={classes.infoLabel}>User ID</Text>
                            <Text className={classes.infoValue}>{exceptionDetails.userId}</Text>
                          </Box>
                        )}
                      </Box>
                    </Box>
                  </Box>
                )}
                
                {/* Stack Trace */}
                {exceptionDetails?.exceptionStackTrace && (
                  <Box className={classes.exceptionSection}>
                    <Text className={classes.exceptionSectionLabel}>Stack Trace</Text>
                    <Box className={classes.stackTrace}>
                      {exceptionDetails.exceptionStackTrace}
                    </Box>
                  </Box>
                )}
                
                {/* Grouping Info */}
                {((exceptionDetails?.groupId || item.metadata?.groupId) || exceptionDetails?.fingerprint) && (
                  <Box className={classes.exceptionSection}>
                    <Text className={classes.exceptionSectionLabel}>Grouping</Text>
                    <Box className={classes.infoCard}>
                      {(exceptionDetails?.groupId || item.metadata?.groupId) && (
                        <Box className={classes.infoItem} mb="xs">
                          <Text className={classes.infoLabel}>Group ID</Text>
                          <Text className={classes.monoValue}>
                            {exceptionDetails?.groupId || item.metadata?.groupId}
                          </Text>
                        </Box>
                      )}
                      {exceptionDetails?.fingerprint && (
                        <Box className={classes.infoItem}>
                          <Text className={classes.infoLabel}>Fingerprint</Text>
                          <Text className={classes.monoValue}>
                            {exceptionDetails.fingerprint}
                          </Text>
                        </Box>
                      )}
                    </Box>
                  </Box>
                )}
              </Box>
            )}

            {isOrphan && (
              <Badge color="gray" size="sm" mt="md">
                ⚠️ Orphan: Parent span not found in this session
              </Badge>
            )}
          </Box>

          {/* Tabs for Attributes/Events/Links */}
          <Box className={classes.tabs}>
            <button
              className={`${classes.tab} ${activeTab === "attributes" ? classes.tabActive : ""}`}
              onClick={() => setActiveTab("attributes")}
            >
              <IconTag size={14} style={{ marginRight: 4 }} />
              Attributes
            </button>
            {!isLog && !isException && (
              <>
                <button
                  className={`${classes.tab} ${activeTab === "events" ? classes.tabActive : ""}`}
                  onClick={() => setActiveTab("events")}
                >
                  <IconList size={14} style={{ marginRight: 4 }} />
                  Events ({events?.length || 0})
                </button>
                <button
                  className={`${classes.tab} ${activeTab === "links" ? classes.tabActive : ""}`}
                  onClick={() => setActiveTab("links")}
                >
                  <IconLink size={14} style={{ marginRight: 4 }} />
                  Links ({links?.length || 0})
                </button>
              </>
            )}
          </Box>

          {/* Tab Content */}
          {isLoading ? (
            <Box className={classes.loadingContainer}>
              <Loader color="teal" size="md" />
              <Text size="sm" c="dimmed">Loading details...</Text>
            </Box>
          ) : (
            <>
              {activeTab === "attributes" && (
                <>
                  <Box className={classes.section}>
                    <Box className={classes.sectionHeader}>
                      <Text className={classes.sectionTitle}>
                        {isException ? "Log Attributes" : isLog ? "Log Attributes" : "Span Attributes"}
                      </Text>
                      <Badge className={classes.sectionBadge}>
                        {Object.keys(mainAttributes).length}
                      </Badge>
                    </Box>
                    {renderAttributeList(mainAttributes, "No attributes found")}
                  </Box>

                  <Box className={classes.section}>
                    <Box className={classes.sectionHeader}>
                      <Text className={classes.sectionTitle}>Resource Attributes</Text>
                      <Badge className={classes.sectionBadge}>
                        {Object.keys(resourceAttributes).length}
                      </Badge>
                    </Box>
                    {renderAttributeList(resourceAttributes, "No resource attributes found")}
                  </Box>
                </>
              )}

              {activeTab === "events" && !isLog && (
                <Box className={classes.section}>
                  <Box className={classes.sectionHeader}>
                    <Text className={classes.sectionTitle}>Span Events</Text>
                    <Badge className={classes.sectionBadge}>
                      {events?.length || 0}
                    </Badge>
                  </Box>
                  {renderEvents()}
                </Box>
              )}

              {activeTab === "links" && !isLog && (
                <Box className={classes.section}>
                  <Box className={classes.sectionHeader}>
                    <Text className={classes.sectionTitle}>Span Links</Text>
                    <Badge className={classes.sectionBadge}>
                      {links?.length || 0}
                    </Badge>
                  </Box>
                  {renderLinks()}
                </Box>
              )}
            </>
          )}
        </ScrollArea>
      </Box>
    </>
  );
}

