/*
 * Copyright (c) 2007-2010 LabKey Corporation
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

import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlOptions;
import org.json.JSONArray;
import org.json.JSONObject;
import org.labkey.api.action.*;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.*;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.query.*;
import org.labkey.api.query.snapshot.QuerySnapshotDefinition;
import org.labkey.api.query.snapshot.QuerySnapshotForm;
import org.labkey.api.query.snapshot.QuerySnapshotService;
import org.labkey.api.security.*;
import org.labkey.api.security.permissions.*;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.util.*;
import org.labkey.api.view.*;
import org.labkey.api.view.template.PageConfig;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.query.CustomViewImpl;
import org.labkey.query.QueryDefinitionImpl;
import org.labkey.query.TableXML;
import org.labkey.query.design.DgMessage;
import org.labkey.query.design.ErrorsDocument;
import org.labkey.query.design.QueryDocument;
import org.labkey.query.design.ViewDocument;
import org.labkey.query.metadata.MetadataServiceImpl;
import org.labkey.query.metadata.client.MetadataEditor;
import org.labkey.query.persist.CstmView;
import org.labkey.query.persist.DbUserSchemaDef;
import org.labkey.query.persist.QueryDef;
import org.labkey.query.persist.QueryManager;
import org.labkey.query.sql.Query;
import org.labkey.query.sql.SqlParser;
import org.labkey.query.xml.ApiTestsDocument;
import org.labkey.query.xml.TestCaseType;
import org.springframework.beans.PropertyValues;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

public class QueryController extends SpringActionController
{
    private static final Logger LOG = Logger.getLogger(QueryController.class);

    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(QueryController.class,
            ValidateQueryAction.class,
            GetSchemaQueryTreeAction.class,
            GetQueryDetailsAction.class,
            ViewQuerySourceAction.class);

    public QueryController() throws Exception
    {
        setActionResolver(_actionResolver);
    }

    public static class QueryUrlsImpl implements QueryUrls
    {
        public ActionURL urlCreateSnapshot(Container c)
        {
            return new ActionURL(CreateSnapshotAction.class, c);
        }

        public ActionURL urlCustomizeSnapshot(Container c)
        {
            return new ActionURL(EditSnapshotAction.class, c);
        }

        public ActionURL urlUpdateSnapshot(Container c)
        {
            return new ActionURL(UpdateSnapshotAction.class, c);
        }

        public ActionURL urlSchemaBrowser(Container c)
        {
            return new ActionURL(BeginAction.class, c);
        }

        public ActionURL urlExternalSchemaAdmin(Container c)
        {
            return urlExternalSchemaAdmin(c, null);
        }

        public ActionURL urlExternalSchemaAdmin(Container c, String reloadedSchema)
        {
            ActionURL url = new ActionURL(AdminAction.class, c);

            if (null != reloadedSchema)
                url.addParameter("reloadedSchema", reloadedSchema);

            return url;
        }

        public ActionURL urlInsertExternalSchema(Container c)
        {
            return new ActionURL(InsertExternalSchemaAction.class, c);
        }

        public ActionURL urlNewQuery(Container c)
        {
            return new ActionURL(NewQueryAction.class, c);
        }
    }

    protected boolean queryExists(QueryForm form)
    {
        return form.getSchema() != null && form.getSchema().getTable(form.getQueryName()) != null;
    }

    protected void assertQueryExists(QueryForm form) throws ServletException
    {
        if (form.getSchema() == null)
            HttpView.throwNotFound("Could not find schema: " + form.getSchemaName().getSource());

        try
        {
            if (!queryExists(form))
                HttpView.throwNotFound("Query '" + form.getQueryName() + "' in schema '" + form.getSchemaName().getSource() + "' doesn't exist.");
        }
        catch (QueryException qe)
        {
            // it exists, but it has an error
            return;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class BrowseAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView<Object>(QueryController.class, "browse.jsp", null);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Schema Browser");
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class BeginAction extends QueryViewAction
    {
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            return new JspView<QueryForm>(QueryController.class, "browse.jsp", form);

        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Query Schema Browser", new QueryUrlsImpl().urlSchemaBrowser(getContainer()));
            return root;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class SchemaAction extends QueryViewAction
    {
        public SchemaAction() {}

        SchemaAction(QueryForm form)
        {
            _form = form;
        }

        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            _form = form;
            return new JspView<QueryForm>(QueryController.class, "browse.jsp", form);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            String schemaName = _form.getSchemaName().toString();
            ActionURL url = new ActionURL(BeginAction.class, _form.getViewContext().getContainer());
            url.addParameter("schemaName", _form.getSchemaName());
            url.addParameter("queryName", _form.getQueryName());
            (new BeginAction()).appendNavTrail(root)
                .addChild(schemaName + " Schema", url);
            return root;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class NewQueryAction extends FormViewAction<NewQueryForm>
    {
        NewQueryForm _form;
        ActionURL _successUrl;

        public void validateCommand(NewQueryForm target, org.springframework.validation.Errors errors)
        {
            target.ff_newQueryName = StringUtils.trimToNull(target.ff_newQueryName);
            if (null == target.ff_newQueryName)
                errors.reject(ERROR_MSG, "QueryName is required");
                //errors.rejectValue("ff_newQueryName", ERROR_REQUIRED);
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public ModelAndView getView(NewQueryForm form, boolean reshow, BindException errors) throws Exception
        {
            if (form.getSchema() == null)
            {
                if (form.getSchemaName() == null || form.getSchemaName().isEmpty())
                    HttpView.throwNotFound("Schema name not specified");
                else
                    HttpView.throwNotFound("Could not find schema: " + form.getSchemaName().getSource());
            }
            if (!form.getSchema().canCreate())
                HttpView.throwUnauthorized();
            getPageConfig().setFocusId("ff_newQueryName");
            _form = form;
            setHelpTopic(new HelpTopic("customSQL"));
            return new JspView<NewQueryForm>(QueryController.class, "newQuery.jsp", form, errors);
        }

        public boolean handlePost(NewQueryForm form, BindException errors) throws Exception
        {
            if (form.getSchema() == null)
                HttpView.throwNotFound("Could not find schema: " + form.getSchemaName().getSource());
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
                String newQueryName = form.ff_newQueryName;
                QueryDef existing = QueryManager.get().getQueryDef(getContainer(), form.getSchemaName().toString(), newQueryName, true);
                if (existing != null)
                {
                    errors.reject(ERROR_MSG, "The query '" + newQueryName + "' already exists.");
                    return false;
                }
                TableInfo existingTable = form.getSchema().getTable(newQueryName);
                if (existingTable != null)
                {
                    errors.reject(ERROR_MSG, "A table with the name '" + newQueryName + "' already exists.");
                    return false;
                }
                // bug 6095 -- conflicting query and dataset names
                if (form.getSchema().getTableNames().contains(newQueryName))
                {
                    errors.reject(ERROR_MSG, "The query '" + newQueryName + "' already exists as a table");
                    return false;
                }
                QueryDefinition newDef = QueryService.get().createQueryDef(getContainer(), form.getSchemaName().toString(), form.ff_newQueryName);
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
            (new SchemaAction(_form)).appendNavTrail(root)
                    .addChild("New Query", new QueryUrlsImpl().urlNewQuery(getContainer()));
            return root;
        }
    }


/*
    @RequiresPermissionClass(AdminPermission.class)
    public class RenameQueryAction extends FormViewAction<NewQueryForm>
    {
        NewQueryForm _form;
        
        public void validateCommand(NewQueryForm form, Errors errors)
        {

        }

        public ModelAndView getView(NewQueryForm form, boolean reshow, BindException errors) throws Exception
        {
            _form = form;
            return null; 
        }

        public boolean handlePost(NewQueryForm form, BindException errors) throws Exception
        {
            _form = form;
            return false;
        }

        public ActionURL getSuccessURL(NewQueryForm form)
        {
            return actionURL(QueryAction.schema, QueryParam.schemaName, form.getSchemaName());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            (new QueryController.SchemaAction(_form)).appendNavTrail(root)
                    .addChild("Rename Query", actionURL(QueryAction.newQuery));
            return root;
        }
    }
*/

    @RequiresPermissionClass(ReadPermission.class)
    public class SourceQueryAction extends FormViewAction<SourceForm>
    {
        SourceForm _form;

        public void validateCommand(SourceForm target, Errors errors)
        {
        }

        public ModelAndView getView(SourceForm form, boolean reshow, BindException errors) throws Exception
        {
            _form = form;
            if (form.getQueryDef() == null)
	    	{
	    		HttpView.throwNotFound();
	    		return null;
	    	}
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
                try
                {
                    ExceptionUtil.logExceptionToMothership(getViewContext().getRequest(), e);
                }
                catch (Throwable t)
                {
                    //
                }
                errors.reject("ERROR_MSG", e.toString());
                Logger.getLogger(QueryController.class).error("Error", e);
            }

            return new JspView<SourceForm>(QueryController.class, "sourceQuery.jsp", form, errors);
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
                if (query.isTableQueryDefinition() && StringUtils.trimToNull(form.ff_metadataText) == null)
                {
                    if (QueryManager.get().getQueryDef(getContainer(), form.getSchemaName().toString(), form.getQueryName(), false) != null)
                    {
                        // Remember the URL and redirect immediately because the form won't be able to create
                        // the URL again after the query definition is deleted
                        ActionURL redirect = _form.getForwardURL();
                        query.delete(getUser());
                        HttpView.throwRedirect(redirect);
                    }
                }
                else
                {
                    query.setMetadataXml(form.ff_metadataText);
                    query.save(getUser(), getContainer());
                }
                return true;
            }
            catch (SQLException e)
            {
                errors.reject("An exception occurred: " + e);
                Logger.getLogger(QueryController.class).error("Error", e);
                return false;
            }
        }

        public ActionURL getSuccessURL(SourceForm sourceForm)
        {
            return _form.getForwardURL();
        }

        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic(new HelpTopic("customSQL"));

            (new SchemaAction(_form)).appendNavTrail(root)
                    .addChild("Edit " + _form.getQueryName(), _form.urlFor(QueryAction.sourceQuery));
            return root;
        }
    }


    @RequiresPermissionClass(DeletePermission.class)
    public class DeleteQueryAction extends ConfirmAction<QueryForm>
    {
        QueryForm _form;

        public ModelAndView getConfirmView(QueryForm queryForm, BindException errors) throws Exception
        {
            return new JspView<QueryForm>(QueryController.class, "deleteQuery.jsp", queryForm, errors);
        }

        public boolean handlePost(QueryForm form, BindException errors) throws Exception
        {
            _form = form;
            QueryDefinition d = form.getQueryDef();
            if (null == d)
                return false;
            try
            {
                d.delete(getUser());
            }
            catch (Table.OptimisticConflictException x)
            {
            }
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


    @RequiresPermissionClass(ReadPermission.class)
    public class ExecuteQueryAction extends QueryViewAction
    {
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            assertQueryExists(form);

            _form = form;

            QueryDefinition query = form.getQueryDef();
            if (null == query)
            {
                HttpView.throwNotFound("Query '" + form.getQueryName() + "' in schema '" + form.getSchemaName().getSource() + "' not found");
                return null;
            }

            QueryView queryView = QueryView.create(form);
            if (isPrint())
            {
                queryView.setPrintView(true);
                getPageConfig().setTemplate(PageConfig.Template.Print);
                getPageConfig().setShowPrintDialog(true);
            }
            queryView.setShadeAlternatingRows(true);
            queryView.setShowBorders(true);
            setHelpTopic(new HelpTopic("customSQL"));
            return queryView;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            (new SchemaAction(_form)).appendNavTrail(root);
            root.addChild(_form.getQueryName(), _form.urlFor(QueryAction.executeQuery));
            return root;
        }
    }


    // for backwards compat same as _executeQuery.view ?_print=1
    @RequiresPermissionClass(ReadPermission.class)
    public class PrintRowsAction extends ExecuteQueryAction
    {
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            assertQueryExists(form);
            _print = true;
            String title = form.getQueryName();
            if (StringUtils.isEmpty(title))
                title = form.getSchemaName().toString();
            getPageConfig().setTitle(title, true);
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


    public static class ExportScriptForm extends QueryForm
    {
        private String _type;

        public String getScriptType()
        {
            return _type;
        }

        public void setScriptType(String type)
        {
            _type = type;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ExportScriptAction extends SimpleViewAction<ExportScriptForm>
    {
        public ModelAndView getView(ExportScriptForm form, BindException errors) throws Exception
        {
            assertQueryExists(form);

            return ExportScriptModel.getExportScriptView(QueryView.create(form), form.getScriptType(), getPageConfig(), getViewContext().getResponse());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ExportRowsExcelAction extends _ExportQuery
    {
        void _export(QueryForm form, QueryView view) throws Exception
        {
            view.exportToExcel(getViewContext().getResponse());
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ExportExcelTemplateAction extends _ExportQuery
    {
        void _export(QueryForm form, QueryView view) throws Exception
        {
            view.exportToExcelTemplate(getViewContext().getResponse());
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class ExportRowsTsvAction extends _ExportQuery
    {
        void _export(QueryForm form, QueryView view) throws Exception
        {
            view.exportToTsv(getViewContext().getResponse(), form.isExportAsWebPage());
        }
    }

    @RequiresNoPermission
    @IgnoresTermsOfUse
    public class ExcelWebQueryAction extends ExportRowsTsvAction
    {
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            if (!getContainer().hasPermission(getUser(), ReadPermission.class))
            {
                if (!getUser().isGuest())
                    HttpView.throwUnauthorized();
                getViewContext().getResponse().setHeader("WWW-Authenticate", "Basic realm=\"" + LookAndFeelProperties.getInstance(ContainerManager.getRoot()).getDescription() + "\"");
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


    @RequiresPermissionClass(ReadPermission.class)
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
                url.setAction(ExcelWebQueryAction.class);
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

    @RequiresPermissionClass(AdminPermission.class)
    public class CreateSnapshotAction extends FormViewAction<QuerySnapshotForm>
    {
        ActionURL _successURL;
        public void validateCommand(QuerySnapshotForm form, Errors errors)
        {
            String name = StringUtils.trimToNull(form.getSnapshotName());

            if (name != null)
            {
                QuerySnapshotDefinition def = QueryService.get().getSnapshotDef(getContainer(), form.getSchemaName().toString(), name);
                if (def != null)
                    errors.reject("snapshotQuery.error", "A Snapshot with the same name already exists");
            }
            else
                errors.reject("snapshotQuery.error", "The Query Snapshot name cannot be blank");
        }

        public ModelAndView getView(QuerySnapshotForm form, boolean reshow, BindException errors) throws Exception
        {
            if (!reshow)
            {
                List<DisplayColumn> columns = QuerySnapshotService.get(form.getSchemaName().toString()).getDisplayColumns(form);
                String[] columnNames = new String[columns.size()];
                int i=0;

                for (DisplayColumn dc : columns)
                    columnNames[i++] = dc.getName();
                form.setSnapshotColumns(columnNames);
            }
            return new JspView<QueryForm>("/org/labkey/query/controllers/createSnapshot.jsp", form, errors);
        }

        public boolean handlePost(QuerySnapshotForm form, BindException errors) throws Exception
        {
            List<String> errorList = new ArrayList<String>();
            _successURL = QuerySnapshotService.get(form.getSchemaName().toString()).createSnapshot(form, errorList);
            if (!errorList.isEmpty())
            {
                for (String error : errorList)
                    errors.reject("snapshotQuery.error", error);
                return false;
            }
            return true;
        }

        public ActionURL getSuccessURL(QuerySnapshotForm queryForm)
        {
            return _successURL;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Create Query Snapshot");
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class EditSnapshotAction extends FormViewAction<QuerySnapshotForm>
    {
        ActionURL _successURL;

        public void validateCommand(QuerySnapshotForm form, Errors errors)
        {
        }

        public ModelAndView getView(QuerySnapshotForm form, boolean reshow, BindException errors) throws Exception
        {
            if (!reshow)
                form.init(QueryService.get().getSnapshotDef(getContainer(), form.getSchemaName().toString(), form.getSnapshotName()), getUser());

            VBox box = new VBox();
            QuerySnapshotService.I provider = QuerySnapshotService.get(form.getSchemaName().toString());

            if (provider != null)
            {
                boolean showHistory = BooleanUtils.toBoolean(getViewContext().getActionURL().getParameter("showHistory"));

                box.addView(new JspView<QueryForm>("/org/labkey/query/controllers/editSnapshot.jsp", form, errors));

                if (showHistory)
                {
                    HttpView historyView = provider.createAuditView(form);
                    if (historyView != null)
                        box.addView(historyView);
                }

                box.addView(new JspView<QueryForm>("/org/labkey/query/controllers/createSnapshot.jsp", form, errors));
            }
            return box;
        }

        public boolean handlePost(QuerySnapshotForm form, BindException errors) throws Exception
        {
            QuerySnapshotDefinition def = QueryService.get().getSnapshotDef(getContainer(), form.getSchemaName().toString(), form.getSnapshotName());
            if (def != null)
            {
                List<String> errorList = new ArrayList<String>();

                def.setColumns(form.getFieldKeyColumns());

                _successURL = QuerySnapshotService.get(form.getSchemaName().toString()).updateSnapshotDefinition(getViewContext(), def, errorList);
                if (!errorList.isEmpty())
                {
                    for (String error : errorList)
                        errors.reject(SpringActionController.ERROR_MSG, error);
                    return false;
                }
            }
            else
                errors.reject("snapshotQuery.error", "Unable to create QuerySnapshotDefinition");

            return false;
        }

        public ActionURL getSuccessURL(QuerySnapshotForm form)
        {
            return _successURL;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Edit Query Snapshot");
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class UpdateSnapshotAction extends SimpleViewAction<QuerySnapshotForm>
    {
        public ModelAndView getView(QuerySnapshotForm form, BindException errors) throws Exception
        {
            List<String> errorList = new ArrayList<String>();
            ActionURL url = QuerySnapshotService.get(form.getSchemaName().toString()).updateSnapshot(form, errorList);
            if (url != null)
                return HttpView.redirect(url);
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class MetadataServiceAction extends GWTServiceAction
    {
        protected BaseRemoteService createService()
        {
            return new MetadataServiceImpl(getViewContext());
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
    public class MetadataQueryAction extends FormViewAction<QueryForm>
    {
        QueryDefinition _query = null;
        QueryForm _form = null;
        
        public void validateCommand(QueryForm target, Errors errors)
        {
        }

        public ModelAndView getView(QueryForm form, boolean reshow, BindException errors) throws Exception
        {
            assertQueryExists(form);
            _form = form;
            _query = _form.getQueryDef();
            Map<String, String> props = new HashMap<String, String>();
            props.put("schemaName", form.getSchemaName().toString());
            props.put("queryName", form.getQueryName());
            if (!_query.isTableQueryDefinition())
            {
                props.put(MetadataEditor.DESIGN_QUERY_URL, _form.getQueryDef().urlFor(QueryAction.designQuery, getContainer()).toString());
            }
            props.put(MetadataEditor.EDIT_SOURCE_URL, _form.getQueryDef().urlFor(QueryAction.sourceQuery, getContainer()).toString());
            props.put(MetadataEditor.VIEW_DATA_URL, _form.getQueryDef().urlFor(QueryAction.executeQuery, getContainer()).toString());

            return new GWTView(MetadataEditor.class, props);
        }

        public boolean handlePost(QueryForm form, BindException errors) throws Exception
        {
            return false;
        }

        public ActionURL getSuccessURL(QueryForm metadataForm)
        {
            return _query.urlFor(QueryAction.metadataQuery);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            (new SchemaAction(_form)).appendNavTrail(root);
            root.addChild("Edit Metadata: " + _form.getQueryName(), _query.urlFor(QueryAction.metadataQuery));
            return root;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
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
                    SourceForm srcForm = new SourceForm(getViewContext());
                    srcForm.bindParameters(getPropertyValues());
                    return a.getView(srcForm, reshow, errors);
                }
                QueryDocument queryDoc = _queryDef.getDesignDocument(form.getSchema());
                if (queryDoc == null)
                    return HttpView.redirect(_queryDef.urlFor(QueryAction.sourceQuery));
                form.ff_designXML = queryDoc.toString();
            }
            setHelpTopic(new HelpTopic("customSQL"));
            return new JspView<DesignForm>(QueryController.class, "designQuery.jsp", form, errors);
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
            (new SchemaAction(_form)).appendNavTrail(root);
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


    @RequiresPermissionClass(ReadPermission.class)
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
                    errors.reject(ERROR_MSG, "Inherited view '" + view.getName() + "' can only be edited from the folder it was created in");
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
            return new JspView<ChooseColumnsForm>(QueryController.class, "chooseColumns.jsp", form, errors);
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
            boolean isHidden = false;
            if (canEdit)
            {
                CustomView view = form.getQueryDef().getCustomView(owner, getViewContext().getRequest(), name);
                if (view == null || (owner != null && view.getOwner() == null))
                {
                    view = form.getQueryDef().createCustomView(owner, name);
                }
                ViewDocument doc = ViewDocument.Factory.parse(StringUtils.trimToEmpty(form.ff_designXML));
                ((CustomViewImpl) view).update(doc, form.ff_saveFilter);
                if (form.canSaveForAllUsers())
                {
                    view.setCanInherit(form.ff_inheritable);
                }
                isHidden = view.isHidden();
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
                else if (!isHidden)
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


    @RequiresPermissionClass(ReadPermission.class)
    public class PropertiesQueryAction extends FormViewAction<PropertiesForm>
    {
        PropertiesForm _form = null;
        private String _queryName;
        
        public void validateCommand(PropertiesForm target, Errors errors)
        {
        }

        public ModelAndView getView(PropertiesForm form, boolean reshow, BindException errors) throws Exception
        {
            // assertQueryExists requires that it be well-formed
            // assertQueryExists(form);
            QueryDefinition queryDef = form.getQueryDef();
            if (queryDef == null)
			{
                HttpView.throwNotFound("Query not found");
				return null;
			}
            _form = form;
            _form.setDescription(queryDef.getDescription());
            _form.setInheritable(queryDef.canInherit());
            _form.setHidden(queryDef.isHidden());
            setHelpTopic(new HelpTopic("customSQL"));
            _queryName = form.getQueryName();
            
            return new JspView<PropertiesForm>(QueryController.class, "propertiesQuery.jsp", form, errors);
        }

        public boolean handlePost(PropertiesForm form, BindException errors) throws Exception
        {
            // assertQueryExists requires that it be well-formed
            // assertQueryExists(form);
            if (!form.canEdit())
                HttpView.throwUnauthorized();
            QueryDefinition queryDef = form.getQueryDef();
            _queryName = form.getQueryName();
            if (queryDef == null || !queryDef.getContainer().getId().equals(getContainer().getId()))
                HttpView.throwNotFound("Query not found");

			_form = form;
			
			if (!StringUtils.isEmpty(form.rename) && !form.rename.equalsIgnoreCase(queryDef.getName()))
			{
				QueryService s = QueryService.get();
				QueryDefinition copy = s.createQueryDef(queryDef.getContainer(), queryDef.getSchemaName(), form.rename);
				copy.setSql(queryDef.getSql());
				copy.setMetadataXml(queryDef.getMetadataXml());
				copy.setDescription(form.description);
				copy.setCanInherit(form.inheritable);
				copy.setIsHidden(form.hidden);
				copy.save(getUser(), copy.getContainer());
				queryDef.delete(getUser());
				// update form so getSuccessURL() works
				_form = new PropertiesForm(form.getSchemaName().toString(), form.rename);
				_form.setViewContext(form.getViewContext());
                _queryName = form.rename;
				return true;
			}

            queryDef.setDescription(form.description);
            queryDef.setCanInherit(form.inheritable);
            queryDef.setIsHidden(form.hidden);
            queryDef.save(getUser(), getContainer());
            return true;
        }

        public ActionURL getSuccessURL(PropertiesForm propertiesForm)
        {
            ActionURL url = new ActionURL(BeginAction.class, propertiesForm.getViewContext().getContainer());
            url.addParameter("schemaName", propertiesForm.getSchemaName());
            if (null != _queryName)
                url.addParameter("queryName", _queryName);
            return url;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            (new SchemaAction(_form)).appendNavTrail(root);
            root.addChild("Edit query properties");
            return root;
        }
    }


    @RequiresPermissionClass(DeletePermission.class)
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
            TableInfo table = form.getQueryDef().getTable(form.getSchema(), null, true);
            QueryUpdateForm quf = new QueryUpdateForm(table, getViewContext(), errors);
            if (!table.hasPermission(getUser(), DeletePermission.class))
            {
                HttpView.throwUnauthorized();
            }
            try
            {
                _url = table.delete(getUser(), forward, quf);
            }
            catch (SQLException x)
            {
                if (!SqlDialect.isConstraintException(x))
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

    protected static abstract class UserSchemaAction extends FormViewAction<QueryUpdateForm>
    {
        QueryForm _form;
        UserSchema _schema;
        TableInfo _table;

        public BindException bindParameters(PropertyValues m) throws Exception
        {
            QueryForm form = new QueryForm();
            form.setViewContext(getViewContext());
            form.bindParameters(getViewContext().getBindPropertyValues());

            _form = form;
            _schema = form.getSchema();
            if (null == _schema)
            {
                HttpView.throwNotFound("Schema not found");
                return null;
            }
            _table = _schema.getTable(form.getQueryName());
            if (null == _table)
            {
                HttpView.throwNotFound("Query not found");
                return null;
            }
            QueryUpdateForm command = new QueryUpdateForm(_table, getViewContext(), null);
            BindException errors = new BindException(new BeanUtilsPropertyBindingResult(command, "form"));
            command.validateBind(errors);
            return errors;
        }

        public void validateCommand(QueryUpdateForm target, Errors errors)
        {
        }

        public ActionURL getSuccessURL(QueryUpdateForm form)
        {
            return _schema.urlFor(QueryAction.executeQuery, _form.getQueryDef());
        }

        public ActionURL getCancelURL(QueryUpdateForm form)
        {
            ActionURL cancelURL;
            if (getViewContext().getActionURL().getParameter(QueryParam.srcURL) != null)
            {
                cancelURL = new ActionURL(getViewContext().getActionURL().getParameter(QueryParam.srcURL));
            }
            else if (_schema != null && _table != null)
            {
                cancelURL = _schema.urlFor(QueryAction.executeQuery, _form.getQueryDef());
            }
            else
            {
                cancelURL = new ActionURL(ExecuteQueryAction.class, form.getContainer());
            }
            return cancelURL;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            if (_table != null)
                root.addChild(_table.getName(), getSuccessURL(null));
            return root;
        }

        protected Map<String, Object> getFormValues(QueryUpdateForm form, TableInfo table)
        {
            Map<String, Object> values = new HashMap<String, Object>();
            for (ColumnInfo column : form.getTable().getColumns())
            {
                if (form.hasTypedValue(column))
                {
                    values.put(column.getName(), form.getTypedValue(column));
                }
            }
            return values;
        }

        protected void doInsertUpdate(QueryUpdateForm form, BindException errors, boolean insert) throws Exception
        {
            TableInfo table = form.getTable();
            if (!table.hasPermission(form.getUser(), insert ? InsertPermission.class : UpdatePermission.class))
                HttpView.throwUnauthorized();

            Map<String, Object> values = getFormValues(form, table);

            QueryUpdateService qus = table.getUpdateService();
            if (qus == null)
                throw new IllegalArgumentException("The query '" + _table.getName() + "' in the schema '" + _schema.getName() + "' is not updatable.");

            try
            {
                if (insert)
                {
                    qus.insertRow(form.getUser(), form.getContainer(), values);
                }
                else
                {
                    Map<String, Object> oldValues = null;
                    if (form.getOldValues() instanceof Map)
                    {
                        oldValues = (Map<String, Object>)form.getOldValues();
                    }
                    qus.updateRow(form.getUser(), form.getContainer(), values, oldValues);
                }
            }
            catch (SQLException x)
            {
                if (!SqlDialect.isConstraintException(x))
                    throw x;
                errors.reject(ERROR_MSG, x.getMessage());
            }
            catch (Exception x)
            {
                errors.reject(ERROR_MSG, x.getMessage());
            }
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public static class DetailsQueryRowAction extends UserSchemaAction
    {
        public ModelAndView getView(QueryUpdateForm tableForm, boolean reshow, BindException errors) throws Exception
        {
            ButtonBar bb = new ButtonBar();
            bb.setStyle(ButtonBar.Style.separateButtons);

            if (_schema != null && _table != null)
            {
                if (getViewContext().hasPermission(ACL.PERM_UPDATE))
                {
                    StringExpression updateExpr = _schema.urlExpr(QueryAction.updateQueryRow, _form.getQueryDef());
                    if (updateExpr != null)
                    {
                        ActionURL updateUrl = new ActionURL(updateExpr.eval(tableForm.getTypedValues()));
                        ActionButton editButton = new ActionButton("Edit", updateUrl);
                        bb.add(editButton);
                    }
                }

                ActionURL gridUrl = _schema.urlFor(QueryAction.executeQuery, _form.getQueryDef());
                if (gridUrl != null)
                {
                    ActionButton gridButton = new ActionButton("Show Grid", gridUrl);
                    bb.add(gridButton);
                }
            }

            DetailsView view = new DetailsView(tableForm);
            view.getDataRegion().setButtonBar(bb);
            return view;
        }

        public boolean handlePost(QueryUpdateForm tableForm, BindException errors) throws Exception
        {
            return false;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            super.appendNavTrail(root);
            root.addChild("Details");
            return root;
        }
    }

    @RequiresPermissionClass(InsertPermission.class)
    public static class InsertQueryRowAction extends UserSchemaAction
    {
        public ModelAndView getView(QueryUpdateForm tableForm, boolean reshow, BindException errors) throws Exception
        {
            ButtonBar bb = new ButtonBar();
            bb.setStyle(ButtonBar.Style.separateButtons);
            ActionButton btnSubmit = new ActionButton(getViewContext().getActionURL(), "Submit");
            ActionButton btnCancel = new ActionButton(getCancelURL(tableForm), "Cancel");
            bb.add(btnSubmit);
            bb.add(btnCancel);
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

    @RequiresPermissionClass(UpdatePermission.class)
    public static class UpdateQueryRowAction extends UserSchemaAction
    {
        public ModelAndView getView(QueryUpdateForm tableForm, boolean reshow, BindException errors) throws Exception
        {
            ButtonBar bb = new ButtonBar();
            bb.setStyle(ButtonBar.Style.separateButtons);
            ActionButton btnSubmit = new ActionButton(getViewContext().getActionURL(), "Submit");
            ActionButton btnCancel = new ActionButton(getCancelURL(tableForm), "Cancel");
            bb.add(btnSubmit);
            bb.add(btnCancel);
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
        private String _containerFilter;

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

        public String getContainerFilter()
        {
            return _containerFilter;
        }

        public void setContainerFilter(String containerFilter)
        {
            _containerFilter = containerFilter;
        }
    }


    @ActionNames("selectRows, getQuery")
    @RequiresPermissionClass(ReadPermission.class)
    @ApiVersion(9.1)
    public class SelectRowsAction extends ApiAction<APIQueryForm>
    {
        public ApiResponse execute(APIQueryForm form, BindException errors) throws Exception
        {
            //TODO: remove this hack once we can send maxRows=0 down to the table layer
            //currently Query and the table layer interprets maxRows=0 as meaning "all rows"
            String maxRowsParam = StringUtils.trimToNull(getViewContext().getRequest().getParameter(QueryView.DATAREGIONNAME_DEFAULT + "." + QueryParam.maxRows.name()));
            boolean metaDataOnly = "0".equals(maxRowsParam);

            assertQueryExists(form);

            //show all rows by default
            if(null == form.getLimit() 
                    && null == getViewContext().getRequest().getParameter(form.getDataRegionName() + "." + QueryParam.maxRows))
                form.getQuerySettings().setShowRows(ShowRows.ALL);

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
            if (form.getContainerFilter() != null)
            {
                // If the user specified an incorrect filter, throw an IllegalArgumentException
                ContainerFilter.Type containerFilterType =
                    ContainerFilter.Type.valueOf(form.getContainerFilter());
                form.getQuerySettings().setContainerFilterName(containerFilterType.name());
            }

            //split the column list into a list of field keys
            List<FieldKey> fieldKeys = new ArrayList<FieldKey>();
            String columns = getViewContext().getRequest().getParameter("query.columns");
            if(null != columns && columns.length() > 0)
            {
                String[] cols = columns.split(",");
                for(String col : cols)
                    fieldKeys.add(FieldKey.fromString(col.trim()));
            }

            QueryView view = QueryView.create(form);
            if(metaDataOnly)
                view.getSettings().setMaxRows(1); //query assumes that 0 means all rows!

            //if viewName was specified, ensure that it was actually found and used
            //QueryView.create() will happily ignore an invalid view name and just return the default view
            if(null != StringUtils.trimToNull(form.getViewName()) &&
                    null == view.getQueryDef().getCustomView(getUser(), getViewContext().getRequest(), form.getViewName()))
            {
                throw new NotFoundException("The view named '" + form.getViewName() + "' does not exist for this user!");
            }

            boolean isEditable = isQueryEditable(view.getTable());

            //if requested version is >= 9.1, use the extended api query response
            if(getRequestedApiVersion() >= 9.1)
                return new ExtendedApiQueryResponse(view, getViewContext(), isEditable, true,
                        form.getSchemaName().toString(), form.getQueryName(), form.getQuerySettings().getOffset(), fieldKeys, metaDataOnly);
            else
                return new ApiQueryResponse(view, getViewContext(), isEditable, true,
                        form.getSchemaName().toString(), form.getQueryName(), form.getQuerySettings().getOffset(), fieldKeys, metaDataOnly);
        }
    }

    protected boolean isQueryEditable(TableInfo table)
    {
        if (!getViewContext().getContainer().hasPermission(getUser(), DeletePermission.class))
            return false;
        QueryUpdateService updateService = null;
        try
        {
            updateService = table.getUpdateService();
        }
        catch(Exception ignore) {}
        return null != table && null != updateService;
    }

    public static class ExecuteSqlForm
    {
        private String _schemaName;
        private String _sql;
        private Integer _maxRows;
        private Integer _offset;
        private String _containerFilter;

        public String getSchemaName()
        {
            return _schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            _schemaName = schemaName;
        }

        public String getSql()
        {
            return _sql;
        }

        public void setSql(String sql)
        {
            _sql = sql;
        }

        public Integer getMaxRows()
        {
            return _maxRows;
        }

        public void setMaxRows(Integer maxRows)
        {
            _maxRows = maxRows;
        }

        public Integer getOffset()
        {
            return _offset;
        }

        public void setOffset(Integer offset)
        {
            _offset = offset;
        }

        public String getContainerFilter()
        {
            return _containerFilter;
        }

        public void setContainerFilter(String containerFilter)
        {
            _containerFilter = containerFilter;
        }

        public void setLimit(Integer limit)
        {
            _maxRows = limit;
        }

        public void setStart(Integer start)
        {
            _offset = start;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    @ApiVersion(9.1)
    public class ExecuteSqlAction extends ApiAction<ExecuteSqlForm>
    {
        public ApiResponse execute(ExecuteSqlForm form, BindException errors) throws Exception
        {
            String schemaName = StringUtils.trimToNull(form.getSchemaName());
            if(null == schemaName)
                throw new IllegalArgumentException("No value was supplied for the required parameter 'schemaName'.");
            String sql = StringUtils.trimToNull(form.getSql());
            if(null == sql)
                throw new IllegalArgumentException("No value was supplied for the required parameter 'sql'.");

            UserSchema schema = QueryService.get().getUserSchema(getViewContext().getUser(), getViewContext().getContainer(), schemaName);

            //create a temp query settings object initialized with the posted LabKey SQL
            //this will provide a temporary QueryDefinition to Query
            TempQuerySettings settings = new TempQuerySettings(schemaName, sql, getViewContext().getContainer());

            //need to explicitly turn off various UI options that will try to refer to the
            //current URL and query string
            settings.setAllowChooseQuery(false);
            settings.setAllowChooseView(false);
            settings.setAllowCustomizeView(false);

            //by default, return all rows
            settings.setShowRows(ShowRows.ALL);

            //apply optional settings (maxRows, offset)
            boolean metaDataOnly = false;
            if(null != form.getMaxRows() && form.getMaxRows().intValue() >= 0)
            {
                settings.setShowRows(ShowRows.PAGINATED);
                settings.setMaxRows(0 == form.getMaxRows().intValue() ? 1 : form.getMaxRows().intValue());
                metaDataOnly = (0 == form.getMaxRows().intValue());
            }

            if(null != form.getOffset())
                settings.setOffset(form.getOffset().longValue());

            if (form.getContainerFilter() != null)
            {
                // If the user specified an incorrect filter, throw an IllegalArgumentException
                ContainerFilter.Type containerFilterType =
                    ContainerFilter.Type.valueOf(form.getContainerFilter());
                settings.setContainerFilterName(containerFilterType.name());
            }

            //build a query view using the schema and settings
            QueryView view = new QueryView(schema, settings, errors);
            view.setShowRecordSelectors(false);
            view.setShowExportButtons(false);
            view.setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);

            boolean isEditable = isQueryEditable(view.getTable());

            if(getRequestedApiVersion() >= 9.1)
                return new ExtendedApiQueryResponse(view, getViewContext(), isEditable,
                        false, schemaName, "sql", 0, null, metaDataOnly);
            else
                return new ApiQueryResponse(view, getViewContext(), isEditable,
                        false, schemaName, "sql", 0, null, metaDataOnly);
        }
    }

    public static class ExportSqlForm
    {
        private String _sql;
        private String _schemaName;
        private String _containerFilter;
        private String _format = "excel";

        public String getSql()
        {
            return _sql;
        }

        public void setSql(String sql)
        {
            _sql = sql;
        }

        public String getSchemaName()
        {
            return _schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            _schemaName = schemaName;
        }

        public String getContainerFilter()
        {
            return _containerFilter;
        }

        public void setContainerFilter(String containerFilter)
        {
            _containerFilter = containerFilter;
        }

        public String getFormat()
        {
            return _format;
        }

        public void setFormat(String format)
        {
            _format = format;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    @ApiVersion(9.2)
    public class ExportSqlAction extends ExportAction<ExportSqlForm>
    {
        public void export(ExportSqlForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            String schemaName = StringUtils.trimToNull(form.getSchemaName());
            if (null == schemaName)
                throw new IllegalArgumentException("No value was supplied for the required parameter 'schemaName'.");
            String sql = StringUtils.trimToNull(form.getSql());
            if (null == sql)
                throw new IllegalArgumentException("No value was supplied for the required parameter 'sql'.");

            UserSchema schema = QueryService.get().getUserSchema(getViewContext().getUser(), getViewContext().getContainer(), schemaName);

            //create a temp query settings object initialized with the posted LabKey SQL
            //this will provide a temporary QueryDefinition to Query
            TempQuerySettings settings = new TempQuerySettings(schemaName, sql, getViewContext().getContainer());

            //need to explicitly turn off various UI options that will try to refer to the
            //current URL and query string
            settings.setAllowChooseQuery(false);
            settings.setAllowChooseView(false);
            settings.setAllowCustomizeView(false);

            //return all rows
            settings.setShowRows(ShowRows.ALL);

            //add container filter if supplied
            if (form.getContainerFilter() != null && form.getContainerFilter().length() > 0)
            {
                ContainerFilter.Type containerFilterType =
                    ContainerFilter.Type.valueOf(form.getContainerFilter());
                settings.setContainerFilterName(containerFilterType.name());
            }

            //build a query view using the schema and settings
            QueryView view = new QueryView(schema, settings, errors);
            view.setShowRecordSelectors(false);
            view.setShowExportButtons(false);
            view.setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);
            
            //export it
            response.setContentType("application/vnd.ms-excel");
            response.setHeader("Content-disposition", "attachment; filename=\"QueryResults.xls\"");
            response.setHeader("Pragma", "private");
            response.setHeader("Cache-Control", "private");

            if ("excel".equalsIgnoreCase(form.getFormat()))
                view.exportToExcel(response);
            else if ("tsv".equalsIgnoreCase(form.getFormat()))
                view.exportToTsv(response);
        }
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
    public abstract class BaseSaveRowsAction extends ApiAction<ApiSaveRowsForm>
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
                throw new IllegalArgumentException("No 'rows' array supplied!");

            UserSchema schema = QueryService.get().getUserSchema(user, container, schemaName);
            if (null == schema)
                throw new IllegalArgumentException("The schema '" + schemaName + "' does not exist.");

            TableInfo table = schema.getTable(queryName);
            if (table == null)
                throw new IllegalArgumentException("The query '" + queryName + "' in the schema '" + schemaName + "' does not exist.");

            if (table.getPkColumns().size() == 0)
                throw new IllegalArgumentException("The table '" + table.getPublicSchemaName() + "." + table.getPublicName() + "' cannot be updated because it has no primary key defined!");

            checkTablePermission(user, table);
            QueryUpdateService qus = table.getUpdateService();
            if (null == qus)
                throw new IllegalArgumentException("The query '" + queryName + "' in the schema '" + schemaName + "' is not updatable via the HTTP-based APIs.");

            //we will transact operations by default, but the user may
            //override this by sending a "transacted" property set to false
            boolean transacted = json.optBoolean("transacted", true);
            if (transacted)
                schema.beginTransaction();

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
                    JSONObject jsonObj = rows.getJSONObject(idx);
                    if(null != jsonObj)
                    {
                        CaseInsensitiveHashMap<Object> rowMap = new CaseInsensitiveHashMap<Object>(jsonObj);
                        saveRow(table, qus, rowMap, responseRows);
                        ++rowsAffected;
                    }
                }

                if (transacted)
                    schema.commitTransaction();
            }
            finally
            {
                if (transacted && schema.isTransactionActive())
                    schema.rollbackTransaction();
            }

            response.put("rowsAffected", rowsAffected);

            return response;
        }

        protected abstract void checkTablePermission(User user, TableInfo table);

        /**
         * Dervied classes should implement this method to do the actual save operation (insert, update or delete)
         * @param table The table
         * @param qus The QueryUpdateService
         * @param row The row map
         * @param responseRows The array of response row maps to append to
         * @throws InvalidKeyException Thrown if the key is invalid
         * @throws DuplicateKeyException Thrown if the key is a duplicate of an existing key (insert)
         * @throws ValidationException Thrown if the data is not valid
         * @throws QueryUpdateServiceException Thrown if there is a implementation-specific error
         * @throws SQLException Thrown if there was a problem communicating with the database
         * @throws UnauthorizedException Thrown if the user does not have permissions to save the row
         */
        protected abstract void saveRow(TableInfo table, QueryUpdateService qus, Map<String, Object> row, ArrayList<Object> responseRows)
                throws InvalidKeyException, DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException, UnauthorizedException;

        /**
         * Returns the name of the dervied class's command. This will be returned to
         * the client in the 'command' property.
         * @return The name of the derived class's command
         */
        protected abstract String getSaveCommandName(); //unfortunatley, getCommandName() is already defined in Spring action classes

    }

    @RequiresPermissionClass(UpdatePermission.class)
    @ApiVersion(8.3)
    public class UpdateRowsAction extends BaseSaveRowsAction
    {
        protected String getSaveCommandName()
        {
            return "update";
        }

        protected void checkTablePermission(User user, TableInfo table)
        {
            if (!table.hasPermission(user, UpdatePermission.class))
                HttpView.throwUnauthorized();
        }

        protected void saveRow(TableInfo table, QueryUpdateService qus, Map<String, Object> row, ArrayList<Object> responseRows)
                throws InvalidKeyException, DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException, UnauthorizedException
        {
            Map<String,Object> updatedRow = qus.updateRow(getViewContext().getUser(), getViewContext().getContainer(),
                                                            row, null);
            if(null != updatedRow)
                updatedRow = qus.getRow(getViewContext().getUser(), getViewContext().getContainer(), updatedRow);
            if(null != updatedRow)
                responseRows.add(updatedRow);
        }

    }

    @RequiresPermissionClass(InsertPermission.class)
    @ApiVersion(8.3)
    public class InsertRowsAction extends BaseSaveRowsAction
    {
        protected String getSaveCommandName()
        {
            return "insert";
        }

        protected void checkTablePermission(User user, TableInfo table)
        {
            if (!table.hasPermission(user, InsertPermission.class))
                HttpView.throwUnauthorized();
        }

        protected void saveRow(TableInfo table, QueryUpdateService qus, Map<String, Object> row, ArrayList<Object> responseRows)
                throws InvalidKeyException, DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException, UnauthorizedException
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
    @RequiresPermissionClass(DeletePermission.class)
    @ApiVersion(8.3)
    public class DeleteRowsAction extends BaseSaveRowsAction
    {
        protected String getSaveCommandName()
        {
            return "delete";
        }

        protected void checkTablePermission(User user, TableInfo table)
        {
            if (!table.hasPermission(user, DeletePermission.class))
                HttpView.throwUnauthorized();
        }

        protected void saveRow(TableInfo table, QueryUpdateService qus, Map<String, Object> row, ArrayList<Object> responseRows) throws InvalidKeyException, DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException, UnauthorizedException
        {
            Map<String,Object> deletedRow = qus.deleteRow(getViewContext().getUser(), getViewContext().getContainer(),
                                                            row);
            if(null != deletedRow)
                responseRows.add(deletedRow);
        }
    }

    @RequiresNoPermission //will check below
    public class SaveRowsAction extends BaseSaveRowsAction
    {
        private static final String PROP_VALUES = "values";
        private static final String PROP_OLD_KEYS = "oldKeys";
        private static final String PROP_COMMAND = "command";

        protected void checkTablePermission(User user, TableInfo table)
        {
            // will check below
        }

        protected void saveRow(TableInfo table, QueryUpdateService qus, Map<String, Object> row, ArrayList<Object> responseRows) throws InvalidKeyException, DuplicateKeyException, ValidationException, QueryUpdateServiceException, SQLException, UnauthorizedException
        {
            User user = getViewContext().getUser();
            Container container = getViewContext().getContainer();

            //for this action, the shape of the row map is a little different so as to
            //accommodate the action and old keys and such
            String command = (String)row.get(PROP_COMMAND);
            Map<String,Object> values = ((JSONObject)row.get(PROP_VALUES));
            Map<String,Object> oldKeys = row.containsKey(PROP_OLD_KEYS) ? ((JSONObject)row.get(PROP_OLD_KEYS)) : null;
            Map<String, Object> responseRow = new HashMap<String,Object>();

            responseRow.put(PROP_COMMAND, command);
            if(null != oldKeys)
                responseRow.put(PROP_OLD_KEYS, oldKeys);

            if("insert".equalsIgnoreCase(command))
            {
                if(!container.hasPermission(user, InsertPermission.class))
                    throw new UnauthorizedException("You do not have permissions to insert data into this folder.");
                if (!table.hasPermission(user, InsertPermission.class))
                    throw new UnauthorizedException("You do not have permission to insert data into this table.");

                Map<String,Object> insertedRow = qus.insertRow(user, container, values);
                if(null != insertedRow)
                    insertedRow = qus.getRow(user, container, insertedRow);
                if(null != insertedRow)
                    responseRow.put(PROP_VALUES, insertedRow);
            }
            else if("update".equalsIgnoreCase(command))
            {
                if(!container.hasPermission(user, UpdatePermission.class))
                    throw new UnauthorizedException("You do not have permissions to update data into this folder.");
                if (!table.hasPermission(user, UpdatePermission.class))
                    throw new UnauthorizedException("You do not have permission to update data into this table.");

                Map<String,Object> updatedRow = qus.updateRow(user, container, values, oldKeys);
                if(null != updatedRow)
                    updatedRow = qus.getRow(user, container, updatedRow);
                if(null != updatedRow)
                    responseRow.put(PROP_VALUES, updatedRow);
            }
            else if("delete".equalsIgnoreCase(command))
            {
                if(!container.hasPermission(user, DeletePermission.class))
                    throw new UnauthorizedException("You do not have permissions to delete data into this folder.");
                if (!table.hasPermission(user, DeletePermission.class))
                    throw new UnauthorizedException("You do not have permission to delete data into this table.");

                qus.deleteRow(user, container, values);
            }
            else
                throw new IllegalArgumentException("'" + command + "' is not a valid command name! Use 'insert', 'update', 'delete'.");


            responseRows.add(responseRow);
        }

        protected String getSaveCommandName()
        {
            return "save";
        }
    }

    @RequiresPermissionClass(AdminPermission.class)
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


    @RequiresPermissionClass(AdminPermission.class)
    public class AdminAction extends SimpleViewAction<QueryForm>
    {
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
           setHelpTopic(new HelpTopic("externalSchemas"));
           return new JspView<QueryForm>(getClass(), "admin.jsp", form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new BeginAction().appendNavTrail(root);
            root.addChild("Schema Administration", new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer()));
            return root;
        }
    }

    
    @RequiresSiteAdmin
    public class InsertExternalSchemaAction extends FormViewAction<DbUserSchemaForm>
    {
        public void validateCommand(DbUserSchemaForm form, Errors errors)
        {
			form.validate(errors);
        }

        public ModelAndView getView(DbUserSchemaForm form, boolean reshow, BindException errors) throws Exception
        {
            setHelpTopic(new HelpTopic("externalSchemas"));
            return new JspView<ExternalSchemaBean>(QueryController.class, "externalSchema.jsp", new ExternalSchemaBean(getContainer(), form.getBean(), true), errors);
        }

        public boolean handlePost(DbUserSchemaForm form, BindException errors) throws Exception
        {
            try
            {
                form.doInsert();
            }
            catch (SQLException e)
            {
                if (SqlDialect.isConstraintException(e))
                {
                    errors.reject(ERROR_MSG, "A schema by that name is already defined in this folder");
                    return false;
                }

                throw e;
            }

            return true;
        }

        public ActionURL getSuccessURL(DbUserSchemaForm dbUserSchemaForm)
        {
            return new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new AdminAction().appendNavTrail(root);
            root.addChild("Define External Schema", new QueryUrlsImpl().urlInsertExternalSchema(getContainer()));
            return root;
        }
    }


    public static class ExternalSchemaBean
    {
        private final Map<DbScope, Collection<String>> _scopesAndSchemas = new LinkedHashMap<DbScope, Collection<String>>();
        private final Container _c;
        private final DbUserSchemaDef _def;
        private final boolean _insert;
        private final Map<String, String> _help = new HashMap<String, String>();

        public ExternalSchemaBean(Container c, DbUserSchemaDef def, boolean insert)
        {
            _c = c;
            _def = def;
            _insert = insert;
            Collection<DbScope> scopes = DbScope.getDbScopes();

            for (DbScope scope : scopes)
            {
                Connection con = null;
                ResultSet rs = null;

                try
                {
                    con = scope.getConnection();
                    DatabaseMetaData dbmd = con.getMetaData();

                    rs = dbmd.getSchemas();

                    Collection<String> schemaNames = new LinkedList<String>();

                    while(rs.next())
                        schemaNames.add(rs.getString(1).trim());

                    _scopesAndSchemas.put(scope, schemaNames);
                }
                catch (SQLException e)
                {
                    LOG.error("Exception retrieving schemas from DbScope '" + scope.getDataSourceName() + "'");
                }
                finally
                {
                    ResultSetUtil.close(rs);

                    if (null != con)
                    {
                        try
                        {
                            con.close();
                        }
                        catch (SQLException e)
                        {
                            // ignore
                        }
                    }
                }
            }

            TableInfo ti = QueryManager.get().getTableInfoDbUserSchema();

            for (ColumnInfo ci : ti.getColumns())
                if (null != ci.getDescription())
                    _help.put(ci.getName(), ci.getDescription());
        }

        public Collection<DbScope> getScopes()
        {
            return _scopesAndSchemas.keySet();
        }

        public Collection<String> getSchemaNames(DbScope scope)
        {
            return _scopesAndSchemas.get(scope);
        }

        public DbUserSchemaDef getSchemaDef()
        {
            return _def;
        }

        public boolean isInsert()
        {
            return _insert;
        }

        public ActionURL getReturnURL()
        {
            return new ActionURL(AdminAction.class, _c);
        }

        public ActionURL getDeleteURL()
        {
            return getDeleteExternalSchemaURL(_c, _def.getDbUserSchemaId());
        }

        public String getHelpHTML(String fieldName)
        {
            return _help.get(fieldName);
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class EditExternalSchemaAction extends FormViewAction<DbUserSchemaForm>
    {
		public void validateCommand(DbUserSchemaForm form, Errors errors)
		{
            form.validate(errors);
		}

        public ModelAndView getView(DbUserSchemaForm form, boolean reshow, BindException errors) throws Exception
        {
            form.refreshFromDb();
            DbUserSchemaDef def = form.getBean();
            Container defContainer = def.lookupContainer();

            if (!defContainer.equals(getContainer()))
                throw new UnauthorizedException();

            setHelpTopic(new HelpTopic("externalSchemas"));
            return new JspView<ExternalSchemaBean>(QueryController.class, "externalSchema.jsp", new ExternalSchemaBean(getContainer(), def, false), errors);
        }

        public boolean handlePost(DbUserSchemaForm form, BindException errors) throws Exception
        {
            DbUserSchemaDef def = form.getBean();
            DbUserSchemaDef fromDb = QueryManager.get().getDbUserSchemaDef(def.getDbUserSchemaId());

            // Unauthorized if def in the database reports a different container
            if (!fromDb.lookupContainer().equals(getContainer()))
                throw new UnauthorizedException();

            try
            {
                form.doUpdate();
            }
            catch (SQLException e)
            {
                if (SqlDialect.isConstraintException(e))
                {
                    errors.reject(ERROR_MSG, "A schema by that name is already defined in this folder");
                    return false;
                }

                throw e;
            }
            return true;
        }

        public ActionURL getSuccessURL(DbUserSchemaForm dbUserSchemaForm)
        {
            return new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new AdminAction().appendNavTrail(root);
            root.addChild("Edit External Schema", new QueryUrlsImpl().urlInsertExternalSchema(getContainer()));
            return root;
        }
    }


    public static ActionURL getDeleteExternalSchemaURL(Container c, int schemaId)
    {
        ActionURL url = new ActionURL(DeleteExternalSchemaAction.class, c);
        url.addParameter("dbUserSchemaId", schemaId);
        return url;
    }


    @RequiresSiteAdmin
    public class DeleteExternalSchemaAction extends ConfirmAction<DbUserSchemaForm>
    {
        public String getConfirmText()
        {
            return "Delete";
        }

        public ModelAndView getConfirmView(DbUserSchemaForm form, BindException errors) throws Exception
        {
            form.refreshFromDb();
            return new HtmlView("Are you sure you want to delete the schema '" + form.getBean().getUserSchemaName() + "'? The tables and queries defined in this schema will no longer be accessible.");
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
            return new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer());
        }
    }


    // UNDONE: should use POST, change to FormHandlerAction
    @RequiresPermissionClass(AdminPermission.class)
    public class ReloadDbUserSchemaAction extends SimpleViewAction<DbUserSchemaForm>
    {
        public ModelAndView getView(DbUserSchemaForm form, BindException errors) throws Exception
        {
            form.refreshFromDb();
            DbUserSchemaDef def = form.getBean();

            try
            {
                QueryManager.get().reloadDbUserSchema(def);
            }
            catch (Exception e)
            {
                errors.reject(ERROR_MSG, "Could not reload schema " + def.getUserSchemaName() + ". The data source for the schema may be unreachable, or the schema may have been deleted.");
                getPageConfig().setTemplate(PageConfig.Template.Dialog);
                return new SimpleErrorView(errors);
            }

            return HttpView.redirect(getSuccessURL(form));
        }

        public ActionURL getSuccessURL(DbUserSchemaForm form)
        {
            return new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer(), form.getBean().getUserSchemaName());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }


    @RequiresPermissionClass(AdminPermission.class)
    public class ReloadAllUserSchemas extends SimpleRedirectAction
    {
        @Override
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            QueryManager.get().reloadAllDbUserSchemas(getContainer());
            return new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer(), "ALL");
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
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
    @RequiresPermissionClass(ReadPermission.class) @RequiresLogin
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
                if (!getViewContext().getContainer().hasPermission(getUser(), EditSharedViewPermission.class))
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


    @RequiresNoPermission
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
                (new SqlParser()).parseExpr(sql, errors);
            }
            catch (Throwable t)
            {
                Logger.getInstance(QueryController.class).error("Error", t);
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


    @RequiresPermissionClass(ReadPermission.class) @RequiresLogin
    public class ManageViewsAction extends SimpleViewAction<QueryForm>
    {
        QueryForm _form;

        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            _form = form;
            return new JspView<QueryForm>(QueryController.class, "manageViews.jsp", form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new BeginAction().appendNavTrail(root);
            root.addChild("Manage Views", QueryController.this.getViewContext().getActionURL());
            return root;
        }
    }


    @RequiresPermissionClass(ReadPermission.class)
    public class InternalDeleteView extends ConfirmAction<InternalViewForm>
    {
        public ModelAndView getConfirmView(InternalViewForm form, BindException errors) throws Exception
        {
            return new JspView<InternalViewForm>(QueryController.class, "internalDeleteView.jsp", form, errors);
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
            return new ActionURL(ManageViewsAction.class, getContainer());
        }
    }


    @RequiresPermissionClass(ReadPermission.class) @RequiresLogin
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
            return new JspView<InternalSourceViewForm>(QueryController.class, "internalSourceView.jsp", form, errors);
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
            return new ActionURL(ManageViewsAction.class, getViewContext().getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new ManageViewsAction().appendNavTrail(root);
            root.addChild("Edit source of Grid View");
            return root;
        }
    }


    @RequiresPermissionClass(ReadPermission.class) @RequiresLogin
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
            return new JspView<InternalNewViewForm>(QueryController.class, "internalNewView.jsp", form, errors);
        }

        public boolean handlePost(InternalNewViewForm form, BindException errors) throws Exception
        {
            if (form.ff_share)
            {
                if (!getContainer().hasPermission(getUser(), AdminPermission.class))
                    HttpView.throwUnauthorized();
            }
            CstmView[] existing = QueryManager.get().getCstmViews(getContainer(), form.ff_schemaName, form.ff_queryName, form.ff_viewName, form.ff_share ? null : getUser(), false);
            CstmView view;
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
                    Logger.getInstance(QueryController.class).error("Error", e);
                    errors.reject(ERROR_MSG, "An exception occurred: " + e);
                    return false;
                }
                _customViewId = view.getCustomViewId();
            }
            return true;
        }

        public ActionURL getSuccessURL(InternalNewViewForm form)
        {
            ActionURL forward = new ActionURL(InternalSourceViewAction.class, getContainer());
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


    @ActionNames("clearSelected, selectNone")
    @RequiresPermissionClass(ReadPermission.class)
    public static class SelectNoneAction extends ApiAction<SelectForm>
    {
        public SelectNoneAction()
        {
            super(SelectForm.class);
        }

        public ApiResponse execute(final SelectForm form, BindException errors) throws Exception
        {
            DataRegionSelection.clearAll(getViewContext(), form.getKey());
            return new SelectionResponse(0);
        }
    }

    public static class SelectForm
    {
        protected String key;

        public String getKey()
        {
            return key;
        }

        public void setKey(String key)
        {
            this.key = key;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public static class SelectAllAction extends ApiAction<SelectAllForm>
    {
        public SelectAllAction()
        {
            super(SelectAllForm.class);
        }

        public void validateForm(SelectAllForm form, Errors errors)
        {
            if (form.getSchemaName() == null ||
                form.getQueryName() == null)
            {
                errors.reject(ERROR_MSG, "schemaName and queryName required");
            }
        }

        public ApiResponse execute(final SelectAllForm form, BindException errors) throws Exception
        {
            int count = DataRegionSelection.selectAll(
                    getViewContext(), form.getKey(), form);
            return new SelectionResponse(count);
        }
    }

    public class SelectAllForm extends QueryForm
    {
        protected String key;

        public String getKey()
        {
            return key;
        }

        public void setKey(String key)
        {
            this.key = key;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public static class GetSelectedAction extends ApiAction<SelectForm>
    {
        public GetSelectedAction()
        {
            super(SelectForm.class);
        }

        public ApiResponse execute(final SelectForm form, BindException errors) throws Exception
        {
            Set<String> selected = DataRegionSelection.getSelected(
                    getViewContext(), form.getKey(), true, false);
            return new ApiSimpleResponse("selected", selected);
        }
    }

    @ActionNames("setSelected, setCheck")
    @RequiresPermissionClass(ReadPermission.class)
    public static class SetCheckAction extends ApiAction<SetCheckForm>
    {
        public SetCheckAction()
        {
            super(SetCheckForm.class);
        }

        public ApiResponse execute(final SetCheckForm form, BindException errors) throws Exception
        {
            String[] ids = form.getId(getViewContext().getRequest());
            List<String> selection = new ArrayList<String>();
            if (ids != null)
            {
                for (String id : ids)
                {
                    if (StringUtils.isNotBlank(id))
                        selection.add(id);
                }
            }

            int count = DataRegionSelection.setSelected(
                    getViewContext(), form.getKey(),
                    selection, form.isChecked());
            return new SelectionResponse(count);
        }
    }

    public static class SetCheckForm extends SelectForm
    {
        protected String[] ids;
        protected boolean checked;

        public String[] getId(HttpServletRequest request)
        {
            // 5025 : DataRegion checkbox names may contain comma
            // Beehive parses a single parameter value with commas into an array
            // which is not what we want.
            return request.getParameterValues("id");
        }

        public void setId(String[] ids)
        {
            this.ids = ids;
        }

        public boolean isChecked()
        {
            return checked;
        }

        public void setChecked(boolean checked)
        {
            this.checked = checked;
        }
    }

    public static class SelectionResponse extends ApiSimpleResponse
    {
        public SelectionResponse(int count)
        {
            super("count", count);
        }
    }

    public static String getMessage(SqlDialect d, SQLException x)
    {
        return x.getMessage();
    }

    @RequiresPermissionClass(ReadPermission.class)
    @ApiVersion(9.3)
    public class    GetSchemasAction extends ApiAction
    {
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            if (getRequestedApiVersion() >= 9.3)
            {
                ApiSimpleResponse resp = new ApiSimpleResponse();

                Container container = getViewContext().getContainer();
                User user = getViewContext().getUser();
                DefaultSchema defSchema = DefaultSchema.get(user, container);

                for (String name : defSchema.getUserSchemaNames())
                {
                    QuerySchema schema = DefaultSchema.get(user, container).getSchema(name);
                    if (null == schema)
                        continue;

                    Map<String,Object> schemaProps = new HashMap<String,Object>();
                    schemaProps.put("description", schema.getDescription());
                    
                    resp.put(schema.getName(), schemaProps);
                }

                return resp;

            }
            else
                return new ApiSimpleResponse("schemas", DefaultSchema.get(getViewContext().getUser(),
                        getViewContext().getContainer()).getUserSchemaNames());
        }
    }

    public static class GetQueriesForm
    {
        private String _schemaName;
        private boolean _includeUserQueries = true;
        private boolean _includeColumns = true;

        public String getSchemaName()
        {
            return _schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            _schemaName = schemaName;
        }

        public boolean isIncludeUserQueries()
        {
            return _includeUserQueries;
        }

        public void setIncludeUserQueries(boolean includeUserQueries)
        {
            _includeUserQueries = includeUserQueries;
        }

        public boolean isIncludeColumns()
        {
            return _includeColumns;
        }

        public void setIncludeColumns(boolean includeColumns)
        {
            _includeColumns = includeColumns;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class GetQueriesAction extends ApiAction<GetQueriesForm>
    {
        public ApiResponse execute(GetQueriesForm form, BindException errors) throws Exception
        {
            if(null == StringUtils.trimToNull(form.getSchemaName()))
                throw new IllegalArgumentException("You must supply a value for the 'schemaName' parameter!");

            ApiSimpleResponse response = new ApiSimpleResponse();
            QuerySchema qschema = DefaultSchema.get(getViewContext().getUser(), getViewContext().getContainer()).getSchema(form.getSchemaName());
            if(null == qschema)
                throw new NotFoundException("The schema name '" + form.getSchemaName()
                        + "' was not found within the folder '" + getViewContext().getContainer().getPath());

            if(!(qschema instanceof UserSchema))
                throw new NotFoundException("The schema name '" + form.getSchemaName() + "'  cannot be accessed by these APIs!");

            response.put("schemaName", form.getSchemaName());
            UserSchema uschema = (UserSchema) qschema;

            List<Map<String,Object>> qinfos = new ArrayList<Map<String,Object>>();

            //user-defined queries
            if (form.isIncludeUserQueries())
            {
                Map<String,QueryDefinition> queryDefMap = QueryService.get().getQueryDefs(getContainer(), uschema.getSchemaName());
                for (Map.Entry<String,QueryDefinition> entry : queryDefMap.entrySet())
                {
                    QueryDefinition qdef = entry.getValue();
                    if (!qdef.isHidden())
                        qinfos.add(getQueryProps(qdef.getName(), qdef.getDescription(), true, uschema, form.isIncludeColumns()));
                }
            }

            //built-in tables
            for(String qname : uschema.getTableNames())
            {
                qinfos.add(getQueryProps(qname, null, false, uschema, form.isIncludeColumns()));
            }

            
            response.put("queries", qinfos);

            return response;
        }

        protected Map<String,Object> getQueryProps(String name, String description, boolean isUserDefined, UserSchema schema, boolean includeColumns)
        {
            Map<String,Object> qinfo = new HashMap<String,Object>();
            qinfo.put("name", name);
            qinfo.put("isUserDefined", isUserDefined);
            if (null != description)
                qinfo.put("description", description);

            //get the table info if the user requested column info
            //or if the description coming in was null (need to get form TableInfo)
            TableInfo table = null;
            if (includeColumns || !isUserDefined)
            {
                try
                {
                    table = schema.getTable(name);
                }
                catch(Exception e)
                {
                    //may happen due to query failing parse
                }
                if (null != table && null == description)
                    qinfo.put("description", table.getDescription());
            }

            if (null != table && includeColumns)
            {
                //enumerate the columns
                List<Map<String,Object>> cinfos = new ArrayList<Map<String,Object>>();
                for(ColumnInfo col : table.getColumns())
                {
                    Map<String,Object> cinfo = new HashMap<String,Object>();
                    cinfo.put("name", col.getName());
                    if(null != col.getLabel())
                        cinfo.put("caption", col.getLabel());
                    if(null != col.getDescription())
                        cinfo.put("description", col.getDescription());

                    cinfos.add(cinfo);
                }
                if(cinfos.size() > 0)
                    qinfo.put("columns", cinfos);
            }
            return qinfo;
        }
    }

    public static class GetQueryViewsForm
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

    @RequiresPermissionClass(ReadPermission.class)
    public class GetQueryViewsAction extends ApiAction<GetQueryViewsForm>
    {
        public ApiResponse execute(GetQueryViewsForm form, BindException errors) throws Exception
        {
            if(null == StringUtils.trimToNull(form.getSchemaName()))
                throw new IllegalArgumentException("You must pass a value for the 'schemaName' parameter!");
            if(null == StringUtils.trimToNull(form.getQueryName()))
                throw new IllegalArgumentException("You must pass a value for the 'queryName' parameter!");

            QuerySchema qschema = DefaultSchema.get(getViewContext().getUser(), getViewContext().getContainer()).getSchema(form.getSchemaName());
            if(null == qschema)
                throw new NotFoundException("The schema name '" + form.getSchemaName()
                        + "' was not found within the folder '" + getViewContext().getContainer().getPath());

            if(!(qschema instanceof UserSchema))
                throw new NotFoundException("The schema name '" + form.getSchemaName() + "'  cannot be accessed by these APIs!");
            
            QueryDefinition querydef = QueryService.get().createQueryDefForTable((UserSchema)qschema, form.getQueryName());
            if(null == querydef)
                throw new NotFoundException("The query '" + form.getQueryName() + "' was not found within the '"
                        + form.getSchemaName() + "' schema in the container '"
                        + getViewContext().getContainer().getPath() + "'!");

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("schemaName", form.getSchemaName());
            response.put("queryName", form.getQueryName());
            
            Map<String,CustomView> views = querydef.getCustomViews(getViewContext().getUser(), getViewContext().getRequest());
            if(null == views)
                views = Collections.emptyMap();

            List<Map<String,Object>> viewInfos = new ArrayList<Map<String,Object>>(views.size());
            for(CustomView view : views.values())
                viewInfos.add(getViewInfo(view));

            response.put("views", viewInfos);

            return response;
        }

        protected Map<String,Object> getViewInfo(CustomView view)
        {
            Map<String,Object> viewInfo = new HashMap<String,Object>();
            viewInfo.put("name", view.getName());
            if (null != view.getOwner())
                viewInfo.put("owner", view.getOwner().getDisplayName(getViewContext()));
            List<Map<String,Object>> colInfos = new ArrayList<Map<String,Object>>();
            for(FieldKey key : view.getColumns())
            {
                Map<String,Object> colInfo = new HashMap<String,Object>();
                colInfo.put("name", key.getName());
                colInfo.put("key", key.toString());
                colInfos.add(colInfo);
            }
            viewInfo.put("columns", colInfos);
            return viewInfo;
        }
    }

    @RequiresNoPermission
    public class GetServerDateAction extends ApiAction
    {
        public ApiResponse execute(Object o, BindException errors) throws Exception
        {
            return new ApiSimpleResponse("date", new Date());
        }
    }

    private static class SaveApiTestForm
    {
        private String _getUrl;
        private String _postUrl;
        private String _postData;
        private String _response;

        public String getGetUrl()
        {
            return _getUrl;
        }

        public void setGetUrl(String getUrl)
        {
            _getUrl = getUrl;
        }

        public String getPostUrl()
        {
            return _postUrl;
        }

        public void setPostUrl(String postUrl)
        {
            _postUrl = postUrl;
        }

        public String getResponse()
        {
            return _response;
        }

        public void setResponse(String response)
        {
            _response = response;
        }

        public String getPostData()
        {
            return _postData;
        }

        public void setPostData(String postData)
        {
            _postData = postData;
        }
    }

    @RequiresPermissionClass(ReadPermission.class)
    public class SaveApiTestAction extends ApiAction<SaveApiTestForm>
    {
        public ApiResponse execute(SaveApiTestForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            ApiTestsDocument doc = ApiTestsDocument.Factory.newInstance();

            TestCaseType test = doc.addNewApiTests().addNewTest();
            test.setName("recorded test case");
            ActionURL url = null;

            if (!StringUtils.isEmpty(form.getGetUrl()))
            {
                test.setType("get");
                url = new ActionURL(form.getGetUrl());
            }
            else if (!StringUtils.isEmpty(form.getPostUrl()))
            {
                test.setType("post");
                test.setFormData(form.getPostData());
                url = new ActionURL(form.getPostUrl());
            }

            if (url != null)
            {
                String uri = url.getLocalURIString();
                if (uri.startsWith(url.getContextPath()))
                    uri = uri.substring(url.getContextPath().length() + 1);

                test.setUrl(uri);
            }
            test.setResponse(form.getResponse());

            XmlOptions opts = new XmlOptions();
            opts.setSaveCDataEntityCountThreshold(0);
            opts.setSaveCDataLengthThreshold(0);
            opts.setSavePrettyPrint();
            opts.setUseDefaultNamespace();

            response.put("xml", doc.xmlText(opts));

            return response;
        }
    }
}
