<div id="searchDiv">
    
</div>

<script>
var searchField;
var store;

Ext.onReady(function(){
    searchField = new Ext.form.TextField({id:'searchField', fieldLabel:'Search for', width:400});
    var form = new Ext.form.FormPanel({
        border : false,
        items:[searchField]
    });
    searchField.on("change",function(){search(searchField.getValue());});
    form.render('searchDiv');

    store = new Ext.data.JsonStore({
        url: 'json.view', root:'hits', fields:['id','title','summary','url']
    });
    store.on("load",load);
    var model = new Ext.grid.ColumnModel({
        defaults: {width:190, renderer:Ext.util.Format.htmlEncode},
        columns:[{id:'id', width:20},{id:'title', header:'Title'},{id:'summary', header:'Summary'},{id:'url',header:'URL'}]
    });
    var grid = new Ext.grid.GridPanel({
        store: store,
        colModel : model,
        width: 600,
        height: 400
    });
    grid.render('searchDiv');
});

function search(q)
{
    if (!q)
        return;
    store.reload({params:{q:q}});
}

function load(store,records,options)
{
    console.dir(store.reader.jsonData);
    console.dir(options);
}

</script>