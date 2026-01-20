/**
 * ExceptionDetails Component
 * 
 * Displays detailed information for exception items including:
 * - Exception type and message
 * - Screen context and active interactions
 * - Device and app information
 * - Stack trace
 * - Grouping information (Group ID, Fingerprint)
 */

import { Box, Text } from "@mantine/core";
import { ExceptionDetailsResponse } from "../../../../../hooks/useGetSpanDetails/useGetSpanDetails.interface";
import { FlameChartNode } from "../../../utils/flameChartTransform";
import classes from "../DetailsSidebar.module.css";

interface ExceptionDetailsProps {
  item: FlameChartNode;
  exceptionDetails: ExceptionDetailsResponse | null;
}

export function ExceptionDetails({ item, exceptionDetails }: ExceptionDetailsProps) {
  return (
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
      {exceptionDetails &&
        (exceptionDetails.platform ||
          exceptionDetails.osVersion ||
          exceptionDetails.deviceModel ||
          exceptionDetails.appVersion ||
          exceptionDetails.sdkVersion ||
          exceptionDetails.userId) && (
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
          <Box className={classes.stackTrace}>{exceptionDetails.exceptionStackTrace}</Box>
        </Box>
      )}

      {/* Grouping Info */}
      {((exceptionDetails?.groupId || item.metadata?.groupId) ||
        exceptionDetails?.fingerprint) && (
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
                <Text className={classes.monoValue}>{exceptionDetails.fingerprint}</Text>
              </Box>
            )}
          </Box>
        </Box>
      )}
    </Box>
  );
}
