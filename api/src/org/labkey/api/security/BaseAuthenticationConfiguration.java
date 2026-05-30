/*
 * Copyright (c) 2019-2026 LabKey Corporation
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
package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentParentType;
import org.labkey.api.data.ContainerManager;

import java.util.Collections;
import java.util.Map;

import static org.labkey.api.util.IntegerUtils.asInteger;

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
        _rowId = asInteger(standardSettings.get("RowId"));
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
    public @NotNull AttachmentParentType getAttachmentParentType()
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
