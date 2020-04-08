/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
module.exports = {
    apps: [{
        name: 'domainDesigner',
        title: 'Field Editor',
        permission: 'admin', // this is admin so that direct access to this view has highest level perm, see PropertyController.EditDomainAction for main usage
        path: './src/client/DomainDesigner'
    }, {
        name: 'sampleTypeDesigner',
        title: 'Sample Set Designer',
        permission: 'admin', // this is admin so that direct access to this view has highest level perm, see ExperimentController.BaseSampleSetAction for main usage
        path: './src/client/SampleTypeDesigner'
    },{
        name: 'dataClassDesigner',
        title: 'Data Class Designer',
        permission: 'admin', // this is admin so that direct access to this view has highest level perm, see <View Action TBD> for main usage
        path: './src/client/DataClassDesigner'
    },{
        name: 'runGraph',
        title: 'Experiment Run Graph',
        path: './src/client/RunGraph',
        generateViews: false // used by experimentRunGraphView.jsp
    }]
};