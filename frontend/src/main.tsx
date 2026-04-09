import React from 'react';
import ReactDOM from 'react-dom/client';
import TicketingPage from './features/ticketing/TicketingPage';
import './styles/globals.css';
import './styles/app.css';

document.documentElement.setAttribute(
  'data-theme',
  localStorage.getItem('theme') || 'dark'
);

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <div className="content-shell" style={{ minHeight: '100vh' }}>
      <TicketingPage />
    </div>
  </React.StrictMode>
);
