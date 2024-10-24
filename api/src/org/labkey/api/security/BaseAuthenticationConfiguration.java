package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.ContainerManager;

import java.util.Collections;
import java.util.Map;

public abstract class BaseAuthenticationConfiguration<AP extends AuthenticationProvider> implements AuthenticationConfiguration<AP>
{
    private final AP _provider;
    private final String _description;
    private final boolean _enabled;
    private final int _rowId;
    private final String _entityId;

    public BaseAuthenticationConfiguration(AP provider, Map<String, Object> standardSettings)
    {
        _provider = provider;
        _rowId = (Integer)standardSettings.get("RowId");
        _entityId = (String)standardSettings.get("EntityId");
        _description = (String)standardSettings.get("Description");
        _enabled = (Boolean)standardSettings.get("Enabled");
    }

    @Override
    public int getRowId()
    {
        return _rowId;
    }

    @Override
    public String getEntityId()
    {
        return _entityId;
    }

    @Override
    public String getContainerId()
    {
        return ContainerManager.getRoot().getId();
    }

    @Override
    public @NotNull AttachmentType getAttachmentType()
    {
        return AuthenticationLogoType.get();
    }

    @Override
    public @NotNull String getDescription()
    {
        return _description;
    }

    @NotNull
    @Override
    public AP getAuthenticationProvider()
    {
        return _provider;
    }

    @Override
    public boolean isEnabled()
    {
        return _enabled;
    }

    @Override
    public @NotNull Map<String, Object> getCustomProperties()
    {
        return Collections.emptyMap();
    }

    @Override
    public String toString()
    {
        return getClass().getSimpleName() + " " + getDescription();
    }
}
