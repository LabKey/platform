import React from 'react';
import ReactDOM from 'react-dom';
import { App } from '@labkey/api';

import { AppContext, QuerySelectInput } from './QuerySelectInput';

const render = (target: string, ctx: AppContext): void => {
    ReactDOM.render(<QuerySelectInput context={ctx} />, document.getElementById(target));
};

App.registerApp<AppContext>('querySelectInput', render, true);
