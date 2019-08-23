/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
module.exports = {
    apps: [{
        name: 'assayDesigner',
        title: 'Assay Designer',
        permission: 'read', // the component will check for DesignAssayPermission and show edit vs read only mode accordingly
        path: './src/client/AssayDesigner'
    },{
        name: 'domainDesigner',
        title: 'Domain Designer',
        permission: 'admin', // TODO this should likely be read and then it is up to the DomainKind permissions check
        path: './src/client/DomainDesigner'
    },{
        name: 'assayDataImport',
        title: 'Assay Data Import',
        permission: 'insert',
        path: './src/client/AssayDataImport'
    }]
};