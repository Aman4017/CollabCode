/**
 * Editor.js — The collaborative code editor component with compilation support.
 *
 * WHAT THIS FILE DOES:
 * --------------------
 * This is the heart of the application — the code editor where users write code
 * collaboratively in real-time. It contains:
 *   1. A CodeMirror editor instance (syntax highlighting, line numbers, themes).
 *   2. A language selector dropdown (Python, Java, C++, C).
 *   3. A "Run" button that compiles/runs the code via the backend API.
 *   4. Input/Output textareas for providing stdin and viewing program output.
 *
 * KEY CONCEPTS:
 * -------------
 * - CodeMirror 5: A powerful in-browser code editor library that provides syntax
 *   highlighting, line numbers, auto-closing brackets, and many other features.
 *   It's created from a <textarea> element and replaces it with a rich editor.
 *
 * - Real-time sync: When one user types, the code change is sent via WebSocket
 *   to all other users in the same room, and their editors are updated instantly.
 *
 * - useRef: React's way of holding a reference to a value that persists across
 *   re-renders but doesn't trigger a re-render when changed. Used here to hold
 *   the CodeMirror instance and the socket reference.
 *
 * PROPS (data received from parent EditorPage):
 * -----------------------------------------------
 * - socket:       The WebSocket connection (SocketWrapper instance) or null.
 * - roomId:       The current room's ID (used when emitting code changes).
 * - onCodeChange: Callback function to notify the parent of code changes.
 *
 * DATA FLOW FOR CODE CHANGES:
 * ---------------------------
 *   User A types        → CodeMirror fires 'change' event
 *     → Editor.js calls socket.emit('code-change', { code })
 *     → Backend receives and broadcasts to all OTHER users
 *     → User B's socket receives 'code-change' event
 *     → Editor.js calls editorRef.current.setValue(code)
 *     → CodeMirror updates the editor content (with origin 'setValue')
 *     → The 'change' handler sees origin is 'setValue' and does NOT re-emit
 *       (this prevents an infinite loop)
 */
import React, { useEffect, useRef, useState } from 'react';
import Codemirror from 'codemirror';
import 'codemirror/lib/codemirror.css';
import 'codemirror/theme/dracula.css';
import 'codemirror/addon/edit/closetag';
import 'codemirror/addon/edit/closebrackets';
import 'codemirror/mode/javascript/javascript';
import 'codemirror/mode/python/python';
import 'codemirror/mode/clike/clike';
import ACTIONS from '../Actions';

/**
 * Maps our language identifiers to CodeMirror's MIME type modes.
 * CodeMirror uses MIME types to determine which syntax highlighting rules to apply.
 *
 * - 'python':         Python syntax highlighting
 * - 'text/x-java':    Java syntax highlighting (part of the 'clike' mode)
 * - 'text/x-c++src':  C++ syntax highlighting (part of the 'clike' mode)
 * - 'text/x-csrc':    C syntax highlighting (part of the 'clike' mode)
 */
const languageModes = {
    python: 'python',
    java: 'text/x-java',
    cpp: 'text/x-c++src',
    c: 'text/x-csrc'
};

/**
 * Editor component — Renders the code editor, toolbar, and I/O panels.
 *
 * @param {Object} props               - React props from the parent (EditorPage).
 * @param {SocketWrapper|null} props.socket       - The WebSocket connection instance.
 * @param {string} props.roomId        - The current room's unique identifier.
 * @param {Function} props.onCodeChange - Callback invoked with the new code string
 *                                        whenever the editor content changes.
 */
const Editor = ({ socket, roomId, onCodeChange }) => {
    /**
     * useRef — Creates a mutable reference that persists across re-renders.
     *
     * editorRef:   Holds the CodeMirror editor instance after initialization.
     *              Used to read/write editor content and change settings.
     * textareaRef: References the <textarea> DOM element that CodeMirror replaces.
     * socketRef:   Holds a reference to the latest socket prop value. This is needed
     *              because the CodeMirror 'change' handler is created once (in the
     *              initial useEffect) and captures the socket value at that time.
     *              Without this ref, the handler would use a stale (possibly null) socket.
     */
    const editorRef = useRef(null);
    const textareaRef = useRef(null);
    const socketRef = useRef(socket);

    /**
     * useState — State variables that trigger a re-render when they change.
     *
     * selectedLanguage: The currently selected programming language (default: python).
     * input:            The stdin text entered in the Input textarea.
     * output:           The program output displayed in the Output textarea.
     * compiling:        Whether code is currently being compiled (disables the Run button).
     */
    const [selectedLanguage, setSelectedLanguage] = useState('python');
    const [input, setInput] = useState('');
    const [output, setOutput] = useState('');
    const [compiling, setCompiling] = useState(false);

    /**
     * Effect: Keep the socketRef in sync with the latest socket prop.
     *
     * Why: The CodeMirror 'change' handler (created in the next useEffect) captures
     * variables in a "closure" — it remembers the values from when it was created.
     * If the socket prop changes later (e.g., from null to connected), the closure
     * would still have the old null value. By using a ref and updating it here,
     * the 'change' handler can always access the latest socket through socketRef.current.
     */
    useEffect(() => { socketRef.current = socket; }, [socket]);

    /**
     * Effect: Initialize the CodeMirror editor (runs once on component mount).
     *
     * This creates the CodeMirror editor from the textarea element. The empty
     * dependency array [] means this runs exactly once when the component first
     * renders.
     *
     * CodeMirror.fromTextArea():
     * - Takes a regular <textarea> element
     * - Hides it and creates a rich code editor in its place
     * - Returns an editor instance with methods like getValue(), setValue(), etc.
     *
     * The 'change' event handler:
     * - Fires every time the editor content changes (user types or setValue is called)
     * - If the change was from user typing (origin !== 'setValue'):
     *   → Emits the code change via WebSocket to sync with other users
     * - If the change was from setValue (receiving remote changes):
     *   → Does NOT re-emit (prevents infinite loop)
     *
     * Cleanup: When the component unmounts (user leaves the page), toTextArea()
     * restores the original <textarea> and removes the CodeMirror editor.
     */
    useEffect(() => {
        if (editorRef.current) return; // Already initialized

        editorRef.current = Codemirror.fromTextArea(textareaRef.current, {
            mode: languageModes[selectedLanguage],
            theme: 'dracula',
            autoCloseTags: true,
            autoCloseBrackets: true,
            lineNumbers: true,
        });

        editorRef.current.on('change', (instance, changes) => {
            const { origin } = changes;
            const code = instance.getValue();
            onCodeChange(code); // Notify parent (EditorPage) of the new code

            // Only emit to WebSocket if the user typed it (not received from remote)
            if (origin !== 'setValue' && socketRef.current) {
                socketRef.current.emit(ACTIONS.CODE_CHANGE, {
                    roomId,
                    code,
                });
            }
        });

        return () => {
            if (editorRef.current) {
                editorRef.current.toTextArea();
                editorRef.current = null;
            }
        };
        // eslint-disable-next-line react-hooks/exhaustive-deps
    }, []);

    /**
     * Effect: Listen for incoming code changes from other users.
     *
     * When another user types in their editor, the backend broadcasts the change
     * to all other users. This effect sets up a listener for those broadcasts.
     *
     * When a 'code-change' event arrives:
     * - Extract the code string from the event data.
     * - Call editorRef.current.setValue(code) to update the editor.
     * - setValue() triggers the 'change' event with origin 'setValue', which our
     *   handler above recognizes and does NOT re-emit (preventing an infinite loop).
     *
     * The [socket] dependency means this effect re-runs whenever the socket
     * changes (e.g., from null to connected). The cleanup function removes the
     * old listener to prevent duplicate handlers.
     */
    useEffect(() => {
        if (!socket) return;

        const handler = ({ code }) => {
            if (code !== null && editorRef.current) {
                editorRef.current.setValue(code);
            }
        };

        socket.on(ACTIONS.CODE_CHANGE, handler);

        return () => {
            socket.off(ACTIONS.CODE_CHANGE, handler);
        };
    }, [socket]);

    /**
     * Effect: Update CodeMirror's syntax highlighting when the language changes.
     *
     * CodeMirror's "mode" option determines which syntax rules to apply.
     * When the user selects a different language from the dropdown, we update
     * the editor's mode so the highlighting matches the new language.
     */
    useEffect(() => {
        if (editorRef.current) {
            editorRef.current.setOption('mode', languageModes[selectedLanguage]);
        }
    }, [selectedLanguage]);

    /**
     * handleCompile — Sends the code to the backend for compilation and execution.
     *
     * Flow:
     * 1. Gets the current code from the editor.
     * 2. Sends a POST request to the backend's /api/compile endpoint with:
     *    - code: The source code to compile/run
     *    - input: The stdin input for the program
     *    - language: The selected programming language
     * 3. The backend forwards this to the Wandbox API and returns the output.
     * 4. The output is displayed in the Output textarea.
     *
     * Error handling:
     * - If the server returns a non-200 status, shows the HTTP error code.
     * - If the fetch fails entirely (network error), shows a connection error.
     * - The "compiling" state disables the Run button during compilation.
     */
    const handleCompile = async () => {
        if (!editorRef.current) return;
        const code = editorRef.current.getValue();
        const backendUrl = (process.env.REACT_APP_BACKEND_URL || 'http://localhost:8080').replace(/\/+$/, '');

        setCompiling(true);
        setOutput('Running...');

        try {
            const response = await fetch(`${backendUrl}/api/compile`, {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ code, input, language: selectedLanguage })
            });

            if (!response.ok) {
                setOutput(`Error: Server returned ${response.status}`);
                return;
            }

            const data = await response.json();
            setOutput(data.output || 'No output');
        } catch (err) {
            setOutput('Error: Could not connect to the server.');
        } finally {
            setCompiling(false);
        }
    };

    return (
        <div className="editorPanel">
            {/* Toolbar: language selector and Run button */}
            <div className="editorToolbar">
                <select
                    className='langSelect'
                    onChange={(e) => setSelectedLanguage(e.target.value)}
                    value={selectedLanguage}
                >
                    <option value="python">Python</option>
                    <option value="java">Java</option>
                    <option value="cpp">C++</option>
                    <option value="c">C</option>
                </select>
                <button
                    className='btn runBtn'
                    onClick={handleCompile}
                    disabled={compiling}
                >
                    {compiling ? 'Running...' : '▶ Run'}
                </button>
            </div>

            {/* The textarea that CodeMirror replaces with the rich editor */}
            <div className="editorArea">
                <textarea ref={textareaRef} id="realtimeEditor"></textarea>
            </div>

            {/* Input/Output panels for code execution */}
            <div className='codeRunner'>
                <div className="runnerSection">
                    <label>Input</label>
                    <textarea
                        className='runnerTextArea'
                        value={input}
                        onChange={(e) => setInput(e.target.value)}
                        placeholder="Standard input..."
                    />
                </div>
                <div className="runnerSection">
                    <label>Output</label>
                    <textarea
                        className='runnerTextArea'
                        value={output}
                        readOnly
                        placeholder="Output will appear here..."
                    />
                </div>
            </div>
        </div>
    );
};

export default Editor;
