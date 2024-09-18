package org.labkey.core.admin.logger;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.test.TestWhen;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.logging.LogHelper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.security.CodeSource;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.stream.Collectors;

@TestWhen(TestWhen.When.BVT)
public class LoggingTestCase extends Assert
{
    @Test
    public void testXmlFiles() throws IOException
    {
        // BioJava and Picard include log4j2.xml files. Verify that first log4j2.xml file found is the LabKey one.
        String filename = "log4j2.xml";
        String testString = "org.labkey"; // Reasonable bet that third-party log4j2.xml files won't include this string
        ClassLoader classLoader = getClass().getClassLoader();

        try (InputStream is = classLoader.getResourceAsStream(filename))
        {
            // log4j2.xml is not found on Dan's machine... not sure why, but we can tolerate that
            if (null == is)
            {
                LogHelper.getLogger(LoggingTestCase.class, "log4j2.xml file status").info("Did not find file \"{}\" on the class path", filename);
            }
            else
            {
                String contents = PageFlowUtil.getStreamContentsAsString(is);
                assertTrue("lo4j2.xml file contents did not match expectations. Here are the files that were found: " +
                    Collections.list(classLoader.getResources(filename)).stream()
                        .map(URL::toString)
                        .collect(Collectors.joining(", ")),
                    contents.contains(testString));
            }
        }
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
        strictTest("log4j.properties", Collections.emptyList()); // Issue #45120
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
    public void testNoLog4j()
    {
        // Ensure no dependency has pulled in log4j 1.2 implementation
        try
        {
            // This class is present in log4j 1.2 but not the 1.2-to-2.0 mapper
            Class<?> clazz = Class.forName("org.apache.log4j.AsyncAppender");
            CodeSource source = clazz.getProtectionDomain().getCodeSource();
            fail(clazz + " was found which means log4j 1.2 is present!" + (null != source ? " (" + source.getLocation() + ")" : ""));
        }
        catch (ClassNotFoundException ignored)
        {
            // Expected
        }
    }
}
