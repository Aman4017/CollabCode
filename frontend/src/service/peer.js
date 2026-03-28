class PeerService {
    constructor() {
        this.peer = this._createPeer();
    }

    _createPeer() {
        return new RTCPeerConnection({
            iceServers: [
                {
                    urls: [
                        "stun:stun.l.google.com:19302",
                        "stun:global.stun.twilio.com:3478",
                    ],
                },
            ],
        });
    }

    reset() {
        if (this.peer) {
            this.peer.close();
        }
        this.peer = this._createPeer();
    }

    async getAnswer(offer) {
        await this.peer.setRemoteDescription(offer);
        const ans = await this.peer.createAnswer();
        await this.peer.setLocalDescription(new RTCSessionDescription(ans));
        return ans;
    }

    async setRemoteDescription(ans) {
        await this.peer.setRemoteDescription(new RTCSessionDescription(ans));
    }

    async getOffer() {
        const offer = await this.peer.createOffer();
        await this.peer.setLocalDescription(new RTCSessionDescription(offer));
        return offer;
    }
}

export default new PeerService();
