/*
 * Copyright (c) 2014-2017 LabKey Corporation
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
package org.labkey.api.view;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.Parameter;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.User;
import org.labkey.api.settings.AppProps;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;

import java.util.Collections;
import java.util.List;

/**
 * Bean class for short URLs, which are TinyURL-style redirects handled within the server.
 * User: jeckels
 * Date: 1/23/14
 */
public class ShortURLRecord implements SecurableResource, Parameter.JdbcParameterValue
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
        return renderShortURL(getShortURL());
    }

    public static String renderShortURL(String shortUrl)
    {
        shortUrl = StringUtils.trimToNull(shortUrl);
        if(shortUrl != null)
        {
            return AppProps.getInstance().getBaseServerUrl() + AppProps.getInstance().getContextPath() + "/" + PageFlowUtil.encode(shortUrl) + URL_SUFFIX;
        }
        else
           return "Error_rendering_short_url";
    }



    @Nullable
    @Override
    public Object getJdbcParameterValue()
    {
        return getEntityId();
    }

    @NotNull
    @Override
    public JdbcType getJdbcParameterType()
    {
        return JdbcType.VARCHAR;
    }
}
