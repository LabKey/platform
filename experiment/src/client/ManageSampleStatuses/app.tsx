import React from 'react'
import ReactDOM from 'react-dom'
import { ManageSampleStatusesPanel } from '@labkey/components'

import './ManageSampleStatuses.scss';

// Need to wait for container element to be available in labkey wrapper before render
window.addEventListener('DOMContentLoaded', (event) => {
    ReactDOM.render(<ManageSampleStatusesPanel />, document.getElementById('app'));
});
