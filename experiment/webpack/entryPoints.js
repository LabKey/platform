/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
module.exports = {
    apps: [{
    //     name: 'assayDesigner',
    //     title: 'Assay Designer',
    //     permission: 'admin',
    //     path: './src/client/AssayDesigner'
    // },{
        name: 'domainDesigner',
        title: 'Domain Designer',
        permission: 'admin',
        path: './src/client/DomainDesigner'
    },{
        name: 'fileUploadPreview',
        title: 'File Upload Preview',
        permission: 'insert',
        path: './src/client/FileUploadPreview'
    }]
};