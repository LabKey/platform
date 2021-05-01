import React from 'react';
import ReactDOM from 'react-dom';
import { AppContainer } from 'react-hot-loader';
import { App } from '@labkey/api';

import {AppContext, ConceptFilterView} from './ConceptFilterView';

const render = (target: string, ctx: AppContext) => {
    ReactDOM.render(
        <AppContainer>
            <ConceptFilterView context={ctx}/>
        </AppContainer>,
        document.getElementById(target)
    );
};

App.registerApp<AppContext>('conceptFilter', render, true);

declare const module: any;

if (module.hot) {
    module.hot.accept();
}
