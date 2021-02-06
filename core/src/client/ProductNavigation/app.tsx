import React from 'react';
import ReactDOM from 'react-dom';
import { App } from '@labkey/api';

import {AppContext, ProductNavigation} from './ProductNavigation';

// TODO JS error with the core module npm run build-prod
App.registerApp<AppContext>('productNavigation', (target, ctx) => {
    console.log(document.getElementById(target));
    ReactDOM.render(<ProductNavigation context={ctx}/>, document.getElementById(target));
});
