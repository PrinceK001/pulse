import { UseMutationResult, useMutation, useQueryClient } from "@tanstack/react-query";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import {
  UpdateNotificationChannelRequest,
  UpdateNotificationChannelResponse,
  UseUpdateNotificationChannelOptions,
} from "./useUpdateNotificationChannel.interface";

export const useUpdateNotificationChannel = (
  options: UseUpdateNotificationChannelOptions = {}
): UseMutationResult<
  ApiResponse<UpdateNotificationChannelResponse>,
  unknown,
  UpdateNotificationChannelRequest,
  unknown
> => {
  const queryClient = useQueryClient();
  const updateNotificationChannel = API_ROUTES.UPDATE_NOTIFICATION_CHANNEL;

  return useMutation<
    ApiResponse<UpdateNotificationChannelResponse>,
    unknown,
    UpdateNotificationChannelRequest
  >({
    mutationFn: (params: UpdateNotificationChannelRequest) => {
      return makeRequest<UpdateNotificationChannelResponse>({
        url: `${API_BASE_URL}${updateNotificationChannel.apiPath}/${params.notification_channel_id}`,
        init: {
          method: updateNotificationChannel.method,
          headers: {
            "Content-Type": "application/json",
          },
          body: JSON.stringify({
            name: params.name,
            type: params.type,
            config: params.config,
          }),
        },
      });
    },
    onSettled: (data, error, variables, context) => {
      // Invalidate and refetch notification channels list on success
      if (data?.data && !data?.error) {
        queryClient.invalidateQueries({ 
          queryKey: [API_ROUTES.GET_ALERT_NOTIFICATION_CHANNELS.key] 
        });
      }
      options.onSettled?.(data, error, variables, context);
    },
  });
};
