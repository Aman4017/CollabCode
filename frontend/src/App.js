import './App.css';
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import Home from './pages/Home'
import EditorPage from './pages/EditorPage';
import { Toaster } from 'react-hot-toast';

function App() {
    return (
        <>
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
            <BrowserRouter>
                <Routes>
                    <Route path='/' element={<Home />} />
                    <Route path='/editor/:roomId' element={<EditorPage />} />
                </Routes>
            </BrowserRouter>
        </>
    );
}

export default App;
