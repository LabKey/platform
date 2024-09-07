import React from 'react';
import { createRoot } from 'react-dom/client';

import { App } from './AssayTypeSelect';

const render = () => {
    createRoot(document.getElementById('app')).render(<App />);
};

render();