package org.labkey.study.query;

import org.labkey.api.data.ContainerFilter;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;

/**
 * Created by matthew on 2/11/14.
 */
public class DataspaceContainerFilter extends ContainerFilter.AllInProject
{
    public DataspaceContainerFilter(User user)
    {
        super(user);
    }
}
