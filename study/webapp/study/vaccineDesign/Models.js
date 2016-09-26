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
        {name : 'Adjuvant', defaultValue: []}
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
        {name : 'VisitMap', defaultValue: []}
    ]
});

Ext4.define('LABKEY.VaccineDesign.Visit', {
    extend : 'Ext.data.Model',
    fields : [
        {name : 'RowId', type : 'int'},
        {name : 'Label', type : 'string'},
        {name : 'SortOrder', type : 'int'},
        {name : 'Included', type : 'boolean'}
    ]
});