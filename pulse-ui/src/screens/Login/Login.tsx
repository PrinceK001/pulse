import {
  Box,
  Text,
  useMantineTheme,
  Stack,
  Container,
  Image,
  Button,
  Select,
  Badge,
  Paper,
} from "@mantine/core";
import { CredentialResponse, GoogleLogin } from "@react-oauth/google";
import classes from "./Login.module.css";
import {
  COMMON_CONSTANTS,
  COOKIES_KEY,
  LOGIN_PAGE_CONSTANTS,
  ROUTES,
  MULTI_TENANT_CONSTANTS,
} from "../../constants";
import { useNavigate } from "react-router-dom";
import { IconSquareRoundedX } from "@tabler/icons-react";
import { authenticateUser } from "../../helpers/authenticateUser";
import { useEffect, useState } from "react";
import { LoaderWithMessage } from "../../components/LoaderWithMessage";
import { setCookiesAfterAuthentication } from "../../helpers/setCookiesAfterAuthentication";
import { showNotification } from "../../helpers/showNotification";
import { getCookies } from "../../helpers/cookies";
import { checkRefreshTokenExpiration } from "../../helpers/checkRefreshTokenExpiration";
import { logEvent } from "../../helpers/googleAnalytics";
import {
  isGcpMultiTenantEnabled,
  getGcpTenantOptions,
  getDefaultGcpTenantId,
  signInWithGoogleGcp,
} from "../../helpers/gcpAuth";

export function Login() {
  const navigate = useNavigate();
  const theme = useMantineTheme();
  const [isFetchingTokensFromServer, setIsFetchingTokensFromServer] =
    useState<boolean>(false);
  const gcpTenantOptions = getGcpTenantOptions();
  const defaultTenantId = getDefaultGcpTenantId();
  const [selectedTenantId, setSelectedTenantId] = useState<string | null>(
    defaultTenantId ?? null,
  );

  const gcpMultiTenantEnabled = isGcpMultiTenantEnabled();

  const googleOAuthEnabled = process.env.REACT_APP_GOOGLE_OAUTH_ENABLED;
  const googleClientId = process.env.REACT_APP_GOOGLE_CLIENT_ID ?? "";
  const isDevMode = process.env.NODE_ENV === "development";

  // Determine if dummy login should be shown
  const shouldShowDummyLogin =
    !gcpMultiTenantEnabled &&
    (googleOAuthEnabled === "false" ||
      (isDevMode && (!googleClientId || googleClientId.trim() === "")));

  useEffect(() => {
    const refreshToken = getCookies(COOKIES_KEY.REFRESH_TOKEN);
    if (refreshToken && refreshToken !== "undefined") {
      const isRefreshTokenExpired = checkRefreshTokenExpiration(refreshToken);
      if (!isRefreshTokenExpired) {
        navigate(ROUTES.HOME.basePath);
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const showErrorToast = (errorMessage: string) => {
    showNotification(
      COMMON_CONSTANTS.ERROR_NOTIFICATION_TITLE,
      errorMessage,
      <IconSquareRoundedX />,
      theme.colors.red[6],
    );
  };

  const onSuccess = async ({ credential }: CredentialResponse) => {
    setIsFetchingTokensFromServer(true);
    logEvent("User logged in", ROUTES.LOGIN.key);
    const { data, error } = await authenticateUser(credential);
    if (data) {
      await setCookiesAfterAuthentication(data);
      navigate(ROUTES.HOME.basePath);
      setIsFetchingTokensFromServer(false);
      return;
    }
    setIsFetchingTokensFromServer(false);
    if (error) {
      showErrorToast(error.message);
    }
  };

  const onError = () => {
    showErrorToast(COMMON_CONSTANTS.DEFAULT_ERROR_MESSAGE);
  };

  const onDummyLogin = async () => {
    setIsFetchingTokensFromServer(true);
    logEvent("User logged in (dummy)", ROUTES.LOGIN.key);
    // Use a dummy token that the backend will recognize
    const { data, error } = await authenticateUser("dev-id-token");
    if (data) {
      await setCookiesAfterAuthentication(data);
      navigate(ROUTES.HOME.basePath);
      setIsFetchingTokensFromServer(false);
      return;
    }
    setIsFetchingTokensFromServer(false);
    if (error) {
      showErrorToast(error.message);
    }
  };

  const onGcpSignIn = async () => {
    const tenantId = selectedTenantId ?? defaultTenantId ?? undefined;
    if (!tenantId) {
      showErrorToast("Please select a tenant");
      return;
    }
    setIsFetchingTokensFromServer(true);
    logEvent("User logged in (GCP)", ROUTES.LOGIN.key);
    try {
      const { idToken, email, tenantId: effectiveTenantId } =
        await signInWithGoogleGcp(tenantId);
      const { data, error } = await authenticateUser(idToken, {
        tenantId: effectiveTenantId ?? tenantId,
        userEmail: email,
      });
      if (data) {
        await setCookiesAfterAuthentication(data, {
          tenantId: effectiveTenantId ?? tenantId,
        });
        navigate(ROUTES.HOME.basePath);
        setIsFetchingTokensFromServer(false);
        return;
      }
      if (error) {
        showErrorToast(error.message);
      }
    } catch (err) {
      showErrorToast(
        err instanceof Error ? err.message : COMMON_CONSTANTS.DEFAULT_ERROR_MESSAGE,
      );
    }
    setIsFetchingTokensFromServer(false);
  };

  return (
    <Box className={classes.loginContainer}>
      <Container size="fluid" className={classes.mainWrapper}>
        <div className={classes.splitLayout}>
          {/* Left Side - Dashboard Preview */}
          <Box className={classes.leftSide}>
            <Box className={classes.dashboardPreview}>
              <Image
                src={(process.env.PUBLIC_URL || '') + "/landing_page.png"}
                alt="Dashboard Preview"
                className={classes.previewImage}
              />
            </Box>
          </Box>

          {/* Right Side - Hero & Login */}
          <Box className={classes.rightSide}>
            <Stack
              align="center"
              justify="center"
              className={classes.rightContent}
            >
              <Stack align="center" gap="md" className={classes.heroSection}>
                <h1 className={classes.mainHeading}>
                  All Your App Insights in One Dashboard
                </h1>
                <Text className={classes.subtitle}>
                  Real-time visibility into what matters most - your users'
                  experience
                </Text>
              </Stack>

              <Box className={classes.loginCard}>
                {isFetchingTokensFromServer ? (
                  <LoaderWithMessage
                    className={classes.loader}
                    loadingMessage={LOGIN_PAGE_CONSTANTS.SIGNING_IN_MESSAGE}
                  />
                ) : (
                  <Stack align="center" gap="lg">
                    <Text className={classes.loginPrompt}>
                      Sign in to get started
                    </Text>
                    {gcpMultiTenantEnabled ? (
                      <Paper
                        p="lg"
                        radius="md"
                        withBorder
                        style={{
                          minWidth: 280,
                          borderColor: "rgba(14, 201, 194, 0.3)",
                          backgroundColor: "rgba(255, 255, 255, 0.9)",
                        }}
                      >
                        <Stack gap="md" align="center">
                          <Badge
                            variant="light"
                            color="teal"
                            size="sm"
                            radius="sm"
                          >
                            {MULTI_TENANT_CONSTANTS.BADGE_LABEL}
                          </Badge>
                          <Select
                            label={MULTI_TENANT_CONSTANTS.TENANT_LABEL}
                            placeholder={
                              MULTI_TENANT_CONSTANTS.TENANT_PLACEHOLDER
                            }
                            data={gcpTenantOptions}
                            value={selectedTenantId}
                            onChange={(v) => setSelectedTenantId(v)}
                            size="md"
                            style={{ width: "100%" }}
                          />
                          <Button
                            size="lg"
                            radius="xl"
                            onClick={onGcpSignIn}
                            fullWidth
                            style={{
                              background:
                                "linear-gradient(135deg, #0ec9c2 0%, #0ba09a 100%)",
                              border: "none",
                              fontWeight: 600,
                              fontSize: "16px",
                              padding: "12px 32px",
                            }}
                          >
                            {MULTI_TENANT_CONSTANTS.SIGN_IN_BUTTON}
                          </Button>
                        </Stack>
                      </Paper>
                    ) : shouldShowDummyLogin ? (
                      <Button
                        size="lg"
                        radius="xl"
                        onClick={onDummyLogin}
                        style={{
                          background:
                            "linear-gradient(135deg, #0ec9c2 0%, #0ba09a 100%)",
                          border: "none",
                          fontWeight: 600,
                          fontSize: "16px",
                          padding: "12px 32px",
                          minWidth: "240px",
                        }}
                      >
                        Sign in (Dev Mode)
                      </Button>
                    ) : (
                      <GoogleLogin
                        shape="pill"
                        size="large"
                        onSuccess={onSuccess}
                        onError={onError}
                      />
                    )}
                    <Text className={classes.loginSubtext}>
                      {gcpMultiTenantEnabled
                        ? MULTI_TENANT_CONSTANTS.SIGN_IN_SUBTEXT
                        : shouldShowDummyLogin
                          ? "Development mode: Using dummy authentication"
                          : "Access powerful analytics and insights"}
                    </Text>
                  </Stack>
                )}
              </Box>
            </Stack>
          </Box>
        </div>
      </Container>

      {/* Background decorative elements */}
      <div className={classes.bgDecoration1}></div>
      <div className={classes.bgDecoration2}></div>
      <div className={classes.bgDecoration3}></div>
    </Box>
  );
}
