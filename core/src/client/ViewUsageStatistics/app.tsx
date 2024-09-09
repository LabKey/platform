import React from 'react';
import { createRoot } from 'react-dom/client';

import { UsageStatsViewer } from './UsageStatsViewer';

import './viewUsageStatistics.scss';

window.addEventListener('DOMContentLoaded', () => {
    createRoot(document.getElementById('app')).render(<UsageStatsViewer />);
});
