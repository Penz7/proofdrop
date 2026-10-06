import { StrictMode } from "react";
import { createRoot } from "react-dom/client";
import { BrowserRouter, Navigate, Route, Routes } from "react-router";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { ApiError } from "./lib/api";
import { AuthProvider, useAuth } from "./lib/auth";
import { RealtimeProvider } from "./lib/realtime";
import { Layout } from "./components/Layout";
import { LoginPage } from "./pages/Login";
import { OrdersPage } from "./pages/Orders";
import { LiveMapPage } from "./pages/LiveMap";
import { EvidencePage } from "./pages/Evidence";
import { DevicesPage } from "./pages/Devices";
import { PrintQrPage } from "./pages/PrintQr";
import { PrivacyPage } from "./pages/Privacy";
import "./index.css";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 10_000,
      // Don't retry auth/validation errors; do retry transient network failures.
      retry: (count, error) => !(error instanceof ApiError && error.status >= 400 && error.status < 500) && count < 2,
    },
  },
});

function Protected() {
  const { user } = useAuth();
  if (!user) return <Navigate to="/login" replace />;
  return (
    <RealtimeProvider>
      <Layout />
    </RealtimeProvider>
  );
}

createRoot(document.getElementById("root")!).render(
  <StrictMode>
    <QueryClientProvider client={queryClient}>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/login" element={<LoginPage />} />
            <Route path="/privacy" element={<PrivacyPage />} />
            <Route element={<Protected />}>
              <Route path="/orders" element={<OrdersPage />} />
              <Route path="/map" element={<LiveMapPage />} />
              <Route path="/evidence" element={<EvidencePage />} />
              <Route path="/devices" element={<DevicesPage />} />
              <Route path="/devices/print" element={<PrintQrPage />} />
            </Route>
            <Route path="*" element={<Navigate to="/orders" replace />} />
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </QueryClientProvider>
  </StrictMode>,
);
