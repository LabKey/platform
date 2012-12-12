/*
 * Copyright (c) 2012 LabKey Corporation
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

package org.labkey.survey;

import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.ReturnURLString;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.survey.model.SurveyDesign;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

public class SurveyController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(SurveyController.class);

    public SurveyController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class CreateSurveyTemplateAction extends ApiAction<SurveyTemplateForm>
    {
        @Override
        public ApiResponse execute(SurveyTemplateForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            JSONObject json = SurveyManager.get().createSurveyTemplate(getViewContext(), form.getSchemaName(), form.getQueryName());

            response.put("survey", json);
            //response.put("success", true);

            return response;
        }
    }

    public static class SurveyTemplateForm
    {
        private String _schemaName;
        private String _queryName;

        public String getSchemaName()
        {
            return _schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            _schemaName = schemaName;
        }

        public String getQueryName()
        {
            return _queryName;
        }

        public void setQueryName(String queryName)
        {
            _queryName = queryName;
        }
   }

    @RequiresPermissionClass(InsertPermission.class)
    public class UpdateSurveyDesignAction extends SimpleViewAction<SurveyDesignForm>
    {
        private String _title = "Create Survey Design";

        @Override
        public ModelAndView getView(SurveyDesignForm form,BindException errors) throws Exception
        {
            if (form.getRowId() != 0)
            {
                SurveyDesign survey = SurveyManager.get().getSurvey(getContainer(), getUser(), form.getRowId());
                if (survey != null)
                    _title = "Update Survey Design : " + survey.getLabel();
            }
            JspView view = new JspView<SurveyDesignForm>("/org/labkey/survey/view/surveyDesignWizard.jsp", form);

            return view;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild(_title);
        }
    }

    public static class SurveyDesignForm
    {
        private int _rowId;
        private String _label;
        private String _metadata;
        private String _schemaName;
        private String _queryName;
        private ReturnURLString _srcURL;

        public int getRowId()
        {
            return _rowId;
        }

        public void setRowId(int rowId)
        {
            _rowId = rowId;
        }

        public String getLabel()
        {
            return _label;
        }

        public void setLabel(String label)
        {
            _label = label;
        }

        public String getMetadata()
        {
            return _metadata;
        }

        public void setMetadata(String metadata)
        {
            _metadata = metadata;
        }

        public String getSchemaName()
        {
            return _schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            _schemaName = schemaName;
        }

        public String getQueryName()
        {
            return _queryName;
        }

        public void setQueryName(String queryName)
        {
            _queryName = queryName;
        }

        public ReturnURLString getSrcURL()
        {
            return _srcURL;
        }

        public void setSrcURL(ReturnURLString srcURL)
        {
            _srcURL = srcURL;
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    public class SaveSurveyTemplateAction extends ApiAction<SurveyDesignForm>
    {
        @Override
        public ApiResponse execute(SurveyDesignForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            SurveyDesign survey = getSurvey(form);
            SurveyManager.get().saveSurveyDesign(getContainer(), getUser(), survey);

            response.put("success", true);

            return response;
        }
    }

    private SurveyDesign getSurvey(SurveyDesignForm form)
    {
        SurveyDesign survey = new SurveyDesign();
        if (form.getRowId() != 0)
            survey = SurveyManager.get().getSurvey(getContainer(), getUser(), form.getRowId());

        if (form.getLabel() != null)
            survey.setLabel(form.getLabel());
        survey.setContainerId(getContainer().getId());
        if (form.getSchemaName() != null)
            survey.setSchemaName(form.getSchemaName());
        if (form.getQueryName() != null)
            survey.setQueryName(form.getQueryName());
        if (form.getMetadata() != null)
            survey.setMetadata(form.getMetadata());

        return survey;

}
    @RequiresPermissionClass(ReadPermission.class)
    public class GetSurveyTemplateAction extends ApiAction<SurveyDesignForm>
    {
        @Override
        public ApiResponse execute(SurveyDesignForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            if (form.getRowId() != 0)
            {
                SurveyDesign survey = getSurvey(form);

                response.put("survey", new JSONObject(survey));
                response.put("success", true);
            }
            else
                response.put("success", false);

            return response;
        }
   }
}