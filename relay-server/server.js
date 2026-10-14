/**
 * AshuChat Dedicated WebSocket Signaling Relay Server
 *
 * Lightweight, zero-dependency (only 'ws') broker for WebRTC SDP & ICE token exchange.
 *
 * Usage:
 *   npm install
 *   npm start
 *
 * Environment variables:
 *   PORT: Server port (default: 8080)
 */

const http = require('http');
const { WebSocketServer, WebSocket } = require('ws');

const PORT = process.env.PORT || 8080;

// HTTP server for health checks (Render, Railway, Fly.io, uptime monitors)
const server = http.createServer((req, res) => {
    res.setHeader('Access-Control-Allow-Origin', '*');
    res.setHeader('Access-Control-Allow-Methods', 'GET, OPTIONS');
    res.setHeader('Content-Type', 'application/json');

    if (req.url === '/health' || req.url === '/') {
        res.writeHead(200);
        res.end(JSON.stringify({
            status: 'online',
            service: 'AshuChat WebRTC Relay Server',
            uptime: Math.floor(process.uptime()),
            timestamp: Date.now()
        }));
    } else {
        res.writeHead(404);
        res.end(JSON.stringify({ error: 'Not found' }));
    }
});

const wss = new WebSocketServer({ server });

// Map: roomId -> Set of connected WebSocket clients
const rooms = new Map();

wss.on('connection', (ws, req) => {
    let clientRoom = null;
    let clientId = null;

    console.log(`[AshuChat] Client connected from ${req.socket.remoteAddress}`);

    ws.on('message', (data) => {
        try {
            const message = JSON.parse(data.toString());
            const type = message.type;

            if (type === 'join') {
                clientRoom = (message.roomId || 'default').trim();
                clientId = message.clientId || `peer_${Math.random().toString(36).substring(2, 8)}`;
                ws.clientId = clientId;

                if (!rooms.has(clientRoom)) {
                    rooms.set(clientRoom, new Set());
                }
                const roomClients = rooms.get(clientRoom);
                roomClients.add(ws);

                console.log(`[AshuChat] Peer ${clientId} joined room '${clientRoom}' (${roomClients.size} peers in room)`);

                // Notify all existing peers in the room that a new peer joined
                for (const client of roomClients) {
                    if (client !== ws && client.readyState === WebSocket.OPEN) {
                        client.send(JSON.stringify({
                            type: 'peer_joined',
                            roomId: clientRoom,
                            clientId: clientId
                        }));

                        // Also notify the newly joined peer about existing peers
                        if (client.clientId) {
                            ws.send(JSON.stringify({
                                type: 'peer_joined',
                                roomId: clientRoom,
                                clientId: client.clientId
                            }));
                        }
                    }
                }
                return;
            }

            // Forward signaling messages (offer, answer, ice_candidate) to other peers in the room
            if (clientRoom && rooms.has(clientRoom)) {
                const roomClients = rooms.get(clientRoom);
                for (const client of roomClients) {
                    if (client !== ws && client.readyState === WebSocket.OPEN) {
                        client.send(data.toString());
                    }
                }
            }
        } catch (err) {
            console.error('[AshuChat] Error parsing message:', err.message);
        }
    });

    ws.on('close', () => {
        if (clientRoom && rooms.has(clientRoom)) {
            const roomClients = rooms.get(clientRoom);
            roomClients.delete(ws);
            console.log(`[AshuChat] Peer ${clientId} left room '${clientRoom}' (${roomClients.size} peers remaining)`);

            // Notify remaining peers
            if (clientId) {
                for (const client of roomClients) {
                    if (client.readyState === WebSocket.OPEN) {
                        client.send(JSON.stringify({
                            type: 'peer_left',
                            roomId: clientRoom,
                            clientId: clientId
                        }));
                    }
                }
            }

            if (roomClients.size === 0) {
                rooms.delete(clientRoom);
            }
        }
    });

    ws.on('error', (err) => {
        console.error(`[AshuChat] WebSocket error for peer ${clientId}:`, err.message);
    });
});

server.listen(PORT, '0.0.0.0', () => {
    console.log(`===============================================`);
    console.log(` AshuChat Signaling Relay Server Active`);
    console.log(` Port: ${PORT}`);
    console.log(` WebSocket: ws://localhost:${PORT}`);
    console.log(` Health Check: http://localhost:${PORT}/health`);
    console.log(`===============================================`);
});
