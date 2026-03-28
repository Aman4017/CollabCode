import React, { useCallback, useEffect, useRef, useState } from 'react'
import toast from 'react-hot-toast'
import Client from '../components/Client';
import Editor from '../components/Editor';
import { initSocket } from '../socket';
import ACTIONS from '../Actions';
import { useLocation, useNavigate, Navigate, useParams } from 'react-router-dom';
import peer from '../service/peer';

const VideoPlayer = React.memo(({ stream, muted = false }) => {
    const videoRef = useRef(null);
    useEffect(() => {
        if (videoRef.current) {
            videoRef.current.srcObject = stream || null;
        }
    }, [stream]);
    return <video ref={videoRef} autoPlay playsInline muted={muted} />;
});

const EditorPage = () => {
    const [socket, setSocket] = useState(null);
    const socketRef = useRef(null);
    const location = useLocation();
    const reactNavigator = useNavigate();
    const { roomId } = useParams();
    const codeRef = useRef(null);
    const myStreamRef = useRef(null);

    const [clients, setClients] = useState([]);
    const [remoteSocketId, setRemoteSocketId] = useState(null);
    const [myStream, setMyStream] = useState(null);
    const [remoteStream, setRemoteStream] = useState(null);
    const [sidebarOpen, setSidebarOpen] = useState(false);

    const userName = location.state?.userName;

    useEffect(() => { myStreamRef.current = myStream; }, [myStream]);

    // Main socket connection — stable deps only
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
                    setRemoteSocketId(socketId);
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

            // WebRTC signaling
            sock.on('incoming-call', async ({ from, offer }) => {
                setRemoteSocketId(from);
                try {
                    const stream = await navigator.mediaDevices.getUserMedia({
                        audio: true, video: true,
                    });
                    setMyStream(stream);
                    const ans = await peer.getAnswer(offer);
                    sock.emit('call-accepted', { to: from, ans });
                } catch (err) {
                    toast.error('Could not access camera/microphone');
                }
            });

            sock.on('call-accepted', async ({ ans }) => {
                await peer.setRemoteDescription(ans);
                const stream = myStreamRef.current;
                if (stream) {
                    const senders = peer.peer.getSenders();
                    for (const track of stream.getTracks()) {
                        if (!senders.find(s => s.track === track)) {
                            peer.peer.addTrack(track, stream);
                        }
                    }
                }
            });

            sock.on('peer-nego-needed', async ({ from, offer }) => {
                const ans = await peer.getAnswer(offer);
                sock.emit('peer-nego-done', { to: from, ans });
            });

            sock.on('peer-nego-final', async ({ ans }) => {
                await peer.setRemoteDescription(ans);
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

    // Peer negotiation
    useEffect(() => {
        const handleNegoNeeded = async () => {
            try {
                const offer = await peer.getOffer();
                if (socketRef.current) {
                    socketRef.current.emit('peer-nego-needed', { offer, to: remoteSocketId });
                }
            } catch (err) {
                console.error('Negotiation error:', err);
            }
        };

        peer.peer.addEventListener('negotiationneeded', handleNegoNeeded);
        return () => {
            peer.peer.removeEventListener('negotiationneeded', handleNegoNeeded);
        };
    }, [remoteSocketId]);

    // Remote track listener
    useEffect(() => {
        const handleTrack = (ev) => {
            setRemoteStream(ev.streams[0]);
        };
        peer.peer.addEventListener('track', handleTrack);
        return () => {
            peer.peer.removeEventListener('track', handleTrack);
        };
    }, []);

    // Peer cleanup on unmount
    useEffect(() => {
        return () => {
            if (myStreamRef.current) {
                myStreamRef.current.getTracks().forEach(track => track.stop());
            }
            peer.reset();
        };
    }, []);

    const handleCallUser = async () => {
        try {
            const stream = await navigator.mediaDevices.getUserMedia({
                audio: true, video: true,
            });
            setMyStream(stream);
            const offer = await peer.getOffer();
            if (socketRef.current) {
                socketRef.current.emit('user-call', { to: remoteSocketId, offer });
            }
        } catch (err) {
            toast.error('Could not access camera/microphone');
        }
    };

    const sendStreams = useCallback(() => {
        if (myStream) {
            const senders = peer.peer.getSenders();
            for (const track of myStream.getTracks()) {
                if (!senders.find(s => s.track === track)) {
                    peer.peer.addTrack(track, myStream);
                }
            }
        }
    }, [myStream]);

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
        if (myStreamRef.current) {
            myStreamRef.current.getTracks().forEach(track => track.stop());
        }
        reactNavigator('/');
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
                <div className="asideInner">
                    <div className="logo">
                        <img src="/Code Collab copy.png" alt="CodeCollab" />
                    </div>
                    <h3>Connected</h3>
                    <div className="clientList">
                        {clients.map((client) => (
                            <Client
                                key={client.socketId}
                                userName={client.userName}
                            />
                        ))}
                    </div>
                </div>

                <div className="asideActions">
                    {remoteSocketId && (
                        <div className="callActions">
                            <button className='btn callBtn' onClick={handleCallUser}>
                                📞 Call
                            </button>
                            <button className='btn callBtn' onClick={sendStreams}>
                                📤 Send
                            </button>
                        </div>
                    )}
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
                {(myStream || remoteStream) && (
                    <div className="player">
                        {myStream && (
                            <div className="playerWrapper">
                                <VideoPlayer stream={myStream} muted={true} />
                                <span className="playerLabel">You</span>
                            </div>
                        )}
                        {remoteStream && (
                            <div className="playerWrapper">
                                <VideoPlayer stream={remoteStream} />
                                <span className="playerLabel">Remote</span>
                            </div>
                        )}
                    </div>
                )}
            </div>
        </div>
    );
}

export default EditorPage;
