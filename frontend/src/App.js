/**
 * App.js — The root component of the React application.
 *
 * WHAT THIS FILE DOES:
 * --------------------
 * This is the top-level component that React renders first. It sets up:
 * 1. TOAST NOTIFICATIONS: A global toast notification system (react-hot-toast)
 *    that shows popup messages like "User joined" or "Room ID copied".
 * 2. CLIENT-SIDE ROUTING: Uses React Router to map URL paths to page components:
 *    - "/"              → Home page (create/join room)
 *    - "/editor/:roomId" → Editor page (the collaborative coding environment)
 *
 * WHAT IS REACT ROUTER?
 * ---------------------
 * In a traditional website, when you click a link, the browser loads a completely
 * new page from the server. React Router enables "client-side routing" — when you
 * navigate to /editor/abc-123, React Router just swaps the visible component
 * WITHOUT reloading the page. This makes navigation instant and preserves app state.
 *
 * COMPONENT HIERARCHY:
 * --------------------
 *   <App>
 *     ├── <Toaster />            (global toast notification container)
 *     └── <BrowserRouter>         (enables client-side URL routing)
 *           └── <Routes>          (defines the route → component mappings)
 *                 ├── "/" → <Home />           (landing page with join form)
 *                 └── "/editor/:roomId" → <EditorPage />  (collaborative editor)
 */
import './App.css';
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import Home from './pages/Home'
import EditorPage from './pages/EditorPage';
import { Toaster } from 'react-hot-toast';

function App() {
    return (
        <>
            {/* Global toast notification container — positioned top-right */}
            {/* Styled to match the Dracula dark theme used throughout the app */}
            <Toaster
                position='top-right'
                toastOptions={{
                    style: {
                        background: '#282a36',
                        color: '#f8f8f2',
                        border: '1px solid #44475a',
                        borderRadius: '8px',
                        fontSize: '14px',
                    },
                }}
            />
            {/* BrowserRouter uses the browser's History API for clean URLs (no # in URL) */}
            <BrowserRouter>
                <Routes>
                    {/* Landing page — user creates or joins a room */}
                    <Route path='/' element={<Home />} />

                    {/* Editor page — :roomId is a URL parameter captured by useParams() */}
                    {/* Example: /editor/abc-123 → roomId = "abc-123" */}
                    <Route path='/editor/:roomId' element={<EditorPage />} />
                </Routes>
            </BrowserRouter>
        </>
    );
}

export default App;
