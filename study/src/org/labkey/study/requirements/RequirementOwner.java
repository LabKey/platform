package org.labkey.study.requirements;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

/**
 * User: brittp
 * Date: Jun 4, 2007
 * Time: 2:31:59 PM
 */
public interface RequirementOwner
{
    String getEntityId();

    Container getContainer();
}
