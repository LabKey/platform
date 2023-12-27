import React from 'react';
import ReactDOM from 'react-dom';
import { App } from '@labkey/api';

import { AppContext, ConceptFilterView } from './ConceptFilterView';

const render = (target: string, ctx: AppContext): void => {
    ReactDOM.render(<ConceptFilterView context={ctx} />, document.getElementById(target));
};

App.registerApp<AppContext>('conceptFilter', render, true);
