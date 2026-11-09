/*
 * Copyright (c) 2020-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React from 'react';
import { createRoot } from 'react-dom/client';
import { App } from '@labkey/api';

import { AppContext, RunGraph } from './RunGraph';

App.registerApp<AppContext>('runGraph', (target, ctx) => {
    createRoot(document.getElementById(target)).render(<RunGraph context={ctx} />);
});
