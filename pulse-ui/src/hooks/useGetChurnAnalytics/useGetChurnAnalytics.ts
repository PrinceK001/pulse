import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { makeRequest } from "../../helpers/makeRequest";
import { ApiResponse } from "../../helpers/makeRequest";
import { ChurnAnalyticsRequest, ChurnAnalyticsResponse } from "./useGetChurnAnalytics.interface";

export function useGetChurnAnalytics(request: ChurnAnalyticsRequest) {
  const analyticsRoute = API_ROUTES.GET_CHURN_ANALYTICS;
  
  return useQuery({
    queryKey: ["churn-analytics", request],
    queryFn: async () => {
      return makeRequest<ChurnAnalyticsResponse>({
        url: `${API_BASE_URL}${analyticsRoute.apiPath}`,
        init: {
          method: analyticsRoute.method,
          body: JSON.stringify(request),
        },
      });
    },
    refetchInterval: 300000, // Refetch every 5 minutes
  });
}

