import React from 'react';
import ReactDOM from 'react-dom';
import { App } from '@labkey/api';

import { AppContext, ProductNavigation } from './ProductNavigation';

const render = (target: string, ctx: AppContext) => {
    ReactDOM.render(<ProductNavigation context={ctx} />, document.getElementById(target));
};

App.registerApp<AppContext>('productNavigation', render, true);
