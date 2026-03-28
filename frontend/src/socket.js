class SocketWrapper {
    constructor(url) {
        this.url = url;
        this.listeners = new Map();
        this.queue = [];
        this.connected = false;
        this.shouldReconnect = true;
        this.connectGeneration = 0;
        this._connect();
    }

    _connect() {
        const generation = ++this.connectGeneration;
        this.ws = new WebSocket(this.url);

        this.ws.onopen = () => {
            if (generation !== this.connectGeneration) return;
            this.connected = true;
            this.queue.forEach(msg => this.ws.send(msg));
            this.queue = [];
            this._fire('connect');
        };

        this.ws.onmessage = (event) => {
            if (generation !== this.connectGeneration) return;
            try {
                const msg = JSON.parse(event.data);
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
            if (this.shouldReconnect) {
                setTimeout(() => {
                    if (this.shouldReconnect && generation === this.connectGeneration) {
                        this._connect();
                    }
                }, 2000);
            }
        };
    }

    emit(type, data) {
        const msg = JSON.stringify({ type, data });
        if (this.connected && this.ws && this.ws.readyState === WebSocket.OPEN) {
            this.ws.send(msg);
        } else {
            this.queue.push(msg);
        }
    }

    on(type, callback) {
        if (!this.listeners.has(type)) {
            this.listeners.set(type, new Set());
        }
        this.listeners.get(type).add(callback);
    }

    off(type, callback) {
        if (!this.listeners.has(type)) return;
        if (callback) {
            this.listeners.get(type).delete(callback);
        } else {
            this.listeners.delete(type);
        }
    }

    disconnect() {
        this.shouldReconnect = false;
        this.queue = [];
        this.connectGeneration++;
        if (this.ws) {
            this.ws.close();
            this.ws = null;
        }
    }

    _fire(type, data) {
        const callbacks = this.listeners.get(type);
        if (callbacks) {
            callbacks.forEach(cb => {
                try { cb(data); } catch (e) { console.error(e); }
            });
        }
    }
}

export const initSocket = async () => {
    const backendUrl = process.env.REACT_APP_BACKEND_URL || 'http://localhost:8080';
    const wsUrl = backendUrl.replace(/^http/, 'ws') + '/ws';

    return new Promise((resolve, reject) => {
        const socket = new SocketWrapper(wsUrl);

        const timeout = setTimeout(() => {
            socket.off('connect', onConnect);
            socket.off('connect_error', onError);
            socket.disconnect();
            reject(new Error('WebSocket connection timeout'));
        }, 10000);

        const onConnect = () => {
            clearTimeout(timeout);
            socket.off('connect_error', onError);
            resolve(socket);
        };

        const onError = (err) => {
            clearTimeout(timeout);
            socket.off('connect', onConnect);
            reject(err);
        };

        socket.on('connect', onConnect);
        socket.on('connect_error', onError);
    });
};
