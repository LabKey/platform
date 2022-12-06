package org.labkey.core.admin.logger;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.PageFlowUtil;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@TestWhen(TestWhen.When.BVT)
public class LoggingTestCase extends Assert
{
    @Test
    public void testXmlFiles() throws IOException
    {
        // BioJava jars add log4j2.xml files to the class path. Tolerate them, but verify that LabKey's standard
        // log4j2.xml file is found first.
        String filename = "log4j2.xml";
        String substring = "labkeyWebapp/WEB-INF/classes/log4j2.xml"; // Our standard log4j2.xml file

        List<URL> list = Collections.list(getClass().getClassLoader().getResources(filename));
        assertFalse("Didn't find expected file: " + filename, list.isEmpty());
        String first = list.get(0).toString();
        assertTrue("Did not find substring \"" + substring + "\" in file path of the first " + filename + " file on the class path. Here's what was found: "
            + list.stream().map(URL::toString).collect(Collectors.joining(", ")), first.contains(substring));
    }

    @Test
    public void testPropertiesFiles() throws IOException
    {
        strictTest("log4j2.properties", Collections.emptyList());
    }

    @Test
    public void testOldXmlFiles() throws IOException
    {
        strictTest("log4j.xml", List.of("jxl-2.6.3.jar")); // Issue #45119
    }

    @Test
    public void testOldPropertiesFiles() throws IOException
    {
        strictTest("log4j.properties", List.of("activeio-core-3.1.0-tests.jar")); // Issue #45120
    }

    // Must find all expected substrings and no others
    private void strictTest(String filename, List<String> expectedSubstrings) throws IOException
    {
        List<URL> list = Collections.list(getClass().getClassLoader().getResources(filename));
        assertEquals("Found the wrong number of " + filename + " files on the class path: " + list.stream().map(URL::toString).collect(Collectors.joining(", ")), expectedSubstrings.size(), list.size());
        List<String> remainingSubstrings = new LinkedList<>(expectedSubstrings);
        for (URL url : list)
        {
            String urlString = url.toString();
            ListIterator<String> iter = remainingSubstrings.listIterator();
            while (iter.hasNext())
            {
                String substring = iter.next();
                if (urlString.contains(substring))
                {
                    iter.remove();
                    break;
                }
            }
        }
        assertTrue("Expected substrings not found: " + String.join(", ", remainingSubstrings), remainingSubstrings.isEmpty());
    }

    @Test
    public void testNoJxlLogFile() throws IOException
    {
        File tomcatLib = ModuleLoader.getTomcatLib();

        if (null != tomcatLib)
        {
            File tomcat = tomcatLib.getParentFile();
            try (Stream<Path> found = Files.find(tomcat.toPath(), 3, (path, basicFileAttributes) -> path.endsWith("jxl.log")))
            {
                String allFound = found.map(p ->
                {
                    StringBuilder result = new StringBuilder(p.toString());
                    try
                    {
                        result.append(", modified ").append(Files.getLastModifiedTime(p)).append(", size ").append(Files.size(p)).append(", content:");
                        try (BufferedReader r = new BufferedReader(Files.newBufferedReader(p)))
                        {
                            int lineNum = 1;
                            String line;
                            while ((line = r.readLine()) != null && lineNum <= 4)
                            {
                                lineNum++;
                                result.append("\n").append(line);
                            }
                        }
                    }
                    catch (IOException ignored) {}
                    return result.toString();
                }).collect(Collectors.joining(", "));
                assertTrue("Found jxl.log file(s) in Tomcat folder; this likely means that log4j 1.2 is present! Files found: " + allFound, allFound.isEmpty());
            }
        }
    }
}
