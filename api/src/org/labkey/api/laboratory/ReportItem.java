/*
 * Copyright (c) 2013-2014 LabKey Corporation
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
package org.labkey.api.laboratory;

import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.ldk.AbstractNavItem;
import org.labkey.api.security.User;

/**
 * User: bimber
 * Date: 5/5/13
 * Time: 9:41 AM
 */
public class ReportItem extends AbstractQueryNavItem
{
    private String _subjectFieldKey;
    private String _sampleDateFieldKey;

    public ReportItem(DataProvider provider, Container targetContainer, String schema, String query, String reportCategory, String label)
    {
        super(provider, schema, query, LaboratoryService.NavItemCategory.reports, reportCategory, label);
        setTargetContainer(targetContainer);
    }

    public ReportItem(DataProvider provider, String schema, String query, String reportCategory)
    {
        super(provider, schema, query, LaboratoryService.NavItemCategory.reports, reportCategory, query);
    }

    @Override
    protected String getItemText(Container c, User u)
    {
        return getLabel();
    }

    @Override
    public JSONObject toJSON(Container c, User u)
    {
        JSONObject json = super.toJSON(c, u);
        TabbedReportItem.applyOverrides(this, c, json);

        return json;
    }

    public String getSubjectFieldKey()
    {
        return _subjectFieldKey;
    }

    public void setSubjectFieldKey(String subjectFieldKey)
    {
        _subjectFieldKey = subjectFieldKey;
    }

    public String getSampleDateFieldKey()
    {
        return _sampleDateFieldKey;
    }

    public void setSampleDateFieldKey(String sampleDateFieldKey)
    {
        _sampleDateFieldKey = sampleDateFieldKey;
    }

    @Override
    public String getPropertyManagerKey()
    {
        return getDataProvider().getKey() + "||" + getReportCategory() + "||" + getName() + "||" + getLabel();
    }
}
