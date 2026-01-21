import { useQuery } from "@tanstack/react-query";
import { API_BASE_URL, API_ROUTES } from "../../constants";
import { makeRequest } from "../../helpers/makeRequest";
import {
  ChurnPredictionRequest,
  ChurnPredictionResponse,
} from "./useGetChurnPredictions.interface";

export const useGetChurnPredictions = (request: ChurnPredictionRequest) => {
  const churnPredictions = API_ROUTES.GET_CHURN_PREDICTIONS;

  return useQuery({
    queryKey: [
      churnPredictions.key,
      request.userId,
      request.deviceModel,
      request.osVersion,
      request.appVersion,
      request.riskLevel,
      request.minRiskScore,
      request.limit,
    ],
    queryFn: async () => {
      return makeRequest<ChurnPredictionResponse>({
        url: `${API_BASE_URL}${churnPredictions.apiPath}`,
        init: {
          method: churnPredictions.method,
          body: JSON.stringify(request),
        },
      });
    },
    refetchOnWindowFocus: false,
    enabled: true,
    staleTime: 30000, // 30 seconds
  });
};

