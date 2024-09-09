import React from 'react';
import { createRoot } from 'react-dom/client';

import { App } from './AssayTypeSelect';

// Need to wait for container element to be available in labkey wrapper before render
window.addEventListener('DOMContentLoaded', (event) => {
    createRoot(document.getElementById('app')).render(<App />);
});