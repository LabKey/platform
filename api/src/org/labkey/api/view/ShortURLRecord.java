package org.labkey.api.view;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.GUID;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * User: jeckels
 * Date: 1/23/14
 */
public class ShortURLRecord implements SecurableResource
{
    public static final String URL_SUFFIX = ".url";

    private User _createdBy;
    private User _modifiedBy;
    private int _rowId;
    private String _shortURL;
    private String _fullURL;
    private GUID _entityId;

    public User getCreatedBy()
    {
        return _createdBy;
    }

    public void setCreatedBy(User createdBy)
    {
        _createdBy = createdBy;
    }

    public User getModifiedBy()
    {
        return _modifiedBy;
    }

    public void setModifiedBy(User modifiedBy)
    {
        _modifiedBy = modifiedBy;
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public String getShortURL()
    {
        return _shortURL;
    }

    public void setShortURL(String shortURL)
    {
        _shortURL = shortURL;
    }

    public String getFullURL()
    {
        return _fullURL;
    }

    public void setFullURL(String fullURL)
    {
        _fullURL = fullURL;
    }

    public GUID getEntityId()
    {
        return _entityId;
    }

    public void setEntityId(GUID entityId)
    {
        _entityId = entityId;
    }

    @NotNull
    @Override
    public String getResourceId()
    {
        return _entityId.toString();
    }

    @NotNull
    @Override
    public String getResourceName()
    {
        return _shortURL;
    }

    @NotNull
    @Override
    public String getResourceDescription()
    {
        return "Short URL from " + _shortURL + " to " + _fullURL;
    }

    @NotNull
    @Override
    public Set<Class<? extends Permission>> getRelevantPermissions()
    {
        return RoleManager.BasicPermissions;
    }

    @NotNull
    @Override
    public Module getSourceModule()
    {
        return ModuleLoader.getInstance().getCoreModule();
    }

    @Nullable
    @Override
    public SecurableResource getParentResource()
    {
        return null;
    }

    @NotNull
    @Override
    public Container getResourceContainer()
    {
        return ContainerManager.getRoot();
    }

    @NotNull
    @Override
    public List<SecurableResource> getChildResources(User user)
    {
        return Collections.emptyList();
    }

    @Override
    public boolean mayInheritPolicy()
    {
        return false;
    }

    public String renderShortURL()
    {
        return AppProps.getInstance().getBaseServerUrl() + AppProps.getInstance().getContextPath() + "/" + getShortURL() + URL_SUFFIX;
    }
}
