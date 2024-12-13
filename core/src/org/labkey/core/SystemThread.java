package org.labkey.core;

// No-op class used to test classloading. See ClassLoaderTestCase
public class SystemThread extends Thread
{
    public static final class InstrumentSystemThread extends SystemThread
    {
    }
}
