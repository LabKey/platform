/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.study.controllers.samples;

import org.labkey.api.action.FormViewAction;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.Study;
import org.labkey.api.view.*;
import org.labkey.study.SampleManager;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.SpecimenQueryView;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RequiresPermissionClass(ReadPermission.class)
public class ShowSearchAction extends FormViewAction<ShowSearchAction.SearchForm>
{
    private Study _study;
    private String _title;

    public void validateCommand(SearchForm target, Errors errors)
    {
    }

    public ModelAndView getView(SearchForm form, boolean reshow, BindException errors) throws Exception
    {
        _study = StudyManager.getInstance().getStudy(getViewContext().getContainer());
        if (null == _study)
            HttpView.throwNotFound("No study exists in this folder.");

        _title = (form.isShowVials() ? "Vial" : "Specimen") + " Search";

        return new JspView<SearchBean>("/org/labkey/study/view/samples/search.jsp",
                new SearchBean(getViewContext(), form.isShowVials()));
    }

    public boolean handlePost(SearchForm form, BindException errors) throws Exception
    {
        return true;
    }

    public ActionURL getSuccessURL(SearchForm form)
    {
        ActionURL url = new ActionURL(SpringSpecimenController.SamplesAction.class, getViewContext().getContainer());
        url.addParameter("showVials", Boolean.toString(form.isShowVials()));
        for (ShowSearchAction.SearchForm.SearchParam param : form.getSearchParams())
        {
            if (param.getCompareType() != null && param.getCompareType().length() > 0)
            {
                CompareType compare = CompareType.valueOf(param.getCompareType());
                if (!compare.isDataValueRequired() || (param.getValue() != null && param.getValue().length() > 0))
                    url.addParameter(param.getColumnName() + "~" + compare.getUrlKey(), param.getValue());
            }
        }
        return url;
    }

    public NavTree appendNavTrail(NavTree root)
    {
        root.addChild(_study.getLabel(), new ActionURL(StudyController.OverviewAction.class, getViewContext().getContainer()));
        root.addChild("Manage Study", new ActionURL(SpringSpecimenController.SamplesAction.class, getViewContext().getContainer()));
        root.addChild(_title);

        return root;
    }

    public static class SearchForm extends ShowSearchForm
    {
        private SearchParam[] _searchParams;
        public static class SearchParam
        {
            private String _compareType;
            private String _value;
            private String _columnName;

            public String getCompareType()
            {
                return _compareType;
            }

            public void setCompareType(String compareType)
            {
                _compareType = compareType;
            }

            public String getValue()
            {
                return _value;
            }

            public void setValue(String value)
            {
                _value = value;
            }

            public String getColumnName()
            {
                return _columnName;
            }

            public void setColumnName(String columnName)
            {
                _columnName = columnName;
            }
        }
        public SearchForm()
        {
            _searchParams = new SearchParam[100];
            for (int i = 0; i < 100; i++)
                _searchParams[i] = new SearchParam();
        }

        public SearchParam[] getSearchParams()
        {
            return _searchParams;
        }

        public void setSearchParams(SearchParam[] searchParams)
        {
            _searchParams = searchParams;
        }
    }

    public static class SearchBean
    {
        private boolean _detailsView;
        private List<DisplayColumn> _displayColumns;
        private ActionURL _baseViewURL;
        private String _dataRegionName;
        private Container _container;
        private Map<String, DisplayColumnInfo> _defaultDetailCols;
        private Map<String, DisplayColumnInfo> _defaultSummaryCols;

        private static class DisplayColumnInfo
        {
            private boolean _displayByDefault;
            private boolean _displayAsPickList;
            private boolean _forceDistinctQuery;

            public DisplayColumnInfo(boolean displayByDefault, boolean displayAsPickList)
            {
                this(displayByDefault, displayAsPickList, false);
            }

            public DisplayColumnInfo(boolean displayByDefault, boolean displayAsPickList, boolean forceDistinctQuery)
            {
                _displayByDefault = displayByDefault;
                _displayAsPickList = displayAsPickList;
                _forceDistinctQuery = forceDistinctQuery;
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
        }


        public SearchBean(ViewContext context, boolean detailsView)
        {
            _container = context.getContainer();
            _detailsView = detailsView;
            SpecimenQueryView view = SpecimenQueryView.createView(context, detailsView ? SpecimenQueryView.ViewType.VIALS :
                    SpecimenQueryView.ViewType.SUMMARY);
            _displayColumns = view.getDisplayColumns();
            _dataRegionName = view.getDataRegionName();
            _baseViewURL = view.getBaseViewURL();

            _defaultDetailCols = new HashMap<String, DisplayColumnInfo>();
            _defaultDetailCols.put("PrimaryType", new DisplayColumnInfo(true, true));
            _defaultDetailCols.put("AdditiveType", new DisplayColumnInfo(true, true));
            _defaultDetailCols.put("SiteName", new DisplayColumnInfo(true, true, true));
            _defaultDetailCols.put("Visit", new DisplayColumnInfo(true, true));
            _defaultDetailCols.put("ParticipantId", new DisplayColumnInfo(true, true, true));
            _defaultDetailCols.put("Available", new DisplayColumnInfo(true, true));
            _defaultDetailCols.put("SiteLdmsCode", new DisplayColumnInfo(false, true));
            _defaultDetailCols.put("DerivativeType", new DisplayColumnInfo(true, true));
            _defaultDetailCols.put("VolumeUnits", new DisplayColumnInfo(false, true));
            _defaultDetailCols.put("GlobalUniqueId", new DisplayColumnInfo(true, false));
            _defaultDetailCols.put("Clinic", new DisplayColumnInfo(true, true));

            _defaultSummaryCols = new HashMap<String, DisplayColumnInfo>();
            _defaultSummaryCols.put("PrimaryType", new DisplayColumnInfo(true, true));
            _defaultSummaryCols.put("AdditiveType", new DisplayColumnInfo(true, true));
            _defaultSummaryCols.put("DerivativeType", new DisplayColumnInfo(true, true));
            _defaultSummaryCols.put("SiteName", new DisplayColumnInfo(true, true, true));
            _defaultSummaryCols.put("Visit", new DisplayColumnInfo(true, true));
            _defaultSummaryCols.put("ParticipantId", new DisplayColumnInfo(true, true, true));
            _defaultSummaryCols.put("Available", new DisplayColumnInfo(true, false));
            _defaultSummaryCols.put("VolumeUnits", new DisplayColumnInfo(false, true));
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
            return SampleManager.getInstance().getDistinctColumnValues(_container, info, colInfo.isForceDistinctQuery());
        }
    }

    public static class ShowSearchForm
    {
        private boolean _showVials;

        public boolean isShowVials()
        {
            return _showVials;
        }

        public void setShowVials(boolean showVials)
        {
            _showVials = showVials;
        }
    }
}
