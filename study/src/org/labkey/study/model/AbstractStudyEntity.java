package org.labkey.study.model;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;

import java.io.Serializable;

/**
 * User: brittp
 * Date: Jan 17, 2006
 * Time: 2:48:41 PM
 */
public abstract class AbstractStudyEntity<T>
        extends AbstractStudyCachable<T> implements StudyEntity, Serializable
{
    transient private Container _container;
    private String _containerId;
    protected String _entityId;
    protected String _label;
    protected boolean _showByDefault = true;
    protected int _displayOrder;


    public AbstractStudyEntity()
    {
        super();
    }
    
    public AbstractStudyEntity(Container c)
    {
        super();
        setContainer(c);
    }

    public Container getContainer()
    {
        if (_container == null && _containerId != null)
            _container = ContainerManager.getForId(_containerId);
        return _container;
    }

    public void setContainer(Container container)
    {
        verifyMutability();
        _container = container;
        _containerId = container == null ? null : container.getId();
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        verifyMutability();
        _label = label;
    }

    public int getDisplayOrder()
    {
        return _displayOrder;
    }

    public void setDisplayOrder(int displayOrder)
    {
        verifyMutability();
        _displayOrder = displayOrder;
    }

    public boolean isShowByDefault()
    {
        return _showByDefault;
    }

    public void setShowByDefault(boolean showByDefault)
    {
        verifyMutability();
        _showByDefault = showByDefault;
    }

    public String getDisplayString()
    {
        StringBuilder builder = new StringBuilder();
        builder.append(getPrimaryKey());
        if (getLabel() != null)
            builder.append(": ").append(getLabel());
        return builder.toString();
    }

    public String getEntityId()
    {
        return _entityId;
    }

    public void setEntityId(String entityId)
    {
        _entityId = entityId;
    }

    public ACL getACL()
    {
        final Study study = StudyManager.getInstance().getStudy(getContainer());
        if (study != null && study.isStudySecurity())
            return org.labkey.api.security.SecurityManager.getACL(getContainer(), _entityId);
        else
            return getContainer().getAcl();
    }

    public void updateACL(ACL acl)
    {
        if (!supportsACLUpdate())
            throw new IllegalArgumentException("unexpected class " + this.getClass().getName());
        org.labkey.api.security.SecurityManager.updateACL(getContainer(), getEntityId(), acl);
    }

    protected boolean supportsACLUpdate()
    {
        return false;
    }

    public int getPermissions(User u)
    {
        return getACL().getPermissions(u);
    }
}
