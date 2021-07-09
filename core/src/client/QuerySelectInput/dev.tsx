import React from 'react';
import ReactDOM from 'react-dom';
import { AppContainer } from 'react-hot-loader';
import { App } from '@labkey/api';

import {AppContext, QuerySelectInput} from './QuerySelectInput';

const render = (target: string, ctx: AppContext) => {
    ReactDOM.render(
        <AppContainer>
            <QuerySelectInput context={ctx}/>
        </AppContainer>,
        document.getElementById(target)
    );
};

App.registerApp<AppContext>('querySelectInput', render, true);

declare const module: any;

if (module.hot) {
    module.hot.accept();
}
