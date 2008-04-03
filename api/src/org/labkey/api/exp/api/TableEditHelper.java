package org.labkey.api.exp.api;

import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;
import org.labkey.api.query.QueryUpdateForm;

abstract public class TableEditHelper
{
    abstract public boolean hasPermission(User user, int perm);
    abstract public ActionURL delete(User user, ActionURL srcURL, QueryUpdateForm form) throws Exception;
}
