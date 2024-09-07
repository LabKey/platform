import React from 'react';
import { createRoot } from 'react-dom/client';
import { App } from '@labkey/api';

import { AppContext, ProductNavigation } from './ProductNavigation';

const render = (target: string, ctx: AppContext) => {
    createRoot(document.getElementById(target)).render(<ProductNavigation context={ctx} />);
};

App.registerApp<AppContext>('productNavigation', render, true);
