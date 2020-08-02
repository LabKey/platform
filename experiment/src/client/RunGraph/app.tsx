import React from 'react';
import ReactDOM from 'react-dom';
import { App } from '@labkey/api';

import { AppContext, RunGraph } from './RunGraph';

App.registerApp<AppContext>('runGraph', (target, ctx) => {
    ReactDOM.render(<RunGraph context={ctx} />, document.getElementById(target));
});
