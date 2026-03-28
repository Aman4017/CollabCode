import React, { useCallback, useEffect, useRef, useState } from 'react'
import toast from 'react-hot-toast'
import Client from '../components/Client';
import Editor from '../components/Editor';
import { initSocket } from '../socket';
import ACTIONS from '../Actions';
import { useLocation, useNavigate, Navigate, useParams } from 'react-router-dom';

const EditorPage = () => {
    const [socket, setSocket] = useState(null);
    const socketRef = useRef(null);
    const location = useLocation();
    const reactNavigator = useNavigate();
    const { roomId } = useParams();
    const codeRef = useRef(null);

    const [clients, setClients] = useState([]);
    const [messages, setMessages] = useState([]);
    const [messageInput, setMessageInput] = useState('');
    const [sidebarOpen, setSidebarOpen] = useState(false);
    const chatEndRef = useRef(null);

    const userName = location.state?.userName;

    useEffect(() => {
        if (!userName) return;

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

            if (!mounted) {
                sock.disconnect();
                return;
            }

            socketRef.current = sock;
            setSocket(sock);

            sock.on('connect_error', () => {
                toast.error('Connection lost. Trying to reconnect...');
            });

            sock.emit(ACTIONS.JOIN, { roomId, userName });

            sock.on(ACTIONS.JOINED, ({ clients: clientList, userName: joinedUser, socketId }) => {
                if (joinedUser !== userName) {
                    toast.success(`${joinedUser} joined the room.`);
                    sock.emit(ACTIONS.SYNC_CODE, {
                        code: codeRef.current,
                        socketId,
                    });
                }
                setClients(clientList);
            });

            sock.on(ACTIONS.DISCONNECTED, ({ socketId, userName: leftUser }) => {
                toast.success(`${leftUser} left the room.`);
                setClients(prev => prev.filter(c => c.socketId !== socketId));
            });

            sock.on('chat-message', ({ message, userName: sender, timestamp }) => {
                setMessages(prev => [...prev, { message, userName: sender, timestamp }]);
            });
        };

        init();

        return () => {
            mounted = false;
            if (socketRef.current) {
                socketRef.current.disconnect();
                socketRef.current = null;
                setSocket(null);
            }
        };
    }, [roomId, userName, reactNavigator]);

    // Auto-scroll chat on new messages
    useEffect(() => {
        chatEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages]);

    const sendMessage = () => {
        const text = messageInput.trim();
        if (!text || !socketRef.current) return;

        const msg = {
            message: text,
            userName,
            timestamp: Date.now(),
        };

        setMessages(prev => [...prev, msg]);
        socketRef.current.emit('chat-message', { message: text });
        setMessageInput('');
    };

    const handleCodeChange = useCallback((code) => {
        codeRef.current = code;
    }, []);

    async function copyRoomID() {
        try {
            await navigator.clipboard.writeText(roomId);
            toast.success('Room ID copied');
        } catch (error) {
            toast.error('Could not copy Room ID');
        }
    }

    function leaveRoom() {
        reactNavigator('/');
    }

    function formatTime(ts) {
        const d = new Date(ts);
        return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
    }

    if (!location.state) {
        return <Navigate to="/" />;
    }

    return (
        <div className='mainWrap'>
            <button
                className="sidebarToggle"
                onClick={() => setSidebarOpen(!sidebarOpen)}
                aria-label="Toggle sidebar"
            >
                {sidebarOpen ? '✕' : '☰'}
            </button>

            {sidebarOpen && (
                <div
                    className="sidebarOverlay"
                    onClick={() => setSidebarOpen(false)}
                />
            )}

            <div className={`aside ${sidebarOpen ? 'open' : ''}`}>
                <div className="asideHeader">
                    <div className="logo">
                        <img src="/Code Collab copy.png" alt="CodeCollab" />
                    </div>
                    <div className="connectedSection">
                        <h3>Connected <span className="badge">{clients.length}</span></h3>
                        <div className="clientList">
                            {clients.map((client) => (
                                <Client
                                    key={client.socketId}
                                    userName={client.userName}
                                />
                            ))}
                        </div>
                    </div>
                </div>

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

                <div className="asideActions">
                    <button className='btn copyBtn' onClick={copyRoomID}>
                        Copy ROOM ID
                    </button>
                    <button className='btn leaveBtn' onClick={leaveRoom}>
                        Leave Room
                    </button>
                </div>
            </div>

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
