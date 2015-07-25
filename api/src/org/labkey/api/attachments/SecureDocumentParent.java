package org.labkey.api.attachments;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.*;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.view.ViewContext;

import java.util.Collections;
import java.util.List;

/**
 * Created by davebradlee on 7/16/15.
 *
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

    public String getDownloadURL(ViewContext context, String name)
    {
        return null;
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
        MutableSecurityPolicy securityPolicy = new MutableSecurityPolicy(this);
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

}
