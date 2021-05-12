import React from 'react';
import ReactDOM from 'react-dom';
import { App } from '@labkey/api';

import { Props, CreatePipelineTrigger } from './CreatePipelineTrigger';

App.registerApp<Props>('createPipelineTrigger', (target, ctx) => {
    ReactDOM.render(<CreatePipelineTrigger {...ctx} />, document.getElementById(target));
});
