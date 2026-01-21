import {
  Card,
  Text,
  Table,
  Badge,
  Stack,
  Group,
  Title,
  SimpleGrid,
  Progress,
  Select,
  Button,
  Paper,
  Tooltip,
  ActionIcon,
} from "@mantine/core";
import { IconAlertTriangle, IconTrendingDown, IconUsers } from "@tabler/icons-react";
import { useGetChurnPredictions } from "../../../../hooks/useGetChurnPredictions";
import { useState } from "react";
import classes from "./ChurnPredictionDashboard.module.css";

export function ChurnPredictionDashboard() {
  const [riskLevelFilter, setRiskLevelFilter] = useState<string | null>(null);
  const [minRiskScore, setMinRiskScore] = useState<number>(0);

  const { data, isLoading, error } = useGetChurnPredictions({
    riskLevel: riskLevelFilter as "HIGH" | "MEDIUM" | "LOW" | undefined,
    minRiskScore,
    limit: 100,
  });

  const churnData = data?.data;

  if (isLoading) {
    return (
      <div className={classes.container}>
        <Text>Loading churn predictions...</Text>
      </div>
    );
  }

  if (error || !churnData) {
    return (
      <div className={classes.container}>
        <Text c="red">Error loading churn predictions</Text>
      </div>
    );
  }

  const getRiskBadgeColor = (riskLevel: string) => {
    switch (riskLevel) {
      case "HIGH":
        return "red";
      case "MEDIUM":
        return "yellow";
      case "LOW":
        return "green";
      default:
        return "gray";
    }
  };

  const rows = churnData.users.map((user) => (
    <Table.Tr key={user.userId}>
      <Table.Td>
        <Text fw={500} size="sm">
          {user.userId.substring(0, 8)}...
        </Text>
      </Table.Td>
      <Table.Td>
        <Badge color={getRiskBadgeColor(user.riskLevel)} variant="light">
          {user.riskLevel}
        </Badge>
      </Table.Td>
      <Table.Td>
        <Group gap={4}>
          <Text size="sm" fw={600}>
            {user.riskScore}
          </Text>
          <Progress
            value={user.riskScore}
            color={getRiskBadgeColor(user.riskLevel)}
            size="sm"
            style={{ width: 60 }}
          />
        </Group>
      </Table.Td>
      <Table.Td>
        <Text size="sm">{user.daysSinceLastSession} days</Text>
      </Table.Td>
      <Table.Td>
        <Text size="sm">{user.sessionsLast7Days}</Text>
      </Table.Td>
      <Table.Td>
        <Text size="sm">{user.crashCountLast7Days}</Text>
      </Table.Td>
      <Table.Td>
        <Tooltip 
          label={
            <div>
              <Text size="xs" fw={600} mb={4}>Risk Factors:</Text>
              {user.primaryRiskFactors.map((factor, idx) => (
                <Text key={idx} size="xs">• {factor}</Text>
              ))}
            </div>
          }
          multiline
          width={300}
        >
          <Text size="sm" c="dimmed" style={{ maxWidth: 200 }} truncate>
            {user.primaryRiskFactors.length > 0 
              ? `${user.primaryRiskFactors[0]}${user.primaryRiskFactors.length > 1 ? ` (+${user.primaryRiskFactors.length - 1} more)` : ''}`
              : "N/A"}
          </Text>
        </Tooltip>
      </Table.Td>
      <Table.Td>
        <Text size="sm">{user.deviceModel || "N/A"}</Text>
      </Table.Td>
    </Table.Tr>
  ));

  return (
    <div className={classes.container}>
      <Stack gap="lg">
        {/* Header */}
        <div>
          <Title order={2}>Churn Prediction Dashboard</Title>
          <Text c="dimmed" size="sm" mt={4}>
            Identify users at risk of churning and take proactive action
          </Text>
        </div>

        {/* Overview Cards */}
        <SimpleGrid cols={{ base: 1, sm: 4 }} spacing="md">
          <Card withBorder padding="md" radius="md">
            <Group justify="space-between">
              <div>
                <Text size="xs" tt="uppercase" fw={700} c="dimmed">
                  Overall Health
                </Text>
                <Text fw={700} size="xl" c={churnData.highRiskCount > churnData.totalUsers * 0.1 ? "red" : churnData.highRiskCount > churnData.totalUsers * 0.05 ? "yellow" : "green"}>
                  {churnData.totalUsers > 0
                    ? Math.round(((churnData.totalUsers - churnData.highRiskCount - churnData.mediumRiskCount) / churnData.totalUsers) * 100)
                    : 0}%
                </Text>
                <Text size="xs" c="dimmed" mt={4}>
                  Healthy users
                </Text>
              </div>
              <IconUsers size={40} color="var(--mantine-color-blue-6)" />
            </Group>
          </Card>
          
          <Card withBorder padding="md" radius="md">
            <Group justify="space-between">
              <div>
                <Text size="xs" tt="uppercase" fw={700} c="dimmed">
                  High Risk Users
                </Text>
                <Text fw={700} size="xl" c="red">
                  {churnData.highRiskCount}
                </Text>
                <Text size="xs" c="dimmed" mt={4}>
                  {churnData.totalUsers > 0
                    ? Math.round((churnData.highRiskCount / churnData.totalUsers) * 100)
                    : 0}
                  % of total
                </Text>
              </div>
              <IconAlertTriangle size={40} color="var(--mantine-color-red-6)" />
            </Group>
          </Card>

          <Card withBorder padding="md" radius="md">
            <Group justify="space-between">
              <div>
                <Text size="xs" tt="uppercase" fw={700} c="dimmed">
                  Medium Risk Users
                </Text>
                <Text fw={700} size="xl" c="yellow">
                  {churnData.mediumRiskCount}
                </Text>
                <Text size="xs" c="dimmed" mt={4}>
                  {churnData.totalUsers > 0
                    ? Math.round((churnData.mediumRiskCount / churnData.totalUsers) * 100)
                    : 0}
                  % of total
                </Text>
              </div>
              <IconTrendingDown size={40} color="var(--mantine-color-yellow-6)" />
            </Group>
          </Card>

          <Card withBorder padding="md" radius="md">
            <Group justify="space-between">
              <div>
                <Text size="xs" tt="uppercase" fw={700} c="dimmed">
                  Total Users Analyzed
                </Text>
                <Text fw={700} size="xl">
                  {churnData.totalUsers}
                </Text>
                <Text size="xs" c="dimmed" mt={4}>
                  Last updated: {new Date(churnData.predictionDate).toLocaleString()}
                </Text>
              </div>
              <IconUsers size={40} color="var(--mantine-color-blue-6)" />
            </Group>
          </Card>
          
          <Card withBorder padding="md" radius="md">
            <Group justify="space-between">
              <div>
                <Text size="xs" tt="uppercase" fw={700} c="dimmed">
                  Avg Churn Risk
                </Text>
                <Text fw={700} size="xl" c={
                  churnData.users.length > 0
                    ? (() => {
                        const avgRisk = churnData.users.reduce((sum, u) => sum + u.riskScore, 0) / churnData.users.length;
                        return avgRisk >= 70 ? "red" : avgRisk >= 40 ? "yellow" : "green";
                      })()
                    : "gray"
                }>
                  {churnData.users.length > 0
                    ? Math.round(churnData.users.reduce((sum, u) => sum + u.riskScore, 0) / churnData.users.length)
                    : 0}
                </Text>
                <Text size="xs" c="dimmed" mt={4}>
                  Average risk score
                </Text>
              </div>
              <IconTrendingDown size={40} color="var(--mantine-color-orange-6)" />
            </Group>
          </Card>
        </SimpleGrid>

        {/* Filters */}
        <Group>
          <Select
            label="Risk Level"
            placeholder="All risk levels"
            data={[
              { value: "", label: "All" },
              { value: "HIGH", label: "High Risk" },
              { value: "MEDIUM", label: "Medium Risk" },
              { value: "LOW", label: "Low Risk" },
            ]}
            value={riskLevelFilter || ""}
            onChange={(value) => setRiskLevelFilter(value || null)}
            clearable
            style={{ flex: 1 }}
          />
          <Select
            label="Min Risk Score"
            placeholder="Minimum risk score"
            data={[
              { value: "0", label: "All" },
              { value: "40", label: "40+" },
              { value: "70", label: "70+" },
            ]}
            value={minRiskScore.toString()}
            onChange={(value) => setMinRiskScore(value ? parseInt(value) : 0)}
            style={{ flex: 1 }}
          />
        </Group>

        {/* At-Risk Users Table */}
        <Card withBorder padding="md" radius="md">
          <Title order={4} mb="md">
            At-Risk Users
          </Title>
          <Table.ScrollContainer minWidth={800}>
            <Table striped highlightOnHover>
              <Table.Thead>
                <Table.Tr>
              <Table.Th>User ID</Table.Th>
              <Table.Th>Health Status</Table.Th>
              <Table.Th>Risk Score</Table.Th>
              <Table.Th>Churn Probability</Table.Th>
                  <Table.Th>Days Since Last Session</Table.Th>
                  <Table.Th>Sessions (7d)</Table.Th>
                  <Table.Th>Crashes (7d)</Table.Th>
                  <Table.Th>Risk Factors</Table.Th>
                  <Table.Th>Device</Table.Th>
                </Table.Tr>
              </Table.Thead>
              <Table.Tbody>
                {rows.length > 0 ? (
                  rows
                ) : (
                  <Table.Tr>
                    <Table.Td colSpan={8}>
                      <Text c="dimmed" ta="center" py="xl">
                        No users found matching the filters
                      </Text>
                    </Table.Td>
                  </Table.Tr>
                )}
              </Table.Tbody>
            </Table>
          </Table.ScrollContainer>
        </Card>
      </Stack>
    </div>
  );
}

