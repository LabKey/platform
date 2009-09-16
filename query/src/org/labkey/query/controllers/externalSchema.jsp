
<div id="form"></div>

<script type="text/javascript">

    Ext.QuickTips.init();


var datasources = [['labkey'],['sas']];
var store = new Ext.data.SimpleStore({
    fields:['name'],
    data:datasources
});

var values={schemaName:'fred'};


Ext.onReady(function() {
    var f = new LABKEY.ext.FormPanel({
        url:window.location,
        values:values,
        border:false,
        standardSubmit:true,
        items:[
            {name:'schemaName', fieldLabel:'Schema Name', xtype:'textfield'},
            {name:'editable', fieldLabel:'Editable', xtype:'checkbox'},
            {name:'datasource', qtip:{html:'hi'}, fieldLabel:'Data Source', xtype:'combo', store:store, valueField:'name', displayField:'name'}
        ],
        buttons:['submit']
    });
    f.render('form');
});
</script>