package org.labkey.study.requirements;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.study.model.SampleRequestActor;

/**
 * User: brittp
 * Date: Jun 4, 2007
 * Time: 2:31:52 PM
 */
public interface Requirement<R extends Requirement>
{
    String getOwnerEntityId();

    void setOwnerEntityId(String entityId);

    Container getContainer();

    Object getActorPrimaryKey();

    boolean isComplete();

    R update(User user);

    R createMutable();

    boolean isEqual(R requirement);

    void delete();

    R persist(User user, String ownerEntityId);
}
