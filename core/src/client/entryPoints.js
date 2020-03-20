/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
module.exports = {
    apps: [{
        name: 'authenticationConfiguration',
        title: 'Authentication',
        permission: 'admin',
        path: './src/client/AuthenticationConfiguration'
    },{
        name: 'components',
        title: '@labkey/components',
        permission: 'admin',
        path: './src/client/LabKeyUIComponentsPage'
    }]
};