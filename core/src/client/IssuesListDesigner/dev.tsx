import React from 'react';
import { createRoot } from 'react-dom/client';

import { App } from './IssuesListDesigner';

const render = (): void => {
    createRoot(document.getElementById('app')).render(<App />);
};

render();
