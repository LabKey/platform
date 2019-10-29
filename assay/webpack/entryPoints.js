/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
module.exports = {
    apps: [{
        name: 'assayDesigner',
        title: 'Assay Designer',
        permission: 'read', // the component will check for DesignAssayPermission and show edit vs read only mode accordingly, see assay/action/DesignerAction.java for main usage
        path: './src/client/AssayDesigner'
    },{
        name: 'assayDataImport',
        title: 'Assay Data Import',
        permission: 'insert',
        path: './src/client/AssayDataImport'
    }]
};