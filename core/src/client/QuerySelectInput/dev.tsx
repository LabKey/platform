import React from 'react';
import { createRoot } from 'react-dom/client';
import { App } from '@labkey/api';

import { AppContext, QuerySelectInput } from './QuerySelectInput';

const render = (target: string, ctx: AppContext): void => {
    createRoot(document.getElementById(target)).render(<QuerySelectInput context={ctx} />);
};

App.registerApp<AppContext>('querySelectInput', render, true);
