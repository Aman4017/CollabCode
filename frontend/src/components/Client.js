import React from 'react'
import Avatar from 'react-avatar'

const Client = ({ userName }) => {
    return (
        <div className='client'>
            <Avatar name={userName} size={40} round={true} />
            <span className='userName'>{userName}</span>
        </div>
    )
}

export default Client
