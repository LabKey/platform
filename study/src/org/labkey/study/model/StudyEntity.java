package org.labkey.study.model;

import org.labkey.api.data.Container;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;

/**
 * Created by IntelliJ IDEA.
 * User: Matthew
 * Date: Jun 6, 2006
 * Time: 2:57:59 PM
 */
public interface StudyEntity
{
    Container getContainer();

    void setContainer(Container container);

    String getLabel();

    void setLabel(String label);

    int getDisplayOrder();

    void setDisplayOrder(int displayOrder);

    boolean isShowByDefault();

    void setShowByDefault(boolean showByDefault);

    String getDisplayString();

    String getEntityId();

    void setEntityId(String entityId);

    ACL getACL();

    /** it's a little odd that updateACL is separate feom save(), however it does seem to
     * fit the actual usage pattern. */
    void updateACL(ACL acl);

    int getPermissions(User u);
}
