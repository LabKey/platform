/*
 * Copyright (c) 2006-2008 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
