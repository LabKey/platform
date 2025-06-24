package org.labkey.api.util;

import org.labkey.api.jsp.JspLoader;

import java.util.function.Supplier;

/*
 * NOTE: we could call JspLoader.loadClass() directly in getIntegrationTests(), however,
 * that would cause all test jsp's to be compiled at startup.
 */
public class JspTestCase implements Supplier<Class<?>>
{
    private final String jspPath;

    public JspTestCase(String path)
    {
        jspPath = path;
    }

    @Override
    public Class<?> get()
    {
        return JspLoader.loadClass(jspPath);
    }
}
