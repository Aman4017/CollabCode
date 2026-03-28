/**
 * EditorPage.js — The main collaborative editor page with sidebar, chat, and editor.
 *
 * WHAT THIS FILE DOES:
 * --------------------
 * This is the largest and most complex page in the app. When a user navigates to
 * /editor/room-abc, this component renders and manages:
 *
 * 1. WEBSOCKET CONNECTION: Establishes and manages the WebSocket connection to the
 *    backend server for real-time communication.
 *
 * 2. SIDEBAR (left panel):
 *    - Logo and connected users list with avatars
 *    - Real-time chat messaging
 *    - "Copy Room ID" and "Leave Room" buttons
 *    - On mobile: collapsible sidebar with hamburger menu
 *
 * 3. EDITOR (right panel):
 *    - The <Editor> component (CodeMirror code editor with compile support)
 *
 * REACT CONCEPTS USED:
 * --------------------
 * - useState:      Manages reactive state (clients, messages, socket, etc.)
 * - useEffect:     Runs side effects (WebSocket setup, auto-scroll)
 * - useRef:        Holds mutable values that persist across re-renders
 * - useCallback:   Memoizes a function so it doesn't change on every render
 * - useParams:     Extracts the roomId from the URL (/editor/:roomId)
 * - useLocation:   Accesses the navigation state (contains userName)
 * - useNavigate:   Programmatically navigates (e.g., redirect to home)
 *
 * LIFECYCLE FLOW:
 * ---------------
 * 1. Component mounts → useEffect runs → initSocket() connects WebSocket
 * 2. Socket connects → emit "join" event with roomId and userName
 * 3. Server responds with "joined" event containing the client list
 * 4. User interacts (types code, sends chat messages)
 * 5. Component unmounts (user leaves) → cleanup disconnects the WebSocket
 *
 * REAL-TIME EVENT HANDLING:
 * -------------------------
 * Incoming events from the server:
 *   "joined"       → Update the connected clients list
 *   "disconnected" → Remove a user from the clients list
 *   "chat-message" → Add the message to the chat display
 *   "code-change"  → Handled by the Editor component directly
 */
import React, { useCallback, useEffect, useRef, useState } from 'react'
import toast from 'react-hot-toast'
import Client from '../components/Client';
import Editor from '../components/Editor';
import { initSocket } from '../socket';
import ACTIONS from '../Actions';
import { useLocation, useNavigate, Navigate, useParams } from 'react-router-dom';

const EditorPage = () => {
    /**
     * SOCKET STATE:
     * socket (useState): The WebSocket connection stored as React state. When this
     *   changes from null to a connected socket, components that depend on it
     *   (like Editor) re-render with the new socket.
     * socketRef (useRef): A ref to the same socket, used inside event handlers
     *   and the cleanup function where we need the latest socket value without
     *   triggering re-renders.
     */
    const [socket, setSocket] = useState(null);
    const socketRef = useRef(null);

    /**
     * ROUTER HOOKS:
     * location:        Contains the current URL and navigation state.
     *                  location.state.userName has the username passed from Home.js.
     * reactNavigator:  Function to navigate to another page (e.g., redirect to "/").
     * roomId:          The room ID extracted from the URL path (/editor/:roomId).
     */
    const location = useLocation();
    const reactNavigator = useNavigate();
    const { roomId } = useParams();

    /**
     * CODE STATE:
     * codeRef: Stores the latest code from the editor. Using a ref (not state)
     *   because we don't want code changes to trigger a re-render of this component
     *   — the Editor handles its own rendering. This ref is used when syncing code
     *   to newly joined users.
     */
    const codeRef = useRef(null);

    /**
     * UI STATE:
     * clients:      Array of connected users (each has socketId and userName).
     * messages:     Array of chat messages (each has message, userName, timestamp).
     * messageInput: Current text in the chat input field.
     * sidebarOpen:  Whether the sidebar is visible (for mobile responsiveness).
     * chatEndRef:   Ref to an empty div at the bottom of chat, used for auto-scrolling.
     */
    const [clients, setClients] = useState([]);
    const [messages, setMessages] = useState([]);
    const [messageInput, setMessageInput] = useState('');
    const [sidebarOpen, setSidebarOpen] = useState(false);
    const chatEndRef = useRef(null);

    /**
     * Extract the userName from the navigation state passed by Home.js.
     * If the user navigated directly to /editor/xyz without going through
     * Home.js first, location.state will be null and userName will be undefined.
     */
    const userName = location.state?.userName;

    /**
     * Effect: Initialize WebSocket connection and set up all event listeners.
     *
     * This is the main setup effect that runs when the component mounts (and when
     * roomId, userName, or reactNavigator change). It:
     *
     * 1. Calls initSocket() to establish a WebSocket connection.
     * 2. Emits a "join" event to tell the server we want to enter the room.
     * 3. Sets up listeners for server events:
     *    - "connect_error": Show a toast when connection is lost.
     *    - "joined": Update the client list and toast when someone new joins.
     *    - "disconnected": Remove the user from the list and show a toast.
     *    - "chat-message": Add incoming messages to the chat.
     *
     * The "mounted" flag prevents state updates after the component unmounts
     * (which would cause React warnings). This is important because initSocket()
     * is asynchronous — the component might unmount while we're waiting.
     *
     * Cleanup: When the component unmounts or the effect re-runs, the socket
     * is disconnected to prevent memory leaks and ghost connections.
     *
     * DEPENDENCY ARRAY [roomId, userName, reactNavigator]:
     * These are the values this effect depends on. If any of them change,
     * the effect re-runs (old socket is cleaned up, new one is created).
     */
    useEffect(() => {
        if (!userName) return; // No username = user navigated here directly

        let mounted = true;

        const init = async () => {
            let sock;
            try {
                sock = await initSocket();
            } catch (err) {
                if (mounted) {
                    toast.error('Socket connection failed, try again later.');
                    reactNavigator('/');
                }
                return;
            }

            // If component unmounted while we were connecting, clean up
            if (!mounted) {
                sock.disconnect();
                return;
            }

            // Store the socket in both state (for React rendering) and ref (for callbacks)
            socketRef.current = sock;
            setSocket(sock);

            // Handle connection errors (e.g., network drops)
            sock.on('connect_error', () => {
                toast.error('Connection lost. Trying to reconnect...');
            });

            // Tell the server we're joining this room
            sock.emit(ACTIONS.JOIN, { roomId, userName });

            // Handle "joined" events — someone (including ourselves) joined the room
            sock.on(ACTIONS.JOINED, ({ clients: clientList, userName: joinedUser, socketId }) => {
                if (joinedUser !== userName) {
                    toast.success(`${joinedUser} joined the room.`);
                    // Send our current code to the new joiner so their editor syncs
                    sock.emit(ACTIONS.SYNC_CODE, {
                        code: codeRef.current,
                        socketId,
                    });
                }
                // Update the connected clients list for the sidebar
                setClients(clientList);
            });

            // Handle "disconnected" events — someone left the room
            sock.on(ACTIONS.DISCONNECTED, ({ socketId, userName: leftUser }) => {
                toast.success(`${leftUser} left the room.`);
                // Remove the disconnected user from our local client list
                setClients(prev => prev.filter(c => c.socketId !== socketId));
            });

            // Handle incoming chat messages from other users
            sock.on('chat-message', ({ message, userName: sender, timestamp }) => {
                setMessages(prev => [...prev, { message, userName: sender, timestamp }]);
            });
        };

        init();

        // Cleanup function: runs when the component unmounts or effect re-runs
        return () => {
            mounted = false;
            if (socketRef.current) {
                socketRef.current.disconnect();
                socketRef.current = null;
                setSocket(null);
            }
        };
    }, [roomId, userName, reactNavigator]);

    /**
     * Effect: Auto-scroll the chat to the bottom when new messages arrive.
     *
     * chatEndRef points to an invisible <div> at the bottom of the chat messages.
     * scrollIntoView() smoothly scrolls the chat container so the latest message
     * is visible. The ?. (optional chaining) ensures no error if the ref is null.
     */
    useEffect(() => {
        chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages]);

    /**
     * sendMessage — Sends a chat message to all other users in the room.
     *
     * Flow:
     * 1. Trims whitespace from the input. If empty or no socket, do nothing.
     * 2. Adds the message to our own chat display immediately (optimistic UI).
     * 3. Emits the message via WebSocket to the server.
     * 4. The server broadcasts it to all other room members.
     * 5. Clears the input field.
     *
     * Note: The sender adds the message locally (step 2) and the server broadcasts
     * to everyone EXCEPT the sender (to avoid duplicate messages).
     */
    const sendMessage = () => {
        const text = messageInput.trim();
        if (!text || !socketRef.current) return;

        const msg = {
            message: text,
            userName,
            timestamp: Date.now(),
        };

        // Add to our own message list immediately (no waiting for server)
        setMessages(prev => [...prev, msg]);
        // Send to server for broadcasting to other users
        socketRef.current.emit('chat-message', { message: text });
        setMessageInput('');
    };

    /**
     * handleCodeChange — Callback passed to the Editor component.
     *
     * Called every time the editor content changes. Stores the latest code in
     * codeRef so we can send it to newly joining users via SYNC_CODE.
     *
     * useCallback with [] dependency means this function reference stays the same
     * across re-renders, which prevents unnecessary re-renders of the Editor.
     *
     * @param {string} code - The full source code string from the editor.
     */
    const handleCodeChange = useCallback((code) => {
        codeRef.current = code;
    }, []);

    /**
     * copyRoomID — Copies the current room ID to the system clipboard.
     *
     * Uses the browser's Clipboard API (navigator.clipboard.writeText).
     * Shows a success or error toast depending on whether it worked.
     * Some browsers restrict clipboard access in certain contexts.
     */
    async function copyRoomID() {
        try {
            await navigator.clipboard.writeText(roomId);
            toast.success('Room ID copied');
        } catch (error) {
            toast.error('Could not copy Room ID');
        }
    }

    /**
     * leaveRoom — Navigates the user back to the home page.
     *
     * When the user clicks "Leave Room", we navigate to "/". This triggers
     * the useEffect cleanup which disconnects the WebSocket, and the server
     * broadcasts a "disconnected" event to remaining room members.
     */
    function leaveRoom() {
        reactNavigator('/');
    }

    /**
     * formatTime — Converts a Unix timestamp to a human-readable time string.
     *
     * Used in the chat to show when each message was sent.
     * Example: 1711234567890 → "2:36 PM"
     *
     * @param {number} ts - Unix timestamp in milliseconds.
     * @returns {string} Formatted time string (e.g., "2:36 PM").
     */
    function formatTime(ts) {
        const d = new Date(ts);
        return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    }

    /**
     * Guard: If there's no location.state (user navigated directly to
     * /editor/xyz without going through the Home page), redirect to "/".
     * The <Navigate> component from React Router performs an immediate redirect.
     */
    if (!location.state) {
        return <Navigate to="/" />;
    }

    return (
        <div className='mainWrap'>
            {/* Hamburger/close button — only visible on mobile (< 768px) */}
            <button
                className="sidebarToggle"
                onClick={() => setSidebarOpen(!sidebarOpen)}
                aria-label="Toggle sidebar"
            >
                {sidebarOpen ? '✕' : '☰'}
            </button>

            {/* Dark overlay behind the sidebar on mobile — clicking it closes the sidebar */}
            {sidebarOpen && (
                <div
                    className="sidebarOverlay"
                    onClick={() => setSidebarOpen(false)}
                />
            )}

            {/* ===== SIDEBAR ===== */}
            <div className={`aside ${sidebarOpen ? 'open' : ''}`}>
                {/* Header section: logo + connected users */}
                <div className="asideHeader">
                    <div className="logo">
                        <img src="/Code Collab copy.png" alt="CodeCollab" />
                    </div>
                    <div className="connectedSection">
                        <h3>Connected <span className="badge">{clients.length}</span></h3>
                        <div className="clientList">
                            {/* Render one Client component per connected user */}
                            {clients.map((client) => (
                                <Client
                                    key={client.socketId}
                                    userName={client.userName}
                                />
                            ))}
                        </div>
                    </div>
                </div>

                {/* Chat section: messages and input */}
                <div className="chatSection">
                    <h3>Chat</h3>
                    <div className="chatMessages">
                        {messages.length === 0 && (
                            <p className="chatEmpty">No messages yet. Say hello!</p>
                        )}
                        {messages.map((msg, i) => (
                            <div
                                key={i}
                                className={`chatMsg ${msg.userName === userName ? 'own' : ''}`}
                            >
                                <div className="chatMsgHeader">
                                    <span className="chatUser">{msg.userName}</span>
                                    <span className="chatTime">{formatTime(msg.timestamp)}</span>
                                </div>
                                <span className="chatText">{msg.message}</span>
                            </div>
                        ))}
                        {/* Invisible element at the bottom — used for auto-scrolling */}
                        <div ref={chatEndRef} />
                    </div>
                    <div className="chatInputArea">
                        <input
                            type="text"
                            className="chatInput"
                            value={messageInput}
                            onChange={(e) => setMessageInput(e.target.value)}
                            onKeyDown={(e) => e.key === 'Enter' && sendMessage()}
                            placeholder="Type a message..."
                        />
                        <button className="btn chatSendBtn" onClick={sendMessage}>
                            Send
                        </button>
                    </div>
                </div>

                {/* Action buttons: Copy Room ID and Leave Room */}
                <div className="asideActions">
                    <button className='btn copyBtn' onClick={copyRoomID}>
                        Copy ROOM ID
                    </button>
                    <button className='btn leaveBtn' onClick={leaveRoom}>
                        Leave Room
                    </button>
                </div>
            </div>

            {/* ===== EDITOR AREA ===== */}
            <div className="editorWrap">
                <Editor
                    socket={socket}
                    roomId={roomId}
                    onCodeChange={handleCodeChange}
                />
            </div>
        </div>
    );
}

export default EditorPage;
