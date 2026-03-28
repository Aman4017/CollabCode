/**
 * Actions.js — Defines all WebSocket event type constants used across the app.
 *
 * WHAT THIS FILE DOES:
 * --------------------
 * Instead of typing raw strings like "join" or "code-change" everywhere in the
 * code (which is error-prone — one typo and things break silently), we define
 * them as constants here. Then every file imports and uses ACTIONS.JOIN,
 * ACTIONS.CODE_CHANGE, etc.
 *
 * WHY USE CONSTANTS?
 * ------------------
 * If you mistype ACTIONS.JOINN, JavaScript throws an error immediately (undefined
 * property). But if you mistype "joinn" as a string, nothing warns you — it just
 * silently doesn't work, which is much harder to debug.
 *
 * HOW THESE MAP TO BACKEND EVENTS:
 * ---------------------------------
 * These constants match the "type" field in the JSON messages sent over WebSocket.
 * Both the frontend and backend must agree on these exact strings.
 *
 *   Frontend sends: socket.emit(ACTIONS.JOIN, { roomId, userName })
 *                   → becomes: { "type": "join", "data": { "roomId": "...", "userName": "..." } }
 *
 *   Backend receives the "type" field and routes to the appropriate handler.
 */
const ACTIONS = {
    /** Sent by client when a user wants to join a room. */
    JOIN: 'join',

    /** Sent by server to all room members when a user successfully joins. */
    JOINED: 'joined',

    /** Sent by server to remaining room members when a user leaves/disconnects. */
    DISCONNECTED: 'disconnected',

    /** Sent by client when code changes, and by server to broadcast the change to others. */
    CODE_CHANGE: 'code-change',

    /** Sent by client to share their current code with a specific newly-joined user. */
    SYNC_CODE: 'sync-code',

    /** Sent by client when intentionally leaving a room (currently unused but reserved). */
    LEAVE: 'leave',
};

export default ACTIONS;
