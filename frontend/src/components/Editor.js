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

const languageModes = {
    python: 'python',
    java: 'text/x-java',
    cpp: 'text/x-c++src',
    c: 'text/x-csrc'
};

const Editor = ({ socket, roomId, onCodeChange }) => {
    const editorRef = useRef(null);
    const textareaRef = useRef(null);
    const socketRef = useRef(socket);
    const [selectedLanguage, setSelectedLanguage] = useState('python');
    const [input, setInput] = useState('');
    const [output, setOutput] = useState('');
    const [compiling, setCompiling] = useState(false);

    useEffect(() => { socketRef.current = socket; }, [socket]);

    useEffect(() => {
        if (editorRef.current) return;

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
            onCodeChange(code);
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

    useEffect(() => {
        if (editorRef.current) {
            editorRef.current.setOption('mode', languageModes[selectedLanguage]);
        }
    }, [selectedLanguage]);

    const handleCompile = async () => {
        if (!editorRef.current) return;
        const code = editorRef.current.getValue();
        const backendUrl = process.env.REACT_APP_BACKEND_URL || 'http://localhost:8080';

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
            <div className="editorArea">
                <textarea ref={textareaRef} id="realtimeEditor"></textarea>
            </div>
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
