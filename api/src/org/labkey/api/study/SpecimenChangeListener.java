package org.labkey.api.study;

import org.apache.log4j.Logger;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

/**
 * User: kevink
 * Date: 6/29/14
 */
public interface SpecimenChangeListener
{
    /**
     * Fired when specimens in the given container have been added, updated, or removed.
     */
    void specimensChanged(Container c, User user, Logger logger);

}
