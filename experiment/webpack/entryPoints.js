/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
module.exports = {
    apps: [{
        name: 'domainDesigner',
        title: 'Domain Designer',
        permission: 'admin', // TODO this should likely be read and then it is up to the DomainKind permissions check
        path: './src/client/DomainDesigner'
    }]
};