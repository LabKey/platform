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
package org.labkey.api.query;

import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.NullSafeBindException;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.SpringAttachmentFile;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveMapWrapper;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.Button;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.springframework.beans.PropertyValues;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * User: klum
 * Date: 9/29/12
 */
public abstract class UserSchemaAction extends FormViewAction<QueryUpdateForm>
{
    protected QueryForm _form;
    protected UserSchema _schema;
    protected TableInfo _table;

    public BindException bindParameters(PropertyValues m) throws Exception
    {
        _form = createQueryForm(getViewContext());
        _schema = _form.getSchema();
        if (null == _schema)
        {
            throw new NotFoundException("Schema not found");
        }
        _table = _schema.getTable(_form.getQueryName(), true, true);
        if (null == _table)
        {
            throw new NotFoundException("Query not found");
        }
        QueryUpdateForm command = new QueryUpdateForm(_table, getViewContext(), null);
        if (command.isBulkUpdate())
            command.setValidateRequired(false);
        BindException errors = new NullSafeBindException(new BeanUtilsPropertyBindingResult(command, "form"));
        command.validateBind(errors);
        return errors;
    }

    protected QueryForm createQueryForm(ViewContext context)
    {
        QueryForm form = new QueryForm();
        form.setViewContext(getViewContext());
        form.bindParameters(getViewContext().getBindPropertyValues());

        return form;
    }

    public void validateCommand(QueryUpdateForm target, Errors errors)
    {
    }

    protected ButtonBar createSubmitCancelButtonBar(QueryUpdateForm form)
    {
        ButtonBar bb = new ButtonBar();
        bb.setStyle(ButtonBar.Style.separateButtons);

        ActionButton btnSubmit = new ActionButton(getViewContext().getActionURL(), "Submit")
                .setActionType(ActionButton.Action.POST)
                .setDisableOnClick(true);

        return bb.add(
            btnSubmit,
            new Button.ButtonBuilder("Cancel").href(getCancelURL(form)).build()
        );
    }

    private ActionURL getActionURLParam(ActionURL.Param param)
    {
        String url = getViewContext().getActionURL().getParameter(param);
        if (url != null)
        {
            try
            {
                return new ActionURL(url);
            }
            catch (IllegalArgumentException ignored) {}
        }
        return null;
    }

    /*
     * NOTE (MAB) UserSchemaAction.appendNavTrail() uses getSuccessURL(null) for the nav trail link.
     * That's not really right, since the success url and the back/cancel url could be different.
     *
     * I changed getSuccessURL(null) to return cancelUrl if it is provided.
     */
    public ActionURL getSuccessURL(QueryUpdateForm form)
    {
        ActionURL returnURL = null;
        if (null == form)
            returnURL = getActionURLParam(ActionURL.Param.cancelUrl);
        if (null == returnURL)
            returnURL = getActionURLParam(ActionURL.Param.returnUrl);
        if (null == returnURL)
        {
            if (_schema != null && _table != null)
                returnURL = _schema.urlFor(QueryAction.executeQuery, _form.getQueryDef());
            else
                returnURL = QueryService.get().urlDefault(form.getContainer(), QueryAction.executeQuery, null, null);
        }
        return returnURL;
    }

    public ActionURL getCancelURL(QueryUpdateForm form)
    {
        ActionURL cancelURL = getActionURLParam(ActionURL.Param.cancelUrl);
        if (cancelURL == null)
            cancelURL = getActionURLParam(ActionURL.Param.returnUrl);
        if (cancelURL == null)
        {
            if (_schema != null && _table != null)
                cancelURL = _schema.urlFor(QueryAction.executeQuery, _form.getQueryDef());
            else
                cancelURL = QueryService.get().urlDefault(form.getContainer(), QueryAction.executeQuery, null, null);
        }
        return cancelURL;
    }

    public NavTree appendNavTrail(NavTree root)
    {
        if (_table != null)
            root.addChild(_table.getName(), getSuccessURL(null));
        return root;
    }

    protected List<Map<String, Object>> doInsertUpdate(QueryUpdateForm form, BindException errors, boolean insert) throws Exception
    {
        TableInfo table = form.getTable();
        if (!table.hasPermission(form.getUser(), insert ? InsertPermission.class : UpdatePermission.class))
        {
            throw new UnauthorizedException();
        }

        Map<String, Object> values = form.getTypedColumns();

        // Allow for attachment-based columns
        Map<String, MultipartFile> fileMap = getFileMap();
        if (null != fileMap)
        {
            for (String key : fileMap.keySet())
            {
                // Check if the column has already been processed
                if (!values.containsKey(key))
                {
                    SpringAttachmentFile file = new SpringAttachmentFile(fileMap.get(key));
                    form.setTypedValue(key, file.isEmpty() ? null : file);
                }
            }
        }

        values = form.getTypedColumns();

        QueryUpdateService qus = table.getUpdateService();
        if (qus == null)
            throw new IllegalArgumentException("The query '" + _table.getName() + "' in the schema '" + _schema.getName() + "' is not updatable.");


        List<Map<String, Object>> rows;
        List<Map<String, Object>> ret = null;

        if (form.isBulkUpdate())
        {
            rows = new ArrayList<>();

            // Merge the bulk edits back into the selected rows + validate
            String[] pkValues = form.getSelectedRows();

            if (pkValues == null || pkValues.length == 0)
                errors.reject(SpringActionController.ERROR_MSG, "Unable to update multiple rows. Please reselect the rows to update.");
            else if (table.getPkColumnNames().size() > 1)
            {
                errors.reject(SpringActionController.ERROR_MSG, "Unable to update multiple rows. Does not support update for multi-keyed tables.");
            }
            else
            {
                Map<String, Object> row;
                String pkName = table.getPkColumnNames().get(0);
                for (String pkValue : pkValues)
                {
                    row = new CaseInsensitiveHashMap<>();
                    for (Map.Entry<String, Object> entry : values.entrySet())
                    {
                        // If a value is left as null it is considered untouched for a given row
                        if (entry.getValue() != null)
                            row.put(entry.getKey(), entry.getValue());
                    }

                    row.put(pkName, pkValue);
                    rows.add(row);
                }
            }
        }
        else
        {
            rows = Collections.singletonList(values);
        }

        DbSchema dbschema = table.getSchema();
        try
        {
            try (DbScope.Transaction transaction = dbschema.getScope().ensureTransaction())
            {
                if (insert)
                {
                    BatchValidationException batchErrors = new BatchValidationException();
                    ret = qus.insertRows(form.getUser(), form.getContainer(), rows, batchErrors, null, null);
                    if (batchErrors.hasErrors())
                        batchErrors.addToErrors(errors);
                }
                else
                {
                    // Currently, bulkUpdate doesn't support oldValues due to the need to re-query...
                    if (form.isBulkUpdate())
                    {
                        ret = qus.updateRows(form.getUser(), form.getContainer(), rows, null, null, null);
                    }
                    else
                    {
                        Map<String, Object> oldValues = null;
                        if (form.getOldValues() instanceof Map)
                        {
                            oldValues = (Map<String, Object>) form.getOldValues();
                            if (!(oldValues instanceof CaseInsensitiveMapWrapper))
                                oldValues = new CaseInsensitiveMapWrapper<>(oldValues);
                        }

                        // 18292 - updateRows expects a null list in the case of an "empty" or null map.
                        List<Map<String, Object>> oldKeys = (oldValues == null || oldValues.isEmpty()) ? null : Collections.singletonList(oldValues);
                        ret = qus.updateRows(form.getUser(), form.getContainer(), rows, oldKeys, null, null);
                    }
                }
                if (!errors.hasErrors())
                    transaction.commit();       // Only commit if there were no errors
            }
            catch (SQLException x)
            {
                if (!RuntimeSQLException.isConstraintException(x))
                    throw x;
                errors.reject(SpringActionController.ERROR_MSG, x.getMessage());
            }
            catch (InvalidKeyException | DuplicateKeyException | DataIntegrityViolationException | RuntimeSQLException x)
            {
                errors.reject(SpringActionController.ERROR_MSG, x.getMessage());
            }
            catch (BatchValidationException x)
            {
                x.addToErrors(errors);
            }
            return ret;
        }
        catch (Exception x)
        {
            // Do this in a separate, outer try/catch so that we will have already committed or rolled back
            // the transaction we started. Otherwise, our database connection is likely in a bad state and can't be
            // reused when submitting the exception report.
            errors.reject(SpringActionController.ERROR_MSG, null == x.getMessage() ? x.toString() : x.getMessage());
            ExceptionUtil.logExceptionToMothership(getViewContext().getRequest(), x);
            return null;
        }
    }
}
