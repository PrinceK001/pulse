import { ApiResponse } from "../../helpers/makeRequest";
import { NotificationChannelType } from "../useGetAlertNotificationChannels/useGetAlertNotificationChannels.interface";

export type UpdateNotificationChannelRequest = {
  notification_channel_id: number;
  name: string;
  type: NotificationChannelType;
  config: string;
};

export type UpdateNotificationChannelResponse = boolean;

export type UpdateNotificationChannelOnSettled = (
  data: ApiResponse<UpdateNotificationChannelResponse> | undefined,
  error: unknown,
  variables: UpdateNotificationChannelRequest,
  context: unknown,
) => void;

export interface UseUpdateNotificationChannelOptions {
  onSettled?: UpdateNotificationChannelOnSettled;
}
