/*
 * Copyright (c) 2011-2015 LabKey Corporation
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
package org.labkey.study.query;

import org.apache.commons.beanutils.PropertyUtils;
import org.labkey.api.query.QuerySettings;
import org.labkey.study.model.QCStateSet;
import org.springframework.beans.PropertyValues;

/**
 * User: klum
 * Date: Sep 30, 2011
 * Time: 4:48:11 PM
 */
public class DatasetQuerySettings extends QuerySettings
{
    private boolean _showSourceLinks;
    private boolean _showEditLinks = true;
    private boolean _useQCSet = true;

    public boolean isUseQCSet()
    {
        return _useQCSet;
    }

    public void setUseQCSet(boolean useQCSet)
    {
        _useQCSet = useQCSet;
    }

    public DatasetQuerySettings(String dataRegionName)
    {
        super(dataRegionName);
    }

    public DatasetQuerySettings(PropertyValues pvs, String dataRegionName)
    {
        super(pvs, dataRegionName);
    }

    public DatasetQuerySettings(QuerySettings settings)
    {
        super(settings.getDataRegionName());

        try {
            PropertyUtils.copyProperties(this, settings);
        }
        catch (Exception e)
        {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void init(PropertyValues params)
    {
        super.init(params);
        _showSourceLinks = _getParameter(param("showSourceLinks")) != null;
        _showEditLinks = _getParameter(param("showEditLinks")) != null;
    }

    public boolean isShowSourceLinks()
    {
        return _showSourceLinks;
    }

    public void setShowSourceLinks(boolean showSourceLinks)
    {
        _showSourceLinks = showSourceLinks;
    }

    public boolean isShowEditLinks()
    {
        return _showEditLinks;
    }

    public void setShowEditLinks(boolean showEditLinks)
    {
        _showEditLinks = showEditLinks;
    }
}
