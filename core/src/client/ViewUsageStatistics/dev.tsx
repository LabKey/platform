import React from 'react';
import ReactDOM from 'react-dom';
import { UsageStatsViewer } from './UsageStatsViewer';

import './viewUsageStatistics.scss';

const render = (): void => {
    ReactDOM.render(<UsageStatsViewer />, document.getElementById('app'));
};

render();
