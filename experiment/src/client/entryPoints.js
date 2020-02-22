/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
module.exports = {
    apps: [{
        name: 'domainDesigner',
        title: 'Field Editor',
        permission: 'admin', // this is admin so that direct access to this view has highest level perm, see PropertyController.EditDomainAction for main usage
        path: './src/client/DomainDesigner'
    }, {
        name: 'sampleTypeDesigner',
        title: 'Field Editor',
        permission: 'admin', // this is admin so that direct access to this view has highest level perm, see PropertyController.EditDomainAction for main usage
        path: './src/client/SampleTypeDesigner'
    }]
};