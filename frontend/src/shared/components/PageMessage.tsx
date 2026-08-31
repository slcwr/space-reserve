import type { ReactNode } from 'react'

export function PageMessage({ children }: { children: ReactNode }) {
  return (
    <div className="page-message" role="status">
      {children}
    </div>
  )
}
