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

import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import org.apache.commons.lang3.math.NumberUtils;
import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.collections.CaseInsensitiveMapWrapper;
import org.labkey.api.data.BeanObjectFactory;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableViewForm;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.ReturnURLString;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.survey.model.Survey;
import org.labkey.survey.model.SurveyDesign;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    public class UpdateSurveyAction extends SimpleViewAction<SurveyForm>
    {
        private String _title = null;

        @Override
        public ModelAndView getView(SurveyForm form, BindException errors) throws Exception
        {
            if (form.getRowId() == null && form.getSurveyDesignId() == null)
            {
                errors.reject(ERROR_MSG, "Error: Please provide a rowId or surveyDesignId for the Survey to be created/updated.");
            }
            else if (form.getRowId() != null)
            {
                Survey survey = SurveyManager.get().getSurvey(getContainer(), getUser(), form.getRowId());

                // check to make sure the survey was found and has not been submitted
                if (survey == null)
                    errors.reject(ERROR_MSG, "Error: No survey record found for rowId " + form.getRowId() + ".");
                else
                {
                    form.setSurveyDesignId(survey.getSurveyDesignId());
                    form.setLabel(survey.getLabel());
                    form.setStatus(survey.getStatus());
                    form.setResponsesPk(survey.getResponsesPk());

                    if (survey.getSubmitted() != null)
                        errors.reject(ERROR_MSG, "Error: You are not allowed to update a survey that has already been submitted. Please contact the site administrator if the survey status for this record needs to be changed.");

                    SurveyDesign surveyDesign = SurveyManager.get().getSurveyDesign(getContainer(), getUser(), form.getSurveyDesignId());
                    if (surveyDesign != null)
                        _title = "Update " + surveyDesign.getLabel();
                }
            }
            else if (form.getSurveyDesignId() != null)
            {
                SurveyDesign surveyDesign = SurveyManager.get().getSurveyDesign(getContainer(), getUser(), form.getSurveyDesignId());
                if (surveyDesign == null)
                {
                    errors.reject(ERROR_MSG, "Error: No SurveyDesign record found for rowId " + form.getSurveyDesignId() + ".");
                }
                else
                {
                    form.setRowId(null); // no rowId for newly created surveys
                    _title = "Create " + surveyDesign.getLabel();
                }
            }

            return new JspView<SurveyForm>("/org/labkey/survey/view/surveyWizard.jsp", form, errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild(_title);
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    public class SurveyDesignAction extends SimpleViewAction<SurveyDesignForm>
    {
        private String _title = "Create Survey Design";

        @Override
        public ModelAndView getView(SurveyDesignForm form,BindException errors) throws Exception
        {
            if (form.getRowId() != 0)
            {
                SurveyDesign survey = SurveyManager.get().getSurveyDesign(getContainer(), getUser(), form.getRowId());
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
            SurveyDesign survey = getSurveyDesign(form);
            JSONObject errorInfo = null;

            try {
                // try to validate the metadata
                JsonParser parser = new JsonParser();
                parser.parse(form.getMetadata());
            }
            catch (JsonSyntaxException e)
            {
                String msg = "The survey questions must be valid JSON. Validation failed with the error : (%s)";
                String cause;
                if (e.getCause() != null)
                    cause = e.getCause().getMessage();
                else
                    cause = e.getMessage();

                // try to parse out the error location so we can position the editor selection
                errorInfo = parseErrorPosition(cause);
                errorInfo.put("message", String.format(msg, cause));
            }

            if (errorInfo == null)
            {
                SurveyManager.get().saveSurveyDesign(getContainer(), getUser(), survey);
                response.put("success", true);
            }
            else
            {
                response.put("errorInfo", errorInfo);
                response.put("success", false);
            }

            return response;
        }

        private JSONObject parseErrorPosition(String errorMsg)
        {
            JSONObject errors = new JSONObject();
            boolean capture = false;
            String key = null;

            for (String part : errorMsg.split(" "))
            {
                if ("line".equalsIgnoreCase(part))
                {
                    capture = true;
                    key = "line";
                }
                else if ("column".equalsIgnoreCase(part))
                {
                    capture = true;
                    key = "column";
                }
                else if (NumberUtils.isNumber(part))
                {
                    if (capture && key != null)
                        errors.put(key, NumberUtils.toInt(part));
                }
                else
                {
                    capture = false;
                    key = null;
                }
            }
            return errors;
        }
    }

    private SurveyDesign getSurveyDesign(SurveyDesignForm form)
    {
        SurveyDesign survey = new SurveyDesign();
        if (form.getRowId() != 0)
            survey = SurveyManager.get().getSurveyDesign(getContainer(), getUser(), form.getRowId());

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

    private Survey getSurvey(SurveyForm form)
    {
        Survey survey = new Survey();
        if (form.getRowId() != null)
            survey = SurveyManager.get().getSurvey(getContainer(), getUser(), form.getRowId());

        if (form.getLabel() != null)
            survey.setLabel(form.getLabel());
        survey.setContainerId(getContainer().getId());
        if (form.getStatus() != null)
            survey.setStatus(form.getStatus());
        if (form.getSurveyDesignId() != null)
            survey.setSurveyDesignId(form.getSurveyDesignId());

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
                SurveyDesign survey = getSurveyDesign(form);

                response.put("survey", new JSONObject(survey));
                response.put("success", true);
            }
            else
                response.put("success", false);

            return response;
        }
   }

    @RequiresPermissionClass(InsertPermission.class)
    public class UpdateSurveyResponseAction extends ApiAction<SurveyResponseForm>
    {
        @Override
        public ApiResponse execute(SurveyResponseForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            SurveyDesign surveyDesign = null;
            if (form.getSurveyDesignId() != null)
                surveyDesign = SurveyManager.get().getSurveyDesign(getContainer(), getUser(), form.getSurveyDesignId());

            if (surveyDesign != null)
            {
                TableInfo table = getSurveyAnswersTableInfo(surveyDesign);
                FieldKey pk = table.getAuditRowPk();

                if (table != null && pk != null)
                {
                    DbSchema dbschema = table.getSchema();
                    try
                    {
                        dbschema.getScope().ensureTransaction();

                        TableViewForm tvf = new TableViewForm(table);
                        Survey survey = getSurvey(form);

                        tvf.setViewContext(getViewContext());
                        tvf.setTypedValues(form.getResponses(), false);

                        if (!survey.isNew())
                        {
                            Map<String, Object> keys = new HashMap<String, Object>();

                            keys.put(pk.toString(), survey.getResponsesPk());
                            tvf.setOldValues(keys);
                        }

                        Map<String, Object> row = doInsertUpdate(tvf, errors, survey.isNew());

                        if (!row.isEmpty())
                        {
                            if (survey.isNew())
                            {
                                // update the survey instance with the key for the answers so that existing answers can
                                // be updated.
                                Object key = row.get(pk.toString());
                                survey.setResponsesPk(String.valueOf(key));
                            }
                            survey = SurveyManager.get().saveSurvey(getContainer(), getUser(), survey);

                            response.put("surveyResults", row);
                            response.put("survey", new JSONObject(survey));
                        }
                        response.put("success", !row.isEmpty());
                        dbschema.getScope().commitTransaction();
                    }
                    finally
                    {
                        dbschema.getScope().closeConnection();
                    }
                }
            }
            else
            {
                response.put("errorInfo", "No survey design found for the following rowId: " + form.getSurveyDesignId());
                response.put("success", false);
            }

            return response;
        }

        protected Map<String, Object> doInsertUpdate(TableViewForm form, BindException errors, boolean insert) throws Exception
        {
            TableInfo table = form.getTable();
            Map<String, Object> values = form.getTypedColumns();

            QueryUpdateService qus = table.getUpdateService();
            if (qus == null)
                throw new IllegalArgumentException("The query '" + table.getName() + "' in the schema '" + table.getSchema().getName() + "' is not updatable.");

            try
            {
                Map<String, Object> row;

                if (insert)
                {
                    BatchValidationException batchErrors = new BatchValidationException();
                    List<Map<String, Object>> updated = qus.insertRows(form.getUser(), form.getContainer(), Collections.singletonList(values), batchErrors, null);
                    if (batchErrors.hasErrors())
                        throw batchErrors;

                    assert(updated.size() == 1);
                    row = updated.get(0);
                }
                else
                {
                    Map<String, Object> oldValues = null;
                    if (form.getOldValues() instanceof Map)
                    {
                        oldValues = (Map<String, Object>)form.getOldValues();
                        if (!(oldValues instanceof CaseInsensitiveMapWrapper))
                            oldValues = new CaseInsensitiveMapWrapper<Object>(oldValues);
                    }
                    List<Map<String, Object>> updated = qus.updateRows(form.getUser(), form.getContainer(), Collections.singletonList(values), Collections.singletonList(oldValues), null);

                    assert(updated.size() == 1);
                    row = updated.get(0);
                }
                return row;
            }
            catch (SQLException x)
            {
                if (!SqlDialect.isConstraintException(x))
                    throw x;
                errors.reject(SpringActionController.ERROR_MSG, x.getMessage());
            }
            catch (BatchValidationException x)
            {
                x.addToErrors(errors);
            }
            catch (Exception x)
            {
                errors.reject(SpringActionController.ERROR_MSG, null == x.getMessage() ? x.toString() : x.getMessage());
                //ExceptionUtil.logExceptionToMothership(getViewContext().getRequest(), x);
            }
            return Collections.emptyMap();
        }
    }

    public static class SurveyResponseForm extends SurveyForm implements CustomApiForm
    {
        private Map<String, Object> _responses = new HashMap<String, Object>();
        private BeanObjectFactory<Survey> _factory = new BeanObjectFactory<Survey>(Survey.class);
        private Survey _bean;

        @Override
        public void bindProperties(Map<String, Object> props)
        {
            if (props.containsKey("rowId"))
                _rowId = NumberUtils.createInteger(String.valueOf(props.get("rowId")));
            if (props.containsKey("label"))
                _label = String.valueOf(props.get("label"));
            if (props.containsKey("surveyDesignId"))
                _surveyDesignId = NumberUtils.createInteger(String.valueOf(props.get("surveyDesignId")));
            if (props.containsKey("status"))
                _status = String.valueOf(props.get("status"));
            if (props.containsKey("responses"))
                _responses = (JSONObject)props.get("responses");

            //_bean = _factory.fromMap(props);
        }

        public Map<String, Object> getResponses()
        {
            return _responses;
        }
    }

    protected TableInfo getSurveyAnswersTableInfo(SurveyDesign survey)
    {
        UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), survey.getSchemaName());

        if (schema != null)
        {
            return schema.getTable(survey.getQueryName());
        }
        return null;
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetSurveyResponseAction extends ApiAction<SurveyForm>
    {
        @Override
        public ApiResponse execute(SurveyForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            Survey survey = getSurvey(form);

            if (!survey.isNew())
            {
                SurveyDesign surveyDesign = SurveyManager.get().getSurveyDesign(getContainer(), getUser(), survey.getSurveyDesignId());

                if (surveyDesign != null)
                {
                    TableInfo table = getSurveyAnswersTableInfo(surveyDesign);
                    FieldKey pk = table.getAuditRowPk();

                    if (table != null && pk != null)
                    {
                        QueryUpdateService qus = table.getUpdateService();
                        if (qus != null)
                        {
                            List<Map<String, Object>> keys = new ArrayList<Map<String, Object>>();
                            keys.add(Collections.singletonMap(pk.toString(), (Object)survey.getResponsesPk()));

                            List<Map<String, Object>> rows = qus.getRows(getUser(), getContainer(), keys);

                            assert rows.size() <= 1;

                            if (rows.size() == 1)
                            {
                                response.put("surveyResults", rows.get(0));
                                response.put("success", true);
                            }
                        }
                    }
                }
            }
            else
            {
                errors.reject(ERROR_MSG, "The requested survey responses have not been created yet.");
                response.put("success", false);
            }
            return response;
        }
    }
}