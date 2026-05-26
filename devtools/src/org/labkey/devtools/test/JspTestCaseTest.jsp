<%@ page import="org.junit.Test" %>
<%@ page import="static org.junit.Assert.*" %>
<%@ page import="static org.junit.Assert.assertNotEquals" %>
<%@ page import="static org.junit.Assert.assertEquals" %>
<%@ page extends="org.labkey.api.jsp.JspTest.DRT" %>

<%!
    @Test
    public void test1()
    {
        assertEquals(1, 1);
    }

    @Test
    public void test2()
    {
        assertNotEquals(0, 1);
    }
%>

