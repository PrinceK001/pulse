import {
  Card,
  Text,
  Stack,
  Group,
  Title,
  SimpleGrid,
  Progress,
  Badge,
  Paper,
  RingProgress,
  Tabs,
  Table,
  Tooltip,
} from "@mantine/core";
import {
  IconAlertTriangle,
  IconTrendingDown,
  IconUsers,
  IconChartBar,
  IconDeviceMobile,
  IconBrandAndroid,
  IconCode,
  IconActivity,
  IconBulb,
  IconWrench,
  IconTrendingUp,
  IconShapes,
} from "@tabler/icons-react";
import { useGetChurnAnalytics, ChurnAnalyticsResponse } from "../../../../hooks/useGetChurnAnalytics";
import { useState } from "react";
import classes from "./ChurnAnalyticsDashboard.module.css";

export function ChurnAnalyticsDashboard() {
  const [activeTab, setActiveTab] = useState<string>("overview");

  const { data, isLoading, error } = useGetChurnAnalytics({
    limit: 2000, // Reduced for ML analysis (statistically valid sample)
  });

  const analytics = data?.data;

  if (isLoading) {
    return (
      <div className={classes.container}>
        <Text>Loading churn analytics...</Text>
      </div>
    );
  }

  if (error || !analytics) {
    return (
      <div className={classes.container}>
        <Text c="red">Error loading churn analytics</Text>
      </div>
    );
  }

  const getRiskColor = (score: number) => {
    if (score >= 70) return "red";
    if (score >= 40) return "yellow";
    return "green";
  };

  const formatNumber = (num: number) => {
    if (num >= 1000000) return `${(num / 1000000).toFixed(1)}M`;
    if (num >= 1000) return `${(num / 1000).toFixed(1)}K`;
    return num.toString();
  };

  const formatPercentage = (num: number) => `${num.toFixed(1)}%`;

  return (
    <div className={classes.container}>
      <Stack gap="lg">
        {/* Header */}
        <div>
          <Title order={2}>Churn Analytics Dashboard</Title>
          <Text c="dimmed" size="sm" mt={4}>
            Comprehensive insights for {formatNumber(analytics.totalUsers)} users - 
            Identify patterns, segments, and actionable insights
          </Text>
        </div>

        {/* Key Metrics Cards */}
        <SimpleGrid cols={{ base: 1, sm: 4 }} spacing="md">
          <Card withBorder padding="md" radius="md">
            <Group justify="space-between">
              <div>
                <Text size="xs" tt="uppercase" fw={700} c="dimmed">
                  Overall Health
                </Text>
                <Text fw={700} size="xl" c={getRiskColor(analytics.averageRiskScore)}>
                  {formatPercentage(
                    ((analytics.lowRiskCount / analytics.totalUsers) * 100) || 0
                  )}
                </Text>
                <Text size="xs" c="dimmed" mt={4}>
                  {formatNumber(analytics.lowRiskCount)} healthy users
                </Text>
              </div>
              <IconUsers size={40} color="var(--mantine-color-blue-6)" />
            </Group>
          </Card>

          <Card withBorder padding="md" radius="md">
            <Group justify="space-between">
              <div>
                <Text size="xs" tt="uppercase" fw={700} c="dimmed">
                  At Risk Users
                </Text>
                <Text fw={700} size="xl" c="red">
                  {formatNumber(analytics.highRiskCount + analytics.mediumRiskCount)}
                </Text>
                <Text size="xs" c="dimmed" mt={4}>
                  {formatPercentage(
                    ((analytics.highRiskCount + analytics.mediumRiskCount) /
                      analytics.totalUsers) *
                      100
                  )}{" "}
                  of total
                </Text>
              </div>
              <IconAlertTriangle size={40} color="var(--mantine-color-red-6)" />
            </Group>
          </Card>

          <Card withBorder padding="md" radius="md">
            <Group justify="space-between">
              <div>
                <Text size="xs" tt="uppercase" fw={700} c="dimmed">
                  Avg Churn Risk
                </Text>
                <Text fw={700} size="xl" c={getRiskColor(analytics.averageRiskScore)}>
                  {analytics.averageRiskScore.toFixed(1)}
                </Text>
                <Text size="xs" c="dimmed" mt={4}>
                  {formatPercentage(analytics.overallChurnProbability * 100)} probability
                </Text>
              </div>
              <IconTrendingDown size={40} color="var(--mantine-color-orange-6)" />
            </Group>
          </Card>

          <Card withBorder padding="md" radius="md">
            <Group justify="space-between">
              <div>
                <Text size="xs" tt="uppercase" fw={700} c="dimmed">
                  Total Analyzed
                </Text>
                <Text fw={700} size="xl">
                  {formatNumber(analytics.totalUsers)}
                </Text>
                <Text size="xs" c="dimmed" mt={4}>
                  Users in analysis
                </Text>
              </div>
              <IconChartBar size={40} color="var(--mantine-color-blue-6)" />
            </Group>
          </Card>
        </SimpleGrid>

        {/* Tabs for different views */}
        <Tabs value={activeTab} onChange={(value) => setActiveTab(value || "overview")}>
          <Tabs.List>
            <Tabs.Tab value="overview" leftSection={<IconChartBar size={16} />}>
              Overview
            </Tabs.Tab>
            <Tabs.Tab value="risk-factors" leftSection={<IconAlertTriangle size={16} />}>
              Risk Factors
            </Tabs.Tab>
            <Tabs.Tab value="segments" leftSection={<IconDeviceMobile size={16} />}>
              Segments
            </Tabs.Tab>
            <Tabs.Tab value="performance" leftSection={<IconActivity size={16} />}>
              Performance Impact
            </Tabs.Tab>
            <Tabs.Tab value="engagement" leftSection={<IconUsers size={16} />}>
              Engagement Patterns
            </Tabs.Tab>
            <Tabs.Tab value="root-causes" leftSection={<IconBulb size={16} />}>
              Root Causes (ML)
            </Tabs.Tab>
            <Tabs.Tab value="priority-fixes" leftSection={<IconWrench size={16} />}>
              Priority Fixes
            </Tabs.Tab>
            <Tabs.Tab value="trends" leftSection={<IconTrendingUp size={16} />}>
              Trends & Anomalies
            </Tabs.Tab>
            <Tabs.Tab value="patterns" leftSection={<IconShapes size={16} />}>
              Patterns (ML)
            </Tabs.Tab>
          </Tabs.List>

          {/* Overview Tab */}
          <Tabs.Panel value="overview" pt="md">
            <SimpleGrid cols={{ base: 1, md: 2 }} spacing="md">
              {/* Risk Distribution */}
              <Card withBorder padding="md" radius="md">
                <Title order={4} mb="md">
                  Risk Score Distribution
                </Title>
                <Stack gap="sm">
                  {Object.entries(analytics.riskDistribution || {}).map(([range, count]) => {
                    const percentage = (count / analytics.totalUsers) * 100;
                    const [min, max] = range.split("-").map(Number);
                    const color = getRiskColor((min + max) / 2);
                    return (
                      <div key={range}>
                        <Group justify="space-between" mb={4}>
                          <Text size="sm" fw={500}>
                            {range}
                          </Text>
                          <Group gap="xs">
                            <Text size="sm" c="dimmed">
                              {formatNumber(count)}
                            </Text>
                            <Text size="sm" fw={600} c={color}>
                              {formatPercentage(percentage)}
                            </Text>
                          </Group>
                        </Group>
                        <Progress value={percentage} color={color} size="lg" radius="xl" />
                      </div>
                    );
                  })}
                </Stack>
              </Card>

              {/* Top Risk Factors */}
              <Card withBorder padding="md" radius="md">
                <Title order={4} mb="md">
                  Top Risk Factors
                </Title>
                <Stack gap="sm">
                  {analytics.topRiskFactors?.slice(0, 5).map((factor, idx) => (
                    <Paper key={idx} p="sm" withBorder>
                      <Group justify="space-between" mb={4}>
                        <Text size="sm" fw={500} style={{ flex: 1 }}>
                          {factor.factor}
                        </Text>
                        <Badge color={getRiskColor(factor.severity === "HIGH" ? 75 : factor.severity === "MEDIUM" ? 50 : 25)}>
                          {factor.severity}
                        </Badge>
                      </Group>
                      <Group justify="space-between">
                        <Text size="xs" c="dimmed">
                          {formatNumber(factor.userCount)} users affected
                        </Text>
                        <Text size="xs" fw={600}>
                          {formatPercentage(factor.percentage)}
                        </Text>
                      </Group>
                      <Progress
                        value={factor.percentage}
                        color={getRiskColor(factor.severity === "HIGH" ? 75 : factor.severity === "MEDIUM" ? 50 : 25)}
                        size="sm"
                        mt={4}
                      />
                    </Paper>
                  ))}
                </Stack>
              </Card>
            </SimpleGrid>
          </Tabs.Panel>

          {/* Risk Factors Tab */}
          <Tabs.Panel value="risk-factors" pt="md">
            <Card withBorder padding="md" radius="md">
              <Title order={4} mb="md">
                All Risk Factors (Ranked by Impact)
              </Title>
              <Table>
                <Table.Thead>
                  <Table.Tr>
                    <Table.Th>Risk Factor</Table.Th>
                    <Table.Th>Users Affected</Table.Th>
                    <Table.Th>% of Users</Table.Th>
                    <Table.Th>Severity</Table.Th>
                  </Table.Tr>
                </Table.Thead>
                <Table.Tbody>
                  {analytics.topRiskFactors?.map((factor, idx) => (
                    <Table.Tr key={idx}>
                      <Table.Td>
                        <Text size="sm" fw={500}>
                          {factor.factor}
                        </Text>
                      </Table.Td>
                      <Table.Td>
                        <Text size="sm">{formatNumber(factor.userCount)}</Text>
                      </Table.Td>
                      <Table.Td>
                        <Text size="sm" fw={600}>
                          {formatPercentage(factor.percentage)}
                        </Text>
                      </Table.Td>
                      <Table.Td>
                        <Badge
                          color={getRiskColor(
                            factor.severity === "HIGH" ? 75 : factor.severity === "MEDIUM" ? 50 : 25
                          )}
                        >
                          {factor.severity}
                        </Badge>
                      </Table.Td>
                    </Table.Tr>
                  ))}
                </Table.Tbody>
              </Table>
            </Card>
          </Tabs.Panel>

          {/* Segments Tab */}
          <Tabs.Panel value="segments" pt="md">
            <Tabs defaultValue="device">
              <Tabs.List>
                <Tabs.Tab value="device" leftSection={<IconDeviceMobile size={16} />}>
                  Device
                </Tabs.Tab>
                <Tabs.Tab value="os" leftSection={<IconBrandAndroid size={16} />}>
                  OS Version
                </Tabs.Tab>
                <Tabs.Tab value="app" leftSection={<IconCode size={16} />}>
                  App Version
                </Tabs.Tab>
              </Tabs.List>

              <Tabs.Panel value="device" pt="md">
                <SegmentTable
                  segments={analytics.deviceSegments}
                  title="Device Segments"
                  formatNumber={formatNumber}
                  formatPercentage={formatPercentage}
                  getRiskColor={getRiskColor}
                />
              </Tabs.Panel>

              <Tabs.Panel value="os" pt="md">
                <SegmentTable
                  segments={analytics.osSegments}
                  title="OS Version Segments"
                  formatNumber={formatNumber}
                  formatPercentage={formatPercentage}
                  getRiskColor={getRiskColor}
                />
              </Tabs.Panel>

              <Tabs.Panel value="app" pt="md">
                <SegmentTable
                  segments={analytics.appVersionSegments}
                  title="App Version Segments"
                  formatNumber={formatNumber}
                  formatPercentage={formatPercentage}
                  getRiskColor={getRiskColor}
                />
              </Tabs.Panel>
            </Tabs>
          </Tabs.Panel>

          {/* Performance Impact Tab */}
          <Tabs.Panel value="performance" pt="md">
            {analytics.performanceImpact && (
              <SimpleGrid cols={{ base: 1, md: 2 }} spacing="md">
                <Card withBorder padding="md" radius="md">
                  <Title order={4} mb="md">
                    Performance Issues Impact
                  </Title>
                  <Stack gap="md">
                    <div>
                      <Group justify="space-between" mb={4}>
                        <Text size="sm">Users with Crashes</Text>
                        <Text size="sm" fw={600}>
                          {formatNumber(analytics.performanceImpact.usersWithCrashes)} (
                          {formatPercentage(
                            (analytics.performanceImpact.usersWithCrashes / analytics.totalUsers) *
                              100
                          )}
                          )
                        </Text>
                      </Group>
                      <Text size="xs" c="dimmed">
                        Avg: {analytics.performanceImpact.avgCrashRate.toFixed(1)} crashes/user
                      </Text>
                    </div>

                    <div>
                      <Group justify="space-between" mb={4}>
                        <Text size="sm">Users with ANRs</Text>
                        <Text size="sm" fw={600}>
                          {formatNumber(analytics.performanceImpact.usersWithAnrs)} (
                          {formatPercentage(
                            (analytics.performanceImpact.usersWithAnrs / analytics.totalUsers) * 100
                          )}
                          )
                        </Text>
                      </Group>
                      <Text size="xs" c="dimmed">
                        Avg: {analytics.performanceImpact.avgAnrRate.toFixed(1)} ANRs/user
                      </Text>
                    </div>

                    <div>
                      <Group justify="space-between" mb={4}>
                        <Text size="sm">Users with Frozen Frames</Text>
                        <Text size="sm" fw={600}>
                          {formatNumber(analytics.performanceImpact.usersWithFrozenFrames)} (
                          {formatPercentage(
                            (analytics.performanceImpact.usersWithFrozenFrames /
                              analytics.totalUsers) *
                              100
                          )}
                          )
                        </Text>
                      </Group>
                      <Text size="xs" c="dimmed">
                        Avg: {formatPercentage(
                          analytics.performanceImpact.avgFrozenFrameRate * 100
                        )}{" "}
                        frozen frame rate
                      </Text>
                    </div>
                  </Stack>
                </Card>

                <Card withBorder padding="md" radius="md">
                  <Title order={4} mb="md">
                    Performance Risk Correlation
                  </Title>
                  <Group justify="center">
                    <RingProgress
                      size={200}
                      thickness={20}
                      sections={[
                        {
                          value: Math.min(100, Math.abs(analytics.performanceImpact.performanceRiskCorrelation)),
                          color: getRiskColor(70),
                          tooltip: `Performance issues increase risk by ${formatPercentage(
                            Math.abs(analytics.performanceImpact.performanceRiskCorrelation)
                          )}`,
                        },
                      ]}
                      label={
                        <Text size="lg" fw={700} ta="center">
                          {formatPercentage(
                            Math.abs(analytics.performanceImpact.performanceRiskCorrelation)
                          )}
                        </Text>
                      }
                    />
                  </Group>
                  <Text size="sm" c="dimmed" ta="center" mt="md">
                    Risk increase from performance issues
                  </Text>
                </Card>
              </SimpleGrid>
            )}
          </Tabs.Panel>

          {/* Engagement Patterns Tab */}
          <Tabs.Panel value="engagement" pt="md">
            {analytics.engagementPatterns && (
              <SimpleGrid cols={{ base: 1, md: 2 }} spacing="md">
                <Card withBorder padding="md" radius="md">
                  <Title order={4} mb="md">
                    Engagement Health
                  </Title>
                  <Stack gap="md">
                    <div>
                      <Group justify="space-between" mb={4}>
                        <Text size="sm">Inactive Users (7+ days)</Text>
                        <Text size="sm" fw={600} c="red">
                          {formatNumber(analytics.engagementPatterns.inactiveUsers)} (
                          {formatPercentage(
                            (analytics.engagementPatterns.inactiveUsers / analytics.totalUsers) *
                              100
                          )}
                          )
                        </Text>
                      </Group>
                    </div>

                    <div>
                      <Group justify="space-between" mb={4}>
                        <Text size="sm">Declining Engagement</Text>
                        <Text size="sm" fw={600} c="yellow">
                          {formatNumber(analytics.engagementPatterns.decliningUsers)} (
                          {formatPercentage(
                            (analytics.engagementPatterns.decliningUsers / analytics.totalUsers) *
                              100
                          )}
                          )
                        </Text>
                      </Group>
                      <Text size="xs" c="dimmed">
                        Session frequency declining by 50%+
                      </Text>
                    </div>

                    <div>
                      <Text size="sm" mb={4}>
                        Average Days Since Last Session
                      </Text>
                      <Text size="xl" fw={700}>
                        {analytics.engagementPatterns.avgDaysSinceLastSession.toFixed(1)} days
                      </Text>
                    </div>
                  </Stack>
                </Card>

                <Card withBorder padding="md" radius="md">
                  <Title order={4} mb="md">
                    Session Metrics
                  </Title>
                  <Stack gap="md">
                    <div>
                      <Text size="sm" mb={4}>
                        Avg Sessions (Last 7 Days)
                      </Text>
                      <Text size="xl" fw={700}>
                        {analytics.engagementPatterns.avgSessionsLast7Days.toFixed(1)}
                      </Text>
                    </div>

                    <div>
                      <Text size="sm" mb={4}>
                        Avg Sessions (Last 30 Days)
                      </Text>
                      <Text size="xl" fw={700}>
                        {analytics.engagementPatterns.avgSessionsLast30Days.toFixed(1)}
                      </Text>
                    </div>

                    <div>
                      <Text size="sm" mb={4}>
                        Avg Session Duration
                      </Text>
                      <Text size="xl" fw={700}>
                        {Math.round(analytics.engagementPatterns.avgSessionDuration / 1000)}s
                      </Text>
                    </div>
                  </Stack>
                </Card>
              </SimpleGrid>
            )}
          </Tabs.Panel>

          {/* Root Causes Tab (ML-Driven) */}
          <Tabs.Panel value="root-causes" pt="md">
            {analytics.rootCauseAnalysis ? (
              <Stack gap="md">
                <Card withBorder padding="md" radius="md">
                  <Title order={4} mb="md">
                    ML-Identified Root Causes
                  </Title>
                  <Text size="sm" c="dimmed" mb="lg">
                    Discovered automatically using ML feature importance analysis
                  </Text>
                  <Stack gap="md">
                    {analytics.rootCauseAnalysis.primaryCauses?.map((cause, idx) => (
                      <Paper key={idx} p="md" withBorder>
                        <Group justify="space-between" mb="sm">
                          <div style={{ flex: 1 }}>
                            <Text size="lg" fw={600} mb={4}>
                              {idx + 1}. {cause.cause}
                            </Text>
                            <Text size="sm" c="dimmed">
                              {cause.recommendedFix}
                            </Text>
                          </div>
                          <Badge size="lg" color={getRiskColor(cause.averageSeverity)}>
                            Impact: {cause.impactScore.toFixed(0)}
                          </Badge>
                        </Group>
                        <SimpleGrid cols={3} spacing="md" mt="md">
                          <div>
                            <Text size="xs" c="dimmed">Affected Users</Text>
                            <Text size="lg" fw={600}>
                              {formatNumber(cause.affectedUserCount)}
                            </Text>
                          </div>
                          <div>
                            <Text size="xs" c="dimmed">ML Importance</Text>
                            <Text size="lg" fw={600}>
                              {formatPercentage(cause.importance * 100)}
                            </Text>
                          </div>
                          <div>
                            <Text size="xs" c="dimmed">Est. Churn Reduction</Text>
                            <Text size="lg" fw={600} c="green">
                              {formatPercentage(cause.estimatedChurnReduction)}%
                            </Text>
                          </div>
                        </SimpleGrid>
                        {cause.affectedSegments && cause.affectedSegments.length > 0 && (
                          <div mt="sm">
                            <Text size="xs" c="dimmed" mb={4}>Affected Segments:</Text>
                            <Group gap="xs">
                              {cause.affectedSegments.slice(0, 5).map((segment, i) => (
                                <Badge key={i} variant="light" size="sm">
                                  {segment}
                                </Badge>
                              ))}
                            </Group>
                          </div>
                        )}
                      </Paper>
                    ))}
                  </Stack>
                </Card>

                {/* Feature Importance */}
                {analytics.rootCauseAnalysis.aggregateFeatureImportance && (
                  <Card withBorder padding="md" radius="md">
                    <Title order={4} mb="md">
                      Feature Importance (ML Model)
                    </Title>
                    <Stack gap="sm">
                      {Object.entries(analytics.rootCauseAnalysis.aggregateFeatureImportance)
                        .sort((a, b) => b[1] - a[1])
                        .slice(0, 10)
                        .map(([feature, importance]) => (
                          <div key={feature}>
                            <Group justify="space-between" mb={4}>
                              <Text size="sm" fw={500}>
                                {feature}
                              </Text>
                              <Text size="sm" fw={600}>
                                {formatPercentage(importance * 100)}
                              </Text>
                            </Group>
                            <Progress value={importance * 100} size="sm" radius="xl" />
                          </div>
                        ))}
                    </Stack>
                  </Card>
                )}
              </Stack>
            ) : (
              <Card withBorder padding="md" radius="md">
                <Text c="dimmed">Root cause analysis not available. ML service may be unavailable.</Text>
              </Card>
            )}
          </Tabs.Panel>

          {/* Priority Fixes Tab */}
          <Tabs.Panel value="priority-fixes" pt="md">
            {analytics.priorityFixes && analytics.priorityFixes.length > 0 ? (
              <Stack gap="md">
                <Card withBorder padding="md" radius="md">
                  <Title order={4} mb="md">
                    Priority Fixes (Ranked by Impact)
                  </Title>
                  <Text size="sm" c="dimmed" mb="lg">
                    Prioritized based on ML impact analysis
                  </Text>
                  <Table>
                    <Table.Thead>
                      <Table.Tr>
                        <Table.Th>Priority</Table.Th>
                        <Table.Th>Issue</Table.Th>
                        <Table.Th>Affected Users</Table.Th>
                        <Table.Th>Impact Score</Table.Th>
                        <Table.Th>Effort</Table.Th>
                        <Table.Th>Est. Reduction</Table.Th>
                        <Table.Th>Fix Description</Table.Th>
                      </Table.Tr>
                    </Table.Thead>
                    <Table.Tbody>
                      {analytics.priorityFixes.map((fix, idx) => (
                        <Table.Tr key={idx}>
                          <Table.Td>
                            <Badge
                              size="lg"
                              color={
                                fix.priority >= 8
                                  ? "red"
                                  : fix.priority >= 5
                                  ? "yellow"
                                  : "blue"
                              }
                            >
                              #{fix.priority}
                            </Badge>
                          </Table.Td>
                          <Table.Td>
                            <Text size="sm" fw={500}>
                              {fix.issue}
                            </Text>
                          </Table.Td>
                          <Table.Td>
                            <Text size="sm">{formatNumber(fix.estimatedAffectedUsers)}</Text>
                          </Table.Td>
                          <Table.Td>
                            <Text size="sm" fw={600}>
                              {fix.impactScore.toFixed(0)}
                            </Text>
                          </Table.Td>
                          <Table.Td>
                            <Badge
                              color={
                                fix.estimatedEffort === "Low"
                                  ? "green"
                                  : fix.estimatedEffort === "Medium"
                                  ? "yellow"
                                  : "red"
                              }
                            >
                              {fix.estimatedEffort}
                            </Badge>
                          </Table.Td>
                          <Table.Td>
                            <Text size="sm" fw={600} c="green">
                              {formatPercentage(fix.estimatedChurnReduction)}%
                            </Text>
                          </Table.Td>
                          <Table.Td>
                            <Tooltip label={fix.fixDescription} multiline width={300}>
                              <Text size="sm" c="dimmed" style={{ maxWidth: 200 }} truncate>
                                {fix.fixDescription}
                              </Text>
                            </Tooltip>
                          </Table.Td>
                        </Table.Tr>
                      ))}
                    </Table.Tbody>
                  </Table>
                </Card>
              </Stack>
            ) : (
              <Card withBorder padding="md" radius="md">
                <Text c="dimmed">Priority fixes not available.</Text>
              </Card>
            )}
          </Tabs.Panel>

          {/* Trends & Anomalies Tab */}
          <Tabs.Panel value="trends" pt="md">
            {analytics.trendAnalysis ? (
              <SimpleGrid cols={{ base: 1, md: 2 }} spacing="md">
                <Card withBorder padding="md" radius="md">
                  <Title order={4} mb="md">
                    Trend Analysis
                  </Title>
                  <Stack gap="md">
                    <div>
                      <Text size="sm" c="dimmed" mb={4}>
                        Trend Direction
                      </Text>
                      <Group gap="xs">
                        <Badge
                          size="lg"
                          color={
                            analytics.trendAnalysis.trendDirection > 0
                              ? "red"
                              : analytics.trendAnalysis.trendDirection < 0
                              ? "green"
                              : "gray"
                          }
                        >
                          {analytics.trendAnalysis.trendDirectionLabel || "stable"}
                        </Badge>
                        <Text size="lg" fw={700}>
                          {analytics.trendAnalysis.trendDirection > 0 ? "+" : ""}
                          {formatPercentage(analytics.trendAnalysis.trendDirection)}
                        </Text>
                      </Group>
                    </div>

                    <div>
                      <Text size="sm" c="dimmed" mb={4}>
                        Trend Strength
                      </Text>
                      <Text size="xl" fw={700}>
                        {analytics.trendAnalysis.trendStrength.toFixed(2)}
                      </Text>
                    </div>

                    <div>
                      <Text size="sm" c="dimmed" mb={4}>
                        Statistical Significance
                      </Text>
                      <Badge
                        color={analytics.trendAnalysis.statisticalSignificance ? "green" : "gray"}
                      >
                        {analytics.trendAnalysis.statisticalSignificance
                          ? "Significant"
                          : "Not Significant"}
                      </Badge>
                    </div>

                    {analytics.trendAnalysis.currentPeriod && (
                      <div>
                        <Text size="sm" c="dimmed" mb={4}>
                          Current Period Avg Risk
                        </Text>
                        <Text size="xl" fw={700}>
                          {analytics.trendAnalysis.currentPeriod.averageRiskScore.toFixed(1)}
                        </Text>
                      </div>
                    )}

                    {analytics.trendAnalysis.previousPeriod && (
                      <div>
                        <Text size="sm" c="dimmed" mb={4}>
                          Previous Period Avg Risk
                        </Text>
                        <Text size="xl" fw={700}>
                          {analytics.trendAnalysis.previousPeriod.averageRiskScore.toFixed(1)}
                        </Text>
                      </div>
                    )}
                  </Stack>
                </Card>

                <Card withBorder padding="md" radius="md">
                  <Title order={4} mb="md">
                    Anomalies Detected
                  </Title>
                  {analytics.trendAnalysis.isAnomaly ? (
                    <Stack gap="md">
                      <Badge size="lg" color="red">
                        ⚠️ Anomaly Detected
                      </Badge>
                      {analytics.trendAnalysis.anomalies?.map((anomaly, idx) => (
                        <Paper key={idx} p="md" withBorder>
                          <Group justify="space-between" mb="sm">
                            <Badge color="red">{anomaly.type}</Badge>
                            <Text size="xs" c="dimmed">
                              {anomaly.detectedAt}
                            </Text>
                          </Group>
                          <Text size="sm" fw={500} mb={4}>
                            {anomaly.description}
                          </Text>
                          <Text size="xs" c="dimmed">
                            Potential Cause: {anomaly.potentialCause}
                          </Text>
                          <Text size="xs" c="dimmed" mt={4}>
                            Severity: {anomaly.severity.toFixed(2)}
                          </Text>
                        </Paper>
                      ))}
                    </Stack>
                  ) : (
                    <Text c="dimmed">No anomalies detected</Text>
                  )}
                </Card>
              </SimpleGrid>
            ) : (
              <Card withBorder padding="md" radius="md">
                <Text c="dimmed">Trend analysis not available. Historical data may be required.</Text>
              </Card>
            )}
          </Tabs.Panel>

          {/* Patterns Tab (ML-Driven) */}
          <Tabs.Panel value="patterns" pt="md">
            {analytics.patternInsights ? (
              <Stack gap="md">
                <Card withBorder padding="md" radius="md">
                  <Title order={4} mb="md">
                    ML-Discovered Churn Patterns
                  </Title>
                  <Text size="sm" c="dimmed" mb="lg">
                    Patterns discovered automatically using ML clustering
                  </Text>
                  <Stack gap="md">
                    {analytics.patternInsights.commonPatterns?.map((pattern, idx) => (
                      <Paper key={idx} p="md" withBorder>
                        <Group justify="space-between" mb="sm">
                          <div style={{ flex: 1 }}>
                            <Text size="lg" fw={600} mb={4}>
                              Pattern {idx + 1}: {pattern.pattern}
                            </Text>
                            {pattern.characteristics && (
                              <Text size="sm" c="dimmed">
                                {JSON.stringify(pattern.characteristics, null, 2)}
                              </Text>
                            )}
                          </div>
                          <Badge
                            size="lg"
                            color={getRiskColor(pattern.averageRiskScore)}
                          >
                            Risk: {pattern.averageRiskScore.toFixed(1)}
                          </Badge>
                        </Group>
                        <SimpleGrid cols={3} spacing="md" mt="md">
                          <div>
                            <Text size="xs" c="dimmed">Users</Text>
                            <Text size="lg" fw={600}>
                              {formatNumber(pattern.userCount)}
                            </Text>
                          </div>
                          <div>
                            <Text size="xs" c="dimmed">Avg Risk</Text>
                            <Text size="lg" fw={600}>
                              {pattern.averageRiskScore.toFixed(1)}
                            </Text>
                          </div>
                          <div>
                            <Text size="xs" c="dimmed">Churn Prob</Text>
                            <Text size="lg" fw={600}>
                              {formatPercentage(pattern.churnProbability * 100)}
                            </Text>
                          </div>
                        </SimpleGrid>
                        {pattern.commonSegments && pattern.commonSegments.length > 0 && (
                          <div mt="sm">
                            <Text size="xs" c="dimmed" mb={4}>Common Segments:</Text>
                            <Group gap="xs">
                              {pattern.commonSegments.slice(0, 5).map((segment, i) => (
                                <Badge key={i} variant="light" size="sm">
                                  {segment}
                                </Badge>
                              ))}
                            </Group>
                          </div>
                        )}
                      </Paper>
                    ))}
                  </Stack>
                </Card>
              </Stack>
            ) : (
              <Card withBorder padding="md" radius="md">
                <Text c="dimmed">Pattern insights not available. ML service may be unavailable.</Text>
              </Card>
            )}
          </Tabs.Panel>
        </Tabs>
      </Stack>
    </div>
  );
}

function SegmentTable({
  segments,
  title,
  formatNumber,
  formatPercentage,
  getRiskColor,
}: {
  segments: Record<string, ChurnAnalyticsResponse.SegmentStats> | undefined;
  title: string;
  formatNumber: (num: number) => string;
  formatPercentage: (num: number) => string;
  getRiskColor: (score: number) => string;
}) {
  if (!segments || Object.keys(segments).length === 0) {
    return (
      <Card withBorder padding="md" radius="md">
        <Text c="dimmed">No segment data available</Text>
      </Card>
    );
  }

  const segmentEntries = Object.entries(segments).sort(
    (a, b) => b[1].userCount - a[1].userCount
  );

  return (
    <Card withBorder padding="md" radius="md">
      <Title order={4} mb="md">
        {title}
      </Title>
      <Table>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>Segment</Table.Th>
            <Table.Th>Users</Table.Th>
            <Table.Th>Avg Risk</Table.Th>
            <Table.Th>High Risk %</Table.Th>
            <Table.Th>Churn Prob</Table.Th>
            <Table.Th>Top Risk Factors</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          {segmentEntries.map(([segment, stats]) => (
            <Table.Tr key={segment}>
              <Table.Td>
                <Stack gap={2}>
                  <Text size="sm" fw={segment === "Other" ? 600 : 500} c={segment === "Other" ? "dimmed" : undefined}>
                    {segment === "Other" ? "Other (aggregated)" : segment}
                  </Text>
                  {segment === "Other" && (
                    <Text size="xs" c="dimmed">
                      Segments with &lt;0.5% of users
                    </Text>
                  )}
                </Stack>
              </Table.Td>
              <Table.Td>
                <Text size="sm">{formatNumber(stats.userCount)}</Text>
              </Table.Td>
              <Table.Td>
                <Badge color={getRiskColor(stats.averageRiskScore)}>
                  {stats.averageRiskScore.toFixed(1)}
                </Badge>
              </Table.Td>
              <Table.Td>
                <Text size="sm" fw={600} c={stats.highRiskPercentage > 20 ? "red" : "default"}>
                  {formatPercentage(stats.highRiskPercentage)}
                </Text>
              </Table.Td>
              <Table.Td>
                <Text size="sm">{formatPercentage(stats.churnProbability * 100)}</Text>
              </Table.Td>
              <Table.Td>
                <Tooltip
                  label={stats.topRiskFactors?.join(", ")}
                  multiline
                  width={300}
                >
                  <Text size="sm" c="dimmed" style={{ maxWidth: 200 }} truncate>
                    {stats.topRiskFactors?.[0] || "N/A"}
                  </Text>
                </Tooltip>
              </Table.Td>
            </Table.Tr>
          ))}
        </Table.Tbody>
      </Table>
    </Card>
  );
}

