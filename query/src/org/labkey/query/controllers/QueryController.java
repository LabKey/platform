/*
 * Copyright (c) 2007-2017 LabKey Corporation
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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.antlr.runtime.tree.Tree;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.multimap.ArrayListValuedHashMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.labkey.api.action.*;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.view.AuditChangesView;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.RowMapFactory;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.JdbcMetaDataLocator;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.ListofMapsDataIterator;
import org.labkey.api.exceptions.OptimisticConflictException;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.gwt.server.BaseRemoteService;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.query.*;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.IgnoresTermsOfUse;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AbstractActionPermissionTest;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.EditSharedViewPermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.stats.BaseAggregatesAnalyticsProvider;
import org.labkey.api.stats.ColumnAnalyticsProvider;
import org.labkey.api.study.DatasetTable;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DetailsView;
import org.labkey.api.view.GWTView;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.InsertView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.UpdateView;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.writer.ZipFile;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TablesType;
import org.labkey.data.xml.externalSchema.TemplateSchemaType;
import org.labkey.query.CustomViewImpl;
import org.labkey.query.CustomViewUtil;
import org.labkey.query.EditableCustomView;
import org.labkey.query.LinkedTableInfo;
import org.labkey.query.ModuleCustomView;
import org.labkey.query.QueryServiceImpl;
import org.labkey.query.TableXML;
import org.labkey.query.audit.QueryExportAuditProvider;
import org.labkey.query.audit.QueryUpdateAuditProvider;
import org.labkey.query.design.DgMessage;
import org.labkey.query.design.ErrorsDocument;
import org.labkey.query.metadata.MetadataServiceImpl;
import org.labkey.query.metadata.client.MetadataEditor;
import org.labkey.query.persist.AbstractExternalSchemaDef;
import org.labkey.query.persist.CstmView;
import org.labkey.query.persist.ExternalSchemaDef;
import org.labkey.query.persist.LinkedSchemaDef;
import org.labkey.query.persist.QueryDef;
import org.labkey.query.persist.QueryManager;
import org.labkey.query.reports.ReportsController;
import org.labkey.query.reports.getdata.DataRequest;
import org.labkey.query.sql.QNode;
import org.labkey.query.sql.Query;
import org.labkey.query.sql.SqlParser;
import org.labkey.query.xml.ApiTestsDocument;
import org.labkey.query.xml.TestCaseType;
import org.labkey.remoteapi.RemoteConnections;
import org.labkey.remoteapi.SelectRowsStreamHack;
import org.labkey.remoteapi.query.SelectRowsCommand;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.zip.GZIPOutputStream;

import static org.labkey.api.data.DbScope.NO_OP_TRANSACTION;

public class QueryController extends SpringActionController
{
    private static final Logger LOG = Logger.getLogger(QueryController.class);

    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(QueryController.class,
            ValidateQueryAction.class,
            ValidateQueriesAction.class,
            GetSchemaQueryTreeAction.class,
            GetQueryDetailsAction.class,
            ViewQuerySourceAction.class);

    public QueryController()
    {
        setActionResolver(_actionResolver);
    }

    public static void registerAdminConsoleLinks()
    {
        AdminConsole.addLink(AdminConsole.SettingsLinkType.Diagnostics, "data sources", new ActionURL(DataSourceAdminAction.class, ContainerManager.getRoot()));
    }

    public static class RemoteQueryConnectionUrls
    {
        public static ActionURL urlManageRemoteConnection(Container c)
        {
            return new ActionURL(ManageRemoteConnectionsAction.class, c);
        }

        public static ActionURL urlCreateRemoteConnection(Container c)
        {
            return new ActionURL(EditRemoteConnectionAction.class, c);
        }

        public static ActionURL urlEditRemoteConnection(Container c, String connectionName)
        {
            ActionURL url = new ActionURL(QueryController.EditRemoteConnectionAction.class, c);
            url.addParameter("connectionName", connectionName);
            return url;
        }

        public static ActionURL urlSaveRemoteConnection(Container c)
        {
            return new ActionURL(EditRemoteConnectionAction.class, c);
        }

        public static ActionURL urlDeleteRemoteConnection(Container c, @Nullable String connectionName)
        {
            ActionURL url = new ActionURL(QueryController.DeleteRemoteConnectionAction.class, c);
            if (connectionName != null)
                url.addParameter("connectionName", connectionName);
            return url;
        }

        public static ActionURL urlTestRemoteConnection(Container c, String connectionName)
        {
            ActionURL url = new ActionURL(QueryController.TestRemoteConnectionAction.class, c);
            url.addParameter("connectionName", connectionName);
            return url;
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class EditRemoteConnectionAction extends FormViewAction<RemoteConnections.RemoteConnectionForm>
    {
        @Override
        public void validateCommand(RemoteConnections.RemoteConnectionForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(RemoteConnections.RemoteConnectionForm remoteConnectionForm, boolean reshow, BindException errors) throws Exception
        {
            remoteConnectionForm.setConnectionKind(RemoteConnections.CONNECTION_KIND_QUERY);
            if (!errors.hasErrors())
            {
                String name = remoteConnectionForm.getConnectionName();
                // package the remote-connection properties into the remoteConnectionForm and pass them along
                Map<String, String> map1 = RemoteConnections.getRemoteConnection(RemoteConnections.REMOTE_QUERY_CONNECTIONS_CATEGORY, name, getContainer());
                remoteConnectionForm.setUrl(map1.get("URL"));
                remoteConnectionForm.setUser(map1.get("user"));
                remoteConnectionForm.setPassword(map1.get("password"));
                remoteConnectionForm.setContainer(map1.get("container"));
            }
            getPageConfig().setHelpTopic(new HelpTopic("remoteConnection"));
            return new JspView<>("/org/labkey/query/view/createRemoteConnection.jsp", remoteConnectionForm, errors);
        }

        @Override
        public boolean handlePost(RemoteConnections.RemoteConnectionForm remoteConnectionForm, BindException errors) throws Exception
        {
            return RemoteConnections.createOrEditRemoteConnection(remoteConnectionForm, getContainer(), errors);
        }

        @Override
        public URLHelper getSuccessURL(RemoteConnections.RemoteConnectionForm remoteConnectionForm)
        {
            return RemoteQueryConnectionUrls.urlManageRemoteConnection(getContainer());
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            new BeginAction(getViewContext()).appendNavTrail(root);
            root.addChild("Create/Edit Remote Connection", new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer()));
            return root;
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class DeleteRemoteConnectionAction extends FormViewAction<RemoteConnections.RemoteConnectionForm>
    {
        @Override
        public void validateCommand(RemoteConnections.RemoteConnectionForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(RemoteConnections.RemoteConnectionForm remoteConnectionForm, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/query/view/confirmDeleteConnection.jsp", remoteConnectionForm, errors);
        }

        @Override
        public boolean handlePost(RemoteConnections.RemoteConnectionForm remoteConnectionForm, BindException errors) throws Exception
        {
            remoteConnectionForm.setConnectionKind(RemoteConnections.CONNECTION_KIND_QUERY);
            return RemoteConnections.deleteRemoteConnection(remoteConnectionForm, getContainer());
        }

        @Override
        public URLHelper getSuccessURL(RemoteConnections.RemoteConnectionForm remoteConnectionForm)
        {
            return RemoteQueryConnectionUrls.urlManageRemoteConnection(getContainer());
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            new BeginAction(getViewContext()).appendNavTrail(root);
            root.addChild("Confirm Delete Connection", new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer()));
            return root;
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class TestRemoteConnectionAction extends FormViewAction<RemoteConnections.RemoteConnectionForm>
    {
        @Override
        public void validateCommand(RemoteConnections.RemoteConnectionForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(RemoteConnections.RemoteConnectionForm remoteConnectionForm, boolean reshow, BindException errors) throws Exception
        {
            String name = remoteConnectionForm.getConnectionName();
            String schemaName = "core"; // test Schema Name
            String queryName = "Users"; // test Query Name

            // Extract the username, password, and container from the secure property store
            Map<String, String> singleConnectionMap = RemoteConnections.getRemoteConnection(RemoteConnections.REMOTE_QUERY_CONNECTIONS_CATEGORY, name, getContainer());
            String url = singleConnectionMap.get(RemoteConnections.FIELD_URL);
            String user = singleConnectionMap.get(RemoteConnections.FIELD_USER);
            String password = singleConnectionMap.get(RemoteConnections.FIELD_PASSWORD);
            String container = singleConnectionMap.get(RemoteConnections.FIELD_CONTAINER);

            // connect to the remote server and retrieve an input stream
            org.labkey.remoteapi.Connection cn = new org.labkey.remoteapi.Connection(url, user, password);
            final SelectRowsCommand cmd = new SelectRowsCommand(schemaName, queryName);
            try
            {
                DataIteratorBuilder source = SelectRowsStreamHack.go(cn, container, cmd);
                // immediately close the source after opening it, this is a test.
                source.getDataIterator(new DataIteratorContext()).close();
            }
            catch (Exception e)
            {
                errors.addError(new LabKeyError("The listed credentials for this remote connection failed to connect."));
                return new JspView<>("/org/labkey/query/view/testRemoteConnectionsFailure.jsp", remoteConnectionForm);
            }

            return new JspView<>("/org/labkey/query/view/testRemoteConnectionsSuccess.jsp", remoteConnectionForm);
        }

        @Override
        public boolean handlePost(RemoteConnections.RemoteConnectionForm remoteConnectionForm, BindException errors) throws Exception
        {
            return true;
        }

        @Override
        public URLHelper getSuccessURL(RemoteConnections.RemoteConnectionForm remoteConnectionForm)
        {
            return null;
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            new BeginAction(getViewContext()).appendNavTrail(root);
            root.addChild("Manage Remote Connections", new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer()));
            return root;
        }
    }

    public static class QueryUrlsImpl implements QueryUrls
    {
        @Override
        public ActionURL urlSchemaBrowser(Container c)
        {
            return new ActionURL(BeginAction.class, c);
        }

        @Override
        public ActionURL urlSchemaBrowser(Container c, @Nullable String schemaName)
        {
            ActionURL ret = urlSchemaBrowser(c);
            if (schemaName != null)
            {
                ret.addParameter(QueryParam.schemaName.toString(), schemaName);
            }
            return ret;
        }

        @Override
        public ActionURL urlSchemaBrowser(Container c, @Nullable String schemaName, @Nullable String queryName)
        {
            if (StringUtils.isEmpty(queryName))
                return urlSchemaBrowser(c, schemaName);
            ActionURL ret = urlSchemaBrowser(c);
            if (schemaName != null)
            {
                ret.addParameter(QueryParam.schemaName.toString(), schemaName);
            }
            if (queryName != null)
            {
                ret.addParameter(QueryParam.queryName.toString(), queryName);
            }
            return ret;
        }

        public ActionURL urlExternalSchemaAdmin(Container c)
        {
            return urlExternalSchemaAdmin(c, null);
        }

        public ActionURL urlExternalSchemaAdmin(Container c, @Nullable String reloadedSchema)
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

        public ActionURL urlUpdateExternalSchema(Container c, AbstractExternalSchemaDef def)
        {
            ActionURL url = new ActionURL(QueryController.EditExternalSchemaAction.class, c);
            url.addParameter("externalSchemaId", Integer.toString(def.getExternalSchemaId()));
            return url;
        }

        public ActionURL urlDeleteExternalSchema(Container c, AbstractExternalSchemaDef def)
        {
            ActionURL url = new ActionURL(QueryController.DeleteExternalSchemaAction.class, c);
            url.addParameter("externalSchemaId", Integer.toString(def.getExternalSchemaId()));
            return url;
        }

        @Override
        public ActionURL urlStartBackgroundRReport(@NotNull ActionURL baseURL, String reportId)
        {
            ActionURL result = baseURL.clone();
            result.setAction(ReportsController.StartBackgroundRReportAction.class);
            result.replaceParameter(ReportDescriptor.Prop.reportId, reportId);
            return result;
        }

        @Override
        public ActionURL urlExecuteQuery(@NotNull ActionURL baseURL)
        {
            ActionURL result = baseURL.clone();
            result.setAction(ExecuteQueryAction.class);
            return result;
        }

        @Override
        public ActionURL urlExecuteQuery(Container c, String schemaName, String queryName)
        {
            return new ActionURL(ExecuteQueryAction.class, c)
                .addParameter(QueryParam.schemaName, schemaName)
                .addParameter(QueryParam.queryName, queryName);
        }

        @Override
        public ActionURL urlCreateExcelTemplate(Container c, String schemaName, String queryName)
        {
            return new ActionURL(ExportExcelTemplateAction.class, c)
                    .addParameter(QueryParam.schemaName, schemaName)
                    .addParameter("query.queryName", queryName);
        }
    }

    protected boolean queryExists(QueryForm form)
    {
        try
        {
            UserSchema schema = form.getSchema();
            return schema != null && schema.getTable(form.getQueryName()) != null;
        }
        catch (QueryException x)
        {
            // exists with errors
            return true;
        }
    }

    @Override
    public PageConfig defaultPageConfig()
    {
        // set default help topic for query controller
        PageConfig config = super.defaultPageConfig();
        config.setHelpTopic(new HelpTopic("querySchemaBrowser"));
        return config;
    }

    /**
     * ensureQueryParams throws NotFound if the query/table parameters aren't present - nothing more.
     */
    protected void ensureQueryParams(QueryForm form)
    {
        if (form.getSchema() == null)
        {
            throw new NotFoundException("Could not find schema: " + form.getSchemaName());
        }

        if (StringUtils.isEmpty(form.getQueryName()))
        {
            throw new NotFoundException("Query not specified");
        }
    }

    /**
     * ensureQueryExists throws NotFound if the query/table does not exist.
     * Does not guarantee that the query is syntactically correct, or can execute.
     * This check can be expensive.
     */
    protected void ensureQueryExists(QueryForm form)
    {
        ensureQueryParams(form);

        if (!queryExists(form))
        {
            throw new NotFoundException("Query '" + form.getQueryName() + "' in schema '" + form.getSchemaName() + "' doesn't exist.");
        }

        // If the above checks succeed then QueryDef should be non-null. TODO: Remove null != getQueryDef() checks from callers of ensureQueryExists()
        assert null != form.getQueryDef();
    }


    @AdminConsoleAction(AdminOperationsPermission.class)
    public class DataSourceAdminAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            List<ExternalSchemaDef> allDefs = QueryManager.get().getExternalSchemaDefs(null);

            MultiValuedMap<String, ExternalSchemaDef> byDataSourceName = new ArrayListValuedHashMap<>();

            for (ExternalSchemaDef def : allDefs)
                byDataSourceName.put(def.getDataSource(), def);

            StringBuilder sb = new StringBuilder();

            sb.append("\n<div>This page lists all the data sources defined in your ").append(AppProps.getInstance().getWebappConfigurationFilename()).append(" file that were available at server startup and the external schemas defined in each.</div><br/>\n");
            sb.append("\n<table class=\"labkey-data-region\">\n");
            sb.append("<tr class=\"labkey-show-borders\">");
            sb.append("  <td class=\"labkey-column-header\">Data Source</td>");
            sb.append("  <td class=\"labkey-column-header\">Current Status</td>");
            sb.append("  <td class=\"labkey-column-header\">URL</td>");
            sb.append("  <td class=\"labkey-column-header\">Product Name</td>");
            sb.append("  <td class=\"labkey-column-header\">Product Version</td></tr>\n");

            int rowCount = 0;
            for (DbScope scope : DbScope.getDbScopes())
            {
                if (rowCount % 2 == 0)
                    sb.append("<tr class=\"labkey-alternate-row labkey-show-borders\">");
                else
                    sb.append("<tr class=\"labkey-row labkey-show-borders\">");

                sb.append("<td>");
                sb.append(scope.getDisplayName());
                sb.append("</td><td>");

                String status;
                Connection conn = null;

                try
                {
                    conn = scope.getConnection();
                    status = "connected";
                }
                catch (SQLException e)
                {
                    status = "<font class=\"labkey-error\">disconnected</font>";
                }
                finally
                {
                    if (null != conn)
                        scope.releaseConnection(conn);
                }

                sb.append(status);
                sb.append("</td><td>");
                sb.append(scope.getURL());
                sb.append("</td><td>");
                sb.append(scope.getDatabaseProductName());
                sb.append("</td><td>");
                sb.append(scope.getDatabaseProductVersion());
                sb.append("</td></tr>\n");

                Collection<ExternalSchemaDef> dsDefs = byDataSourceName.get(scope.getDataSourceName());

                sb.append("<tr class=\"").append(rowCount % 2 == 0 ? "labkey-alternate-row" : "labkey-row").append("\">\n");
                sb.append("  <td colspan=5>\n");
                sb.append("    <table>\n");

                if (null != dsDefs)
                {
                    MultiValuedMap<String, ExternalSchemaDef> byContainerPath = new ArrayListValuedHashMap<>();

                    for (ExternalSchemaDef def : dsDefs)
                        byContainerPath.put(def.getContainerPath(), def);

                    TreeSet<String> paths = new TreeSet<>(byContainerPath.keySet());
                    QueryUrlsImpl urls = new QueryUrlsImpl();

                    for (String path : paths)
                    {
                        sb.append("      <tr><td colspan=4>&nbsp;</td><td>\n");
                        Container c = ContainerManager.getForPath(path);

                        if (null != c)
                        {
                            Collection<ExternalSchemaDef> cDefs = byContainerPath.get(path);

                            sb.append("        <table>\n        <tr><td colspan=3>");

                            ActionURL schemaAdminURL = urls.urlExternalSchemaAdmin(c);

                            sb.append("<a href=\"").append(PageFlowUtil.filter(schemaAdminURL)).append("\">").append(PageFlowUtil.filter(path)).append("</a>");
                            sb.append("</td></tr>\n");
                            sb.append("          <tr><td>&nbsp;</td><td>\n");
                            sb.append("            <table>\n");

                            for (ExternalSchemaDef def : cDefs)
                            {
                                ActionURL url = urls.urlUpdateExternalSchema(c, def);

                                sb.append("              <tr><td>");
                                sb.append("<a href=\"").append(PageFlowUtil.filter(url)).append("\">").append(PageFlowUtil.filter(def.getUserSchemaName()));

                                if (!def.getSourceSchemaName().equals(def.getUserSchemaName()))
                                {
                                    sb.append(" (");
                                    sb.append(PageFlowUtil.filter(def.getSourceSchemaName()));
                                    sb.append(")");
                                }

                                sb.append("</a></td></tr>\n");
                            }

                            sb.append("            </table>\n");
                            sb.append("          </td></tr>\n");
                            sb.append("        </table>\n");
                        }

                        sb.append("      </td></tr>\n");
                    }
                }
                else
                {
                    sb.append("      <tr><td>&nbsp;</td></tr>\n");
                }

                sb.append("    </table>\n");
                sb.append("  </td></tr>\n");

                rowCount++;
            }

            sb.append("</table>\n");

            return new HtmlView(sb.toString());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            PageFlowUtil.urlProvider(AdminUrls.class).appendAdminNavTrail(root, "Data Source Administration ", null);
            return root;
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class BrowseAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/query/view/browse.jsp", null);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Schema Browser");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class BeginAction extends QueryViewAction
    {
        @SuppressWarnings("UnusedDeclaration")
        public BeginAction()
        {
        }

        public BeginAction(ViewContext ctx)
        {
            setViewContext(ctx);
        }

        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            JspView view = new JspView<>("/org/labkey/query/view/browse.jsp", form);
            view.setFrame(WebPartView.FrameType.NONE);
            return view;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Query Schema Browser", new QueryUrlsImpl().urlSchemaBrowser(getContainer()));
            return root;
        }
    }

    @RequiresPermission(ReadPermission.class)
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
            return new JspView<>("/org/labkey/query/view/browse.jsp", form);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            if (_form.getSchema() != null)
                return _appendSchemaActionNavTrail(root, _form.getSchema().getSchemaPath(), _form.getQueryName());
            else
                return root;
        }
    }


    NavTree _appendSchemaActionNavTrail(NavTree root, SchemaKey schemaKey, String queryName)
    {
        if (getContainer().hasPermission(getUser(), AdminPermission.class) || getUser().isDeveloper())
        {
            // Don't show the full query nav trail to non-admin/non-developer users as they almost certainly don't
            // want it
            try
            {
                String schemaName = schemaKey.toDisplayString();
                ActionURL url = new ActionURL(BeginAction.class, getContainer());
                url.addParameter("schemaName", schemaKey.toString());
                url.addParameter("queryName", queryName);
                (new BeginAction(getViewContext())).appendNavTrail(root)
                        .addChild(schemaName + " Schema", url);
            }
            catch (NullPointerException e)
            {
                LOG.error("NullPointerException in appendNavTrail", e);
                return root;
            }
        }
        return root;
    }


    @RequiresPermission(AdminPermission.class)
    @Action(ActionType.SelectData.class)
    public class NewQueryAction extends FormViewAction<NewQueryForm>
    {
        private NewQueryForm _form;
        private ActionURL _successUrl;

        public void validateCommand(NewQueryForm target, org.springframework.validation.Errors errors)
        {
            target.ff_newQueryName = StringUtils.trimToNull(target.ff_newQueryName);
            if (null == target.ff_newQueryName)
                errors.reject(ERROR_MSG, "QueryName is required");
        }

        public ModelAndView getView(NewQueryForm form, boolean reshow, BindException errors) throws Exception
        {
            if (form.getSchema() == null)
            {
                if (form.getSchemaName().isEmpty())
                {
                    throw new NotFoundException("Schema name not specified");
                }
                else
                {
                    throw new NotFoundException("Could not find schema: " + form.getSchemaName());
                }
            }
            if (!form.getSchema().canCreate())
            {
                throw new UnauthorizedException();
            }
            getPageConfig().setFocusId("ff_newQueryName");
            _form = form;
            setHelpTopic(new HelpTopic("sqlTutorial"));
            return new JspView<>("/org/labkey/query/view/newQuery.jsp", form, errors);
        }

        public boolean handlePost(NewQueryForm form, BindException errors) throws Exception
        {
            if (form.getSchema() == null)
            {
                throw new NotFoundException("Could not find schema: " + form.getSchemaName());
            }
            if (!form.getSchema().canCreate())
            {
                throw new UnauthorizedException();
            }
            try
            {
                if (form.ff_baseTableName == null || "".equals(form.ff_baseTableName))
                {
                    errors.reject(ERROR_MSG, "You must select a base table or query name.");
                    return false;
                }

                UserSchema schema = form.getSchema();
                String newQueryName = form.ff_newQueryName;
                QueryDef existing = QueryManager.get().getQueryDef(getContainer(), form.getSchemaName(), newQueryName, true);
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
                QueryDefinition newDef = QueryService.get().createQueryDef(getUser(), getContainer(), form.getSchemaName(), form.ff_newQueryName);
                Query query = new Query(schema);
                query.setRootTable(FieldKey.fromParts(form.ff_baseTableName));
                String sql = query.getQueryText();
                if (null == sql)
                    sql = "SELECT * FROM \"" + form.ff_baseTableName + "\"";
                newDef.setSql(sql);

                try
                {
                    newDef.save(getUser(), getContainer());
                }
                catch (SQLException x)
                {
                    if (RuntimeSQLException.isConstraintException(x))
                    {
                        errors.reject(ERROR_MSG, "The query '" + newQueryName + "' already exists.");
                        return false;
                    }
                    else
                    {
                        throw x;
                    }
                }

                _successUrl = newDef.urlFor(form.ff_redirect);
                return true;
            }
            catch (Exception e)
            {
                ExceptionUtil.logExceptionToMothership(getViewContext().getRequest(), e);
                errors.reject(ERROR_MSG, StringUtils.defaultString(e.getMessage(),e.toString()));
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


    // CONSIDER : deleting this action after the SQL editor UI changes are finalized, keep in mind that built-in views
    // use this view as well via the edit metadata page.
    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectMetaData.class)  // Note: This action deals with just meta data; it AJAXes data into place using GetWebPartAction
    public class SourceQueryAction extends SimpleViewAction<SourceForm>
    {
        public SourceForm _form;
        public QuerySchema _baseSchema;
        public QueryDefinition _queryDef;


        @Override
        public void validate(SourceForm target, BindException errors)
        {
            _form = target;
            if (StringUtils.isEmpty(target.getSchemaName()))
                throw new NotFoundException("schema name not specified");
            if (StringUtils.isEmpty(target.getQueryName()))
                throw new NotFoundException("query name not specified");

            _baseSchema = DefaultSchema.get(getUser(), getContainer(), _form.getSchemaKey());
            if (null == _baseSchema)
                throw new NotFoundException("schema not found: " + _form.getSchemaKey().toDisplayString());
        }


        @Override
        public ModelAndView getView(SourceForm form, BindException errors) throws Exception
        {
            _queryDef = QueryService.get().getQueryDef(getUser(), getContainer(), _baseSchema.getSchemaName(), form.getQueryName());
            if (null == _queryDef)
                _queryDef = ((UserSchema)_baseSchema).getQueryDefForTable(form.getQueryName());
            if (null == _queryDef)
                throw new NotFoundException("Query not found: " + form.getQueryName());

            try
            {
                if (form.ff_queryText == null)
                {
                    form.ff_queryText = _queryDef.getSql();
                    form.ff_metadataText = _queryDef.getMetadataXml();
                }

                for (QueryException qpe : _queryDef.getParseErrors(_baseSchema))
                {
                    errors.reject(ERROR_MSG, StringUtils.defaultString(qpe.getMessage(),qpe.toString()));
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

            return new JspView<>("/org/labkey/query/view/sourceQuery.jsp", this, errors);
        }


        public NavTree appendNavTrail(NavTree root)
        {
            setHelpTopic(new HelpTopic("useSqlEditor"));

            _appendSchemaActionNavTrail(root, _form.getSchemaKey(), _form.getQueryName());

            root.addChild("Edit " + _form.getQueryName());
            return root;
        }
    }


    /**
     * Ajax action to save a query. If the save is successful the request will return successfully. A query
     * with SQL syntax errors can still be saved successfully.
     *
     * If the SQL contains parse errors, a parseErrors object will be returned which contains an array of
     * JSON serialized error information.
     */
    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.Configure.class)
    public class SaveSourceQueryAction extends MutatingApiAction<SourceForm>
    {
        SourceForm _form;
        QuerySchema _baseSchema;
        QueryDefinition _queryDef;


        @Override
        public void validateForm(SourceForm target, Errors errors)
        {
            _form = target;
            if (StringUtils.isEmpty(target.getSchemaName()))
                throw new NotFoundException("Query definition not found, schemaName and queryName are required.");
            if (StringUtils.isEmpty(target.getQueryName()))
                throw new NotFoundException("Query definition not found, schemaName and queryName are required.");

            _baseSchema = DefaultSchema.get(getUser(), getContainer(), _form.getSchemaKey());
            if (null == _baseSchema)
                throw new NotFoundException("Schema not found: " + _form.getSchemaKey().toDisplayString());

            XmlOptions options = XmlBeansUtil.getDefaultParseOptions();
            List<XmlError> xmlErrors = new ArrayList<>();
            options.setErrorListener(xmlErrors);
            try
            {
                // had a couple of real-world failures due to null pointers in this code, so it's time to be paranoid
                if(target.ff_metadataText != null)
                {
                    TablesDocument tablesDoc = TablesDocument.Factory.parse(target.ff_metadataText, options);
                    if (tablesDoc != null)
                    {
                        TablesType tablesType = tablesDoc.getTables();
                        if(tablesType != null)
                        {
                            TableType[] tableArray = tablesType.getTableArray();
                            if (tableArray.length > 0)
                            {
                                TableType firstTable = tableArray[0];
                                if(firstTable != null)
                                {
                                    TableType.Columns tableColumns = firstTable.getColumns();
                                    if(tableColumns != null)
                                    {
                                        ColumnType[] tableColumnArray = tableColumns.getColumnArray();
                                        for (ColumnType column : tableColumnArray)
                                        {
                                            if (column.isSetPhi() || column.isSetProtected())
                                            {
                                                throw new IllegalArgumentException("PHI/protected metadata must not be set here.");
                                            }
                                        }
                                    }
                                }
                            }
                        }

                    }
                }
            }
            catch (XmlException e)
            {
                throw new RuntimeException(e);
            }
        }


        @Override
        public ApiResponse execute(SourceForm form, BindException errors) throws Exception
        {
            _queryDef = QueryService.get().getQueryDef(getUser(), getContainer(), _baseSchema.getSchemaName(), form.getQueryName());
            if (null == _queryDef)
                _queryDef = ((UserSchema)_baseSchema).getQueryDefForTable(form.getQueryName());
            if (null == _queryDef)
                throw new NotFoundException("Query not found: " + form.getQueryName());

            if (!_queryDef.canEdit(getUser()))
                throw new UnauthorizedException("Edit permissions are required.");

            ApiSimpleResponse response = new ApiSimpleResponse();

            try
            {
                _queryDef.setSql(form.ff_queryText);

                if (_queryDef.isTableQueryDefinition() && StringUtils.trimToNull(form.ff_metadataText) == null)
                {
                    if (QueryManager.get().getQueryDef(getContainer(), form.getSchemaName(), form.getQueryName(), false) != null)
                    {
                        // delete the query in order to reset the metadata over a built-in query
                        _queryDef.delete(getUser());
                    }
                }
                else
                {
                    _queryDef.setMetadataXml(form.ff_metadataText);
                    _queryDef.save(getUser(), getContainer());

                    // the query was successfully saved, validate the query but return any errors in the success response
                    List<QueryParseException> parseErrors = new ArrayList<>();
                    List<QueryParseException> parseWarnings = new ArrayList<>();
                    _queryDef.validateQuery(_baseSchema, parseErrors, parseWarnings);
                    if (!parseErrors.isEmpty())
                    {
                        JSONArray errorArray = new JSONArray();

                        for (QueryException e : parseErrors)
                        {
                            errorArray.put(e.toJSON(form.ff_queryText));
                        }
                        response.put("parseErrors", errorArray);
                    }
                    else if (!parseWarnings.isEmpty())
                    {
                        JSONArray errorArray = new JSONArray();

                        for (QueryException e : parseWarnings)
                        {
                            errorArray.put(e.toJSON(form.ff_queryText));
                        }
                        response.put("parseWarnings", errorArray);
                    }
                }
            }
            catch (SQLException e)
            {
                errors.reject(ERROR_MSG, "An exception occurred: " + e);
                LOG.error("Error", e);
            }
            catch (RuntimeException e)
            {
                errors.reject(ERROR_MSG, "An exception occurred: " + e.getMessage());
                LOG.error("Error", e);
            }

            if (errors.hasErrors())
                return null;

            //if we got here, the query is OK
            response.put("success", true);
            return response;
        }
    }


    @RequiresPermission(DeletePermission.class)
    @Action(ActionType.Configure.class)
    public class DeleteQueryAction extends ConfirmAction<SourceForm>
    {
        public SourceForm _form;
        public QuerySchema _baseSchema;
        public QueryDefinition _queryDef;


        @Override
        public void validateCommand(SourceForm target, Errors errors)
        {
            _form = target;
            if (StringUtils.isEmpty(target.getSchemaName()))
                throw new NotFoundException("Query definition not found, schemaName and queryName are required.");
            if (StringUtils.isEmpty(target.getQueryName()))
                throw new NotFoundException("Query definition not found, schemaName and queryName are required.");

            _baseSchema = DefaultSchema.get(getUser(), getContainer(), _form.getSchemaKey());
            if (null == _baseSchema)
                throw new NotFoundException("Schema not found: " + _form.getSchemaKey().toDisplayString());
        }


        @Override
        public ModelAndView getConfirmView(SourceForm form, BindException errors) throws Exception
        {
            _queryDef = QueryService.get().getQueryDef(getUser(), getContainer(), _baseSchema.getSchemaName(), form.getQueryName());

            if (null == _queryDef)
                throw new NotFoundException("Query not found: " + form.getQueryName());

            return new JspView<>("/org/labkey/query/view/deleteQuery.jsp", this, errors);
        }


        @Override
        public boolean handlePost(SourceForm form, BindException errors) throws Exception
        {
            _queryDef = QueryService.get().getQueryDef(getUser(), getContainer(), _baseSchema.getSchemaName(), form.getQueryName());

            if (null == _queryDef)
                return false;
            try
            {
                _queryDef.delete(getUser());
            }
            catch (OptimisticConflictException x)
            {
            }
            return true;
        }


        public ActionURL getSuccessURL(SourceForm queryForm)
        {
            return ((UserSchema)_baseSchema).urlFor(QueryAction.schema);
        }
    }


    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectData.class)
    public class ExecuteQueryAction extends QueryViewAction
    {
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            // ensureQueryExists() is ridiculously expensive, let's handle the errors lazily in this case
            // TODO investigate removing other calls to ensureQueryExists()
            ensureQueryParams(form);
            _form = form;

            QueryView queryView = QueryView.create(form, errors);
            if (isPrint())
            {
                queryView.setPrintView(true);
                getPageConfig().setTemplate(PageConfig.Template.Print);
                getPageConfig().setShowPrintDialog(true);
            }
            queryView.setShadeAlternatingRows(true);
            queryView.setShowBorders(true);
            setHelpTopic(new HelpTopic("customSQL"));
            _queryView = queryView;
            return queryView;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            (new SchemaAction(_form)).appendNavTrail(root);
            TableInfo ti = null;
            try
            {
                if (null != _queryView)
                ti = _queryView.getTable();
            }
            catch (QueryParseException x)
            {
                /* */
            }
            String display = ti == null ? _form.getQueryName() : ti.getTitle();
            root.addChild(display);
            return root;
        }
    }


    @RequiresPermission(AdminOperationsPermission.class)
    public class RawTableMetaDataAction extends QueryViewAction
    {
        private String _schemaName;
        private String _tableName;

        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            ensureQueryExists(form);

            _form = form;

            QueryView queryView = QueryView.create(form, errors);
            TableInfo ti = queryView.getTable();

            DbSchema schema = ti.getSchema();
            DbScope scope = schema.getScope();
            _schemaName = schema.getName();

            // Try to get the underlying schema table and use the meta data name, #12015
            if (ti instanceof FilteredTable)
                ti = ((FilteredTable) ti).getRealTable();

            if (ti instanceof SchemaTableInfo)
                _tableName = ti.getMetaDataName();
            else if (ti instanceof LinkedTableInfo)
                _tableName = ti.getName();
            else
                _tableName = ti.getSelectName();

            ActionURL url = new ActionURL(RawSchemaMetaDataAction.class, getContainer());
            url.addParameter("schemaName", _schemaName);

            if (ti.getDomain() != null)
            {
                Domain domain = ti.getDomain();
                if (domain.getStorageTableName() != null)
                {
                    // Use the real table and schema names for getting the metadata
                    _tableName = domain.getStorageTableName();
                    _schemaName = domain.getDomainKind().getStorageSchemaName();
                }
            }


            SqlDialect dialect = scope.getSqlDialect();

            VBox result = new VBox();
            HttpView scopeInfo = new ScopeView("Scope and Schema Information", scope, _schemaName, url, _tableName);

            result.addView(scopeInfo);

            try (JdbcMetaDataLocator locator = dialect.getJdbcMetaDataLocator(scope, _schemaName, _tableName))
            {
                JdbcMetaDataSelector columnSelector = new JdbcMetaDataSelector(locator,
                        (dbmd, l) -> dbmd.getColumns(l.getCatalogName(), l.getSchemaName(), l.getTableName(), null));
                result.addView(new ResultSetView(CachedResultSets.create(columnSelector.getResultSet(), true, Table.ALL_ROWS), "Table Meta Data"));

                JdbcMetaDataSelector pkSelector = new JdbcMetaDataSelector(locator,
                        (dbmd, l) -> dbmd.getPrimaryKeys(l.getCatalogName(), l.getSchemaName(), l.getTableName()));
                result.addView(new ResultSetView(CachedResultSets.create(pkSelector.getResultSet(), true, Table.ALL_ROWS), "Primary Key Meta Data"));

                if (dialect.canCheckIndices(ti))
                {
                    JdbcMetaDataSelector indexSelector = new JdbcMetaDataSelector(locator,
                            (dbmd, l) -> dbmd.getIndexInfo(l.getCatalogName(), l.getSchemaName(), l.getTableName(), false, false));
                    result.addView(new ResultSetView(CachedResultSets.create(indexSelector.getResultSet(), true, Table.ALL_ROWS), "Other Index Meta Data"));
                }

                JdbcMetaDataSelector ikSelector = new JdbcMetaDataSelector(locator,
                        (dbmd, l) -> dbmd.getImportedKeys(l.getCatalogName(), l.getSchemaName(), l.getTableName()));
                result.addView(new ResultSetView(CachedResultSets.create(ikSelector.getResultSet(), true, Table.ALL_ROWS), "Imported Keys Meta Data"));

                JdbcMetaDataSelector ekSelector = new JdbcMetaDataSelector(locator,
                        (dbmd, l) -> dbmd.getExportedKeys(l.getCatalogName(), l.getSchemaName(), l.getTableName()));
                result.addView(new ResultSetView(CachedResultSets.create(ekSelector.getResultSet(), true, Table.ALL_ROWS), "Exported Keys Meta Data"));
            }

            return result;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            (new SchemaAction(_form)).appendNavTrail(root);
            root.addChild("JDBC Meta Data For Table \"" + _schemaName + "." + _tableName + "\"");
            return root;
        }
    }


    @RequiresPermission(AdminOperationsPermission.class)
    public class RawSchemaMetaDataAction extends SimpleViewAction
    {
        private String _schemaName;

        public ModelAndView getView(Object form, BindException errors) throws Exception
        {
            _schemaName = getViewContext().getActionURL().getParameter("schemaName");
            DbSchema schema = DefaultSchema.get(getUser(), getContainer()).getSchema(_schemaName).getDbSchema();
            DbScope scope = schema.getScope();
            SqlDialect dialect = scope.getSqlDialect();

            HttpView scopeInfo = new ScopeView("Scope Information", scope);

            ModelAndView tablesView;

            try (JdbcMetaDataLocator locator = dialect.getJdbcMetaDataLocator(scope, _schemaName, null))
            {
                JdbcMetaDataSelector selector = new JdbcMetaDataSelector(locator,
                    (dbmd, locator1) -> dbmd.getTables(locator1.getCatalogName(), locator1.getSchemaName(), locator1.getTableName(), null));

                ActionURL url = new ActionURL(RawTableMetaDataAction.class, getContainer());
                url.addParameter("schemaName", _schemaName);
                String tableLink = url.getEncodedLocalURIString() + "&query.queryName=";
                tablesView = new ResultSetView(CachedResultSets.create(selector.getResultSet(), true, Table.ALL_ROWS), "Tables", 3, tableLink) {
                    @Override
                    protected boolean shouldLink(ResultSet rs) throws SQLException
                    {
                        // Only link to tables and views (not indexes or sequences)
                        String type = rs.getString(4);
                        return "TABLE".equalsIgnoreCase(type) || "VIEW".equalsIgnoreCase(type);
                    }
                };
            }

            return new VBox(scopeInfo, tablesView);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("JDBC Meta Data For Schema \"" + _schemaName + "\"");
            return root;
        }
    }


    public static class ScopeView extends WebPartView
    {
        private final DbScope _scope;
        private final String _schemaName;
        private final String _tableName;
        private final ActionURL _url;

        private ScopeView(String title, DbScope scope)
        {
            this(title, scope, null, null, null);
        }

        private ScopeView(String title, DbScope scope, String schemaName, ActionURL url, String tableName)
        {
            super(title);
            _scope = scope;
            _schemaName = schemaName;
            _tableName = tableName;
            _url = url;
        }

        @Override
        protected void renderView(Object model, PrintWriter out) throws Exception
        {
            StringBuilder sb = new StringBuilder("<table>\n");

            if (null != _schemaName)
                sb.append("<tr><td class=\"labkey-form-label\">Schema</td><td><a href=\"").append(PageFlowUtil.filter(_url)).append("\">").append(PageFlowUtil.filter(_schemaName)).append("</a></td></tr>\n");
            if (null != _tableName)
                sb.append("<tr><td class=\"labkey-form-label\">Table</td><td>").append(PageFlowUtil.filter(_tableName)).append("</td></tr>\n");

            sb.append("<tr><td class=\"labkey-form-label\">Scope</td><td>").append(_scope.getDisplayName()).append("</td></tr>\n");
            sb.append("<tr><td class=\"labkey-form-label\">Dialect</td><td>").append(_scope.getSqlDialect().getClass().getSimpleName()).append("</td></tr>\n");
            sb.append("<tr><td class=\"labkey-form-label\">URL</td><td>").append(_scope.getURL()).append("</td></tr>\n");
            sb.append("</table>\n");

            out.print(sb);
        }
    }


    // for backwards compat same as _executeQuery.view ?_print=1
    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.Export.class)
    public class PrintRowsAction extends ExecuteQueryAction
    {
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            ensureQueryExists(form);
            _print = true;
            String title = form.getQueryName();
            if (StringUtils.isEmpty(title))
                title = form.getSchemaName();
            getPageConfig().setTitle(title, true);
            return super.getView(form, errors);
        }
    }


    abstract class _ExportQuery<K extends ExportQueryForm> extends SimpleViewAction<K>
    {
        public ModelAndView getView(K form, BindException errors) throws Exception
        {
            ensureQueryExists(form);
            QueryView view = QueryView.create(form, errors);
            getPageConfig().setTemplate(PageConfig.Template.None);
            HttpServletResponse response = getViewContext().getResponse();
            response.setHeader("X-Robots-Tag", "noindex");
            try
            {
                _export(form, view);
                return null;
            }
            catch (QueryService.NamedParameterNotProvided | QueryParseException x)
            {
                ExceptionUtil.decorateException(x, ExceptionUtil.ExceptionInfo.SkipMothershipLogging, "true", true);
                throw x;
            }
        }

        abstract void _export(K form, QueryView view) throws Exception;

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


    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectMetaData.class)    // This is called "export" but it doesn't export any data
    public class ExportScriptAction extends SimpleViewAction<ExportScriptForm>
    {
        public ModelAndView getView(ExportScriptForm form, BindException errors) throws Exception
        {
            ensureQueryExists(form);

            return ExportScriptModel.getExportScriptView(QueryView.create(form, errors), form.getScriptType(), getPageConfig(), getViewContext().getResponse());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }


    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.Export.class)
    public class ExportRowsExcelAction extends _ExportQuery
    {
        void _export(ExportQueryForm form, QueryView view) throws Exception
        {
            view.exportToExcel(getViewContext().getResponse(), form.getHeaderType(), ExcelWriter.ExcelDocumentType.xls);
        }
    }

    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.Export.class)
    public class ExportRowsXLSXAction extends _ExportQuery
    {
        void _export(ExportQueryForm form, QueryView view) throws Exception
        {
            view.exportToExcel(getViewContext().getResponse(), form.getHeaderType(), ExcelWriter.ExcelDocumentType.xlsx);
        }
    }

    public static class TemplateForm extends ExportQueryForm
    {
        boolean insertColumnsOnly = true;
        String filenamePrefix;
        FieldKey[] includeColumn;

        public TemplateForm()
        {
            _headerType = ColumnHeaderType.Caption;
        }

        // "captionType" field backwards compatibility
        public void setCaptionType(ColumnHeaderType headerType)
        {
            this._headerType = headerType;
        }

        public ColumnHeaderType getCaptionType()
        {
            return _headerType;
        }

        public List<FieldKey> getIncludeColumns()
        {
            if (includeColumn == null || includeColumn.length == 0)
                return Collections.emptyList();
            return Arrays.asList(includeColumn);
        }

        public FieldKey[] getIncludeColumn()
        {
            return this.includeColumn;
        }

        public void setIncludeColumn(FieldKey[] includeColumn)
        {
            this.includeColumn = includeColumn;
        }

        public String getFilenamePrefix()
        {
            return filenamePrefix;
        }

        public void setFilenamePrefix(String prefix)
        {
            this.filenamePrefix = prefix;
        }
    }


    /**
     * Can be used to generate an excel template for import into a table.  Supported URL params include:
     * <dl>
     *     <dt>filenamePrefix</dt>
     *     <dd>the prefix of the excel file that is generated, defaults to '_data'</dd>
     *
     *     <dt>query.viewName</dt>
     *     <dd>if provided, the resulting excel file will use the fields present in this view.
     *     Non-usereditable columns will be skipped.
     *     Non-existent columns (like a lookup) unless <code>includeMissingColumns</code> is true.
     *     Any required columns missing from this view will be appended to the end of the query.
     *     </dd>
     *
     *     <dt>includeColumn</dt>
     *     <dd>List of column names to include, even if the column doesn't exist or is non-userEditable.
     *     For example, this can be used to add a fake column that is only supported during the import process.
     *     </dd>
     *
     *     <dt>captionType</dt>
     *     <dd>determines which column property is used in the header.  either Label or Name</dd>
     * </dl>
     */
    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.Export.class)
    public class ExportExcelTemplateAction extends _ExportQuery<TemplateForm>
    {
        public ExportExcelTemplateAction()
        {
            setCommandClass(TemplateForm.class);
        }

        void _export(TemplateForm form, QueryView view) throws Exception
        {
            boolean respectView = form.getViewName() != null;
            view.exportToExcelTemplate(getViewContext().getResponse(), form.getHeaderType(), form.insertColumnsOnly, respectView, form.getIncludeColumns(), form.getFilenamePrefix());
        }
    }

    public static class ExportQueryForm extends QueryForm
    {
        protected ColumnHeaderType _headerType = null; // QueryView will provide a default header type if the user doesn't select one

        public ColumnHeaderType getHeaderType()
        {
            return _headerType;
        }

        public void setHeaderType(ColumnHeaderType headerType)
        {
            _headerType = headerType;
        }
    }

    public static class ExportRowsTsvForm extends ExportQueryForm
    {
        private TSVWriter.DELIM _delim = TSVWriter.DELIM.TAB;
        private TSVWriter.QUOTE _quote = TSVWriter.QUOTE.DOUBLE;

        public TSVWriter.DELIM getDelim()
        {
            return _delim;
        }

        public void setDelim(TSVWriter.DELIM delim)
        {
            _delim = delim;
        }

        public TSVWriter.QUOTE getQuote()
        {
            return _quote;
        }

        public void setQuote(TSVWriter.QUOTE quote)
        {
            _quote = quote;
        }
    }

    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.Export.class)
    public class ExportRowsTsvAction extends _ExportQuery<ExportRowsTsvForm>
    {
        public ExportRowsTsvAction()
        {
            setCommandClass(ExportRowsTsvForm.class);
        }

        void _export(ExportRowsTsvForm form, QueryView view) throws Exception
        {
            view.exportToTsv(getViewContext().getResponse(), form.getDelim(), form.getQuote(), form.getHeaderType());
        }
    }


    @RequiresNoPermission
    @IgnoresTermsOfUse
    @Action(ActionType.Export.class)
    public class ExcelWebQueryAction extends ExportRowsTsvAction
    {
        public ModelAndView getView(ExportRowsTsvForm form, BindException errors) throws Exception
        {
            if (!getContainer().hasPermission(getUser(), ReadPermission.class))
            {
                if (!getUser().isGuest())
                {
                    throw new UnauthorizedException();
                }
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

            ensureQueryExists(form);
            QueryView view = QueryView.create(form, errors);
            getPageConfig().setTemplate(PageConfig.Template.None);
            view.exportToExcelWebQuery(getViewContext().getResponse());
            return null;
        }
    }


    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.Export.class)
    public class ExcelWebQueryDefinitionAction extends SimpleViewAction<QueryForm>
    {
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(PageConfig.Template.None);
            ensureQueryExists(form);
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
            String filename =  FileUtil.makeFileNameWithTimestamp(form.getQueryName(), "iqy");
            getViewContext().getResponse().setHeader("Content-disposition", "attachment; filename=\"" + filename +"\"");
            PrintWriter writer = getViewContext().getResponse().getWriter();
            writer.println("WEB");
            writer.println("1");
            writer.println(url.getURIString());

            QueryService.get().addAuditEvent(getUser(), getContainer(), form.getSchemaName(), form.getQueryName(), url, "Exported to Excel Web Query definition", null);
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectMetaData.class)
    public class MetadataServiceAction extends GWTServiceAction
    {
        protected BaseRemoteService createService()
        {
            return new MetadataServiceImpl(getViewContext());
        }
    }

    @RequiresPermission(AdminPermission.class)
    @Action(ActionType.SelectMetaData.class)
    public class MetadataQueryAction extends FormViewAction<QueryForm>
    {
        QueryDefinition _query = null;
        QueryForm _form = null;

        public void validateCommand(QueryForm target, Errors errors)
        {
        }

        public ModelAndView getView(QueryForm form, boolean reshow, BindException errors) throws Exception
        {
            ensureQueryExists(form);
            _form = form;
            _query = _form.getQueryDef();
            Map<String, String> props = new HashMap<>();
            props.put("schemaName", form.getSchemaName());
            props.put("queryName", form.getQueryName());
            props.put(MetadataEditor.EDIT_SOURCE_URL, _form.getQueryDef().urlFor(QueryAction.sourceQuery, getContainer()).toString() + "#metadata");
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

    // Uck. Supports the old and new view designer.
    protected Map<String, Object> saveCustomView(Container container, QueryDefinition queryDef,
                                                 String regionName, String viewName,
                                                 boolean share, boolean inherit,
                                                 boolean session, boolean saveFilter,
                                                 JSONObject jsonView,
                                                 ActionURL srcURL,
                                                 BindException errors)
    {
        User owner = getUser();
        boolean canSaveForAllUsers = container.hasPermission(getUser(), EditSharedViewPermission.class);
        if (share && canSaveForAllUsers && !session)
        {
            owner = null;
        }
        String name = StringUtils.trimToNull(viewName);

        boolean isHidden = false;
        CustomView view;
        if (owner == null)
            view = queryDef.getSharedCustomView(name);
        else
            view = queryDef.getCustomView(owner, getViewContext().getRequest(), name);

        // 11179: Allow editing the view if we're saving to session.
        // NOTE: Check for session flag first otherwise the call to canEdit() will add errors to the errors collection.
        boolean canEdit = view == null || session || view.canEdit(container, errors);
        if (errors.hasErrors())
            return null;

        if (canEdit)
        {
            // Issue 13594: Disallow setting of the customview inherit bit for query views
            // that have no available container filter types.  Unfortunately, the only way
            // to get the container filters is from the QueryView.  Ideally, the query def
            // would know if it was container filterable or not instead of using the QueryView.
            if (inherit && canSaveForAllUsers && !session)
            {
                UserSchema schema = queryDef.getSchema();
                QueryView queryView = schema.createView(getViewContext(), QueryView.DATAREGIONNAME_DEFAULT, queryDef.getName(), errors);
                if (queryView != null)
                {
                    Set<ContainerFilter.Type> allowableContainerFilterTypes = queryView.getAllowableContainerFilterTypes();
                    if (allowableContainerFilterTypes.size() <= 1)
                    {
                        errors.reject(ERROR_MSG, "QueryView doesn't support inherited custom views");
                        return null;
                    }
                }
            }

            // Create a new view if none exists or the current view is a shared view
            // and the user wants to override the shared view with a personal view.
            if (view == null || (owner != null && view.isShared()))
            {
                if (owner == null)
                    view = queryDef.createSharedCustomView(name);
                else
                    view = queryDef.createCustomView(owner, name);

                if (owner != null && session)
                    ((CustomViewImpl) view).isSession(true);
            }
            else if (session != view.isSession())
            {
                if (session)
                {
                    assert !view.isSession();
                    if (owner == null)
                    {
                        errors.reject(ERROR_MSG, "Session views can't be saved for all users");
                        return null;
                    }

                    // The form is saving to session but the view is in the database.
                    // Make a copy in case it's a read-only version from an XML file
                    view = queryDef.createCustomView(owner, name);
                    ((CustomViewImpl) view).isSession(true);
                }
                else
                {
                    // Remove the session view and call saveCustomView again to either create a new view or update an existing view.
                    assert view.isSession();
                    boolean success = false;
                    try
                    {
                        view.delete(getUser(), getViewContext().getRequest());
                        Map<String, Object> ret = saveCustomView(container, queryDef, regionName, viewName, share, inherit, session, saveFilter, jsonView, srcURL, errors);
                        success = !errors.hasErrors() && ret != null;
                        return success ? ret : null;
                    }
                    finally
                    {
                        if (!success)
                        {
                            // dirty the view then save the deleted session view back in session state
                            view.setName(view.getName());
                            view.save(getUser(), getViewContext().getRequest());
                        }
                    }
                }
            }

            // NOTE: Updating, saving, and deleting the view may throw an exception
            CustomViewImpl cview;
            if (view instanceof EditableCustomView && view.isOverridable())
            {
                cview = ((EditableCustomView)view).getEditableViewInfo(owner, session);
            }
            else
            {
                throw new IllegalArgumentException("View cannot be edited");
            }

            cview.update(jsonView, saveFilter);
            if (canSaveForAllUsers && !session)
            {
                cview.setCanInherit(inherit);
            }
            isHidden = view.isHidden();
            cview.setContainer(container);
            cview.save(getUser(), getViewContext().getRequest());
            if (owner == null)
            {
                // New view is shared so delete any previous custom view owned by the user with the same name.
                CustomView personalView = queryDef.getCustomView(getUser(), getViewContext().getRequest(), name);
                if (personalView != null && !personalView.isShared())
                {
                    personalView.delete(getUser(), getViewContext().getRequest());
                }
            }
        }

        ActionURL returnURL = srcURL;
        if (null == returnURL)
        {
            returnURL = getViewContext().cloneActionURL().setAction(ExecuteQueryAction.class);
        }
        else
        {
            returnURL = returnURL.clone();
            if (name == null || !canEdit)
            {
                returnURL.deleteParameter(regionName + "." + QueryParam.viewName);
            }
            else if (!isHidden)
            {
                returnURL.replaceParameter(regionName + "." + QueryParam.viewName, name);
            }
            returnURL.deleteParameter(regionName + "." + QueryParam.ignoreFilter.toString());
            if (saveFilter)
            {
                for (String key : returnURL.getKeysByPrefix(regionName + "."))
                {
                    if (isFilterOrSort(regionName, key))
                        returnURL.deleteFilterParameters(key);
                }
            }
        }

        Map<String, Object> ret = new HashMap<>();
        ret.put("redirect", returnURL);
        if (view != null)
            ret.put("view", CustomViewUtil.toMap(view, getUser(), true));
        return ret;
    }

    private boolean isFilterOrSort(String dataRegionName, String param)
    {
        assert param.startsWith(dataRegionName + ".");
        String check = param.substring(dataRegionName.length() + 1);
        if (check.contains("~"))
            return true;
        if ("sort".equals(check))
            return true;
        if (check.equals("containerFilterName"))
            return true;
        return false;
    }

    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.Configure.class)
    public class SaveQueryViewsAction extends ApiAction<SimpleApiJsonForm>
    {
        @Override
        public ApiResponse execute(SimpleApiJsonForm form, BindException errors) throws Exception
        {
            JSONObject json = form.getJsonObject();
            if (json == null)
                throw new NotFoundException("Empty request");

            String schemaName = json.getString(QueryParam.schemaName.toString());
            String queryName = json.getString(QueryParam.queryName.toString());
            if (schemaName == null || queryName == null)
                throw new NotFoundException("schemaName and queryName are required");

            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), schemaName);
            if (schema == null)
                throw new NotFoundException("schema not found");

            QueryDefinition queryDef = QueryService.get().getQueryDef(getUser(), getContainer(), schemaName, queryName);
            if (queryDef == null)
                queryDef = schema.getQueryDefForTable(queryName);
            if (queryDef == null)
                throw new NotFoundException("query not found");

            Map<String, Object> response = new HashMap<>();
            response.put(QueryParam.schemaName.toString(), schemaName);
            response.put(QueryParam.queryName.toString(), queryName);
            List<Map<String, Object>> views = new ArrayList<>();
            response.put("views", views);

            ActionURL redirect = null;
            JSONArray jsonViews = json.getJSONArray("views");
            for (int i = 0; i < jsonViews.length(); i++)
            {
                final JSONObject jsonView = jsonViews.getJSONObject(i);
                String viewName = jsonView.getString("name");

                boolean shared = jsonView.optBoolean("shared", false);
                boolean inherit = jsonView.optBoolean("inherit", false);
                boolean session = jsonView.optBoolean("session", false);
                // Users may save views to a location other than the current container
                String containerPath = jsonView.optString("containerPath", getContainer().getPath());
                Container container;
                if (inherit)
                {
                    // Only respect this request if it's a view that is inheritable in subfolders
                    container = ContainerManager.getForPath(containerPath);
                }
                else
                {
                    // Otherwise, save it in the current container
                    container = getContainer().isWorkbook() ? getContainer().getParent() : getContainer();
                }

                if (container == null)
                {
                    throw new NotFoundException("No such container: " + containerPath);
                }

                Map<String, Object> savedView = saveCustomView(
                        container, queryDef, QueryView.DATAREGIONNAME_DEFAULT, viewName,
                        shared, inherit, session, true, jsonView, null, errors);

                if (savedView != null)
                {
                    if (redirect == null)
                        redirect = (ActionURL)savedView.get("redirect");
                    views.add((Map<String, Object>)savedView.get("view"));
                }
            }

            if (redirect != null)
                response.put("redirect", redirect);

            if (errors.hasErrors())
                return null;
            else
                return new ApiSimpleResponse(response);
        }
    }

    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.Configure.class)
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
                throw new NotFoundException("Query not found");
			}
            _form = form;
            _form.setDescription(queryDef.getDescription());
            _form.setInheritable(queryDef.canInherit());
            _form.setHidden(queryDef.isHidden());
            setHelpTopic(new HelpTopic("editQueryProperties"));
            _queryName = form.getQueryName();

            return new JspView<>("/org/labkey/query/view/propertiesQuery.jsp", form, errors);
        }

        public boolean handlePost(PropertiesForm form, BindException errors) throws Exception
        {
            // assertQueryExists requires that it be well-formed
            // assertQueryExists(form);
            if (!form.canEdit())
            {
                throw new UnauthorizedException();
            }
            QueryDefinition queryDef = form.getQueryDef();
            _queryName = form.getQueryName();
            if (queryDef == null || !queryDef.getDefinitionContainer().getId().equals(getContainer().getId()))
                throw new NotFoundException("Query not found");

			_form = form;

			if (!StringUtils.isEmpty(form.rename) && !form.rename.equalsIgnoreCase(queryDef.getName()))
			{
                // issue 17766: check if query or table exist with this name
                if (null != QueryManager.get().getQueryDef(getContainer(), form.getSchemaName(), form.rename, true)
                    || null != form.getSchema().getTable(form.rename))
                {
                    errors.reject(ERROR_MSG, "A query or table with the name \"" + form.rename + "\" already exists.");
                    return false;
                }

				queryDef.setName(form.rename);
				// update form so getSuccessURL() works
				_form = new PropertiesForm(form.getSchemaName(), form.rename);
				_form.setViewContext(form.getViewContext());
                _queryName = form.rename;
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

    @RequiresPermission(AdminPermission.class)
    public class TruncateTableAction extends MutatingApiAction<QueryForm>
    {
        UserSchema schema;
        TableInfo table;

        @Override
        public void validateForm(QueryForm form, Errors errors)
        {
            String schemaName = form.getSchemaName();
            String queryName = form.getQueryName();

            if (null == schemaName || null == queryName)
                throw new NotFoundException("schemaName and queryName are required");

            schema = QueryService.get().getUserSchema(getUser(), getContainer(), schemaName);
            if (null == schema)
                throw new NotFoundException("The schema '" + schemaName + "' does not exist.");

            table = schema.getTable(queryName);
            if (null == table)
                throw new NotFoundException("The query '" + queryName + "' in the schema '" + schemaName + "' does not exist.");
        }

        @Override
        public ApiResponse execute(QueryForm form, BindException errors) throws Exception
        {
            int deletedRows;
            QueryUpdateService qus = table.getUpdateService();

            if (null == qus)
                throw new IllegalArgumentException("The query '" + form.getQueryName() + "' in the schema '" + form.getSchemaName() + "' is not truncatable.");

            try (DbScope.Transaction transaction = table.getSchema().getScope().ensureTransaction())
            {
                deletedRows = qus.truncateRows(getUser(), getContainer(), null, null);
                transaction.commit();
            }

            ApiSimpleResponse response = new ApiSimpleResponse();

            response.put("success", true);
            response.put(BaseSaveRowsAction.PROP_SCHEMA_NAME, form.getSchemaName());
            response.put(BaseSaveRowsAction.PROP_QUERY_NAME, form.getQueryName());
            response.put("deletedRows", deletedRows);

            return response;
        }
    }


    @RequiresPermission(DeletePermission.class)
    public class DeleteQueryRowsAction extends FormHandlerAction<QueryForm>
    {
        public void validateCommand(QueryForm target, Errors errors)
        {
        }

        public boolean handlePost(QueryForm form, BindException errors) throws Exception
        {
            ensureQueryExists(form);
            TableInfo table = form.getQueryDef().getTable(form.getSchema(), null, true);

            if (!table.hasPermission(getUser(), DeletePermission.class))
            {
                throw new UnauthorizedException();
            }

            QueryUpdateService updateService = table.getUpdateService();
            if (updateService == null)
                throw new UnsupportedOperationException("Unable to delete - no QueryUpdateService registered for " + form.getSchemaName() + "." + form.getQueryName());

            Set<String> ids = DataRegionSelection.getSelected(form.getViewContext(), null, true, true);
            List<ColumnInfo> pks = table.getPkColumns();
            int numPks = pks.size();

            //normalize the pks to arrays of correctly-typed objects
            List<Map<String, Object>> keyValues = new ArrayList<>(ids.size());
            for (String id : ids)
            {
                String[] stringValues;
                if (numPks > 1)
                {
                    stringValues = id.split(",");
                    if (stringValues.length != numPks)
                        throw new IllegalStateException("This table has " + numPks + " primary-key columns, but " + stringValues.length + " primary-key values were provided!");
                }
                else
                    stringValues = new String[]{id};

                Map<String, Object> rowKeyValues = new CaseInsensitiveHashMap<>();
                for (int idx = 0; idx < numPks; ++idx)
                {
                    ColumnInfo keyColumn = pks.get(idx);
                    Object keyValue = keyColumn.getJavaClass() == String.class ? stringValues[idx] : ConvertUtils.convert(stringValues[idx], keyColumn.getJavaClass());
                    rowKeyValues.put(keyColumn.getName(), keyValue);
                }
                keyValues.add(rowKeyValues);
            }

            DbSchema dbSchema = table.getSchema();
            try (DbScope.Transaction tx = dbSchema.getScope().ensureTransaction())
            {
                updateService.deleteRows(getUser(), getContainer(), keyValues, null, null);
                tx.commit();
            }
            catch (SQLException x)
            {
                if (!RuntimeSQLException.isConstraintException(x))
                    throw x;
                errors.reject(ERROR_MSG, getMessage(table.getSchema().getSqlDialect(), x));
                return false;
            }
            catch (DataIntegrityViolationException | OptimisticConflictException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return false;
            }
            catch (BatchValidationException x)
            {
                x.addToErrors(errors);
            }
            catch (Exception x)
            {
                errors.reject(ERROR_MSG, null == x.getMessage() ? x.toString() : x.getMessage());
                ExceptionUtil.logExceptionToMothership(getViewContext().getRequest(), x);
            }

            return !errors.hasErrors();
        }

        public ActionURL getSuccessURL(QueryForm form)
        {
            return form.getReturnActionURL();
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class DetailsQueryRowAction extends UserSchemaAction
    {
        public ModelAndView getView(QueryUpdateForm tableForm, boolean reshow, BindException errors) throws Exception
        {
            ButtonBar bb = new ButtonBar();
            bb.setStyle(ButtonBar.Style.separateButtons);

            if (_schema != null && _table != null)
            {
                if (_table.hasPermission(getUser(), UpdatePermission.class))
                {
                    StringExpression updateExpr = _form.getQueryDef().urlExpr(QueryAction.updateQueryRow, _schema.getContainer());
                    if (updateExpr != null)
                    {
                        String url = updateExpr.eval(tableForm.getTypedValues());
                        if (url != null)
                        {
                            ActionURL updateUrl = new ActionURL(url);
                            ActionButton editButton = new ActionButton("Edit", updateUrl);
                            bb.add(editButton);
                        }
                    }
                }


                ActionURL gridUrl;
                if (_form.getReturnActionURL() != null)
                {
                    // If we have a specific return URL requested, use that
                    gridUrl = _form.getReturnActionURL();
                }
                else
                {
                    // Otherwise go back to the default grid view
                    gridUrl = _schema.urlFor(QueryAction.executeQuery, _form.getQueryDef());
                }
                if (gridUrl != null)
                {
                    ActionButton gridButton = new ActionButton("Show Grid", gridUrl);
                    bb.add(gridButton);
                }
            }

            DetailsView detailsView = new DetailsView(tableForm);
            detailsView.setFrame(WebPartView.FrameType.PORTAL);
            detailsView.getDataRegion().setButtonBar(bb);

            VBox view = new VBox(detailsView);

            DetailsURL detailsURL = QueryService.get().getAuditDetailsURL(getUser(), getContainer(), _table);

            if (detailsURL != null)
            {
                String url = detailsURL.eval(tableForm.getTypedValues());
                if (url != null)
                {
                    ActionURL auditURL = new ActionURL(url);

                    QueryView historyView = QueryUpdateAuditProvider.createDetailsQueryView(getViewContext(),
                            auditURL.getParameter(QueryParam.schemaName),
                            auditURL.getParameter(QueryParam.queryName),
                            auditURL.getParameter("keyValue"), errors);

                    historyView.setFrame(WebPartView.FrameType.PORTAL);
                    historyView.setTitle("History");

                    view.addView(historyView);
                }
            }
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

    @RequiresPermission(InsertPermission.class)
    public static class InsertQueryRowAction extends UserSchemaAction
    {
        @Override
        public BindException bindParameters(PropertyValues m) throws Exception
        {
            BindException bind = super.bindParameters(m);

            // what is going on with UserSchemaAction and form binding?  Why doesn't successUrl bind?
            QueryUpdateForm form = (QueryUpdateForm)bind.getTarget();
            if (null == form.getSuccessUrl() && null != m.getPropertyValue(ActionURL.Param.successUrl.name()))
                form.setSuccessUrl(m.getPropertyValue(ActionURL.Param.successUrl.name()).getValue().toString());
            return bind;
        }

        Map<String,Object> insertedRow = null;

        public ModelAndView getView(QueryUpdateForm tableForm, boolean reshow, BindException errors) throws Exception
        {
            InsertView view = new InsertView(tableForm, errors);
            view.getDataRegion().setButtonBar(createSubmitCancelButtonBar(tableForm));
            return view;
        }

        public boolean handlePost(QueryUpdateForm tableForm, BindException errors) throws Exception
        {
            List<Map<String,Object>> list = doInsertUpdate(tableForm, errors, true);
            if (null != list && list.size() == 1)
                insertedRow = list.get(0);
            return 0 == errors.getErrorCount();
        }

        /**
         * NOTE: UserSchemaAction.appendNavTrail() uses this method getSuccessURL() for the nav trail link (form==null).
         * It is used for where to go on success, and also as a "back" link in the nav trail
         * If there is a setSuccessUrl specified, we will use that for successful submit
         */
        @Override
        public ActionURL getSuccessURL(QueryUpdateForm form)
        {
            if (null == form)
                return super.getSuccessURL(null);

            String str = form.getSuccessUrl();
            if (StringUtils.isBlank(str))
                str = form.getReturnUrl();

            if (StringUtils.equals(str,"details.view"))
            {
                if (null == insertedRow)
                    return super.getSuccessURL(form);
                StringExpression se = form.getTable().getDetailsURL(null, getContainer());
                if (null == se)
                    return super.getSuccessURL(form);
                str = se.eval(insertedRow);
            }
            try
            {
                if (!StringUtils.isBlank(str))
                    return new ActionURL(str);
            }
            catch (IllegalArgumentException x)
            {
                // pass
            }
            return super.getSuccessURL(form);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            super.appendNavTrail(root);
            root.addChild("Insert " + _table.getName());
            return root;
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public static class UpdateQueryRowAction extends UserSchemaAction
    {
        public ModelAndView getView(QueryUpdateForm tableForm, boolean reshow, BindException errors) throws Exception
        {
            ButtonBar bb = createSubmitCancelButtonBar(tableForm);
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
            root.addChild("Edit " + _table.getName());
            return root;
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public static class UpdateQueryRowsAction extends UpdateQueryRowAction
    {
        @Override
        public ModelAndView handleRequest(QueryUpdateForm tableForm, BindException errors) throws Exception
        {
            tableForm.setBulkUpdate(true);
            return super.handleRequest(tableForm, errors);
        }

        @Override
        public boolean handlePost(QueryUpdateForm tableForm, BindException errors) throws Exception
        {
            if (tableForm.isDataSubmit())
                return super.handlePost(tableForm, errors);

            return false;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            root.addChild("Edit Multiple " + _table.getName());
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
        QueryView _queryView;
    }

    public abstract class QueryFormAction extends FormViewAction<QueryForm>
    {
        QueryForm _form;
        QueryView _queryView;
    }

    public static class APIQueryForm extends QueryForm
    {
        private Integer _start;
        private Integer _limit;
        private boolean _includeDetailsColumn = false;
        private boolean _includeUpdateColumn = false;
        private String _containerFilter;
        private boolean _includeTotalCount = true;
        private boolean _includeStyle = false;
        private boolean _includeDisplayValues = false;
        private boolean _minimalColumns = true;

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

        public String getContainerFilter()
        {
            return _containerFilter;
        }

        public void setContainerFilter(String containerFilter)
        {
            _containerFilter = containerFilter;
        }

        public boolean isIncludeTotalCount()
        {
            return _includeTotalCount;
        }

        public void setIncludeTotalCount(boolean includeTotalCount)
        {
            _includeTotalCount = includeTotalCount;
        }

        public boolean isIncludeStyle()
        {
            return _includeStyle;
        }

        public void setIncludeStyle(boolean includeStyle)
        {
            _includeStyle = includeStyle;
        }

        public boolean isIncludeDetailsColumn()
        {
            return _includeDetailsColumn;
        }

        public void setIncludeDetailsColumn(boolean includeDetailsColumn)
        {
            _includeDetailsColumn = includeDetailsColumn;
        }

        public boolean isIncludeUpdateColumn()
        {
            return _includeUpdateColumn;
        }

        public void setIncludeUpdateColumn(boolean includeUpdateColumn)
        {
            _includeUpdateColumn = includeUpdateColumn;
        }

        public boolean isIncludeDisplayValues()
        {
            return _includeDisplayValues;
        }

        public void setIncludeDisplayValues(boolean includeDisplayValues)
        {
            _includeDisplayValues = includeDisplayValues;
        }

        public boolean isMinimalColumns()
        {
            return _minimalColumns;
        }

        public void setMinimalColumns(boolean minimalColumns)
        {
            _minimalColumns = minimalColumns;
        }
    }

    public static final int DEFAULT_API_MAX_ROWS = 100000;

    @CSRF(CSRF.Method.NONE)//TODO remove this annotation when possible (see flow-reports-create, flow-reports-update)
    @ActionNames("selectRows, getQuery")
    @RequiresPermission(ReadPermission.class)
    @ApiVersion(9.1)
    @Action(ActionType.SelectData.class)
    public class SelectRowsAction extends ApiAction<APIQueryForm>
    {
        public ApiResponse execute(APIQueryForm form, BindException errors) throws Exception
        {
            ensureQueryExists(form);

            // Issue 12233: add implicit maxRows=100k when using client API
            HttpServletRequest request = getViewContext().getRequest();
            boolean missingShowRows = null == request.getParameter(form.getDataRegionName() + "." + QueryParam.showRows);
            if (null == form.getLimit() && !form.getQuerySettings().isMaxRowsSet() && missingShowRows)
            {
                form.getQuerySettings().setShowRows(ShowRows.PAGINATED);
                form.getQuerySettings().setMaxRows(DEFAULT_API_MAX_ROWS);
            }

            if (form.getLimit() != null)
            {
                form.getQuerySettings().setShowRows(ShowRows.PAGINATED);
                form.getQuerySettings().setMaxRows(form.getLimit());
            }
            if (form.getStart() != null)
                form.getQuerySettings().setOffset(form.getStart());
            if (form.getContainerFilter() != null)
            {
                // If the user specified an incorrect filter, throw an IllegalArgumentException
                ContainerFilter.Type containerFilterType =
                    ContainerFilter.Type.valueOf(form.getContainerFilter());
                form.getQuerySettings().setContainerFilterName(containerFilterType.name());
            }

            QueryView view = QueryView.create(form, errors);

            view.setShowPagination(form.isIncludeTotalCount());

            //if viewName was specified, ensure that it was actually found and used
            //QueryView.create() will happily ignore an invalid view name and just return the default view
            if (null != StringUtils.trimToNull(form.getViewName()) &&
                    null == view.getQueryDef().getCustomView(getUser(), getViewContext().getRequest(), form.getViewName()))
            {
                throw new NotFoundException("The view named '" + form.getViewName() + "' does not exist for this user!");
            }

            boolean isEditable = isQueryEditable(view.getTable());
            boolean metaDataOnly = form.getQuerySettings().getMaxRows() == 0;
            boolean arrayMultiValueColumns = getRequestedApiVersion() >= 16.2;
            boolean includeFormattedValue = getRequestedApiVersion() >= 17.1;

            ApiQueryResponse response;

            // 13.2 introduced the getData API action, a condensed response wire format, and a js wrapper to consume the wire format. Support this as an option for legacy APIs.
            if (getRequestedApiVersion() >= 13.2)
            {
                ReportingApiQueryResponse fancyResponse = new ReportingApiQueryResponse(view, isEditable, true, view.getQueryDef().getName(), form.getQuerySettings().getOffset(), null,
                        metaDataOnly, form.isIncludeDetailsColumn(), form.isIncludeUpdateColumn());
                fancyResponse.arrayMultiValueColumns(arrayMultiValueColumns);
                fancyResponse.includeFormattedValue(includeFormattedValue);
                response = fancyResponse;
            }
            //if requested version is >= 9.1, use the extended api query response
            else if (getRequestedApiVersion() >= 9.1)
            {
                response = new ExtendedApiQueryResponse(view, isEditable, true,
                        form.getSchemaName(), form.getQueryName(), form.getQuerySettings().getOffset(), null,
                        metaDataOnly, form.isIncludeDetailsColumn(), form.isIncludeUpdateColumn());
            }
            else
            {
                response = new ApiQueryResponse(view, isEditable, true,
                        form.getSchemaName(), form.getQueryName(), form.getQuerySettings().getOffset(), null,
                        metaDataOnly, form.isIncludeDetailsColumn(), form.isIncludeUpdateColumn(),
                        form.isIncludeDisplayValues());
            }
            response.includeStyle(form.isIncludeStyle());

            // Issues 29515 and 32269 - force key and other non-requested columns to be sent back, but only if the client has
            // requested minimal columns, as we now do for ExtJS stores
            if (form.isMinimalColumns())
            {
                response.setColumnFilter(form.getQuerySettings().getFieldKeys());
            }

            return response;
        }
    }

    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectData.class)
    public class GetDataAction extends ApiAction<SimpleApiJsonForm>
    {
        public ApiResponse execute(SimpleApiJsonForm form, BindException errors) throws Exception
        {
            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY, true);
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            JSONObject object = form.getJsonObject();
            if (object == null)
            {
                object = new JSONObject();
            }
            DataRequest builder = mapper.readValue(object.toString(), DataRequest.class);

            return builder.render(getViewContext(), errors);
        }
    }

    protected boolean isQueryEditable(TableInfo table)
    {
        if (!getContainer().hasPermission("isQueryEditable", getUser(), DeletePermission.class))
            return false;
        QueryUpdateService updateService = null;
        try
        {
            updateService = table.getUpdateService();
        }
        catch(Exception ignore) {}
        return null != table && null != updateService;
    }

    public static class ExecuteSqlForm extends APIQueryForm
    {
        private String _sql;
        private Integer _maxRows;
        private Integer _offset;
        private boolean _saveInSession;

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

        public void setLimit(Integer limit)
        {
            _maxRows = limit;
        }

        public void setStart(Integer start)
        {
            _offset = start;
        }

        public boolean isSaveInSession()
        {
            return _saveInSession;
        }

        public void setSaveInSession(boolean saveInSession)
        {
            _saveInSession = saveInSession;
        }

        @Override
        public String getQueryName()
        {
            // ExecuteSqlAction doesn't allow setting query name parameter.
            return null;
        }

        @Override
        public void setQueryName(String name)
        {
            // ExecuteSqlAction doesn't allow setting query name parameter.
        }
    }

    @CSRF(CSRF.Method.NONE)//TODO remove this annotation when possible (see assay-assayResults.view various luminex reports)
    @RequiresPermission(ReadPermission.class)
    @ApiVersion(9.1)
    @Action(ActionType.SelectData.class)
    public class ExecuteSqlAction extends ApiAction<ExecuteSqlForm>
    {
        public ApiResponse execute(ExecuteSqlForm form, BindException errors) throws Exception
        {
            if (form.getSchema() == null)
            {
                throw new NotFoundException("Could not find schema: " + form.getSchemaName());
            }

            String schemaName = StringUtils.trimToNull(form.getQuerySettings().getSchemaName());
            if (null == schemaName)
                throw new IllegalArgumentException("No value was supplied for the required parameter 'schemaName'.");
            String sql = StringUtils.trimToNull(form.getSql());
            if (null == sql)
                throw new IllegalArgumentException("No value was supplied for the required parameter 'sql'.");

            //create a temp query settings object initialized with the posted LabKey SQL
            //this will provide a temporary QueryDefinition to Query
            QuerySettings settings = form.getQuerySettings();
            if (form.isSaveInSession())
            {
                HttpSession session = getViewContext().getSession();
                if (session == null)
                    throw new IllegalStateException("Session required");

                QueryDefinition def = QueryService.get().saveSessionQuery(getViewContext(), getContainer(), schemaName, sql);
                settings.setDataRegionName("executeSql");
                settings.setQueryName(def.getName());
            }
            else
            {
                settings = new TempQuerySettings(getViewContext(), sql, settings);
            }

            //need to explicitly turn off various UI options that will try to refer to the
            //current URL and query string
            settings.setAllowChooseView(false);
            settings.setAllowCustomizeView(false);

            // Issue 12233: add implicit maxRows=100k when using client API
            settings.setShowRows(ShowRows.PAGINATED);
            settings.setMaxRows(DEFAULT_API_MAX_ROWS);

            // 16961: ExecuteSql API without maxRows parameter defaults to returning 100 rows
            //apply optional settings (maxRows, offset)
            boolean metaDataOnly = false;
            if (null != form.getMaxRows() && (form.getMaxRows() >= 0 || form.getMaxRows() == Table.ALL_ROWS))
            {
                settings.setMaxRows(form.getMaxRows());
                metaDataOnly = Table.NO_ROWS == form.getMaxRows();
            }

            int offset = 0;
            if (null != form.getOffset())
            {
                settings.setOffset(form.getOffset().longValue());
                offset = form.getOffset();
            }

            if (form.getContainerFilter() != null)
            {
                // If the user specified an incorrect filter, throw an IllegalArgumentException
                ContainerFilter.Type containerFilterType =
                    ContainerFilter.Type.valueOf(form.getContainerFilter());
                settings.setContainerFilterName(containerFilterType.name());
            }

            //build a query view using the schema and settings
            QueryView view = new QueryView(form.getSchema(), settings, errors);
            view.setShowRecordSelectors(false);
            view.setShowExportButtons(false);
            view.setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);
            view.setShowPagination(form.isIncludeTotalCount());

            TableInfo t = view.getTable();
            boolean isEditable = null != t && isQueryEditable(view.getTable());
            boolean arrayMultiValueColumns = getRequestedApiVersion() >= 16.2;
            boolean includeFormattedValue = getRequestedApiVersion() >= 17.1;

            // 13.2 introduced the getData API action, a condensed response wire format, and a js wrapper to consume the wire format. Support this as an option for legacy APIs.
            if (getRequestedApiVersion() >= 13.2)
            {
                ReportingApiQueryResponse response = new ReportingApiQueryResponse(view, isEditable, false, form.isSaveInSession() ? settings.getQueryName() : "sql", offset, null,
                        metaDataOnly, form.isIncludeDetailsColumn(), form.isIncludeUpdateColumn());
                response.includeStyle(form.isIncludeStyle());
                response.arrayMultiValueColumns(arrayMultiValueColumns);
                response.includeFormattedValue(includeFormattedValue);
                return response;
            }
            if (getRequestedApiVersion() >= 9.1)
                return new ExtendedApiQueryResponse(view, isEditable,
                        false, schemaName, form.isSaveInSession() ? settings.getQueryName() : "sql", offset, null,
                        metaDataOnly, form.isIncludeDetailsColumn(), form.isIncludeUpdateColumn());
            else
                return new ApiQueryResponse(view, isEditable,
                        false, schemaName, form.isSaveInSession() ? settings.getQueryName() : "sql", offset, null,
                        metaDataOnly, form.isIncludeDetailsColumn(), form.isIncludeUpdateColumn(),
                        form.isIncludeDisplayValues());
        }
    }

    public static class SelectDistinctForm extends QueryForm
    {
        private String _containerFilter;

        public String getContainerFilter()
        {
            return _containerFilter;
        }

        public void setContainerFilter(String containerFilter)
        {
            _containerFilter = containerFilter;
        }
    }

    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectData.class)
    public class SelectDistinctAction extends ApiAction<SelectDistinctForm>
    {
        @Override
        public ApiResponse execute(SelectDistinctForm form, BindException errors) throws Exception
        {
            ensureQueryExists(form);

            TableInfo table = form.getSchema().getTable(form.getQueryName());
            SqlSelector sqlSelector = getDistinctSql(table, form, errors);

            if (errors.hasErrors() || null == sqlSelector)
                return null;

            ApiResponseWriter writer = new ApiJsonWriter(getViewContext().getResponse());
            writer.startResponse();

            writer.writeProperty("schemaName", form.getSchemaName());
            writer.writeProperty("queryName", form.getQueryName());
            writer.startList("values");

            try (ResultSet rs = sqlSelector.getResultSet())
            {
                while (rs.next())
                {
                    writer.writeListEntry(rs.getObject(1));
                }
            }
            catch (SQLException x)
            {
                throw new RuntimeSQLException(x);
            }
            catch (DataAccessException x)       // Spring error translator can return various subclasses of this
            {
                throw new RuntimeException(x);
            }
            writer.endList();
            writer.endResponse();

            return null;
        }

        @Nullable
        private SqlSelector getDistinctSql(TableInfo table, SelectDistinctForm form, BindException errors)
        {
            QuerySettings settings = form.getQuerySettings();
            QueryService service = QueryService.get();

            if (null == getViewContext().getRequest().getParameter(QueryParam.maxRows.toString()))
                settings.setMaxRows(DEFAULT_API_MAX_ROWS);
            else
                settings.setMaxRows(Integer.parseInt(getViewContext().getRequest().getParameter(QueryParam.maxRows.toString())));

            if (null != form.getContainerFilter())
            {
                // If the user specified an incorrect filter, throw an IllegalArgumentException
                ContainerFilter.Type containerFilterType =
                        ContainerFilter.Type.valueOf(form.getContainerFilter());
                settings.setContainerFilterName(containerFilterType.name());

                if (table instanceof ContainerFilterable)
                    ((ContainerFilterable)table).setContainerFilter(ContainerFilter.getContainerFilterByName(settings.getContainerFilterName(), table.getUserSchema().getUser()));
            }

            List<FieldKey> fieldKeys = settings.getFieldKeys();
            if (null == fieldKeys || fieldKeys.size() != 1)
            {
                errors.reject(ERROR_MSG, "Select Distinct requires that only one column be requested.");
                return null;
            }
            Map<FieldKey, ColumnInfo> columns = service.getColumns(table, fieldKeys);
            if (columns.size() != 1)
            {
                errors.reject(ERROR_MSG, "Select Distinct requires that only one column be requested.");
                return null;
            }

            ColumnInfo col = columns.get(settings.getFieldKeys().get(0));
            if (col == null)
            {
                errors.reject(ERROR_MSG, "\"" + settings.getFieldKeys().get(0).getName() + "\" is not a valid column.");
                return null;
            }

            SimpleFilter filter = getFilterFromQueryForm(form);

            // Strip out filters on columns that don't exist - issue 21669
            service.ensureRequiredColumns(table, columns.values(), filter, null, new HashSet<FieldKey>());
            QueryLogging queryLogging = new QueryLogging();
            SQLFragment selectSql = service.getSelectSQL(table, columns.values(), filter, null, Table.ALL_ROWS, Table.NO_OFFSET, false, queryLogging);

            if (queryLogging.getColumnLoggings().contains(col.getColumnLogging()))
            {
                errors.reject(ERROR_MSG, "Cannot choose values from a column that requires logging.");
                return null;
            }

            // Regenerate the column since the alias may have changed after call to getSelectSQL()
            columns = service.getColumns(table, settings.getFieldKeys());
            col = columns.get(settings.getFieldKeys().get(0));

            SQLFragment sql = new SQLFragment("SELECT DISTINCT " + table.getSqlDialect().getColumnSelectName(col.getAlias()) + " AS value FROM (");
            sql.append(selectSql);
            sql.append(") S ORDER BY value");

            sql = table.getSqlDialect().limitRows(sql, settings.getMaxRows());

            // 18875: Support Parameterized queries in Select Distinct
            Map<String, Object> _namedParameters = settings.getQueryParameters();

            if (null != _namedParameters)
            {
                try
                {
                    service.bindNamedParameters(sql, _namedParameters);
                    service.validateNamedParameters(sql);
                }
                catch (ConversionException | QueryService.NamedParameterNotProvided e)
                {
                    errors.reject(ERROR_MSG, e.getMessage());
                    return null;
                }
            }

            return new SqlSelector(table.getSchema().getScope(), sql, QueryLogging.noValidationNeededQueryLogging());
        }
    }

    private SimpleFilter getFilterFromQueryForm(QueryForm form)
    {
        QuerySettings settings = form.getQuerySettings();
        SimpleFilter filter = null;

        // 21032: Respect 'ignoreFilter'
        if (settings != null && !settings.getIgnoreUserFilter())
        {
            // Attach any URL-based filters. This would apply to 'filterArray' from the JavaScript API.
            filter = new SimpleFilter(settings.getBaseFilter());

            String dataRegionName = form.getDataRegionName();
            if (StringUtils.trimToNull(dataRegionName) == null)
                dataRegionName = QueryView.DATAREGIONNAME_DEFAULT;

            // Support for 'viewName'
            CustomView view = settings.getCustomView(getViewContext(), form.getQueryDef());
            if (null != view && view.hasFilterOrSort())
            {
                ActionURL url = new ActionURL(SelectDistinctAction.class, getContainer());
                view.applyFilterAndSortToURL(url, dataRegionName);
                filter.addAllClauses(new SimpleFilter(url, dataRegionName));
            }

            filter.addUrlFilters(settings.getSortFilterURL(), dataRegionName);
        }

        return filter;
    }

    @RequiresPermission(ReadPermission.class)
    public class GetColumnSummaryStatsAction extends ApiAction<QueryForm>
    {
        private FieldKey _colFieldKey;

        @Override
        public void validateForm(QueryForm form, Errors errors)
        {
            ensureQueryExists(form);

            QuerySettings settings = form.getQuerySettings();
            List<FieldKey> fieldKeys = settings != null ? settings.getFieldKeys() : null;
            if (null == fieldKeys || fieldKeys.isEmpty() || fieldKeys.size() != 1)
                errors.reject(ERROR_MSG, "GetColumnSummaryStats requires that only one column be requested.");
            else
                _colFieldKey = fieldKeys.get(0);
        }

        public ApiResponse execute(QueryForm form, BindException errors) throws Exception
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            QueryView view = QueryView.create(form, errors);
            DisplayColumn displayColumn = null;

            for (DisplayColumn dc : view.getDisplayColumns())
            {
                if (dc.getColumnInfo() != null && _colFieldKey.equals(dc.getColumnInfo().getFieldKey()))
                {
                    displayColumn = dc;
                    break;
                }
            }

            if (displayColumn != null && displayColumn.getColumnInfo() != null)
            {
                // get the map of the analytics providers to their relevant aggregates and add the information to the response
                Map<String, Map<String, Object>> analyticsProviders = new LinkedHashMap<>();
                Set<Aggregate> colAggregates = new HashSet<>();
                for (ColumnAnalyticsProvider analyticsProvider : displayColumn.getAnalyticsProviders())
                {
                    if (analyticsProvider instanceof BaseAggregatesAnalyticsProvider)
                    {
                        BaseAggregatesAnalyticsProvider baseAggProvider = (BaseAggregatesAnalyticsProvider) analyticsProvider;
                        Map<String, Object> props = new HashMap<>();
                        props.put("label", baseAggProvider.getLabel());

                        List<String> aggregateNames = new ArrayList<>();
                        for (Aggregate aggregate : AnalyticsProviderItem.createAggregates(baseAggProvider, _colFieldKey, null))
                        {
                            aggregateNames.add(aggregate.getType().getName());
                            colAggregates.add(aggregate);
                        }
                        props.put("aggregates", aggregateNames);

                        analyticsProviders.put(baseAggProvider.getName(), props);
                    }
                }

                // get the filter set from the queryform and verify that they resolve
                SimpleFilter filter = getFilterFromQueryForm(form);
                if (filter != null)
                {
                    Map<FieldKey, ColumnInfo> resolvedCols = QueryService.get().getColumns(view.getTable(), filter.getAllFieldKeys());
                    for (FieldKey filterFieldKey : filter.getAllFieldKeys())
                    {
                        if (!resolvedCols.containsKey(filterFieldKey))
                            filter.deleteConditions(filterFieldKey);
                    }
                }

                // query the table/view for the aggregate results
                Collection<ColumnInfo> columns = Collections.singleton(displayColumn.getColumnInfo());
                TableSelector selector = new TableSelector(view.getTable(), columns, filter, null).setNamedParameters(form.getQuerySettings().getQueryParameters());
                Map<String, List<Aggregate.Result>> aggResults = selector.getAggregates(new ArrayList(colAggregates));

                // create a response object mapping the analytics providers to their relevant aggregate results
                Map<String, Map<String, Object>> aggregateResults = new HashMap<>();
                for (Aggregate.Result r : aggResults.get(_colFieldKey.toString()))
                {
                    Map<String, Object> props = new HashMap<>();
                    Aggregate.Type type = r.getAggregate().getType();
                    props.put("label", type.getFullLabel());
                    props.put("description", type.getDescription());
                    props.put("value", r.getFormattedValue(displayColumn, getContainer()));
                    aggregateResults.put(type.getName(), props);
                }

                response.put("success", true);
                response.put("analyticsProviders", analyticsProviders);
                response.put("aggregateResults", aggregateResults);
            }
            else
            {
                response.put("success", false);
                response.put("message", "Unable to find ColumnInfo for " + _colFieldKey);
            }

            return response;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ImportAction extends AbstractQueryImportAction<QueryForm>
    {
        private QueryForm _form;

        public ImportAction()
        {
            super(QueryForm.class);
        }

        @Override
        protected void initRequest(QueryForm form) throws ServletException
        {
            _form = form;
            ensureQueryExists(form);

            QueryDefinition query = form.getQueryDef();
            List<QueryException> qpe = new ArrayList<>();
            TableInfo t = query.getTable(form.getSchema(), qpe, true);
            if (!qpe.isEmpty())
                throw qpe.get(0);
            if (null != t)
                setTarget(t);
        }

        @Override
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            initRequest(form);
            return super.getDefaultImportView(form, errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            (new SchemaAction(_form)).appendNavTrail(root);
            root.addChild(_form.getQueryName(), _form.urlFor(QueryAction.executeQuery));
            root.addChild("Import Data");
            return root;
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

        @SuppressWarnings({"UnusedDeclaration"})
        public void setFormat(String format)
        {
            _format = format;
        }
    }

    @RequiresPermission(ReadPermission.class)
    @ApiVersion(9.2)
    @Action(ActionType.Export.class)
    public class ExportSqlAction extends ExportAction<ExportSqlForm>
    {
        public void export(ExportSqlForm form, HttpServletResponse response, BindException errors) throws IOException, ExportException
        {
            String schemaName = StringUtils.trimToNull(form.getSchemaName());
            if (null == schemaName)
                throw new NotFoundException("No value was supplied for the required parameter 'schemaName'");
            String sql = StringUtils.trimToNull(form.getSql());
            if (null == sql)
                throw new NotFoundException("No value was supplied for the required parameter 'sql'");

            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), schemaName);

            if (null == schema)
                throw new NotFoundException("Schema '" + schemaName + "' not found in this folder");

            //create a temp query settings object initialized with the posted LabKey SQL
            //this will provide a temporary QueryDefinition to Query
            TempQuerySettings settings = new TempQuerySettings(getViewContext(), sql);

            //need to explicitly turn off various UI options that will try to refer to the
            //current URL and query string
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
            response.setHeader("Pragma", "private");
            response.setHeader("Cache-Control", "private");
            response.setHeader("X-Robots-Tag", "noindex");

            if ("excel".equalsIgnoreCase(form.getFormat()))
                view.exportToExcel(response);
            else if ("tsv".equalsIgnoreCase(form.getFormat()))
                view.exportToTsv(response);
            else
                errors.reject(null, "Invalid format specified; must be 'excel' or 'tsv'");

            for (QueryException qe : view.getParseErrors())
                errors.reject(null, qe.getMessage());

            if (errors.hasErrors())
                throw new ExportException(new SimpleErrorView(errors, false));
        }
    }

    public static class ApiSaveRowsForm extends SimpleApiJsonForm
    {
    }

    private enum CommandType
    {
        insert(InsertPermission.class)
        {
            public List<Map<String, Object>> saveRows(QueryUpdateService qus, List<Map<String, Object>> rows, User user, Container container, Map<String, Object> extraContext)
                    throws SQLException, InvalidKeyException, QueryUpdateServiceException, BatchValidationException, DuplicateKeyException
            {
                BatchValidationException errors = new BatchValidationException();
                List<Map<String, Object>> insertedRows = qus.insertRows(user, container, rows, errors, null, extraContext);
                if (errors.hasErrors())
                    throw errors;
                return qus.getRows(user, container, insertedRows);
            }
        },
        insertWithKeys(InsertPermission.class)
        {
            public List<Map<String, Object>> saveRows(QueryUpdateService qus, List<Map<String, Object>> rows, User user, Container container, Map<String, Object> extraContext)
                    throws SQLException, InvalidKeyException, QueryUpdateServiceException, BatchValidationException, DuplicateKeyException
            {
                List<Map<String, Object>> newRows = new ArrayList<>();
                List<Map<String, Object>> oldKeys = new ArrayList<>();
                for (Map<String, Object> row : rows)
                {
                    //issue 13719: use CaseInsensitiveHashMaps.  Also allow either values or oldKeys to be null
                    CaseInsensitiveHashMap newMap = row.get(SaveRowsAction.PROP_VALUES) != null ? new CaseInsensitiveHashMap((Map<String, Object>)row.get(SaveRowsAction.PROP_VALUES)) : new CaseInsensitiveHashMap();
                    newRows.add(newMap);

                    CaseInsensitiveHashMap oldMap = row.get(SaveRowsAction.PROP_OLD_KEYS) != null ? new CaseInsensitiveHashMap((Map<String, Object>)row.get(SaveRowsAction.PROP_OLD_KEYS)) : new CaseInsensitiveHashMap();
                    oldKeys.add(oldMap);
                }
                BatchValidationException errors = new BatchValidationException();
                List<Map<String, Object>> updatedRows = qus.insertRows(user, container, newRows, errors, null, extraContext);
                if (errors.hasErrors())
                    throw errors;
                updatedRows = qus.getRows(user, container, updatedRows);
                List<Map<String, Object>> results = new ArrayList<>();
                for (int i = 0; i < updatedRows.size(); i++)
                {
                    Map<String, Object> result = new HashMap<>();
                    result.put(SaveRowsAction.PROP_VALUES, updatedRows.get(i));
                    result.put(SaveRowsAction.PROP_OLD_KEYS, oldKeys.get(i));
                    results.add(result);
                }
                return results;
            }
        },
        importRows(InsertPermission.class)
        {
            public List<Map<String, Object>> saveRows(QueryUpdateService qus, List<Map<String, Object>> rows, User user, Container container, Map<String, Object> extraContext)
                    throws SQLException, InvalidKeyException, QueryUpdateServiceException, BatchValidationException, DuplicateKeyException
            {
                BatchValidationException errors = new BatchValidationException();
                DataIteratorBuilder it = new ListofMapsDataIterator.Builder(rows.get(0).keySet(), rows);
                qus.importRows(user, container, it, errors, null, extraContext);
                if (errors.hasErrors())
                    throw errors;
                return Collections.emptyList();
            }
        },
        update(UpdatePermission.class)
        {
            public List<Map<String, Object>> saveRows(QueryUpdateService qus, List<Map<String, Object>> rows, User user, Container container, Map<String, Object> extraContext)
                    throws SQLException, InvalidKeyException, QueryUpdateServiceException, BatchValidationException, DuplicateKeyException
            {
                List<Map<String, Object>> updatedRows = qus.updateRows(user, container, rows, rows, null, extraContext);
                return qus.getRows(user, container, updatedRows);
            }
        },
        updateChangingKeys(UpdatePermission.class)
        {
            public List<Map<String, Object>> saveRows(QueryUpdateService qus, List<Map<String, Object>> rows, User user, Container container, Map<String, Object> extraContext)
                    throws SQLException, InvalidKeyException, QueryUpdateServiceException, BatchValidationException, DuplicateKeyException
            {
                List<Map<String, Object>> newRows = new ArrayList<>();
                List<Map<String, Object>> oldKeys = new ArrayList<>();
                for (Map<String, Object> row : rows)
                {
                    // issue 13719: use CaseInsensitiveHashMaps.  Also allow either values or oldKeys to be null.
                    // this should never happen on an update, but we will let it fail later with a better error message instead of the NPE here
                    CaseInsensitiveHashMap newMap = row.get(SaveRowsAction.PROP_VALUES) != null ? new CaseInsensitiveHashMap((Map<String, Object>)row.get(SaveRowsAction.PROP_VALUES)) : new CaseInsensitiveHashMap();
                    newRows.add(newMap);

                    CaseInsensitiveHashMap oldMap = row.get(SaveRowsAction.PROP_OLD_KEYS) != null ? new CaseInsensitiveHashMap((Map<String, Object>)row.get(SaveRowsAction.PROP_OLD_KEYS)) : new CaseInsensitiveHashMap();
                    oldKeys.add(oldMap);
                }
                List<Map<String, Object>> updatedRows = qus.updateRows(user, container, newRows, oldKeys, null, extraContext);
                updatedRows = qus.getRows(user, container, updatedRows);
                List<Map<String, Object>> results = new ArrayList<>();
                for (int i = 0; i < updatedRows.size(); i++)
                {
                    Map<String, Object> result = new HashMap<>();
                    result.put(SaveRowsAction.PROP_VALUES, updatedRows.get(i));
                    result.put(SaveRowsAction.PROP_OLD_KEYS, oldKeys.get(i));
                    results.add(result);
                }
                return results;
            }
        },
        delete(DeletePermission.class)
        {
            @Override
            public List<Map<String, Object>> saveRows(QueryUpdateService qus, List<Map<String, Object>> rows, User user, Container container, Map<String, Object> extraContext)
                    throws SQLException, InvalidKeyException, QueryUpdateServiceException, BatchValidationException, DuplicateKeyException
            {
                return qus.deleteRows(user, container, rows, null, extraContext);
            }
        };

        private final Class<? extends Permission> _permission;

        private CommandType(Class<? extends Permission> permission)
        {
            _permission = permission;
        }

        public Class<? extends Permission> getPermission()
        {
            return _permission;
        }

        public abstract List<Map<String, Object>> saveRows(QueryUpdateService qus, List<Map<String, Object>> rows, User user, Container container, Map<String, Object> extraContext)
                throws SQLException, InvalidKeyException, QueryUpdateServiceException, BatchValidationException, DuplicateKeyException;
    }

    /**
     * Base action class for insert/update/delete actions
     */
    public abstract class BaseSaveRowsAction extends ApiAction<ApiSaveRowsForm>
    {
        public static final String PROP_SCHEMA_NAME = "schemaName";
        public static final String PROP_QUERY_NAME = "queryName";
        public static final String PROP_COMMAND = "command";
        private static final String PROP_ROWS = "rows";

        protected JSONObject executeJson(JSONObject json, CommandType commandType, boolean allowTransaction, Errors errors) throws IOException, BatchValidationException, SQLException, DuplicateKeyException, InvalidKeyException, QueryUpdateServiceException
        {
            JSONObject response = new JSONObject();
            Container container = getContainer();
            User user = getUser();

            if (json == null)
                throw new IllegalArgumentException("Empty request");

            JSONArray rows;
            try
            {
                rows = json.getJSONArray(PROP_ROWS);
                if (rows.length() < 1)
                    throw new IllegalArgumentException("No '"+PROP_ROWS+"' array supplied!");
            }
            catch (JSONException x)
            {
                throw new IllegalArgumentException("No '"+PROP_ROWS+"' array supplied!");
            }

            String schemaName = json.getString(PROP_SCHEMA_NAME);
            String queryName = json.getString(PROP_QUERY_NAME);
            TableInfo table = getTableInfo(container, user, schemaName, queryName);

            if (!table.hasPermission(user, commandType.getPermission()))
                throw new UnauthorizedException();

            if (commandType != CommandType.insert && table.getPkColumns().size() == 0)
                throw new IllegalArgumentException("The table '" + table.getPublicSchemaName() + "." +
                        table.getPublicName() + "' cannot be updated because it has no primary key defined!");

            QueryUpdateService qus = table.getUpdateService();
            if (null == qus)
                throw new IllegalArgumentException("The query '" + queryName + "' in the schema '" + schemaName +
                        "' is not updatable via the HTTP-based APIs.");

            int rowsAffected = 0;

            List<Map<String, Object>> rowsToProcess = new ArrayList<>();

            // NOTE RowMapFactory is faster, but for update it's important to preserve missing v explicit NULL values
            // Do we need to support some soft of UNDEFINED and NULL instance of MvFieldWrapper?
            RowMapFactory<Object> f = null;
            if (commandType == CommandType.insert || commandType == CommandType.insertWithKeys)
                f = new RowMapFactory<>();

            for (int idx = 0; idx < rows.length(); ++idx)
            {
                JSONObject jsonObj;
                try
                {
                    jsonObj = rows.getJSONObject(idx);
                }
                catch (JSONException x)
                {
                    throw new IllegalArgumentException("rows[" + idx + "] is not an object.");
                }
                if (null != jsonObj)
                {
                    Map<String, Object> rowMap = null == f ? new CaseInsensitiveHashMap<>() : f.getRowMap();
                    rowMap.putAll(jsonObj);
                    rowsToProcess.add(rowMap);
                    rowsAffected++;
                }
            }

            Map<String, Object> extraContext = json.optJSONObject("extraContext");
            if (extraContext == null)
                extraContext = new CaseInsensitiveHashMap<>();

            //setup the response, providing the schema name, query name, and operation
            //so that the client can sort out which request this response belongs to
            //(clients often submit these async)
            response.put(PROP_SCHEMA_NAME, schemaName);
            response.put(PROP_QUERY_NAME, queryName);
            response.put("command", commandType.name());
            response.put("containerPath", container.getPath());

            //we will transact operations by default, but the user may
            //override this by sending a "transacted" property set to false
            // 11741: A transaction may already be active if we're trying to
            // insert/update/delete from within a transformation/validation script.
            boolean transacted = allowTransaction && json.optBoolean("transacted", true);

            try (DbScope.Transaction transaction = transacted ? table.getSchema().getScope().ensureTransaction() : NO_OP_TRANSACTION)
            {
                List<Map<String, Object>> responseRows =
                        commandType.saveRows(qus, rowsToProcess, getUser(), getContainer(), extraContext);

                if (commandType != CommandType.importRows)
                    response.put("rows", responseRows);

                transaction.commit();
            }
            catch (OptimisticConflictException e)
            {
                //issue 13967: provide better message for OptimisticConflictException
                errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
            }
            catch (ConversionException | DuplicateKeyException | DataIntegrityViolationException e)
            {
                //Issue 14294: improve handling of ConversionException (and DuplicateKeyException (Issue 28037), and DataIntegrity (uniqueness) (Issue 22779)
                errors.reject(SpringActionController.ERROR_MSG, e.getMessage() == null ? e.toString() : e.getMessage());
            }
            catch (BatchValidationException e)
            {
                if (isSuccessOnValidationError())
                {
                    response.put("errors", createResponseWriter().getJSON(e));
                }
                else
                {
                    ExceptionUtil.decorateException(e, ExceptionUtil.ExceptionInfo.SkipMothershipLogging, "true", true);
                    throw e;
                }
            }

            response.put("rowsAffected", rowsAffected);

            return response;
        }

        protected boolean isSuccessOnValidationError()
        {
            return getRequestedApiVersion() >= 13.2;
        }

        @NotNull
        protected TableInfo getTableInfo(Container container, User user, String schemaName, String queryName)
        {
            if (null == schemaName || null == queryName)
                throw new IllegalArgumentException("You must supply a schemaName and queryName!");

            UserSchema schema = QueryService.get().getUserSchema(user, container, schemaName);
            if (null == schema)
                throw new IllegalArgumentException("The schema '" + schemaName + "' does not exist.");

            TableInfo table = schema.getTable(queryName);
            if (table == null)
                throw new IllegalArgumentException("The query '" + queryName + "' in the schema '" + schemaName + "' does not exist.");
            return table;
        }
    }

    // Issue: 20522 - require read access to the action but executeJson will check for update privileges from the table
    //
    @RequiresPermission(ReadPermission.class) //will check below
    @ApiVersion(8.3)
    public class UpdateRowsAction extends BaseSaveRowsAction
    {
        @Override
        public ApiResponse execute(ApiSaveRowsForm apiSaveRowsForm, BindException errors) throws Exception
        {
            JSONObject response = executeJson(apiSaveRowsForm.getJsonObject(), CommandType.update, true, errors);
            if (response == null || errors.hasErrors())
                return null;
            return new ApiSimpleResponse(response);
        }
    }

    @RequiresPermission(ReadPermission.class) //will check below
    @ApiVersion(8.3)
    public class InsertRowsAction extends BaseSaveRowsAction
    {
        @Override
        public ApiResponse execute(ApiSaveRowsForm apiSaveRowsForm, BindException errors) throws Exception
        {
            JSONObject response = executeJson(apiSaveRowsForm.getJsonObject(), CommandType.insert, true, errors);
            if (response == null || errors.hasErrors())
                return null;
            return new ApiSimpleResponse(response);
        }
    }

    @RequiresPermission(ReadPermission.class) //will check below
    @ApiVersion(8.3)
    public class ImportRowsAction extends BaseSaveRowsAction
    {
        @Override
        public ApiResponse execute(ApiSaveRowsForm apiSaveRowsForm, BindException errors) throws Exception
        {
            JSONObject response = executeJson(apiSaveRowsForm.getJsonObject(), CommandType.importRows, true, errors);
            if (response == null || errors.hasErrors())
                return null;
            return new ApiSimpleResponse(response);
        }
    }

    @ActionNames("deleteRows, delRows")
    @RequiresPermission(ReadPermission.class) //will check below
    @ApiVersion(8.3)
    public class DeleteRowsAction extends BaseSaveRowsAction
    {
        @Override
        public ApiResponse execute(ApiSaveRowsForm apiSaveRowsForm, BindException errors) throws Exception
        {
            JSONObject response = executeJson(apiSaveRowsForm.getJsonObject(), CommandType.delete, true, errors);
            if (response == null || errors.hasErrors())
                return null;
            return new ApiSimpleResponse(response);
        }
    }

    @RequiresNoPermission //will check below
    public class SaveRowsAction extends BaseSaveRowsAction
    {
        public static final String PROP_VALUES = "values";
        public static final String PROP_OLD_KEYS = "oldKeys";

        @Override
        protected boolean isFailure(BindException errors)
        {
            return !isSuccessOnValidationError() && super.isFailure(errors);
        }

        @Override
        public ApiResponse execute(ApiSaveRowsForm apiSaveRowsForm, BindException errors) throws Exception
        {
            // Issue 21850: Verify that the user has at least some sort of basic access to the container. We'll check for more
            // specific permissions later once we've figured out exactly what they're trying to do. This helps us
            // give a better HTTP response code when they're trying to access a resource that's not available to guests
            if (!getContainer().hasPermission(getUser(), ReadPermission.class) &&
                    !getContainer().hasPermission(getUser(), DeletePermission.class) &&
                    !getContainer().hasPermission(getUser(), InsertPermission.class) &&
                    !getContainer().hasPermission(getUser(), UpdatePermission.class))
            {
                throw new UnauthorizedException();
            }

            JSONObject json = apiSaveRowsForm.getJsonObject();
            if (json == null)
                throw new IllegalArgumentException("Empty request");

            JSONArray commands = (JSONArray)json.get("commands");
            JSONArray resultArray = new JSONArray();
            if (commands == null || commands.length() == 0)
            {
                throw new NotFoundException("Empty request");
            }

            Map<String, Object> extraContext = json.optJSONObject("extraContext");

            boolean validateOnly = json.optBoolean("validateOnly", false);
            // If we are going to validate and not commit, we need to be sure we're transacted as well. Otherwise,
            // respect the client's request.
            boolean transacted = validateOnly || json.optBoolean("transacted", true);

            // Keep track of whether we end up committing or not
            boolean committed = false;

            DbScope scope = null;
            if (transacted)
            {
                for (int i = 0; i < commands.length(); i++)
                {
                    JSONObject commandJSON = commands.getJSONObject(i);
                    String schemaName = commandJSON.getString(PROP_SCHEMA_NAME);
                    String queryName = commandJSON.getString(PROP_QUERY_NAME);
                    TableInfo tableInfo = getTableInfo(getContainer(), getUser(), schemaName, queryName);
                    if (scope == null)
                    {
                        scope = tableInfo.getSchema().getScope();
                    }
                    else if (scope != tableInfo.getSchema().getScope())
                    {
                        throw new IllegalArgumentException("All queries must be from the same source database");
                    }
                }
            }

            int startingErrorIndex = 0;
            int errorCount = 0;
            // 11741: A transaction may already be active if we're trying to
            // insert/update/delete from within a transformation/validation script.

            try (DbScope.Transaction transaction = transacted ? scope.ensureTransaction() : NO_OP_TRANSACTION)
            {
                for (int i = 0; i < commands.length(); i++)
                {
                    JSONObject commandObject = commands.getJSONObject(i);
                    String commandName = commandObject.getString(PROP_COMMAND);
                    if (commandName == null)
                    {
                        throw new ApiUsageException(PROP_COMMAND + " is required but was missing");
                    }
                    CommandType command = CommandType.valueOf(commandName);

                    // Copy the top-level 'extraContext' and merge in the command-level extraContext.
                    Map<String, Object> commandExtraContext = new HashMap<>();
                    if (extraContext != null)
                        commandExtraContext.putAll(extraContext);
                    if (commandObject.has("extraContext"))
                    {
                        commandExtraContext.putAll(commandObject.getJSONObject("extraContext"));
                    }
                    commandObject.put("extraContext", commandExtraContext);

                    JSONObject commandResponse = executeJson(commandObject, command, !transacted, errors);
                    // Bail out immediately if we're going to return a failure-type response message
                    if (commandResponse == null || (errors.hasErrors() && !isSuccessOnValidationError()))
                        return null;

                    //this would be populated in executeJson when a BatchValidationException is thrown
                    if (commandResponse.containsKey("errors"))
                    {
                        errorCount += commandResponse.getJSONObject("errors").getInt("errorCount");
                    }

                    // If we encountered errors with this particular command and the client requested that don't treat
                    // the whole request as a failure (non-200 HTTP status code), stash the errors for this particular
                    // command in its response section.
                    // NOTE: executeJson should handle and serialize BatchValidationException
                    // these errors upstream
                    if (errors.getErrorCount() > startingErrorIndex && isSuccessOnValidationError())
                    {
                        commandResponse.put("errors", ApiResponseWriter.convertToJSON(errors, startingErrorIndex).getValue());
                        startingErrorIndex = errors.getErrorCount();
                    }

                    resultArray.put(commandResponse);
                }

                // Don't commit if we had errors or if the client requested that we only validate (and not commit)
                if (!errors.hasErrors() && !validateOnly && errorCount == 0)
                {
                    transaction.commit();
                    committed = true;
                }
            }

            errorCount += errors.getErrorCount();
            JSONObject result = new JSONObject();
            result.put("result", resultArray);
            result.put("committed", committed);
            result.put("errorCount", errorCount);
            return new ApiSimpleResponse(result);
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ApiTestAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView("/org/labkey/query/view/apitest.jsp");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("API Test");
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class AdminAction extends SimpleViewAction<QueryForm>
    {
        @SuppressWarnings("UnusedDeclaration")
        public AdminAction()
        {
        }

        public AdminAction(ViewContext ctx)
        {
            setViewContext(ctx);
        }

        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
           setHelpTopic(new HelpTopic("externalSchemas"));
           return new JspView<>("/org/labkey/query/view/admin.jsp", form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new BeginAction(getViewContext()).appendNavTrail(root);
            root.addChild("Schema Administration", new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer()));
            return root;
        }
    }


    public static class ResetRemoteConnectionsForm
    {
        private boolean _reset;

        public boolean isReset()
        {
            return _reset;
        }

        public void setReset(boolean reset)
        {
            _reset = reset;
        }
    }

    @CSRF
    @RequiresPermission(AdminPermission.class)
    public class ManageRemoteConnectionsAction extends FormViewAction<ResetRemoteConnectionsForm>
    {
        @Override
        public void validateCommand(ResetRemoteConnectionsForm target, Errors errors) {}

        @Override
        public boolean handlePost(ResetRemoteConnectionsForm form, BindException errors) throws Exception
        {
            if (form.isReset())
            {
                PropertyManager.getEncryptedStore().deletePropertySet(getContainer(), RemoteConnections.REMOTE_QUERY_CONNECTIONS_CATEGORY);
            }
            return true;
        }

        @Override
        public URLHelper getSuccessURL(ResetRemoteConnectionsForm queryForm)
        {
            return new ActionURL(ManageRemoteConnectionsAction.class, getContainer());
        }

        @Override
        public ModelAndView getView(ResetRemoteConnectionsForm queryForm, boolean reshow, BindException errors) throws Exception
        {
            Map<String, String> connectionMap;
            try
            {
                // if the encrypted property store is configured but no values have yet been set, and empty map is returned
                connectionMap = PropertyManager.getEncryptedStore().getProperties(getContainer(), RemoteConnections.REMOTE_QUERY_CONNECTIONS_CATEGORY);
            }
            catch (Exception e)
            {
                connectionMap = null; // render the failure page
            }
            getPageConfig().setHelpTopic(new HelpTopic("remoteConnection"));
            return new JspView<>("/org/labkey/query/view/manageRemoteConnections.jsp", connectionMap, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new BeginAction(getViewContext()).appendNavTrail(root);
            root.addChild("Manage Remote Connections", new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer()));
            return root;
        }
    }

    private abstract class BaseInsertExternalSchemaAction<F extends AbstractExternalSchemaForm<T>, T extends AbstractExternalSchemaDef> extends FormViewAction<F>
    {
        protected BaseInsertExternalSchemaAction(Class<F> commandClass)
        {
            super(commandClass);
        }

        public void validateCommand(F form, Errors errors)
        {
            form.validate(errors);
        }

        public boolean handlePost(F form, BindException errors) throws Exception
        {
            try
            {
                form.doInsert();
                QueryManager.get().updateExternalSchemas(getContainer());
            }
            catch (RuntimeSQLException e)
            {
                if (e.isConstraintException())
                {
                    errors.reject(ERROR_MSG, "A schema by that name is already defined in this folder");
                    return false;
                }

                throw e;
            }

            return true;
        }

        public ActionURL getSuccessURL(F form)
        {
            return new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new AdminAction(getViewContext()).appendNavTrail(root);
            root.addChild("Define Schema", new ActionURL(getClass(), getContainer()));
            return root;
        }

    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class InsertLinkedSchemaAction extends BaseInsertExternalSchemaAction<LinkedSchemaForm, LinkedSchemaDef>
    {
        public InsertLinkedSchemaAction()
        {
            super(LinkedSchemaForm.class);
        }

        public ModelAndView getView(LinkedSchemaForm form, boolean reshow, BindException errors) throws Exception
        {
            setHelpTopic(new HelpTopic("filterSchema"));
            return new JspView<>("/org/labkey/query/view/linkedSchema.jsp", new LinkedSchemaBean(getContainer(), form.getBean(), true), errors);
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class InsertExternalSchemaAction extends BaseInsertExternalSchemaAction<ExternalSchemaForm, ExternalSchemaDef>
    {
        public InsertExternalSchemaAction()
        {
            super(ExternalSchemaForm.class);
        }

        public ModelAndView getView(ExternalSchemaForm form, boolean reshow, BindException errors) throws Exception
        {
            setHelpTopic(new HelpTopic("externalSchemas"));
            return new JspView<>("/org/labkey/query/view/externalSchema.jsp", new ExternalSchemaBean(getContainer(), form.getBean(), true), errors);
        }
    }

    private abstract class BaseDeleteSchemaAction<F extends AbstractExternalSchemaForm<T>, T extends AbstractExternalSchemaDef> extends ConfirmAction<F>
    {
        protected BaseDeleteSchemaAction(Class<F> commandClass)
        {
            super(commandClass);
        }

        public String getConfirmText()
        {
            return "Delete";
        }

        public ModelAndView getConfirmView(F form, BindException errors) throws Exception
        {
            form.refreshFromDb();
            return new HtmlView("Are you sure you want to delete the schema '" + form.getBean().getUserSchemaName() + "'? The tables and queries defined in this schema will no longer be accessible.");
        }

        public boolean handlePost(F form, BindException errors) throws Exception
        {
            delete(form);
            return true;
        }

        protected abstract void delete(F form) throws Exception;

        public void validateCommand(F form, Errors errors)
        {
        }

        public ActionURL getSuccessURL(F form)
        {
            return new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer());
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class DeleteLinkedSchemaAction extends BaseDeleteSchemaAction<LinkedSchemaForm, LinkedSchemaDef>
    {
        public DeleteLinkedSchemaAction()
        {
            super(LinkedSchemaForm.class);
        }

        protected void delete(LinkedSchemaForm form) throws Exception
        {
            form.refreshFromDb();
            QueryManager.get().delete(form.getBean());
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class DeleteExternalSchemaAction extends BaseDeleteSchemaAction<ExternalSchemaForm, ExternalSchemaDef>
    {
        public DeleteExternalSchemaAction()
        {
            super(ExternalSchemaForm.class);
        }

        protected void delete(ExternalSchemaForm form) throws Exception
        {
            form.refreshFromDb();
            QueryManager.get().delete(form.getBean());
        }
    }

    private abstract class BaseEditSchemaAction<F extends AbstractExternalSchemaForm<T>, T extends AbstractExternalSchemaDef> extends FormViewAction<F>
    {
        protected BaseEditSchemaAction(Class<F> commandClass)
        {
            super(commandClass);
        }

        public void validateCommand(F form, Errors errors)
        {
            form.validate(errors);
        }

        protected abstract T getCurrent(int externalSchemaId);

        protected T getDef(F form, boolean reshow, BindException errors) throws Exception
        {
            T def;
            Container defContainer;

            if (reshow)
            {
                def = form.getBean();
                T current = getCurrent(def.getExternalSchemaId());
                defContainer = current.lookupContainer();
            }
            else
            {
                form.refreshFromDb();
                def = form.getBean();
                defContainer = def.lookupContainer();
            }

            if (!getContainer().equals(defContainer))
                throw new UnauthorizedException();

            return def;
        }

        public boolean handlePost(F form, BindException errors) throws Exception
        {
            T def = form.getBean();
            T fromDb = getCurrent(def.getExternalSchemaId());

            // Unauthorized if def in the database reports a different container
            if (!getContainer().equals(fromDb.lookupContainer()))
                throw new UnauthorizedException();

            try
            {
                form.doUpdate();
                QueryManager.get().updateExternalSchemas(getContainer());
            }
            catch (RuntimeSQLException e)
            {
                if (e.isConstraintException())
                {
                    errors.reject(ERROR_MSG, "A schema by that name is already defined in this folder");
                    return false;
                }

                throw e;
            }
            return true;
        }

        public ActionURL getSuccessURL(F externalSchemaForm)
        {
            return new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new AdminAction(getViewContext()).appendNavTrail(root);
            root.addChild("Edit Schema", new ActionURL(getClass(), getContainer()));
            return root;
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class EditLinkedSchemaAction extends BaseEditSchemaAction<LinkedSchemaForm, LinkedSchemaDef>
    {
        public EditLinkedSchemaAction()
        {
            super(LinkedSchemaForm.class);
        }

        protected LinkedSchemaDef getCurrent(int externalId)
        {
            return QueryManager.get().getLinkedSchemaDef(getContainer(), externalId);
        }

        public ModelAndView getView(LinkedSchemaForm form, boolean reshow, BindException errors) throws Exception
        {
            LinkedSchemaDef def = getDef(form, reshow, errors);

            setHelpTopic(new HelpTopic("linkedSchemas"));
            return new JspView<>("/org/labkey/query/view/linkedSchema.jsp", new LinkedSchemaBean(getContainer(), def, false), errors);
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class EditExternalSchemaAction extends BaseEditSchemaAction<ExternalSchemaForm, ExternalSchemaDef>
    {
        public EditExternalSchemaAction()
        {
            super(ExternalSchemaForm.class);
        }

        protected ExternalSchemaDef getCurrent(int externalId)
        {
            return QueryManager.get().getExternalSchemaDef(getContainer(), externalId);
        }

        public ModelAndView getView(ExternalSchemaForm form, boolean reshow, BindException errors) throws Exception
        {
            ExternalSchemaDef def = getDef(form, reshow, errors);

            setHelpTopic(new HelpTopic("externalSchemas"));
            return new JspView<>("/org/labkey/query/view/externalSchema.jsp", new ExternalSchemaBean(getContainer(), def, false), errors);
        }
    }


    public static class DataSourceInfo
    {
        public final String sourceName;
        public final String displayName;
        public final boolean editable;

        public DataSourceInfo(DbScope scope)
        {
            this(scope.getDataSourceName(), scope.getDisplayName(), scope.getSqlDialect().isEditable());
        }

        public DataSourceInfo(Container c)
        {
            this(c.getId(), c.getName(), false);
        }

        public DataSourceInfo(String sourceName, String displayName, boolean editable)
        {
            this.sourceName = sourceName;
            this.displayName = displayName;
            this.editable = editable;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            DataSourceInfo that = (DataSourceInfo) o;
            if (sourceName != null ? !sourceName.equals(that.sourceName) : that.sourceName != null) return false;
            return true;
        }

        @Override
        public int hashCode()
        {
            return sourceName != null ? sourceName.hashCode() : 0;
        }
    }

    public static abstract class BaseExternalSchemaBean<T extends AbstractExternalSchemaDef>
    {
        protected final Container _c;
        protected final T _def;
        protected final boolean _insert;
        protected final Map<String, String> _help = new HashMap<>();

        public BaseExternalSchemaBean(Container c, T def, boolean insert)
        {
            _c = c;
            _def = def;
            _insert = insert;

            TableInfo ti = QueryManager.get().getTableInfoExternalSchema();

            ti.getColumns()
                .stream()
                .filter(ci -> null != ci.getDescription())
                .forEach(ci -> _help.put(ci.getName(), ci.getDescription()));
        }

        public abstract DataSourceInfo getInitialSource();

        public T getSchemaDef()
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
            return new QueryUrlsImpl().urlDeleteExternalSchema(_c, _def);
        }

        public String getHelpHTML(String fieldName)
        {
            return _help.get(fieldName);
        }

    }

    public static class LinkedSchemaBean extends BaseExternalSchemaBean<LinkedSchemaDef>
    {
        public LinkedSchemaBean(Container c, LinkedSchemaDef def, boolean insert)
        {
            super(c, def, insert);
        }

        public DataSourceInfo getInitialSource()
        {
            Container sourceContainer = getInitialContainer();
            return new DataSourceInfo(sourceContainer);
        }

        private @NotNull Container getInitialContainer()
        {
            LinkedSchemaDef def = getSchemaDef();
            Container sourceContainer = def.lookupSourceContainer();
            if (sourceContainer == null)
                sourceContainer = def.lookupContainer();
            if (sourceContainer == null)
                sourceContainer = _c;
            return sourceContainer;
        }
    }

    public static class ExternalSchemaBean extends BaseExternalSchemaBean<ExternalSchemaDef>
    {
        protected final Map<DataSourceInfo, Collection<String>> _sourcesAndSchemas = new LinkedHashMap<>();
        protected final Map<DataSourceInfo, Collection<String>> _sourcesAndSchemasIncludingSystem = new LinkedHashMap<>();

        public ExternalSchemaBean(Container c, ExternalSchemaDef def, boolean insert)
        {
            super(c, def, insert);
            initSources();
        }

        public Collection<DataSourceInfo> getSources()
        {
            return _sourcesAndSchemas.keySet();
        }

        public Collection<String> getSchemaNames(DataSourceInfo source, boolean includeSystem)
        {
            if (includeSystem)
                return _sourcesAndSchemasIncludingSystem.get(source);
            else
                return _sourcesAndSchemas.get(source);
        }

        @Override
        public DataSourceInfo getInitialSource()
        {
            ExternalSchemaDef def = getSchemaDef();
            DbScope scope = def.lookupDbScope();
            if (scope == null)
                scope = DbScope.getLabKeyScope();
            return new DataSourceInfo(scope);
        }

        protected void initSources()
        {
            ModuleLoader moduleLoader = ModuleLoader.getInstance();

            for (DbScope scope : DbScope.getDbScopes())
            {
                SqlDialect dialect = scope.getSqlDialect();

                Collection<String> schemaNames = new LinkedList<>();
                Collection<String> schemaNamesIncludingSystem = new LinkedList<>();

                for (String schemaName : scope.getSchemaNames())
                {
                    schemaNamesIncludingSystem.add(schemaName);

                    if (dialect.isSystemSchema(schemaName))
                        continue;

                    if (null != moduleLoader.getModuleForSchemaName(DbSchema.getDisplayName(scope, schemaName)))
                        continue;

                    schemaNames.add(schemaName);
                }

                DataSourceInfo source = new DataSourceInfo(scope);
                _sourcesAndSchemas.put(source, schemaNames);
                _sourcesAndSchemasIncludingSystem.put(source, schemaNamesIncludingSystem);
            }
        }
    }


    public static class GetTablesForm
    {
        private String _dataSource;
        private String _schemaName;
        private boolean _sorted;

        public String getDataSource()
        {
            return _dataSource;
        }

        public void setDataSource(String dataSource)
        {
            _dataSource = dataSource;
        }

        public String getSchemaName()
        {
            return _schemaName;
        }

        public void setSchemaName(String schemaName)
        {
            _schemaName = schemaName;
        }

        public boolean isSorted()
        {
            return _sorted;
        }

        public void setSorted(boolean sorted)
        {
            _sorted = sorted;
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class GetTablesAction extends ApiAction<GetTablesForm>
    {
        @Override
        public ApiResponse execute(GetTablesForm form, BindException errors) throws Exception
        {
            List<Map<String, Object>> rows = new LinkedList<>();
            List<String> tableNames = new ArrayList<>();

            if (null != form.getSchemaName())
            {
                DbScope scope = DbScope.getDbScope(form.getDataSource());
                if (null != scope)
                {
                    DbSchema schema = scope.getSchema(form.getSchemaName(), DbSchemaType.Bare);
                    tableNames.addAll(schema.getTableNames());
                }
                else
                {
                    Container c = ContainerManager.getForId(form.getDataSource());
                    if (null != c)
                    {
                        UserSchema schema = QueryService.get().getUserSchema(getUser(), c, form.getSchemaName());
                        if (null != schema)
                        {
                            if (form.isSorted())
                                for (TableInfo table : schema.getSortedTables())
                                    tableNames.add(table.getName());
                            else
                                tableNames.addAll(schema.getTableAndQueryNames(true));
                        }
                    }
                }
            }

            Collections.sort(tableNames);

            for (String tableName : tableNames)
            {
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("table", tableName);
                rows.add(row);
            }

            Map<String, Object> properties = new HashMap<>();
            properties.put("rows", rows);

            return new ApiSimpleResponse(properties);
        }
    }


    public static class SchemaTemplateForm
    {
        private String _name;

        public String getName()
        {
            return _name;
        }

        public void setName(String name)
        {
            _name = name;
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class SchemaTemplateAction extends ApiAction<SchemaTemplateForm>
    {
        @Override
        public ApiResponse execute(SchemaTemplateForm form, BindException errors) throws Exception
        {
            String name = form.getName();
            if (name == null)
                throw new IllegalArgumentException("name required");

            Container c = getContainer();
            TemplateSchemaType template = QueryServiceImpl.get().getSchemaTemplate(c, name);
            if (template == null)
                throw new NotFoundException("template not found");

            JSONObject templateJson = QueryServiceImpl.get().schemaTemplateJson(name, template);

            return new ApiSimpleResponse("template", templateJson);
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class SchemaTemplatesAction extends ApiAction
    {
        @Override
        public ApiResponse execute(Object form, BindException errors) throws Exception
        {
            Container c = getContainer();
            QueryServiceImpl svc = QueryServiceImpl.get();
            Map<String, TemplateSchemaType> templates = svc.getSchemaTemplates(c);

            JSONArray ret = new JSONArray();
            for (String key : templates.keySet())
            {
                TemplateSchemaType template = templates.get(key);
                JSONObject templateJson = svc.schemaTemplateJson(key, template);
                ret.put(templateJson);
            }

            ApiSimpleResponse resp = new ApiSimpleResponse();
            resp.put("templates", ret);
            resp.put("success", true);
            return resp;
        }
    }



    // UNDONE: should use POST, change to FormHandlerAction
    @RequiresPermission(AdminPermission.class)
    public class ReloadExternalSchemaAction extends SimpleViewAction<ExternalSchemaForm>
    {
        public ModelAndView getView(ExternalSchemaForm form, BindException errors) throws Exception
        {
            form.refreshFromDb();
            ExternalSchemaDef def = form.getBean();

            try
            {
                QueryManager.get().reloadExternalSchema(def);
            }
            catch (Exception e)
            {
                errors.reject(ERROR_MSG, "Could not reload schema " + def.getUserSchemaName() + ". The data source for the schema may be unreachable, or the schema may have been deleted.");
                getPageConfig().setTemplate(PageConfig.Template.Dialog);
                return new SimpleErrorView(errors);
            }

            return HttpView.redirect(getSuccessURL(form));
        }

        public ActionURL getSuccessURL(ExternalSchemaForm form)
        {
            return new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer(), form.getBean().getUserSchemaName());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class ReloadAllUserSchemas extends SimpleRedirectAction
    {
        @Override
        public ActionURL getRedirectURL(Object o) throws Exception
        {
            QueryManager.get().reloadAllExternalSchemas(getContainer());
            return new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer(), "ALL");
        }
    }


    @RequiresPermission(ReadPermission.class)
    public class TableInfoAction extends SimpleViewAction<TableInfoForm>
    {
        public ModelAndView getView(TableInfoForm form, BindException errors) throws Exception
        {
            TablesDocument ret = TablesDocument.Factory.newInstance();
            TablesType tables = ret.addNewTables();

            FieldKey[] fields = form.getFieldKeys();
            if (fields.length != 0)
            {
                TableInfo tinfo = QueryView.create(form, errors).getTable();
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


    // Issue 18870: Guest user can't revert unsaved custom view changes
    // Permission will be checked inline (guests are allowed to delete their session custom views)
    @RequiresNoPermission
    @Action(ActionType.Configure.class)
    public class DeleteViewAction extends ApiAction<DeleteViewForm>
    {
        @Override
        protected ModelAndView handleGet() throws Exception
        {
            throw new UnauthorizedException();
        }

        public ApiResponse execute(DeleteViewForm form, BindException errors) throws Exception
        {
            CustomView view = form.getCustomView();
            if (view == null)
            {
                throw new NotFoundException();
            }

            if (getUser().isGuest())
            {
                // Guests can only delete session custom views.
                if (!view.isSession())
                    throw new UnauthorizedException();
            }
            else
            {
                // Logged in users must have read permission
                if (!getContainer().hasPermission(getUser(), ReadPermission.class))
                    throw new UnauthorizedException();
            }

            if (view.isShared())
            {
                if (!getContainer().hasPermission(getUser(), EditSharedViewPermission.class))
                    throw new UnauthorizedException();
            }

            view.delete(getUser(), getViewContext().getRequest());

            // Delete the first shadowed custom view, if available.
            if (form.isComplete())
            {
                form.reset();
                CustomView shadowed = form.getCustomView();
                if (shadowed != null && shadowed.isEditable() && !(shadowed instanceof ModuleCustomView))
                {
                    if (!shadowed.isShared() || getContainer().hasPermission(getUser(), EditSharedViewPermission.class))
                        shadowed.delete(getUser(), getViewContext().getRequest());
                }
            }

            // Try to get a custom view of the same name as the view we just deleted.
            // The deleted view may have been a session view or a personal view masking shared view with the same name.
            form.reset();
            view = form.getCustomView();
            String nextViewName = null;
            if (view != null)
                nextViewName = view.getName();

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("viewName", nextViewName);
            return response;
        }
    }

    public static class SaveSessionViewForm extends QueryForm
    {
        private String newName;
        private boolean inherit;
        private boolean shared;
        private String containerPath;

        public String getNewName()
        {
            return newName;
        }

        public void setNewName(String newName)
        {
            this.newName = newName;
        }

        public boolean isInherit()
        {
            return inherit;
        }

        public void setInherit(boolean inherit)
        {
            this.inherit = inherit;
        }

        public boolean isShared()
        {
            return shared;
        }

        public void setShared(boolean shared)
        {
            this.shared = shared;
        }

        public String getContainerPath()
        {
            return containerPath;
        }

        public void setContainerPath(String containerPath)
        {
            this.containerPath = containerPath;
        }
    }

    // Moves a session view into the database.
    @RequiresPermission(ReadPermission.class) @RequiresLogin
    public class SaveSessionViewAction extends ApiAction<SaveSessionViewForm>
    {
        @Override
        public ApiResponse execute(SaveSessionViewForm form, BindException errors) throws Exception
        {
            CustomView view = form.getCustomView();
            if (view == null)
            {
                throw new NotFoundException();
            }
            if (!view.isSession())
                throw new IllegalArgumentException("This action only supports saving session views.");

            //if (!getContainer().getId().equals(view.getContainer().getId()))
            //    throw new IllegalArgumentException("View may only be saved from container it was created in.");

            assert !view.canInherit() && !view.isShared() && view.isEditable(): "Session view should never be inheritable or shared and always be editable";

            // Users may save views to a location other than the current container
            String containerPath = form.getContainerPath();
            Container container;
            if (form.isInherit() && containerPath != null)
            {
                // Only respect this request if it's a view that is inheritable in subfolders
                container = ContainerManager.getForPath(containerPath);
            }
            else
            {
                // Otherwise, save it in the current container
                container = getContainer();
            }

            if (container == null)
                throw new NotFoundException("No such container: " + containerPath);

            if (form.isShared() || form.isInherit())
            {
                if (!container.hasPermission(getUser(), EditSharedViewPermission.class))
                    throw new UnauthorizedException();
            }

            DbScope scope = QueryManager.get().getDbSchema().getScope();
            try (DbScope.Transaction tx = scope.ensureTransaction())
            {
                // Delete the session view.  The view will be restored if an exception is thrown.
                view.delete(getUser(), getViewContext().getRequest());

                // Get any previously existing non-session view.
                // The session custom view and the view-to-be-saved may have different names.
                // If they do have different names, we may need to delete an existing session view with that name.
                // UNDONE: If the view has a different name, we will clobber it without asking.
                CustomView existingView = form.getQueryDef().getCustomView(getUser(), null, form.getNewName());
                if (existingView != null && existingView.isSession())
                {
                    // Delete any session view we are overwriting.
                    existingView.delete(getUser(), getViewContext().getRequest());
                    existingView = form.getQueryDef().getCustomView(getUser(), null, form.getNewName());
                }

                if (existingView == null)
                {
                    User owner = form.isShared() ? null : getUser();

                    CustomViewImpl viewCopy = new CustomViewImpl(form.getQueryDef(), owner, form.getNewName());
                    viewCopy.setColumns(view.getColumns());
                    viewCopy.setCanInherit(form.isInherit());
                    viewCopy.setFilterAndSort(view.getFilterAndSort());
                    viewCopy.setColumnProperties(view.getColumnProperties());
                    viewCopy.setIsHidden(view.isHidden());
                    if (form.isInherit())
                        viewCopy.setContainer(container);

                    viewCopy.save(getUser(), getViewContext().getRequest());
                }
                else if (!existingView.isEditable())
                {
                    throw new IllegalArgumentException("Existing view '" + form.getNewName() + "' is not editable.  You may save this view with a different name.");
                }
                else
                {
                    // UNDONE: changing shared property of an existing view is unimplemented.  Not sure if it makes sense from a usability point of view.
                    existingView.setColumns(view.getColumns());
                    existingView.setFilterAndSort(view.getFilterAndSort());
                    existingView.setColumnProperties(view.getColumnProperties());
                    existingView.setCanInherit(form.isInherit());
                    if (form.isInherit())
                        ((CustomViewImpl)existingView).setContainer(container);

                    existingView.save(getUser(), getViewContext().getRequest());
                }

                tx.commit();
                return new ApiSimpleResponse("success", true);
            }
            catch (Exception e)
            {
                // dirty the view then save the deleted session view back in session state
                view.setName(view.getName());
                view.save(getUser(), getViewContext().getRequest());
                
                throw e;
            }
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
                sql = PageFlowUtil.getReaderContentsAsString(getViewContext().getRequest().getReader());
            ErrorsDocument ret = ErrorsDocument.Factory.newInstance();
            org.labkey.query.design.Errors xbErrors = ret.addNewErrors();
            List<QueryParseException> errors = new ArrayList<>();
            try
            {
                (new SqlParser()).parseExpr(sql, errors);
            }
            catch (Throwable t)
            {
                Logger.getLogger(QueryController.class).error("Error", t);
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


    @RequiresPermission(AdminPermission.class)
    public class ManageViewsAction extends SimpleViewAction<QueryForm>
    {
        @SuppressWarnings("UnusedDeclaration")
        public ManageViewsAction()
        {
        }

        public ManageViewsAction(ViewContext ctx)
        {
            setViewContext(ctx);
        }

        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/query/view/manageViews.jsp", form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new BeginAction(getViewContext()).appendNavTrail(root);
            root.addChild("Manage Views", QueryController.this.getViewContext().getActionURL());
            return root;
        }
    }


    @RequiresPermission(AdminPermission.class)
    public class InternalDeleteView extends ConfirmAction<InternalViewForm>
    {
        public ModelAndView getConfirmView(InternalViewForm form, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/query/view/internalDeleteView.jsp", form, errors);
        }

        public boolean handlePost(InternalViewForm form, BindException errors) throws Exception
        {
            CstmView view = form.getViewAndCheckPermission();
            QueryManager.get().delete(view);
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


    @RequiresPermission(AdminPermission.class)
    public class InternalSourceViewAction extends FormViewAction<InternalSourceViewForm>
    {
        public void validateCommand(InternalSourceViewForm target, Errors errors)
        {
        }

        public ModelAndView getView(InternalSourceViewForm form, boolean reshow, BindException errors) throws Exception
        {
            CstmView view = form.getViewAndCheckPermission();
            form.ff_inherit = QueryManager.get().canInherit(view.getFlags());
            form.ff_hidden = QueryManager.get().isHidden(view.getFlags());
            form.ff_columnList = view.getColumns();
            form.ff_filter = view.getFilter();
            return new JspView<>("/org/labkey/query/view/internalSourceView.jsp", form, errors);
        }

        public boolean handlePost(InternalSourceViewForm form, BindException errors) throws Exception
        {
            CstmView view = form.getViewAndCheckPermission();
            int flags = view.getFlags();
            flags = QueryManager.get().setCanInherit(flags, form.ff_inherit);
            flags = QueryManager.get().setIsHidden(flags, form.ff_hidden);
            view.setFlags(flags);
            view.setColumns(form.ff_columnList);
            view.setFilter(form.ff_filter);
            QueryManager.get().update(getUser(), view);
            return true;
        }

        public ActionURL getSuccessURL(InternalSourceViewForm form)
        {
            return new ActionURL(ManageViewsAction.class, getContainer());
        }

        public NavTree appendNavTrail(NavTree root)
        {
            new ManageViewsAction(getViewContext()).appendNavTrail(root);
            root.addChild("Edit source of Grid View");
            return root;
        }
    }


    @RequiresPermission(AdminPermission.class)
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
            return new JspView<>("/org/labkey/query/view/internalNewView.jsp", form, errors);
        }

        public boolean handlePost(InternalNewViewForm form, BindException errors) throws Exception
        {
            if (form.ff_share)
            {
                if (!getContainer().hasPermission(getUser(), AdminPermission.class))
                    throw new UnauthorizedException();
            }
            List<CstmView> existing = QueryManager.get().getCstmViews(getContainer(), form.ff_schemaName, form.ff_queryName, form.ff_viewName, form.ff_share ? null : getUser(), false, false);
            CstmView view;
            if (!existing.isEmpty())
            {
                view = existing.get(0);
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
                    Logger.getLogger(QueryController.class).error("Error", e);
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
            root.addChild("Create New Grid View");
            return root;
        }
    }


    @ActionNames("clearSelected, selectNone")
    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectData.class)
    public static class SelectNoneAction extends ApiAction<SelectForm>
    {
        public SelectNoneAction()
        {
            super(SelectForm.class);
        }

        public ApiResponse execute(final SelectForm form, BindException errors) throws Exception
        {
            DataRegionSelection.clearAll(getViewContext(), form.getKey());
            return new DataRegionSelection.SelectionResponse(0);
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

    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectData.class)
    public static class SelectAllAction extends MutatingApiAction<QueryForm>
    {
        public void validateForm(QueryForm form, Errors errors)
        {
            if (form.getSchemaName().isEmpty() ||
                form.getQueryName() == null)
            {
                errors.reject(ERROR_MSG, "schemaName and queryName required");
            }
        }

        public ApiResponse execute(final QueryForm form, BindException errors) throws Exception
        {
            int count = DataRegionSelection.selectAll(form);
            return new DataRegionSelection.SelectionResponse(count);
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class GetSelectedAction extends ApiAction<SelectForm>
    {
        public GetSelectedAction()
        {
            super(SelectForm.class);
        }

        public ApiResponse execute(final SelectForm form, BindException errors) throws Exception
        {
            Set<String> selected = DataRegionSelection.getSelected(getViewContext(), form.getKey(), true, false);
            return new ApiSimpleResponse("selected", selected);
        }
    }

    @ActionNames("setSelected, setCheck")
    @RequiresPermission(ReadPermission.class)
    public static class SetCheckAction extends ApiAction<SetCheckForm>
    {
        public SetCheckAction()
        {
            super(SetCheckForm.class);
        }

        public ApiResponse execute(final SetCheckForm form, BindException errors) throws Exception
        {
            String[] ids = form.getId(getViewContext().getRequest());
            List<String> selection = new ArrayList<>();
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
            return new DataRegionSelection.SelectionResponse(count);
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

    public static String getMessage(SqlDialect d, SQLException x)
    {
        return x.getMessage();
    }

    public static class GetSchemasForm
    {
        private boolean _includeHidden = true;
        private SchemaKey _schemaName;

        public SchemaKey getSchemaName()
        {
            return _schemaName;
        }

        public void setSchemaName(SchemaKey schemaName)
        {
            _schemaName = schemaName;
        }

        public boolean isIncludeHidden()
        {
            return _includeHidden;
        }

        public void setIncludeHidden(boolean includeHidden)
        {
            _includeHidden = includeHidden;
        }
    }

    @RequiresPermission(ReadPermission.class)
    @ApiVersion(12.3)
    public class GetSchemasAction extends ApiAction<GetSchemasForm>
    {
        public ApiResponse execute(GetSchemasForm form, BindException errors) throws Exception
        {
            final Container container = getContainer();
            final User user = getUser();

            final boolean includeHidden = form.isIncludeHidden();
            if (getRequestedApiVersion() >= 9.3)
            {
                SimpleSchemaTreeVisitor visitor = new SimpleSchemaTreeVisitor<Void, JSONObject>(includeHidden)
                {
                    @Override
                    public Void visitUserSchema(UserSchema schema, Path path, JSONObject json)
                    {
                        JSONObject schemaProps = new JSONObject();

                        schemaProps.put("schemaName", schema.getName());
                        schemaProps.put("fullyQualifiedName", schema.getSchemaName());
                        schemaProps.put("description", schema.getDescription());
                        schemaProps.put("hidden", schema.isHidden());
                        NavTree tree = schema.getSchemaBrowserLinks(user);
                        if (tree != null && tree.hasChildren())
                            schemaProps.put("menu", tree.toJSON());

                        // Collect children schemas
                        JSONObject children = new JSONObject();
                        visit(schema.getSchemas(_includeHidden), path, children);
                        if (children.size() > 0)
                            schemaProps.put("schemas", children);

                        // Add node's schemaProps to the parent's json.
                        json.put(schema.getName(), schemaProps);
                        return null;
                    }
                };

                // By default, start from the root.
                QuerySchema schema;
                if (form.getSchemaName() != null)
                    schema = DefaultSchema.get(user, container, form.getSchemaName());
                else
                    schema = DefaultSchema.get(user, container);

                if (schema == null)
                {
                    throw new NotFoundException("Could not find schema: " + form.getSchemaName());
                }

                // Create the JSON response by visiting the schema children.  The parent schema information isn't included.
                JSONObject ret = new JSONObject();
                visitor.visitTop(schema.getSchemas(includeHidden), ret);

                ApiSimpleResponse resp = new ApiSimpleResponse();
                resp.putAll(ret);
                return resp;
            }
            else
            {
                return new ApiSimpleResponse("schemas", DefaultSchema.get(user, container).getUserSchemaPaths(includeHidden));
            }
        }
    }

    public static class GetQueriesForm
    {
        private String _schemaName;
        private boolean _includeUserQueries = true;
        private boolean _includeSystemQueries = true;
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

        public boolean isIncludeSystemQueries()
        {
            return _includeSystemQueries;
        }

        public void setIncludeSystemQueries(boolean includeSystemQueries)
        {
            _includeSystemQueries = includeSystemQueries;
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

    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectMetaData.class)
    public class GetQueriesAction extends ApiAction<GetQueriesForm>
    {
        public ApiResponse execute(GetQueriesForm form, BindException errors) throws Exception
        {
            if (null == StringUtils.trimToNull(form.getSchemaName()))
                throw new IllegalArgumentException("You must supply a value for the 'schemaName' parameter!");

            ApiSimpleResponse response = new ApiSimpleResponse();
            UserSchema uschema = QueryService.get().getUserSchema(getUser(), getContainer(), form.getSchemaName());
            if (null == uschema)
                throw new NotFoundException("The schema name '" + form.getSchemaName()
                        + "' was not found within the folder '" + getContainer().getPath() + "'");

            response.put("schemaName", form.getSchemaName());

            List<Map<String, Object>> qinfos = new ArrayList<>();

            //user-defined queries
            if (form.isIncludeUserQueries())
            {
                Map<String,QueryDefinition> queryDefMap = uschema.getQueryDefs();
                for (Map.Entry<String,QueryDefinition> entry : queryDefMap.entrySet())
                {
                    QueryDefinition qdef = entry.getValue();
                    if (!qdef.isTemporary())
                    {
                        ActionURL viewDataUrl = uschema.urlFor(QueryAction.executeQuery, qdef);
                        qinfos.add(getQueryProps(qdef, viewDataUrl, true, uschema, form.isIncludeColumns()));
                    }
                }
            }

            //built-in tables
            if (form.isIncludeSystemQueries())
            {
                for (String qname : uschema.getVisibleTableNames())
                {
                    // Go direct against the UserSchema instead of calling into QueryService, which takes a schema and
                    // query name as strings and therefore has to create new instances
                    QueryDefinition qdef = uschema.getQueryDefForTable(qname);
                    if (qdef != null)
                    {
                        ActionURL viewDataUrl = uschema.urlFor(QueryAction.executeQuery, qdef);
                        qinfos.add(getQueryProps(qdef, viewDataUrl, false, uschema, form.isIncludeColumns()));
                    }
                }
            }
            response.put("queries", qinfos);

            return response;
        }

        protected Map<String, Object> getQueryProps(QueryDefinition qdef, ActionURL viewDataUrl, boolean isUserDefined, UserSchema schema, boolean includeColumns)
        {
            Map<String, Object> qinfo = new HashMap<>();
            qinfo.put("name", qdef.getName());
            qinfo.put("hidden", qdef.isHidden());
            qinfo.put("snapshot", qdef.isSnapshot());
            qinfo.put("inherit", qdef.canInherit());
            qinfo.put("isUserDefined", isUserDefined);
            boolean canEdit = qdef.canEdit(getUser());
            qinfo.put("canEdit", canEdit);
            qinfo.put("canEditSharedViews", getContainer().hasPermission(getUser(), EditSharedViewPermission.class));
            qinfo.put("isMetadataOverrideable", canEdit); //for now, this is the same as canEdit(), but in the future we can support this for non-editable queries

            if (isUserDefined)
                qinfo.put("moduleName", qdef.getModuleName());
            boolean isInherited = qdef.canInherit() && !getContainer().equals(qdef.getDefinitionContainer());
            qinfo.put("isInherited", isInherited);
            if (isInherited)
                qinfo.put("containerPath", qdef.getDefinitionContainer().getPath());

            if (null != qdef.getDescription())
                qinfo.put("description", qdef.getDescription());
            if (viewDataUrl != null)
                qinfo.put("viewDataUrl", viewDataUrl);

            String title = qdef.getName();
            try
            {
                //get the table info if the user requested column info
                TableInfo table = qdef.getTable(schema, null, true);

                if (null != table)
                {
                    if (includeColumns)
                    {
                        //enumerate the columns
                        List<Map<String, Object>> cinfos = new ArrayList<>();
                        for(ColumnInfo col : table.getColumns())
                        {
                            Map<String, Object> cinfo = new HashMap<>();
                            cinfo.put("name", col.getName());
                            if (null != col.getLabel())
                                cinfo.put("caption", col.getLabel());
                            if (null != col.getShortLabel())
                                cinfo.put("shortCaption", col.getShortLabel());
                            if (null != col.getDescription())
                                cinfo.put("description", col.getDescription());

                            cinfos.add(cinfo);
                        }
                        if (cinfos.size() > 0)
                            qinfo.put("columns", cinfos);
                    }
                    if (table instanceof DatasetTable)
                        title = table.getTitle();
                }
            }
            catch(Exception e)
            {
                //may happen due to query failing parse
            }

            qinfo.put("title", title);
            return qinfo;
        }
    }

    public static class GetQueryViewsForm
    {
        private String _schemaName;
        private String _queryName;
        private String _viewName;
        private boolean _metadata;

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

        public String getViewName()
        {
            return _viewName;
        }

        public void setViewName(String viewName)
        {
            _viewName = viewName;
        }

        public boolean isMetadata()
        {
            return _metadata;
        }

        public void setMetadata(boolean metadata)
        {
            _metadata = metadata;
        }
    }

    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectMetaData.class)
    public class GetQueryViewsAction extends ApiAction<GetQueryViewsForm>
    {
        public ApiResponse execute(GetQueryViewsForm form, BindException errors) throws Exception
        {
            if (null == StringUtils.trimToNull(form.getSchemaName()))
                throw new IllegalArgumentException("You must pass a value for the 'schemaName' parameter!");
            if (null == StringUtils.trimToNull(form.getQueryName()))
                throw new IllegalArgumentException("You must pass a value for the 'queryName' parameter!");

            UserSchema schema = QueryService.get().getUserSchema(getUser(), getContainer(), form.getSchemaName());
            if (null == schema)
                throw new NotFoundException("The schema name '" + form.getSchemaName()
                        + "' was not found within the folder '" + getContainer().getPath() + "'");

            QueryDefinition querydef = QueryService.get().createQueryDefForTable(schema, form.getQueryName());
            if (null == querydef || querydef.getTable(null, true) == null)
                throw new NotFoundException("The query '" + form.getQueryName() + "' was not found within the '"
                        + form.getSchemaName() + "' schema in the container '"
                        + getContainer().getPath() + "'!");

            Map<String, CustomView> views = querydef.getCustomViews(getUser(), getViewContext().getRequest(), true, false);
            if (null == views)
                views = Collections.emptyMap();

            Map<FieldKey, Map<String, Object>> columnMetadata = new HashMap<>();

            List<Map<String, Object>> viewInfos = Collections.emptyList();
            if (getViewContext().getBindPropertyValues().contains("viewName"))
            {
                // Get info for a named view or the default view (null)
                String viewName = StringUtils.trimToNull(form.getViewName());
                CustomView view = views.get(viewName);
                if (view != null)
                {
                    viewInfos = Collections.singletonList(CustomViewUtil.toMap(view, getUser(), form.isMetadata()));
                }
                else if (viewName == null)
                {
                    // The default view was requested but it hasn't been customized yet. Create the 'default default' view.
                    viewInfos = Collections.singletonList(CustomViewUtil.toMap(getViewContext(), schema, form.getQueryName(), null, form.isMetadata(), true, columnMetadata));
                }
            }
            else
            {
                boolean foundDefault = false;
                viewInfos = new ArrayList<>(views.size());
                for (CustomView view : views.values())
                {
                    if (view.getName() == null)
                        foundDefault = true;
                    viewInfos.add(CustomViewUtil.toMap(view, getUser(), form.isMetadata()));
                }

                if (!foundDefault)
                {
                    // The default view hasn't been customized yet. Create the 'default default' view.
                    viewInfos.add(CustomViewUtil.toMap(getViewContext(), schema, form.getQueryName(), null, form.isMetadata(), true, columnMetadata));
                }
            }

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("schemaName", form.getSchemaName());
            response.put("queryName", form.getQueryName());
            response.put("views", viewInfos);

            return response;
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

    @RequiresPermission(ReadPermission.class)
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


    private abstract class ParseAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            List<QueryParseException> qpe = new ArrayList<>();
            String expr = getViewContext().getRequest().getParameter("q");
            ArrayList<String> html = new ArrayList<>();
            html.add("<form method=GET><textarea id=\"expression\" cols=100 rows=10 name=q>" + PageFlowUtil.filter(expr) + "</textarea><br><input type=submit onclick='Ext.getBody().mask();'></form>\n" +
                    "<script>" +
                    "    var resizer = new (Ext4||Ext).Resizable(\"expression\", {\n" +
                            "        handles: 'se',\n" +
                            "        minWidth: 200,\n" +
                            "        minHeight: 100,\n" +
                            "        maxWidth: 1200,\n" +
                            "        maxHeight: 800,\n" +
                            "        pinned: true\n" +
                            "    });\n" +
                    "</script>"
            );

            QNode e = null;
            if (null != expr)
            {
                try
                {
                    e = _parse(expr,qpe);
                }
                catch (RuntimeException x)
                {
                    qpe.add(new QueryParseException(x.getMessage(),x, 0, 0));
                }
            }

            Tree tree = null;
            if (null != expr)
            {
                try
                {
                    tree = _tree(expr);
                } catch (Exception x)
                {
                    qpe.add(new QueryParseException(x.getMessage(),x, 0, 0));
                }
            }

            for (Throwable x : qpe)
            {
                if (null != x.getCause() && x != x.getCause())
                    x = x.getCause();
                html.add("<br>" + PageFlowUtil.filter(x.toString()));
                Logger.getLogger(QueryController.class).debug(expr,x);
            }
            if (null != e)
            {
                String prefix = SqlParser.toPrefixString(e);
                html.add("<hr>");
                html.add(PageFlowUtil.filter(prefix));
            }
            if (null != tree)
            {
                String prefix = SqlParser.toPrefixString(tree);
                html.add("<hr>");
                html.add(PageFlowUtil.filter(prefix));
            }
            html.add("</body></html>");
            return new HtmlView(StringUtils.join(html,""));
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }

        abstract QNode _parse(String e, List<QueryParseException> errors);
        abstract Tree _tree(String e) throws Exception;
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class ParseExpressionAction extends ParseAction
    {
        QNode _parse(String s, List<QueryParseException> errors)
        {
            return new SqlParser().parseExpr(s, errors);
        }

        @Override
        Tree _tree(String e)
        {
            return null;
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class ParseQueryAction extends ParseAction
    {
        QNode _parse(String s, List<QueryParseException> errors)
        {
            return new SqlParser().parseQuery(s, errors, null);
        }

        @Override
        Tree _tree(String s) throws Exception
        {
            return new SqlParser().rawQuery(s);
        }
    }


    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectMetaData.class)
    public class ValidateQueryMetadataAction extends ApiAction<QueryForm>
    {
        public ApiResponse execute(QueryForm form, BindException errors) throws Exception
        {
            UserSchema schema = form.getSchema();

            if (null == schema)
            {
                errors.reject(ERROR_MSG, "could not resolve schema: " + form.getSchemaName());
                return null;
            }

            TableInfo table = schema.getTable(form.getQueryName());

            if (null == table)
            {
                errors.reject(ERROR_MSG, "could not resolve table: " + form.getQueryName());
                return null;
            }

            ApiSimpleResponse response = new ApiSimpleResponse();
            List<QueryParseException> parseErrors = new ArrayList<>();
            List<QueryParseException> parseWarnings = new ArrayList<>();

            if (!QueryManager.get().validateQuery(table, true, parseErrors, parseWarnings))
            {
                for (QueryParseException e : parseErrors)
                {
                    errors.reject(ERROR_MSG, e.getMessage());
                }
                return response;
            }

            SchemaKey schemaKey = SchemaKey.fromString(form.getSchemaName());
            QueryManager.get().validateQueryMetadata(schemaKey, form.getQueryName(), getUser(), getContainer(), parseErrors, parseWarnings);
            QueryManager.get().validateQueryViews(schemaKey, form.getQueryName(), getUser(), getContainer(), parseErrors, parseWarnings);

            for (QueryParseException e : parseErrors)
            {
                errors.reject(ERROR_MSG, e.getMessage());
            }

            for (QueryParseException e : parseWarnings)
            {
                errors.reject(ERROR_MSG, "WARNING: " + e.getMessage());
            }

            return response;
        }
    }

    public static class QueryExportAuditForm
    {
        private int rowId;

        public int getRowId()
        {
            return rowId;
        }

        public void setRowId(int rowId)
        {
            this.rowId = rowId;
        }
    }

    /**
     * Action used to redirect QueryAuditProvider [details] column to the exported table's grid view.
     */
    @RequiresPermission(AdminPermission.class)
    public class QueryExportAuditRedirectAction extends RedirectAction<QueryExportAuditForm>
    {
        private ActionURL _url;

        @Override
        public URLHelper getSuccessURL(QueryExportAuditForm form)
        {
            return _url;
        }

        @Override
        public boolean doAction(QueryExportAuditForm form, BindException errors) throws Exception
        {
            UserSchema auditSchema = QueryService.get().getUserSchema(getUser(), getContainer(), AbstractAuditTypeProvider.QUERY_SCHEMA_NAME);
            TableInfo queryExportAuditTable = auditSchema.getTable(QueryExportAuditProvider.QUERY_AUDIT_EVENT);

            TableSelector selector = new TableSelector(queryExportAuditTable,
                    PageFlowUtil.set(
                            QueryExportAuditProvider.COLUMN_NAME_SCHEMA_NAME,
                            QueryExportAuditProvider.COLUMN_NAME_QUERY_NAME,
                            QueryExportAuditProvider.COLUMN_NAME_DETAILS_URL),
                    new SimpleFilter(FieldKey.fromParts(AbstractAuditTypeProvider.COLUMN_NAME_ROW_ID), form.getRowId()), null);

            Map<String, Object> result = selector.getMap();
            if (result == null)
                throw new NotFoundException("Query export audit event not found for rowId");

            String schemaName = (String)result.get(QueryExportAuditProvider.COLUMN_NAME_SCHEMA_NAME);
            String queryName = (String)result.get(QueryExportAuditProvider.COLUMN_NAME_QUERY_NAME);
            String detailsURL = (String)result.get(QueryExportAuditProvider.COLUMN_NAME_DETAILS_URL);

            if (schemaName == null || queryName == null)
                throw new NotFoundException("Query export audit event has not schemaName or queryName");

            ActionURL url = new ActionURL(QueryController.ExecuteQueryAction.class, getContainer());

            // Apply the sorts and filters
            if (detailsURL != null)
            {
                ActionURL sortFilterURL = new ActionURL(detailsURL);
                url.setPropertyValues(sortFilterURL.getPropertyValues());
            }

            if (url.getParameter(QueryParam.schemaName) == null)
                url.addParameter(QueryParam.schemaName, schemaName);
            if (url.getParameter(QueryParam.queryName) == null && url.getParameter(QueryView.DATAREGIONNAME_DEFAULT + "." + QueryParam.queryName) == null)
                url.addParameter(QueryParam.queryName, queryName);

            _url = url;
            return true;
        }

        @Override
        public void validateCommand(QueryExportAuditForm form, Errors errors)
        {
            if (form.getRowId() == 0)
                throw new NotFoundException("Query export audit rowid required");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class AuditHistoryAction extends SimpleViewAction<QueryForm>
    {
        @Override
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            return QueryUpdateAuditProvider.createHistoryQueryView(getViewContext(), form, errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Audit History");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class AuditDetailsAction extends SimpleViewAction<QueryDetailsForm>
    {
        @Override
        public ModelAndView getView(QueryDetailsForm form, BindException errors) throws Exception
        {
            return QueryUpdateAuditProvider.createDetailsQueryView(getViewContext(), form, errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Audit History");
        }
    }

    public static class QueryDetailsForm extends QueryForm
    {
        String _keyValue;

        public String getKeyValue()
        {
            return _keyValue;
        }

        public void setKeyValue(String keyValue)
        {
            _keyValue = keyValue;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class QueryAuditChangesAction extends SimpleViewAction<AuditChangesForm>
    {
        @Override
        public ModelAndView getView(AuditChangesForm form, BindException errors) throws Exception
        {
            int auditRowId = form.getAuditRowId();
            String comment = null;
            String oldRecord = null;
            String newRecord = null;

            QueryUpdateAuditProvider.QueryUpdateAuditEvent event = AuditLogService.get().getAuditEvent(getUser(), QueryUpdateAuditProvider.QUERY_UPDATE_AUDIT_EVENT, auditRowId);

            if (event != null)
            {
                comment = event.getComment();
                oldRecord = event.getOldRecordMap();
                newRecord = event.getNewRecordMap();
            }

            if (oldRecord != null || newRecord != null)
            {
                Map<String,String> oldData = QueryExportAuditProvider.decodeFromDataMap(oldRecord);
                Map<String,String> newData = QueryExportAuditProvider.decodeFromDataMap(newRecord);

                return new AuditChangesView(comment, oldData, newData);
            }
            return new NoRecordView();
        }

        private class NoRecordView extends HttpView
        {
            protected void renderInternal(Object model, PrintWriter out) throws Exception
            {
                out.write("<p>No current record found</p>");
            }
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Audit Details");
        }
    }

    public static class AuditChangesForm
    {
        private int auditRowId;

        public int getAuditRowId() {return auditRowId;}

        public void setAuditRowId(int auditRowId) {this.auditRowId = auditRowId;}
    }

    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.Export.class)
    public class ExportTablesAction extends FormViewAction<ExportTablesForm>
    {
        private ActionURL _successUrl;

        @Override
        public void validateCommand(ExportTablesForm form, Errors errors)
        {
        }

        @Override
        public boolean handlePost(ExportTablesForm form, BindException errors)
        {
            HttpServletResponse httpResponse = getViewContext().getResponse();
            Container container = getContainer();
            QueryServiceImpl svc = (QueryServiceImpl)QueryService.get();

            try (ByteArrayOutputStream baos = new ByteArrayOutputStream(); OutputStream outputStream = new BufferedOutputStream(baos))
            {
                try (ZipFile zip = new ZipFile(outputStream, true))
                {
                    svc.writeTables(container, getUser(), zip, form.getSchemas(), form.getHeaderType());
                }

                PageFlowUtil.streamFileBytes(httpResponse, FileUtil.makeFileNameWithTimestamp(container.getName(), "tables.zip"), baos.toByteArray(), false);
            }
            catch (Exception e)
            {
                errors.reject(ERROR_MSG, e.getMessage() != null ? e.getMessage() : e.getClass().getName());
                LOG.error("Errror exporting tables", e);
            }

            if (errors.hasErrors())
            {
                _successUrl = new ActionURL(ExportTablesAction.class, getContainer());
            }

            return !errors.hasErrors();
        }

        @Override
        public ModelAndView getView(ExportTablesForm form, boolean reshow, BindException errors) throws Exception
        {
            // When exporting the zip to the browser, the base action will attempt to reshow the view since we returned
            // null as the success URL; returning null here causes the base action to stop pestering the action.
            if (reshow && !errors.hasErrors())
                return null;
            
            return new JspView<>("/org/labkey/query/view/exportTables.jsp", form, errors);
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Export Tables");
        }

        @Override
        public ActionURL getSuccessURL(ExportTablesForm form)
        {
            return _successUrl;
        }
    }

    public static class ExportTablesForm implements HasBindParameters
    {
        ColumnHeaderType _headerType = ColumnHeaderType.DisplayFieldKey;
        Map<String, List<Map<String, Object>>> _schemas = new HashMap<>();

        public ColumnHeaderType getHeaderType()
        {
            return _headerType;
        }

        public void setHeaderType(ColumnHeaderType headerType)
        {
            _headerType = headerType;
        }

        public Map<String, List<Map<String, Object>>> getSchemas()
        {
            return _schemas;
        }

        public void setSchemas(Map<String, List<Map<String, Object>>> schemas)
        {
            _schemas = schemas;
        }

        public BindException bindParameters(PropertyValues values)
        {
            BindException errors = new NullSafeBindException(this, "form");

            PropertyValue schemasProperty = values.getPropertyValue("schemas");
            if (schemasProperty != null && schemasProperty.getValue() != null)
            {
                ObjectMapper om = new ObjectMapper();
                try
                {
                    _schemas = om.readValue((String)schemasProperty.getValue(), _schemas.getClass());
                }
                catch (IOException e)
                {
                    errors.rejectValue("schemas", ERROR_MSG, e.getMessage());
                }
            }

            PropertyValue headerTypeProperty = values.getPropertyValue("headerType");
            if (headerTypeProperty != null && headerTypeProperty.getValue() != null)
            {
                try
                {
                    _headerType = ColumnHeaderType.valueOf(String.valueOf(headerTypeProperty.getValue()));
                }
                catch (IllegalArgumentException ex)
                {
                    // ignore
                }
            }

            return errors;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class SaveNamedSetAction extends ApiAction<NamedSetForm>
    {
        @Override
        public Object execute(NamedSetForm namedSetForm, BindException errors) throws Exception
        {
            QueryService.get().saveNamedSet(namedSetForm.getSetName(), namedSetForm.parseSetList());
            return new ApiSimpleResponse("success", true);
        }
    }

    public static class NamedSetForm
    {
        String setName;
        String[] setList;

        public String getSetName()
        {
            return setName;
        }

        public void setSetName(String setName)
        {
            this.setName = setName;
        }

        public String[] getSetList()
        {
            return setList;
        }

        public void setSetList(String[] setList)
        {
            this.setList = setList;
        }

        public List<String> parseSetList() throws IOException
        {
            return Arrays.asList(setList);
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class DeleteNamedSetAction extends ApiAction<NamedSetForm>
    {

        @Override
        public Object execute(NamedSetForm namedSetForm, BindException errors) throws Exception
        {
            QueryService.get().deleteNamedSet(namedSetForm.getSetName());
            return new ApiSimpleResponse("success", true);
        }
    }

    public static class GenerateSchemaForm extends ReturnUrlForm
    {
        String sourceSchema;
        String sourceDataSource;
        String targetSchema;
        String pathInScript;
        String outputDir; 

        public String getSourceSchema()
        {
            return sourceSchema;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setSourceSchema(String sourceSchema)
        {
            this.sourceSchema = sourceSchema;
        }

        public String getTargetSchema()
        {
            return targetSchema;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setTargetSchema(String targetSchema)
        {
            this.targetSchema = targetSchema;
        }

        public String getPathInScript()
        {
            return pathInScript;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setPathInScript(String pathInScript)
        {
            this.pathInScript = pathInScript;
        }

        public String getSourceDataSource()
        {
            return sourceDataSource;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setSourceDataSource(String sourceDataSource)
        {
            this.sourceDataSource = sourceDataSource;
        }

        public String getOutputDir()
        {
            return outputDir;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setOutputDir(String outputDir)
        {
            this.outputDir = outputDir;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class GenerateSchemaAction extends FormViewAction<GenerateSchemaForm>
    {

        @Override
        public void validateCommand(GenerateSchemaForm form, Errors errors)
        {
            // TODO validate schemaNames and dataSources are real
            // TODO validate path is not empty string
        }

        @Override
        public ModelAndView getView(GenerateSchemaForm form, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/query/view/generateSchema.jsp", form, errors);
        }

        @Override
        public boolean handlePost(GenerateSchemaForm form, BindException errors) throws Exception
        {
            StringBuilder importScript = new StringBuilder();

            // NOTE: should we add any kind of dialect tags to the importScript output?
            DbSchema sourceSchema = DbSchema.createFromMetaData(DbScope.getDbScope(form.getSourceDataSource()), form.getSourceSchema(), DbSchemaType.Bare);
            String targetSchema = StringUtils.isBlank(form.getTargetSchema()) ? form.getSourceSchema() : form.getTargetSchema();
            String pathInScript = StringUtils.isBlank(form.getPathInScript()) ? "" : form.getPathInScript();
            if (!pathInScript.endsWith("/"))
                pathInScript += "/";

            List<TableInfo> tables;
            try
            {
                tables = TableSorter.sort(sourceSchema);
            }
            catch (IllegalStateException e)
            {
                errors.reject(ERROR_MSG, "Problem with schema: " + e.getMessage());
                return false;
            }

            try
            {
                for (TableInfo table : tables)
                {
                    if (DatabaseTableType.TABLE.equals(table.getTableType()))
                    {
                        String tableName = table.getName();
                        try (Results results = new TableSelector(table).getResults(false))
                        {
                            if (results.isBeforeFirst()) // only export tables with data
                            {
                                File outputFile = new File(form.getOutputDir(), tableName + ".tsv.gz");
                                GZIPOutputStream outputStream = new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile), 64 * 1024), 64 * 1024);
                                try (TSVGridWriter tsv = new TSVGridWriter(results))
                                {
                                    tsv.setColumnHeaderType(ColumnHeaderType.DisplayFieldKey);
                                    tsv.setApplyFormats(false);
                                    tsv.setPreserveEmptyString(true); // TODO: Make that an option on export?
                                    tsv.write(outputStream);
                                }

                                importScript.append(sourceSchema.getSqlDialect().execute(DbSchema.get("core", DbSchemaType.Module), "bulkImport", "'" + targetSchema + "', '" + tableName + "', '" + pathInScript + outputFile.getName() + "'")).append(";\n");
                            }
                        }
                    }
                }

                PrintWriter writer = new PrintWriter(new File(form.getOutputDir(), form.getSourceSchema() + "_updateScript.sql"), StandardCharsets.UTF_8.name());
                writer.print(importScript.toString());
                writer.close();
            }
            catch (FileNotFoundException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return false;
            }

            return true;
        }

        @Override
        public URLHelper getSuccessURL(GenerateSchemaForm form)
        {
            return form.getReturnActionURL();
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Generate Schema");
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class GetSchemasWithDataSourcesAction extends ApiAction
    {

        @Override
        public Object execute(Object o, BindException errors) throws Exception
        {
            // NOTE: copy pasta from initSources()
            Collection<Map<String, Object>> sourcesAndSchemas = new LinkedList<>();
            for (DbScope scope : DbScope.getDbScopes())
            {
                DataSourceInfo source = new DataSourceInfo(scope);
                for (String schemaName : scope.getSchemaNames())
                {
                    Map<String, Object> map = new HashMap<>();
                    map.put("dataSourceDisplayName", source.displayName);
                    map.put("dataSourceSourceName", source.sourceName);
                    map.put("schemaName", schemaName);
                    sourcesAndSchemas.add(map);
                }
            }

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("schemas", sourcesAndSchemas);
            return response;
        }
    }


    // could make this requires(ReadPermission), but it could be pretty easy to abuse, or maybe RequiresLogin && ReadPermission
    @RequiresLogin
    public class TestSQLAction extends SimpleViewAction
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            getPageConfig().addClientDependency(ClientDependency.fromPath("internal/jQuery"));
            getPageConfig().addClientDependency(ClientDependency.fromPath("clientapi"));
            return new HtmlView("<script src='" + AppProps.getInstance().getContextPath() + "/query/testquery.js'></script><div id=testQueryDiv style='min-height:600px;min-width:800px;'></div>");
        }

        @Override
        public NavTree appendNavTrail(NavTree root)
        {
            return root;
        }
    }

    public static class TestCase extends AbstractActionPermissionTest
    {
        @Override
        public void testActionPermissions()
        {
            User user = TestContext.get().getUser();
            assertTrue(user.isInSiteAdminGroup());

            QueryController controller = new QueryController();

            // @RequiresPermission(ReadPermission.class)
            assertForReadPermission(user,
                controller.new BrowseAction(),
                controller.new BeginAction(),
                controller.new SchemaAction(),
                controller.new SourceQueryAction(),
                controller.new SaveSourceQueryAction(),
                controller.new ExecuteQueryAction(),
                controller.new PrintRowsAction(),
                controller.new ExportScriptAction(),
                controller.new ExportRowsExcelAction(),
                controller.new ExportRowsXLSXAction(),
                controller.new ExportExcelTemplateAction(),
                controller.new ExportRowsTsvAction(),
                controller.new ExcelWebQueryDefinitionAction(),
                controller.new MetadataServiceAction(),
                controller.new SaveQueryViewsAction(),
                controller.new PropertiesQueryAction(),
                controller.new SelectRowsAction(),
                controller.new GetDataAction(),
                controller.new ExecuteSqlAction(),
                controller.new SelectDistinctAction(),
                controller.new GetColumnSummaryStatsAction(),
                controller.new ImportAction(),
                controller.new ExportSqlAction(),
                controller.new UpdateRowsAction(),
                controller.new InsertRowsAction(),
                controller.new ImportRowsAction(),
                controller.new DeleteRowsAction(),
                controller.new TableInfoAction(),
                controller.new SaveSessionViewAction(),
                controller.new GetSchemasAction(),
                controller.new GetQueriesAction(),
                controller.new GetQueryViewsAction(),
                controller.new SaveApiTestAction(),
                controller.new ValidateQueryMetadataAction(),
                controller.new AuditHistoryAction(),
                controller.new AuditDetailsAction(),
                controller.new QueryAuditChangesAction(),
                controller.new ExportTablesAction(),
                controller.new SaveNamedSetAction(),
                controller.new DeleteNamedSetAction()
            );

            // @RequiresPermission(DeletePermission.class)
            assertForUpdateOrDeletePermission(user,
                controller.new DeleteQueryAction(),
                controller.new DeleteQueryRowsAction()
            );

            // @RequiresPermission(AdminPermission.class)
            assertForAdminPermission(user,
                controller.new NewQueryAction(),
                controller.new MetadataQueryAction(),
                controller.new TruncateTableAction(),
                controller.new ApiTestAction(),
                controller.new AdminAction(),
                controller.new ManageRemoteConnectionsAction(),
                controller.new ReloadExternalSchemaAction(),
                controller.new ReloadAllUserSchemas(),
                controller.new ManageViewsAction(),
                controller.new InternalDeleteView(),
                controller.new InternalSourceViewAction(),
                controller.new InternalNewViewAction(),
                controller.new QueryExportAuditRedirectAction(),
                controller.new GenerateSchemaAction(),
                controller.new GetSchemasWithDataSourcesAction()
            );

            // @RequiresPermission(AdminOperationsPermission.class)
            assertForAdminOperationsPermission(user,
                controller.new EditRemoteConnectionAction(),
                controller.new DeleteRemoteConnectionAction(),
                controller.new TestRemoteConnectionAction(),
                controller.new RawTableMetaDataAction(),
                controller.new RawSchemaMetaDataAction(),
                controller.new InsertLinkedSchemaAction(),
                controller.new InsertExternalSchemaAction(),
                controller.new DeleteLinkedSchemaAction(),
                controller.new DeleteExternalSchemaAction(),
                controller.new EditLinkedSchemaAction(),
                controller.new EditExternalSchemaAction(),
                controller.new GetTablesAction(),
                controller.new SchemaTemplateAction(),
                controller.new SchemaTemplatesAction(),
                controller.new ParseExpressionAction(),
                controller.new ParseQueryAction()
            );

            // @AdminConsoleAction
            assertForAdminPermission(ContainerManager.getRoot(), user,
                controller.new DataSourceAdminAction()
            );
        }
    }
}
