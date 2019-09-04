import * as React from 'react'
import * as ReactDOM from 'react-dom'
import $ from 'jquery'

import {App} from './ToDoListPage'

$(() => ReactDOM.render(
    <App/>,
    document.getElementById('app'))
);