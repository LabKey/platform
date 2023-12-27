import React from 'react';
import ReactDOM from 'react-dom';
import { App } from '@labkey/api';

import { AppContext, ErrorHandler } from './ErrorHandler';

import './errorHandler.scss';

const render = (target: string, ctx: AppContext) => {
    ReactDOM.render(<ErrorHandler context={ctx} />, document.getElementById(target));
};

App.registerApp<AppContext>('errorHandler', render, true);
