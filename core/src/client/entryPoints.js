/*
 * Copyright (c) 2019 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
module.exports = {
    apps: [{
        name: 'authenticationConfiguration',
        title: 'Authentication',
        permissionClasses: ['org.labkey.api.security.permissions.AdminPermission'],
        path: './src/client/AuthenticationConfiguration'
    },{
        name: 'components',
        title: '@labkey/components',
        permissionClasses: ['org.labkey.api.security.permissions.AdminPermission'],
        path: './src/client/LabKeyUIComponentsPage'
    }, {
        name: 'viewUsageStatistics',
        title: 'View Usage Statistics',
        permissionClasses: ['org.labkey.api.security.permissions.AdminPermission'],
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
        permissionClasses: ['org.labkey.api.security.permissions.ReadPermission'], // the component will check for DesignAssayPermission and show edit vs read only mode accordingly, used in assay/action/DesignerAction.java
        path: './src/client/AssayDesigner'
    },{
        name: 'domainDesigner',
        title: 'Field Editor',
        permissionClasses: ['org.labkey.api.security.permissions.AdminPermission'], // used in PropertyController.EditDomainAction
        path: './src/client/DomainDesigner'
    }, {
        name: 'sampleTypeDesigner',
        title: 'Sample Type Designer',
        permissionClasses: ['org.labkey.api.security.permissions.AdminPermission'], // used in ExperimentController.EditSampleTypeAction
        path: './src/client/SampleTypeDesigner'
    },{
        name: 'dataClassDesigner',
        title: 'Data Class Designer',
        permissionClasses: ['org.labkey.api.security.permissions.AdminPermission'], // used in ExperimentController.EditDataClassAction
        path: './src/client/DataClassDesigner'
    },{
        name: 'issuesListDesigner',
        title: 'Issues List Definition Designer',
        permissionClasses: ['org.labkey.api.security.permissions.AdminPermission'], // used in IssuesController.AdminAction
        path: './src/client/IssuesListDesigner'
    },{
        name: 'listDesigner',
        title: 'List Designer',
        permissionClasses: ['org.labkey.api.security.permissions.ReadPermission'], // the component will check for DesignListPermission and show edit vs read only mode accordingly, used in ListController.EditListDefinitionAction
        path: './src/client/ListDesigner'
    },{
        name: 'queryMetadataEditor',
        title: 'Metadata Editor',
        permissionClasses: ['org.labkey.api.security.permissions.AdminPermission'], // used in QueryController.MetadataQueryAction
        path: './src/client/QueryMetadataEditor'
    },{
        name: 'datasetDesigner',
        title: 'Dataset Designer',
        permissionClasses: ['org.labkey.api.security.permissions.AdminPermission'], // used in StudyController.EditTypeAction and StudyController.DefineDatasetTypeAction
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
