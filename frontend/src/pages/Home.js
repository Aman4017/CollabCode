/**
 * Home.js — The landing page where users create or join a collaboration room.
 *
 * WHAT THIS FILE DOES:
 * --------------------
 * This page renders a form with two input fields (Room ID and Username) and a
 * "Join Room" button. Users can either:
 *   1. Paste an existing Room ID (shared by someone else) and join that room.
 *   2. Click "new room" to generate a random Room ID, then join with their name.
 *
 * REACT CONCEPTS USED:
 * --------------------
 * - useState:     Stores the Room ID and Username as the user types them.
 * - useNavigate:  Programmatically navigates to the editor page after joining.
 * - Components:   This is a "functional component" — a JavaScript function that
 *                 returns JSX (HTML-like syntax that React converts to real HTML).
 *
 * NAVIGATION FLOW:
 * ----------------
 *   1. User enters Room ID + Username
 *   2. Clicks "Join Room" (or presses Enter)
 *   3. navigate('/editor/abc-123', { state: { userName: 'Alice' } })
 *      → React Router loads the EditorPage component
 *      → EditorPage reads the roomId from the URL and userName from location.state
 */
import React, { useState } from 'react'
import { v4 as uuidv4 } from 'uuid'
import toast from 'react-hot-toast'
import { useNavigate } from 'react-router-dom'

const Home = () => {
    /**
     * useNavigate — React Router hook that returns a function to navigate programmatically.
     * Instead of using an <a> tag (which reloads the page), navigate() changes the URL
     * and renders the new component without a page reload.
     */
    const navigate = useNavigate();

    /**
     * useState — React's way of storing data that can change.
     * When you call setRoomId('new-value'), React re-renders this component
     * with the updated value, and the input field shows the new text.
     *
     * roomId:   The current value of the "ROOM ID" input field.
     * userName: The current value of the "USERNAME" input field.
     */
    const [roomId, setRoomId] = useState('');
    const [userName, setUserName] = useState('');

    /**
     * createNewRoom — Generates a new unique Room ID and fills it into the input.
     *
     * Uses UUID v4 (Universally Unique Identifier) to create a random string
     * like "550e8400-e29b-41d4-a716-446655440000". This is practically guaranteed
     * to be unique — the chance of collision is astronomically small.
     *
     * @param {Event} e - The click event from the "new room" button.
     *                     e.preventDefault() stops the form from submitting/reloading.
     */
    const createNewRoom = (e) => {
        e.preventDefault();
        const id = uuidv4();
        setRoomId(id);
        toast.success('Created a new room');
    }

    /**
     * joinRoom — Validates inputs and navigates to the editor page.
     *
     * Checks that both Room ID and Username are filled in. If valid, navigates
     * to /editor/{roomId} and passes the userName via React Router's "state"
     * mechanism (an invisible data bag attached to the navigation).
     *
     * The state is accessed in EditorPage via: const { userName } = location.state
     */
    const joinRoom = () => {
        if (!roomId || !userName) {
            toast.error('ROOM ID & username is required');
            return;
        }
        navigate(`/editor/${roomId}`, {
            state: { userName },
        })
    }

    /**
     * handleInputEnter — Allows joining a room by pressing the Enter key.
     *
     * Instead of requiring the user to click the "Join Room" button,
     * they can press Enter in either input field.
     *
     * @param {KeyboardEvent} e - The keyboard event. e.key tells us which key was pressed.
     */
    const handleInputEnter = (e) => {
        if (e.key === 'Enter') {
            joinRoom();
        }
    }

    return (
        <div className='homepageWrapper'>
            <div className="formWrapper">
                {/* Logo and tagline section */}
                <div className="logoHeader">
                    <img src="/Code Collab copy.png" alt="CodeCollab" />
                    <p className="tagline">Real-time collaborative coding</p>
                </div>

                <h4 className='mainLabel'>Paste Invitation ROOM ID</h4>

                <div className="inputGroup">
                    {/* Room ID input — user pastes an existing ID or gets one from createNewRoom */}
                    <input
                        type="text"
                        className='inputBox'
                        placeholder='ROOM ID'
                        value={roomId}
                        onChange={(e) => setRoomId(e.target.value)}
                        onKeyUp={handleInputEnter}
                    />
                    {/* Username input — the display name shown to other collaborators */}
                    <input
                        type="text"
                        className='inputBox'
                        placeholder='USERNAME'
                        value={userName}
                        onChange={(e) => setUserName(e.target.value)}
                        onKeyUp={handleInputEnter}
                    />
                    <button className='btn joinBtn' onClick={joinRoom}>Join Room</button>
                    <span className='createInfo'>
                        If you don't have an invite, create a{' '}
                        <button
                            type="button"
                            onClick={createNewRoom}
                            className='createNewBtn'
                        >
                            new room
                        </button>
                    </span>
                </div>
            </div>
        </div>
    )
}

export default Home
