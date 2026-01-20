/**
 * AttributeList Component
 * 
 * Renders a list of key-value attribute pairs in a consistent format.
 * Used for displaying span attributes, log attributes, resource attributes, etc.
 */

import { Box, Text } from "@mantine/core";
import classes from "../DetailsSidebar.module.css";

interface AttributeListProps {
  /** The attributes to display as key-value pairs */
  attributes: Record<string, string>;
  /** Message to show when there are no attributes */
  emptyMessage?: string;
}

export function AttributeList({
  attributes,
  emptyMessage = "No attributes found",
}: AttributeListProps) {
  const entries = Object.entries(attributes);

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
}
