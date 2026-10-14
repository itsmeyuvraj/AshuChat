#!/usr/bin/env python3
"""
AshuChat Zero-Dependency Python Signaling Relay Server
Uses ONLY Python standard library (no pip packages needed).

Usage:
    python3 server.py [PORT]
    (Default port: 8080)
"""

import sys
import json
import time
import threading
from http.server import HTTPServer, BaseHTTPRequestHandler

PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 8080

# Room ID -> list of subscriber queues (threading.Event / list)
subscribers = {}
subscribers_lock = threading.Lock()

class RelayHandler(BaseHTTPRequestHandler):
    def log_message(self, format, *args):
        # Concise logging
        pass

    def do_OPTIONS(self):
        self.send_response(200)
        self.send_header('Access-Control-Allow-Origin', '*')
        self.send_header('Access-Control-Allow-Methods', 'GET, POST, OPTIONS')
        self.send_header('Access-Control-Allow-Headers', 'Content-Type, Accept, User-Agent')
        self.end_headers()

    def do_GET(self):
        path = self.path.split('?')[0].strip('/')

        if path == '' or path == 'health':
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            resp = json.dumps({
                "status": "online",
                "service": "AshuChat Python Relay Server",
                "active_rooms": len(subscribers)
            })
            self.wfile.write(resp.encode('utf-8'))
            return

        # Check for poll / health check query (e.g. /topic/json?poll=1)
        if '?poll=1' in self.path or path.endswith('/json'):
            self.send_response(200)
            self.send_header('Content-Type', 'application/json')
            self.send_header('Access-Control-Allow-Origin', '*')
            self.end_headers()
            self.wfile.write(b"[]")
            return

        # SSE Stream Endpoint: /<room_topic>/sse or /<room_topic>
        room_topic = path.replace('/sse', '')

        self.send_response(200)
        self.send_header('Content-Type', 'text/event-stream')
        self.send_header('Cache-Control', 'no-cache')
        self.send_header('Connection', 'keep-alive')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()

        # Initial open event
        init_event = f"event: open\ndata: {{\"event\":\"open\",\"topic\":\"{room_topic}\"}}\n\n"
        self.wfile.write(init_event.encode('utf-8'))
        self.wfile.flush()

        queue = []
        cond = threading.Condition()

        with subscribers_lock:
            if room_topic not in subscribers:
                subscribers[room_topic] = []
            subscribers[room_topic].append((queue, cond))

        print(f"[AshuChat] Client subscribed to room: {room_topic}")

        try:
            while True:
                with cond:
                    while not queue:
                        cond.wait(timeout=15)
                        if not queue:
                            # Send SSE heartbeat keep-alive
                            self.wfile.write(b": keep-alive\n\n")
                            self.wfile.flush()
                    items = list(queue)
                    queue.clear()

                for msg in items:
                    sse_payload = json.dumps({
                        "id": f"m_{int(time.time()*1000)}",
                        "time": int(time.time()),
                        "event": "message",
                        "topic": room_topic,
                        "message": msg
                    })
                    sse_data = f"event: message\ndata: {sse_payload}\n\n"
                    self.wfile.write(sse_data.encode('utf-8'))
                    self.wfile.flush()
        except (BrokenPipeError, ConnectionResetError):
            pass
        finally:
            with subscribers_lock:
                if room_topic in subscribers:
                    subscribers[room_topic] = [s for s in subscribers[room_topic] if s[0] is not queue]
                    if not subscribers[room_topic]:
                        del subscribers[room_topic]
            print(f"[AshuChat] Client disconnected from room: {room_topic}")

    def do_POST(self):
        path = self.path.split('?')[0].strip('/')
        room_topic = path

        content_len = int(self.headers.get('Content-Length', 0))
        body = self.rfile.read(content_len).decode('utf-8')

        with subscribers_lock:
            subs = list(subscribers.get(room_topic, []))

        for queue, cond in subs:
            with cond:
                queue.append(body)
                cond.notify()

        self.send_response(200)
        self.send_header('Content-Type', 'application/json')
        self.send_header('Access-Control-Allow-Origin', '*')
        self.end_headers()
        self.wfile.write(json.dumps({"success": True, "delivered_to": len(subs)}).encode('utf-8'))

class ThreadedHTTPServer(HTTPServer):
    def process_request(self, request, client_address):
        threading.Thread(target=self.process_request_thread, args=(request, client_address), daemon=True).start()

    def process_request_thread(self, request, client_address):
        try:
            self.finish_request(request, client_address)
        except Exception:
            self.handle_error(request, client_address)
        finally:
            self.shutdown_request(request)

if __name__ == '__main__':
    httpd = ThreadedHTTPServer(('0.0.0.0', PORT), RelayHandler)
    print("==================================================")
    print(f" AshuChat Zero-Dependency Relay Server Running")
    print(f" Port: {PORT}")
    print(f" Local URL: http://localhost:{PORT}")
    print(f" Android Emulator URL: http://10.0.2.2:{PORT}")
    print("==================================================")
    try:
        httpd.serve_forever()
    except KeyboardInterrupt:
        print("\nStopping relay server...")
        httpd.server_close()
