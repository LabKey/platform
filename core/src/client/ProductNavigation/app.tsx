import React from 'react';
import { createRoot } from 'react-dom/client';
import { App } from '@labkey/api';

import { AppContext, ProductNavigation } from './ProductNavigation';

App.registerApp<AppContext>('productNavigation', (target, ctx) => {
    createRoot(document.getElementById(target)).render(<ProductNavigation context={ctx} />);
});
