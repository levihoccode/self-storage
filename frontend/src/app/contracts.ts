import { apiRequest } from "./api";
import { authGateway } from "./auth";
import type { CustomerContract } from "./types";

function accessToken(): string | undefined {
  return authGateway.getSession()?.token;
}

export function listMyContracts(signal?: AbortSignal): Promise<CustomerContract[]> {
  return apiRequest<CustomerContract[]>("/api/contracts", {
    accessToken: accessToken(),
    signal,
  });
}
