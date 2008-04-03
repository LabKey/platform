package org.labkey.query.controllers;

import org.apache.beehive.netui.pageflow.Forward;
import org.apache.beehive.netui.pageflow.annotations.Jpf;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.struts.action.ActionError;
import org.apache.struts.action.ActionErrors;
import org.labkey.api.data.ActionButton;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.jsp.FormPage;
import org.labkey.api.query.*;
import org.labkey.api.security.ACL;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.query.CustomViewImpl;
import org.labkey.query.TableXML;
import org.labkey.query.design.DgMessage;
import org.labkey.query.design.Errors;
import org.labkey.query.design.ErrorsDocument;
import org.labkey.query.persist.CstmView;
import org.labkey.query.persist.DbUserSchemaDef;
import org.labkey.query.persist.QueryManager;
import org.labkey.query.sql.QParser;
import org.springframework.validation.BindException;

import javax.servlet.ServletException;
import java.util.*;

@Jpf.Controller(messageBundles = {@Jpf.MessageBundle(bundlePath = "messages.Validation")})
public class QueryController extends ViewController
{
    private static final Logger _log = Logger.getLogger(QueryController.class);

    protected boolean queryExists(QueryForm form)
    {
        return form.getSchema() != null && form.getSchema().getTable(form.getQueryName(), null) != null;
    }

    protected void assertQueryExists(QueryForm form) throws ServletException
    {
        if (form.getSchema() == null)
            HttpView.throwNotFound("Could not find schema: " + form.getSchemaName());
        if (!queryExists(form))
            HttpView.throwNotFound("Query '" + form.getQueryName() + "' in schema '" + form.getSchemaName() + "' doesn't exist.");
    }

    @Jpf.Action
    protected Forward begin(QueryForm form) throws Exception
    {
        assert false : "should not get here";
        return null;
    }





    protected Forward renderInTemplate(QueryForm form, String page) throws Exception
    {
        return renderInTemplate(form, FormPage.getView(QueryController.class, form, page));
    }

    protected Forward renderInTemplate(QueryForm form, HttpView view) throws Exception
    {
        return renderInTemplate(view, getContainer(), getNavTrailConfig(form));
    }

    protected NavTrailConfig getNavTrailConfig(QueryForm form) throws Exception
    {
        NavTrailConfig ret = new NavTrailConfig(getViewContext());
        if (form.getSchema() == null)
        {
            return ret;
        }
        if (form.getQueryDef() != null)
        {
            ret.setTitle(form.getQueryDef().getName());
            ret.setExtraChildren(new NavTree(form.getSchemaName() + " queries", form.getSchema().urlFor(QueryAction.begin)));
        }
        else if (form.getSchemaName() != null)
        {
            ret.setTitle(form.getSchemaName());
        }
        return ret;
    }

    protected boolean addError(String message)
    {
        PageFlowUtil.getActionErrors(getRequest(), true).add("main", new ActionError("Error", message));
        return true;
    }


    protected boolean addError(Exception e)
    {
        return addError("An exception occurred: " +  e.toString());
    }

    
    @Jpf.Action
    protected Forward tableInfo(TableInfoForm form) throws Exception
    {
        requiresPermission(ACL.PERM_READ);
        TablesDocument ret = TablesDocument.Factory.newInstance();
        TablesDocument.Tables tables = ret.addNewTables();

        FieldKey[] fields = form.getFieldKeys();
        if (fields.length != 0)
        {
            TableInfo tinfo = QueryView.create(form).getTable();
            Map<FieldKey, ColumnInfo> columnMap = CustomViewImpl.getColumnInfos(tinfo, Arrays.asList(fields));
            TableXML.initTable(tables.addNewTable(), tinfo, null, columnMap.values());
        }

        for (FieldKey tableKey : form.getTableKeys())
        {
            TableInfo tableInfo = form.getTableInfo(tableKey);
            TableType xbTable = tables.addNewTable();
            TableXML.initTable(xbTable, tableInfo, tableKey);
        }
        getResponse().setContentType("text/xml");
        getResponse().getWriter().write(ret.toString());

        return null;
    }

    @Jpf.Action
    protected Forward deleteView(DeleteViewForm form) throws Exception
    {
        requiresPermission(ACL.PERM_READ);
        requiresLogin();
        CustomView view = form.getCustomView();
        if (view != null)
        {
            if (view.getOwner() == null)
            {
                requiresPermission(ACL.PERM_ADMIN);
            }
            view.delete(getUser(), getRequest());
        }
        String returnURL = getRequest().getParameter(QueryParam.srcURL.toString());
        if (returnURL != null)
        {
            return new ViewForward(new ActionURL(returnURL));
        }
        return new ViewForward(form.urlFor(QueryAction.begin));
    }

    @Jpf.Action
    protected Forward queryInfo(TableInfoForm form) throws Exception
    {
        requiresPermission(ACL.PERM_READ);
        return null;
    }

    @Jpf.Action
    protected Forward checkSyntax() throws Exception
    {
        String sql = getRequest().getParameter("sql");
        if (sql == null)
        {
            sql = PageFlowUtil.getStreamContentsAsString(getRequest().getInputStream());
        }
        ErrorsDocument ret = ErrorsDocument.Factory.newInstance();
        Errors xbErrors = ret.addNewErrors();
        List<QueryParseException> errors = new ArrayList();
        try
        {
            QParser.parseExpr(sql, errors);
        }
        catch (Throwable t)
        {
            _log.error("Error", t);
            errors.add(new QueryParseException("Unhandled exception: " + t, null, 0, 0));
        }
        for (QueryParseException e : errors)
        {
            DgMessage msg = xbErrors.addNewError();
            msg.setStringValue(e.getMessage());
            msg.setLine(e.getLine());
        }
        getResponse().setContentType("text/xml");
        getResponse().getWriter().write(ret.toString());
        return null;
    }

    @Jpf.Action
    protected Forward manageViews(QueryForm form) throws Exception
    {
        requiresPermission(ACL.PERM_READ);
        requiresLogin();

        return renderInTemplate(form, "manageViews.jsp");
    }

    @Jpf.Action
    protected Forward internalDeleteView(InternalViewForm form) throws Exception
    {
        requiresPermission(ACL.PERM_READ);
        if (isPost())
        {
            CstmView view = form.getViewAndCheckPermission();
            QueryManager.get().delete(getUser(), view);
            return new ViewForward("query", "manageViews", getContainer());
        }
        return renderInTemplate(FormPage.getView(QueryController.class, form, "internalDeleteView.jsp"), getContainer(), "Confirm Delete");
    }

    @Jpf.Action
    protected Forward internalSourceView(InternalSourceViewForm form) throws Exception
    {
        requiresPermission(ACL.PERM_READ);
        CstmView view = form.getViewAndCheckPermission();
        if (isPost())
        {
            view.setColumns(form.ff_columnList);
            view.setFilter(form.ff_filter);
            QueryManager.get().update(getUser(), view);
            return new ViewForward("query", "manageViews", getContainer());
        }
        form.ff_columnList = view.getColumns();
        form.ff_filter = view.getFilter();
        return renderInTemplate(FormPage.getView(QueryController.class, form, "internalSourceView.jsp"), getContainer(), "Edit Source of Grid View");
    }

    @Jpf.Action
    protected Forward internalNewView(InternalNewViewForm form) throws Exception
    {
        requiresLogin();
        requiresPermission(ACL.PERM_READ);
        if (isPost())
        {
            Forward ret = doNewView(form);
            if (ret != null)
                return ret;
        }
        return renderInTemplate(FormPage.getView(QueryController.class, form, "internalNewView.jsp"), getContainer(), "Create New Grid View");
    }

    protected Forward doNewView(InternalNewViewForm form) throws Exception
    {
        boolean errors = false;
        if (StringUtils.trimToNull(form.ff_schemaName) == null)
        {
            errors = addError("Schema name cannot be blank.");
        }
        if (StringUtils.trimToNull(form.ff_queryName) == null)
        {
            errors = addError("Query name cannot be blank");
        }
        if (errors)
            return null;
        if (form.ff_share)
        {
            requiresPermission(ACL.PERM_ADMIN);
        }
        CstmView[] existing = QueryManager.get().getColumnLists(getContainer(), form.ff_schemaName, form.ff_queryName, form.ff_viewName, form.ff_share ? null : getUser(), false);
        CstmView view = null;
        if (existing.length != 0)
        {
            view = existing[0];
        }
        else
        {
            view = new CstmView();
            view.setSchema(form.ff_schemaName);
            view.setQueryName(form.ff_queryName);
            view.setName(form.ff_viewName);
            view.setContainerId(getContainer().getId());
            if (form.ff_share)
            {
                view.setCustomViewOwner(null);
            }
            else
            {
                view.setCustomViewOwner(getUser().getUserId());
            }
            if (form.ff_inherit)
            {
                view.setFlags(QueryManager.get().setCanInherit(view.getFlags(), form.ff_inherit));
            }
            InternalViewForm.checkEdit(getViewContext(), view);
            try
            {
                view = QueryManager.get().insert(getUser(), view);
            }
            catch (Exception e)
            {
                _log.error("Error", e);
                errors = addError("An exception occurred: " + e);
                return null;
            }
        }
        ActionURL forward = new ActionURL("query", "internalSourceView", getContainer());
        forward.addParameter("customViewId", Integer.toString(view.getCustomViewId()));
        return new ViewForward(forward);
    }

    @Jpf.Action
    protected Forward admin(QueryForm form) throws Exception
    {
        requiresGlobalAdmin();
        return renderInTemplate(form, "admin.jsp");
    }

    @Jpf.Action
    protected Forward adminEditDbUserSchema(DbUserSchemaForm form) throws Exception
    {
        BindException errors = null;
        requiresGlobalAdmin();
        ActionURL fwd = new ActionURL("query", "admin", form.getContainer());

        if (isPost())
        {
            // don't let doInsert throw unhelpful SQLException
            ActionErrors err = PageFlowUtil.getActionErrors(getRequest(), true);
            form.populateValues(err);
            if (err.size() == 0)
            {
                form.doUpdate();
                return new ViewForward(fwd);
            }
        }
        UpdateView view = new UpdateView(form,errors);
        ButtonBar bb = new ButtonBar();
        bb.add(new ActionButton("adminEditDbUserSchema.post", "Update"));
        bb.add(new ActionButton("Cancel", fwd));
        ActionURL urlDelete = new ActionURL("query", "adminDeleteDbUserSchema", form.getContainer());
        urlDelete.addParameter("dbUserSchemaId", Integer.toString(form.getBean().getDbUserSchemaId()));
        bb.add(new ActionButton("Delete", urlDelete));
        view.getDataRegion().setButtonBar(bb);

        return renderInTemplate(view, getContainer(), "Update Schema");
    }


    @Jpf.Action
    protected Forward adminReloadDbUserSchema(DbUserSchemaForm form) throws Exception
    {
        requiresGlobalAdmin();
        form.refreshFromDb(false);
        DbUserSchemaDef def = form.getBean();
        ActionURL fwd = new ActionURL("query", "admin", form.getContainer());
        fwd.addParameter("reloadedSchema", def.getUserSchemaName());
        QueryManager.get().reloadDbUserSchema(def);

        return new ViewForward(fwd);
    }


    @Jpf.Action
    protected Forward adminNewDbUserSchema(DbUserSchemaForm form) throws Exception
    {
        BindException errors = null;
        requiresGlobalAdmin();
        ActionURL fwd = new ActionURL("query", "admin", form.getContainer());

        if (isPost())
        {
            // don't let doInsert throw unhelpful SQLException
            ActionErrors err = PageFlowUtil.getActionErrors(getRequest(), true);
            form.populateValues(err);
            if (err.size() == 0)
            {
                form.doInsert();
                return new ViewForward(fwd);
            }
        }
        InsertView view = new InsertView(form,errors);
        Map<String, Object> initialValues = new HashMap<String, Object>();
        initialValues.put("DbContainer", getContainer().getId());
        view.setInitialValues(initialValues);
        ButtonBar bb = new ButtonBar();
        bb.add(new ActionButton("adminNewDbUserSchema.post", "Create"));
        bb.add(new ActionButton("Cancel", fwd));
        view.getDataRegion().setButtonBar(bb);

        return renderInTemplate(view, getContainer(), "Define Schema");
    }


    @Jpf.Action
    protected Forward adminDeleteDbUserSchema(DbUserSchemaForm form) throws Exception
    {
        requiresGlobalAdmin();
        form.refreshFromDb(false);
        ActionURL fwd = new ActionURL("query", "admin", form.getContainer());
        if (isPost())
        {
            QueryManager.get().delete(getUser(), form.getBean());
            return new ViewForward(fwd);
        }
        return renderInTemplate(FormPage.getView(QueryController.class, form, "adminDeleteDbUserSchema.jsp"), getContainer(), "Delete Schema");
    }
}
