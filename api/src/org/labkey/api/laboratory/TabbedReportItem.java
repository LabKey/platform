/*
 * Copyright (c) 2013-2016 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;

import java.util.Map;

/**
 * User: bimber
 * Date: 1/13/13
 * Time: 12:51 PM
 */
public class TabbedReportItem extends AbstractNavItem
{
    protected String _name;
    protected String _label;
    protected boolean _visible = true;
    protected String _reportType = "query";

    protected FieldKey _subjectIdFieldKey = null;
    protected FieldKey _sampleDateFieldKey = null;
    protected FieldKey _overlappingProjectsFieldKey = null;
    protected FieldKey _allProjectsFieldKey = null;

    public static final String OVERRIDES_PROP_KEY = "laboratory.tabItemOverride";
    protected static final Logger _log = Logger.getLogger(TabbedReportItem.class);

    public TabbedReportItem(DataProvider provider, String name, String label, String reportCategory)
    {
        super(provider, LaboratoryService.NavItemCategory.tabbedReports, reportCategory);
        _name = name;
        _label = label;
    }

    public String getName()
    {
        return _name;
    }

    public String getLabel()
    {
        return _label;
    }

    public String getRendererName()
    {
        return "";
    }

    public boolean getDefaultVisibility(Container c, User u)
    {
        return _visible;
    }

    @Override
    public JSONObject toJSON(Container c, User u)
    {
        JSONObject json = super.toJSON(c, u);
        applyOverrides(this, c, json);

        json.put("overridesKey", getOverridesPropertyKey(this));

        if (_subjectIdFieldKey != null)
            json.put("subjectFieldName", _subjectIdFieldKey);

        if (_sampleDateFieldKey != null)
            json.put("dateFieldName", _sampleDateFieldKey);

        if (_overlappingProjectsFieldKey != null)
        {
            json.put("overlappingProjectsFieldName", _overlappingProjectsFieldKey.toString());
            json.put("overlappingProjectsFieldKeyArray", new JSONArray(_overlappingProjectsFieldKey.getParts()));
        }

        if (_allProjectsFieldKey != null)
        {
            json.put("allProjectsFieldName", _allProjectsFieldKey.toString());
            json.put("allProjectsFieldKeyArray", new JSONArray(_allProjectsFieldKey.getParts()));
        }

        json.put("reportType", getReportType());

        return json;
    }

    protected void inferColumnsFromTable(TableInfo ti)
    {
        for (ColumnInfo ci : ti.getColumns())
        {
            if (_subjectIdFieldKey == null && LaboratoryService.PARTICIPANT_CONCEPT_URI.equalsIgnoreCase(ci.getConceptURI()))
            {
                _subjectIdFieldKey = ci.getFieldKey();
            }
            else if (_sampleDateFieldKey == null && LaboratoryService.SAMPLEDATE_CONCEPT_URI.equalsIgnoreCase(ci.getConceptURI()))
            {
                _sampleDateFieldKey = ci.getFieldKey();
            }
        }

        if (_overlappingProjectsFieldKey == null || _allProjectsFieldKey == null)
        {
            FieldKey overlapKey = FieldKey.fromString("overlappingProjectsPivot");
            FieldKey allKey = FieldKey.fromString("allProjectsPivot");

            Map<FieldKey, ColumnInfo> colMap = _queryCache.getColumns(ti, PageFlowUtil.set(overlapKey, allKey));
            if (_overlappingProjectsFieldKey == null && colMap.containsKey(overlapKey))
                _overlappingProjectsFieldKey = colMap.get(overlapKey).getFieldKey();

            if (_allProjectsFieldKey == null && colMap.containsKey(allKey))
                _allProjectsFieldKey = colMap.get(allKey).getFieldKey();
        }
    }

    public String getReportType()
    {
        return _reportType;
    }

    public void setReportType(String reportType)
    {
        _reportType = reportType;
    }

    public FieldKey getSubjectIdFieldKey()
    {
        return _subjectIdFieldKey;
    }

    public void setSubjectIdFieldKey(FieldKey subjectIdFieldKey)
    {
        _subjectIdFieldKey = subjectIdFieldKey;
    }

    public FieldKey getSampleDateFieldKey()
    {
        return _sampleDateFieldKey;
    }

    public void setSampleDateFieldKey(FieldKey sampleDateFieldKey)
    {
        _sampleDateFieldKey = sampleDateFieldKey;
    }

    public FieldKey getOverlappingProjectsFieldKey()
    {
        return _overlappingProjectsFieldKey;
    }

    public void setOverlappingProjectsFieldKey(FieldKey overlappingProjectsFieldKey)
    {
        _overlappingProjectsFieldKey = overlappingProjectsFieldKey;
    }

    public FieldKey getAllProjectsFieldKey()
    {
        return _allProjectsFieldKey;
    }

    public void setAllProjectsFieldKey(FieldKey allProjectsFieldKey)
    {
        _allProjectsFieldKey = allProjectsFieldKey;
    }

    public void setVisible(boolean visible)
    {
        _visible = visible;
    }

    @Override
    public String getPropertyManagerKey()
    {
        return getOverridesPropertyKey(this);
    }

    public static String getOverridesPropertyKey(NavItem item)
    {
        return item.getDataProvider().getKey() + "||tabReport||" + item.getReportCategory() + "||" + item.getName() + "||" + item.getLabel();
    }

    public static void applyOverrides(NavItem item, Container c, JSONObject json)
    {
        Map<String, String> map = PropertyManager.getProperties(c, OVERRIDES_PROP_KEY);
        if (map.containsKey(getOverridesPropertyKey(item)))
        {
            JSONObject props = new JSONObject(map.get(getOverridesPropertyKey(item)));
            if (props.containsKey("label"))
                json.put("label", props.get("label"));

            if (props.containsKey("reportCategory"))
                json.put("reportCategory", props.get("reportCategory"));
            // retained for settings saved prior to refactor
            else if (props.containsKey("category"))
                json.put("reportCategory", props.get("category"));
        }
    }
}
