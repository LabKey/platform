package org.labkey.api.exp.xar;

import org.apache.logging.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

public interface XarReaderDelegate
{
    String getXarDelegateName();

    void postProcessImportedProtocol(Container container, User user, ExpProtocol experiment, Logger logger) throws ExperimentException, ValidationException;
    void postProcessImportedRun(Container container, User user, ExpRun run, Logger logger) throws ExperimentException, ValidationException;
}
