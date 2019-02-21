/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
import * as React from 'react'
import * as ReactDOM from 'react-dom'
import $ from 'jquery'

import {App} from './AssayDesigner'

$(() => ReactDOM.render(
    <App/>,
    document.getElementById('app'))
);

