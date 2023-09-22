package org.labkey.api.mbean;

import java.io.IOException;

public interface SearchMXBean
{
    /** @return is the crawler enabled */
    boolean isRunning();

    /** @return is the crawler working on something right now - an indicator that there are a lot of things in the queue */
    boolean isBusy();

    int getNumDocs() throws IOException;
}
