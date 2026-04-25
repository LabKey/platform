/*
 * Copyright (c) 2024 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
import React from 'react';
import { createRoot } from 'react-dom/client';
import { getServerContext } from '@labkey/api';
import { ServerContextProvider, withAppUser } from '@labkey/components';

import { PlateTemplateDesigner } from './PlateTemplateDesigner';

const render = () => {
    createRoot(document.getElementById('app')).render(
        <ServerContextProvider initialContext={withAppUser(getServerContext())}>
            <PlateTemplateDesigner />
        </ServerContextProvider>
    );
};

render();
