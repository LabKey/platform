package org.labkey.study.requirements;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.study.model.Site;

import java.sql.SQLException;

/**
 * User: brittp
 * Date: Jun 4, 2007
 * Time: 2:56:54 PM
 */
public interface RequirementActor<A extends RequirementActor>
{
    Object getPrimaryKey();

    Container getContainer();

    String getGroupName();

    void addMembers(User... users);

    void addMembers(Site site, User... users);

    User[] getMembers();

    User[] getMembers(Site site);

    void removeMembers(User... members);

    void removeMembers(Site site, User... members);

    void deleteAllGroups();

    A create(User user);

    A update(User user);

    void delete();
}