import React from 'react';
import { createRoot } from 'react-dom/client';
import { App } from '@labkey/api';

import { AppContext, QuerySelectInput } from './QuerySelectInput';

App.registerApp<AppContext>('querySelectInput', (target, ctx) => {
    createRoot(document.getElementById(target)).render(<QuerySelectInput context={ctx} />);
});
