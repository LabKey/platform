import React from 'react';
import ReactDOM from 'react-dom';

import { RunGraph } from './RunGraph';
import { AppContext, registerApp } from './util';

registerApp<AppContext>('runGraph', (target, ctx) => {
    ReactDOM.render(<RunGraph context={ctx} />, document.getElementById(target));
});
