/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.query.controllers.dbuserschema;

import org.labkey.api.data.*;
import org.labkey.api.data.collections.CaseInsensitiveMap;
import org.labkey.api.query.*;
import org.labkey.api.security.ACL;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.view.*;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.BaseViewAction;
import org.labkey.query.controllers.QueryControllerSpring;
import org.labkey.query.data.DbUserSchemaTable;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.beans.PropertyValues;
import org.apache.commons.beanutils.ConvertUtils;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;


public class DbUserSchemaController extends SpringActionController
{
    public enum Action
    {
        insert,
        update,
        details,
    }

    static DefaultActionResolver _actionResolver = new DefaultActionResolver(DbUserSchemaController.class);

    public DbUserSchemaController() throws Exception
    {
        super();
        setActionResolver(_actionResolver);
    }


    abstract class UserSchemaAction extends FormViewAction<QueryUpdateForm>
    {
        String _schemaName;
        String _queryName;
        
        public BindException bindParameters(PropertyValues m) throws Exception
        {
            QueryForm form = new QueryForm(null);
            form.setViewContext(getViewContext());
            BaseViewAction.springBindParameters(form, getViewContext().getRequest());

            UserSchema schema = form.getSchema();
            if (null == schema)
            {
                HttpView.throwNotFound("Schema not found");
                return null;
            }
            TableInfo table = schema.getTable(form.getQueryName(), null);
            if (null == table)
            {
                HttpView.throwNotFound("Query not found");
                return null;
            }
            _schemaName = form.getSchemaName().toString();
            _queryName = form.getQueryName();
            QueryUpdateForm command = new QueryUpdateForm(table, getViewContext().getRequest());
            BindException errors = new BindException(new BeanUtilsPropertyBindingResult(command, "form"));
            command.validateBind(errors);
            return errors;
        }

        public void validateCommand(QueryUpdateForm target, Errors errors)
        {
        }

        public ActionURL getSuccessURL(QueryUpdateForm form)
        {
            return QueryService.get().urlFor(getContainer(), QueryAction.executeQuery, _schemaName, _queryName);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild(_queryName, getSuccessURL(null));
            return root;
        }
    }


    @RequiresPermission(ACL.PERM_INSERT)
    public class InsertAction extends UserSchemaAction
    {
        public ModelAndView getView(QueryUpdateForm tableForm, boolean reshow, BindException errors) throws Exception
        {
            ButtonBar bb = new ButtonBar();
            ActionButton btnSubmit = new ActionButton(getViewContext().getActionURL(), "Submit");
            bb.add(btnSubmit);
            InsertView view = new InsertView(tableForm, errors);
            view.getDataRegion().setButtonBar(bb);
            return view;
        }

        public boolean handlePost(QueryUpdateForm tableForm, BindException errors) throws Exception
        {
            doInsertUpdate(tableForm, errors, true);
            return 0 == errors.getErrorCount();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            super.appendNavTrail(root);
            root.addChild("Insert");
            return root;
        }
    }


    @RequiresPermission(ACL.PERM_UPDATE)
    public class UpdateAction extends UserSchemaAction
    {
        public ModelAndView getView(QueryUpdateForm tableForm, boolean reshow, BindException errors) throws Exception
        {
            ButtonBar bb = new ButtonBar();
            ActionButton btnSubmit = new ActionButton(getViewContext().getActionURL(), "Submit");
            bb.add(btnSubmit);
            UpdateView view = new UpdateView(tableForm, errors);
            view.getDataRegion().setButtonBar(bb);
            return view;
        }

        public boolean handlePost(QueryUpdateForm tableForm, BindException errors) throws Exception
        {
            doInsertUpdate(tableForm, errors, false);
            return 0 == errors.getErrorCount();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            super.appendNavTrail(root);
            root.addChild("Edit");
            return root;
        }
    }


    protected void doInsertUpdate(QueryUpdateForm form, BindException errors, boolean insert) throws Exception
    {
        DbUserSchemaTable table = (DbUserSchemaTable)form.getTable();
        
        if (!table.hasPermission(getUser(), insert ? ACL.PERM_INSERT : ACL.PERM_UPDATE))
            HttpView.throwUnauthorized();

        Map<String, Object> values = new HashMap<String, Object>();
        for (ColumnInfo column : form.getTable().getColumns())
        {
            if (form.hasTypedValue(column))
            {
                values.put(column.getName(), form.getTypedValue(column));
            }
        }
        if (table.getContainerId() != null)
        {
            values.put("container", table.getContainerId());
        }

        try
        {
            if (insert)
            {
                Table.insert(getUser(), table.getRealTable(), values);
            }
            else
            {
                Object pkVal = form.getPkVal();
                if (pkVal instanceof String)
                {
                    ColumnInfo col = table.getRealTable().getPkColumns().get(0);
                    pkVal = ConvertUtils.convert((String)pkVal, col.getJavaClass());
                }
                if (table.getContainerId() != null)
                {
                    Map<String, Object> oldValues = Table.selectObject(table.getRealTable(), pkVal, Map.class);
                    if (oldValues == null)
                    {
                        errors.reject(ERROR_MSG, "The existing row was not found.");
                        return;
                    }
                    Object oldContainer = new CaseInsensitiveMap(oldValues).get("container");
                    if (!table.getContainerId().equals(oldContainer))
                    {
                        errors.reject(ERROR_MSG, "The row is from the wrong container");
                        return;
                    }
                }
                Table.update(getUser(), table.getRealTable(), values, pkVal, null);
            }
        }
        catch (SQLException x)
        {
            if (!SqlDialect.isConstraintException(x))
                throw x;
            errors.reject(ERROR_MSG, QueryControllerSpring.getMessage(table.getSchema().getSqlDialect() , x));
        }        
    }
}
