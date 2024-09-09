import React from 'react';
import { createRoot } from 'react-dom/client';
import { App } from '@labkey/api';

import { AppContext, RunGraph } from './RunGraph';

const render = (target: string, ctx: AppContext): void => {
    createRoot(document.getElementById(target)).render(<RunGraph context={ctx} />);
};

App.registerApp<AppContext>('runGraph', render, true);
