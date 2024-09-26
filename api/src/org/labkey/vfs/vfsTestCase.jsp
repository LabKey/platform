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
<%@ page import="java.net.URI" %>
<%@ page import="org.labkey.api.util.FileUtil" %>
<%@ page import="org.labkey.vfs.FileSystemLike" %>
<%@ page import="org.labkey.api.util.Path" %>
<%@ page import="org.labkey.vfs.FileLike" %>
<%@ page import="static org.junit.Assert.*" %>


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


    void testChars(FileSystemLike fs)
    {
        //noinspection UnnecessaryUnicodeEscape
        String TRICKY_CHARACTERS = "\u2603 ~!@$&()_+{}-=[],.#\u00E4\u00F6\u00FC\u00C5";
        Path p;
        FileLike f;

        for (char c = 32; c <= 127; c++)
        {
            if (c == '/')
                continue;
            String s = "_" + c;
            p = new Path(s,"%.txt");
            f = fs.resolveFile(p);
            assertEquals(s, f.getPath().get(0));
            assertEquals("%.txt", f.getPath().get(1));

            try
            {
                s = "/" + c + ".txt";
                var ioFile = new java.io.File(s);
                f = new FileSystemLike.Builder(ioFile).root();
                assertEquals(s, f.toNioPathForRead().toString());
                URI uri = f.toURI();
            }
            catch (Exception e)
            {
                System.out.println("BAD CHAR " + c);
            }
        }

        f = fs.resolveFile(new Path(TRICKY_CHARACTERS));
        assertEquals(TRICKY_CHARACTERS, f.getPath().get(0));
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
    }
%>