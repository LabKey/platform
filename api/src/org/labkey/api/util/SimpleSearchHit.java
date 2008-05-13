/*
 * Copyright (c) 2008 LabKey Corporation
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
package org.labkey.api.util;

import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.Container;

/**
 * Provides a simple implementation of SearchHit
 *
 * Created by IntelliJ IDEA.
 * User: Dave
 * Date: May 2, 2008
 * Time: 9:37:33 AM
 * To change this template use File | Settings | File Templates.
 */
public class SimpleSearchHit implements SearchHit
{
    private String _domain;
    private String _title;
    private String _containerPath;
    private String _href;
    private String _type;
    private String _typeDescription;
    private String _details;

    /**
     * Constructs without setting any member values. If you use this construct, use the various
     * setters to initialize the object before adding it to the search results
     */
    public SimpleSearchHit() {}

    /**
     * Constructs the object with supplied information.
     * @param domain Domain in which this hit was found (should match the name of your Searchable implementation domain)
     * @param containerPath ContainerId in which the hit was found
     * @param title Title of the search hit
     * @param href Href for the search hit
     * @param type Type of the search hit
     * @param typeDescription A description of the the type of hit
     */
    public SimpleSearchHit(String domain, String containerPath, String title,
                           String href, String type, String typeDescription)
    {
        assert null != domain;
        assert null != title;
        assert null != containerPath;
        assert null != href;
        assert null != type;

        _domain = domain;
        _title = title;
        _containerPath = containerPath;
        _href = href;
        _type = type;
        _typeDescription = typeDescription;
    }

    public String getDomain()
    {
        return _domain;
    }

    public void setDomain(String domain)
    {
        _domain = domain;
    }

    public String getTitle()
    {
        return _title;
    }

    public void setTitle(String title)
    {
        _title = title;
    }

    public String getContainerPath()
    {
        return _containerPath;
    }

    public void setContainerPath(String containerPath)
    {
        _containerPath = containerPath;
    }

    public void setContainerPathById(String containerId)
    {
        Container container = ContainerManager.getForId(containerId);
        assert(null != container) : "Invalid container id '" + containerId + "' passed to SimpleSearchHit.setContainerById()!";
        _containerPath = container.getPath();
    }

    public String getHref()
    {
        return _href;
    }

    public void setHref(String href)
    {
        _href = href;
    }

    public String getType()
    {
        return _type;
    }

    public void setType(String type)
    {
        _type = type;
    }

    public String getTypeDescription()
    {
        return _typeDescription;
    }

    public void setTypeDescription(String typeDescription)
    {
        _typeDescription = typeDescription;
    }

    public String getDetails()
    {
        return _details;
    }

    public void setDetails(String details)
    {
        _details = details;
    }

    public boolean equals(Object o)
    {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        SimpleSearchHit that = (SimpleSearchHit) o;

        if (!_containerPath.equals(that._containerPath)) return false;
        if (!_domain.equals(that._domain)) return false;
        if (!_href.equals(that._href)) return false;
        if (!_title.equals(that._title)) return false;
        if (!_type.equals(that._type)) return false;

        return true;
    }

    public int hashCode()
    {
        int result;
        result = _domain.hashCode();
        result = 31 * result + _title.hashCode();
        result = 31 * result + _containerPath.hashCode();
        result = 31 * result + _href.hashCode();
        result = 31 * result + _type.hashCode();
        return result;
    }

    public String toString()
    {
        return "domain: '" + _domain + "'"
                + ", container: '" + _containerPath + "'"
                + ", title: '" + _title + "'"
                + ", type: '" + _type + "'";
    }
}
