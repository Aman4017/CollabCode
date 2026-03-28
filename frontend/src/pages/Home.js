import React, { useState } from 'react'
import { v4 as uuidv4 } from 'uuid'
import toast from 'react-hot-toast'
import { useNavigate } from 'react-router-dom'

const Home = () => {
    const navigate = useNavigate();
    const [roomId, setRoomId] = useState('');
    const [userName, setUserName] = useState('');

    const createNewRoom = (e) => {
        e.preventDefault();
        const id = uuidv4();
        setRoomId(id);
        toast.success('Created a new room');
    }

    const joinRoom = () => {
        if (!roomId || !userName) {
            toast.error('ROOM ID & username is required');
            return;
        }
        navigate(`/editor/${roomId}`, {
            state: { userName },
        })
    }

    const handleInputEnter = (e) => {
        if (e.key === 'Enter') {
            joinRoom();
        }
    }

    return (
        <div className='homepageWrapper'>
            <div className="formWrapper">
                <div className="logoHeader">
                    <img src="/Code Collab copy.png" alt="CodeCollab" />
                    <p className="tagline">Real-time collaborative coding</p>
                </div>
                <h4 className='mainLabel'>Paste Invitation ROOM ID</h4>
                <div className="inputGroup">
                    <input
                        type="text"
                        className='inputBox'
                        placeholder='ROOM ID'
                        value={roomId}
                        onChange={(e) => setRoomId(e.target.value)}
                        onKeyUp={handleInputEnter}
                    />
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
