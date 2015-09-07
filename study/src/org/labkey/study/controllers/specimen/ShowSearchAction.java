/*
 * Copyright (c) 2009-2015 LabKey Corporation
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
package org.labkey.study.controllers.specimen;

import org.labkey.api.action.FormViewAction;
import org.labkey.api.data.CompareType;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.Study;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.study.controllers.BaseStudyController;
import org.labkey.study.model.StudyManager;
import org.labkey.study.specimen.SpecimenSearchBean;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

@RequiresPermission(ReadPermission.class)
public class ShowSearchAction extends FormViewAction<ShowSearchAction.SearchForm>
{
    private Study _study;
    private String _title;

    public void validateCommand(SearchForm target, Errors errors)
    {
    }

    public ModelAndView getView(SearchForm form, boolean reshow, BindException errors) throws Exception
    {
        _study = StudyManager.getInstance().getStudy(getContainer());
        if (null == _study)
            throw new NotFoundException("No study exists in this folder.");

        _title = (form.isShowVials() ? "Individual Vial" : "Grouped Vial") + " Search";

        SpecimenSearchBean bean = new SpecimenSearchBean(getViewContext(), form.isShowVials(), false);
        bean.setAdvancedExpanded(form.isShowAdvanced());
        bean.init(getViewContext(), form.isShowVials(), true);
        bean.setWebPartId(1);
        setTitle(_title);

        return new JspView<>("/org/labkey/study/view/specimen/search.jsp", bean);
    }

    public boolean handlePost(SearchForm form, BindException errors) throws Exception
    {
        return true;
    }

    public ActionURL getSuccessURL(SearchForm form)
    {
        ActionURL url = SpecimenController.getSamplesURL(getContainer());
        url.addParameter("showVials", Boolean.toString(form.isShowVials()));
        for (ShowSearchAction.SearchForm.SearchParam param : form.getSearchParams())
        {
            if (param.getCompareType() != null && param.getCompareType().length() > 0)
            {
                CompareType compare = CompareType.valueOf(param.getCompareType());
                if (!compare.isDataValueRequired() || (param.getValue() != null && param.getValue().length() > 0))
                    url.addParameter(param.getColumnName() + "~" + compare.getPreferredUrlKey(), param.getValue());
            }
        }
        return url;
    }

    public NavTree appendNavTrail(NavTree root)
    {
        setHelpTopic("specimenShopping");
        root.addChild(_study.getLabel(), BaseStudyController.getStudyOverviewURL(getContainer()));
        root.addChild("Specimen Overview", new ActionURL(SpecimenController.OverviewAction.class, getContainer()));
        root.addChild(_title);

        return root;
    }

    public static class SearchForm extends ShowSearchForm
    {
        private boolean _showAdvanced = false;
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

        public boolean isShowAdvanced()
        {
            return _showAdvanced;
        }

        public void setShowAdvanced(boolean showAdvanced)
        {
            _showAdvanced = showAdvanced;
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
