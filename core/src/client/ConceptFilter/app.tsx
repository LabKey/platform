import React from 'react';
import ReactDOM from 'react-dom';
import { App } from '@labkey/api';

import {AppContext, ConceptFilterView} from './ConceptFilterView';

App.registerApp<AppContext>('conceptFilter', (target, ctx) => {
    ReactDOM.render(<ConceptFilterView context={ctx} />, document.getElementById(target));
});
