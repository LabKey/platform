import React from 'react';
import { createRoot } from 'react-dom/client';
import { App } from '@labkey/api';

import { AppContext, RunGraph } from './RunGraph';

App.registerApp<AppContext>('runGraph', (target, ctx) => {
    createRoot(document.getElementById(target)).render(<RunGraph context={ctx} />);
});
