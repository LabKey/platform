package org.labkey.study.samples;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.TableInfo;
import org.labkey.api.study.StudyService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.study.SampleManager;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.SpecimenQueryView;
import org.labkey.study.query.StudyQuerySchema;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
* Copyright (c) 2008-2010 LabKey Corporation
* <p/>
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
* <p/>
* http://www.apache.org/licenses/LICENSE-2.0
* <p/>
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
* <p/>
* <p/>
* User: brittp
* Date: Oct 21, 2010 4:01:33 PM
*/
public class SampleSearchBean
{
    private boolean _detailsView;
    private List<DisplayColumn> _displayColumns;
    private ActionURL _baseViewURL;
    private String _dataRegionName;
    private Container _container;
    private Map<String, DisplayColumnInfo> _defaultDetailCols;
    private Map<String, DisplayColumnInfo> _defaultSummaryCols;
    private boolean _inWebPart;
    private boolean _advancedExpanded;

    private static class DisplayColumnInfo
    {
        private boolean _displayByDefault;
        private boolean _displayAsPickList;
        private boolean _forceDistinctQuery;
        private String _orderBy;
        private TableInfo _tableInfo;

        public DisplayColumnInfo(boolean displayByDefault, boolean displayAsPickList)
        {
            this(displayByDefault, displayAsPickList, false);
        }

        public DisplayColumnInfo(boolean displayByDefault, boolean displayAsPickList, boolean forceDistinctQuery)
        {
            this(displayByDefault, displayAsPickList, forceDistinctQuery, null);
        }

        public DisplayColumnInfo(boolean displayByDefault, boolean displayAsPickList, boolean forceDistinctQuery, TableInfo tableInfo)
        {
            _displayByDefault = displayByDefault;
            _displayAsPickList = displayAsPickList;
            _forceDistinctQuery = forceDistinctQuery;
            _tableInfo = tableInfo;
        }

        public boolean isDisplayAsPickList()
        {
            return _displayAsPickList;
        }

        public boolean isDisplayByDefault()
        {
            return _displayByDefault;
        }

        public boolean isForceDistinctQuery()
        {
            return _forceDistinctQuery;
        }

        public String getOrderBy()
        {
            return _orderBy;
        }

        public void setOrderBy(String orderBy)
        {
            _orderBy = orderBy;
        }

        public TableInfo getTableInfo()
        {
            return _tableInfo;
        }
    }

    public SampleSearchBean()
    {

    }

    public SampleSearchBean(ViewContext context, boolean detailsView, boolean inWebPart)
    {
        init(context, detailsView, inWebPart);
    }

    public void init(ViewContext context, boolean detailsView, boolean inWebPart)
    {
        _inWebPart = inWebPart;
        _container = context.getContainer();
        _detailsView = detailsView;
        SpecimenQueryView view = SpecimenQueryView.createView(context, detailsView ? SpecimenQueryView.ViewType.VIALS :
                SpecimenQueryView.ViewType.SUMMARY);

        _displayColumns = new ArrayList<DisplayColumn>();
        for (DisplayColumn dc : view.getDisplayColumns())
            if (dc.getColumnInfo() != null)
                _displayColumns.add(dc);
        _dataRegionName = view.getDataRegionName();
        _baseViewURL = view.getBaseViewURL();

        _defaultDetailCols = new HashMap<String, DisplayColumnInfo>();
        _defaultDetailCols.put("PrimaryType", new DisplayColumnInfo(true, true));
        _defaultDetailCols.put("AdditiveType", new DisplayColumnInfo(true, true));
        DisplayColumnInfo visitInfo = new DisplayColumnInfo(true, true);
        visitInfo.setOrderBy("DisplayOrder");
        _defaultDetailCols.put("Visit", visitInfo);
        StudyQuerySchema schema = new StudyQuerySchema(StudyManager.getInstance().getStudy(_container), context.getUser(), true);
        TableInfo simpleSpecimenTable = schema.createSimpleSpecimenTable();

        DisplayColumnInfo participantColInfo = new DisplayColumnInfo(true, true, true, simpleSpecimenTable);
        participantColInfo.setOrderBy("PTID");
        _defaultDetailCols.put(StudyService.get().getSubjectColumnName(context.getContainer()), participantColInfo);
        _defaultDetailCols.put("Available", new DisplayColumnInfo(true, true));
        _defaultDetailCols.put("SiteLdmsCode", new DisplayColumnInfo(true, true));
        _defaultDetailCols.put("DerivativeType", new DisplayColumnInfo(true, true));
        _defaultDetailCols.put("VolumeUnits", new DisplayColumnInfo(false, true, false, simpleSpecimenTable));
        _defaultDetailCols.put("GlobalUniqueId", new DisplayColumnInfo(true, false));
        _defaultDetailCols.put("Clinic", new DisplayColumnInfo(true, true));

        _defaultSummaryCols = new HashMap<String, DisplayColumnInfo>();
        _defaultSummaryCols.put("PrimaryType", new DisplayColumnInfo(true, true));
        _defaultSummaryCols.put("AdditiveType", new DisplayColumnInfo(true, true));
        _defaultSummaryCols.put("DerivativeType", new DisplayColumnInfo(true, true));
        _defaultSummaryCols.put("Visit", visitInfo);
        _defaultSummaryCols.put(StudyService.get().getSubjectColumnName(context.getContainer()), participantColInfo);
        _defaultSummaryCols.put("Available", new DisplayColumnInfo(true, false));
        _defaultSummaryCols.put("VolumeUnits", new DisplayColumnInfo(false, true, false, simpleSpecimenTable));
        _defaultSummaryCols.put("Clinic", new DisplayColumnInfo(true, true));
    }

    public List<DisplayColumn> getDisplayColumns()
    {
        return _displayColumns;
    }

    public boolean isDetailsView()
    {
        return _detailsView;
    }

    public ActionURL getBaseViewURL()
    {
        return _baseViewURL;
    }

    public String getDataRegionName()
    {
        return _dataRegionName;
    }

    public boolean isDefaultColumn(ColumnInfo info)
    {
        Map<String, DisplayColumnInfo> defaultColumns = isDetailsView() ? _defaultDetailCols : _defaultSummaryCols;
        DisplayColumnInfo colInfo = defaultColumns.get(info.getName());
        return colInfo != null && colInfo.isDisplayByDefault();
    }

    public boolean isPickListColumn(ColumnInfo info)
    {
        Map<String, DisplayColumnInfo> defaultColumns = isDetailsView() ? _defaultDetailCols : _defaultSummaryCols;
        DisplayColumnInfo colInfo = defaultColumns.get(info.getName());
        return colInfo != null && colInfo.isDisplayAsPickList();
    }

    public List<String> getPickListValues(ColumnInfo info) throws SQLException
    {
        Map<String, DisplayColumnInfo> defaultColumns = isDetailsView() ? _defaultDetailCols : _defaultSummaryCols;
        DisplayColumnInfo colInfo = defaultColumns.get(info.getName());
        assert colInfo != null : info.getName() + " is not a picklist column.";
        return SampleManager.getInstance().getDistinctColumnValues(_container, info, colInfo.isForceDistinctQuery(), colInfo.getOrderBy(), colInfo.getTableInfo());
    }

    public boolean isInWebPart()
    {
        return _inWebPart;
    }

    public boolean isAdvancedExpanded()
    {
        return _advancedExpanded;
    }

    public void setAdvancedExpanded(boolean advancedExpanded)
    {
        _advancedExpanded = advancedExpanded;
    }
}
