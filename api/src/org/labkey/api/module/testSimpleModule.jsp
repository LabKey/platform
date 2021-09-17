<%@ page import="org.junit.Test" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.module.SimpleModule" %>
<%@ page extends="org.labkey.api.jsp.JspTest.DRT" %>
<%@ page import="static org.junit.Assert.*" %>
<%@ page import="org.labkey.api.util.GUID" %>
<%@ page import="java.util.Map" %>

<%!
    // https://docs.oracle.com/javase/7/docs/api/java/nio/file/FileSystem.html#getPathMatcher(java.lang.String)

    @Test
    public void testGlob()
    {
        var m = new SimpleModule();
        var a = new Container(null,  "a", GUID.makeGUID(), 1, 1, null, 0, false);
        var b = new Container(   a,  "b", GUID.makeGUID(), 1, 1, null, 0, false);
        var bx= new Container(   a, "bx", GUID.makeGUID(), 1, 1, null, 0, false);
        var c1= new Container(   b, "c1", GUID.makeGUID(), 1, 1, null, 0, false);
        var c2= new Container(   b, "c2", GUID.makeGUID(), 1, 1, null, 0, false);
        var d1= new Container(  c1, "d1", GUID.makeGUID(), 1, 1, null, 0, false);
        var d2= new Container(  c2, "d2", GUID.makeGUID(), 1, 1, null, 0, false);

        assertTrue("canBeEnabled for " + a.getPath(), m.canBeEnabled(a));
        assertTrue("canBeEnabled for " + b.getPath(), m.canBeEnabled(b));
        assertTrue("canBeEnabled for " + c1.getPath(), m.canBeEnabled(c1));
        assertTrue("canBeEnabled for " + d2.getPath(), m.canBeEnabled(d2));

        // match /a only
        assertCanBeEnabled("glob:/a", m, Map.of(
                a, true,
                b, false,
                c1, false,
                d2, false));

        // direct subfolder of /a
        assertCanBeEnabled("glob:/a/*", m, Map.of(
                a, false,
                b, true,
                c1, false,
                d2, false));

        // match any subfolder of a (recursively)
        assertCanBeEnabled("glob:/a/**", m, Map.of(
                a, false,
                b, true,
                c1, true,
                d2, true));

        // match /a/b or any subfolders
        assertCanBeEnabled("regex:(/a/b)|(/a/b/.*)", m, Map.of(
                a, false,
                b, true,
                bx, false,
                c1, true,
                d2, true));

        // match /a/b or any subfolders
        assertCanBeEnabled("regex:/a/b($|/).*", m, Map.of(
                a, false,
                b, true,
                bx, false,
                c1, true,
                d2, true));

        // no matches
        assertCanBeEnabled("glob:/x/**", m, Map.of(
                a, false,
                b, false,
                c1, false,
                d2, false));

        assertCanBeEnabled("regex:.*c.*", m, Map.of(
                a, false,
                b, false,
                c1, true,
                c2, true,
                d1, true,
                d2, true));
    }

    private void assertCanBeEnabled(String pattern, SimpleModule m, Map<Container, Boolean> containers)
    {
        m.setFolderPathPattern(pattern);
        for (Container c : containers.keySet())
        {
            Boolean expectedCanBeEnabled = containers.get(c);
            assertEquals(String.format("SimpleModule.canBeEnabled: folderPathPattern=\"%s\" Container=%s", pattern, c.getPath()),
                    expectedCanBeEnabled,
                    m.canBeEnabled(c));
        }
    }
 %>
