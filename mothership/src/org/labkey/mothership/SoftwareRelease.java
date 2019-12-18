/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import java.util.Date;

/**
 * User: jeckels
 * Date: Aug 29, 2006
 */
public class SoftwareRelease
{
    private int _softwareReleaseId;
    private String _vcsRevision;
    private String _vcsUrl;
    private String _vcsBranch;
    private String _vcsTag;
    private String _container;
    private Date _buildTime;

    private String _buildNumber;

    public String getContainer()
    {
        return _container;
    }

    public void setContainer(String container)
    {
        _container = container;
    }

    public String getVcsRevision()
    {
        return _vcsRevision;
    }

    public void setVcsRevision(String vcsRevision)
    {
        _vcsRevision = vcsRevision;
    }

    public String getVcsUrl()
    {
        return _vcsUrl;
    }

    public void setVcsUrl(String vcsUrl)
    {
        _vcsUrl = vcsUrl;
    }

    public int getSoftwareReleaseId()
    {
        return _softwareReleaseId;
    }

    public void setSoftwareReleaseId(int softwareReleaseId)
    {
        _softwareReleaseId = softwareReleaseId;
    }

    public Date getBuildTime()
    {
        return _buildTime;
    }

    public void setBuildTime(Date buildDate)
    {
        _buildTime = buildDate;
    }

    public String getVcsBranch()
    {
        return _vcsBranch;
    }

    public void setVcsBranch(String vcsBranch)
    {
        _vcsBranch = vcsBranch;
    }

    public String getVcsTag()
    {
        return _vcsTag;
    }

    public void setVcsTag(String vcsTag)
    {
        _vcsTag = vcsTag;
    }

    public String getBuildNumber()
    {
        return _buildNumber;
    }

    public void setBuildNumber(String buildNumber)
    {
        _buildNumber = buildNumber;
    }

    public Integer getSVNRevision()
    {
        if (_buildNumber != null && _buildNumber.contains("."))
        {
            // Issue 36116 - use first part of build number (which is the latest across all SVN modules)
            String[] parts = _buildNumber.split("\\.");
            try
            {
                return Integer.parseInt(parts[0]);
            }
            catch (NumberFormatException ignored) {}
        }
        if (_vcsRevision != null && _vcsRevision.length() < 10)
        {
            try
            {
                return Integer.parseInt(_vcsRevision);
            }
            catch (NumberFormatException ignored) {}
        }
        return null;
    }
}
