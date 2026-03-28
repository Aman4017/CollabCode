/**
 * Client.js — Displays a single connected user's avatar and name in the sidebar.
 *
 * WHAT THIS FILE DOES:
 * --------------------
 * This is a small, reusable React component that renders one user "chip" in the
 * sidebar's "Connected" section. Each chip shows:
 *   1. A circular avatar (auto-generated from the user's name with unique colors).
 *   2. The user's display name.
 *
 * HOW IT'S USED:
 * --------------
 * In EditorPage.js, we map over the list of connected clients and render one
 * <Client> component for each:
 *
 *   {clients.map((client) => (
 *       <Client key={client.socketId} userName={client.userName} />
 *   ))}
 *
 * PROPS EXPLAINED:
 * ----------------
 * "Props" (short for "properties") are how React passes data from a parent
 * component to a child component. This component receives:
 *   - userName: The display name of the connected user (e.g., "Alice").
 *
 * REACT-AVATAR LIBRARY:
 * ---------------------
 * The Avatar component from 'react-avatar' automatically generates a colored
 * circular avatar with the user's initials. For example, "Alice" gets "A"
 * with a specific color, "Bob" gets "B" with a different color. No actual
 * profile pictures are needed.
 */
import React from 'react'
import Avatar from 'react-avatar'

/**
 * Client component — Renders a user's avatar and name.
 *
 * @param {Object} props          - React props passed from the parent component.
 * @param {string} props.userName - The display name of the connected user.
 *
 * @returns {JSX.Element} A div containing an Avatar circle and the username text.
 */
const Client = ({ userName }) => {
    return (
        <div className='client'>
            {/* Avatar: auto-generates a colored circle with the user's initial */}
            {/* size={40}: 40px diameter circle */}
            {/* round={true}: makes the avatar circular (not square) */}
            <Avatar name={userName} size={40} round={true} />
            <span className='userName'>{userName}</span>
        </div>
    )
}

export default Client
