<%@ page import="org.junit.Test" %>
<%@ page import="static org.junit.Assert.*" %>
<%@ page extends="org.labkey.api.jsp.JspTest.DRT" %>

<%!
    record MiniUser(String first, String last)
    {
    }

    @Test
    public void test1() throws InvocationTargetException, InstantiationException, IllegalAccessException
    {
        assertTrue(1==1);
    }

    @Test
    public void test2()
    {
        assertFalse(0==1);
    }
%>

