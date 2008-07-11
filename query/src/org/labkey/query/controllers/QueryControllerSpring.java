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
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.*;
import org.labkey.api.data.*;
import org.labkey.api.query.*;
import org.labkey.api.security.*;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.AppProps;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig;
import org.labkey.query.CustomViewImpl;
import org.labkey.query.QueryDefinitionImpl;
import org.labkey.query.TableXML;
import org.labkey.query.sql.QParser;
import org.labkey.query.data.Query;
import org.labkey.query.data.DefaultSchemaUpdateService;
import org.labkey.query.design.QueryDocument;
import org.labkey.query.design.ViewDocument;
import org.labkey.query.design.ErrorsDocument;
import org.labkey.query.design.DgMessage;
import org.labkey.query.persist.DbUserSchemaDef;
import org.labkey.query.persist.QueryDef;
import org.labkey.query.persist.QueryManager;
import org.labkey.query.persist.CstmView;
import org.labkey.query.view.DbUserSchema;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TableType;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.sql.SQLException;
import java.util.*;

public class QueryControllerSpring extends SpringActionController
{
    static DefaultActionResolver _actionResolver = new DefaultActionResolver(QueryControllerSpring.class);

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
            return new JspView<QueryForm>(QueryControllerSpring.class, "begin.jsp", form, errors);

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
            return new JspView<QueryForm>(QueryControllerSpring.class, "schema.jsp", form, errors);
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

        @SuppressWarnings({"UnusedDeclaration"})
        public ModelAndView getView(NewQueryForm form, boolean reshow, BindException errors) throws Exception
        {
            if (!form.getSchema().canCreate())
                HttpView.throwUnauthorized();
            getPageConfig().setFocus("forms[0].ff_newQueryName");
            _form = form;
            return new JspView<NewQueryForm>(QueryControllerSpring.class, "newQuery.jsp", form, errors);
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
            return new JspView<QueryForm>(QueryControllerSpring.class, "deleteQuery.jsp", queryForm, errors);
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
    @IgnoresTermsOfUse
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
            return new JspView<MetadataForm>(QueryControllerSpring.class, "metadata.jsp", form, errors);
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
            return new JspView<DesignForm>(QueryControllerSpring.class, "designQuery.jsp", form, errors);
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

            if (!reshow)
                form.initForView();

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
            return new JspView<ChooseColumnsForm>(QueryControllerSpring.class, "chooseColumns.jsp", form, errors);
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
                _returnURL = _returnURL.clone();
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
            return new JspView<PropertiesForm>(QueryControllerSpring.class, "propertiesQuery.jsp", form, errors);
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
        if (!getViewContext().getContainer().hasPermission(getUser(), ACL.PERM_UPDATE | ACL.PERM_INSERT | ACL.PERM_DELETE))
            return false;
        if (schema.getSchemaName().equalsIgnoreCase("lists"))
            return true;
        else if (schema.getSchemaName().equalsIgnoreCase("study"))
            return StudyService.get().areDatasetsEditable(getViewContext().getContainer());
        else if (schema instanceof DbUserSchema)
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
            Container container = getViewContext().getContainer();
            User user = getViewContext().getUser();

            String schemaName = json.getString(PROP_SCHEMA_NAME);
            String queryName = json.getString(PROP_QUERY_NAME);
            if(null == schemaName || null == queryName)
                throw new IllegalArgumentException("You must supply a schemaName and queryName!");

            JSONArray rows = json.getJSONArray(PROP_ROWS);
            if(null == rows || rows.length() < 1)
                throw new Exception("No 'rows' array supplied!");

            //get the schema update service for this schema
            SchemaUpdateService sus = getSchemaUpdateService(schemaName);
            if(null == sus)
                throw new Exception("The schema '" + schemaName + "' is not editable via the HTTP-based APIs!");

            //get the query update service for the query
            QueryUpdateService qus = sus.getQueryUpdateService(queryName, container, user);
            if(null == qus)
                throw new Exception("The query '" + queryName + "' in the schema '" + schemaName + "' is not updateable via the HTTP-based APIs!");

            //we will transact operations by default, but the user may
            //override this by sending a "transacted" property set to false
            boolean transacted = json.optBoolean("transacted", true);
            if(transacted)
                sus.beginTransaction();

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
                    if(null != rowMap)
                    {
                        saveRow(qus, rowMap, responseRows);
                        ++rowsAffected;
                    }
                }

                if(transacted)
                    sus.commitTransaction();
            }
            finally
            {
                if(transacted && sus.isTransactionActive())
                    sus.rollbackTransaction();
            }

            response.put("rowsAffected", rowsAffected);

            return response;
        }

        /**
         * Dervied classes should implement this method to do the actual save operation (insert, update or delete)
         * @param qus The QueryUpdateService
         * @param row The row map
         * @param responseRows The array of response row maps to append to
         * @throws InvalidKeyException Thrown if the key is invalid
         * @throws DuplicateKeyException Thrown if the key is a duplicate of an existing key (insert)
         * @throws ValidationException Thrown if the data is not valid
         * @throws QueryUpdateServiceException Thrown if there is a implementation-specific error
         * @throws SQLException Thrown if there was a problem communicating with the database
         */
        protected abstract void saveRow(QueryUpdateService qus, Map<String,Object> row, ArrayList<Object> responseRows)
                throws InvalidKeyException, DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException;

        /**
         * Returns the name of the dervied class's command. This will be returned to
         * the client in the 'command' property.
         * @return The name of the derived class's command
         */
        protected abstract String getSaveCommandName(); //unfortunatley, getCommandName() is already defined in Spring action classes


        protected SchemaUpdateService getSchemaUpdateService(String schemaName)
        {
            SchemaUpdateService sus = SchemaUpdateServiceRegistry.get().getService(schemaName);

            //if we didn't find anything, try looking for a DbUserSchema in the current container
            //that matches the schemaName. If there is one, and it's editable,
            //return a DefaultSchemaUpdateService over it
            if(null == sus)
            {
                DbUserSchemaDef dbusd = QueryManager.get().getDbUserSchemaDef(getViewContext().getContainer(), schemaName);
                if(null != dbusd && dbusd.isEditable())
                    sus = new DefaultSchemaUpdateService(new DbUserSchema(getViewContext().getUser(),
                            getViewContext().getContainer(), dbusd));
            }

            return sus;
        }
    }

    @RequiresPermission(ACL.PERM_UPDATE)
    public class UpdateRowsAction extends SaveRowsAction
    {
        protected String getSaveCommandName()
        {
            return "update";
        }

        protected void saveRow(QueryUpdateService qus, Map<String, Object> row, ArrayList<Object> responseRows)
                throws InvalidKeyException, DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            Map<String,Object> updatedRow = qus.updateRow(getViewContext().getUser(), getViewContext().getContainer(),
                                                            row, null);
            if(null != updatedRow)
                updatedRow = qus.getRow(getViewContext().getUser(), getViewContext().getContainer(), updatedRow);
            if(null != updatedRow)
                responseRows.add(updatedRow);
        }

    }

    @RequiresPermission(ACL.PERM_INSERT)
    public class InsertRowsAction extends SaveRowsAction
    {
        protected String getSaveCommandName()
        {
            return "insert";
        }

        protected void saveRow(QueryUpdateService qus, Map<String, Object> row, ArrayList<Object> responseRows)
                throws InvalidKeyException, DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            Map<String,Object> insertedRow = qus.insertRow(getViewContext().getUser(), getViewContext().getContainer(),
                                                            row);
            if(null != insertedRow)
                insertedRow = qus.getRow(getViewContext().getUser(), getViewContext().getContainer(), insertedRow);
            if(null != insertedRow)
                responseRows.add(insertedRow);
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

        protected void saveRow(QueryUpdateService qus, Map<String, Object> row, ArrayList<Object> responseRows) throws InvalidKeyException, DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException
        {
            Map<String,Object> deletedRow = qus.deleteRow(getViewContext().getUser(), getViewContext().getContainer(),
                                                            row);
            if(null != deletedRow)
                responseRows.add(deletedRow);

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
            InsertView view = new InsertView(form, errors);
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


    // UNDONE: should use POST, change to FormHandlerAction
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


    @RequiresPermission(ACL.PERM_READ)
    public class TableInfoAction extends SimpleViewAction<TableInfoForm>
    {
        public ModelAndView getView(TableInfoForm form, BindException errors) throws Exception
        {
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
            getViewContext().getResponse().setContentType("text/xml");
            getViewContext().getResponse().getWriter().write(ret.toString());
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

    
    // UNDONE: should use POST, change to FormHandlerAction
    @RequiresPermission(ACL.PERM_READ) @RequiresLogin
    public class DeleteViewAction extends SimpleViewAction<DeleteViewForm>
    {
        public ModelAndView getView(DeleteViewForm form, BindException errors) throws Exception
        {
            CustomView view = form.getCustomView();
            if (view == null)
            {
                HttpView.throwNotFound();
                return null;
            }
            if (view.getOwner() == null)
            {
                if (!getViewContext().getContainer().hasPermission(getUser(), ACL.PERM_ADMIN))
                    HttpView.throwUnauthorized();
            }
            view.delete(getUser(), getViewContext().getRequest());
            return HttpView.redirect(getSuccessURL(form));
        }

        public ActionURL getSuccessURL(DeleteViewForm form)
        {
            String returnURL = getViewContext().getRequest().getParameter(QueryParam.srcURL.toString());
            if (returnURL != null)
                return new ActionURL(returnURL);
            return form.urlFor(QueryAction.begin);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }


    @RequiresPermission(ACL.PERM_NONE)
    public class CheckSyntaxAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException bindErrors) throws Exception
        {
            getPageConfig().setTemplate(PageConfig.Template.None);

            String sql = (String)getProperty("sql");
            if (sql == null)
                sql = PageFlowUtil.getStreamContentsAsString(getViewContext().getRequest().getInputStream());
            ErrorsDocument ret = ErrorsDocument.Factory.newInstance();
            org.labkey.query.design.Errors xbErrors = ret.addNewErrors();
            List<QueryParseException> errors = new ArrayList<QueryParseException>();
            try
            {
                QParser.parseExpr(sql, errors);
            }
            catch (Throwable t)
            {
                Logger.getInstance(QueryControllerSpring.class).error("Error", t);
                errors.add(new QueryParseException("Unhandled exception: " + t, null, 0, 0));
            }
            for (QueryParseException e : errors)
            {
                DgMessage msg = xbErrors.addNewError();
                msg.setStringValue(e.getMessage());
                msg.setLine(e.getLine());
            }
            HtmlView view = new HtmlView(ret.toString());
            view.setContentType("text/xml");
            view.setFrame(WebPartView.FrameType.NONE);
            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermission(ACL.PERM_READ) @RequiresLogin
    public class ManageViewsAction extends SimpleViewAction<QueryForm>
    {
        QueryForm _form;

        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            _form = form;
            return new JspView<QueryForm>(QueryControllerSpring.class, "manageViews.jsp", form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new BeginAction().appendNavTrail(root);
            root.addChild("Manage Views", QueryControllerSpring.this.getViewContext().getActionURL());
            return root;
        }
    }


    @RequiresPermission(ACL.PERM_READ)
    public class InternalDeleteView extends ConfirmAction<InternalViewForm>
    {
        public ModelAndView getConfirmView(InternalViewForm form, BindException errors) throws Exception
        {
            return new JspView<InternalViewForm>(QueryControllerSpring.class, "internalDeleteView.jsp", form, errors);
        }

        public boolean handlePost(InternalViewForm form, BindException errors) throws Exception
        {
            CstmView view = form.getViewAndCheckPermission();
            QueryManager.get().delete(getUser(), view);
            return true;
        }

        public void validateCommand(InternalViewForm internalViewForm, Errors errors)
        {
        }

        public ActionURL getSuccessURL(InternalViewForm internalViewForm)
        {
            return new ActionURL("query", "manageViews", getContainer());
        }
    }


    @RequiresPermission(ACL.PERM_READ) @RequiresLogin
    public class InternalSourceViewAction extends FormViewAction<InternalSourceViewForm>
    {
        public void validateCommand(InternalSourceViewForm target, Errors errors)
        {
        }

        public ModelAndView getView(InternalSourceViewForm form, boolean reshow, BindException errors) throws Exception
        {
            CstmView view = form.getViewAndCheckPermission();
            form.ff_columnList = view.getColumns();
            form.ff_filter = view.getFilter();
            return new JspView<InternalSourceViewForm>(QueryControllerSpring.class, "internalSourceView.jsp", form, errors);
        }

        public boolean handlePost(InternalSourceViewForm form, BindException errors) throws Exception
        {
            CstmView view = form.getViewAndCheckPermission();
            view.setColumns(form.ff_columnList);
            view.setFilter(form.ff_filter);
            QueryManager.get().update(getUser(), view);
            return true;
        }

        public ActionURL getSuccessURL(InternalSourceViewForm form)
        {
            return new ActionURL("query", "manageViews", getViewContext().getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new ManageViewsAction().appendNavTrail(root);
            root.addChild("Edit source of Grid View");
            return root;
        }
    }


    @RequiresPermission(ACL.PERM_READ) @RequiresLogin
    public class InternalNewViewAction extends FormViewAction<InternalNewViewForm>
    {
        int _customViewId = 0;
        
        public void validateCommand(InternalNewViewForm form, Errors errors)
        {
            if (StringUtils.trimToNull(form.ff_schemaName) == null)
            {
                errors.reject(ERROR_MSG, "Schema name cannot be blank.");
            }
            if (StringUtils.trimToNull(form.ff_queryName) == null)
            {
                errors.reject(ERROR_MSG, "Query name cannot be blank");
            }
        }

        public ModelAndView getView(InternalNewViewForm form, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<InternalNewViewForm>(QueryControllerSpring.class, "internalNewView.jsp", form, errors);
        }

        public boolean handlePost(InternalNewViewForm form, BindException errors) throws Exception
        {
            if (form.ff_share)
            {
                if (!getContainer().hasPermission(getUser(),ACL.PERM_ADMIN))
                    HttpView.throwUnauthorized();
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
                    Logger.getInstance(QueryControllerSpring.class).error("Error", e);
                    errors.reject(ERROR_MSG, "An exception occurred: " + e);
                    return false;
                }
                _customViewId = view.getCustomViewId();
            }
            return true;
        }

        public ActionURL getSuccessURL(InternalNewViewForm form)
        {
            ActionURL forward = new ActionURL("query", "internalSourceView", getContainer());
            forward.addParameter("customViewId", Integer.toString(_customViewId));
            return forward;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            //new ManageViewsAction().appendNavTrail(root);
            root.addChild("Create New Grid View");
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
