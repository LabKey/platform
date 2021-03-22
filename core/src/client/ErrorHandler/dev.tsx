import React from 'react';
import ReactDOM from 'react-dom';
import { AppContainer } from 'react-hot-loader';
import { App } from '@labkey/api';

import { AppContext, ErrorHandler } from './ErrorHandler';

import './errorHandler.scss';

const render = (target: string, ctx: AppContext) => {
    ReactDOM.render(
        <AppContainer>
            <ErrorHandler context={ctx}/>
        </AppContainer>,
        document.getElementById(target)
    );
};

App.registerApp<AppContext>('errorHandler', render, true);

declare const module: any;

if (module.hot) {
    module.hot.accept();
}