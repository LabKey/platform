import React from 'react'
import ReactDOM from 'react-dom'
import {AppContainer} from 'react-hot-loader'

import {App} from './IssuesListDesigner'

const render = () => {
    ReactDOM.render(
        <AppContainer>
            <App/>
        </AppContainer>,
        document.getElementById('app')
    )
};

declare const module: any;

if (module.hot) {
    module.hot.accept();
}

render();