/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
package org.labkey.api.study.publish;

import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;

import java.util.List;
import java.util.Set;

public class PublishBean
{
    private final List<Long> _ids;
    private final Set<Container> _studies;
    private final boolean _nullStudies;
    private final boolean _insufficientPermissions;
    private final String _dataRegionSelectionKey;
    private final ActionURL _returnUrl;
    private final ActionURL _successURL;
    private final String _containerFilterName;
    private final List<Long> _batchIds;
    private final String _batchNoun;
    private final boolean _autoLinkEnabled;

    public PublishBean(ActionURL successURL,
                       List<Long> ids, String dataRegionSelectionKey,
                       Set<Container> studies, boolean nullStudies, boolean insufficientPermissions, ActionURL returnUrl,
                       String containerFilterName, List<Long> batchIds, String batchNoun, boolean autoLinkEnabled)
    {
        _successURL = successURL;
        _insufficientPermissions = insufficientPermissions;
        _studies = studies;
        _nullStudies = nullStudies;
        _ids = ids;
        _dataRegionSelectionKey = dataRegionSelectionKey;
        _returnUrl = returnUrl;
        _containerFilterName = containerFilterName;
        _batchIds = batchIds;
        _batchNoun = batchNoun;
        _autoLinkEnabled = autoLinkEnabled;
    }

    public ActionURL getSuccessURL()
    {
        return _successURL;
    }

    public ActionURL getReturnUrl()
    {
        return _returnUrl;
    }

    public List<Long> getIds()
    {
        return _ids;
    }

    public String getDataRegionSelectionKey()
    {
        return _dataRegionSelectionKey;
    }

    public Set<Container> getStudies()
    {
        return _studies;
    }

    public boolean isNullStudies()
    {
        return _nullStudies;
    }

    public boolean isInsufficientPermissions()
    {
        return _insufficientPermissions;
    }

    public String getContainerFilterName()
    {
        return _containerFilterName;
    }

    public List<Long> getBatchIds()
    {
        return _batchIds;
    }

    public String getBatchNoun()
    {
        return _batchNoun;
    }

    public Boolean isAutoLinkEnabled()
    {
        return _autoLinkEnabled;
    }
}
