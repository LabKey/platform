import React from 'react';
import ReactDOM from 'react-dom';
import { UsageStatsViewer } from './UsageStatsViewer';

import './viewUsageStatistics.scss';

window.addEventListener('DOMContentLoaded', () => {
    ReactDOM.render(<UsageStatsViewer />, document.getElementById('app'));
});
