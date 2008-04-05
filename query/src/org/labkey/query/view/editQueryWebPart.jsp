<%@ page import="org.json.JSONObject"%>
<%@ page import="org.labkey.api.query.DefaultSchema"%>
<%@ page import="static org.labkey.api.query.QueryService.*" %>
<%@ page import="org.labkey.api.query.QueryDefinition" %>
<%@ page import="org.labkey.api.query.UserSchema" %>
<%@ page import="org.labkey.api.util.CaseInsensitiveHashMap" %>
<%@ page import="java.util.*" %>
<%@ page extends="org.labkey.query.view.EditQueryPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<%
    Map<String, String> schemaOptions = new TreeMap<String, String>();

    Map<String, Map<String, List<String>>> schemaTableNames = new CaseInsensitiveHashMap<Map<String, List<String>>>();
    DefaultSchema defSchema = DefaultSchema.get(getUser(), getContainer());
    for (String name : defSchema.getUserSchemaNames())
    {
        schemaOptions.put(name, name);
        UserSchema schema = get().getUserSchema(getUser(), getContainer(), name);
        Map<String, List<String>> tableNames = new CaseInsensitiveHashMap<List<String>>();
        for (String tableName : new TreeSet<String>(schema.getTableAndQueryNames(true)))
        {
            List<String> viewNames = new LinkedList<String>();
            viewNames.add(""); // default view

            QueryDefinition queryDef = schema.getQueryDefForTable(tableName);
            if (queryDef != null)
            {
                for (String viewName : queryDef.getCustomViews(getUser(), getViewContext().getRequest()).keySet())
                {
                    viewNames.add(viewName);
                }
            }

            tableNames.put(tableName, viewNames);
        }
        schemaTableNames.put(name, tableNames);
    }

    Map<String, String> pm = getWebPart().getPropertyMap();
    String strAllowChooseQuery = pm.get("allowChooseQuery");
    boolean allowChooseQuery = false;
    if (strAllowChooseQuery != null)
    {
        allowChooseQuery = Boolean.valueOf(strAllowChooseQuery);
    }
    String strAllowChooseView = pm.get("allowChooseView");
    boolean allowChooseView = true;
    if (strAllowChooseView != null)
    {
        allowChooseView = Boolean.valueOf(strAllowChooseView);
    }

    boolean querySelected = pm.get("queryName") != null && !"".equals(pm.get("queryName").trim());
%>
<script type="text/javascript">
var schemaInfos = <%= new JSONObject(schemaTableNames).toString() %>;
function updateQueries(schemaName)
{
    var tableNames = schemaInfos[schemaName];
    var querySelect = document.getElementById('queryName');

    querySelect.options.length = 0;
    for (var opt in tableNames)
    {
        querySelect.options[querySelect.options.length] = new Option(opt, opt);
    }

    updateViews(querySelect.value);
}
function updateViews(queryName)
{
    var tableName = document.getElementById('schemaName').value;
    var viewNames = schemaInfos[tableName][queryName];
    var viewSelect = document.getElementById('viewName');

    viewSelect.options.length = 0;
    for (var i = 0; i < viewNames.length; i++)
    {
        var opt = viewNames[i];
        viewSelect.options[i] = new Option(opt == "" ? "<default view>" : opt, opt);
    }
}
function disableQuerySelect(disable)
{
    document.getElementById('queryName').disabled = disable;
    document.getElementById('viewName').disabled = disable;
}
</script>
<form name="frmCustomize" method="post" action="<%=h(_part.getCustomizePostURL(getContainer()))%>">
    <table>
        <tr>
            <td class="ms-searchform">Web Part Title:</td>
            <td><input type="text" name="title" size="40" value="<%=h(pm.get("title"))%>"></td>
        </tr>
        <tr>
            <td class="ms-searchform">Schema:</td>
            <td>
                <select name="schemaName" id="schemaName"
                        title="Select a Schema Name"
                        onchange="updateQueries(this.value)">
                    <labkey:options value="<%=pm.get("schemaName")%>" map="<%=schemaOptions%>" />
                </select>
            </td>
        </tr>
        <tr>
            <td class="ms-searchform" valign="top">Query and View:</td>
            <td>
                <table class="normal">
                    <tr>
                        <td><input type="radio" name="selectQuery" value="false" <% if (!querySelected) { %> checked <% } %> onchange="disableQuerySelect(this.value == 'false');"/></td>
                        <td>Show the list of tables in this schema.</td>
                    </tr>
                    <tr>
                        <td><input type="radio" name="selectQuery" value="true" <% if (querySelected) { %> checked <% } %>  onchange="disableQuerySelect(this.value == 'false');"/></td>
                        <td>Show the contents of a specific table and view.</td>
                    </tr>
                    <tr>
                        <td/>
                        <td>
                            <select name="queryName" id="queryName"
                                    title="Select a Table Name" onchange="updateViews(this.value)"
                                    <% if (!querySelected) { %> disabled="true" <% } %>>
                                <%
                                Map<String, List<String>> tableNames = schemaTableNames.get(pm.get("schemaName"));
                                if (tableNames != null)
                                {
                                    for (String queryName : tableNames.keySet())
                                    {
                                        %><option value="<%=h(queryName)%>" <%=queryName.equals(pm.get("queryName")) ? "selected" : ""%>><%=h(queryName)%></option><%
                                    }
                                }
                                %>
                            </select>
                            <br/>
                            <select name="viewName" id="viewName"
                                    title="Select a View Name"
                                    <% if (!querySelected) { %> disabled="true" <% } %>>
                                <%
                                if (tableNames != null)
                                {
                                    List<String> viewNames = tableNames.get(pm.get("queryName"));
                                    if (viewNames != null)
                                    {
                                        for (String viewName : viewNames)
                                        {
                                            String value = viewName.equals("") ? "<default view>" : viewName;
                                            %><option value="<%=h(viewName)%>" <%=viewName.equals(pm.get("viewName")) ? "selected" : ""%>><%=h(value)%></option><%
                                        }
                                    }
                                }
                                %>
                            </select>
                        </td>
                    </tr>
                </table>
            </td>
        </tr>
        <tr>
            <td class="ms-searchform">Allow user to choose query?</td>
            <td>
                <select name="allowChooseQuery">
                    <option value="true"<%=allowChooseQuery ? " selected" : ""%>>Yes</option>
                    <option value="false"<%=allowChooseQuery ? "" : " selected"%>>No</option>
                </select>                
            </td>
        </tr>
        <tr>
            <td class="ms-searchform">Allow user to choose view?</td>
            <td>
                <select name="allowChooseView">
                    <option value="true"<%=allowChooseView ? " selected" : ""%>>Yes</option>
                    <option value="false"<%=allowChooseView ? "" : " selected"%>>No</option>
                </select>
            </td>
        </tr>
        <tr>
            <td/>
            <td><labkey:button text="Submit" /></td>
        </tr>
    </table>
</form>