/*
 * Copyright (c) 2016-2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0: http://www.apache.org/licenses/LICENSE-2.0
 */
Ext4.define('LABKEY.VaccineDesign.Product', {
    extend : 'Ext.data.Model',
    idgen: 'sequential',
    fields : [
        {name : 'RowId', defaultValue: undefined},
        {name : 'Label', type : 'string'},
        {name : 'Role', type : 'string'},
        {name : 'Type', type : 'string'},
        {name : 'Antigens', defaultValue: []},
        {name : 'DoseAndRoute', defaultValue: []}
    ]
});

Ext4.define('LABKEY.VaccineDesign.Treatment', {
    extend : 'Ext.data.Model',
    idgen: 'sequential',
    fields : [
        {name : 'RowId', defaultValue: undefined},
        {name : 'Label', type : 'string'},
        {name : 'Description', type : 'string'},
        {name : 'Immunogen', defaultValue: []},
        {name : 'Adjuvant', defaultValue: []},
        {name : 'Challenge', defaultValue: []}
    ]
});

Ext4.define('LABKEY.VaccineDesign.Cohort', {
    extend : 'Ext.data.Model',
    idgen: 'sequential',
    fields : [
        {name : 'RowId', defaultValue: undefined},
        {name : 'Label', type : 'string'},
        // the DataView XTemplate gets mad if this is defined as type 'int'
        {name : 'SubjectCount', type : 'string'},
        {name : 'CanDelete', type : 'boolean'},
        {name : 'VisitMap', defaultValue: []}
    ]
});

Ext4.define('LABKEY.VaccineDesign.Assay', {
    extend : 'Ext.data.Model',
    idgen: 'sequential',
    fields : [
        {name : 'RowId', defaultValue: undefined},
        {name : 'AssayName', type : 'string'},
        {name : 'DataSet', type : 'int'},
        {name : 'Description', type : 'string'},
        {name : 'Lab', type : 'string'},
        {name : 'LocationId', type : 'int'},
        {name : 'SampleType', type : 'string'},
        {name : 'Source', type : 'string'},
        {name : 'TubeType', type : 'string'},
        {name : 'SampleQuantity', type : 'float'},
        {name : 'SampleUnits', type : 'string'},
        {name : 'VisitMap', defaultValue: []}
    ]
});

Ext4.define('LABKEY.VaccineDesign.AssaySpecimenVisit', {
    extend : 'Ext.data.Model',
    fields : [
        {name : 'RowId', type : 'int'},
        {name : 'VisitId', type : 'int'},
        {name : 'AssaySpecimenId', type : 'int'}
    ]
});

Ext4.define('LABKEY.VaccineDesign.Visit', {
    extend : 'Ext.data.Model',
    fields : [
        {name : 'RowId', type : 'int'},
        {name : 'Label', type : 'string'},
        {name : 'DisplayOrder', type : 'int'},
        {name : 'SequenceNumMin', type : 'numeric'},
        {name : 'Included', type : 'boolean'}
    ]
});