
Ext4.define('LABKEY.VaccineDesign.Product', {
    extend : 'Ext.data.Model',
    idgen: 'sequential',
    fields : [
        {name : 'RowId', defaultValue: undefined},
        {name : 'Label', type : 'string'},
        {name : 'Role', type : 'string'},
        {name : 'Type', type : 'string'},
        {name : 'Antigens', defaultValue: []}
    ]
});