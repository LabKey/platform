package org.labkey.core;

import org.junit.Assert;
import org.junit.Test;

// Regression test for Issue #51784. When running Tomcat 10.1.33 along with the Datadog Java agent, attempting to load
// by name a class like InstrumentSystemThread failed with a class loading exception.
public class ClassLoaderTestCase extends Assert
{
    @Test
    public void testLoadingInnerClassByName() throws ClassNotFoundException
    {
        Class.forName("org.labkey.core.SystemThread$InstrumentSystemThread");
    }
}
