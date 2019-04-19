package org.labkey.api.jsp;

import org.apache.log4j.Logger;
import org.labkey.api.util.Button;
import org.labkey.api.view.ActionURL;

import javax.servlet.jsp.JspWriter;
import java.io.IOException;

public class LabKeyJspWriter extends JspWriterWrapper
{
    private static final Logger LOG = Logger.getLogger(LabKeyJspWriter.class);

    public LabKeyJspWriter(JspWriter jspWriter)
    {
        super(jspWriter);
    }

    @Override
    public void print(char[] s) throws IOException
    {
        super.print(s);
    }

    @Override
    public void print(String s) throws IOException
    {
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
        super.print(obj);
    }
}
