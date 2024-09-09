import React from 'react';
import { createRoot } from 'react-dom/client';
import { App } from '@labkey/api';

import { AppContext, ConceptFilterView } from './ConceptFilterView';

App.registerApp<AppContext>('conceptFilter', (target, ctx) => {
    createRoot(document.getElementById(target)).render(<ConceptFilterView context={ctx} />);
});
