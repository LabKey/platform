package org.labkey.search.model;

public enum CrawlerRunningState
{
    Pause
    {
        @Override
        String getAuditMessage()
        {
            return "Crawler Paused";
        }

        @Override
        boolean isRunning()
        {
            return false;
        }
    },
    Start
    {
        @Override
        String getAuditMessage()
        {
            return "Crawler Started";
        }

        @Override
        boolean isRunning()
        {
            return true;
        }
    };

    abstract String getAuditMessage();
    abstract boolean isRunning();
}
