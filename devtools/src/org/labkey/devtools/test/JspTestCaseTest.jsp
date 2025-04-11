<%@ page import="org.junit.Test" %>
<%@ page import="static org.junit.Assert.*" %>
<%@ page import="org.labkey.api.module.ModuleLoader" %>
<%@ page import="org.labkey.api.module.SupportedDatabase" %>
<%@ page import="java.util.Objects" %>
<%@ page extends="org.labkey.api.jsp.JspTest.DRT" %>

<%!
    @Test
    public void test1()
    {
        ModuleLoader.getInstance().getModules().stream()
            .filter(m -> !m.getSupportedDatabasesSet().contains(SupportedDatabase.mssql))
            .map(m -> m.getModuleResource("schemas/dbscripts/sqlserver/"))
            .filter(Objects::nonNull)
            .forEach(r -> System.out.println(r));
    }

    @Test
    public void test2()
    {
        assertFalse(0==1);
    }
%>

