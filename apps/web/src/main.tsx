import React from "react";
import ReactDOM from "react-dom/client";
import { BrowserRouter } from "react-router-dom";
import "./styles.css";
import App from "./App";
import { AuthProvider } from "./auth/AuthContext";
import { NotificationCenterProvider } from "./components/notifications/NotificationCenterProvider";

ReactDOM.createRoot(document.getElementById("root")!).render(
  <React.StrictMode>
    <BrowserRouter future={{ v7_startTransition: true, v7_relativeSplatPath: true }}>
      <AuthProvider>
        <NotificationCenterProvider>
          <App />
        </NotificationCenterProvider>
      </AuthProvider>
    </BrowserRouter>
  </React.StrictMode>,
);
