import React from 'react';
import { createRoot } from 'react-dom/client';
import { App } from '@labkey/api';

import { AppContext, ErrorHandler } from './ErrorHandler';

import './errorHandler.scss';

App.registerApp<AppContext>('errorHandler', (target, ctx) => {
    createRoot(document.getElementById(target)).render(<ErrorHandler context={ctx} />);
});
