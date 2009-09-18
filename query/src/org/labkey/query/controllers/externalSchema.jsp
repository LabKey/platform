<%@ page import="org.labkey.api.data.CoreSchema" %>
<%@ page import="org.labkey.api.data.DbScope" %>
<%@ page import="org.labkey.api.view.HttpView" %>
<%@ page import="org.labkey.query.controllers.QueryControllerSpring" %>
<%
    QueryControllerSpring.ExternalSchemaBean bean = (QueryControllerSpring.ExternalSchemaBean)HttpView.currentModel();
%>

<div id="form"></div>

<script type="text/javascript">
    // TODO: js encode strings

    Ext.QuickTips.init();

    var dataSources = [<%
    DbScope coreScope = CoreSchema.getInstance().getSchema().getScope();    // TODO: Get currently selected data source from form
    int coreIndex = 0;
    int i = 0;
    String sep = "";

    for (DbScope scope : bean.getScopes())
    {
        out.print(sep);
        out.print("[");
        out.print("'" + scope.getJndiName() + "', ");
        out.print("'" + getDisplayName(scope.getJndiName()) + "', [");

        String sep2 = "";

        for (String schemaName : bean.getSchemaNames(scope))
        {
            out.print(sep2);
            out.print("'" + schemaName + "'");
            sep2 = ",";
        }

        out.print("]]");

        if (scope == coreScope)
            coreIndex = i;

        sep = ",";
        i++;
    }
%>];

    var store = new Ext.data.SimpleStore({
    fields:['value', 'name', 'schemas'],
    data:dataSources
});

//var values={dbSchemaName:'guest'};

var f = new LABKEY.ext.FormPanel({
//        values:values,
        border:false,
        standardSubmit:true,
        items:[
            // Admin can only choose from the data sources in the drop down.  Selecting a data source updates the schemas drop down below.
            dataSourceCombo = new Ext.form.ComboBox({fieldLabel:'Data Source', mode:'local', store:store, valueField:'value', displayField:'name', hiddenName:'dataSource', editable:false, triggerAction:'all', value:dataSources[<%=coreIndex%>][0]}),
            // Admin can choose one of the schemas listed or type in their own (e.g., admin might want to use a system schema that we're filtering out). 
            dbSchemaCombo = new Ext.form.ComboBox({name:'dbSchemaName', fieldLabel:'Database Schema Name', xtype:'combo', store:dataSources[<%=coreIndex%>][2], editable:true, triggerAction:'all'}),
            {name:'userSchemaName', fieldLabel:'Schema Name', xtype:'textfield'},
            {name:'editable', fieldLabel:'Editable', xtype:'checkbox'},
            {name:'metaData', fieldLabel:'Meta Data', xtype:'textarea'}
        ],
        buttons:[{text:'Create', type:'submit', handler:function() {f.getForm().submit();}}, {text:'Cancel'}], // TODO: Cancel URL
        buttonAlign:'left'
    });

Ext.onReady(function()
{
    dataSourceCombo.on('select', dataSourceCombo_onSelect);

    f.render('form');
});

// Replace the "Database Schema Name" combobox with a new one containing the new data source's schemas
// TODO: More efficient way to do this?
function dataSourceCombo_onSelect()
{
    var dataSourceIndex = store.find("value", dataSourceCombo.value);
    f.remove(dbSchemaCombo);
    dbSchemaCombo = new Ext.form.ComboBox({name:'dbSchemaName', fieldLabel:'Database Schema Name', xtype:'combo', store:dataSources[dataSourceIndex][2], editable:false, triggerAction:'all'});
    f.insert(1, dbSchemaCombo);
    f.doLayout();
}
</script>
<%!
    // Strip off "jdbc/" and "DataSource" to create friendly name.  TODO: Add admin UI to allow setting this for each data source.
    private String getDisplayName(String jndiName)
    {
        if (jndiName.startsWith("jdbc/"))
            jndiName = jndiName.substring(5);

        if (jndiName.endsWith("DataSource"))
            jndiName = jndiName.substring(0, jndiName.length() - 10);

        return jndiName;
    }
%>