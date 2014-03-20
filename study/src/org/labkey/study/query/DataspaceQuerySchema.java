package org.labkey.study.query;

import org.labkey.api.data.ContainerFilter;
import org.labkey.api.security.User;
import org.labkey.study.model.StudyImpl;

/**
 * Created by matthew on 2/11/14.
 *
 * Don't really need a subclass, but this helps isolate the exact difference between a regular study schema, and
 * a dataspace study schema.
 */

public class DataspaceQuerySchema extends StudyQuerySchema
{
    public DataspaceQuerySchema(StudyImpl study, User user, boolean mustCheckPermissions)
    {
        super(study, user, mustCheckPermissions);
    }

    /* for tables that support container filter, should they turn on support or not */
    @Override
    public boolean allowSetContainerFilter()
    {
        return false;
    }


    @Override
    ContainerFilter getDefaultContainerFilter()
    {
        return new DataspaceContainerFilter(getUser());
    }


    @Override
    public boolean isDataspace()
    {
        return true;
    }
}
