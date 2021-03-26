package org.labkey.api.exp.xar;

import org.labkey.api.data.Container;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

public interface XarReaderDelegate
{
    String getXarDelegateName();

    void postProcessImportedRun(Container container, User user, ExpRun run) throws ExperimentException, ValidationException;
}
