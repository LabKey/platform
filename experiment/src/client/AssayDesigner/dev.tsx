/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import * as React from 'react'
import * as ReactDOM from 'react-dom'
import $ from 'jquery'

import {AppContainer} from 'react-hot-loader'

import {App} from './AssayDesigner'

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