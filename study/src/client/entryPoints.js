/*
 * Copyright (c) 2020 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
module.exports = {
    apps: [{
        name: 'datasetDesigner',
        title: 'Dataset Designer',
        permission: 'admin', // this is admin so that direct access to this view has highest level perm, see StudyController.EditTypeAction for main usage
        path: './src/client/DatasetDesigner'
    }]
};