import * as React from 'react'
import * as ReactDOM from 'react-dom'

import {AppContainer} from 'react-hot-loader'

import {App} from './ToDoListPage'

const render = () => {
    ReactDOM.render(
        <AppContainer>
            <App/>
        </AppContainer>,
        document.getElementById('app')
    )
};

declare const module: any;

window.addEventListener('DOMContentLoaded', (event) => {
    render();
    if (module.hot) {
        module.hot.accept();
    }
});