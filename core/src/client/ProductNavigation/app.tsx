import React from 'react';
import ReactDOM from 'react-dom';
import { App } from '@labkey/api';

import {AppContext, ProductNavigation} from './ProductNavigation';

App.registerApp<AppContext>('productNavigation', (target, ctx) => {
    ReactDOM.render(<ProductNavigation context={ctx}/>, document.getElementById(target));
});
