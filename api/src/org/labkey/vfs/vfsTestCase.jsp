/*
* Copyright (c) 2015-2019 LabKey Corporation
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
<%@ page import="org.junit.After" %>
<%@ page import="org.junit.Before" %>
<%@ page import="org.junit.Test" %>
<%@ page import="java.io.File" %>
<%@ page import="java.net.URI" %>
<%@ page import="org.labkey.api.util.FileUtil" %>
<%@ page import="org.labkey.vfs.FileSystemLike" %>
<%@ page import="org.labkey.api.util.Path" %>
<%@ page import="org.labkey.vfs.FileLike" %>
<%@ page import="static org.junit.Assert.*" %>
<%@ page import="java.util.HashSet" %>
<%@ page import="org.labkey.api.util.JsonUtil" %>
<%@ page import="com.fasterxml.jackson.databind.ObjectMapper" %>
<%@ page import="java.io.ByteArrayOutputStream" %>
<%@ page import="java.io.ByteArrayInputStream" %>
<%@ page import="org.apache.commons.lang3.StringUtils" %>


<%@ page extends="org.labkey.api.jsp.JspTest.BVT" %>
<%!
    java.nio.file.Path tempPath;
    URI tempUri;

    @Before
    public void setUp() throws Exception
    {
        tempPath = FileUtil.createTempDirectory("junit");
        tempUri = tempPath.toUri();
    }

    @After
    public void tearDown() throws Exception
    {
        FileUtil.deleteDir(tempPath);
    }


    @Test
    public void testSet() throws Exception
    {
        // we put files in maps and sets, test that this works for _FileObject

        // same file from different roots, and different files same root
        FileLike a = new FileSystemLike.Builder(new File("/a/")).root();
        FileLike c1 = a.resolveFile(Path.parse("b/c.txt"));
        assertNotNull(c1);
        FileLike b = new FileSystemLike.Builder(new File("/a/b/")).root();
        FileLike c2 = b.resolveChild("c.txt");
        assertNotNull(c2);
        FileLike d1 =  b.resolveChild("d.txt");
        assertNotNull(d1);
        FileLike d2 =  b.resolveFile(Path.parse("c/d.txt"));
        assertNotNull(d2);

        assertEquals(c1, c2);
        assertEquals(0, c1.compareTo(c2));
        assertEquals(c1.hashCode(), c2.hashCode());
        assertNotEquals(d1, d2);

        HashSet<FileLike> s = new HashSet<>();
        s.add(c1);
        assertTrue(s.contains(c2));
        s.add(d1);
        assertTrue(s.contains(d1));
        assertFalse(s.contains(d2));
    }


    void testChars(FileSystemLike fs)
    {
        //noinspection UnnecessaryUnicodeEscape
        String TRICKY_CHARACTERS = "\u2603 ~!@$&()_+{}-=[],.#\u00E4\u00F6\u00FC\u00C5";
        Path p;
        FileLike f;

        for (char c = 32; c <= 127; c++)
        {
            if (c == '/' || c == '\\')
                continue;
            String s = "_" + c + "_";
            p = new Path(s,"%.txt");
            f = fs.resolveFile(p);
            assertEquals(s, f.getPath().get(0));
            assertEquals("%.txt", f.getPath().get(1));

            try
            {
                s = "/" + c + ".txt";
                var ioFile = new java.io.File(s);
                f = new FileSystemLike.Builder(ioFile).root();
                assertTrue(StringUtils.replaceChars(f.toNioPathForRead().toString(),'\\','/').endsWith(s));
                assertNotNull(f.toURI());
            }
            catch (Exception e)
            {
                fail("BAD CHAR " + c);
            }
        }

        f = fs.resolveFile(new Path(TRICKY_CHARACTERS));
        assertEquals(TRICKY_CHARACTERS, f.getPath().get(0));
    }

    void serialize(FileSystemLike fs) throws Exception
    {
        ObjectMapper m = JsonUtil.DEFAULT_MAPPER;

        var root = fs.getRoot();
        var bos = new ByteArrayOutputStream();
        m.writeValue(bos, root);
        var root2 = m.readValue(new ByteArrayInputStream(bos.toByteArray()), FileLike.class);
        assertNotNull(root2);
        assertEquals(root, root2);

        var file = root.resolveFile(Path.parse("/a/b/c.txt"));
        bos = new ByteArrayOutputStream();
        m.writeValue(bos, file);
        var file2 = m.readValue(new ByteArrayInputStream(bos.toByteArray()), FileLike.class);
        assertNotNull(file2);
        assertEquals(file, file2);
    }


    void resolve(FileSystemLike fs)
    {

    }

    void list(FileSystemLike fs)
    {

    }

    void createFile(FileSystemLike fs)
    {

    }

    void mkdir(FileSystemLike fs)
    {

    }

    @Test
    public void vfs() throws Exception
    {
        var fs = new FileSystemLike.Builder(tempUri).vfs().build();
        testChars(fs);
        resolve(fs);
        list(fs);
        createFile(fs);
        mkdir(fs);
        serialize(fs);
    }

    @Test
    public void nio() throws Exception
    {
        var fs = new FileSystemLike.Builder(tempUri).build();
        testChars(fs);
        resolve(fs);
        list(fs);
        createFile(fs);
        mkdir(fs);
        serialize(fs);
    }
%>