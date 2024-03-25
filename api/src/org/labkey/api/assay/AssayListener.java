package org.labkey.api.assay;

import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.security.User;

import java.util.Map;

public interface AssayListener
{
    default void beforeResultDelete(Container container, User user, ExpRun run, Map<String, Object> resultRow) { }
}
