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
    public void testWindowsFilePaths() throws Exception
    {
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.startsWith("windows"))
        {
            // same file from different roots, and different files same root
            FileLike a = new FileSystemLike.Builder(new File("\\a\\")).root();
            FileLike c1 = a.resolveFile(Path.parse("b/c.txt"));
            assertNotNull(c1);
            FileLike b = new FileSystemLike.Builder(new File("\\a\\b\\")).root();
            FileLike c2 = b.resolveChild("c.txt");
            assertNotNull(c2);
            FileLike d1 =  b.resolveChild("d.txt");
            assertNotNull(d1);
            FileLike d2 =  b.resolveFile(Path.parse("c/d.txt"));
            assertNotNull(d2);

            FileLike tempDir = new FileSystemLike.Builder(tempPath.toFile()).root();
            assertNotNull(tempDir);
            FileLike t1 = tempDir.resolveFile(Path.parse(tempPath + "\\b\\t1.txt"));
            assertNotNull(t1);
            FileLike td1 = tempDir.resolveFile(Path.parse(tempPath + "\\b\\"));
            assertNotNull(td1);
            FileLike tc1 = td1.resolveChild("t1.txt");
            assertNotNull(tc1);
            assertEquals(t1, tc1);

            FileLike root = new FileSystemLike.Builder(new File("C:\\")).root();
            assertNotNull(root);
            FileLike r1 = root.resolveFile(Path.parse("r1.txt"));
            assertNotNull(r1);

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
        boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
        //noinspection UnnecessaryUnicodeEscape
        String TRICKY_CHARACTERS = "\u2603 ~!@$&()_+{}-=[],.#\u00E4\u00F6\u00FC\u00C5";
        Path p;
        FileLike f;

        for (char c = 32; c <= 127; c++)
        {
            if (c == '/' || c == '\\')
                continue;
            if (isWindows && -1 != "<>:\"|?*".indexOf(c))
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


    @Test
    public void testDescendant()
    {
        // Just a smoke test. See URIUtil.TestCase
        var root = new FileSystemLike.Builder(tempUri).root();
        String str = StringUtils.stripEnd(root.toNioPathForRead().toString(),"/");
        assertTrue(root.isDescendant(URI.create(str)));
        assertTrue(root.isDescendant(URI.create(str+"/")));
        assertTrue(root.isDescendant(new File(str).toURI()));
        assertTrue(root.isDescendant(new File(str).toPath().toUri()));

        assertFalse(root.isDescendant(URI.create(str+"x")));
        assertFalse(root.isDescendant(URI.create(str+"x/")));
        assertFalse(root.isDescendant(new File(str+"x").toURI()));
        assertFalse(root.isDescendant(new File(str+"x/").toPath().toUri()));
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

   void testCaching(FileSystemLike fs) throws Exception
    {
        // Modify the local fs directly and check that FS does not pickup changes immediately
        fs = fs.getCachingFileSystem();
        FileLike root = fs.getRoot();
        File ROOT = root.toNioPathForWrite().toFile();
        File DIR = new File(ROOT,"caching");
        FileUtil.deleteDir(new File(ROOT,"caching"));

        FileLike dir = root.resolveChild("caching");
        assertFalse(dir.exists());
        assertFalse(dir.isDirectory());
        DIR.mkdir();
        assertFalse(dir.exists());
        assertFalse(dir.isDirectory());
        dir.refresh();
        assertTrue(dir.exists());
        assertTrue(dir.isDirectory());
        assertTrue(dir.getChildren().isEmpty());

        File FILE = new File(DIR, "a.txt");
        FileLike file = dir.resolveChild("a.txt");
        assertFalse(file.exists());
        assertFalse(file.isFile());
        assertTrue(dir.getChildren().isEmpty());
        FILE.createNewFile();
        assertFalse(file.exists());
        assertFalse(file.isFile());
        assertTrue(dir.getChildren().isEmpty());
        file.refresh();
        assertTrue(file.exists());
        assertTrue(file.isFile());
        dir.refresh();
        assertTrue(dir.exists());
        assertTrue(dir.isDirectory());
        assertFalse(dir.getChildren().isEmpty());

        FILE.delete();
        // NOTE calling getChildren() above may or may not refresh metadata of children, so we don't know state of 'file'
        assertFalse(dir.getChildren().isEmpty());
        file.refresh();
        assertFalse(file.exists());
        assertFalse(file.isFile());
        dir.refresh();
        assertTrue(dir.exists());
        assertTrue(dir.isDirectory());
        assertTrue(dir.getChildren().isEmpty());

        DIR.delete();
        assertTrue(dir.exists());
        assertTrue(dir.isDirectory());
        dir.refresh();
        assertFalse(dir.exists());
        assertFalse(dir.isDirectory());
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
        testCaching(fs);
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
        testCaching(fs);
    }
%>