/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Transient;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.RoleAssignment;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.study.StudyEntity;
import org.labkey.study.StudyModule;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

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

    @NotNull
    public String getResourceId()
    {
        return _entityId;
    }

    public String getEntityId()
    {
        return _entityId;
    }

    public void setEntityId(String entityId)
    {
        _entityId = entityId;
    }


    @Transient
    public SecurityPolicy getPolicy()
    {
        final StudyImpl study = StudyManager.getInstance().getStudy(getContainer());
        if (study != null &&
            (study.getSecurityType() == SecurityType.ADVANCED_READ ||
             study.getSecurityType() == SecurityType.ADVANCED_WRITE))
        {
            return SecurityPolicyManager.getPolicy(this);
        }
        else
        {
            return SecurityPolicyManager.getPolicy(getContainer());
        }
    }

    protected String getPolicyChangeSummary(MutableSecurityPolicy policy, SecurityPolicy existingPolicy, String baseDescription, String removalDescription, String additionDescription)
    {
        StringBuilder sb = new StringBuilder(baseDescription);

        List<RoleAssignment> removedAssignments = new ArrayList<>(existingPolicy.getAssignments());
        removedAssignments.removeAll(policy.getAssignments());
        sb.append(appendRoleDescriptions(removalDescription, removedAssignments));

        List<RoleAssignment> addedAssignments = new ArrayList<>(policy.getAssignments());
        addedAssignments.removeAll(existingPolicy.getAssignments());
        sb.append(appendRoleDescriptions(additionDescription, addedAssignments));

        return sb.toString();
    }

    /** Useful for building up audit event descriptions */
    private String appendRoleDescriptions(String action, Collection<RoleAssignment> assignments)
    {
        StringBuilder sb = new StringBuilder();
        if (!assignments.isEmpty())
        {
            sb.append(" ");
            sb.append(action);
            sb.append(": ");
            String separator = "";
            for (RoleAssignment removedAssignment : assignments)
            {
                sb.append(separator);
                separator = ", ";
                UserPrincipal removedPrincipal = SecurityManager.getPrincipal(removedAssignment.getUserId());
                sb.append(removedPrincipal == null ? "<deleted principal - #" + removedAssignment.getUserId() + ">" : removedPrincipal.getName());
                sb.append(" - ");
                sb.append(removedAssignment.getRole().getName());
            }
            sb.append(".");
        }
        return sb.toString();
    }

    public void savePolicy(MutableSecurityPolicy policy, User user)
    {
        if (!supportsPolicyUpdate())
            throw new IllegalArgumentException("unexpected class " + this.getClass().getName());
        SecurityPolicyManager.savePolicy(policy);
    }

    protected boolean supportsPolicyUpdate()
    {
        return false;
    }

    @NotNull
    public Module getSourceModule()
    {
        return ModuleLoader.getInstance().getModule(StudyModule.MODULE_NAME);
    }

    @NotNull
    public List<SecurableResource> getChildResources(User user)
    {
        //study will override to return the set of securable entities
        //for all other entities, there are no children
        return Collections.emptyList();
    }

    @NotNull
    public String getResourceName()
    {
        return getLabel();
    }

    @NotNull
    public String getResourceDescription()
    {
        return getDisplayString();
    }

    @Transient
    public SecurableResource getParentResource()
    {
        //by default all study entities are children of the study
        //will override in Study to return the container as parent
        return StudyManager.getInstance().getStudy(getContainer());
    }

    @NotNull
    public Container getResourceContainer()
    {
        return getContainer();
    }

    public boolean mayInheritPolicy()
    {
        return true;
    }
}
