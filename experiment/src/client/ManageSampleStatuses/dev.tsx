import React from 'react'
import ReactDOM from 'react-dom'
import { AppContainer } from 'react-hot-loader'
import { ManageSampleStatusesPanel } from '@labkey/components'

import './ManageSampleStatuses.scss';

const render = () => {
    ReactDOM.render(
        <AppContainer>
            <ManageSampleStatusesPanel />
        </AppContainer>,
        document.getElementById('app')
    )
};

declare const module: any;

if (module.hot) {
    module.hot.accept();
}

render();