import React from 'react';
import ReactDOM from 'react-dom';

import { App } from './ListDesigner';

const render = (): void => {
    ReactDOM.render(<App />, document.getElementById('app'));
};

render();
