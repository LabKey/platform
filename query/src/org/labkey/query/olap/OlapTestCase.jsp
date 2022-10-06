<%@ page import="org.json.old.JSONObject" %>
<%@ page import="org.junit.Test" %>
<%@ page import="org.labkey.api.security.User" %>
<%@ page import="org.labkey.api.util.JunitUtil" %>
<%@ page import="org.labkey.api.util.TestContext" %>
<%@ page import="org.labkey.api.view.ActionURL" %>
<%@ page import="static org.junit.Assert.*" %>
<%@ page import="org.labkey.api.view.ViewServlet" %>
<%@ page import="java.util.Map" %>
<%@ page import="org.apache.logging.log4j.LogManager" %>
<%@ page import="org.labkey.query.controllers.OlapController" %>
<%@ page extends="org.labkey.api.jsp.JspTest.DRT" %>

<%!

JSONObject executeJsonApi(ActionURL url, User user, String json) throws Exception
{
    Map<String,Object> headers = Map.of("Content-Type", "application/json");
    var req = ViewServlet.mockRequest("POST", url, user, headers, json);
    var res = ViewServlet.mockDispatch(req, null);
    // Oddly, BaseApiAction returns SC_BAD_REQUEST by default when an handled error is returned, even though the it is not a bad request.
    assertTrue(HttpServletResponse.SC_BAD_REQUEST == res.getStatus() || HttpServletResponse.SC_OK == res.getStatus());
    assertEquals("application/json;charset=UTF-8", res.getContentType());
    return new JSONObject(res.getContentAsString());
}

boolean canExecuteMdx(String configId) throws Exception
{
    JSONObject config = new JSONObject();
    config.put("query", "SELECT [Measures].[RowCount] ON COLUMNS, [Fact.ptid].[ptid].Members ON ROWS FROM [Facts]");
    config.put("configId", configId);
    config.put("schemaName", "OlapTest");
    JSONObject result = executeJsonApi(new ActionURL("olap","executeMdx", JunitUtil.getTestContainer()), TestContext.get().getUser(), config.toString());
    if (null != result.get("success") && Boolean.FALSE == result.getBoolean("success"))
        return false;
    if (null == result.get("cells"))
        return false;
    return true;
}

boolean canExecuteJson(String configId) throws Exception
{
    JSONObject config = new JSONObject();
    config.put("query", new JSONObject("{\"onRows\":{\"level\":\"[Fact.ptid].[ptid]\"},\"onColumns\":{\"level\":\"[Measures].[MeasuresLevel]\"},\"sliceFilter\":[],\"countFilter\":[],\"whereFilter\":[],\"showEmpty\":false,\"includeNullMemberInCount\":true}"));
    config.put("configId", configId);
    config.put("schemaName", "OlapTest");
    config.put("cubeName", "Facts");

    JSONObject result = executeJsonApi(new ActionURL("olap","jsonQuery", JunitUtil.getTestContainer()), TestContext.get().getUser(), config.toString());
    if (null != result.get("success") && Boolean.FALSE == result.getBoolean("success"))
        return false;
    if (null == result.get("cells"))
        return false;
    return true;
}

boolean canExecuteCountDistinct(String configId) throws Exception
{
    JSONObject config = new JSONObject();
    config.put("query", new JSONObject("{\"countDistinctLevel\" : \"[Fact.ptid].[ptid]\", \"onRows\" : {\"level\":\"[Fact.assay].[assay]\"} }"));
    config.put("configId", configId);
    config.put("schemaName", "OlapTest");
    config.put("cubeName", "Facts");

    JSONObject result = executeJsonApi(new ActionURL("olap","countDistinctQuery", JunitUtil.getTestContainer()), TestContext.get().getUser(), config.toString());
    if (null != result.get("success") && Boolean.FALSE == result.getBoolean("success"))
        return false;
    if (null == result.get("cells"))
        return false;
    return true;
}

@Test
public void testCanExecuteMdx() throws Exception
{
    assertFalse(canExecuteMdx("query:/FactCubeNotEnabled"));
    assertFalse(canExecuteMdx("query:/FactCubeContainer"));
    assertTrue(canExecuteMdx("query:/FactCube"));

    assertFalse(canExecuteJson("query:/FactCubeNotEnabled"));
    assertFalse(canExecuteJson("query:/FactCubeContainer"));
    assertTrue(canExecuteJson("query:/FactCube"));

    assertTrue(canExecuteCountDistinct("query:/FactCubeNotEnabled"));
    assertTrue(canExecuteCountDistinct("query:/FactCubeContainer"));
    assertTrue(canExecuteCountDistinct("query:/FactCube"));
}

%>