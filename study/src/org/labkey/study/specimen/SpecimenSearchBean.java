/*
 * Copyright (c) 2010-2014 LabKey Corporation
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

package org.labkey.study.specimen;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.DemoMode;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.SpecimenQueryView;
import org.labkey.study.query.StudyQuerySchema;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * User: brittp
 * Date: Oct 21, 2010 4:01:33 PM
 */
public class SpecimenSearchBean
{
    private boolean _detailsView;
    private List<DisplayColumn> _displayColumns;
    private ActionURL _baseViewURL;
    private String _dataRegionName;
    private Container _container;
    private User _user;
    private Map<String, DisplayColumnInfo> _defaultDetailCols;
    private Map<String, DisplayColumnInfo> _defaultSummaryCols;
    private boolean _inWebPart;
    private int _webPartId = 0;
    private boolean _advancedExpanded;

    private static class DisplayColumnInfo
    {
        private final boolean _displayByDefault;
        private final boolean _displayAsPickList;
        private final boolean _forceDistinctQuery;
        private final boolean _obfuscate;
        private final @Nullable TableInfo _tableInfo;

        private String _orderBy;

        public DisplayColumnInfo(boolean displayByDefault, boolean displayAsPickList)
        {
            this(displayByDefault, displayAsPickList, false);
        }

        public DisplayColumnInfo(boolean displayByDefault, boolean displayAsPickList, boolean forceDistinctQuery)
        {
            this(displayByDefault, displayAsPickList, forceDistinctQuery, false, null);
        }

        public DisplayColumnInfo(boolean displayByDefault, boolean displayAsPickList, boolean forceDistinctQuery, boolean obfuscate, @Nullable TableInfo tableInfo)
        {
            _displayByDefault = displayByDefault;
            _displayAsPickList = displayAsPickList;
            _forceDistinctQuery = forceDistinctQuery;
            _obfuscate = obfuscate;
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

        public boolean shouldObfuscate()
        {
            return _obfuscate;
        }

        public String getOrderBy()
        {
            return _orderBy;
        }

        public void setOrderBy(String orderBy)
        {
            _orderBy = orderBy;
        }

        public @Nullable TableInfo getTableInfo()
        {
            return _tableInfo;
        }
    }

    public SpecimenSearchBean()
    {

    }

    public SpecimenSearchBean(ViewContext context, boolean detailsView, boolean inWebPart)
    {
        init(context, detailsView, inWebPart);
    }

    public void init(ViewContext context, boolean detailsView, boolean inWebPart)
    {
        _inWebPart = inWebPart;
        _container = context.getContainer();
        _user = context.getUser();
        _detailsView = detailsView;
        SpecimenQueryView view = SpecimenQueryView.createView(context, detailsView ? SpecimenQueryView.ViewType.VIALS :
                SpecimenQueryView.ViewType.SUMMARY);

        _displayColumns = new ArrayList<>();
        for (DisplayColumn dc : view.getDisplayColumns())
            if (dc.getColumnInfo() != null)
                _displayColumns.add(dc);
        _dataRegionName = view.getDataRegionName();
        _baseViewURL = view.getBaseViewURL();

        _defaultDetailCols = new HashMap<>();
        _defaultDetailCols.put("PrimaryType", new DisplayColumnInfo(true, true));
        _defaultDetailCols.put("AdditiveType", new DisplayColumnInfo(true, true));
        DisplayColumnInfo visitInfo = new DisplayColumnInfo(true, true);
        visitInfo.setOrderBy("DisplayOrder");
        _defaultDetailCols.put("Visit", visitInfo);
        StudyQuerySchema schema = StudyQuerySchema.createSchema(StudyManager.getInstance().getStudy(_container), context.getUser(), true);
        TableInfo simpleSpecimenTable = schema.createTable(StudyQuerySchema.SIMPLE_SPECIMEN_TABLE_NAME);

        DisplayColumnInfo participantColInfo = new DisplayColumnInfo(true, true, true, DemoMode.isDemoMode(context), simpleSpecimenTable);
        participantColInfo.setOrderBy("PTID");
        _defaultDetailCols.put(StudyService.get().getSubjectColumnName(context.getContainer()), participantColInfo);
        _defaultDetailCols.put("Available", new DisplayColumnInfo(true, true));
        _defaultDetailCols.put("DerivativeType", new DisplayColumnInfo(true, true));
        _defaultDetailCols.put("VolumeUnits", new DisplayColumnInfo(false, true, false, false, simpleSpecimenTable));
        _defaultDetailCols.put("GlobalUniqueId", new DisplayColumnInfo(true, false));
        _defaultDetailCols.put("Clinic", new DisplayColumnInfo(true, true));

        _defaultSummaryCols = new HashMap<>();
        _defaultSummaryCols.put("PrimaryType", new DisplayColumnInfo(true, true));
        _defaultSummaryCols.put("AdditiveType", new DisplayColumnInfo(true, true));
        _defaultSummaryCols.put("DerivativeType", new DisplayColumnInfo(true, true));
        _defaultSummaryCols.put("Visit", visitInfo);
        _defaultSummaryCols.put(StudyService.get().getSubjectColumnName(context.getContainer()), participantColInfo);
        _defaultSummaryCols.put("Available", new DisplayColumnInfo(true, false));
        _defaultSummaryCols.put("VolumeUnits", new DisplayColumnInfo(false, true, false, false, simpleSpecimenTable));
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

/*    private @Nullable DisplayColumnInfo getDisplayColumnInfo(ColumnInfo info)
    {
        Map<String, DisplayColumnInfo> defaultColumns = isDetailsView() ? _defaultDetailCols : _defaultSummaryCols;
        return defaultColumns.get(info.getName());
    }

    public boolean isDefaultColumn(ColumnInfo info)
    {
        DisplayColumnInfo colInfo = getDisplayColumnInfo(info);
        return colInfo != null && colInfo.isDisplayByDefault();
    }

    public boolean isPickListColumn(ColumnInfo info)
    {
        DisplayColumnInfo colInfo = getDisplayColumnInfo(info);
        return colInfo != null && colInfo.isDisplayAsPickList();
    }

    public List<String> getPickListValues(ColumnInfo info) throws SQLException
    {
        DisplayColumnInfo colInfo = getDisplayColumnInfo(info);
        assert colInfo != null : info.getName() + " is not a picklist column.";
        return SampleManager.getInstance().getDistinctColumnValues(_container, _user, info, colInfo.isForceDistinctQuery(), colInfo.getOrderBy(), colInfo.getTableInfo());
    }

    public boolean shouldObfuscate(ColumnInfo info) throws SQLException
    {
        DisplayColumnInfo colInfo = getDisplayColumnInfo(info);
        assert colInfo != null : info.getName() + " is not a picklist column.";
        return colInfo.shouldObfuscate();
    }
*/
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

    public int getWebPartId()
    {
        return _webPartId;
    }

    public void setWebPartId(int webPartId)
    {
        _webPartId = webPartId;
    }
}
