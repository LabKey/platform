import React from 'react';
import { createRoot } from 'react-dom/client';

import { App } from './ListDesigner';

const render = (): void => {
    createRoot(document.getElementById('app')).render(<App />);
};

render();
