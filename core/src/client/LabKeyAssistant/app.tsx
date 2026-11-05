/*
 * Copyright (c) 2021-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React from 'react';
import { createRoot } from 'react-dom/client';
import { App } from '@labkey/api';

import { LabKeyAssistant } from './assistant';

function render(target: string) {
    console.log('assistant app.tsx render');
    createRoot(document.getElementById(target)).render(<LabKeyAssistant />);
}

App.registerApp('labKeyAssistant', render);
