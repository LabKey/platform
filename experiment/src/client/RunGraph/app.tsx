import React from 'react';
import ReactDOM from 'react-dom';
import { registerApp } from '@labkey/components';

import { AppContext, RunGraph } from './RunGraph';

registerApp<AppContext>('runGraph', (target, ctx) => {
    ReactDOM.render(<RunGraph context={ctx} />, document.getElementById(target));
});
