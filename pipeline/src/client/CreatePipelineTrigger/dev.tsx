import React from 'react';
import ReactDOM from 'react-dom';
import { App } from '@labkey/api';

import { Props, CreatePipelineTrigger } from './CreatePipelineTrigger';

const render = (target: string, ctx: Props): void => {
    ReactDOM.render(<CreatePipelineTrigger {...ctx} />, document.getElementById(target));
};

App.registerApp<Props>('createPipelineTrigger', render, true);
