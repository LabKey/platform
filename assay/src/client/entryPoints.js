/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
module.exports = {
    apps: [{
        name: 'assayDataImport',
        title: 'Assay Data Import',
        permission: 'insert',
        path: './src/client/AssayDataImport'
    },{
        name: 'assayTypeSelect',
        title: 'New Assay Design',
        permission: 'insert',
        permissionClasses: ['org.labkey.api.assay.security.DesignAssayPermission'],
        path: './src/client/AssayTypeSelect'
    }]
};