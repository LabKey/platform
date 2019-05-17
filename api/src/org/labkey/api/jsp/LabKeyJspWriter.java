package org.labkey.api.jsp;

import org.apache.log4j.Logger;
import org.labkey.api.collections.ConcurrentHashSet;

import javax.servlet.jsp.JspWriter;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

public class LabKeyJspWriter extends JspWriterWrapper
{
    private static final Logger LOG = Logger.getLogger(LabKeyJspWriter.class);

    private static final AtomicInteger CHAR_ARRAY_INVOCATIONS = new AtomicInteger();
    private static final AtomicInteger STRING_INVOCATIONS = new AtomicInteger();
    private static final AtomicInteger OBJECT_INVOCATIONS = new AtomicInteger();
    private static final Set<String> UNIQUE_CHAR_ARRAY_INVOCATIONS = new ConcurrentHashSet<>();
    private static final Set<String> UNIQUE_STRING_INVOCATIONS = new ConcurrentHashSet<>();
    private static final Set<String> UNIQUE_OBJECT_INVOCATIONS = new ConcurrentHashSet<>();

    LabKeyJspWriter(JspWriter jspWriter)
    {
        super(jspWriter);
    }

    @Override
    public void print(char[] s) throws IOException
    {
        CHAR_ARRAY_INVOCATIONS.incrementAndGet();
        UNIQUE_CHAR_ARRAY_INVOCATIONS.add(Thread.currentThread().getStackTrace()[2].toString());
        super.print(s);
    }

    @Override
    public void print(String s) throws IOException
    {
        STRING_INVOCATIONS.incrementAndGet();
        UNIQUE_STRING_INVOCATIONS.add(Thread.currentThread().getStackTrace()[2].toString());
        super.print(s);
    }

    @Override
    public void print(Object obj) throws IOException
    {
//        if
//        (
//            !(obj instanceof Number) &&
//            !"org.labkey.api.jsp.JspBase$_HtmlString".equals(obj.getClass().getName()) &&
//            !(obj instanceof Button.ButtonBuilder) &&  // Ignore for now
//            !(obj instanceof ActionURL)                // Ignore for now
//        )
//            LOG.warn("I don't like this object: " + obj);
//
        OBJECT_INVOCATIONS.incrementAndGet();
        UNIQUE_OBJECT_INVOCATIONS.add(Thread.currentThread().getStackTrace()[2].toString());
        super.print(obj);
    }

    public static void logStatistics()
    {
        LOG.info("print(char[]) invocations: " + CHAR_ARRAY_INVOCATIONS);
        LOG.info("print(String) invocations: " + STRING_INVOCATIONS);
        LOG.info("print(Object) invocations: " + OBJECT_INVOCATIONS);

        LOG.info("Unique code points that invoke print(char[]): " + UNIQUE_CHAR_ARRAY_INVOCATIONS.size());
        LOG.info("Unique code points that invoke print(String): " + UNIQUE_STRING_INVOCATIONS.size());
        LOG.info("Unique code points that invoke print(Object): " + UNIQUE_OBJECT_INVOCATIONS.size());
    }
}
