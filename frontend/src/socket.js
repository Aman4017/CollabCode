/**
 * socket.js — Custom WebSocket wrapper that provides a socket.io-like API.
 *
 * WHAT THIS FILE DOES:
 * --------------------
 * The browser has a built-in WebSocket API, but it's quite basic — you can only
 * send raw strings and listen with onmessage. This file wraps the native WebSocket
 * in a SocketWrapper class that provides a much nicer API:
 *
 *   socket.emit("join", { roomId, userName })    // Send structured events
 *   socket.on("joined", (data) => { ... })       // Listen for specific events
 *   socket.off("joined", handler)                // Remove a listener
 *   socket.disconnect()                          // Close the connection
 *
 * This mirrors the popular "socket.io" library API, but uses raw WebSockets
 * (no socket.io server needed). The backend expects this JSON protocol.
 *
 * KEY FEATURES:
 * -------------
 * 1. EVENT-BASED MESSAGING: Instead of raw strings, messages have a "type" and "data".
 * 2. MESSAGE QUEUE: If you emit() before the connection is open, messages are queued
 *    and sent automatically once the connection is established.
 * 3. AUTO-RECONNECTION: If the connection drops, it automatically retries after 2 seconds.
 * 4. GENERATION TRACKING: Prevents stale callbacks from firing after a reconnection.
 */

/**
 * SocketWrapper — Wraps the native WebSocket with an event-based API.
 *
 * LIFECYCLE:
 * ----------
 *   1. new SocketWrapper(url) → Creates instance and starts connecting
 *   2. Connection opens       → Flushes queued messages, fires 'connect' event
 *   3. Messages flow          → emit() sends, on() receives
 *   4. Connection drops       → Fires error events, auto-reconnects after 2s
 *   5. disconnect() called    → Stops reconnecting, closes the WebSocket
 *
 * GENERATION TRACKING (this.connectGeneration):
 * -----------------------------------------------
 * When a reconnection happens, a new WebSocket is created. But the old WebSocket's
 * callbacks (onopen, onmessage, etc.) might still fire if the old connection was
 * slow to close. The "generation" counter prevents these stale callbacks from
 * interfering. Each _connect() call increments the generation, and every callback
 * checks if its generation still matches the current one.
 */
class SocketWrapper {
    /**
     * Creates a new SocketWrapper and immediately begins connecting.
     *
     * @param {string} url - The WebSocket URL to connect to (e.g., "ws://localhost:8080/ws").
     */
    constructor(url) {
        /** @type {string} The WebSocket server URL. */
        this.url = url;

        /**
         * @type {Map<string, Set<Function>>} Event listeners map.
         * Key is the event type (e.g., "connect", "joined"), value is a Set of callback functions.
         */
        this.listeners = new Map();

        /**
         * @type {string[]} Messages queued while the WebSocket is not yet open.
         * These are flushed (sent) once the connection is established.
         */
        this.queue = [];

        /** @type {boolean} Whether the WebSocket is currently connected and open. */
        this.connected = false;

        /** @type {boolean} Whether we should attempt to reconnect after a disconnect. */
        this.shouldReconnect = true;

        /**
         * @type {number} Counter that increments on every _connect() call.
         * Used to detect and ignore callbacks from stale (previous) connections.
         */
        this.connectGeneration = 0;

        this._connect();
    }

    /**
     * Creates a new WebSocket connection and sets up all event handlers.
     *
     * This is called on initial construction and on every auto-reconnection attempt.
     * The generation counter ensures that if a new connection is initiated before the
     * old one fully closes, callbacks from the old connection are ignored.
     *
     * WEBSOCKET LIFECYCLE EVENTS:
     * - onopen:    Connection established → flush queued messages, fire 'connect'
     * - onmessage: Message received → parse JSON, fire the appropriate event
     * - onerror:   Error occurred → fire 'connect_error' and 'connect_failed'
     * - onclose:   Connection closed → schedule reconnection after 2 seconds
     */
    _connect() {
        const generation = ++this.connectGeneration;
        this.ws = new WebSocket(this.url);

        this.ws.onopen = () => {
            if (generation !== this.connectGeneration) return; // Stale connection, ignore
            this.connected = true;
            // Flush all queued messages
            this.queue.forEach(msg => this.ws.send(msg));
            this.queue = [];
            this._fire('connect');
        };

        this.ws.onmessage = (event) => {
            if (generation !== this.connectGeneration) return;
            try {
                const msg = JSON.parse(event.data);
                // Fire the event handler for msg.type (e.g., "joined", "code-change")
                this._fire(msg.type, msg.data);
            } catch (e) {
                console.error('WebSocket message parse error:', e);
            }
        };

        this.ws.onerror = () => {
            if (generation !== this.connectGeneration) return;
            this._fire('connect_error', new Error('WebSocket connection error'));
            this._fire('connect_failed', new Error('WebSocket connection error'));
        };

        this.ws.onclose = () => {
            if (generation !== this.connectGeneration) return;
            this.connected = false;
            // Auto-reconnect after 2 seconds (if not intentionally disconnected)
            if (this.shouldReconnect) {
                setTimeout(() => {
                    if (this.shouldReconnect && generation === this.connectGeneration) {
                        this._connect();
                    }
                }, 2000);
            }
        };
    }

    /**
     * Sends a message to the server with the given event type and data.
     *
     * Messages are formatted as JSON: { "type": "join", "data": { ... } }
     * If the connection isn't open yet, the message is queued and will be
     * sent automatically once the connection is established.
     *
     * @param {string} type - The event type (e.g., "join", "code-change").
     * @param {Object} data - The event payload (any JSON-serializable object).
     */
    emit(type, data) {
        const msg = JSON.stringify({ type, data });
        if (this.connected && this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(msg);
        } else {
            this.queue.push(msg);
        }
    }

    /**
     * Registers an event listener for a specific event type.
     *
     * Multiple listeners can be registered for the same event type.
     *
     * @param {string} type     - The event type to listen for (e.g., "joined", "code-change").
     * @param {Function} callback - The function to call when this event fires.
     *                              Receives the event's data payload as its argument.
     *
     * @example
     *   socket.on('joined', ({ clients, userName }) => {
     *     console.log(`${userName} joined! ${clients.length} users online.`);
     *   });
     */
    on(type, callback) {
        if (!this.listeners.has(type)) {
            this.listeners.set(type, new Set());
        }
        this.listeners.get(type).add(callback);
    }

    /**
     * Removes an event listener.
     *
     * If a specific callback is provided, only that callback is removed.
     * If no callback is provided, ALL listeners for that event type are removed.
     *
     * @param {string} type       - The event type to stop listening for.
     * @param {Function} [callback] - The specific callback to remove. If omitted,
     *                                all callbacks for this type are removed.
     */
    off(type, callback) {
        if (!this.listeners.has(type)) return;
        if (callback) {
            this.listeners.get(type).delete(callback);
        } else {
            this.listeners.delete(type);
        }
    }

    /**
     * Permanently closes the WebSocket connection.
     *
     * Stops auto-reconnection, clears the message queue, and closes the
     * underlying WebSocket. Called when the user leaves the editor page.
     */
    disconnect() {
        this.shouldReconnect = false;
        this.queue = [];
        this.connectGeneration++; // Invalidate any pending reconnection attempts
        if (this.ws) {
            this.ws.close();
            this.ws = null;
        }
    }

    /**
     * Fires all registered callbacks for a given event type.
     *
     * This is an internal method called by the WebSocket event handlers.
     * Each callback is wrapped in a try/catch so one failing listener
     * doesn't prevent others from running.
     *
     * @param {string} type - The event type that occurred.
     * @param {*} data      - The data to pass to each callback.
     */
    _fire(type, data) {
        const callbacks = this.listeners.get(type);
        if (callbacks) {
            callbacks.forEach(cb => {
                try { cb(data); } catch (e) { console.error(e); }
            });
        }
    }
}

/**
 * initSocket — Creates and returns a connected SocketWrapper instance.
 *
 * This is the main function that the rest of the app uses to establish a
 * WebSocket connection. It returns a Promise that resolves with the connected
 * socket or rejects if the connection fails within 10 seconds.
 *
 * HOW THE URL IS CONSTRUCTED:
 * ---------------------------
 * The backend URL is read from the REACT_APP_BACKEND_URL environment variable
 * (set in .env for local dev, or in Vercel's settings for production).
 *
 * Example:
 *   REACT_APP_BACKEND_URL = "https://my-backend.onrender.com"
 *   → Replace "https" with "wss"
 *   → Append "/ws"
 *   → Result: "wss://my-backend.onrender.com/ws"
 *
 * For local development:
 *   REACT_APP_BACKEND_URL = "http://localhost:8080"
 *   → "ws://localhost:8080/ws"
 *
 * @returns {Promise<SocketWrapper>} Resolves with a connected SocketWrapper instance.
 *          Rejects with an Error if connection fails or times out after 10 seconds.
 *
 * @example
 *   try {
 *     const socket = await initSocket();
 *     socket.emit('join', { roomId: 'abc', userName: 'Alice' });
 *   } catch (err) {
 *     console.error('Failed to connect:', err);
 *   }
 */
export const initSocket = async () => {
    // Read the backend URL, strip trailing slashes to prevent double-slash URLs
    const backendUrl = (process.env.REACT_APP_BACKEND_URL || 'http://localhost:8080').replace(/\/+$/, '');

    // Convert HTTP(S) protocol to WS(S) protocol and append the WebSocket endpoint path
    const wsUrl = backendUrl.replace(/^http/, 'ws') + '/ws';

    return new Promise((resolve, reject) => {
        const socket = new SocketWrapper(wsUrl);

        // Set a 10-second timeout — if we can't connect in time, give up
        const timeout = setTimeout(() => {
            socket.off('connect', onConnect);
            socket.off('connect_error', onError);
            socket.disconnect();
            reject(new Error('WebSocket connection timeout'));
        }, 10000);

        // Success handler — connection established
        const onConnect = () => {
            clearTimeout(timeout);
            socket.off('connect_error', onError);
            resolve(socket);
        };

        // Failure handler — connection error
        const onError = (err) => {
            clearTimeout(timeout);
            socket.off('connect', onConnect);
            reject(err);
        };

        socket.on('connect', onConnect);
        socket.on('connect_error', onError);
    });
};
