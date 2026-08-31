import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { App } from './app/App'
import { registerSessionExpiry } from './app/sessionExpiry'
import './app/styles/global.css'

// 描画より前に呼ぶこと。
registerSessionExpiry()

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <App />
  </StrictMode>,
)
