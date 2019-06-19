/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
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
        name: 'assayDataImport',
        title: 'Assay Data Import',
        permission: 'insert',
        path: './src/client/AssayDataImport'
    }]
};