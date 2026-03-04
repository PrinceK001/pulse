---
name: add-ui-screen
description: Step-by-step workflow for adding a new screen/page to pulse-ui. Use when creating a new UI screen, page, dashboard view, or route in pulse-ui/.
---

# Add UI Screen

## Workflow

```
- [ ] Step 1: Create screen folder structure
- [ ] Step 2: Define types/interfaces
- [ ] Step 3: Create React Query hook
- [ ] Step 4: Build the screen component
- [ ] Step 5: Add styles
- [ ] Step 6: Register route
- [ ] Step 7: Add navbar entry (if top-level)
- [ ] Step 8: Verify build
```

## Step 1: Create Folder

```
pulse-ui/src/screens/MyScreen/
├── MyScreen.tsx
├── MyScreen.interface.ts
├── MyScreen.module.css
├── MyScreen.constants.ts
├── index.ts
└── components/           (if needed)
```

`index.ts`:
```typescript
export { MyScreen } from "./MyScreen";
```

## Step 2: Define Types

`MyScreen.interface.ts`:
```typescript
export interface MyScreenProps {
  // props if any
}

export interface MyDataItem {
  id: string;
  name: string;
  // ...
}
```

## Step 3: React Query Hook

`pulse-ui/src/hooks/useGetMyData.ts`:
```typescript
import { useQuery } from "@tanstack/react-query";
import { makeRequest } from "../helpers/makeRequest/makeRequest";
import { API_BASE_URL } from "../constants/Constants";

export function useGetMyData(enabled: boolean) {
  return useQuery({
    queryKey: ["myData"],
    queryFn: () => makeRequest<MyDataResponse>({
      url: `${API_BASE_URL}/v1/my-endpoint`,
      init: { method: "GET" },
    }),
    enabled,
    refetchOnWindowFocus: false,
  });
}
```

## Step 4: Screen Component

Use Mantine components, handle loading/error states:
```typescript
import { Container, Title, Loader, Alert } from "@mantine/core";
import classes from "./MyScreen.module.css";
import { useGetMyData } from "../../hooks/useGetMyData";

export function MyScreen() {
  const { data, isLoading, error } = useGetMyData(true);
  if (isLoading) return <Loader />;
  if (error) return <Alert color="red">Failed to load data</Alert>;
  return (
    <Container className={classes.container}>
      <Title order={2}>My Screen</Title>
      {/* content */}
    </Container>
  );
}
```

## Step 5: Styles

`MyScreen.module.css` using Mantine CSS variables:
```css
.container {
  padding: var(--mantine-spacing-md);
}
```

## Step 6: Register Route

In `Constants.ts`, add to `ROUTES`:
```typescript
MY_SCREEN: { basePath: "/my-screen", ... }
```

In `App.tsx`, add `<Route>`:
```typescript
<Route path={ROUTES.MY_SCREEN.basePath} element={<MyScreen />} />
```

## Step 7: Navbar (optional)

Add entry in `NAVBAR_ITEMS` in `Constants.ts` with a Tabler icon.

## Step 8: Verify

```bash
cd pulse-ui && yarn build
```
