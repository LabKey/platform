import React from 'react';
import ReactDOM from 'react-dom';
import { AppContainer } from 'react-hot-loader';
import { registerApp } from '@labkey/components';

import { AppContext, RunGraph } from './RunGraph';

const render = (target: string, ctx: AppContext) => {
    ReactDOM.render(
        <AppContainer>
            <RunGraph context={ctx}/>
        </AppContainer>,
        document.getElementById(target)
    );
};

registerApp<AppContext>('runGraph', render, true);

declare const module: any;

if (module.hot) {
    module.hot.accept();
}