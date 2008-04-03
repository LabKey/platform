package org.labkey.query.controllers.dbuserschema;

import org.apache.beehive.netui.pageflow.Forward;
import org.apache.beehive.netui.pageflow.annotations.Jpf;
import org.apache.struts.action.ActionErrors;
import org.labkey.api.data.*;
import org.labkey.api.data.collections.CaseInsensitiveMap;
import org.labkey.api.query.*;
import org.labkey.api.security.ACL;
import org.labkey.api.view.*;
import org.labkey.query.controllers.QueryControllerSpring;
import org.labkey.query.data.DbUserSchemaTable;
import org.springframework.validation.BindException;

import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;


@Jpf.Controller(messageBundles = {@Jpf.MessageBundle(bundlePath = "messages.Validation")})
public class DbUserSchemaController extends ViewController
{
    public enum Action
    {
        insert,
        update,
        details,
    }

    @Jpf.Action
    protected Forward begin()
    {
        return null;
    }

    private NavTrailConfig getNavTrailConfig(QueryForm form, String title)
    {
        NavTrailConfig ret = new NavTrailConfig(getViewContext());
        ret.setTitle(title);
        ret.add(new NavTree(form.getQueryName(), form.urlFor(QueryAction.executeQuery)));
        return ret;
    }

    @Jpf.Action
    protected Forward insert(DbUserSchemaForm form) throws Exception
    {
        BindException errors = null;
        requiresPermission(ACL.PERM_INSERT);
        UserSchema schema = form.getSchema();
        if (null == schema)
            return HttpView.throwNotFound("Schema not found");
        TableInfo table = schema.getTable(form.getQueryName(), null);
        if (null == table)
            return HttpView.throwNotFound("Query not found");

        QueryUpdateForm tableForm = new QueryUpdateForm(table, getRequest());
        if (isPost())
        {
            Forward forward = doInsertUpdate((DbUserSchemaTable) table, form, true);
            if (forward != null)
                return forward;
        }
        ButtonBar bb = new ButtonBar();
        ActionButton btnSubmit = new ActionButton(getActionURL().toString(), "Submit");
        bb.add(btnSubmit);
        InsertView view = new InsertView(tableForm, errors);
        view.getDataRegion().setButtonBar(bb);
        return renderInTemplate(view, getContainer(), getNavTrailConfig(form, "Insert"));

    }

    @Jpf.Action
    protected Forward update(DbUserSchemaForm form) throws Exception
    {
        BindException errors = null;
        requiresPermission(ACL.PERM_UPDATE);
        UserSchema schema = form.getSchema();

        if (schema == null)
        {
            return HttpView.throwNotFound("Could not find schema " + form.getSchemaName());
        }

        TableInfo table = schema.getTable(form.getQueryName(), null);

        if (table == null)
        {
            return HttpView.throwNotFound("Could not find table " + form.getQueryName());
        }
        
        QueryUpdateForm tableForm = new QueryUpdateForm(table, getRequest());
        if (isPost())
        {
            Forward forward = doInsertUpdate((DbUserSchemaTable) table, form, false);
            if (forward != null)
                return forward;
        }
        ButtonBar bb = new ButtonBar();
        ActionButton btnSubmit = new ActionButton(getActionURL().toString(), "Submit");
        bb.add(btnSubmit);
        UpdateView view = new UpdateView(tableForm, errors);
        view.getDataRegion().setButtonBar(bb);
        return renderInTemplate(view, getContainer(), getNavTrailConfig(form, "Edit"));
    }

    protected Forward doInsertUpdate(DbUserSchemaTable dbUserSchemaTable, QueryForm queryForm, boolean insert) throws Exception
    {
        if (!dbUserSchemaTable.hasPermission(getUser(), insert ? ACL.PERM_INSERT : ACL.PERM_UPDATE))
            HttpView.throwUnauthorized();
        QueryUpdateForm form = new QueryUpdateForm(dbUserSchemaTable, getRequest());
        ActionErrors errors = form.validate(null, getRequest());
        if (!errors.isEmpty())
            return null;

        Map<String, Object> values = new HashMap<String, Object>();
        for (ColumnInfo column : dbUserSchemaTable.getColumns())
        {
            if (form.hasTypedValue(column))
            {
                values.put(column.getName(), form.getTypedValue(column));
            }
        }
        if (dbUserSchemaTable.getContainerId() != null)
        {
            values.put("container", dbUserSchemaTable.getContainerId());
        }

        try
        {
            if (insert)
            {
                Table.insert(getUser(), dbUserSchemaTable.getRealTable(), values);
            }
            else
            {
                if (dbUserSchemaTable.getContainerId() != null)
                {
                    Map<String, Object> oldValues = Table.selectObject(dbUserSchemaTable.getRealTable(), form.getPkVal(), Map.class);
                    if (oldValues == null)
                    {
                        addError("The existing row was not found.");
                        return null;
                    }
                    Object oldContainer = new CaseInsensitiveMap(oldValues).get("container");
                    if (!dbUserSchemaTable.getContainerId().equals(oldContainer))
                    {
                        addError("The row is from the wrong container");
                        return null;
                    }
                }
                Table.update(getUser(), dbUserSchemaTable.getRealTable(), values, form.getPkVal(), null);
            }
        }
        catch (SQLException x)
        {
            if (!QueryControllerSpring.isConstraintException(x))
                throw x;
            addError(QueryControllerSpring.getMessage(dbUserSchemaTable.getSchema().getSqlDialect() , x));
            return null;
        }
        
        return new ViewForward(QueryService.get().urlFor(getContainer(), QueryAction.executeQuery, queryForm.getSchemaName(), queryForm.getQueryName()));
    }
}
