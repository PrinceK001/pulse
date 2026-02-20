import { Container, Title, Table, Button, Group, Text } from '@mantine/core';
import { IconUserPlus } from '@tabler/icons-react';

export function OrganizationMembers() {
  return (
    <Container size="xl">
      <Group justify="space-between" mb="xl">
        <Title order={1}>Team Members</Title>
        <Button leftSection={<IconUserPlus size={16} />}>
          Invite Member
        </Button>
      </Group>
      
      <Table>
        <Table.Thead>
          <Table.Tr>
            <Table.Th>Name</Table.Th>
            <Table.Th>Email</Table.Th>
            <Table.Th>Role</Table.Th>
            <Table.Th>Projects</Table.Th>
          </Table.Tr>
        </Table.Thead>
        <Table.Tbody>
          <Table.Tr>
            <Table.Td colSpan={4}>
              <Text c="dimmed" ta="center" py="xl">
                Member management will be available soon. This will fetch from /v1/tenants/&#123;tenantId&#125;/members
              </Text>
            </Table.Td>
          </Table.Tr>
        </Table.Tbody>
      </Table>
    </Container>
  );
}
