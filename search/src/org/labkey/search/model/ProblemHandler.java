package org.labkey.search.model;

import org.apache.log4j.Logger;
import org.apache.tika.config.InitializableProblemHandler;

public class ProblemHandler implements InitializableProblemHandler
{
    private final Logger LOG = Logger.getLogger(LuceneSearchServiceImpl.class);

    @Override
    public void handleInitializableProblem(String className, String message)
    {
        if (!message.contains("jai-image-io"))
            LOG.warn(message);
    }
}
