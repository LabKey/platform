/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
module.exports = {
    apps: [{
        name: 'authenticationConfiguration',
        title: 'Authentication',
        permission: 'admin',
        path: './src/client/AuthenticationConfiguration'
    },{
        name: 'components',
        title: '@labkey/components',
        permission: 'admin',
        path: './src/client/LabKeyUIComponentsPage'
    }, {
        name: 'viewUsageStatistics',
        title: 'View Usage Statistics',
        permission: 'admin',
        path: './src/client/ViewUsageStatistics',
    },{
        name: 'errorHandler',
        title: 'Error Handler',
        path: './src/client/ErrorHandler',
        generateLib: true // used in errorView.jsp
    },{
        name: 'productNavigation',
        title: 'Product Navigation',
        path: './src/client/ProductNavigation',
        generateLib: true // used in header.jsp
    },{
        name: 'assayDesigner',
        title: 'Assay Designer',
        permission: 'read', // the component will check for DesignAssayPermission and show edit vs read only mode accordingly, used in assay/action/DesignerAction.java
        path: './src/client/AssayDesigner'
    },{
        name: 'domainDesigner',
        title: 'Field Editor',
        permission: 'admin', // used in PropertyController.EditDomainAction
        path: './src/client/DomainDesigner'
    }, {
        name: 'sampleTypeDesigner',
        title: 'Sample Type Designer',
        permission: 'admin', // used in ExperimentController.EditSampleTypeAction
        path: './src/client/SampleTypeDesigner'
    },{
        name: 'dataClassDesigner',
        title: 'Data Class Designer',
        permission: 'admin', // used in ExperimentController.EditDataClassAction
        path: './src/client/DataClassDesigner'
    },{
        name: 'issuesListDesigner',
        title: 'Issues List Definition Designer',
        permission: 'admin', // used in IssuesController.AdminAction
        path: './src/client/IssuesListDesigner'
    },{
        name: 'listDesigner',
        title: 'List Designer',
        permission: 'read', // the component will check for DesignListPermission and show edit vs read only mode accordingly, used in ListController.EditListDefinitionAction
        path: './src/client/ListDesigner'
    },{
        name: 'queryMetadataEditor',
        title: 'Metadata Editor',
        permission: 'admin', // used in QueryController.MetadataQueryAction
        path: './src/client/QueryMetadataEditor'
    },{
        name: 'datasetDesigner',
        title: 'Dataset Designer',
        permission: 'admin', // used in StudyController.EditTypeAction and StudyController.DefineDatasetTypeAction
        path: './src/client/DatasetDesigner'
    }, {
        name: 'conceptFilter',
        title: 'Concept Filter',
        generateLib: true, // used in FilterDialog.js
        path: './src/client/ConceptFilter'
    }, {
        name: 'querySelectInput',
        title: 'Query Select Input',
        generateLib: true, // used in TypeAheadSelectDisplayColumn.java
        path: './src/client/QuerySelectInput'
    }, {
        name: 'apiKeys', // used in ApiKeyViewProvider.java
        title: 'API Keys',
        path: './src/client/APIKeys'
    }]
};
