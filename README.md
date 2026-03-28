# CodeCollab — Real-Time Collaborative Code Editor

A web application where multiple users can **write code together in real-time**, chat with each other, and compile/run code — all in the browser.

Built with a **React** frontend and a **Java Spring Boot** backend, connected via **WebSockets** for instant communication.

---

## Table of Contents

1. [How It Works (High-Level)](#how-it-works-high-level)
2. [Project Structure](#project-structure)
3. [Technology Stack](#technology-stack)
4. [Frontend Documentation](#frontend-documentation)
   - [File-by-File Breakdown](#frontend-file-by-file-breakdown)
   - [All Functions & Methods](#frontend-functions--methods)
5. [Backend Documentation](#backend-documentation)
   - [File-by-File Breakdown](#backend-file-by-file-breakdown)
   - [All Functions & Methods](#backend-functions--methods)
6. [Configuration Files](#configuration-files)
7. [How Data Flows](#how-data-flows)
8. [Local Setup](#local-setup)
9. [Deployment](#deployment)

---

## How It Works (High-Level)

```
┌─────────────────────────────────────────────────────────────────┐
│                        USER'S BROWSER                           │
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐   │
│  │  React Frontend (runs in browser)                        │   │
│  │  ┌─────────┐  ┌──────────────┐  ┌──────────────────┐    │   │
│  │  │ Home    │  │ Editor Page  │  │ CodeMirror Editor │    │   │
│  │  │ Page    │→ │ (Sidebar +   │  │ (Syntax-highlight │    │   │
│  │  │ (Join)  │  │  Chat + Code)│  │  code editing)    │    │   │
│  │  └─────────┘  └──────────────┘  └──────────────────────┘ │  │
│  └──────────────────────────────────────────────────────────┘   │
│           │ WebSocket (real-time)         │ HTTP POST           │
│           │ (code sync, chat, join/leave) │ (compile code)      │
└───────────┼──────────────────────────────┼──────────────────────┘
            │                              │
            ▼                              ▼
┌─────────────────────────────────────────────────────────────────┐
│                   JAVA SPRING BOOT BACKEND                       │
│                                                                  │
│  ┌─────────────────────┐  ┌──────────────────────────────────┐  │
│  │ WebSocket Handler   │  │ REST API Controller              │  │
│  │ (CollabWebSocket    │  │ (CompilerController)             │  │
│  │  Handler)           │  │  POST /api/compile → Wandbox API │  │
│  │                     │  │  GET  /api/health  → { "ok" }    │  │
│  │ Handles:            │  └──────────────────────────────────┘  │
│  │  • join / leave     │                                        │
│  │  • code-change      │  ┌──────────────────────────────────┐  │
│  │  • chat-message     │  │ RoomService                      │  │
│  │  • sync-code        │  │ (In-memory storage of rooms,     │  │
│  └─────────────────────┘  │  users, and code)                │  │
│                           └──────────────────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
```

**In simple terms:**
1. User opens the website → sees the Home page → enters a Room ID and Username → clicks "Join Room".
2. Frontend establishes a WebSocket connection to the backend.
3. User types code → change is sent via WebSocket to the backend → backend broadcasts to all other users in the same room → their editors update instantly.
4. User sends a chat message → same flow via WebSocket.
5. User clicks "Run" → code is sent via HTTP POST to the backend → backend forwards to Wandbox API → output is returned and displayed.

---

## Project Structure

```
CodeCollab/
│
├── frontend/                          # React application (runs in browser)
│   ├── public/                        # Static assets served as-is
│   │   ├── index.html                 # The single HTML page (React renders into <div id="root">)
│   │   ├── Code Collab copy.png       # App logo image
│   │   ├── Icon.png                   # App icon
│   │   ├── favicon.ico                # Browser tab icon
│   │   ├── play.png                   # Play button icon
│   │   ├── manifest.json              # PWA manifest (app name, icons, theme)
│   │   └── robots.txt                 # Search engine crawler instructions
│   │
│   ├── src/                           # React source code
│   │   ├── index.js                   # ★ Entry point — mounts <App> into the DOM
│   │   ├── index.css                  # ★ Global CSS variables, resets, scrollbar styles
│   │   ├── App.js                     # ★ Root component — sets up routing and toasts
│   │   ├── App.css                    # ★ All component styles (home, editor, sidebar, chat)
│   │   ├── Actions.js                 # ★ WebSocket event type constants
│   │   ├── socket.js                  # ★ WebSocket wrapper (SocketWrapper class + initSocket)
│   │   │
│   │   ├── pages/                     # Full-page components (one per route)
│   │   │   ├── Home.js                # ★ Landing page — create/join room form
│   │   │   └── EditorPage.js          # ★ Main editor page — sidebar + chat + editor
│   │   │
│   │   ├── components/                # Reusable UI components
│   │   │   ├── Client.js              # ★ Single user avatar + name chip
│   │   │   └── Editor.js              # ★ CodeMirror editor + language selector + compiler
│   │   │
│   │   ├── App.test.js                # Unit test for App component
│   │   ├── setupTests.js              # Test configuration
│   │   ├── reportWebVitals.js         # Performance metrics reporting
│   │   └── logo.svg                   # Default React logo (unused)
│   │
│   ├── .env                           # Environment variables (REACT_APP_BACKEND_URL)
│   ├── package.json                   # NPM dependencies and scripts
│   ├── package-lock.json              # Locked dependency versions
│   └── vercel.json                    # Vercel deployment configuration
│
├── backend/                           # Java Spring Boot application
│   ├── src/main/java/com/codecollab/
│   │   ├── CodeCollabApplication.java # ★ Main entry point — starts the Spring Boot server
│   │   │
│   │   ├── config/                    # Configuration classes
│   │   │   ├── CorsConfig.java        # ★ CORS rules for REST API (/api/**)
│   │   │   └── WebSocketConfig.java   # ★ WebSocket endpoint registration (/ws)
│   │   │
│   │   ├── controller/                # REST API endpoints
│   │   │   └── CompilerController.java # ★ POST /api/compile, GET /api/health
│   │   │
│   │   ├── handler/                   # WebSocket message handlers
│   │   │   └── CollabWebSocketHandler.java # ★ Core handler for all WebSocket events
│   │   │
│   │   ├── model/                     # Data models
│   │   │   └── UserSession.java       # ★ POJO: sessionId + userName + roomId
│   │   │
│   │   └── service/                   # Business logic
│   │       ├── RoomService.java       # ★ In-memory room/user/code management
│   │       └── CompilerService.java   # ★ Sends code to Wandbox API for compilation
│   │
│   ├── src/main/resources/
│   │   └── application.properties     # Server port and CORS settings
│   │
│   ├── pom.xml                        # Maven dependencies and build config
│   ├── Dockerfile                     # Docker container build instructions
│   └── system.properties              # Java version for deployment platforms
│
├── .gitignore                         # Files/folders Git should ignore
└── README.md                          # This documentation file
```

Files marked with ★ contain documented code with detailed comments explaining every function and method.

---

## Technology Stack

| Layer | Technology | Purpose |
|-------|-----------|---------|
| **Frontend Framework** | React 18 | Building the user interface with reusable components |
| **Code Editor** | CodeMirror 5 | In-browser code editor with syntax highlighting, line numbers, auto-close brackets |
| **Routing** | React Router v6 | Client-side URL routing (Home page ↔ Editor page) |
| **Notifications** | react-hot-toast | Toast popup notifications (user joined, room copied, etc.) |
| **Avatars** | react-avatar | Auto-generated colored circle avatars from usernames |
| **UUID** | uuid (v4) | Generating unique Room IDs |
| **Backend Framework** | Spring Boot 3.2 | Java web framework — handles HTTP requests and WebSocket connections |
| **WebSocket** | Spring WebSocket | Real-time bidirectional communication between browser and server |
| **HTTP Client** | RestTemplate | Sending HTTP requests from the backend to the Wandbox API |
| **JSON Processing** | Jackson (ObjectMapper) | Parsing and building JSON messages |
| **Code Compilation** | Wandbox API | Free external service that compiles and runs code (Python, Java, C++, C) |
| **Build Tool** | Maven | Managing Java dependencies and building the backend JAR file |
| **Containerization** | Docker | Packaging the backend for deployment on Render |
| **Frontend Hosting** | Vercel | Free hosting for the React frontend |
| **Backend Hosting** | Render | Free hosting for the Dockerized Spring Boot backend |

---

## Frontend Documentation

### Frontend File-by-File Breakdown

#### `src/index.js` — Application Entry Point
The very first file that runs. It finds the `<div id="root">` element in `public/index.html` and renders the `<App>` component inside it. Wraps everything in `<React.StrictMode>` which helps catch bugs during development.

#### `src/App.js` — Root Component
Sets up two things:
1. **Toast notifications** — Configures the `react-hot-toast` library with dark theme colors.
2. **Routing** — Maps URL paths to page components:
   - `/` → `<Home>` (landing page)
   - `/editor/:roomId` → `<EditorPage>` (collaborative editor)

#### `src/Actions.js` — Event Type Constants
Defines string constants for all WebSocket event types (`JOIN`, `JOINED`, `DISCONNECTED`, `CODE_CHANGE`, `SYNC_CODE`, `LEAVE`). Using constants instead of raw strings prevents hard-to-find typos.

#### `src/socket.js` — WebSocket Connection Manager
Contains two things:
1. **`SocketWrapper` class** — Wraps the browser's native `WebSocket` with a user-friendly API (`emit`, `on`, `off`, `disconnect`). Includes message queuing, auto-reconnection, and generation tracking.
2. **`initSocket()` function** — Creates a connected `SocketWrapper` and returns it as a Promise. Handles URL construction (HTTP → WS protocol conversion) and connection timeout.

#### `src/pages/Home.js` — Landing Page
A form with Room ID input, Username input, and a "Join Room" button. Users can paste an existing Room ID or generate a new one (UUID v4). On submit, navigates to `/editor/:roomId` with the username in navigation state.

#### `src/pages/EditorPage.js` — Main Editor Page
The most complex component. Manages:
- WebSocket connection lifecycle (connect, join room, disconnect on leave)
- Connected users list
- Real-time chat messaging
- Sidebar visibility (collapsible on mobile)
- Code state synchronization with new joiners

#### `src/components/Editor.js` — Code Editor Component
Wraps the CodeMirror 5 library. Handles:
- Editor initialization and cleanup
- Real-time code synchronization via WebSocket
- Language mode switching (Python/Java/C++/C)
- Code compilation via the backend REST API
- Input/Output panels for program I/O

#### `src/components/Client.js` — User Avatar Component
A small component that renders a user's colored avatar circle and display name. Used in the sidebar's "Connected" section.

#### `src/index.css` — Global Styles
CSS variables (Dracula theme colors), CSS reset, body styles, and custom scrollbar styling.

#### `src/App.css` — Component Styles
All CSS for the application's components: home page form, buttons, editor layout (CSS Grid), sidebar (Flexbox), chat messages, code runner, and responsive breakpoints (900px, 768px, 480px).

---

### Frontend Functions & Methods

#### `SocketWrapper` Class (`socket.js`)

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `constructor(url)` | `url: string` — WebSocket URL | — | Creates instance, initializes state (listeners Map, message queue, connected flag), begins connecting. |
| `_connect()` | — | — | Creates a new `WebSocket`, sets up `onopen`/`onmessage`/`onerror`/`onclose` handlers. Increments generation counter to prevent stale callbacks. On open: flushes queued messages. On close: schedules reconnection after 2 seconds. |
| `emit(type, data)` | `type: string` — event name, `data: object` — payload | — | Sends `{ type, data }` as JSON. If not connected, queues the message for later. |
| `on(type, callback)` | `type: string` — event name, `callback: function` — handler | — | Registers an event listener. Multiple listeners per event type are supported. |
| `off(type, callback?)` | `type: string`, `callback?: function` | — | Removes a specific listener, or all listeners for that type if no callback given. |
| `disconnect()` | — | — | Permanently closes the WebSocket. Stops auto-reconnection, clears queue, increments generation. |
| `_fire(type, data)` | `type: string`, `data: any` | — | Internal method. Calls all registered callbacks for the given event type. Each callback is wrapped in try/catch. |

#### `initSocket()` Function (`socket.js`)

| Parameter | Type | Description |
|-----------|------|-------------|
| — | — | No parameters. Reads `REACT_APP_BACKEND_URL` from environment. |
| **Returns** | `Promise<SocketWrapper>` | Resolves with a connected socket, or rejects after 10s timeout. |

**What it does:**
1. Reads `REACT_APP_BACKEND_URL` environment variable (defaults to `http://localhost:8080`).
2. Strips trailing slashes to prevent double-slash URLs.
3. Converts protocol: `http` → `ws`, `https` → `wss`.
4. Appends `/ws` path.
5. Creates a `SocketWrapper` with the constructed URL.
6. Returns a Promise that resolves on successful connection or rejects on error/timeout.

---

#### `Home` Component (`pages/Home.js`)

| Function | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `createNewRoom(e)` | `e: Event` — click event | — | Generates a UUID v4 string and sets it as the Room ID. Shows a success toast. |
| `joinRoom()` | — | — | Validates that roomId and userName are filled. If valid, navigates to `/editor/{roomId}` with userName in route state. If invalid, shows an error toast. |
| `handleInputEnter(e)` | `e: KeyboardEvent` | — | If the pressed key is Enter, calls `joinRoom()`. Enables keyboard shortcut for joining. |

---

#### `EditorPage` Component (`pages/EditorPage.js`)

| Function / Effect | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `useEffect (socket init)` | Dependencies: `[roomId, userName, reactNavigator]` | Cleanup function | The main setup effect. Calls `initSocket()`, emits "join", sets up listeners for "joined", "disconnected", "chat-message", and "connect_error". On cleanup: disconnects socket. |
| `useEffect (auto-scroll)` | Dependencies: `[messages]` | — | Scrolls the chat to the bottom whenever a new message is added. |
| `sendMessage()` | — | — | Trims message input, adds message to local state (optimistic UI), emits "chat-message" via WebSocket, clears input. |
| `handleCodeChange(code)` | `code: string` | — | Stores the latest code in `codeRef`. Memoized with `useCallback` to prevent unnecessary Editor re-renders. |
| `copyRoomID()` | — | `Promise<void>` | Copies the current roomId to the clipboard using `navigator.clipboard.writeText()`. Shows success/error toast. |
| `leaveRoom()` | — | — | Navigates to `"/"` which triggers the useEffect cleanup (socket disconnect). |
| `formatTime(ts)` | `ts: number` — Unix timestamp (ms) | `string` | Converts a timestamp to "HH:MM AM/PM" format for chat message display. |

---

#### `Editor` Component (`components/Editor.js`)

| Function / Effect | Parameters | Returns | Description |
|----------|-----------|---------|-------------|
| `useEffect (socketRef sync)` | Dependencies: `[socket]` | — | Keeps `socketRef.current` in sync with the latest `socket` prop. Needed because the CodeMirror change handler closure captures a stale reference otherwise. |
| `useEffect (CodeMirror init)` | Dependencies: `[]` (runs once) | Cleanup function | Creates the CodeMirror editor from a textarea. Registers the 'change' event handler that emits code changes via WebSocket (but only for user-typed changes, not remote `setValue` calls). Cleanup: restores the textarea. |
| `useEffect (code-change listener)` | Dependencies: `[socket]` | Cleanup function | Listens for incoming "code-change" events from the WebSocket. When received, calls `editor.setValue(code)` to update the editor content. Cleanup: removes the listener. |
| `useEffect (language change)` | Dependencies: `[selectedLanguage]` | — | Updates CodeMirror's syntax highlighting mode when the user selects a different language. |
| `handleCompile()` | — | `Promise<void>` | Sends the code, input, and language to `POST /api/compile` via `fetch()`. Displays "Running..." while waiting, then shows the output or error. |

---

#### `Client` Component (`components/Client.js`)

| Parameter | Type | Description |
|-----------|------|-------------|
| `userName` | `string` | The display name of the user. Passed as a prop from EditorPage. |

Renders a circular avatar (auto-generated colors from the name) and the username text.

---

## Backend Documentation

### Backend File-by-File Breakdown

#### `CodeCollabApplication.java` — Application Entry Point
The `main()` method that starts the entire Spring Boot server. `@SpringBootApplication` tells Spring to auto-configure everything, scan for components, and start the embedded Tomcat web server.

#### `config/CorsConfig.java` — REST API CORS Configuration
Configures Cross-Origin Resource Sharing for REST endpoints (`/api/**`). Reads allowed origins from the `app.cors.allowed-origins` property. Without this, browsers would block the frontend from calling the backend API.

#### `config/WebSocketConfig.java` — WebSocket Endpoint Registration
Registers the WebSocket endpoint at `/ws` and maps it to `CollabWebSocketHandler`. Also applies CORS rules to WebSocket connections.

#### `model/UserSession.java` — User Data Model
A simple POJO (Plain Old Java Object) that holds three fields: `sessionId`, `userName`, and `roomId`. Represents a single user's connection to a room. Fields are `final` (immutable after creation).

#### `controller/CompilerController.java` — REST API Controller
Exposes two HTTP endpoints:
- `POST /api/compile` — Accepts code, input, and language; delegates to `CompilerService`; returns the output.
- `GET /api/health` — Returns `{"status": "ok"}` for health checks.

#### `service/CompilerService.java` — Code Compilation Service
Sends code to the Wandbox API (https://wandbox.org/api/compile.json) for compilation. Maps language names to Wandbox compiler identifiers. Includes HTTP timeouts and robust response parsing.

#### `service/RoomService.java` — Room & User Management
The "brain" of the collaboration system. Stores all data in memory using thread-safe `ConcurrentHashMap` structures:
- `userSessions`: Maps session IDs to `UserSession` objects.
- `rooms`: Maps room IDs to sets of session IDs.
- `roomCode`: Maps room IDs to the latest code string.

Handles user deduplication, room switching, and empty room cleanup.

#### `handler/CollabWebSocketHandler.java` — Core WebSocket Handler
The most important backend class. Processes all WebSocket messages:
- `"join"` → Adds user to room, broadcasts client list, sends existing code.
- `"code-change"` → Stores code, broadcasts to other room members.
- `"sync-code"` → Sends code to a specific user (point-to-point).
- `"chat-message"` → Validates and broadcasts chat messages.
- Connection close → Removes user, broadcasts "disconnected" event.

---

### Backend Functions & Methods

#### `CodeCollabApplication`

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `main(String[] args)` | `args` — command-line arguments | — | Starts the Spring Boot application. Spring creates the web server, initializes all components, and begins listening for connections. |

---

#### `CorsConfig` (implements `WebMvcConfigurer`)

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `addCorsMappings(CorsRegistry registry)` | `registry` — Spring's CORS rule registry | — | Parses the `app.cors.allowed-origins` property into an array of origins. Registers CORS rules for `/api/**` allowing GET, POST, PUT, DELETE, OPTIONS methods and all headers. |

---

#### `WebSocketConfig` (implements `WebSocketConfigurer`)

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `WebSocketConfig(CollabWebSocketHandler handler)` | `handler` — the WebSocket handler bean | — | Constructor. Spring injects the handler automatically (dependency injection). |
| `registerWebSocketHandlers(WebSocketHandlerRegistry registry)` | `registry` — Spring's handler registry | — | Maps the URL path `/ws` to `CollabWebSocketHandler` and sets allowed CORS origins. |

---

#### `CompilerController`

| Method | Endpoint | Parameters | Returns | Description |
|--------|----------|-----------|---------|-------------|
| `CompilerController(CompilerService service)` | — | `service` — compiler service bean | — | Constructor injection of the CompilerService. |
| `compile(Map<String, String> request)` | `POST /api/compile` | JSON body: `{ "code", "input", "language" }` | `ResponseEntity<Map<String, String>>` with `{ "output": "..." }` | Extracts code, input, language from request. Calls `compilerService.compile()`. Returns 200 with output, or 400 if body is missing. |
| `health()` | `GET /api/health` | — | `ResponseEntity<Map<String, String>>` with `{ "status": "ok" }` | Simple health check returning 200 OK. |

---

#### `CompilerService`

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `CompilerService(RestTemplateBuilder builder)` | `builder` — Spring's HTTP client builder | — | Creates a `RestTemplate` with 10s connect timeout and 30s read timeout. |
| `compile(String code, String input, String language)` | `code` — source code, `input` — stdin, `language` — "python"/"java"/"cpp"/"c" | `String` — program output or error message | Validates language, builds JSON request, sends POST to Wandbox API, parses response. Returns compilation output, errors, or exit code. |
| `getField(JsonNode root, String field)` | `root` — JSON response, `field` — field name | `String` — field value or "" | Safely extracts a string field from a JSON node. Returns empty string if field is missing or null. |

**Compiler Map:**
| Language | Wandbox Compiler |
|----------|-----------------|
| python | cpython-3.12.7 |
| java | openjdk-jdk-22+36 |
| cpp | gcc-13.2.0 |
| c | gcc-13.2.0-c |

---

#### `RoomService`

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `addUser(String sessionId, String userName, String roomId)` | Session ID, display name, room ID | `List<String>` — removed duplicate session IDs | **Synchronized.** Adds a user to a room. Deduplicates: removes existing sessions with the same userName in the same room. Removes user from previous room if switching. Creates room if it doesn't exist. Returns list of removed session IDs for WebSocket cleanup. |
| `removeUser(String sessionId)` | The session ID to remove | `UserSession` or `null` | **Synchronized.** Removes a user from their room. Cleans up empty rooms (removes room entry and stored code). Returns the removed UserSession for broadcasting "disconnected" events. |
| `getRoomUsers(String roomId)` | The room ID to query | `List<UserSession>` | **Synchronized.** Returns all users currently in the specified room. Returns empty list if room doesn't exist. |
| `getUser(String sessionId)` | The session ID to look up | `UserSession` or `null` | Returns the UserSession for a given session ID. Used to determine which room a user belongs to. |
| `updateCode(String roomId, String code)` | Room ID, code string | — | Stores the latest code for a room. Called on every code change. |
| `getCode(String roomId)` | Room ID | `String` or `null` | Retrieves the stored code for a room. Sent to new joiners for synchronization. |

**Data Structures:**
| Map | Key | Value | Purpose |
|-----|-----|-------|---------|
| `userSessions` | Session ID (String) | `UserSession` | Look up user info by session ID |
| `rooms` | Room ID (String) | `Set<String>` (session IDs) | Track which sessions are in which room |
| `roomCode` | Room ID (String) | Code (String) | Store the latest code per room |

---

#### `CollabWebSocketHandler` (extends `TextWebSocketHandler`)

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `afterConnectionEstablished(WebSocketSession session)` | Raw WebSocket session | — | Called when a browser connects. Wraps session for thread safety, generates UUID session ID, stores in lookup maps, sends "session-id" event to client. |
| `handleTextMessage(WebSocketSession session, TextMessage message)` | Session + message | — | Called on every incoming message. Parses JSON, extracts "type" and "data", routes to the correct handler method (join/code-change/sync-code/chat-message). |
| `afterConnectionClosed(WebSocketSession session, CloseStatus status)` | Session + close reason | — | Called when a browser disconnects. Removes user from room via RoomService, broadcasts "disconnected" event to remaining room members. |
| `handleJoin(String sessionId, JsonNode data)` | Session ID, `{ "roomId", "userName" }` | — | Adds user to room, closes duplicate sessions, builds client list, broadcasts "joined" event, sends stored code to the new joiner. |
| `handleCodeChange(String sessionId, JsonNode data)` | Session ID, `{ "code" }` | — | Stores updated code in RoomService, broadcasts "code-change" to all other room members (excludes sender). |
| `handleSyncCode(String sessionId, JsonNode data)` | Session ID, `{ "socketId", "code" }` | — | Sends code directly to one specific user (point-to-point). Used when an existing user shares code with a newly-joined user. |
| `handleChatMessage(String sessionId, JsonNode data)` | Session ID, `{ "message" }` | — | Validates message (non-empty, ≤2000 chars), attaches sender's userName and timestamp, broadcasts to all other room members. |
| `broadcastToRoom(String roomId, String type, JsonNode data, String excludeSessionId)` | Room ID, event type, payload, session to skip | — | Sends a message to every user in a room, optionally skipping one user (usually the sender). |
| `sendEvent(WebSocketSession session, String type, JsonNode data)` | Target session, event type, payload | — | Builds a JSON message `{ "type": "...", "data": {...} }` and sends it over the WebSocket connection. |

**WebSocket Message Protocol:**
| Direction | Type | Data Fields | Description |
|-----------|------|------------|-------------|
| Server → Client | `session-id` | `{ sessionId }` | Assigns the client a unique session ID |
| Client → Server | `join` | `{ roomId, userName }` | Request to join a room |
| Server → Client | `joined` | `{ clients[], userName, socketId }` | Someone joined; includes full client list |
| Server → Client | `disconnected` | `{ socketId, userName }` | Someone left the room |
| Client → Server | `code-change` | `{ roomId, code }` | User typed/modified code |
| Server → Client | `code-change` | `{ code }` | Code update from another user |
| Client → Server | `sync-code` | `{ socketId, code }` | Send code to a specific user |
| Client → Server | `chat-message` | `{ message }` | Send a chat message |
| Server → Client | `chat-message` | `{ message, userName, timestamp }` | Chat message from another user |

---

#### `UserSession`

| Method | Parameters | Returns | Description |
|--------|-----------|---------|-------------|
| `UserSession(String sessionId, String userName, String roomId)` | Session ID, display name, room ID | — | Constructor. Creates an immutable user session object. |
| `getSessionId()` | — | `String` | Returns the unique WebSocket session identifier. |
| `getUserName()` | — | `String` | Returns the user's display name. |
| `getRoomId()` | — | `String` | Returns the room ID this user belongs to. |

---

## Configuration Files

### `backend/src/main/resources/application.properties`
```properties
server.port=${PORT:8080}                                    # Server port (uses PORT env var, defaults to 8080)
app.cors.allowed-origins=${CORS_ALLOWED_ORIGINS:http://localhost:3000}  # Allowed frontend origins
```

### `backend/pom.xml` — Maven Dependencies
| Dependency | Purpose |
|-----------|---------|
| `spring-boot-starter-web` | Web server (embedded Tomcat), REST API support |
| `spring-boot-starter-websocket` | WebSocket support |
| `spring-boot-starter-test` | Testing framework (JUnit) |

Build produces `app.jar` (set via `<finalName>app</finalName>`).

### `backend/Dockerfile`
Two-stage Docker build:
1. **Build stage**: Uses Maven to compile Java code and produce `app.jar`.
2. **Runtime stage**: Uses a lightweight JRE image to run `app.jar`.

### `frontend/.env`
```
REACT_APP_BACKEND_URL=http://localhost:8080
```
This is the default for local development. In production (Vercel), this is overridden via Vercel's environment variable settings.

### `frontend/vercel.json`
```json
{
  "buildCommand": "npm run build",
  "outputDirectory": "build",
  "rewrites": [{ "source": "/(.*)", "destination": "/index.html" }]
}
```
The `rewrites` rule ensures that all URLs (like `/editor/abc-123`) serve `index.html`, letting React Router handle the routing client-side.

### `frontend/package.json` — NPM Dependencies
| Dependency | Version | Purpose |
|-----------|---------|---------|
| `react` | ^18.3.1 | UI framework |
| `react-dom` | ^18.3.1 | React DOM rendering |
| `react-router-dom` | ^6.23.1 | Client-side URL routing |
| `react-hot-toast` | ^2.4.1 | Toast notifications |
| `react-avatar` | ^5.0.3 | Auto-generated user avatars |
| `codemirror` | ^5.65.16 | Code editor with syntax highlighting |
| `uuid` | ^10.0.0 | UUID generation for Room IDs |

---

## How Data Flows

### 1. User Joins a Room
```
Browser                          Backend
  │                                │
  │─── WebSocket connect ────────>│  afterConnectionEstablished()
  │<── { type: "session-id" } ────│  → assigns UUID, stores session
  │                                │
  │─── { type: "join",           │  handleJoin()
  │      data: { roomId,         │  → addUser() deduplicates
  │              userName } } ───>│  → builds client list
  │                                │  → broadcasts "joined" to all
  │<── { type: "joined",         │
  │      data: { clients,        │
  │              userName,        │
  │              socketId } } ────│
  │                                │
  │<── { type: "code-change",    │  → sends stored code to joiner
  │      data: { code } } ───────│
```

### 2. User Types Code
```
User A's Browser                 Backend                    User B's Browser
  │                                │                          │
  │─── { type: "code-change",    │                          │
  │      data: { code } } ──────>│  handleCodeChange()      │
  │                                │  → roomService.updateCode()
  │                                │  → broadcastToRoom()    │
  │                                │  (excludes sender)      │
  │                                │──── { type: "code-change",
  │                                │       data: { code } } ─>│
  │                                │                          │
  │                                │             Editor.js setValue(code)
```

### 3. User Sends Chat Message
```
Sender's Browser                 Backend                    Other Browsers
  │                                │                          │
  │ (adds to local messages)      │                          │
  │─── { type: "chat-message",   │  handleChatMessage()     │
  │      data: { message } } ───>│  → validates message     │
  │                                │  → adds userName,       │
  │                                │    timestamp            │
  │                                │  → broadcastToRoom()    │
  │                                │  (excludes sender)      │
  │                                │── { type: "chat-message",
  │                                │    data: { message,     │
  │                                │            userName,    │
  │                                │            timestamp } }>│
```

### 4. User Runs Code
```
Browser                          Backend                     Wandbox API
  │                                │                          │
  │─── POST /api/compile ────────>│  CompilerController      │
  │    { code, input, language }  │  .compile()              │
  │                                │─── POST /compile.json ──>│
  │                                │    { code, compiler,     │
  │                                │      stdin }             │
  │                                │<── { program_output,    │
  │                                │      compiler_error,    │
  │                                │      status } ──────────│
  │<── { output: "Hello" } ──────│                          │
```

---

## Local Setup

### Prerequisites
- **Node.js** (v16+) and **npm** — [Download](https://nodejs.org/)
- **Java 17** (JDK) — [Download](https://adoptium.net/)
- **Maven** — [Download](https://maven.apache.org/) (or use the Maven wrapper)

### Start the Backend
```bash
cd backend
mvn spring-boot:run
```
The backend starts on `http://localhost:8080`.

### Start the Frontend
```bash
cd frontend
npm install
npm start
```
The frontend starts on `http://localhost:3000` and automatically connects to the backend at `http://localhost:8080` (configured in `frontend/.env`).

### Test It
1. Open `http://localhost:3000` in your browser.
2. Click "new room" to generate a Room ID.
3. Enter a username and click "Join Room".
4. Open another browser tab, paste the same Room ID, use a different username.
5. Type code in one tab — it appears in the other tab instantly.
6. Try the chat and the "Run" button.

---

## Deployment

### Frontend (Vercel)
1. Push your code to GitHub.
2. Go to [vercel.com](https://vercel.com), import the GitHub repository.
3. Set the **Root Directory** to `frontend`.
4. Add the environment variable:
   - `REACT_APP_BACKEND_URL` = `https://your-backend.onrender.com` (no trailing slash)
5. Deploy.

### Backend (Render)
1. Go to [render.com](https://render.com), create a new **Web Service**.
2. Connect your GitHub repository.
3. Set the **Root Directory** to `backend`.
4. Set **Runtime** to **Docker**.
5. Add environment variables:
   - `PORT` = `8080`
   - `CORS_ALLOWED_ORIGINS` = `https://your-frontend.vercel.app`
6. Deploy.

---

## License

This project is open source and available for educational purposes.
