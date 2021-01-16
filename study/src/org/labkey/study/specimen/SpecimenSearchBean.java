/*
 * Copyright (c) 2014-2019 LabKey Corporation
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
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.UserSchema;
import org.labkey.api.specimen.SpecimenQuerySchema;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.DemoMode;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.study.query.SpecimenQueryView;

import java.util.ArrayList;
import java.util.List;

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
        _detailsView = detailsView;
        SpecimenQueryView view = SpecimenQueryView.createView(context, detailsView ? SpecimenQueryView.ViewType.VIALS :
                SpecimenQueryView.ViewType.SUMMARY);

        _displayColumns = new ArrayList<>();
        for (DisplayColumn dc : view.getDisplayColumns())
            if (dc.getColumnInfo() != null)
                _displayColumns.add(dc);
        _dataRegionName = view.getDataRegionName();
        _baseViewURL = view.getBaseViewURL();

        DisplayColumnInfo visitInfo = new DisplayColumnInfo(true, true);
        visitInfo.setOrderBy("DisplayOrder");
        UserSchema schema = SpecimenQuerySchema.get(StudyService.get().getStudy(context.getContainer()), context.getUser());
        TableInfo simpleSpecimenTable = schema.getTable(SpecimenQuerySchema.SIMPLE_SPECIMEN_TABLE_NAME);
        DisplayColumnInfo participantColInfo = new DisplayColumnInfo(true, true, true, DemoMode.isDemoMode(context), simpleSpecimenTable);
        participantColInfo.setOrderBy("PTID");
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
