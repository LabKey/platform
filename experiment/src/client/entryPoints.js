/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
module.exports = {
    apps: [{
        name: 'runGraph',
        title: 'Experiment Run Graph',
        path: './src/client/RunGraph',
        generateLib: true // used by experimentRunGraphView.jsp
    }, {
        name: 'manageSampleStatuses',
        title: 'Manage Sample Statuses',
        permission: 'admin',
        path: './src/client/ManageSampleStatuses'
    }]
};