<%@ page import="org.labkey.api.query.DefaultSchema"%>
<%@ page import="org.labkey.api.query.UserSchema"%>
<%@ page import="static org.labkey.api.query.QueryService.*" %>
<%@ page import="java.util.*" %>
<%@ page import="org.labkey.api.util.CaseInsensitiveHashMap" %>
<%@ page extends="org.labkey.query.view.EditQueryPage" %>
<%@ taglib prefix="labkey" uri="http://www.labkey.org/taglib" %>
<script type="text/javascript">
    var schemaInfos = new Object();
    var tableNames;
<%
    Map<String, String> schemaOptions = new TreeMap<String, String>();
    Map<String, Map<String, String>> schemaTableNames = new CaseInsensitiveHashMap<Map<String, String>>();
    DefaultSchema defSchema = DefaultSchema.get(getUser(), getContainer());
    for (String name : defSchema.getUserSchemaNames())
    {
        schemaOptions.put(name, name);
        UserSchema schema = get().getUserSchema(getUser(), getContainer(), name);
        Map<String, String> tableNames = new LinkedHashMap<String, String>();
        for (String tableName : new TreeSet<String>(schema.getTableAndQueryNames(true)))
        {
            tableNames.put(tableName, tableName);
        }
        schemaTableNames.put(name, tableNames);
        %>
        tableNames = new Array();
        schemaInfos['<%= name %>'] = tableNames;
        <%
        int index = 0;
        for (String tableName : tableNames.values()) {
            %>
            tableNames[<%= index++%>] = '<%= tableName %>';
            <%
        }
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
function updateQueries(schemaName)
{
    var tableNames = schemaInfos[schemaName];
    var querySelect = document.getElementById('queryName');
    querySelect.options.length = 0;
    for (var i = 0; i < tableNames.length; i++)
    {
        querySelect.options[i] = new Option(tableNames[i], tableNames[i]);
    }
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
                <select name="schemaName" onchange="updateQueries(this.value)">
                    <labkey:options value="<%=pm.get("schemaName")%>" map="<%=schemaOptions%>" />
                </select>
            </td>
        </tr>
        <tr>
            <td class="ms-searchform" rowspan="2">Query:</td>
            <td><input type="radio" name="selectQuery" value="false" <% if (!querySelected) { %> checked <% } %> onchange="document.getElementById('queryName').disabled = this.value == 'false';"/>Show the list of tables in this schema.</td>
        </tr>
        <tr>
            <td>
                <input type="radio" name="selectQuery" value="true" <% if (querySelected) { %> checked <% } %>  onchange="document.getElementById('queryName').disabled = this.value == 'false';"/>Show the contents of a specific table:
                <select name="queryName" id="queryName" <% if (!querySelected) { %> disabled="true" <% } %>>
                    <labkey:options value="<%= pm.get("queryName") %>" map="<%= schemaTableNames.get(pm.get("schemaName")) == null ? Collections.<String, String>emptyMap() : schemaTableNames.get(pm.get("schemaName")) %>" />
                </select>
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