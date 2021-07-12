import React from 'react';
import ReactDOM from 'react-dom';
import { App } from '@labkey/api';

import { AppContext, QuerySelectInput } from './QuerySelectInput';

App.registerApp<AppContext>('querySelectInput', (target, ctx) => {
    ReactDOM.render(<QuerySelectInput context={ctx} />, document.getElementById(target));
});
