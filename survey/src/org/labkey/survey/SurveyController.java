/*
 * Copyright (c) 2012-2013 LabKey Corporation
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
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.Converter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiQueryResponse;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.action.ExtendedApiQueryResponse;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.collections.CaseInsensitiveMapWrapper;
import org.labkey.api.data.BeanObjectFactory;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableViewForm;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.ReturnURLString;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.survey.model.Survey;
import org.labkey.api.survey.model.SurveyDesign;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
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
                    form.setSubmitted(survey.getSubmitted() != null);

                    SurveyDesign surveyDesign = SurveyManager.get().getSurveyDesign(getContainer(), getUser(), form.getSurveyDesignId());
                    if (surveyDesign != null)
                    {
                        _title = (form.isSubmitted() ? "Review " : "Update ") + surveyDesign.getLabel();
                    }
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
                String metadata = StringUtils.trimToNull(form.getMetadata());

                if (metadata != null)
                {
                    JsonParser parser = new JsonParser();
                    parser.parse(metadata);
                }
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
                TableInfo table = getSurveyAnswersTableInfo(surveyDesign, getContainer());
                table.setAuditBehavior(AuditBehaviorType.DETAILED);

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

                        // only allow saving changes to a submitted survey for project and site admin
                        Container project = getContainer().getProject();
                        boolean isAdmin = (project != null && project.hasPermission(getUser(), AdminPermission.class)) || getUser().isAdministrator();
                        if (survey.getSubmitted() != null && !isAdmin)
                        {
                            response.put("errorInfo", "You are not allowed to update a survey that has already been submitted.");
                            response.put("success", false);
                        }
                        else
                        {
                            if (!survey.isNew())
                            {
                                Map<String, Object> keys = new HashMap<String, Object>();

                                keys.put(pk.toString(), survey.getResponsesPk());
                                tvf.setOldValues(keys);
                            }

                            if (form.isSubmitted())
                            {
                                survey.setSubmittedBy(getUser().getUserId());
                                survey.setSubmitted(new Date());
                                survey.setStatus("Submitted");
                            }

                            Map<String, Object> row = doInsertUpdate(tvf, errors, survey.isNew());

                            if (survey.isNew())
                            {
                                if (!row.isEmpty())
                                {
                                    // update the survey instance with the key for the answers so that existing answers can
                                    // be updated.
                                    Object key = row.get(pk.toString());
                                    survey.setResponsesPk(String.valueOf(key));
                                }

                                // set the initial status to Draft
                                if (!form.isSubmitted())
                                    survey.setStatus("Draft");
                            }

                            survey = SurveyManager.get().saveSurvey(getContainer(), getUser(), survey);

                            response.put("surveyResults", row);
                            response.put("survey", new JSONObject(survey));

                            dbschema.getScope().commitTransaction();
                            response.put("success", true);
                        }
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
            if (props.containsKey("submit"))
                _isSubmitted = Boolean.parseBoolean(String.valueOf(props.get("submit")));

            //_bean = _factory.fromMap(props);
        }

        public Map<String, Object> getResponses()
        {
            return _responses;
        }
    }

    protected TableInfo getSurveyAnswersTableInfo(SurveyDesign survey, Container container)
    {
        //Container container = ContainerManager.getForId(survey.getContainerId());
        if (container != null)
        {
            UserSchema schema = QueryService.get().getUserSchema(getUser(), container, survey.getSchemaName());

            if (schema != null)
            {
                return schema.getTable(survey.getQueryName());
            }
        }
        return null;
    }

    protected Map<String, Object> doInsertUpdate(TableViewForm form, BindException errors, boolean insert) throws Exception
    {
        TableInfo table = form.getTable();
        Map<String, Object> values = form.getTypedColumns();

        // nothing to update
        if (!insert && values.isEmpty())
            return Collections.emptyMap();

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
                    TableInfo table = getSurveyAnswersTableInfo(surveyDesign, getContainer());
                    if (table == null)
                    {
                        errors.reject(ERROR_MSG, "The survey responses table could not be found and may have been deleted.");
                        response.put("success", false);
                    }
                    else
                    {
                        FieldKey pk = table.getAuditRowPk();
                        UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), surveyDesign.getSchemaName());

                        if (schema != null)
                        {
                            QuerySettings settings = schema.getSettings(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT, surveyDesign.getQueryName());

                            Object value = survey.getResponsesPk();
                            if (value == null)
                            {
                                errors.reject(ERROR_MSG, "The survey responses primary key could not be found and may have been deleted.");
                                response.put("success", false);
                            }
                            else
                            {
                                ColumnInfo col = table.getColumn(pk);

                                if (!value.getClass().equals(col.getJavaClass()))
                                {
                                    Class targetType = col.getJavaClass();
                                    Converter converter = ConvertUtils.lookup(targetType);
                                    if(null == converter)
                                        throw new ConversionException("Cannot convert the value for column " + col.getName() + " from a " + value.getClass().toString() + " into a " + targetType.toString());

                                    value = converter.convert(targetType, value);
                                }
                                settings.setBaseFilter(new SimpleFilter(pk, value));
                                QueryView view = schema.createView(getViewContext(), settings, errors);

                                if (view != null)
                                {
                                    ApiQueryResponse queryResponse = new ExtendedApiQueryResponse(view, getViewContext(), false, true,
                                            surveyDesign.getSchemaName(), surveyDesign.getQueryName(), settings.getOffset(), null,
                                            false, false, false);

                                    // add some of the survey record information to the response
                                    Map<String, Object> extraProps = new HashMap<String, Object>();
                                    extraProps.put("rowId", survey.getRowId());
                                    extraProps.put("label", survey.getLabel());
                                    extraProps.put("status", survey.getStatus());
                                    if (survey.getSubmitted() != null)
                                        extraProps.put("submitted", survey.getSubmitted());
                                    if (survey.getSubmittedBy() != null)
                                    {
                                        User submitUser = UserManager.getUser(survey.getSubmittedBy());
                                        if (submitUser != null)
                                            extraProps.put("submittedBy", submitUser.getDisplayName(getUser()));
                                    }
                                    queryResponse.setExtraReturnProperties(extraProps);

                                    return queryResponse;
                                }
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

    public static class SurveyAttachmentForm extends SurveyForm
    {
        private String _questionName;
        private String _entityId;

        public String getQuestionName()
        {
            return _questionName;
        }

        public void setQuestionName(String questionName)
        {
            _questionName = questionName;
        }

        public String getEntityId()
        {
            return _entityId;
        }

        public void setEntityId(String entityId)
        {
            _entityId = entityId;
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
     public class UpdateSurveyResponseAttachmentsAction extends ApiAction<SurveyAttachmentForm>
    {
        @Override
        public ApiResponse execute(SurveyAttachmentForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            Survey survey = getSurvey(form);

            if (!survey.isNew())
            {
                List<AttachmentFile> files = getAttachmentFileList();
                if (!files.isEmpty())
                {
                    SurveyDesign surveyDesign = SurveyManager.get().getSurveyDesign(getContainer(), getUser(), survey.getSurveyDesignId());

                    if (surveyDesign != null)
                    {
                        TableInfo table = getSurveyAnswersTableInfo(surveyDesign, getContainer());
                        FieldKey pk = table.getAuditRowPk();

                        if (table != null && pk != null)
                        {
                            DbSchema dbschema = table.getSchema();
                            try
                            {
                                dbschema.getScope().ensureTransaction();
                                ColumnInfo col = table.getColumn(FieldKey.fromParts(form.getQuestionName()));

                                // if the column is an attachment type, it only allows a single file per question (this is the basic case)
                                if (col.getJavaClass() == File.class)
                                {
                                    TableViewForm tvf = new TableViewForm(table);

                                    tvf.setViewContext(getViewContext());

                                    AttachmentFile af = files.get(0);
                                    tvf.setTypedValues(Collections.singletonMap(form.getQuestionName(), (Object)af), false);

                                    // add the survey answer row pk
                                    Map<String, Object> keys = new HashMap<String, Object>();
                                    keys.put(pk.toString(), survey.getResponsesPk());
                                    tvf.setOldValues(keys);

                                    Map<String, Object> row = doInsertUpdate(tvf, errors, survey.isNew());

                                    response.put("success", row.isEmpty());
                                    response.put("value", row.get(form.getQuestionName()));
                                }

/*
                                if (form.getEntityId() == null)
                                {
                                    // create an entity id so we can associate attachments with this question
                                    form.setEntityId(GUID.makeGUID());

                                    TableViewForm tvf = new TableViewForm(table);

                                    tvf.setViewContext(getViewContext());
//                                    tvf.setTypedValues(Collections.singletonMap(form.getQuestionName(), (Object)form.getEntityId()), false);

                                    AttachmentFile af = files.get(0);
                                    tvf.setTypedValues(Collections.singletonMap(form.getQuestionName(), (Object)af), false);

                                    // add the survey answer row pk
                                    Map<String, Object> keys = new HashMap<String, Object>();
                                    keys.put(pk.toString(), survey.getResponsesPk());
                                    tvf.setOldValues(keys);

                                    Map<String, Object> row = doInsertUpdate(tvf, errors, survey.isNew());
                                    response.put("success", row.isEmpty());
                                }

                                // and save the attachments
                                SurveyQuestion question = new SurveyQuestion(getContainer().getId(), form.getEntityId());
                                AttachmentService.get().addAttachments(question, files, getUser());
*/
                                dbschema.getScope().commitTransaction();
                            }
                            finally
                            {
                                dbschema.getScope().closeConnection();
                            }
                       }
                    }
                }
            }
            return response;
        }
    }

    @RequiresPermissionClass(DeletePermission.class)
    public class DeleteSurveysAction extends FormHandlerAction<QueryForm>
    {
        private ActionURL _returnURL;

        @Override
        public void validateCommand(QueryForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(QueryForm form, BindException errors) throws Exception
        {
            String returnURL = (String)this.getProperty(QueryParam.srcURL);
            if (returnURL != null)
                _returnURL = new ActionURL(returnURL);

            DbScope scope = SurveySchema.getInstance().getSchema().getScope();

            try {
                scope.ensureTransaction();

                for (String surveyDesign : DataRegionSelection.getSelected(getViewContext(), true))
                {
                    int rowId = NumberUtils.toInt(surveyDesign);
                    SurveyManager.get().deleteSurveyDesign(getContainer(), getUser(), rowId, true);
                }
                scope.commitTransaction();
            }
            catch (SQLException x)
            {
                throw new RuntimeSQLException(x);
            }
            finally
            {
                scope.closeConnection();
            }

            return true;
        }

        @Override
        public URLHelper getSuccessURL(QueryForm form)
        {
            return _returnURL;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetValidDesignQueries extends ApiAction<QueryForm>
    {
        @Override
        public ApiResponse execute(QueryForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            List<Map<String, Object>> queries = new ArrayList<Map<String, Object>>();

            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), form.getSchemaName());

            if (schema != null)
            {
                for (String tableName : schema.getVisibleTableNames())
                {
                    TableInfo table = schema.getTable(tableName);

                    if (table.getUpdateService() != null)
                    {
                        Map<String, Object> query = new HashMap<String, Object>();

                        query.put("name", tableName);
                        query.put("isUserDefined", false);

                        queries.add(query);
                    }
                }
            }
            response.put("queries", queries);

            return response;
        }
    }
}