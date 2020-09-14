import React from 'react';
import ReactDOM from 'react-dom';
import { App } from '@labkey/api';

import { AppContext, ErrorHandler } from './ErrorHandler';

App.registerApp<AppContext>('errorHandler', (target, ctx) => {
    ReactDOM.render(<ErrorHandler context={ctx} />, document.getElementById(target));
});
