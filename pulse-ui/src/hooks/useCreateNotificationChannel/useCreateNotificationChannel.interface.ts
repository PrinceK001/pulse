import { ApiResponse } from "../../helpers/makeRequest";
import { CreateNotificationChannelRequest } from "../useGetAlertNotificationChannels/useGetAlertNotificationChannels.interface";

export type CreateNotificationChannelParams = CreateNotificationChannelRequest;

export type CreateNotificationChannelResponse = boolean;

export type CreateNotificationChannelOnSettled = (
  data: ApiResponse<CreateNotificationChannelResponse> | undefined,
  error: unknown,
  variables: CreateNotificationChannelParams,
  context: unknown,
) => void;

export interface UseCreateNotificationChannelOptions {
  onSettled?: CreateNotificationChannelOnSettled;
}
