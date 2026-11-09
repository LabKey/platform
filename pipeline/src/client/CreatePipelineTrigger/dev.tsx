/*
 * Copyright (c) 2021-2026 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React from 'react';
import { createRoot } from 'react-dom/client';
import { App } from '@labkey/api';

import { Props, CreatePipelineTrigger } from './CreatePipelineTrigger';

const render = (target: string, ctx: Props): void => {
    createRoot(document.getElementById(target)).render(<CreatePipelineTrigger {...ctx} />);
};

App.registerApp<Props>('createPipelineTrigger', render, true);
