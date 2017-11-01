/*
 * Copyright (c) 2015-2017 LabKey Corporation
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
package org.labkey.api.attachments;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.Role;

import java.util.Collections;
import java.util.List;

/**
 * This was used originally by the Argos export request workflow. Currently not used, except by junit tests. If resurrected,
 * constructor should be changed to take an actual object (not an arbitrary EntityId) and AttachmentType should be updated
 * to filter the appropriate rows in core.Documents.
 *
 * Created by davebradlee on 7/16/15.
 */
public class SecureDocumentParent implements AttachmentParent, SecurableResource
{
    private static final String NAME = "SecureDocumentParent";

    private final String _entityId;
    private final String _containerId;
    private final String _sourceModule;

    public SecureDocumentParent(String entityId, Container container, Module sourceModule)
    {
        _entityId = entityId;
        _containerId = container.getId();
        _sourceModule = sourceModule.getName();
    }

    public String getEntityId()
    {
        return _entityId;
    }

    public String getContainerId()
    {
        return _containerId;
    }

    @NotNull
    public String getResourceId()
    {
        return getEntityId();
    }

    @NotNull
    public String getResourceName()
    {
        return NAME;
    }

    @NotNull
    public String getResourceDescription()
    {
        return "";
    }

    @NotNull
    public Module getSourceModule()
    {
        return ModuleLoader.getInstance().getModule(_sourceModule);
    }

    @Nullable
    public SecurableResource getParentResource()
    {
        return null;
    }

    @NotNull
    public Container getResourceContainer()
    {
        Container container = ContainerManager.getForId(_containerId);
        if (null == container)
            throw new IllegalStateException("Container '" + _containerId + "' not found.");
        return container;
    }

    @NotNull
    public List<SecurableResource> getChildResources(User user)
    {
        return Collections.emptyList();
    }

    public boolean mayInheritPolicy()
    {
        return false;
    }

    public void addRoleAssignment(User user, @NotNull Class<? extends Role> roleClass)
    {
        MutableSecurityPolicy securityPolicy = new MutableSecurityPolicy(SecurityPolicyManager.getPolicy(this));
        securityPolicy.addRoleAssignment(user, roleClass);
        SecurityPolicyManager.savePolicy(securityPolicy);
    }

    public void addRoleAssignments(@NotNull Class<? extends Role> roleClass, @NotNull Class<? extends Permission> permissionClass)
    {
        // add role assignment for all users with permission to have roleClass role in container
        MutableSecurityPolicy securityPolicy = new MutableSecurityPolicy(SecurityPolicyManager.getPolicy(this));
        for (User activeUser : UserManager.getActiveUsers())
        {
            if (getResourceContainer().hasPermission(activeUser, permissionClass))
            {
                securityPolicy.addRoleAssignment(activeUser, roleClass);
            }
        }
        SecurityPolicyManager.savePolicy(securityPolicy);
    }

    public SecurityPolicy getSecurityPolicy()
    {
        return SecurityPolicyManager.getPolicy(this);
    }

    @Override
    public @NotNull AttachmentType getAttachmentType()
    {
        return SecureDocumentType.get();
    }
}
