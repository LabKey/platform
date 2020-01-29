/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
module.exports = {
    apps: [{
        name: 'listDesigner',
        title: 'List Designer',
        permission: 'read', // the component will check for DesignListPermission and show edit vs read only mode accordingly, see ListController.EditListDefinitionAction for main usage
        path: './src/client/ListDesigner'
    }]
};