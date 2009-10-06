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

import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryAction;
import org.labkey.api.query.QueryForm;
import org.labkey.api.query.QueryUpdateForm;
import org.labkey.api.query.UserSchema;
import org.labkey.api.security.RequiresPermissionClass;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.StringExpression;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;


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

    abstract class DbUserRedirectAction extends SimpleRedirectAction
    {
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            QueryForm form = new QueryForm(null);
            form.setViewContext(getViewContext());
            form.bindParameters(getViewContext().getBindPropertyValues());

            UserSchema schema = form.getSchema();
            if (null == schema)
            {
                HttpView.throwNotFound("Schema not found");
                return null;
            }
            TableInfo table = schema.getTable(form.getQueryName());
            if (null == table)
            {
                HttpView.throwNotFound("Query not found");
                return null;
            }

            return getRedirectURL(form, schema, table);
        }

        abstract ActionURL getRedirectURL(QueryForm form, UserSchema schema, TableInfo table);
    }

    @RequiresPermissionClass(InsertPermission.class)
    public class InsertAction extends DbUserRedirectAction
    {
        ActionURL getRedirectURL(QueryForm form, UserSchema schema, TableInfo table)
        {
            return schema.urlFor(QueryAction.insertQueryRow, form.getQueryDef());
        }
    }

    @RequiresPermissionClass(UpdatePermission.class)
    public class UpdateAction extends DbUserRedirectAction
    {
        ActionURL getRedirectURL(QueryForm form, UserSchema schema, TableInfo table)
        {
            QueryUpdateForm command = new QueryUpdateForm(table, getViewContext(), null);

            StringExpression expr = schema.urlExpr(QueryAction.updateQueryRow, form.getQueryDef());
            assert expr != null;
            return new ActionURL(expr.eval(command.getTypedValues()));
        }
    }
}
