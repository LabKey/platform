<%@ page import="org.junit.Test" %>
<%@ page import="org.labkey.api.data.Container" %>
<%@ page import="org.labkey.api.module.SimpleModule" %>
<%@ page import="org.labkey.api.util.GUID" %>
<%@ page import="static junit.framework.TestCase.assertTrue" %>
<%@ page extends="org.labkey.api.jsp.JspTest.DRT" %>
<%@ page import="static org.junit.Assert.*" %>

<%!
    // https://docs.oracle.com/javase/7/docs/api/java/nio/file/FileSystem.html#getPathMatcher(java.lang.String)

    @Test
    public void testGlob()
    {
        var m = new SimpleModule();
        var a = new Container(null,  "a", GUID.makeGUID(), 1, 1, null, 0, false);
        var b = new Container(   a,  "b", GUID.makeGUID(), 1, 1, null, 0, false);
        var bx = new Container(  a,  "bx", GUID.makeGUID(), 1, 1, null, 0, false);
        var c1= new Container(   b, "c1", GUID.makeGUID(), 1, 1, null, 0, false);
        var c2= new Container(   b, "c2", GUID.makeGUID(), 1, 1, null, 0, false);
        var d1= new Container(  c1, "d1", GUID.makeGUID(), 1, 1, null, 0, false);
        var d2= new Container(  c2, "d2", GUID.makeGUID(), 1, 1, null, 0, false);

        assertTrue(m.canBeEnabled(a));
        assertTrue(m.canBeEnabled(b));
        assertTrue(m.canBeEnabled(c1));
        assertTrue(m.canBeEnabled(d2));

        // match /a only
        m.setFolderPathPattern("glob:/a");
        assertTrue(m.canBeEnabled(a));
        assertFalse(m.canBeEnabled(b));
        assertFalse(m.canBeEnabled(c1));
        assertFalse(m.canBeEnabled(d2));

        // direct subfolder of /a
        m.setFolderPathPattern("glob:/a/*");
        assertFalse(m.canBeEnabled(a));
        assertTrue(m.canBeEnabled(b));
        assertFalse(m.canBeEnabled(c1));
        assertFalse(m.canBeEnabled(d2));

        // match any subfolder of a (recursively)
        m.setFolderPathPattern("glob:/a/**");
        assertFalse(m.canBeEnabled(a));
        assertTrue(m.canBeEnabled(b));
        assertTrue(m.canBeEnabled(c1));
        assertTrue(m.canBeEnabled(d2));

        // match /a/b or any subfolders
        m.setFolderPathPattern("regex:(/a/b)|(/a/b/.*)");
        assertFalse(m.canBeEnabled(a));
        assertTrue(m.canBeEnabled(b));
        assertFalse(m.canBeEnabled(bx));
        assertTrue(m.canBeEnabled(c1));
        assertTrue(m.canBeEnabled(d2));

        // match /a/b or any subfolders
        m.setFolderPathPattern("regex:/a/b($|/).*");
        assertFalse(m.canBeEnabled(a));
        assertTrue(m.canBeEnabled(b));
        assertFalse(m.canBeEnabled(bx));
        assertTrue(m.canBeEnabled(c1));
        assertTrue(m.canBeEnabled(d2));

        // no matches
        m.setFolderPathPattern("glob:/x/**");
        assertFalse(m.canBeEnabled(a));
        assertFalse(m.canBeEnabled(b));
        assertFalse(m.canBeEnabled(c1));
        assertFalse(m.canBeEnabled(d2));

        m.setFolderPathPattern("regex:.*c.*");
        assertFalse(m.canBeEnabled(a));
        assertFalse(m.canBeEnabled(b));
        assertTrue(m.canBeEnabled(c1));
        assertTrue(m.canBeEnabled(c2));
        assertTrue(m.canBeEnabled(d1));
        assertTrue(m.canBeEnabled(d2));
    }
 %>