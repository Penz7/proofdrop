import { useQuery } from "@tanstack/react-query";
import { api } from "./api";

export const useOrders = () => useQuery({ queryKey: ["orders"], queryFn: api.orders });

// Couriers carry online status and activeOrders, so refresh them periodically as well as on events.
export const useCouriers = () => useQuery({ queryKey: ["couriers"], queryFn: api.couriers, refetchInterval: 15_000 });

export const useDevices = () => useQuery({ queryKey: ["devices"], queryFn: api.devices, refetchInterval: 30_000 });

export const useEvidence = (courierId?: string) =>
  useQuery({ queryKey: ["evidence", courierId ?? "all"], queryFn: () => api.evidence(courierId) });
