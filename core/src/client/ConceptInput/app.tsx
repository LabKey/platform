import React from 'react';
import ReactDOM from 'react-dom';
import { App } from '@labkey/api';

import {AppContext, ConceptInputPanel} from './ConceptInputPanel';

App.registerApp<AppContext>('conceptInput', (target, ctx) => {
    ReactDOM.render(<ConceptInputPanel context={ctx} />, document.getElementById(target));
});
