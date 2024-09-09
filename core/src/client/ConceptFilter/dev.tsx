import React from 'react';
import { createRoot } from 'react-dom/client';
import { App } from '@labkey/api';

import { AppContext, ConceptFilterView } from './ConceptFilterView';

const render = (target: string, ctx: AppContext): void => {
    createRoot(document.getElementById(target)).render(<ConceptFilterView context={ctx} />);
};

App.registerApp<AppContext>('conceptFilter', render, true);
