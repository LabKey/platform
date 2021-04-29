import React from 'react';
import ReactDOM from 'react-dom';
import { AppContainer } from 'react-hot-loader';
import { App } from '@labkey/api';

import { Props, CreatePipelineTrigger } from './CreatePipelineTrigger';

const render = (target: string, ctx: Props): void => {
    ReactDOM.render(
        <AppContainer>
            <CreatePipelineTrigger {...ctx} />
        </AppContainer>,
        document.getElementById(target)
    );
};

App.registerApp<Props>('createPipelineTrigger', render, true);

// eslint-disable-next-line @typescript-eslint/no-explicit-any
declare const module: any;

if (module.hot) {
    module.hot.accept();
}
