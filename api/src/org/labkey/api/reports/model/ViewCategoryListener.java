package org.labkey.api.reports.model;

import org.labkey.api.security.User;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Oct 18, 2011
 * Time: 5:00:58 PM
 */
public interface ViewCategoryListener
{
    void categoryDeleted(User user, ViewCategory category) throws Exception;
    void categoryCreated(User user, ViewCategory category) throws Exception;
    void categoryUpdated(User user, ViewCategory category) throws Exception;
}
