/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
module.exports = {
    apps: [{
        name: 'designer',
        title: 'Issues List Definition Designer',
        permission: 'read', //TODO: should this be 'admin' instead?
        path: './src/client/Designer'
    }]
};