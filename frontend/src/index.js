/**
 * index.js — The entry point of the React application.
 *
 * WHAT THIS FILE DOES:
 * --------------------
 * This is the very first JavaScript file that runs when the app loads in the browser.
 * It finds the HTML element with id="root" in public/index.html and tells React
 * to render the entire application inside that element.
 *
 * HOW REACT RENDERING WORKS:
 * --------------------------
 * 1. The browser loads public/index.html which has: <div id="root"></div>
 * 2. This file finds that div using document.getElementById('root').
 * 3. ReactDOM.createRoot() creates a React rendering container attached to that div.
 * 4. root.render(<App />) tells React to render the App component inside it.
 * 5. React builds the entire UI tree (App → Routes → Home/EditorPage → etc.)
 *    and inserts the resulting HTML into the #root div.
 *
 * REACT STRICT MODE:
 * ------------------
 * <React.StrictMode> is a development-only wrapper that helps catch bugs by:
 * - Running effects (useEffect) twice to ensure proper cleanup.
 * - Warning about deprecated React patterns.
 * - It has NO effect in production builds.
 */
import React from 'react';
import ReactDOM from 'react-dom/client';
import './index.css';
import App from './App';
import reportWebVitals from './reportWebVitals';

const root = ReactDOM.createRoot(document.getElementById('root'));
root.render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);

reportWebVitals();
