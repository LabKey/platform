package org.labkey.api.study.permissions;

import org.labkey.api.security.permissions.AbstractPermission;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jul 27, 2011
 * Time: 9:40:45 AM
 */
public class SharedParticipantGroupPermission extends AbstractPermission
{
    public SharedParticipantGroupPermission()
    {
        super("Share Participant Groups", "Allows creation/management of shared participant groups.");
    }
}
