/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.query.controllers;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.beehive.netui.pageflow.annotations.Jpf;
import org.apache.beehive.netui.pageflow.Forward;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.*;
import org.labkey.api.data.*;
import org.labkey.api.exp.list.ListDefinition;
import org.labkey.api.exp.list.ListItem;
import org.labkey.api.exp.list.ListService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.jsp.FormPage;
import org.labkey.api.query.*;
import org.labkey.api.security.*;
import org.labkey.api.util.AppProps;
import org.labkey.api.util.DateUtil;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.study.StudyService;
import org.labkey.query.CustomViewImpl;
import org.labkey.query.QueryDefinitionImpl;
import org.labkey.query.data.Query;
import org.labkey.query.design.QueryDocument;
import org.labkey.query.design.ViewDocument;
import org.labkey.query.persist.DbUserSchemaDef;
import org.labkey.query.persist.QueryDef;
import org.labkey.query.persist.QueryManager;
import org.labkey.query.view.DbUserSchema;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class QueryControllerSpring extends SpringActionController
{
    static DefaultActionResolver _actionResolver = new BeehivePortingActionResolver(QueryController.class, QueryControllerSpring.class);

    public QueryControllerSpring() throws Exception
    {
        super();
        setActionResolver(_actionResolver);
    }

    private ActionURL actionURL(QueryAction action)
    {
        return new ActionURL("query", action.name(), getContainer());
    }

    private ActionURL actionURL(QueryAction action, QueryParam param, String value)
    {
        return new ActionURL("query", action.name(), getContainer()).addParameter(param, value);
    }

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

    @RequiresPermission(ACL.PERM_READ)
    public class BeginAction extends QueryControllerSpring.QueryViewAction
    {
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            _form = form;
            return FormPage.getView(QueryControllerSpring.class, _form, "begin.jsp");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Query start page", actionURL(QueryAction.begin));
            return root;
        }
    }

    @RequiresPermission(ACL.PERM_READ)
    public class SchemaAction extends QueryControllerSpring.QueryViewAction
    {
        public SchemaAction() {}

        SchemaAction(QueryForm form)
        {
            _form = form;
        }

        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            _form = form;
            if (null == form.getSchemaName())
                return HttpView.redirect(actionURL(QueryAction.begin));
            return FormPage.getView(QueryControllerSpring.class, _form, "schema.jsp");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            String schemaName = _form.getSchemaName();
            (new QueryControllerSpring.BeginAction()).appendNavTrail(root)
                .addChild(schemaName, actionURL(QueryAction.schema, QueryParam.schemaName, schemaName));
            return root;
        }
    }


    @RequiresPermission(ACL.PERM_ADMIN)
    public class NewQueryAction extends FormViewAction<NewQueryForm>
    {
        NewQueryForm _form;
        ActionURL _successUrl;

        public void validateCommand(NewQueryForm target, org.springframework.validation.Errors errors)
        {
        }

        public ModelAndView getView(NewQueryForm form, boolean reshow, BindException errors) throws Exception
        {
            _form = form;
            return FormPage.getView(QueryControllerSpring.class, form, "newQuery.jsp");
        }

        public boolean handlePost(NewQueryForm form, BindException errors) throws Exception
        {
            if (!form.getSchema().canCreate())
                HttpView.throwUnauthorized();
            try
            {
                if (form.ff_baseTableName == null || "".equals(form.ff_baseTableName))
                {
                    errors.reject(ERROR_MSG, "You must select a base table or query name.");
                    return false;
                }

                UserSchema schema = form.getSchema();
                QueryDef existing = QueryManager.get().getQueryDef(getContainer(), form.getSchemaName(), form.ff_newQueryName);
                if (existing != null)
                {
                    errors.reject(ERROR_MSG, "The query '" + form.ff_newQueryName + "' already exists.");
                    return false;
                }
                QueryDefinition newDef = QueryService.get().createQueryDef(getContainer(), form.getSchemaName(), form.ff_newQueryName);
                Query query = new Query(schema);
                query.setRootTable(FieldKey.fromParts(form.ff_baseTableName));
                newDef.setSql(query.getQueryText());
                newDef.save(getUser(), getContainer());

                _successUrl = newDef.urlFor(form.ff_redirect);
                return true;
            }
            catch (Exception e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return false;
            }
        }

        public ActionURL getSuccessURL(NewQueryForm newQueryForm)
        {
            return _successUrl;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public ModelAndView getView(NewQueryForm form, BindException errors) throws Exception
        {
            if (!form.getSchema().canCreate())
                HttpView.throwUnauthorized();
            getPageConfig().setFocus("forms[0].ff_newQueryName");
            return FormPage.getView(QueryControllerSpring.class, form, "newQuery.jsp");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            (new QueryControllerSpring.SchemaAction(_form)).appendNavTrail(root)
                    .addChild("New Query", actionURL(QueryAction.newQuery));
            return root;
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class SourceQueryAction extends FormViewAction<SourceForm>
    {
        SourceForm _form;

        public void validateCommand(SourceForm target, Errors errors)
        {
        }

        public ModelAndView getView(SourceForm form, boolean reshow, BindException errors) throws Exception
        {
            _form = form;
            if (form.ff_queryText == null)
            {
                form.ff_queryText = form.getQueryDef().getSql();
                form.ff_metadataText = form.getQueryDef().getMetadataXml();
            }
            try
            {
                QueryDefinition query = form.getQueryDef();
                for (QueryException qpe : query.getParseErrors(form.getSchema()))
                {
                    errors.reject(ERROR_MSG, qpe.getMessage());
                }
            }
            catch (Exception e)
            {
                errors.reject("ERROR_MSG", e.toString());
                Logger.getLogger(QueryControllerSpring.class).error("Error", e);
            }

            return new JspView<SourceForm>(QueryControllerSpring.class, "sourceQuery.jsp", form, errors);
        }

        public boolean handlePost(SourceForm form, BindException errors) throws Exception
        {
            _form = form;

            if (!form.canEdit())
                return false;

            try
            {
                QueryDefinition query = form.getQueryDef();
                query.setSql(form.ff_queryText);
                query.setMetadataXml(form.ff_metadataText);
                query.save(getUser(), getContainer());
                return true;
            }
            catch (Exception e)
            {
                errors.reject("An exception occurred: " + e);
                Logger.getLogger(QueryControllerSpring.class).error("Error", e);
                return false;
            }
        }

        public ActionURL getSuccessURL(SourceForm sourceForm)
        {
            return _form.getForwardURL();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            (new QueryControllerSpring.SchemaAction(_form)).appendNavTrail(root)
                    .addChild("Edit " + _form.getQueryName(), _form.urlFor(QueryAction.sourceQuery));
            return root;
        }
    }


    @RequiresPermission(ACL.PERM_DELETE)
    public class DeleteQueryAction extends ConfirmAction<QueryForm>
    {
        QueryForm _form;

        public ModelAndView getConfirmView(QueryForm queryForm, BindException errors) throws Exception
        {
            return FormPage.getView(QueryControllerSpring.class, queryForm, "deleteQuerySpring.jsp");
        }

        public boolean handlePost(QueryForm form, BindException errors) throws Exception
        {
            _form = form;
            QueryDefinition d = form.getQueryDef();
            if (null == d)
                return false;
            d.delete(getUser());
            return true;
        }

        public void validateCommand(QueryForm queryForm, Errors errors)
        {
        }

        public ActionURL getSuccessURL(QueryForm queryForm)
        {
            return _form.getSchema().urlFor(QueryAction.schema);
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class ExecuteQueryAction extends QueryControllerSpring.QueryViewAction
    {
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            _form = form;

            if (form.getSchema() == null)
            {
                HttpView.throwNotFound("Schema not found: " + form.getSchemaName());
                return null;
            }
            QueryDefinition query = form.getQueryDef();
            if (null == query)
            {
                HttpView.throwNotFound("Query '" + form.getQueryName() + "' in schema '" + form.getSchemaName() + "' not found");
                return null;
            }

            QueryView queryView = QueryView.create(form);
            if (isPrint())
            {
                queryView.setPrintView(true);
                getPageConfig().setTemplate(PageConfig.Template.Print);
            }
            queryView.setShadeAlternatingRows(true);
            queryView.setShowColumnSeparators(true);
            return queryView;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            (new QueryControllerSpring.SchemaAction(_form)).appendNavTrail(root);
            root.addChild(_form.getQueryName(), _form.urlFor(QueryAction.executeQuery));
            return root;
        }
    }


    // for backwards compat same as _executeQuery.view ?_print=1
    @RequiresPermission(ACL.PERM_READ)
    public class PrintRowsAction extends QueryControllerSpring.ExecuteQueryAction
    {
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            _print = true;
            return super.getView(form, errors);
        }
    }


    abstract class _ExportQuery extends SimpleViewAction<QueryForm>
    {
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            assertQueryExists(form);
            QueryView view = QueryView.create(form);
            getPageConfig().setTemplate(PageConfig.Template.None);
            _export(form, view);
            return null;
        }

        abstract void _export(QueryForm form, QueryView view) throws Exception;

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class ExportRowsExcelAction extends _ExportQuery
    {
        void _export(QueryForm form, QueryView view) throws Exception
        {
            view.exportToExcel(getViewContext().getResponse());
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class ExportExcelTemplateAction extends _ExportQuery
    {
        void _export(QueryForm form, QueryView view) throws Exception
        {
            view.exportToExcelTemplate(getViewContext().getResponse());
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class ExportRowsTsvAction extends _ExportQuery
    {
        void _export(QueryForm form, QueryView view) throws Exception
        {
            view.exportToTsv(getViewContext().getResponse(), form.isExportAsWebPage());
        }
    }

    @RequiresPermission(ACL.PERM_NONE)
    public class ExcelWebQueryAction extends ExportRowsTsvAction
    {
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            if (!getContainer().hasPermission(getUser(), ACL.PERM_READ))
            {
                if (!getUser().isGuest())
                    HttpView.throwUnauthorized();
                getViewContext().getResponse().setHeader("WWW-Authenticate", "Basic realm=\"" + AppProps.getInstance().getSystemDescription() + "\"");
                getViewContext().getResponse().setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                return null;
            }

            // Bug 5610. Excel web queries don't work over SSL if caching is disabled,
            // so we need to allow caching so that Excel can read from IE on Windows.
            HttpServletResponse response = getViewContext().getResponse();
            // Set the headers to allow the client to cache, but not proxies
            response.setHeader("Pragma", "private");
            response.setHeader("Cache-Control", "private");

            assertQueryExists(form);
            QueryView view = QueryView.create(form);
            getPageConfig().setTemplate(PageConfig.Template.None);
            view.exportToExcelWebQuery(getViewContext().getResponse());
            return null;
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class ExcelWebQueryDefinitionAction extends SimpleViewAction<QueryForm>
    {
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            assertQueryExists(form);
            getPageConfig().setTemplate(PageConfig.Template.None);
            assertQueryExists(form);
            String queryViewActionURL = form.getQueryViewActionURL();
            ActionURL url;
            if (queryViewActionURL != null)
            {
                url = new ActionURL(queryViewActionURL);
            }
            else
            {
                url = getViewContext().cloneActionURL();
                url.setAction("excelWebQuery.view");
            }
            getViewContext().getResponse().setContentType("text/x-ms-iqy");
            String filename =  form.getQueryName() + "_" + DateUtil.toISO(System.currentTimeMillis()) + ".iqy";
            filename = filename.replace(':', '_');
            getViewContext().getResponse().setHeader("Content-disposition", "attachment; filename=\"" + filename +"\"");
            PrintWriter writer = getViewContext().getResponse().getWriter();
            writer.println("WEB");
            writer.println("1");
            writer.println(url.getURIString());
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class MetadataQueryAction extends FormViewAction<MetadataForm>
    {
        QueryDefinition _query = null;
        MetadataForm _form = null;
        
        public void validateCommand(MetadataForm target, Errors errors)
        {
        }

        public ModelAndView getView(MetadataForm form, boolean reshow, BindException errors) throws Exception
        {
            assertQueryExists(form);
            _form = form;
            _query = _form.getQueryDef();
            return FormPage.getView(QueryControllerSpring.class, form, errors, "metadata.jsp");
        }

        public boolean handlePost(MetadataForm form, BindException errors) throws Exception
        {
            if (form.canEdit())
            {
                try
                {
                    _query = form.getQueryDef();
                    _query.setMetadataXml(form.ff_metadataText);
                    _query.save(getUser(), getContainer());
                    return true;
                }
                catch (Exception e)
                {
                    errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
                    return false;
                }
            }
            return true;
        }

        public ActionURL getSuccessURL(MetadataForm metadataForm)
        {
            return _query.urlFor(QueryAction.metadataQuery);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            (new SchemaAction(_form)).appendNavTrail(root);
            root.addChild("Query Metadata", _query.urlFor(QueryAction.metadataQuery));
            return root;
        }
    }


    @RequiresPermission(ACL.PERM_ADMIN)
    public class DesignQueryAction extends FormViewAction<DesignForm>
    {
        DesignForm _form;
        QueryDefinitionImpl _queryDef;

        public void validateCommand(DesignForm target, Errors errors)
        {
        }

        public ModelAndView getView(DesignForm form, boolean reshow, BindException errors) throws Exception
        {
            _form = form;
            _queryDef = (QueryDefinitionImpl) form.getQueryDef();
            if (null == _queryDef)
                HttpView.throwNotFound();

            if (form.ff_designXML == null)
            {
                Query q = _queryDef.getQuery(form.getSchema());
                if (q.isAggregate() || q.hasSubSelect())
                {
                    errors.reject(ERROR_MSG, "Query is too complicated for design view");
                    SourceQueryAction a = new SourceQueryAction();
                    a.setViewContext(getViewContext());
                    return a.getView(new SourceForm(getViewContext()), reshow, errors);
                }
                QueryDocument queryDoc = _queryDef.getDesignDocument(form.getSchema());
                if (queryDoc == null)
                    return HttpView.redirect(_queryDef.urlFor(QueryAction.sourceQuery));
                form.ff_designXML = queryDoc.toString();
            }
            return FormPage.getView(QueryControllerSpring.class, form, "designQuery.jsp");
        }

        public boolean handlePost(DesignForm form, BindException errors) throws Exception
        {
            _form = form;
            _queryDef = (QueryDefinitionImpl) form.getQueryDef();
            if (null == _queryDef)
                HttpView.throwNotFound();

            if (form.ff_dirty)
            {
                List<QueryException> qerrors = new ArrayList<QueryException>();
                _queryDef.updateDesignDocument(form.getSchema(), QueryDocument.Factory.parse(form.ff_designXML), qerrors);
                if (qerrors.size() == 0)
                {
                    _queryDef.save(getUser(), getContainer());
                }
                for (QueryException qerror : qerrors)
                {
                    errors.reject(ERROR_MSG, qerror.getMessage());
                }
            }
            return errors.getErrorCount() == 0;
        }

        public ActionURL getSuccessURL(DesignForm designForm)
        {
            return _queryDef.urlFor(_form.ff_redirect);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            (new QueryControllerSpring.SchemaAction(_form)).appendNavTrail(root);
            String name = _queryDef.getName();
            root.addChild("Design query " + name, getViewContext().getActionURL());
            return root;
        }
    }


//    private NavTrailConfig getNavTrailConfig(QueryForm form) throws Exception
//    {
//        NavTrailConfig ret = new NavTrailConfig(getViewContext());
//        if (form.getSchema() == null)
//        {
//            return ret;
//        }
//        if (form.getQueryDef() != null)
//        {
//            ret.setTitle(form.getQueryDef().getName());
//            ret.setExtraChildren(new NavTree(form.getSchemaName() + " queries", form.getSchema().urlFor(QueryAction.begin)));
//        }
//        else if (form.getSchemaName() != null)
//        {
//            ret.setTitle(form.getSchemaName());
//        }
//        return ret;
//    }


    @RequiresPermission(ACL.PERM_READ)
    public class ChooseColumnsAction extends FormViewAction<ChooseColumnsForm>
    {
        ActionURL _returnURL = null;

        protected boolean canEdit(ChooseColumnsForm form, Errors errors)
        {
            CustomView view = form.getQueryDef().getCustomView(getUser(), getViewContext().getRequest(), form.ff_columnListName);
            if (view != null && view.canInherit())
            {
                if (!getContainer().getId().equals(view.getContainer().getId()))
                {
                    errors.reject(ERROR_MSG, "Inherited view '" + view.getName() + "' can only edited from the folder it was created in");
                    return false;
                }
            }
            return true;
        }

        public void validateCommand(ChooseColumnsForm form, Errors errors)
        {
            canEdit(form, errors);
        }

        public ModelAndView getView(ChooseColumnsForm form, boolean reshow, BindException errors) throws Exception
        {
            if (form.getQuerySettings() == null)
            {
                HttpView.throwNotFound();
                return null;
            }

            if (form.ff_designXML == null)
            {
                if (queryExists(form))
                {
                    CustomView view = form.getQueryDef().getCustomView(getUser(), getViewContext().getRequest(), form.getQuerySettings().getViewName());
                    if (view == null)
                    {
                        view = form.getQueryDef().createCustomView(getUser(), form.getQuerySettings().getViewName());
                    }

                    ActionURL url = new ActionURL();
                    form.applyFilterAndSortToURL(url, "query");
                    view.setFilterAndSortFromURL(url, "query");
                    ViewDocument designDoc = ((CustomViewImpl) view).getDesignDocument(form.getSchema());
                    if (designDoc == null)
                    {
                        errors.reject(ERROR_MSG, "The query '" + form.getQueryName() + "' has errors.");
                        form.ff_designXML = null;
                    }
                    else
                    {
                        form.ff_designXML = designDoc.toString();
                    }
                }
                else
                {
                    errors.reject(ERROR_MSG, "The query '" + form.getQueryName() + "' doesn't exist.");
                }
            }
            return FormPage.getView(QueryController.class, form, errors, "chooseColumns.jsp");
        }

        public boolean handlePost(ChooseColumnsForm form, BindException errors) throws Exception
        {
            User owner = getUser();
            String regionName = form.getDataRegionName();
            if (form.ff_saveForAllUsers && form.canSaveForAllUsers())
            {
                owner = null;
            }
            String name = StringUtils.trimToNull(form.ff_columnListName);

            boolean canEdit = canEdit(form, errors);
            if (canEdit)
            {
                CustomView view = form.getQueryDef().getCustomView(owner, getViewContext().getRequest(), name);
                if (view == null || owner != null && view.getOwner() == null)
                {
                    view = form.getQueryDef().createCustomView(owner, name);
                }
                ViewDocument doc = ViewDocument.Factory.parse(StringUtils.trimToEmpty(form.ff_designXML));
                ((CustomViewImpl) view).update(doc, form.ff_saveFilter);
                if (form.canSaveForAllUsers())
                {
                    view.setCanInherit(form.ff_inheritable);
                }
                view.save(getUser(), getViewContext().getRequest());
                if (owner == null)
                {
                    CustomView personalView = form.getQueryDef().getCustomView(getUser(), getViewContext().getRequest(), name);
                    if (personalView != null && personalView.getOwner() != null)
                    {
                        personalView.delete(getUser(), getViewContext().getRequest());
                    }
                }
            }

            _returnURL = form.getSourceURL();
            if (null != _returnURL)
            {
                if (name == null || !canEdit)
                {
                    _returnURL.deleteParameter(regionName + "." + QueryParam.viewName);
                }
                else
                {
                    _returnURL.replaceParameter(regionName + "." + QueryParam.viewName, name);
                }
                _returnURL.deleteParameter(regionName + "." + QueryParam.ignoreFilter.toString());
                if (form.ff_saveFilter)
                {
                    for (String key : _returnURL.getKeysByPrefix(regionName + "."))
                    {
                        if (form.isFilterOrSort(regionName, key))
                            _returnURL.deleteFilterParameters(key);
                    }
                }
            }
            return true;
        }

        public ActionURL getSuccessURL(ChooseColumnsForm chooseColumnsForm)
        {
            if (null != _returnURL)
                return _returnURL;
            return getViewContext().cloneActionURL().setAction(QueryAction.executeQuery.name());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Customize Grid View");
            return root;
        }
    }



    @RequiresPermission(ACL.PERM_READ)
    public class PropertiesQueryAction extends FormViewAction<PropertiesForm>
    {
        PropertiesForm _form = null;
        
        public void validateCommand(PropertiesForm target, Errors errors)
        {
        }

        public ModelAndView getView(PropertiesForm form, boolean reshow, BindException errors) throws Exception
        {
            // assertQueryExists requires that it be well-formed
            // assertQueryExists(form);
            QueryDefinition queryDef = form.getQueryDef();
            if (queryDef == null)
                HttpView.throwNotFound("Query not found");
            _form = form;
            return FormPage.getView(QueryController.class, form, errors, "propertiesQuery.jsp");
        }

        public boolean handlePost(PropertiesForm form, BindException errors) throws Exception
        {
            // assertQueryExists requires that it be well-formed
            // assertQueryExists(form);
            if (!form.canEdit())
                HttpView.throwUnauthorized();
            QueryDefinition queryDef = form.getQueryDef();
            if (queryDef == null)
                HttpView.throwNotFound("Query not found");
            queryDef.setDescription(form.ff_description);
            queryDef.setCanInherit(form.ff_inheritable);
            queryDef.setIsHidden(form.ff_hidden);
            queryDef.save(getUser(), getContainer());
            _form = form;
            return true;
        }


        public ActionURL getSuccessURL(PropertiesForm propertiesForm)
        {
            return _form.getSchema().urlFor(QueryAction.schema);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            (new SchemaAction(_form)).appendNavTrail(root);
            root.addChild("Edit query properties");
            return root;
        }
    }


    @RequiresPermission(ACL.PERM_DELETE)
    public class DeleteQueryRowsAction extends FormHandlerAction<QueryForm>
    {
        ActionURL _url = null;
        
        public void validateCommand(QueryForm target, Errors errors)
        {
        }

        public boolean handlePost(QueryForm form, BindException errors) throws Exception
        {
            ActionURL forward = null;
            String returnURL = (String)this.getProperty(QueryParam.srcURL); // UNDONE: add to QueryForm
            if (returnURL != null)
                forward = new ActionURL(returnURL);
            TableInfo table = form.getQueryDef().getTable(null, form.getSchema(), null);
            QueryUpdateForm quf = new QueryUpdateForm(table, getViewContext().getRequest());
            if (!table.hasPermission(getUser(), ACL.PERM_DELETE))
            {
                HttpView.throwUnauthorized();
            }
            try
            {
                _url = table.delete(getUser(), forward, quf);
            }
            catch (SQLException x)
            {
                if (!isConstraintException(x))
                    throw x;
                errors.reject(ERROR_MSG, getMessage(quf.getTable().getSchema().getSqlDialect(), x));
                return false;
            }
            return true;
        }

        public ActionURL getSuccessURL(QueryForm queryForm)
        {
            return _url;
        }
    }

    
    // alias
    public class DeleteAction extends DeleteQueryRowsAction
    {
    }


/*
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

    private Forward doNewView(InternalNewViewForm form) throws Exception
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
        requiresGlobalAdmin();
        ActionURL fwd = new ActionURL("query", "admin", form.getContainer());

        if (isPost())
        {
            form.doUpdate();
            return new ViewForward(fwd);
        }
        UpdateView view = new UpdateView(form);
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
    protected Forward adminNewDbUserSchema(DbUserSchemaForm form) throws Exception
    {
        requiresGlobalAdmin();
        ActionURL fwd = new ActionURL("query", "admin", form.getContainer());

        if (isPost())
        {
            form.doInsert();
            return new ViewForward(fwd);
        }
        InsertView view = new InsertView(form);
        Map<String, Object> initialValues = new HashMap();
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
    */

    public abstract class QueryViewAction extends SimpleViewAction<QueryForm>
    {
        QueryForm _form;
    }

    public abstract class QueryFormAction extends FormViewAction<QueryForm>
    {
        QueryForm _form;
    }

    public static class APIQueryForm extends QueryForm
    {
        private Integer _start;
        private Integer _limit;
        private String _sort;
        private String _dir;
        private boolean _lookups = true;

        public Integer getStart()
        {
            return _start;
        }

        public void setStart(Integer start)
        {
            _start = start;
        }

        public Integer getLimit()
        {
            return _limit;
        }

        public void setLimit(Integer limit)
        {
            _limit = limit;
        }

        public String getSort()
        {
            return _sort;
        }

        public void setSort(String sort)
        {
            _sort = sort;
        }

        public String getDir()
        {
            return _dir;
        }

        public void setDir(String dir)
        {
            _dir = dir;
        }
    }


    @ActionNames("selectRows, getQuery")
    @RequiresPermission(ACL.PERM_READ)
    public class GetQueryAction extends ApiAction<APIQueryForm>
    {
        public ApiResponse execute(APIQueryForm form, BindException errors) throws Exception
        {
            assertQueryExists(form);
            if (form.getLimit() != null)
                form.getQuerySettings().setMaxRows(form.getLimit().intValue());
            if (form.getStart() != null)
                form.getQuerySettings().setOffset(form.getStart().intValue());
            if (form.getSort() != null)
            {
                ActionURL sortFilterURL = getViewContext().getActionURL().clone();
                boolean desc = "DESC".equals(form.getDir());
                sortFilterURL.replaceParameter("query.sort", (desc ? "-" : "") + form.getSort());
                form.getQuerySettings().setSortFilterURL(sortFilterURL); //this isn't working!
            }

            //split the column list into a list of field keys
            List<FieldKey> fieldKeys = new ArrayList<FieldKey>();
            String columns = getViewContext().getRequest().getParameter("query.columns");
            if(null != columns && columns.length() > 0)
            {
                String[] cols = columns.split(",");
                for(String col : cols)
                    fieldKeys.add(FieldKey.fromString(col));
            }

            QueryView view = QueryView.create(form);
            return new ApiQueryResponse(view, getViewContext(), isSchemaEditable(form.getSchema()), true,
                    form.getSchemaName(), form.getQueryName(), form.getQuerySettings().getOffset(), fieldKeys);
        }
    }

    protected boolean isSchemaEditable(UserSchema schema)
    {
        if(!getViewContext().getContainer().hasPermission(getUser(), ACL.PERM_UPDATE | ACL.PERM_INSERT | ACL.PERM_DELETE))
            return false;
        if(schema.getSchemaName().equalsIgnoreCase("lists"))
            return true;
        else if(schema instanceof DbUserSchema)
            return ((DbUserSchema)schema).areTablesEditable();
        else
            return false;
    }

    public static class ApiSaveRowsForm implements ApiJsonForm
    {
        private JSONObject _json;

        public void setJsonObject(JSONObject jsonObj)
        {
            _json = jsonObj;
        }

        public JSONObject getJsonObject()
        {
            return _json;
        }
    }

    protected enum SaveTarget
    {
        List,
        DbUserSchema,
        StudyDataset
    }

    /**
     * Base action class for insert/update/delete actions
     */
    public abstract class SaveRowsAction extends ApiAction<ApiSaveRowsForm>
    {
        private static final String PROP_SCHEMA_NAME = "schemaName";
        private static final String PROP_QUERY_NAME = "queryName";
        private static final String PROP_ROWS = "rows";

        public ApiResponse execute(ApiSaveRowsForm form, BindException errors) throws Exception
        {
            //TODO: when we add XML support, we'll need to conditionalize this based on getRequestFormat()
            return executeJson(form);
        }

        protected ApiResponse executeJson(ApiSaveRowsForm form) throws Exception
        {
            JSONObject json = form.getJsonObject();
            ApiSimpleResponse response = new ApiSimpleResponse();

            String schemaName = json.getString(PROP_SCHEMA_NAME);
            String queryName = json.getString(PROP_QUERY_NAME);
            if(null == schemaName || null == queryName)
                throw new IllegalArgumentException("You must supply a schemaName and queryName in the metaData object!");

            JSONArray rows = json.getJSONArray(PROP_ROWS);
            if(null == rows || rows.length() < 1)
                throw new Exception("No 'rows' array supplied!");

            SaveTarget target = null;
            Container container = getViewContext().getContainer();
            ListDefinition listDef = null;
            DbUserSchema schema = null;
            TableInfo userTable = null;
            TableInfo dbTable = null;
            int datasetId = -1;
            if(schemaName.equalsIgnoreCase("lists"))
            {
                target = SaveTarget.List;
                listDef = getListDef(queryName);
            }
            else if(schemaName.equalsIgnoreCase("study"))
            {
                target = SaveTarget.StudyDataset;
                datasetId = StudyService.get().getDatasetId(container, queryName);
                if(datasetId < 0)
                    throw new IllegalArgumentException("The dataset '" + queryName + "' does not exist within the container '" + container.getPath() + "'!");
            }
            else
            {
                target = SaveTarget.DbUserSchema;
                schema = getSchema(schemaName);
                userTable = schema.getTable(queryName, null);
                dbTable = getTable(schema, queryName);
            }

            //we will transact operations by default, but the user may
            //override this by sending a "transacted" property set to false
            boolean transacted = json.optBoolean("transacted", true);

            //begin a transaction if there are more than one rows
            //if schema is null, this will start a transaction on lists
            if(transacted)
                beginTransaction(target, schema);

            //setup the response, providing the schema name, query name, and operation
            //so that the client can sort out which request this response belongs to
            //(clients often submit these async)
            response.put(PROP_SCHEMA_NAME, schemaName);
            response.put(PROP_QUERY_NAME, queryName);
            response.put("command", getSaveCommandName());
            response.put("containerPath", container.getPath());

            ArrayList<Object> responseRows = new ArrayList<Object>();
            response.put("rows", responseRows);

            int rowsAffected = 0;

            try
            {
                for(int idx = 0; idx < rows.length(); ++idx)
                {
                    JSONObject row = rows.getJSONObject(idx);
                    Map<String,Object> rowMap = row.getMap(true);
                    if(null != userTable)
                        stripReadOnlyCols(userTable, rowMap);

                    if(null != rowMap)
                    {
                        if(null != listDef)
                            saveListItem(listDef, rowMap, responseRows);
                        else if(datasetId >= 0)
                            saveDatasetRow(datasetId, rowMap, responseRows);
                        else
                            saveTableRow(dbTable, rowMap, responseRows);

                        ++rowsAffected;
                    }
                }

                if(transacted)
                    commitTransaction(target, schema);
            }
            finally
            {
                //will only rollback if transaction is still active
                if(transacted)
                    rollbackTransaction(target, schema);
            }

            response.put("rowsAffected", rowsAffected);

            return response;
        }

        protected abstract void saveListItem(ListDefinition listDef, Map row, ArrayList<Object> responseRows) throws Exception;
        protected abstract void saveTableRow(TableInfo table, Map row, ArrayList<Object> responseRows) throws Exception;
        protected abstract void saveDatasetRow(int datasetId, Map row, ArrayList<Object> responseRows) throws Exception;

        protected abstract String getSaveCommandName(); //unfortunatley, getCommandName() is already defined in Spring action classes

        protected void beginTransaction(SaveTarget target, DbUserSchema schema) throws Exception
        {
            switch(target)
            {
                case List:
                    ListService.get().beginTransaction();
                    break;
                case DbUserSchema:
                    schema.getDbSchema().getScope().beginTransaction();
                    break;
                case StudyDataset:
                    StudyService.get().beginTransaction();
            }
        }

        protected void commitTransaction(SaveTarget target, DbUserSchema schema) throws Exception
        {
            switch(target)
            {
                case List:
                    ListService.get().commitTransaction();
                    break;
                case DbUserSchema:
                    schema.getDbSchema().getScope().commitTransaction();
                    break;
                case StudyDataset:
                    StudyService.get().commitTransaction();
            }
        }

        protected void rollbackTransaction(SaveTarget target, DbUserSchema schema)
        {
            switch(target)
            {
                case List:
                    ListService.Interface lsvc = ListService.get();
                    if(lsvc.isTransactionActive())
                        lsvc.rollbackTransaction();
                    break;
                case DbUserSchema:
                    DbScope scope = schema.getDbSchema().getScope();
                    if(scope.isTransactionActive())
                        scope.rollbackTransaction();
                    break;
                case StudyDataset:
                    StudyService.Service ssvc = StudyService.get();
                    if(ssvc.isTransactionActive())
                        ssvc.rollbackTransaction();
            }
        }

        protected ListDefinition getListDef(String listName)
        {
            Map<String, ListDefinition> listDefs =  ListService.get().getLists(getViewContext().getContainer());
            if(null == listDefs)
                throw new NotFoundException("No lists found in the container '" + getViewContext().getContainer().getPath() + "'.");

            ListDefinition listDef = listDefs.get(listName);
            if(null == listDef)
                throw new NotFoundException("List '" + listName + "' was not found in the container '" + getViewContext().getContainer().getPath() + "'.");
            return listDef;
        }

        protected ListItem getListItem(Map itemData, ListDefinition listDef)
        {
            Object key = itemData.get(listDef.getKeyName());
            if(null == key)
                throw new NotFoundException("Not value was supplied for key column '" + listDef.getKeyName() + "'!");
            ListItem item = listDef.getListItem(key);
            if(null == item)
                throw new NotFoundException("List item with key value '" + itemData.get(listDef.getKeyName()) + "' was not found!");
            return item;
        }

        protected DbUserSchema getSchema(String schemaName) throws Exception
        {
            DbUserSchema schema = null;
            DbUserSchemaDef[] defs = QueryManager.get().getDbUserSchemaDefs(getViewContext().getContainer());
            for(DbUserSchemaDef def : defs)
            {
                if(def.getUserSchemaName().equalsIgnoreCase(schemaName))
                {
                    schema = new DbUserSchema(getUser(), getViewContext().getContainer(), def);
                    break;
                }
            }

            if(null == schema)
                throw new NotFoundException("There is no editable schema named '" + schemaName + "' in " + getViewContext().getContainer().getPath() + "!");
            if(!schema.areTablesEditable())
                throw new RuntimeException("Schema " + schemaName + " is not editable!");

            return schema;
        }

        protected TableInfo getTable(DbUserSchema schema, String tableName)
        {
            TableInfo table = schema.getDbSchema().getTable(tableName);
            if(null == table)
                throw new NotFoundException("Table '" + tableName + "' does not exist within schema '" + schema.getSchemaName() + "'!");
            return table;
        }

        protected Object getRowId(TableInfo table, Map row)
        {
            List<ColumnInfo> pkCols = table.getPkColumns();
            if (1 == pkCols.size())
                return row.get(pkCols.get(0).getName());
            else
            {
                Object[] pkVals = new Object[pkCols.size()];
                for(int idx = 0; idx < pkVals.length; ++idx)
                    pkVals[idx] = row.get(pkCols.get(idx).getName());
                return pkVals;
            }
        }

        //TODO: this should really be an array/enum in Table.java
        //Query handles several columns with specific names automagically, so we should
        //always strip those from the row map so that clients cannot override this behavior
        //the full list is:
        // Owner, CreatedBy, Created, ModifiedBy, Modified, EntityId, _ts
        //
        // HOWEVER, Modified and _ts are used by the table layer to do optimistic concurrency checks
        // so DO NOT strip those out--the table layer will keep those out of the values sent to
        // the database (per Matt) 
        private String[] _specialColumns = {"Owner", "CreatedBy", "Created", "ModifiedBy", "EntityId"};

        protected void stripReadOnlyCols(TableInfo userTable, Map<String,Object> rowMap)
        {
            //remove specially-handled columns from the map
            for(String colName : _specialColumns)
                rowMap.remove(colName);

            //strip all read-only columns from the row map so that we don't get database
            //errors on insert/update. The Ext grid sends all column values during update
            //and other clients may do the same.
            for(ColumnInfo col : userTable.getColumns())
            {
                //don't strip the PK, which will be read-only if it's auto-incr
                if(!col.isKeyField() && isColReadOnly(col))
                    rowMap.remove(col.getName());
            }
        }

        protected boolean isColReadOnly(ColumnInfo col)
        {
            //a column is read-only if it is:
            // - read-only or
            // - not user-editable or
            // - is an fk to a non-public table
            TableInfo fkTable = col.getFkTableInfo();
            return col.isReadOnly() || !col.isUserEditable()
                    || (null != fkTable && !fkTable.isPublic());
        }

        protected Map<String,Object> getListItemAsMap(ListDefinition listDef, ListItem item)
        {
            ListItem itemNew = listDef.getListItem(item.getKey());
            Map<String,Object> map = new HashMap<String,Object>();
            map.put(listDef.getKeyName(), itemNew.getKey());
            map.put("EntityId", itemNew.getEntityId());
            for(DomainProperty prop : listDef.getDomain().getProperties())
                map.put(prop.getName(), itemNew.getProperty(prop));
            
            return map;
        }

        protected String getLSID(Map row)
        {
            String lsid = (String)row.get("lsid");
            if(null == lsid || lsid.length() <= 0)
                throw new IllegalArgumentException("You must supply a value for the LSID column!");
            return lsid;
        }

        protected void throwDatasetErrors(String lsid, List<String> errors)
        {
            String sep = "";
            StringBuilder msg = new StringBuilder("Errors while saving the study dataset row with LSID " + lsid + ": ");
            for(String err : errors)
            {
                msg.append(sep);
                msg.append(err);
                sep = "; ";
            }

            throw new IllegalArgumentException(msg.toString());
        }
    }

    @RequiresPermission(ACL.PERM_UPDATE)
    public class UpdateRowsAction extends SaveRowsAction
    {
        protected String getSaveCommandName()
        {
            return "update";
        }

        protected void saveListItem(ListDefinition listDef, Map row, ArrayList<Object> responseRows) throws Exception
        {
            ListItem item = getListItem(row, listDef);

            for(Object key : row.keySet())
            {
                //if key column, don't try to set
                if(((String)key).equalsIgnoreCase(listDef.getKeyName()))
                    continue;

                DomainProperty prop = listDef.getDomain().getPropertyByName((String)key);
                if(null != prop)
                    item.setProperty(prop, row.get(key));
            }

            item.save(getUser());

            responseRows.add(getListItemAsMap(listDef, item));
        }

        protected void saveTableRow(TableInfo table, Map row, ArrayList<Object> responseRows) throws Exception
        {
            Table.update(getUser(), table, row, getRowId(table, row), null);

            //re-fetch the row from the database to pick up any column values
            //assigned via default expressions or triggers
            row = Table.selectObject(table, getRowId(table, row), Map.class);

            responseRows.add(row);
        }

        protected void saveDatasetRow(int datasetId, Map row, ArrayList<Object> responseRows) throws Exception
        {
            StudyService.Service svc = StudyService.get();
            Container container = getViewContext().getContainer();
            User user = getViewContext().getUser();

            List<String> errors = new ArrayList<String>();
            String lsid = svc.updateDatasetRow(user, container, datasetId, getLSID(row), row, errors);
            if(errors.size() > 0)
                throwDatasetErrors(getLSID(row), errors);

            Map<String,Object> responseRow = svc.getDatasetRow(user, container, datasetId, lsid);
            assert null != responseRow : "Could not refetch the dataset row with LSID '" + lsid + "' from dataset id " + String.valueOf(datasetId);
            responseRows.add(responseRow);
        }
    }

    @RequiresPermission(ACL.PERM_INSERT)
    public class InsertRowsAction extends SaveRowsAction
    {
        protected String getSaveCommandName()
        {
            return "insert";
        }

        protected void saveListItem(ListDefinition listDef, Map row, ArrayList<Object> responseRows) throws Exception
        {
            ListItem item = listDef.createListItem();

            //set the key if it's not an auto-increment
            if (listDef.getKeyType() != ListDefinition.KeyType.AutoIncrementInteger)
                item.setKey(row.get(listDef.getKeyName()));

            for(Object key : row.keySet())
            {
                //if key column, don't try to set
                if(((String)key).equalsIgnoreCase(listDef.getKeyName()))
                    continue;

                DomainProperty prop = listDef.getDomain().getPropertyByName((String)key);
                if(null != prop)
                    item.setProperty(prop, row.get(key));
            }

            item.save(getUser());
            responseRows.add(getListItemAsMap(listDef, item));
        }

        protected void saveTableRow(TableInfo table, Map row, ArrayList<Object> responseRows) throws Exception
        {
            row = Table.insert(getUser(), table, row);

            //re-fetch the row from the database to pick up any column values
            //assigned via default expressions or triggers
            row = Table.selectObject(table, getRowId(table, row), Map.class);

            responseRows.add(row);
        }

        protected void saveDatasetRow(int datasetId, Map row, ArrayList<Object> responseRows) throws Exception
        {
            StudyService.Service svc = StudyService.get();
            Container container = getViewContext().getContainer();
            User user = getViewContext().getUser();

            List<String> errors = new ArrayList<String>();
            String lsid = svc.insertDatasetRow(user, container, datasetId, row, errors);
            if(errors.size() > 0)
                throwDatasetErrors(lsid, errors);

            //fetch the row to send back in the response
            Map<String,Object> responseRow = svc.getDatasetRow(user, container, datasetId, lsid);
            assert null != responseRow : "Could not refetch the dataset row with LSID '" + lsid + "' from dataset id " + String.valueOf(datasetId);
            responseRows.add(responseRow);
        }
    }

    @ActionNames("deleteRows, delRows")
    @RequiresPermission(ACL.PERM_DELETE)
    public class DeleteRowsAction extends SaveRowsAction
    {
        protected String getSaveCommandName()
        {
            return "delete";
        }

        protected void saveListItem(ListDefinition listDef, Map row, ArrayList<Object> responseRows) throws Exception
        {
            ListItem item = getListItem(row, listDef);
            item.delete(getUser(), getViewContext().getContainer());
            Map<String,Object> responseRow = new HashMap<String,Object>();
            responseRow.put(listDef.getKeyName(), item.getKey());
            responseRows.add(responseRow);
        }

        protected void saveTableRow(TableInfo table, Map row, ArrayList<Object> responseRows) throws Exception
        {
            Table.delete(table, getRowId(table, row), null);

            //for delete, just include the key column value(s) in the reply
            Map<String,Object> responseRow = new HashMap<String,Object>();
            for(ColumnInfo col : table.getColumns())
            {
                if(col.isKeyField())
                    responseRow.put(col.getName(), row.get(col.getName()));
            }

            responseRows.add(responseRow);
        }

        protected void saveDatasetRow(int datasetId, Map row, ArrayList<Object> responseRows) throws Exception
        {
            StudyService.get().deleteDatasetRow(getViewContext().getUser(), getViewContext().getContainer(), 
                    datasetId, getLSID(row));
            Map<String,Object> responseRow = new HashMap<String,Object>();
            responseRow.put("lsid", getLSID(row));
            responseRows.add(responseRow);
        }
    }

    @RequiresPermission(ACL.PERM_ADMIN)
    public class ApiTestAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView("/org/labkey/query/controllers/apitest.jsp");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("API Test");
        }
    }


    @RequiresPermission(ACL.PERM_ADMIN)
    public class AdminAction extends SimpleViewAction<QueryForm>
    {
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
           return new JspView<QueryForm>(getClass(), "admin.jsp", form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new BeginAction().appendNavTrail(root);
            root.addChild("Schema Administration", actionURL(QueryAction.admin));
            return root;
        }
    }


    @RequiresSiteAdmin
    public class AdminNewDbUserSchemaAction extends FormViewAction<DbUserSchemaForm>
    {
        public void validateCommand(DbUserSchemaForm form, Errors errors)
        {
        }

        public ModelAndView getView(DbUserSchemaForm form, boolean reshow, BindException errors) throws Exception
        {
            InsertView view = new InsertView(form,errors);
            Map<String, Object> initialValues = new HashMap<String, Object>();
            initialValues.put("DbContainer", getContainer().getId());
            view.setInitialValues(initialValues);
            ButtonBar bb = new ButtonBar();
            bb.add(new ActionButton("adminNewDbUserSchema.post", "Create"));
            bb.add(new ActionButton("Cancel", getSuccessURL(form)));
            view.getDataRegion().setButtonBar(bb);
            return view;
        }

        public boolean handlePost(DbUserSchemaForm form, BindException errors) throws Exception
        {
            form.doInsert();
            return true;
        }

        public ActionURL getSuccessURL(DbUserSchemaForm dbUserSchemaForm)
        {
            return actionURL(QueryAction.admin);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new AdminAction().appendNavTrail(root);
            root.addChild("Define Schema", actionURL(QueryAction.adminNewDbUserSchema));
            return root;
        }
    }


    @RequiresPermission(ACL.PERM_ADMIN)
    public class AdminEditDbUserSchemaAction extends FormViewAction<DbUserSchemaForm>
    {
        public void validateCommand(DbUserSchemaForm target, Errors errors)
        {
        }

        public ModelAndView getView(DbUserSchemaForm form, boolean reshow, BindException errors) throws Exception
        {

            UpdateView view = new UpdateView(form, errors);
            ButtonBar bb = new ButtonBar();
            bb.add(new ActionButton("adminEditDbUserSchema.post", "Update"));
            bb.add(new ActionButton("Cancel", getSuccessURL(form)));
            ActionURL urlDelete = new ActionURL("query", "adminDeleteDbUserSchema", form.getContainer());
            urlDelete.addParameter("dbUserSchemaId", Integer.toString(form.getBean().getDbUserSchemaId()));
            bb.add(new ActionButton("Delete", urlDelete));
            view.getDataRegion().setButtonBar(bb);
            return view;
        }

        public boolean handlePost(DbUserSchemaForm form, BindException errors) throws Exception
        {
            form.doUpdate();
            return true;
        }

        public ActionURL getSuccessURL(DbUserSchemaForm dbUserSchemaForm)
        {
            return actionURL(QueryAction.admin);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new AdminAction().appendNavTrail(root);
            root.addChild("Edit Schema", actionURL(QueryAction.adminNewDbUserSchema));
            return root;
        }
    }


    @RequiresSiteAdmin
    public class AdminDeleteDbUserSchemaAction extends ConfirmAction<DbUserSchemaForm>
    {
        public String getConfirmText()
        {
            return "Delete";
        }

        public ModelAndView getConfirmView(DbUserSchemaForm form, BindException errors) throws Exception
        {
            form.refreshFromDb(false);
            return new HtmlView(
                    null,
                    "Are you sure you want to delete the schema '%s'? The tables and queries defined in this schema will no longer be accessible.",
                    form.getBean().getUserSchemaName()
                    );
        }

        public boolean handlePost(DbUserSchemaForm form, BindException errors) throws Exception
        {
            QueryManager.get().delete(getUser(), form.getBean());
            return true;
        }

        public void validateCommand(DbUserSchemaForm dbUserSchemaForm, Errors errors)
        {
        }

        public ActionURL getSuccessURL(DbUserSchemaForm dbUserSchemaForm)
        {
            return actionURL(QueryAction.admin);
        }
    }


    @RequiresSiteAdmin
    public class AdminReloadDbUserSchemaAction extends SimpleViewAction<DbUserSchemaForm>
    {
        public ModelAndView getView(DbUserSchemaForm form, BindException errors) throws Exception
        {
            form.refreshFromDb(false);
            DbUserSchemaDef def = form.getBean();
            ActionURL fwd = new ActionURL("query", "admin", form.getContainer());
            fwd.addParameter("reloadedSchema", def.getUserSchemaName());
            QueryManager.get().reloadDbUserSchema(def);
            return HttpView.redirect(getSuccessURL(form));
        }

        public ActionURL getSuccessURL(DbUserSchemaForm form)
        {
            ActionURL url = actionURL(QueryAction.admin);
            url.addParameter("reloadedSchema", form.getBean().getUserSchemaName());
            return url;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

    
    public static boolean isConstraintException(SQLException x)
    {
        String sqlState = x.getSQLState();
        if (!sqlState.startsWith("23"))
            return false;
        return sqlState.equals("23000") || sqlState.equals("23505") || sqlState.equals("23503");
    }

    public static String getMessage(SqlDialect d, SQLException x)
    {
        return x.getMessage();
    }
}
