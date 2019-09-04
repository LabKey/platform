import * as React from 'react'
import * as ReactDOM from 'react-dom'
import $ from 'jquery'

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

$(() => {
    render();
    if (module.hot) {
        module.hot.accept();
    }
});