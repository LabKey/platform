
<div id="form"></div>

<script type="text/javascript">

var datasources = [['labkey'],['sas']];
var store = new Ext.data.SimpleStore({
    fields:['name'],
    data:datasources
});

Ext.onReady(function() {
    var f = new Ext.form.FormPanel({
        url:window.location,
        border:false,
        standardSubmit:true,
        items:[
            {name:'schemaName', fieldLabel:'Schema Name', xtype:'textfield'},
            {name:'editable', fieldLabel:'Editable', xtype:'checkbox'},
            {name:'datasource', fieldLabel:'Data Source', xtype:'combo', store:store, valueField:'name', displayField:'name'}
        ],
        buttons:['submit']
    });
    f.render('form');
});
</script>