import React from 'react';
import { createRoot } from 'react-dom/client';
import { App } from '@labkey/api';

import { Props, CreatePipelineTrigger } from './CreatePipelineTrigger';

App.registerApp<Props>('createPipelineTrigger', (target, ctx) => {
    createRoot(document.getElementById(target)).render(<CreatePipelineTrigger {...ctx} />);
});
