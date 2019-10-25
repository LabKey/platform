package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.ContainerManager;

import java.util.Map;

public abstract class BaseAuthenticationConfiguration<AP extends AuthenticationProvider> implements AuthenticationConfiguration<AP>
{
    private final AP _provider;
    private final String _description;
    private final boolean _enabled;
    private final Integer _rowId;
    private final String _entityId;

    protected BaseAuthenticationConfiguration(String key, AP provider, Map<String, String> props)
    {
        _rowId = 0;
        _entityId = null;
        _provider = provider;
        _description = props.get("Description");
        _enabled = Boolean.valueOf(props.get("Enabled"));
    }

    public BaseAuthenticationConfiguration(AP provider, Map<String, Object> props)
    {
        _rowId = (Integer)props.get("RowId");
        _entityId = (String)props.get("EntityId");
        _provider = provider;
        _description = (String)props.get("Description");
        _enabled = (Boolean)props.get("Enabled");
    }

    @Override
    @NotNull
    public Integer getRowId()
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
}
