import { createBrowserRouter, Navigate } from 'react-router'
import { LoginPage, RequireAuth } from '@/authentication'
import { reservationRoutes } from '@/reservations'
import { spaceRoutes } from '@/spaces'
import { userRoutes } from '@/users'
import { HomePage } from './HomePage'
import { AppLayout } from './layouts/AppLayout'

export const router = createBrowserRouter([
  { path: '/login', element: <LoginPage /> },
  {
    element: <RequireAuth />,
    children: [
      {
        element: <AppLayout />,
        children: [
          { index: true, element: <HomePage /> },
          ...reservationRoutes,
          ...spaceRoutes,
          ...userRoutes,
        ],
      },
    ],
  },
  { path: '*', element: <Navigate to="/" replace /> },
])
