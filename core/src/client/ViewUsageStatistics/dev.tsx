import React from 'react';
import { createRoot } from 'react-dom/client';

import { UsageStatsViewer } from './UsageStatsViewer';

import './viewUsageStatistics.scss';

const render = (): void => {
    createRoot(document.getElementById('app')).render(<UsageStatsViewer />);
};

render();
