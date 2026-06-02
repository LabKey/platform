/*
 * Copyright (c) 2024-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React from 'react';
import { createRoot } from 'react-dom/client';

import { UsageStatsViewer } from './UsageStatsViewer';

import './viewUsageStatistics.scss';

const render = (): void => {
    createRoot(document.getElementById('app')).render(<UsageStatsViewer />);
};

render();
