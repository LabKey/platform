/*
 * Copyright (c) 2012-2017 LabKey Corporation
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

import com.fasterxml.jackson.core.JsonProcessingException;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.beanutils.Converter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.action.ApiAction;
import org.labkey.api.action.ApiQueryResponse;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.CustomApiForm;
import org.labkey.api.action.ExtendedApiQueryResponse;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.AttachmentFile;
import org.labkey.api.collections.CaseInsensitiveMapWrapper;
import org.labkey.api.data.AuditConfigurable;
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
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.survey.SurveyUrls;
import org.labkey.api.survey.model.Survey;
import org.labkey.api.survey.model.SurveyDesign;
import org.labkey.api.survey.model.SurveyStatus;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
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
import java.util.stream.Collectors;

public class SurveyController extends SpringActionController implements SurveyUrls
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(SurveyController.class);

    public SurveyController()
    {
        setActionResolver(_actionResolver);
    }

    @Override
    public ActionURL getUpdateSurveyAction(Container container, int surveyId, int surveyDesignId)
    {
        return new ActionURL(UpdateSurveyAction.class, container).
                addParameter("rowId", surveyId).
                addParameter("surveyDesignId", surveyDesignId);
    }

    @Override
    public ActionURL getSurveyDesignAction(Container container, int surveyDesignId)
    {
        return new ActionURL(UpdateSurveyAction.class, container).addParameter("surveyDesignId", surveyDesignId);
    }

    @RequiresPermission(ReadPermission.class)
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

    @RequiresPermission(ReadPermission.class) // readpermissions because this action can be used to see the read-only view of a submitted request
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
                        _title = (form.isSubmitted() ? "Review: " : "Update: ") + surveyDesign.getLabel();
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
                    _title = "Create: " + surveyDesign.getLabel();
                }
            }

            return new JspView<>("/org/labkey/survey/view/surveyWizard.jsp", form, errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild(_title);
        }
    }

    @RequiresPermission(InsertPermission.class)
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
            JspView view = new JspView<>("/org/labkey/survey/view/surveyDesignWizard.jsp", form);

            return view;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild(_title);
        }
    }

    public static class SurveyDesignForm extends ReturnUrlForm
    {
        private int _rowId;
        private String _designId;
        private String _label;
        private String _description;
        private String _metadata;
        private String _schemaName;
        private String _queryName;
        private boolean _stringifyMetadata;

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

        public String getDescription()
        {
            return _description;
        }

        public void setDescription(String description)
        {
            _description = description;
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

        public boolean isStringifyMetadata()
        {
            return _stringifyMetadata;
        }

        public void setStringifyMetadata(boolean stringifyMetadata)
        {
            _stringifyMetadata = stringifyMetadata;
        }

        public String getDesignId()
        {
            return _designId;
        }

        public void setDesignId(String designId)
        {
            _designId = designId;
            if (NumberUtils.isDigits(_designId))
            {
                _rowId = NumberUtils.toInt(_designId);
            }
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class SaveSurveyTemplateAction extends ApiAction<SurveyDesignForm>
    {
        @Override
        public ApiResponse execute(SurveyDesignForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            SurveyDesign survey = getSurveyDesign(form);
            Map<String, Object> errorInfo = new HashMap<>();

            try {
                // try to validate the metadata
                String metadata = StringUtils.trimToNull(form.getMetadata());

                if (metadata != null)
                {
                    // if the json is valid, validate that it meets a minimal survey shape
                    String errorMsg = SurveyManager.validateSurveyMetadata(metadata);
                    if (errorMsg != null)
                        errorInfo.put("message", errorMsg);
                }
            }
            catch (JsonProcessingException e)
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

            if (errorInfo.isEmpty())
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
                else if (NumberUtils.isDigits(part)) // Should be positive integer
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
        else if (form.getDesignId() != null)
        {
            if (NumberUtils.isDigits(form.getDesignId()))
            {
                survey = SurveyManager.get().getSurveyDesign(getContainer(), getUser(), NumberUtils.toInt(form.getDesignId()));
            }
            else
            {
                survey = SurveyManager.get().getModuleSurveyDesign(getContainer(), form.getDesignId());
            }
        }

        if (survey != null)
        {
            if (form.getLabel() != null)
                survey.setLabel(form.getLabel());
            if (form.getDescription() != null)
                survey.setDescription(form.getDescription());
            survey.setContainerId(getContainer().getId());
            if (form.getSchemaName() != null)
                survey.setSchemaName(form.getSchemaName());
            if (form.getQueryName() != null)
                survey.setQueryName(form.getQueryName());
            if (form.getMetadata() != null)
                survey.setMetadata(form.getMetadata());
        }

        return survey;
    }

    private Survey getSurvey(SurveyForm form)
    {
        Survey survey = new Survey();
        if (form.getRowId() != null)
            survey = SurveyManager.get().getSurvey(getContainer(), getUser(), form.getRowId());

        if (survey != null)
        {
            if (form.getLabel() != null)
                survey.setLabel(form.getLabel());
            survey.setContainerId(getContainer().getId());
            if (form.getStatus() != null)
                survey.setStatus(form.getStatus());
            if (form.getSurveyDesignId() != null)
                survey.setSurveyDesignId(form.getSurveyDesignId());
            if (form.getResponsesPk() != null)
                survey.setResponsesPk(form.getResponsesPk());
        }

        return survey;
    }

    @RequiresPermission(ReadPermission.class)
    public class GetSurveyTemplateAction extends ApiAction<SurveyDesignForm>
    {
        @Override
        public ApiResponse execute(SurveyDesignForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            SurveyDesign survey = getSurveyDesign(form);

            if (survey != null)
            {
                response.put("survey", survey.toJSON(getUser(), form.isStringifyMetadata()));
                response.put("success", true);
            }
            else
            {
                errors.reject(ERROR_MSG, "No survey design found for the following rowId: " + form.getRowId());
                response.put("success", false);
            }

            return response;
        }
   }

    @RequiresPermission(InsertPermission.class)
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
                TableInfo table = SurveyManager.get().getSurveyResponsesTableInfo(getContainer(), getUser(), surveyDesign);
                if (table == null)
                {
                    response.put("errorInfo", "Table " + surveyDesign.getSchemaName() + "." + surveyDesign.getQueryName() + " could not be found in this container.");
                    response.put("success", false);
                    return response;
                }
                if (table.supportsAuditTracking())
                    ((AuditConfigurable)table).setAuditBehavior(AuditBehaviorType.DETAILED);
                FieldKey pk = SurveyManager.getSurveyPk(table);

                if (pk != null)
                {
                    DbSchema dbschema = table.getSchema();
                    try (DbScope.Transaction transaction = dbschema.getScope().ensureTransaction())
                    {
                        TableViewForm tvf = new TableViewForm(table)
                        {
                            @Override
                            public String getFormFieldName(@NotNull ColumnInfo column)
                            {
                                return column.getName();
                            }
                        };
                        Survey survey = getSurvey(form);

                        tvf.setViewContext(getViewContext());
                        tvf.setTypedValues(form.getResponses(), false);

                        // only allow saving changes to a submitted survey for project and site/app admin
                        Container project = getContainer().getProject();
                        boolean isAdmin = (project != null && project.hasPermission(getUser(), AdminPermission.class)) || getUser().hasRootAdminPermission();
                        if (survey.getSubmitted() != null && !isAdmin)
                        {
                            response.put("errorInfo", "You are not allowed to update a survey that has already been submitted.");
                            response.put("success", false);
                        }
                        else
                        {
                            if (survey.getResponsesPk() != null)
                            {
                                Map<String, Object> keys = new HashMap<>();

                                keys.put(pk.toString(), survey.getResponsesPk());
                                tvf.setOldValues(keys);
                            }

                            if (form.isSubmitted())
                            {
                                survey.setSubmittedBy(getUser().getUserId());
                                survey.setSubmitted(new Date());
                                survey.setStatus(SurveyStatus.Submitted.name());
                            }

                            List<Throwable> updateErrors = SurveyManager.get().fireBeforeUpdateSurveyResponses(getContainer(), getUser(), survey);
                            if (!updateErrors.isEmpty())
                            {
                                Throwable first = updateErrors.get(0);
                                response.put("errorInfo", first.getMessage());
                                response.put("success", false);
                            }
                            else
                            {
                                Map<String, Object> row = doInsertUpdate(tvf, errors, survey.getResponsesPk() == null);
                                if (errors.hasErrors())
                                {
                                    response.put("errorInfo", errors.getMessage());
                                    response.put("success", false);
                                }
                                else
                                {
                                    if (survey.isNew())
                                    {
                                        if (!row.isEmpty())
                                        {
                                            // update the survey instance with the key for the answers so that existing answers can
                                            // be updated.
                                            Object key = row.get(pk.toString());
                                            survey.setResponsesPk(String.valueOf(key));
                                        }

                                        // set the initial status to Pending
                                        if (!form.isSubmitted())
                                            survey.setStatus(SurveyStatus.Pending.name());
                                    }

                                    survey = SurveyManager.get().saveSurvey(getContainer(), getUser(), survey);
                                    SurveyManager.get().fireUpdateSurveyResponses(getContainer(), getUser(), survey, row);

                                    response.put("surveyResults", row);
                                    response.put("survey", new JSONObject(survey));

                                    transaction.commit();
                                    response.put("success", true);
                                }
                            }
                        }
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
        private Map<String, Object> _responses = new HashMap<>();
        private BeanObjectFactory<Survey> _factory = new BeanObjectFactory<>(Survey.class);
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
            if (props.containsKey("responsesPk"))
                _responsesPk = String.valueOf(props.get("responsesPk"));
            if (props.containsKey("submit"))
                _isSubmitted = Boolean.parseBoolean(String.valueOf(props.get("submit")));

            //_bean = _factory.fromMap(props);
        }

        public Map<String, Object> getResponses()
        {
            return _responses;
        }
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
                List<Map<String, Object>> updated = qus.insertRows(form.getUser(), form.getContainer(), Collections.singletonList(values), batchErrors, null, null);
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
                        oldValues = new CaseInsensitiveMapWrapper<>(oldValues);
                }
                List<Map<String, Object>> updated = qus.updateRows(form.getUser(), form.getContainer(), Collections.singletonList(values), Collections.singletonList(oldValues), null, null);

                assert(updated.size() == 1);
                row = updated.get(0);
            }
            return row;
        }
        catch (SQLException x)
        {
            if (!RuntimeSQLException.isConstraintException(x))
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

    @RequiresPermission(ReadPermission.class)
    public class GetSurveyResponseAction extends ApiAction<SurveyForm>
    {
        @Override
        public ApiResponse execute(SurveyForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            Survey survey = getSurvey(form);

            if (survey != null && !survey.isNew())
            {
                SurveyDesign surveyDesign = SurveyManager.get().getSurveyDesign(getContainer(), getUser(), survey.getSurveyDesignId());

                if (surveyDesign != null)
                {
                    TableInfo table = SurveyManager.get().getSurveyResponsesTableInfo(getContainer(), getUser(), surveyDesign);
                    if (table == null)
                    {
                        errors.reject(ERROR_MSG, "The survey responses table could not be found and may have been deleted.");
                        response.put("success", false);
                    }
                    else
                    {
                        FieldKey pk = SurveyManager.getSurveyPk(table);
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
                                List<FieldKey> fieldKeys = table.getColumns().stream().
                                        map(ColumnInfo::getFieldKey).
                                        collect(Collectors.toList());
                                settings.setFieldKeys(fieldKeys);
                                QueryView view = schema.createView(getViewContext(), settings, errors);

                                if (view != null)
                                {
                                    ApiQueryResponse queryResponse = new ExtendedApiQueryResponse(view, false, true,
                                            surveyDesign.getSchemaName(), surveyDesign.getQueryName(), settings.getOffset(), null,
                                            false, false, false);

                                    // add some of the survey record information to the response
                                    Map<String, Object> extraProps = new HashMap<>();
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

    @RequiresPermission(InsertPermission.class)
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
                        TableInfo table = SurveyManager.get().getSurveyResponsesTableInfo(getContainer(), getUser(), surveyDesign);
                        FieldKey pk = SurveyManager.getSurveyPk(table);

                        if (table != null && pk != null)
                        {
                            DbSchema dbschema = table.getSchema();
                            try (DbScope.Transaction transaction = dbschema.getScope().ensureTransaction())
                            {
                                ColumnInfo col = table.getColumn(FieldKey.fromParts(form.getQuestionName()));

                                // if the column is an attachment type, it only allows a single file per question (this is the basic case)
                                if (col != null && col.getJavaClass() == File.class)
                                {
                                    TableViewForm tvf = new TableViewForm(table)
                                    {
                                        @Override
                                        public String getFormFieldName(@NotNull ColumnInfo column)
                                        {
                                            return column.getName();
                                        }
                                    };

                                    tvf.setViewContext(getViewContext());

                                    AttachmentFile af = files.get(0);
                                    tvf.setTypedValues(Collections.singletonMap(form.getQuestionName(), (Object)af), false);

                                    // add the survey answer row pk
                                    Map<String, Object> keys = new HashMap<>();
                                    keys.put(pk.toString(), survey.getResponsesPk());
                                    tvf.setOldValues(keys);

                                    Map<String, Object> row = doInsertUpdate(tvf, errors, survey.isNew());

                                    response.put("success", !row.isEmpty());
                                    response.put("value", row.get(form.getQuestionName()));
                                }
                                transaction.commit();
                            }
                       }
                    }
                }
            }
            return response;
        }
    }

    @RequiresPermission(DeletePermission.class)
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
            _returnURL = form.getReturnActionURL();

            DbScope scope = SurveySchema.getInstance().getSchema().getScope();

            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                for (String survey : DataRegionSelection.getSelected(getViewContext(), true))
                {
                    int rowId = NumberUtils.toInt(survey);
                    SurveyManager.get().deleteSurvey(getContainer(), getUser(), rowId);
                }
                transaction.commit();
            }

            return true;
        }

        @Override
        public URLHelper getSuccessURL(QueryForm form)
        {
            return _returnURL;
        }
    }

    @RequiresPermission(DeletePermission.class)
    public class DeleteSurveyDesignsAction extends FormHandlerAction<QueryForm>
    {
        private ActionURL _returnURL;

        @Override
        public void validateCommand(QueryForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(QueryForm form, BindException errors) throws Exception
        {
            _returnURL = form.getReturnActionURL();

            DbScope scope = SurveySchema.getInstance().getSchema().getScope();

            try (DbScope.Transaction transaction = scope.ensureTransaction())
            {
                for (String surveyDesign : DataRegionSelection.getSelected(getViewContext(), true))
                {
                    int rowId = NumberUtils.toInt(surveyDesign);
                    SurveyManager.get().deleteSurveyDesign(getContainer(), getUser(), rowId, true);
                }
                transaction.commit();
            }

            return true;
        }

        @Override
        public URLHelper getSuccessURL(QueryForm form)
        {
            return _returnURL;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class GetValidDesignQueries extends ApiAction<QueryForm>
    {
        @Override
        public ApiResponse execute(QueryForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            List<Map<String, Object>> queries = new ArrayList<>();

            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), form.getSchemaName());

            if (schema != null)
            {
                for (String tableName : schema.getVisibleTableNames())
                {
                    TableInfo table = schema.getTable(tableName);

                    if (table != null && table.getUpdateService() != null)
                    {
                        if (SurveyManager.getSurveyPk(table) != null)
                        {
                            Map<String, Object> query = new HashMap<>();

                            query.put("name", tableName);
                            query.put("isUserDefined", false);

                            queries.add(query);
                        }
                    }
                }
            }
            response.put("queries", queries);

            return response;
        }
    }
}
