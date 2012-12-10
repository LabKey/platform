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
import org.labkey.api.action.SpringActionController;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.ReadPermission;
import org.springframework.validation.BindException;

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
            response.put("success", true);

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
}