package org.labkey.api.util;

import org.apache.commons.collections4.Factory;
import org.labkey.api.jsp.JspLoader;

/*
 * NOTE: we could call JspLoader.loadClass() directly in getIntegrationTests(), however,
 * that would cause all test jsp's to be compiled at startup.
 */
public class JspTestCase implements Factory<Class>
{
    private final String jspPath;

    public JspTestCase(String path)
    {
        jspPath = path;
    }

    @Override
    public Class create()
    {
        return JspLoader.loadClass(jspPath);
    }
}
