import * as React from 'react'
import * as ReactDOM from 'react-dom'

import {App} from './HelloWorld'

// Need to wait for container element to be available in labkey wrapper before render
window.addEventListener('DOMContentLoaded', (event) => {
    ReactDOM.render(<App/>, document.getElementById('app'));
});
