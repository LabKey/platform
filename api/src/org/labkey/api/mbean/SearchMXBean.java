package org.labkey.api.mbean;

public interface SearchMXBean
{
    boolean isCrawlerRunning();

    boolean isCrawlerBusy();

    void setCrawlerRunning(boolean enabled);

    int getIndexedDocumentCount();
}
