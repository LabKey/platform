import React from 'react';
import ReactDOM from 'react-dom';
import { AppContainer } from 'react-hot-loader';
import { App } from '@labkey/api';

import {AppContext, ConceptInputPanel} from './ConceptInputPanel';

const render = (target: string, ctx: AppContext) => {
    ReactDOM.render(
        <AppContainer>
            <ConceptInputPanel context={ctx}/>
        </AppContainer>,
        document.getElementById(target)
    );
};

App.registerApp<AppContext>('conceptInput', render, true);

declare const module: any;

if (module.hot) {
    module.hot.accept();
}
