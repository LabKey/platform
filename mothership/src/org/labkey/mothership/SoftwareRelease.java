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

package org.labkey.mothership;

/**
 * User: jeckels
 * Date: Aug 29, 2006
 */
public class SoftwareRelease
{
    private int _softwareReleaseId;
    private Integer _SVNRevision;
    private String _SVNURL;
    private String _description;
    private String _container;

    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String container)
    {
        _container = container;
    }

    public Integer getSVNRevision()
    {
        return _SVNRevision;
    }

    public void setSVNRevision(Integer SVNRevision)
    {
        _SVNRevision = SVNRevision;
    }

    public String getDescription()
    {
        return _description;
    }

    public void setDescription(String description)
    {
        _description = description;
    }

    public int getSoftwareReleaseId()
    {
        return _softwareReleaseId;
    }

    public void setSoftwareReleaseId(int softwareReleaseId)
    {
        _softwareReleaseId = softwareReleaseId;
    }

    public String getSVNURL()
    {
        return _SVNURL;
    }

    public void setSVNURL(String SVNURL)
    {
        _SVNURL = SVNURL;
    }
}
