/*
 * Copyright (c) 2019 LabKey Corporation. All rights reserved. No portion of this work may be reproduced in
 * any form or by any electronic or mechanical means without written permission from LabKey Corporation.
 */
// TODO figure out how to get app.view.template.xml to load all chunks with prefix "vendors" so we don't have to list them here
module.exports = {
    apps: [{
        name: 'assayDesigner',
        title: 'Assay Designer',
        permission: 'admin',
        path: './src/client/AssayDesigner',
        chunks: ['vendors~assayDesigner~domainDesigner', 'assayDesigner']
    },{
        name: 'domainDesigner',
        title: 'Domain Designer',
        permission: 'admin',
        path: './src/client/DomainDesigner',
        chunks: ['vendors~assayDesigner~domainDesigner', 'vendors~domainDesigner', 'domainDesigner']
    }]
};