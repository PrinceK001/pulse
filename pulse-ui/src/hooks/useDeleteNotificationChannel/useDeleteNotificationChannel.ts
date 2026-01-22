import { UseMutationResult, useMutation, useQueryClient } from "@tanstack/react-query";
import { ApiResponse, makeRequest } from "../../helpers/makeRequest";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import {
  DeleteNotificationChannelRequest,
  DeleteNotificationChannelResponse,
  UseDeleteNotificationChannelOptions,
} from "./useDeleteNotificationChannel.interface";

export const useDeleteNotificationChannel = (
  options: UseDeleteNotificationChannelOptions = {}
): UseMutationResult<
  ApiResponse<DeleteNotificationChannelResponse>,
  unknown,
  DeleteNotificationChannelRequest,
  unknown
> => {
  const queryClient = useQueryClient();
  const deleteNotificationChannel = API_ROUTES.DELETE_NOTIFICATION_CHANNEL;

  return useMutation<
    ApiResponse<DeleteNotificationChannelResponse>,
    unknown,
    DeleteNotificationChannelRequest
  >({
    mutationFn: (params: DeleteNotificationChannelRequest) => {
      return makeRequest<DeleteNotificationChannelResponse>({
        url: `${API_BASE_URL}${deleteNotificationChannel.apiPath}/${params.notification_channel_id}`,
        init: {
          method: deleteNotificationChannel.method,
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
