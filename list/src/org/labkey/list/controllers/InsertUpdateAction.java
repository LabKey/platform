/*
 * Copyright (c) 2013 LabKey Corporation
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
package org.labkey.list.controllers;

import org.apache.commons.beanutils.ConversionException;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.SpringAttachmentFile;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.MvUtil;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.defaults.DefaultValueService;
import org.labkey.api.exp.MvFieldWrapper;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.NotFoundException;
import org.labkey.list.controllers.ListController.ListQueryUpdateForm;
import org.labkey.list.model.ListDefinitionImpl;
import org.labkey.list.model.ListQuerySchema;
import org.labkey.list.model.OntologyListQueryUpdateService;
import org.labkey.list.view.ListDefinitionForm;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.labkey.api.data.TableInfo.TriggerType.INSERT;
import static org.labkey.api.data.TableInfo.TriggerType.UPDATE;

/**
 * User: Nick
 * Date: 5/10/13
 * Time: 4:52 PM
 */
public abstract class InsertUpdateAction extends FormViewAction<ListDefinitionForm>
{
    private URLHelper _returnURL;
    // We don't want to construct multiple forms in the reshow case, otherwise we'll double up all the error messages,
    // so stash the form we create in the post handler here.  See #9031.  TODO: Fix this in 10.1, see #9033
    private ListController.ListQueryUpdateForm _form;
    protected ListDefinition _list;

    protected abstract DataView getDataView(ListQueryUpdateForm tableForm, URLHelper returnURL, BindException errors);
    protected abstract boolean isInsert();

    protected Container getContainer()
    {
        return getViewContext().getContainer();
    }

    protected User getUser()
    {
        return getViewContext().getUser();
    }

    public void validateCommand(ListDefinitionForm form, Errors errors)
    {
        _list = form.getList();
    }

    public ModelAndView getView(ListDefinitionForm form, boolean reshow, BindException errors) throws Exception
    {
        _list = form.getList();
        TableInfo table = _list.getTable(getUser());
        ListQueryUpdateForm tableForm = null != _form ? _form : new ListQueryUpdateForm(table, getViewContext(), _list, errors);

        return getDataView(tableForm, form.getReturnURLHelper(), errors);
    }

    public boolean handlePost(ListDefinitionForm form, BindException errors) throws Exception
    {
        if (!ListDefinitionImpl.ontologyBased())
        {
            throw new IllegalStateException("The List InsertUpdateAction cannot be used for hard table lists.");
        }

        if (errors.hasErrors())
            return false;

        TableInfo table = _list.getTable(getUser());

        ListQueryUpdateForm tableForm = new ListQueryUpdateForm(table, getViewContext(), _list, errors);
        _form = tableForm;

        Map<String, MultipartFile> fileMap = getFileMap();
        if (null != fileMap)
        {
            for (String key : fileMap.keySet())
            {
                SpringAttachmentFile file = new SpringAttachmentFile(fileMap.get(key));
                tableForm.setTypedValue(key, file.isEmpty() ? null : file);
            }
        }

        // Issue 10792: Validation scripts should run before errors are generated for list import
        //if (errors.hasErrors())
        //    return false;

        ListItem item = null;

        try
        {
            ExperimentService.get().ensureTransaction();

            BatchValidationException batchErrors = new BatchValidationException();

            TableInfo.TriggerType triggerType;
            Map<String, Object> newValues = tableForm.getTypedColumns(true);
            Map<String, Object> oldValues = null;
            if (isInsert())
            {
                item = _list.createListItem();
                triggerType = INSERT;
            }
            else
            {
                item = _list.getListItem(tableForm.getPkVal());
                if (item == null)
                    throw new NotFoundException("The existing list item was not found.");
                oldValues = OntologyListQueryUpdateService.toMap(_list, item);
                triggerType = UPDATE;
            }
            Object oldKey = item.getKey();

            Domain domain = _list.getDomain();
            table.fireBatchTrigger(getContainer(), triggerType, true, batchErrors, null);
            try
            {
                table.fireRowTrigger(getContainer(), triggerType, true, 0, newValues, oldValues, null);

                for (ColumnInfo column : tableForm.getTable().getColumns())
                {
                    if (!newValues.containsKey(column.getName()))
                        continue;

                    try
                    {
                        Object formValue = newValues.get(column.getName());
                        String mvIndicator = null;
                        if (column.isMvEnabled())
                        {
                            ColumnInfo mvColumn = tableForm.getTable().getColumn(column.getMvColumnName());
                            mvIndicator = (String)newValues.get(mvColumn.getName());
                        }
                        DomainProperty property = domain.getPropertyByName(column.getName());
                        if (column.getName().equals(_list.getKeyName()))
                        {
                            item.setKey(formValue);
                        }

                        if (mvIndicator == null)
                        {
                            if (property != null)
                            {
                                item.setProperty(property, formValue);
                            }
                        }
                        else
                        {
                            MvFieldWrapper mvWrapper = new MvFieldWrapper(MvUtil.getMvIndicators(getContainer()), formValue, mvIndicator);
                            item.setProperty(property, mvWrapper);
                        }
                    }
                    catch (ConversionException cex)
                    {
                        ValidationException vex = new ValidationException(cex.getMessage(), column.getName());
                        vex.setSchemaName(ListQuerySchema.NAME);
                        vex.setQueryName(_list.getName());
                        vex.setRow(newValues);
                        vex.setRowNumber(0);
                    }
                }

                if (!errors.hasErrors() && !batchErrors.hasErrors())
                    item.save(getUser());

                table.fireRowTrigger(getContainer(), triggerType, false, 0, OntologyListQueryUpdateService.toMap(_list, item), oldValues, null);
            }
            catch (ValidationException vex)
            {
                batchErrors.addRowError(vex.fillIn(ListQuerySchema.NAME, _list.getName(), newValues, 0));
            }

            table.fireBatchTrigger(getContainer(), triggerType, false, batchErrors, null);

            if (errors.hasErrors())
                return false;

            ExperimentService.get().commitTransaction();

            _returnURL = form.getReturnURLHelper();

            // If user changed the PK then change returnURL to match
            if (!Objects.equals(oldKey, item.getKey()) && null != _returnURL.getParameter("pk"))
                _returnURL.replaceParameter("pk", item.getKey().toString());

            if (isInsert())
            {
                DomainProperty[] properties = domain.getProperties();
                Map<String, Object> requestMap = tableForm.getTypedValues();
                Map<DomainProperty, Object> dataMap = new HashMap<DomainProperty, Object>(requestMap.size());
                for (DomainProperty property : properties)
                {
                    ColumnInfo tempCol = new ColumnInfo(property.getName());
                    dataMap.put(property, requestMap.get(tableForm.getFormFieldName(tempCol)));
                }
                DefaultValueService.get().setDefaultValues(getContainer(), dataMap, getUser());
            }

            return true;
        }
        catch (BatchValidationException bvex)
        {
            bvex.addToErrors(errors);
        }
        catch (SQLException e)
        {
            handleSqlException(e, errors, item);
        }
        catch (RuntimeSQLException re)
        {
            //issue 14368: SQL errors getting rethrown as RuntimeSQLException should be caught
            SQLException e = re.getSQLException();
            handleSqlException(e, errors, item);
        }
        catch (IOException e)
        {
            if (e instanceof AttachmentService.FileTooLargeException || e instanceof AttachmentService.DuplicateFilenameException)
            {
                errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
            }
            else
            {
                throw e;
            }
        }
        finally
        {
            ExperimentService.get().closeTransaction();
        }

        return false;
    }

    private void handleSqlException (SQLException e, BindException errors, ListItem item)
    {
        if (SqlDialect.isConstraintException(e))
        {
            if (null != item && null != item.getKey())
                errors.reject(SpringActionController.ERROR_MSG, "Error: A record having key \"" + item.getKey() + "\" already exists.");
            else
                errors.reject(SpringActionController.ERROR_MSG, "Error: A record with that key already exists.");
        }
        else if (e instanceof Table.OptimisticConflictException)
        {
            errors.reject(SpringActionController.ERROR_MSG, "Error: The record was updated prior to your changes.  It is recommended that you refresh the page to ensure the values are accurate.");
        }
        else
        {
            errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
        }
    }

    public URLHelper getSuccessURL(ListDefinitionForm listDefinitionForm)
    {
        return _returnURL;
    }

    protected ButtonBar getButtonBar(ActionURL submitURL, URLHelper returnURL)
    {
        ButtonBar bb = new ButtonBar();
        ActionButton btnSubmit = new ActionButton(submitURL, "Submit");
        ActionButton btnCancel = new ActionButton("Cancel", returnURL);
        bb.add(btnSubmit);
        bb.add(btnCancel);
        bb.setStyle(ButtonBar.Style.separateButtons);

        return bb;
    }
}
