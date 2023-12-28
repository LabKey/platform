import React from 'react';
import ReactDOM from 'react-dom';
import { App } from '@labkey/api';

import { AppContext, RunGraph } from './RunGraph';

const render = (target: string, ctx: AppContext): void => {
    ReactDOM.render(
            <RunGraph context={ctx}/>,
        document.getElementById(target)
    );
};

App.registerApp<AppContext>('runGraph', render, true);
