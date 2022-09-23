/*
 * Copyright (c) 2008-2019 LabKey Corporation
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
import org.apache.commons.collections4.multimap.HashSetValuedHashMap;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.old.JSONArray;
import org.json.old.JSONException;
import org.json.old.JSONObject;
import org.labkey.api.action.*;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.attachments.SpringAttachmentFile;
import org.labkey.api.audit.AbstractAuditTypeProvider;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.TransactionAuditProvider;
import org.labkey.api.audit.provider.ContainerAuditProvider;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.RowMapFactory;
import org.labkey.api.data.*;
import org.labkey.api.data.dialect.JdbcMetaDataLocator;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DetailedAuditLogDataIterator;
import org.labkey.api.dataiterator.ListofMapsDataIterator;
import org.labkey.api.exceptions.OptimisticConflictException;
import org.labkey.api.exp.api.ProvenanceRecordingParams;
import org.labkey.api.exp.api.ProvenanceService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.RecordedAction;
import org.labkey.api.query.*;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.IgnoresTermsOfUse;
import org.labkey.api.security.RequiresAllOf;
import org.labkey.api.security.RequiresAnyOf;
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
import org.labkey.api.security.permissions.PlatformDeveloperPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.stats.BaseAggregatesAnalyticsProvider;
import org.labkey.api.stats.ColumnAnalyticsProvider;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.JsonUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ResponseHelper;
import org.labkey.api.util.ReturnURLString;
import org.labkey.api.util.StringExpression;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DetailsView;
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
import org.labkey.api.writer.PrintWriters;
import org.labkey.api.writer.ZipFile;
import org.labkey.data.xml.ColumnType;
import org.labkey.data.xml.TableType;
import org.labkey.data.xml.TablesDocument;
import org.labkey.data.xml.TablesType;
import org.labkey.data.xml.externalSchema.TemplateSchemaType;
import org.labkey.data.xml.queryCustomView.FilterType;
import org.labkey.query.AutoGeneratedDetailsCustomView;
import org.labkey.query.AutoGeneratedInsertCustomView;
import org.labkey.query.AutoGeneratedUpdateCustomView;
import org.labkey.query.CustomViewImpl;
import org.labkey.query.CustomViewUtil;
import org.labkey.query.EditQueriesPermission;
import org.labkey.query.EditableCustomView;
import org.labkey.query.LinkedTableInfo;
import org.labkey.query.MetadataTableJSON;
import org.labkey.query.ModuleCustomQueryDefinition;
import org.labkey.query.ModuleCustomView;
import org.labkey.query.QueryServiceImpl;
import org.labkey.query.TableXML;
import org.labkey.query.audit.QueryExportAuditProvider;
import org.labkey.query.audit.QueryUpdateAuditProvider;
import org.labkey.query.model.MetadataTableJSONMixin;
import org.labkey.query.persist.AbstractExternalSchemaDef;
import org.labkey.query.persist.CstmView;
import org.labkey.query.persist.ExternalSchemaDef;
import org.labkey.query.persist.ExternalSchemaDefCache;
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
import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.PropertyValues;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.multipart.MultipartFile;
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
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.zip.GZIPOutputStream;

import static java.util.Objects.requireNonNull;
import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.trimToEmpty;
import static org.labkey.api.data.DbScope.NO_OP_TRANSACTION;
import static org.labkey.api.util.DOM.BR;
import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.Renderable;
import static org.labkey.api.util.DOM.cl;

@SuppressWarnings("DefaultAnnotationParam")

public class QueryController extends SpringActionController
{
    private static final Logger LOG = LogManager.getLogger(QueryController.class);
    private static final String ROW_ATTACHMENT_INDEX_DELIM = "::";

    private static final Set<String> RESERVED_VIEW_NAMES;
    static
    {
        RESERVED_VIEW_NAMES = new CaseInsensitiveHashSet();
        RESERVED_VIEW_NAMES.add("Default");
        RESERVED_VIEW_NAMES.add(AutoGeneratedDetailsCustomView.NAME);
        RESERVED_VIEW_NAMES.add(AutoGeneratedInsertCustomView.NAME);
        RESERVED_VIEW_NAMES.add(AutoGeneratedUpdateCustomView.NAME);
    }
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
            ActionURL url = new ActionURL(EditRemoteConnectionAction.class, c);
            url.addParameter("connectionName", connectionName);
            return url;
        }

        public static ActionURL urlSaveRemoteConnection(Container c)
        {
            return new ActionURL(EditRemoteConnectionAction.class, c);
        }

        public static ActionURL urlDeleteRemoteConnection(Container c, @Nullable String connectionName)
        {
            ActionURL url = new ActionURL(DeleteRemoteConnectionAction.class, c);
            if (connectionName != null)
                url.addParameter("connectionName", connectionName);
            return url;
        }

        public static ActionURL urlTestRemoteConnection(Container c, String connectionName)
        {
            ActionURL url = new ActionURL(TestRemoteConnectionAction.class, c);
            url.addParameter("connectionName", connectionName);
            return url;
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public static class EditRemoteConnectionAction extends FormViewAction<RemoteConnections.RemoteConnectionForm>
    {
        @Override
        public void validateCommand(RemoteConnections.RemoteConnectionForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(RemoteConnections.RemoteConnectionForm remoteConnectionForm, boolean reshow, BindException errors)
        {
            remoteConnectionForm.setConnectionKind(RemoteConnections.CONNECTION_KIND_QUERY);
            if (!errors.hasErrors())
            {
                String name = remoteConnectionForm.getConnectionName();
                // package the remote-connection properties into the remoteConnectionForm and pass them along
                Map<String, String> map1 = RemoteConnections.getRemoteConnection(RemoteConnections.REMOTE_QUERY_CONNECTIONS_CATEGORY, name, getContainer());
                remoteConnectionForm.setUrl(map1.get("URL"));
                remoteConnectionForm.setUserEmail(map1.get("user"));
                remoteConnectionForm.setPassword(map1.get("password"));
                remoteConnectionForm.setFolderPath(map1.get("container"));
            }
            setHelpTopic("remoteConnection");
            return new JspView<>("/org/labkey/query/view/createRemoteConnection.jsp", remoteConnectionForm, errors);
        }

        @Override
        public boolean handlePost(RemoteConnections.RemoteConnectionForm remoteConnectionForm, BindException errors)
        {
            return RemoteConnections.createOrEditRemoteConnection(remoteConnectionForm, getContainer(), errors);
        }

        @Override
        public URLHelper getSuccessURL(RemoteConnections.RemoteConnectionForm remoteConnectionForm)
        {
            return RemoteQueryConnectionUrls.urlManageRemoteConnection(getContainer());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            new BeginAction(getViewContext()).addNavTrail(root);
            root.addChild("Create/Edit Remote Connection", new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer()));
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public static class DeleteRemoteConnectionAction extends FormViewAction<RemoteConnections.RemoteConnectionForm>
    {
        @Override
        public void validateCommand(RemoteConnections.RemoteConnectionForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(RemoteConnections.RemoteConnectionForm remoteConnectionForm, boolean reshow, BindException errors)
        {
            return new JspView<>("/org/labkey/query/view/confirmDeleteConnection.jsp", remoteConnectionForm, errors);
        }

        @Override
        public boolean handlePost(RemoteConnections.RemoteConnectionForm remoteConnectionForm, BindException errors)
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
        public void addNavTrail(NavTree root)
        {
            new BeginAction(getViewContext()).addNavTrail(root);
            root.addChild("Confirm Delete Connection", new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer()));
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public static class TestRemoteConnectionAction extends FormViewAction<RemoteConnections.RemoteConnectionForm>
    {
        @Override
        public void validateCommand(RemoteConnections.RemoteConnectionForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(RemoteConnections.RemoteConnectionForm remoteConnectionForm, boolean reshow, BindException errors)
        {
            String name = remoteConnectionForm.getConnectionName();
            String schemaName = "core"; // test Schema Name
            String queryName = "Users"; // test Query Name

            // Extract the username, password, and container from the secure property store
            Map<String, String> singleConnectionMap = RemoteConnections.getRemoteConnection(RemoteConnections.REMOTE_QUERY_CONNECTIONS_CATEGORY, name, getContainer());
            if (singleConnectionMap.isEmpty())
                throw new NotFoundException();
            String url = singleConnectionMap.get(RemoteConnections.FIELD_URL);
            String user = singleConnectionMap.get(RemoteConnections.FIELD_USER);
            String password = singleConnectionMap.get(RemoteConnections.FIELD_PASSWORD);
            String container = singleConnectionMap.get(RemoteConnections.FIELD_CONTAINER);

            // connect to the remote server and retrieve an input stream
            org.labkey.remoteapi.Connection cn = new org.labkey.remoteapi.Connection(url, user, password);
            final SelectRowsCommand cmd = new SelectRowsCommand(schemaName, queryName);
            try
            {
                DataIteratorBuilder source = SelectRowsStreamHack.go(cn, container, cmd, getContainer());
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
        public boolean handlePost(RemoteConnections.RemoteConnectionForm remoteConnectionForm, BindException errors)
        {
            return true;
        }

        @Override
        public URLHelper getSuccessURL(RemoteConnections.RemoteConnectionForm remoteConnectionForm)
        {
            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            new BeginAction(getViewContext()).addNavTrail(root);
            root.addChild("Manage Remote Connections", new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer()));
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
            ret.addParameter(QueryParam.schemaName.toString(), trimToEmpty(schemaName));
            ret.addParameter(QueryParam.queryName.toString(), trimToEmpty(queryName));
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
            ActionURL url = new ActionURL(EditExternalSchemaAction.class, c);
            url.addParameter("externalSchemaId", Integer.toString(def.getExternalSchemaId()));
            return url;
        }

        public ActionURL urlReloadExternalSchema(Container c, AbstractExternalSchemaDef def)
        {
            ActionURL url = new ActionURL(ReloadExternalSchemaAction.class, c);
            url.addParameter("externalSchemaId", Integer.toString(def.getExternalSchemaId()));
            return url;
        }

        public ActionURL urlDeleteSchema(Container c, AbstractExternalSchemaDef def)
        {
            ActionURL url = new ActionURL(DeleteSchemaAction.class, c);
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

    @Override
    public PageConfig defaultPageConfig()
    {
        // set default help topic for query controller
        PageConfig config = super.defaultPageConfig();
        config.setHelpTopic("querySchemaBrowser");
        return config;
    }

    @AdminConsoleAction(AdminOperationsPermission.class)
    public static class DataSourceAdminAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            List<ExternalSchemaDef> allDefs = QueryManager.get().getExternalSchemaDefs(null);

            MultiValuedMap<String, ExternalSchemaDef> byDataSourceName = new ArrayListValuedHashMap<>();

            for (ExternalSchemaDef def : allDefs)
                byDataSourceName.put(def.getDataSource(), def);

            StringBuilder sb = new StringBuilder();

            sb.append("\n<div>This page lists all the data sources defined in your ").append(AppProps.getInstance().getWebappConfigurationFilename()).append(" file that were available when first referenced and the external schemas defined in each.</div><br/>\n");
            sb.append("\n<table class=\"labkey-data-region\">\n");
            sb.append("<tr class=\"labkey-show-borders\">");
            sb.append("  <td class=\"labkey-column-header\">Data Source</td>");
            sb.append("  <td class=\"labkey-column-header\">Current Status</td>");
            sb.append("  <td class=\"labkey-column-header\">URL</td>");
            sb.append("  <td class=\"labkey-column-header\">Database Name</td>");
            sb.append("  <td class=\"labkey-column-header\">Product Name</td>");
            sb.append("  <td class=\"labkey-column-header\">Product Version</td>");
            sb.append("  <td class=\"labkey-column-header\">Max Connections</td>");
            sb.append("  <td class=\"labkey-column-header\">Active Connections</td>");
            sb.append("  <td class=\"labkey-column-header\">Idle Connections</td>\n");
            sb.append("  <td class=\"labkey-column-header\">Max Wait (ms)</td></tr>\n");

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
                sb.append(PageFlowUtil.filter(scope.getDatabaseUrl()));
                sb.append("</td><td>");
                sb.append(PageFlowUtil.filter(scope.getDatabaseName()));
                sb.append("</td><td>");
                sb.append(PageFlowUtil.filter(scope.getDatabaseProductName()));
                sb.append("</td><td>");
                sb.append(PageFlowUtil.filter(scope.getDatabaseProductVersion()));
                sb.append("</td><td>");
                sb.append(scope.getDataSourceProperties().getMaxTotal());
                sb.append("</td><td>");
                sb.append(scope.getDataSourceProperties().getNumActive());
                sb.append("</td><td>");
                sb.append(scope.getDataSourceProperties().getNumIdle());
                sb.append("</td><td>");
                sb.append(scope.getDataSourceProperties().getMaxWaitMillis());
                sb.append("</td></tr>\n");

                Collection<ExternalSchemaDef> dsDefs = byDataSourceName.get(scope.getDataSourceName());

                sb.append("<tr class=\"").append(rowCount % 2 == 0 ? "labkey-alternate-row" : "labkey-row").append("\">\n");
                sb.append("  <td colspan=\"10\">\n");
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

                                if (!StringUtils.equals(def.getSourceSchemaName(),def.getUserSchemaName()))
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

            return new HtmlView(HtmlString.unsafe(sb.toString()));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            requireNonNull(urlProvider(AdminUrls.class)).addAdminNavTrail(root, "Data Source Administration", getClass(), getContainer());
        }
    }


    @RequiresPermission(ReadPermission.class)
    public static class BrowseAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return new JspView<>("/org/labkey/query/view/browse.jsp", null);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Schema Browser");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class BeginAction extends QueryViewAction
    {
        @SuppressWarnings("UnusedDeclaration")
        public BeginAction()
        {
        }

        public BeginAction(ViewContext ctx)
        {
            setViewContext(ctx);
        }

        @Override
        public ModelAndView getView(QueryForm form, BindException errors)
        {
            JspView<QueryForm> view = new JspView<>("/org/labkey/query/view/browse.jsp", form);
            view.setFrame(WebPartView.FrameType.NONE);
            return view;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Query Schema Browser", new QueryUrlsImpl().urlSchemaBrowser(getContainer()));
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

        @Override
        public ModelAndView getView(QueryForm form, BindException errors)
        {
            _form = form;
            return new JspView<>("/org/labkey/query/view/browse.jsp", form);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            if (_form.getSchema() != null)
                addSchemaActionNavTrail(root, _form.getSchema().getSchemaPath(), _form.getQueryName());
        }
    }


    void addSchemaActionNavTrail(NavTree root, SchemaKey schemaKey, String queryName)
    {
        if (getContainer().hasOneOf(getUser(), AdminPermission.class, PlatformDeveloperPermission.class))
        {
            // Don't show the full query nav trail to non-admin/non-developer users as they almost certainly don't
            // want it
            try
            {
                String schemaName = schemaKey.toDisplayString();
                ActionURL url = new ActionURL(BeginAction.class, getContainer());
                url.addParameter("schemaName", schemaKey.toString());
                url.addParameter("queryName", queryName);
                new BeginAction(getViewContext()).addNavTrail(root);
                root.addChild(schemaName + " Schema", url);
            }
            catch (NullPointerException e)
            {
                LOG.error("NullPointerException in addNavTrail", e);
            }
        }
    }


    // Trusted analysts who are editors can create and modify queries
    @RequiresAllOf({EditQueriesPermission.class, UpdatePermission.class})
    @Action(ActionType.SelectData.class)
    public class NewQueryAction extends FormViewAction<NewQueryForm>
    {
        private NewQueryForm _form;
        private ActionURL _successUrl;

        @Override
        public void validateCommand(NewQueryForm target, org.springframework.validation.Errors errors)
        {
            target.ff_newQueryName = StringUtils.trimToNull(target.ff_newQueryName);
            if (null == target.ff_newQueryName)
                errors.reject(ERROR_MSG, "QueryName is required");
        }

        @Override
        public ModelAndView getView(NewQueryForm form, boolean reshow, BindException errors)
        {
            form.ensureSchemaExists();

            if (!form.getSchema().canCreate())
            {
                throw new UnauthorizedException();
            }

            getPageConfig().setFocusId("ff_newQueryName");
            _form = form;
            setHelpTopic("sqlTutorial");
            return new JspView<>("/org/labkey/query/view/newQuery.jsp", form, errors);
        }

        @Override
        public boolean handlePost(NewQueryForm form, BindException errors)
        {
            form.ensureSchemaExists();

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
                TableInfo existingTable = form.getSchema().getTable(newQueryName, null);
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

        @Override
        public ActionURL getSuccessURL(NewQueryForm newQueryForm)
        {
            return _successUrl;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            new SchemaAction(_form).addNavTrail(root);
            root.addChild("New Query", new QueryUrlsImpl().urlNewQuery(getContainer()));
        }
    }

    // CONSIDER : deleting this action after the SQL editor UI changes are finalized, keep in mind that built-in views
    // use this view as well via the edit metadata page.
    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectMetaData.class)  // Note: This action deals with just meta data; it AJAXes data into place using GetWebPartAction
    public class SourceQueryAction extends SimpleViewAction<SourceForm>
    {
        public SourceForm _form;
        public UserSchema _schema;
        public QueryDefinition _queryDef;


        @Override
        public void validate(SourceForm target, BindException errors)
        {
            _form = target;
            if (StringUtils.isEmpty(target.getSchemaName()))
                throw new NotFoundException("schema name not specified");
            if (StringUtils.isEmpty(target.getQueryName()))
                throw new NotFoundException("query name not specified");

            QuerySchema querySchema = DefaultSchema.get(getUser(), getContainer(), _form.getSchemaKey());
            if (null == querySchema)
                throw new NotFoundException("schema not found: " + _form.getSchemaKey().toDisplayString());
            if (!(querySchema instanceof UserSchema))
                throw new NotFoundException("Could not find the schema '" + _form.getSchemaName() + "' in the folder '" + getContainer().getPath() + "'");
            _schema = (UserSchema)querySchema;
        }


        @Override
        public ModelAndView getView(SourceForm form, BindException errors)
        {
            _queryDef = _schema.getQueryDef(form.getQueryName());
            if (null == _queryDef)
                _queryDef = _schema.getQueryDefForTable(form.getQueryName());
            if (null == _queryDef)
                throw new NotFoundException("Could not find the query '" + form.getQueryName() + "' in the schema '" + form.getSchemaName() + "'");

            try
            {
                if (form.ff_queryText == null)
                {
                    form.ff_queryText = _queryDef.getSql();
                    form.ff_metadataText = _queryDef.getMetadataXml();
                    if (null == form.ff_metadataText)
                        form.ff_metadataText = form.getDefaultMetadataText();
                }

                for (QueryException qpe : _queryDef.getParseErrors(_schema))
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
                LogManager.getLogger(QueryController.class).error("Error", e);
            }

            Renderable moduleWarning = null;
            if (_queryDef instanceof ModuleCustomQueryDefinition && _queryDef.canEdit(getUser()))
            {
                var mcqd = (ModuleCustomQueryDefinition)_queryDef;
                moduleWarning = DIV(cl("labkey-warning-messages"),
                        "This SQL query is defined in the '" + mcqd.getModuleName() + "' module in directory '" + mcqd.getSqlFile().getParent() + "'.",
                                BR(),
                                "Changes to this query will be reflected in all usages across different folders on the server."
                        );
            }

            var sourceQueryView = new JspView<>("/org/labkey/query/view/sourceQuery.jsp", this, errors);
            WebPartView ret = sourceQueryView;
            if (null != moduleWarning)
                ret = new VBox(new HtmlView(moduleWarning), sourceQueryView);
            return ret;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("useSqlEditor");

            addSchemaActionNavTrail(root, _form.getSchemaKey(), _form.getQueryName());

            root.addChild("Edit " + _form.getQueryName());
        }
    }


    /**
     * Ajax action to save a query. If the save is successful the request will return successfully. A query
     * with SQL syntax errors can still be saved successfully.
     *
     * If the SQL contains parse errors, a parseErrors object will be returned which contains an array of
     * JSON serialized error information.
     */
    // Trusted analysts who are editors can create and modify queries
    @RequiresAllOf({EditQueriesPermission.class, UpdatePermission.class})
    @Action(ActionType.Configure.class)
    public static class SaveSourceQueryAction extends MutatingApiAction<SourceForm>
    {
        private UserSchema _schema;

        @Override
        public void validateForm(SourceForm form, Errors errors)
        {
            if (StringUtils.isEmpty(form.getSchemaName()))
                throw new NotFoundException("Query definition not found, schemaName and queryName are required.");
            if (StringUtils.isEmpty(form.getQueryName()))
                throw new NotFoundException("Query definition not found, schemaName and queryName are required.");

            QuerySchema querySchema = DefaultSchema.get(getUser(), getContainer(), form.getSchemaKey());
            if (null == querySchema)
                throw new NotFoundException("schema not found: " + form.getSchemaKey().toDisplayString());
            if (!(querySchema instanceof UserSchema))
                throw new NotFoundException("Could not find the schema '" + form.getSchemaName() + "' in the folder '" + getContainer().getPath() + "'");
            _schema = (UserSchema)querySchema;

            XmlOptions options = XmlBeansUtil.getDefaultParseOptions();
            List<XmlError> xmlErrors = new ArrayList<>();
            options.setErrorListener(xmlErrors);
            try
            {
                // had a couple of real-world failures due to null pointers in this code, so it's time to be paranoid
                if (form.ff_metadataText != null)
                {
                    TablesDocument tablesDoc = TablesDocument.Factory.parse(form.ff_metadataText, options);
                    if (tablesDoc != null)
                    {
                        tablesDoc.validate(options);
                        TablesType tablesType = tablesDoc.getTables();
                        if (tablesType != null)
                        {
                            for (TableType tableType : tablesType.getTableArray())
                            {
                                if (null != tableType)
                                {
                                    if (!Objects.equals(tableType.getTableName(), form.getQueryName()))
                                    {
                                        errors.reject(ERROR_MSG, "Table name in the XML metadata must match the table/query name: " + form.getQueryName());
                                    }

                                    TableType.Columns tableColumns = tableType.getColumns();
                                    if (null != tableColumns)
                                    {
                                        ColumnType[] tableColumnArray = tableColumns.getColumnArray();
                                        for (ColumnType column : tableColumnArray)
                                        {
                                            if (column.isSetPhi() || column.isSetProtected())
                                            {
                                                throw new IllegalArgumentException("PHI/protected metadata must not be set here.");
                                            }

                                            ColumnType.Fk fk = column.getFk();
                                            if (null != fk)
                                            {
                                                try
                                                {
                                                    validateLookupFilter(AbstractTableInfo.parseXMLLookupFilters(fk.getFilters()), errors);
                                                }
                                                catch (ValidationException e)
                                                {
                                                    errors.reject(ERROR_MSG, e.getMessage());
                                                }
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
                throw new RuntimeValidationException(e);
            }

            for (XmlError xmle : xmlErrors)
            {
                errors.reject(ERROR_MSG, XmlBeansUtil.getErrorMessage(xmle));
            }
        }

        private void validateLookupFilter(Map<ForeignKey.FilterOperation, List<FilterType>> filterMap, Errors errors)
        {
            filterMap.forEach((operation, filters) -> {

                String displayStr = "Filter for operation : " + operation.name();
                for (FilterType filter : filters)
                {
                    if (isBlank(filter.getColumn()))
                        errors.reject(ERROR_MSG, displayStr + " requires columnName");

                    if (null == filter.getOperator())
                    {
                        errors.reject(ERROR_MSG,  displayStr + " requires operator");
                    }
                    else
                    {
                        CompareType compareType = CompareType.getByURLKey(filter.getOperator().toString());
                        if (null == compareType)
                        {
                            errors.reject(ERROR_MSG, displayStr + " operator is invalid");
                        }
                        else
                        {
                            if (compareType.isDataValueRequired() && null == filter.getValue())
                                errors.reject(ERROR_MSG, displayStr + " requires a value but none is specified");
                        }
                    }
                }

                try
                {
                    // attempt to convert to something we can query against
                    SimpleFilter.fromXml(filters.toArray(new FilterType[filters.size()]));
                }
                catch (Exception e)
                {
                    errors.reject(ERROR_MSG, e.getMessage());
                }
            });
        }

        @Override
        public ApiResponse execute(SourceForm form, BindException errors)
        {
            var queryDef = _schema.getQueryDef(form.getQueryName());
            if (null == queryDef)
                queryDef = _schema.getQueryDefForTable(form.getQueryName());
            if (null == queryDef)
                throw new NotFoundException("Could not find the query '" + form.getQueryName() + "' in the schema '" + form.getSchemaName() + "'");

            ApiSimpleResponse response = new ApiSimpleResponse();

            try
            {
                if (form.ff_queryText != null)
                {
                    if (!queryDef.isSqlEditable())
                        throw new UnauthorizedException("Query SQL is not editable.");

                    if (!queryDef.canEdit(getUser()))
                        throw new UnauthorizedException("Edit permissions are required.");

                    queryDef.setSql(form.ff_queryText);
                }

                String metadataText = StringUtils.trimToNull(form.ff_metadataText);
                if (queryDef.isMetadataEditable())
                {
                    if (!Objects.equals(metadataText, queryDef.getMetadataXml()) && !queryDef.canEditMetadata(getUser()))
                        throw new UnauthorizedException("Edit metadata permissions are required.");

                    queryDef.setMetadataXml(metadataText);
                }
                else
                {
                    if (metadataText != null)
                        throw new UnsupportedOperationException("Query metadata is not editable.");
                }

                queryDef.save(getUser(), getContainer());

                // the query was successfully saved, validate the query but return any errors in the success response
                List<QueryParseException> parseErrors = new ArrayList<>();
                List<QueryParseException> parseWarnings = new ArrayList<>();
                queryDef.validateQuery(_schema, parseErrors, parseWarnings);
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


    // Trusted analysts who are editors can create and modify queries
    @RequiresAllOf({EditQueriesPermission.class, DeletePermission.class})
    @Action(ActionType.Configure.class)
    public static class DeleteQueryAction extends ConfirmAction<SourceForm>
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
        public ModelAndView getConfirmView(SourceForm form, BindException errors)
        {
            if (getPageConfig().getTitle() == null)
                setTitle("Delete Query");
            _queryDef = QueryService.get().getQueryDef(getUser(), getContainer(), _baseSchema.getSchemaName(), form.getQueryName());

            if (null == _queryDef)
                throw new NotFoundException("Query not found: " + form.getQueryName());

            if (!_queryDef.canDelete(getUser()))
            {
                errors.reject(ERROR_MSG, "Sorry, this query can not be deleted");
            }

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
                /* reshow will throw NotFound, so just ignore */
            }
            return true;
        }

        @Override
        @NotNull
        public ActionURL getSuccessURL(SourceForm queryForm)
        {
            return ((UserSchema)_baseSchema).urlFor(QueryAction.schema);
        }
    }


    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectData.class)
    public class ExecuteQueryAction extends QueryViewAction
    {
        @Override
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            _form = form;
            QueryView queryView = null;

            if (!errors.hasErrors())
                queryView = form.getQueryView();

            if (errors.hasErrors())
                return new SimpleErrorView(errors, true);

            if (isPrint())
            {
                queryView.setPrintView(true);
                getPageConfig().setTemplate(PageConfig.Template.Print);
                getPageConfig().setShowPrintDialog(true);
            }
            queryView.setShadeAlternatingRows(true);
            queryView.setShowBorders(true);
            setHelpTopic("customSQL");
            _queryView = queryView;
            return queryView;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            new SchemaAction(_form).addNavTrail(root);
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
        }
    }


    @RequiresPermission(AdminOperationsPermission.class)
    public class RawTableMetaDataAction extends QueryViewAction
    {
        private String _dbSchemaName;
        private String _dbTableName;

        @Override
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            _form = form;

            QueryView queryView = form.getQueryView();
            String userSchemaName = queryView.getSchema().getName();
            TableInfo ti = queryView.getTable();
            if (null == ti)
                throw new NotFoundException();

            DbScope scope = ti.getSchema().getScope();

            // Test for provisioned table
            if (ti.getDomain() != null)
            {
                Domain domain = ti.getDomain();
                if (domain.getStorageTableName() != null)
                {
                    // Use the real table and schema names for getting the metadata
                    _dbTableName = domain.getStorageTableName();
                    _dbSchemaName = domain.getDomainKind().getStorageSchemaName();
                }
            }

            // No domain or domain with non-provisioned storage (e.g., core.Users)
            if (null == _dbSchemaName || null == _dbTableName)
            {
                DbSchema dbSchema = ti.getSchema();
                _dbSchemaName = dbSchema.getName();

                // Try to get the underlying schema table and use the meta data name, #12015
                if (ti instanceof FilteredTable)
                    ti = ((FilteredTable) ti).getRealTable();

                if (ti instanceof SchemaTableInfo)
                    _dbTableName = ti.getMetaDataName();
                else if (ti instanceof LinkedTableInfo)
                    _dbTableName = ti.getName();

                if (null == _dbTableName)
                {
                    TableInfo tableInfo = dbSchema.getTable(ti.getName());
                    if (null != tableInfo)
                        _dbTableName = tableInfo.getMetaDataName();
                }
            }

            if (null != _dbTableName)
            {
                VBox result = new VBox();

                ActionURL url = new ActionURL(RawSchemaMetaDataAction.class, getContainer());
                url.addParameter("schemaName", userSchemaName);

                SqlDialect dialect = scope.getSqlDialect();
                ScopeView scopeInfo = new ScopeView("Scope and Schema Information", scope, _dbSchemaName, url, _dbTableName);

                result.addView(scopeInfo);

                try (JdbcMetaDataLocator locator = dialect.getTableResolver().getSingleTableLocator(scope, _dbSchemaName, _dbTableName))
                {
                    JdbcMetaDataSelector columnSelector = new JdbcMetaDataSelector(locator,
                            (dbmd, l) -> dbmd.getColumns(l.getCatalogName(), l.getSchemaNamePattern(), l.getTableNamePattern(), null));
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
            else
            {
                errors.reject(ERROR_MSG, "Raw metadata not accessible for table " + ti.getName());
                return new SimpleErrorView(errors);
            }
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            (new SchemaAction(_form)).addNavTrail(root);
            if (null != _dbTableName)
                root.addChild("JDBC Meta Data For Table \"" + _dbSchemaName + "." + _dbTableName + "\"");
        }
    }


    @RequiresPermission(AdminOperationsPermission.class)
    public class RawSchemaMetaDataAction extends SimpleViewAction<Object>
    {
        private String _schemaName;

        @Override
        public ModelAndView getView(Object form, BindException errors) throws Exception
        {
            _schemaName = getViewContext().getActionURL().getParameter("schemaName");
            if (null == _schemaName)
                throw new NotFoundException();
            QuerySchema qs = DefaultSchema.get(getUser(), getContainer()).getSchema(_schemaName);
            if (null == qs)
                throw new NotFoundException(_schemaName);
            DbSchema schema = qs.getDbSchema();
            String dbSchemaName = schema.getName();
            DbScope scope = schema.getScope();
            SqlDialect dialect = scope.getSqlDialect();

            HttpView scopeInfo = new ScopeView("Scope Information", scope);

            ModelAndView tablesView;

            try (JdbcMetaDataLocator locator = dialect.getTableResolver().getAllTablesLocator(scope, dbSchemaName))
            {
                JdbcMetaDataSelector selector = new JdbcMetaDataSelector(locator,
                    (dbmd, locator1) -> dbmd.getTables(locator1.getCatalogName(), locator1.getSchemaNamePattern(), locator1.getTableNamePattern(), null));

                ActionURL url = new ActionURL(RawTableMetaDataAction.class, getContainer())
                    .addParameter("schemaName", _schemaName)
                    .addParameter("query.queryName", null);
                String tableLink = url.getEncodedLocalURIString();
                tablesView = new ResultSetView(CachedResultSets.create(selector.getResultSet(), true, Table.ALL_ROWS), "Tables", 3, tableLink)
                {
                    @Override
                    protected boolean shouldLink(ResultSet rs) throws SQLException
                    {
                        // Only link to tables and views (not indexes or sequences). And only if they're defined in the query schema.
                        String name = rs.getString(3);
                        String type = rs.getString(4);
                        return ("TABLE".equalsIgnoreCase(type) || "VIEW".equalsIgnoreCase(type)) && qs.getTableNames().contains(name);
                    }
                };
            }

            return new VBox(scopeInfo, tablesView);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("JDBC Meta Data For Schema \"" + _schemaName + "\"");
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
        protected void renderView(Object model, PrintWriter out)
        {
            StringBuilder sb = new StringBuilder("<table>\n");

            if (null != _schemaName)
                sb.append("<tr><td class=\"labkey-form-label\">Schema</td><td><a href=\"").append(PageFlowUtil.filter(_url)).append("\">").append(PageFlowUtil.filter(_schemaName)).append("</a></td></tr>\n");
            if (null != _tableName)
                sb.append("<tr><td class=\"labkey-form-label\">Table</td><td>").append(PageFlowUtil.filter(_tableName)).append("</td></tr>\n");

            sb.append("<tr><td class=\"labkey-form-label\">Scope</td><td>").append(_scope.getDisplayName()).append("</td></tr>\n");
            sb.append("<tr><td class=\"labkey-form-label\">Dialect</td><td>").append(_scope.getSqlDialect().getClass().getSimpleName()).append("</td></tr>\n");
            sb.append("<tr><td class=\"labkey-form-label\">URL</td><td>").append(_scope.getDatabaseUrl()).append("</td></tr>\n");
            sb.append("</table>\n");

            out.print(sb);
        }
    }


    // for backwards compat same as _executeQuery.view ?_print=1
    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.Export.class)
    public class PrintRowsAction extends ExecuteQueryAction
    {
        @Override
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            _print = true;
            ModelAndView result = super.getView(form, errors);
            String title = form.getQueryName();
            if (StringUtils.isEmpty(title))
                title = form.getSchemaName();
            getPageConfig().setTitle(title, true);
            return result;
        }
    }


    abstract static class _ExportQuery<K extends ExportQueryForm> extends SimpleViewAction<K>
    {
        @Override
        public ModelAndView getView(K form, BindException errors) throws Exception
        {
            QueryView view = form.getQueryView();
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

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }


    @SuppressWarnings({"unused", "WeakerAccess"})
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
    @CSRF(CSRF.Method.ALL)
    public static class ExportScriptAction extends SimpleViewAction<ExportScriptForm>
    {
        @Override
        public void validate(ExportScriptForm form, BindException errors)
        {
            // calling form.getQueryView() as a validation check as it will throw if schema/query missing
            form.getQueryView();

            if (StringUtils.isEmpty(form.getScriptType()))
                throw new NotFoundException("Missing required parameter: scriptType.");
        }

        @Override
        public ModelAndView getView(ExportScriptForm form, BindException errors)
        {
            return ExportScriptModel.getExportScriptView(QueryView.create(form, errors), form.getScriptType(), getPageConfig(), getViewContext().getResponse());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }


    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.Export.class)
    public static class ExportRowsExcelAction extends _ExportQuery<ExportQueryForm>
    {
        @Override
        void _export(ExportQueryForm form, QueryView view) throws Exception
        {
            view.exportToExcel(getViewContext().getResponse(), form.getHeaderType(), ExcelWriter.ExcelDocumentType.xls, form.getRenameColumnMap());
        }
    }

    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.Export.class)
    public static class ExportRowsXLSXAction extends _ExportQuery<ExportQueryForm>
    {
        @Override
        void _export(ExportQueryForm form, QueryView view) throws Exception
        {
            view.exportToExcel(getViewContext().getResponse(), form.getHeaderType(), ExcelWriter.ExcelDocumentType.xlsx, form.getRenameColumnMap());
        }
    }

    public static class ExportQueriesForm extends ExportQueryForm implements CustomApiForm
    {
        private String filename;
        private List<ExportQueryForm> queryForms;

        public void setFilename(String filename)
        {
            this.filename = filename;
        }

        public String getFilename()
        {
            return filename;
        }

        public void setQueryForms(List<ExportQueryForm> queryForms)
        {
            this.queryForms = queryForms;
        }

        public List<ExportQueryForm> getQueryForms()
        {
            return queryForms;
        }

        /**
         * Map JSON to Spring PropertyValue objects.
         * @param props
         */
        private MutablePropertyValues getPropertyValues(JSONObject props)
        {
            // Collecting mapped properties as a list because adding them to an existing MutablePropertyValues object replaces existing values
            List<PropertyValue> properties = new ArrayList<>();

            for(Map.Entry<String, Object> entry : props.entrySet())
            {
                String key = entry.getKey();
                if (entry.getValue() instanceof JSONArray)
                {
                    // Split arrays into individual pairs to be bound (Issue #45452)
                    JSONArray val = (JSONArray) entry.getValue();
                    for(int i=0; i < val.length();i++)
                    {
                        properties.add(new PropertyValue(key, val.getString(i)));
                    }
                }
                else
                {
                    properties.add(new PropertyValue(key, entry.getValue()));
                }
            }

            return new MutablePropertyValues(properties);
        }

        @Override
        public void bindProperties(Map<String, Object> props)
        {
            setFilename(props.get("filename").toString());
            List<ExportQueryForm> forms = new ArrayList<>();

            JSONArray models = (JSONArray)props.get("queryForms");
            if (models == null)
            {
                QueryController.LOG.error("No models to export; Form's `queryForms` property was null");
                throw new RuntimeValidationException("No queries to export; Form's `queryForms` property was null");
            }

            for (JSONObject queryModel : models.toJSONObjectArray())
            {
                ExportQueryForm qf = new ExportQueryForm();
                qf.setViewContext(getViewContext());

                qf.bindParameters(getPropertyValues(queryModel));
                forms.add(qf);
            }

            setQueryForms(forms);
        }
    }

    /**
     * Export multiple query forms
     */
    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.Export.class)
    public static class ExportQueriesXLSXAction extends ReadOnlyApiAction<ExportQueriesForm>
    {
        @Override
        public Object execute(ExportQueriesForm form, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(PageConfig.Template.None);
            HttpServletResponse response = getViewContext().getResponse();
            response.setHeader("X-Robots-Tag", "noindex");
            response.setHeader("Content-Disposition", "attachment");
            ViewContext viewContext = getViewContext();

            ExcelWriter writer = new ExcelWriter(ExcelWriter.ExcelDocumentType.xlsx) {
                @Override
                protected void renderSheets(Workbook workbook)
                {
                    for (ExportQueryForm qf : form.getQueryForms())
                    {
                        qf.setViewContext(viewContext);
                        qf.getSchema();

                        QueryView qv = qf.getQueryView();
                        QueryView.ExcelExportConfig config = new QueryView.ExcelExportConfig(response, qf.getHeaderType())
                            .setExcludeColumns(qf.getExcludeColumns())
                            .setRenamedColumns(qf.getRenameColumnMap());
                        String sheetName = qf.getSheetName();
                        qv.configureExcelWriter(this, config);
                        String name = StringUtils.isNotBlank(sheetName)? sheetName : qv.getQueryDef().getName();
                        name = StringUtils.isNotBlank(name)? name : StringUtils.isNotBlank(qv.getDataRegionName()) ? qv.getDataRegionName() : "Data";
                        setSheetName(name);
                        setAutoSize(true);
                        renderNewSheet(workbook);
                        qv.logAuditEvent("Exported to Excel", getDataRowCount());
                    }

                    workbook.setActiveSheet(0);
                }
            };
            writer.setFilenamePrefix(form.getFilename());
            writer.renderWorkbook(response);
            return null; //Returning anything here will cause error as excel writer will close the response stream
        }
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public static class TemplateForm extends ExportQueryForm
    {
        boolean insertColumnsOnly = true;
        String filenamePrefix;
        FieldKey[] includeColumn;
        String fileType;

        public TemplateForm()
        {
            _headerType = ColumnHeaderType.Caption;
        }

        // "captionType" field backwards compatibility
        public void setCaptionType(ColumnHeaderType headerType)
        {
            _headerType = headerType;
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
            return includeColumn;
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
            filenamePrefix = prefix;
        }

        public String getFileType()
        {
            return fileType;
        }

        public void setFileType(String fileType)
        {
            this.fileType = fileType;
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
     *     <dt>excludeColumn</dt>
     *     <dd>List of column names to exclude.
     *     </dd>
     *
     *     <dt>exportAlias.columns</dt>
     *     <dd>Use alternative column name in excel: exportAlias.originalColumnName=aliasColumnName
     *     </dd>
     *
     *     <dt>captionType</dt>
     *     <dd>determines which column property is used in the header.  either Label or Name</dd>
     * </dl>
     */
    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.Export.class)
    public static class ExportExcelTemplateAction extends _ExportQuery<TemplateForm>
    {
        public ExportExcelTemplateAction()
        {
            setCommandClass(TemplateForm.class);
        }

        @Override
        void _export(TemplateForm form, QueryView view) throws Exception
        {
            boolean respectView = form.getViewName() != null;
            ExcelWriter.ExcelDocumentType fileType = ExcelWriter.ExcelDocumentType.xlsx;
            if (form.getFileType() != null)
            {
                try
                {
                    fileType = ExcelWriter.ExcelDocumentType.valueOf(form.getFileType().toLowerCase());
                }
                catch (IllegalArgumentException ignored) {}
            }
            view.exportToExcel( new QueryView.ExcelExportConfig(getViewContext().getResponse(), form.getHeaderType())
                    .setTemplateOnly(true)
                    .setInsertColumnsOnly(form.insertColumnsOnly)
                    .setDocType(fileType)
                    .setRespectView(respectView)
                    .setIncludeColumns(form.getIncludeColumns())
                    .setExcludeColumns(form.getExcludeColumns())
                    .setRenamedColumns(form.getRenameColumnMap())
                    .setPrefix(form.getFilenamePrefix())
            );
        }
    }


    @SuppressWarnings({"unused", "WeakerAccess"})
    public static class ExportQueryForm extends QueryForm
    {
        protected ColumnHeaderType _headerType = null; // QueryView will provide a default header type if the user doesn't select one
        FieldKey[] excludeColumn;
        Map<String, String> renameColumns = null;
        private String sheetName;

        public void setSheetName(String sheetName)
        {
            this.sheetName = sheetName;
        }

        public String getSheetName()
        {
            return sheetName;
        }

        public ColumnHeaderType getHeaderType()
        {
            return _headerType;
        }

        public void setHeaderType(ColumnHeaderType headerType)
        {
            _headerType = headerType;
        }

        public List<FieldKey> getExcludeColumns()
        {
            if (excludeColumn == null || excludeColumn.length == 0)
                return Collections.emptyList();
            return Arrays.asList(excludeColumn);
        }

        public void setExcludeColumn(FieldKey[] excludeColumn)
        {
            this.excludeColumn = excludeColumn;
        }

        public Map<String, String> getRenameColumnMap()
        {
            if (renameColumns != null)
                return renameColumns;

            renameColumns = new CaseInsensitiveHashMap<>();
            final String renameParamPrefix = "exportAlias.";
            PropertyValue[] pvs = getInitParameters().getPropertyValues();
            for (PropertyValue pv : pvs)
            {
                String paramName = pv.getName();
                if (!paramName.startsWith(renameParamPrefix) || pv.getValue() == null)
                    continue;

                renameColumns.put(paramName.substring(renameParamPrefix.length()), (String) pv.getValue());
            }

            return renameColumns;
        }
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
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
    public static class ExportRowsTsvAction extends _ExportQuery<ExportRowsTsvForm>
    {
        public ExportRowsTsvAction()
        {
            setCommandClass(ExportRowsTsvForm.class);
        }

        @Override
        void _export(ExportRowsTsvForm form, QueryView view) throws Exception
        {
            view.exportToTsv(getViewContext().getResponse(), form.getDelim(), form.getQuote(), form.getHeaderType(), form.getRenameColumnMap());
        }
    }


    @RequiresNoPermission
    @IgnoresTermsOfUse
    @Action(ActionType.Export.class)
    public static class ExcelWebQueryAction extends ExportRowsTsvAction
    {
        @Override
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
            ResponseHelper.setPrivate(response);

            QueryView view = form.getQueryView();
            getPageConfig().setTemplate(PageConfig.Template.None);
            view.exportToExcelWebQuery(getViewContext().getResponse());
            return null;
        }
    }


    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.Export.class)
    public class ExcelWebQueryDefinitionAction extends SimpleViewAction<QueryForm>
    {
        @Override
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            getPageConfig().setTemplate(PageConfig.Template.None);
            form.getQueryView();
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
            String filename = FileUtil.makeFileNameWithTimestamp(form.getQueryName(), "iqy");
            getViewContext().getResponse().setHeader("Content-disposition", "attachment; filename=\"" + filename + "\"");
            PrintWriter writer = getViewContext().getResponse().getWriter();
            writer.println("WEB");
            writer.println("1");
            writer.println(url.getURIString());

            QueryService.get().addAuditEvent(getUser(), getContainer(), form.getSchemaName(), form.getQueryName(), url, "Exported to Excel Web Query definition", null);
            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    // Trusted analysts who are editors can create and modify queries
    @RequiresAllOf({EditQueriesPermission.class, UpdatePermission.class})
    @Action(ActionType.SelectMetaData.class)
    public class MetadataQueryAction extends SimpleViewAction<QueryForm>
    {
        QueryForm _form = null;

        @Override
        public ModelAndView getView(QueryForm queryForm, BindException errors) throws Exception
        {
            String schemaName = queryForm.getSchemaName();
            String queryName = queryForm.getQueryName();

            _form = queryForm;

            if (schemaName.isEmpty() && (null == queryName || queryName.isEmpty()))
            {
                throw new NotFoundException("Must provide schemaName and queryName.");
            }

            if (schemaName.isEmpty())
            {
                throw new NotFoundException("Must provide schemaName.");
            }

            if (null == queryName || queryName.isEmpty())
            {
                throw new NotFoundException("Must provide queryName.");
            }

            if (!queryForm.getQueryDef().isMetadataEditable())
                throw new UnauthorizedException("Query metadata is not editable");

            if (!queryForm.canEditMetadata())
                throw new UnauthorizedException("You do not have permission to edit the query metadata");

            return ModuleHtmlView.get(ModuleLoader.getInstance().getModule("core"), ModuleHtmlView.getGeneratedViewPath("queryMetadataEditor"));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            new SchemaAction(_form).addNavTrail(root);
            var metadataQuery = _form.getQueryDef().getName();
            if (null != metadataQuery)
                root.addChild("Edit Metadata: " + _form.getQueryName(), metadataQuery);
            else
                root.addChild("Edit Metadata: " + _form.getQueryName());
        }
    }

    // Uck. Supports the old and new view designer.
    protected Map<String, Object> saveCustomView(Container container, QueryDefinition queryDef,
                                                 String regionName, String viewName, boolean replaceExisting,
                                                 boolean share, boolean inherit,
                                                 boolean session, boolean saveFilter,
                                                 boolean hidden, JSONObject jsonView,
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

        if (name != null && RESERVED_VIEW_NAMES.contains(name.toLowerCase()))
            errors.reject(ERROR_MSG, "The grid view name '" + name + "' is not allowed.");

        boolean isHidden = hidden;
        CustomView view;
        if (owner == null)
            view = queryDef.getSharedCustomView(name);
        else
            view = queryDef.getCustomView(owner, getViewContext().getRequest(), name);

        if (view != null && !replaceExisting && !StringUtils.isEmpty(name))
            errors.reject(ERROR_MSG, "A saved view by the name \"" + viewName + "\" already exists. ");

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
                view.setIsHidden(hidden);
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
                        Map<String, Object> ret = saveCustomView(container, queryDef, regionName, viewName, replaceExisting, share, inherit, session, saveFilter, hidden, jsonView, srcURL, errors);
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
            CustomViewImpl cview = null;
            if (view instanceof EditableCustomView && view.isOverridable())
            {
                cview = ((EditableCustomView)view).getEditableViewInfo(owner, session);
            }
            if (null == cview)
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
    public class SaveQueryViewsAction extends MutatingApiAction<SimpleApiJsonForm>
    {
        @Override
        public ApiResponse execute(SimpleApiJsonForm form, BindException errors)
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
                boolean replace = jsonView.optBoolean("replace", true); // "replace" was the default before the flag is introduced
                boolean inherit = jsonView.optBoolean("inherit", false);
                boolean session = jsonView.optBoolean("session", false);
                boolean hidden = jsonView.optBoolean("hidden", false);
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
                    container = getContainer().getContainerFor(ContainerType.DataType.customQueryViews);
                }

                if (container == null)
                {
                    throw new NotFoundException("No such container: " + containerPath);
                }

                Map<String, Object> savedView = saveCustomView(
                        container, queryDef, QueryView.DATAREGIONNAME_DEFAULT, viewName, replace,
                        shared, inherit, session, true, hidden, jsonView, null, errors);

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

    public static class RenameQueryViewForm extends QueryForm
    {
        private String newName;

        public String getNewName()
        {
            return newName;
        }

        public void setNewName(String newName)
        {
            this.newName = newName;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class RenameQueryViewAction extends MutatingApiAction<RenameQueryViewForm>
    {
        @Override
        public ApiResponse execute(RenameQueryViewForm form, BindException errors)
        {
            CustomView view = form.getCustomView();
            if (view == null)
            {
                throw new NotFoundException();
            }

            Container container = getContainer();
            User user = getUser();

            String schemaName = form.getSchemaName();
            String queryName = form.getQueryName();
            if (schemaName == null || queryName == null)
                throw new NotFoundException("schemaName and queryName are required");

            UserSchema schema = QueryService.get().getUserSchema(user, container, schemaName);
            if (schema == null)
                throw new NotFoundException("schema not found");

            QueryDefinition queryDef = QueryService.get().getQueryDef(user, container, schemaName, queryName);
            if (queryDef == null)
                queryDef = schema.getQueryDefForTable(queryName);
            if (queryDef == null)
                throw new NotFoundException("query not found");

            renameCustomView(container, queryDef, view, form.getNewName(), errors);

            if (errors.hasErrors())
                return null;
            else
                return new ApiSimpleResponse("success", true);
        }
    }

    protected void renameCustomView(Container container, QueryDefinition queryDef, CustomView fromView, String newViewName, BindException errors)
    {
        if (newViewName != null && RESERVED_VIEW_NAMES.contains(newViewName.toLowerCase()))
            errors.reject(ERROR_MSG, "The grid view name '" + newViewName + "' is not allowed.");

        String newName = StringUtils.trimToNull(newViewName);
        if (StringUtils.isEmpty(newName))
            errors.reject(ERROR_MSG, "View name cannot be blank.");

        if (errors.hasErrors())
            return;

        User owner = getUser();
        boolean canSaveForAllUsers = container.hasPermission(getUser(), EditSharedViewPermission.class);

        if (!fromView.canEdit(container, errors))
            return;

        if (fromView.isSession())
        {
            errors.reject(ERROR_MSG, "Cannot rename a session view.");
            return;
        }

        CustomView duplicateView = queryDef.getCustomView(owner, getViewContext().getRequest(), newName);
        if (duplicateView == null && canSaveForAllUsers)
            duplicateView = queryDef.getSharedCustomView(newName);
        if (duplicateView != null)
        {
            // only allow duplicate view name if creating a new private view to shadow an existing shared view
            if (!(!fromView.isShared() && duplicateView.isShared()))
            {
                errors.reject(ERROR_MSG, "Another saved view by the name \"" + newName + "\" already exists. ");
                return;
            }
        }

        fromView.setName(newViewName);
        fromView.save(getUser(), getViewContext().getRequest());
    }

    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.Configure.class)
    public class PropertiesQueryAction extends FormViewAction<PropertiesForm>
    {
        PropertiesForm _form = null;
        private String _queryName;

        @Override
        public void validateCommand(PropertiesForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(PropertiesForm form, boolean reshow, BindException errors)
        {
            // assertQueryExists requires that it be well-formed
            // assertQueryExists(form);
            QueryDefinition queryDef = form.getQueryDef();
            _form = form;
            _form.setDescription(queryDef.getDescription());
            _form.setInheritable(queryDef.canInherit());
            _form.setHidden(queryDef.isHidden());
            setHelpTopic("editQueryProperties");
            _queryName = form.getQueryName();

            return new JspView<>("/org/labkey/query/view/propertiesQuery.jsp", form, errors);
        }

        @Override
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
            if (!queryDef.getDefinitionContainer().getId().equals(getContainer().getId()))
                throw new NotFoundException("Query not found");

			_form = form;

			if (!StringUtils.isEmpty(form.rename) && !form.rename.equalsIgnoreCase(queryDef.getName()))
			{
                // issue 17766: check if query or table exist with this name
                if (null != QueryManager.get().getQueryDef(getContainer(), form.getSchemaName(), form.rename, true)
                    || null != form.getSchema().getTable(form.rename,null))
                {
                    errors.reject(ERROR_MSG, "A query or table with the name \"" + form.rename + "\" already exists.");
                    return false;
                }

                // Issue 40895: update queryName in xml metadata
                updateXmlMetadata(queryDef);
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

        private void updateXmlMetadata(QueryDefinition queryDef) throws XmlException
        {
            if (null != queryDef.getMetadataXml())
            {
                TablesDocument doc = TablesDocument.Factory.parse(queryDef.getMetadataXml());
                if (null != doc)
                {
                    for (TableType tableType : doc.getTables().getTableArray())
                    {
                        if (tableType.getTableName().equalsIgnoreCase(queryDef.getName()))
                        {
                            // update tableName in xml
                            tableType.setTableName(_form.rename);
                        }
                    }
                    XmlOptions xmlOptions = new XmlOptions();
                    xmlOptions.setSavePrettyPrint();
                    // Don't use an explicit namespace, making the XML much more readable
                    xmlOptions.setUseDefaultNamespace();
                    queryDef.setMetadataXml(doc.xmlText(xmlOptions));
                }
            }
        }

        @Override
        public ActionURL getSuccessURL(PropertiesForm propertiesForm)
        {
            ActionURL url = new ActionURL(BeginAction.class, propertiesForm.getViewContext().getContainer());
            url.addParameter("schemaName", propertiesForm.getSchemaName());
            if (null != _queryName)
                url.addParameter("queryName", _queryName);
            return url;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            new SchemaAction(_form).addNavTrail(root);
            root.addChild("Edit query properties");
        }
    }

    @ActionNames("truncateTable")
    @RequiresPermission(AdminPermission.class)
    public static class TruncateTableAction extends MutatingApiAction<QueryForm>
    {
        UserSchema schema;
        TableInfo table;

        @Override
        public void validateForm(QueryForm form, Errors errors)
        {
            String schemaName = form.getSchemaName();
            String queryName = form.getQueryName();

            if (isBlank(schemaName) || isBlank(queryName))
                throw new NotFoundException("schemaName and queryName are required");

            schema = QueryService.get().getUserSchema(getUser(), getContainer(), schemaName);
            if (null == schema)
                throw new NotFoundException("The schema '" + schemaName + "' does not exist.");

            table = schema.getTable(queryName, null);
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
    public static class DeleteQueryRowsAction extends FormHandlerAction<QueryForm>
    {
        @Override
        public void validateCommand(QueryForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(QueryForm form, BindException errors)
        {
            TableInfo table = form.getQueryView().getTable();

            if (!table.hasPermission(getUser(), DeletePermission.class))
            {
                throw new UnauthorizedException();
            }

            QueryUpdateService updateService = table.getUpdateService();
            if (updateService == null)
                throw new UnsupportedOperationException("Unable to delete - no QueryUpdateService registered for " + form.getSchemaName() + "." + form.getQueryName());

            Set<String> ids = DataRegionSelection.getSelected(form.getViewContext(), null, true);
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
            try
            {
                dbSchema.getScope().executeWithRetry(tx ->
                {
                    try
                    {
                        updateService.deleteRows(getUser(), getContainer(), keyValues, null, null);
                    }
                    catch (SQLException x)
                    {
                        if (!RuntimeSQLException.isConstraintException(x))
                            throw new RuntimeSQLException(x);
                        errors.reject(ERROR_MSG, getMessage(table.getSchema().getSqlDialect(), x));
                    }
                    catch (DataIntegrityViolationException | OptimisticConflictException e)
                    {
                        errors.reject(ERROR_MSG, e.getMessage());
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
                    // need to throw here to avoid committing tx
                    if (errors.hasErrors())
                        throw new DbScope.RetryPassthroughException(errors);
                    return true;
                });
            }
            catch (DbScope.RetryPassthroughException x)
            {
                if (x.getCause() != errors)
                    x.throwRuntimeException();
            }
            return !errors.hasErrors();
        }

        @Override
        public ActionURL getSuccessURL(QueryForm form)
        {
            return form.getReturnActionURL();
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class DetailsQueryRowAction extends UserSchemaAction
    {
        @Override
        public ModelAndView getView(QueryUpdateForm tableForm, boolean reshow, BindException errors)
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

                    if (null != historyView)
                    {
                        historyView.setFrame(WebPartView.FrameType.PORTAL);
                        historyView.setTitle("History");

                        view.addView(historyView);
                    }
                }
            }
            return view;
        }

        @Override
        public boolean handlePost(QueryUpdateForm tableForm, BindException errors)
        {
            return false;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            super.addNavTrail(root);
            root.addChild("Details");
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
                form.setSuccessUrl(new ReturnURLString(m.getPropertyValue(ActionURL.Param.successUrl.name()).getValue().toString()));
            return bind;
        }

        Map<String, Object> insertedRow = null;

        @Override
        public ModelAndView getView(QueryUpdateForm tableForm, boolean reshow, BindException errors)
        {
            if (getPageConfig().getTitle() == null)
                setTitle("Insert Row");

            InsertView view = new InsertView(tableForm, errors);
            view.getDataRegion().setButtonBar(createSubmitCancelButtonBar(tableForm));
            return view;
        }

        @Override
        public boolean handlePost(QueryUpdateForm tableForm, BindException errors)
        {
            List<Map<String, Object>> list = doInsertUpdate(tableForm, errors, true);
            if (null != list && list.size() == 1)
                insertedRow = list.get(0);
            return 0 == errors.getErrorCount();
        }

        /**
         * NOTE: UserSchemaAction.addNavTrail() uses this method getSuccessURL() for the nav trail link (form==null).
         * It is used for where to go on success, and also as a "back" link in the nav trail
         * If there is a setSuccessUrl specified, we will use that for successful submit
         */
        @Override
        public ActionURL getSuccessURL(QueryUpdateForm form)
        {
            if (null == form)
                return super.getSuccessURL(null);

            String str = null;
            if (form.getSuccessUrl() != null)
                str = form.getSuccessUrl().toString();
            if (isBlank(str))
                str = form.getReturnUrl();

            if (StringUtils.equals(str, "details.view"))
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
                if (!isBlank(str))
                    return new ActionURL(str);
            }
            catch (IllegalArgumentException x)
            {
                // pass
            }
            return super.getSuccessURL(form);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            super.addNavTrail(root);
            root.addChild("Insert " + _table.getName());
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public static class UpdateQueryRowAction extends UserSchemaAction
    {
        @Override
        public ModelAndView getView(QueryUpdateForm tableForm, boolean reshow, BindException errors)
        {
            ButtonBar bb = createSubmitCancelButtonBar(tableForm);
            UpdateView view = new UpdateView(tableForm, errors);
            view.getDataRegion().setButtonBar(bb);
            return view;
        }

        @Override
        public boolean handlePost(QueryUpdateForm tableForm, BindException errors) throws Exception
        {
            doInsertUpdate(tableForm, errors, false);
            return 0 == errors.getErrorCount();
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            super.addNavTrail(root);
            root.addChild("Edit " + _table.getName());
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
            boolean ret;

            if (tableForm.isDataSubmit())
            {
                ret = super.handlePost(tableForm, errors);
                if (ret)
                    DataRegionSelection.clearAll(getViewContext(), null);  // in case we altered primary keys, see issue #35055
                return ret;
            }

            return false;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Edit Multiple " + _table.getName());
        }
    }

    // alias
    public static class DeleteAction extends DeleteQueryRowsAction
    {
    }

    public abstract static class QueryViewAction extends SimpleViewAction<QueryForm>
    {
        QueryForm _form;
        QueryView _queryView;
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public static class APIQueryForm extends ContainerFilterQueryForm
    {
        private Integer _start;
        private Integer _limit;
        private boolean _includeDetailsColumn = false;
        private boolean _includeUpdateColumn = false;
        private boolean _includeTotalCount = true;
        private boolean _includeStyle = false;
        private boolean _includeDisplayValues = false;
        private boolean _minimalColumns = true;
        private boolean _includeMetadata = true;

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

        public boolean isIncludeMetadata()
        {
            return _includeMetadata;
        }

        public void setIncludeMetadata(boolean includeMetadata)
        {
            _includeMetadata = includeMetadata;
        }

        @Override
        protected QuerySettings createQuerySettings(UserSchema schema)
        {
            QuerySettings results = super.createQuerySettings(schema);

            boolean missingShowRows = null == getViewContext().getRequest().getParameter(getDataRegionName() + "." + QueryParam.showRows);
            if (null == getLimit() && !results.isMaxRowsSet() && missingShowRows)
            {
                results.setShowRows(ShowRows.PAGINATED);
                results.setMaxRows(DEFAULT_API_MAX_ROWS);
            }

            if (getLimit() != null)
            {
                results.setShowRows(ShowRows.PAGINATED);
                results.setMaxRows(getLimit());
            }
            if (getStart() != null)
                results.setOffset(getStart());

            return results;
        }
    }

    public static final int DEFAULT_API_MAX_ROWS = 100000;

    @CSRF(CSRF.Method.NONE) // No need for CSRF token --- this is a non-mutating action that supports POST to allow for large payloads, see #36056
    @ActionNames("selectRows, getQuery")
    @RequiresPermission(ReadPermission.class)
    @ApiVersion(9.1)
    @Action(ActionType.SelectData.class)
    public class SelectRowsAction extends ReadOnlyApiAction<APIQueryForm>
    {
        @Override
        public ApiResponse execute(APIQueryForm form, BindException errors)
        {
            // Issue 12233: add implicit maxRows=100k when using client API
            QueryView view = form.getQueryView();

            view.setShowPagination(form.isIncludeTotalCount());

            //if viewName was specified, ensure that it was actually found and used
            //QueryView.create() will happily ignore an invalid view name and just return the default view
            if (null != StringUtils.trimToNull(form.getViewName()) &&
                    null == view.getQueryDef().getCustomView(getUser(), getViewContext().getRequest(), form.getViewName()))
            {
                throw new NotFoundException("The requested view does not exist for this user!");
            }

            TableInfo t = view.getTable();
            if (null == t)
            {
                List<QueryException> qpes = view.getParseErrors();
                if (!qpes.isEmpty())
                    throw qpes.get(0);
                throw new NotFoundException(form.getQueryName());
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
                        metaDataOnly, form.isIncludeDetailsColumn(), form.isIncludeUpdateColumn(), form.isIncludeMetadata());
                fancyResponse.arrayMultiValueColumns(arrayMultiValueColumns);
                fancyResponse.includeFormattedValue(includeFormattedValue);
                response = fancyResponse;
            }
            //if requested version is >= 9.1, use the extended api query response
            else if (getRequestedApiVersion() >= 9.1)
            {
                response = new ExtendedApiQueryResponse(view, isEditable, true,
                        form.getSchemaName(), form.getQueryName(), form.getQuerySettings().getOffset(), null,
                        metaDataOnly, form.isIncludeDetailsColumn(), form.isIncludeUpdateColumn(), form.isIncludeMetadata());
            }
            else
            {
                response = new ApiQueryResponse(view, isEditable, true,
                        form.getSchemaName(), form.getQueryName(), form.getQuerySettings().getOffset(), null,
                        metaDataOnly, form.isIncludeDetailsColumn(), form.isIncludeUpdateColumn(),
                        form.isIncludeDisplayValues(), form.isIncludeMetadata());
            }
            response.includeStyle(form.isIncludeStyle());

            // Issues 29515 and 32269 - force key and other non-requested columns to be sent back, but only if the client has
            // requested minimal columns, as we now do for ExtJS stores
            if (form.isMinimalColumns())
            {
                // Be sure to use the settings from the view, as it may have swapped it out with a customized version.
                // See issue 38747.
                response.setColumnFilter(view.getSettings().getFieldKeys());
            }

            return response;
        }
    }

    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectData.class)
    public static class GetDataAction extends ReadOnlyApiAction<SimpleApiJsonForm>
    {
        @Override
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

    @SuppressWarnings({"unused", "WeakerAccess"})
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

        @Override
        public void setLimit(Integer limit)
        {
            _maxRows = limit;
        }

        @Override
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

    @CSRF(CSRF.Method.NONE) // No need for CSRF token --- this is a non-mutating action that supports POST to allow for large payloads, see #36056
    @RequiresPermission(ReadPermission.class)
    @ApiVersion(9.1)
    @Action(ActionType.SelectData.class)
    public class ExecuteSqlAction extends ReadOnlyApiAction<ExecuteSqlForm>
    {
        @Override
        public ApiResponse execute(ExecuteSqlForm form, BindException errors)
        {
            form.ensureSchemaExists();

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

            ApiQueryResponse response;

            // 13.2 introduced the getData API action, a condensed response wire format, and a js wrapper to consume the wire format. Support this as an option for legacy APIs.
            if (getRequestedApiVersion() >= 13.2)
            {
                ReportingApiQueryResponse fancyResponse = new ReportingApiQueryResponse(view, isEditable, false, form.isSaveInSession() ? settings.getQueryName() : "sql", offset, null,
                        metaDataOnly, form.isIncludeDetailsColumn(), form.isIncludeUpdateColumn(), form.isIncludeMetadata());
                fancyResponse.arrayMultiValueColumns(arrayMultiValueColumns);
                fancyResponse.includeFormattedValue(includeFormattedValue);
                response = fancyResponse;
            }
            else if (getRequestedApiVersion() >= 9.1)
            {
                response = new ExtendedApiQueryResponse(view, isEditable,
                        false, schemaName, form.isSaveInSession() ? settings.getQueryName() : "sql", offset, null,
                        metaDataOnly, form.isIncludeDetailsColumn(), form.isIncludeUpdateColumn(), form.isIncludeMetadata());
            }
            else
            {
                response = new ApiQueryResponse(view, isEditable,
                        false, schemaName, form.isSaveInSession() ? settings.getQueryName() : "sql", offset, null,
                        metaDataOnly, form.isIncludeDetailsColumn(), form.isIncludeUpdateColumn(),
                        form.isIncludeDisplayValues());
            }
            response.includeStyle(form.isIncludeStyle());

            return response;
        }
    }

    public static class ContainerFilterQueryForm extends QueryForm
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

        @Override
        protected QuerySettings createQuerySettings(UserSchema schema)
        {
            var result = super.createQuerySettings(schema);
            if (getContainerFilter() != null)
            {
                // If the user specified an incorrect filter, throw an IllegalArgumentException
                try
                {
                    ContainerFilter.Type containerFilterType = ContainerFilter.Type.valueOf(getContainerFilter());
                    result.setContainerFilterName(containerFilterType.name());
                }
                catch (IllegalArgumentException e)
                {
                    // Remove bogus value from error message, Issue 45567
                    throw new IllegalArgumentException("'containerFilter' parameter is not valid");
                }
            }
            return result;
        }
    }

    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectData.class)
    public class SelectDistinctAction extends ReadOnlyApiAction<ContainerFilterQueryForm>
    {
        @Override
        public ApiResponse execute(ContainerFilterQueryForm form, BindException errors) throws Exception
        {
            TableInfo table = form.getQueryView().getTable();
            if (null == table)
                throw new NotFoundException();
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
        private SqlSelector getDistinctSql(TableInfo table, ContainerFilterQueryForm form, BindException errors)
        {
            QuerySettings settings = form.getQuerySettings();
            QueryService service = QueryService.get();

            if (null == getViewContext().getRequest().getParameter(QueryParam.maxRows.toString()))
            {
                settings.setMaxRows(DEFAULT_API_MAX_ROWS);
            }
            else
            {
                try
                {
                    int maxRows = Integer.parseInt(getViewContext().getRequest().getParameter(QueryParam.maxRows.toString()));
                    settings.setMaxRows(maxRows);
                }
                catch (NumberFormatException e)
                {
                    // Standard exception message, Issue 45567
                    QuerySettings.throwParameterParseException(QueryParam.maxRows);
                }
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
            service.ensureRequiredColumns(table, columns.values(), filter, null, new HashSet<>());
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
    public class GetColumnSummaryStatsAction extends ReadOnlyApiAction<QueryForm>
    {
        private FieldKey _colFieldKey;

        @Override
        public void validateForm(QueryForm form, Errors errors)
        {
            QuerySettings settings = form.getQuerySettings();
            List<FieldKey> fieldKeys = settings != null ? settings.getFieldKeys() : null;
            if (null == fieldKeys || fieldKeys.size() != 1)
                errors.reject(ERROR_MSG, "GetColumnSummaryStats requires that only one column be requested.");
            else
                _colFieldKey = fieldKeys.get(0);
        }

        @Override
        public ApiResponse execute(QueryForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            QueryView view = form.getQueryView();
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
                Map<String, List<Aggregate.Result>> aggResults = selector.getAggregates(new ArrayList<>(colAggregates));

                // create a response object mapping the analytics providers to their relevant aggregate results
                Map<String, Map<String, Object>> aggregateResults = new HashMap<>();
                if (aggResults.containsKey(_colFieldKey.toString()))
                {
                    for (Aggregate.Result r : aggResults.get(_colFieldKey.toString()))
                    {
                        Map<String, Object> props = new HashMap<>();
                        Aggregate.Type type = r.getAggregate().getType();
                        props.put("label", type.getFullLabel());
                        props.put("description", type.getDescription());
                        props.put("value", r.getFormattedValue(displayColumn, getContainer()).first);
                        aggregateResults.put(type.getName(), props);
                    }

                    response.put("success", true);
                    response.put("analyticsProviders", analyticsProviders);
                    response.put("aggregateResults", aggregateResults);
                }
                else
                {
                    response.put("success", false);
                    response.put("message", "Unable to get aggregate results for " + _colFieldKey);
                }
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

        @Override
        protected void initRequest(QueryForm form) throws ServletException
        {
            _form = form;

            _insertOption = form.getInsertOption();
            QueryDefinition query = form.getQueryDef();
            List<QueryException> qpe = new ArrayList<>();
            TableInfo t = query.getTable(form.getSchema(), qpe, true);
            if (!qpe.isEmpty())
                throw qpe.get(0);
            if (null != t)
                setTarget(t);
            _auditBehaviorType = form.getAuditBehavior();
        }

        @Override
        public ModelAndView getView(QueryForm form, BindException errors) throws Exception
        {
            initRequest(form);
            return super.getDefaultImportView(form, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            new SchemaAction(_form).addNavTrail(root);
            var executeQuery = _form.urlFor(QueryAction.executeQuery);
            if (null == executeQuery)
                root.addChild(_form.getQueryName());
            else
                root.addChild(_form.getQueryName(), executeQuery);
            root.addChild("Import Data");
        }
    }


    @SuppressWarnings({"unused", "WeakerAccess"})
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

    @RequiresPermission(ReadPermission.class)
    @ApiVersion(9.2)
    @Action(ActionType.Export.class)
    public static class ExportSqlAction extends ExportAction<ExportSqlForm>
    {
        @Override
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
            ResponseHelper.setPrivate(response);
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
        insert(InsertPermission.class, QueryService.AuditAction.INSERT)
        {
            @Override
            public List<Map<String, Object>> saveRows(QueryUpdateService qus, List<Map<String, Object>> rows, User user, Container container, Map<Enum, Object> configParameters, Map<String, Object> extraContext)
                    throws SQLException, InvalidKeyException, QueryUpdateServiceException, BatchValidationException, DuplicateKeyException
            {
                BatchValidationException errors = new BatchValidationException();
                List<Map<String, Object>> insertedRows = qus.insertRows(user, container, rows, errors, configParameters, extraContext);
                if (errors.hasErrors())
                    throw errors;
                // Issue 42519: Submitter role not able to insert
                // as per the definition of submitter, should allow insert without read
                if (qus.hasPermission(user, ReadPermission.class))
                {
                    return qus.getRows(user, container, insertedRows);
                }
                else
                {
                    return insertedRows;
                }
            }
        },
        insertWithKeys(InsertPermission.class, QueryService.AuditAction.INSERT)
        {
            @Override
            public List<Map<String, Object>> saveRows(QueryUpdateService qus, List<Map<String, Object>> rows, User user, Container container, Map<Enum, Object> configParameters, Map<String, Object> extraContext)
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
                List<Map<String, Object>> updatedRows = qus.insertRows(user, container, newRows, errors, configParameters, extraContext);
                if (errors.hasErrors())
                    throw errors;
                // Issue 42519: Submitter role not able to insert
                // as per the definition of submitter, should allow insert without read
                if (qus.hasPermission(user, ReadPermission.class))
                {
                    updatedRows = qus.getRows(user, container, updatedRows);
                }
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
        importRows(InsertPermission.class, QueryService.AuditAction.INSERT)
        {
            @Override
            public List<Map<String, Object>> saveRows(QueryUpdateService qus, List<Map<String, Object>> rows, User user, Container container, Map<Enum, Object> configParameters, Map<String, Object> extraContext)
                    throws SQLException, BatchValidationException
            {
                BatchValidationException errors = new BatchValidationException();
                DataIteratorBuilder it = new ListofMapsDataIterator.Builder(rows.get(0).keySet(), rows);
                qus.importRows(user, container, it, errors, configParameters, extraContext);
                if (errors.hasErrors())
                    throw errors;
                return Collections.emptyList();
            }
        },
        update(UpdatePermission.class, QueryService.AuditAction.UPDATE)
        {
            @Override
            public List<Map<String, Object>> saveRows(QueryUpdateService qus, List<Map<String, Object>> rows, User user, Container container, Map<Enum, Object> configParameters, Map<String, Object> extraContext)
                    throws SQLException, InvalidKeyException, QueryUpdateServiceException, BatchValidationException
            {
                List<Map<String, Object>> updatedRows = qus.updateRows(user, container, rows, rows, configParameters, extraContext);
                return qus.getRows(user, container, updatedRows);
            }
        },
        updateChangingKeys(UpdatePermission.class, QueryService.AuditAction.UPDATE)
        {
            @Override
            public List<Map<String, Object>> saveRows(QueryUpdateService qus, List<Map<String, Object>> rows, User user, Container container, Map<Enum, Object> configParameters, Map<String, Object> extraContext)
                    throws SQLException, InvalidKeyException, QueryUpdateServiceException, BatchValidationException
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
                List<Map<String, Object>> updatedRows = qus.updateRows(user, container, newRows, oldKeys, configParameters, extraContext);
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
        delete(DeletePermission.class, QueryService.AuditAction.DELETE)
        {
            @Override
            public List<Map<String, Object>> saveRows(QueryUpdateService qus, List<Map<String, Object>> rows, User user, Container container, Map<Enum, Object> configParameters, Map<String, Object> extraContext)
                    throws SQLException, InvalidKeyException, QueryUpdateServiceException, BatchValidationException
            {
                return qus.deleteRows(user, container, rows, configParameters, extraContext);
            }
        };

        private final Class<? extends Permission> _permission;
        private final QueryService.AuditAction _auditAction;

        CommandType(Class<? extends Permission> permission, QueryService.AuditAction auditAction)
        {
            _permission = permission;
            _auditAction = auditAction;
        }

        public Class<? extends Permission> getPermission()
        {
            return _permission;
        }

        public QueryService.AuditAction getAuditAction()
        {
            return _auditAction;
        }

        public abstract List<Map<String, Object>> saveRows(QueryUpdateService qus, List<Map<String, Object>> rows, User user, Container container, Map<Enum, Object> configParameters, Map<String, Object> extraContext)
                throws SQLException, InvalidKeyException, QueryUpdateServiceException, BatchValidationException, DuplicateKeyException;
    }

    /**
     * Base action class for insert/update/delete actions
     */
    public abstract static class BaseSaveRowsAction extends MutatingApiAction<ApiSaveRowsForm>
    {
        public static final String PROP_SCHEMA_NAME = "schemaName";
        public static final String PROP_QUERY_NAME = "queryName";
        public static final String PROP_COMMAND = "command";
        private static final String PROP_ROWS = "rows";

        private JSONObject _json;

        @Override
        public void validateForm(ApiSaveRowsForm apiSaveRowsForm, Errors errors)
        {
            _json = apiSaveRowsForm.getJsonObject();

            // if the POST was done using FormData, the apiSaveRowsForm would not have bound the json data, so
            // we'll instead look for that data in the request param directly
            if (_json == null && getViewContext().getRequest() != null && getViewContext().getRequest().getParameter("json") != null)
                _json = new JSONObject(getViewContext().getRequest().getParameter("json"));
        }

        protected JSONObject getJsonObject()
        {
            return _json;
        }

        protected JSONObject executeJson(JSONObject json, CommandType commandType, boolean allowTransaction, Errors errors) throws Exception
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
                    throw new IllegalArgumentException("No '" + PROP_ROWS + "' array supplied!");
            }
            catch (JSONException x)
            {
                throw new IllegalArgumentException("No '" + PROP_ROWS + "' array supplied!");
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
            // Do we need to support some sort of UNDEFINED and NULL instance of MvFieldWrapper?
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
                    if (allowRowAttachments())
                        addRowAttachments(rowMap, idx);

                    rowsToProcess.add(rowMap);
                    rowsAffected++;
                }
            }

            Map<String, Object> extraContext = json.optJSONObject("extraContext");
            if (extraContext == null)
                extraContext = new CaseInsensitiveHashMap<>();

            Map<Enum, Object> configParameters = new HashMap<>();

            // Check first if the audit behavior has been defined for the table either in code or through XML.
            // If not defined there, check for the audit behavior defined in the action form (json).
            AuditBehaviorType behaviorType = table.getAuditBehavior(json.getString("auditBehavior"));
            if (behaviorType != null)
            {
                configParameters.put(DetailedAuditLogDataIterator.AuditConfigs.AuditBehavior, behaviorType);
                String auditComment = json.getString("auditUserComment");
                if (!StringUtils.isEmpty(auditComment))
                    configParameters.put(DetailedAuditLogDataIterator.AuditConfigs.AuditUserComment, auditComment);
            }

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
            TransactionAuditProvider.TransactionAuditEvent auditEvent = null;
            try (DbScope.Transaction transaction = transacted ? table.getSchema().getScope().ensureTransaction() : NO_OP_TRANSACTION)
            {
                if (behaviorType != null && behaviorType != AuditBehaviorType.NONE)
                {
                    auditEvent = AbstractQueryUpdateService.createTransactionAuditEvent(getContainer(), commandType.getAuditAction());
                    AbstractQueryUpdateService.addTransactionAuditEvent(transaction,  getUser(), auditEvent);
                }

                List<Map<String, Object>> responseRows =
                        commandType.saveRows(qus, rowsToProcess, getUser(), getContainer(), configParameters, extraContext);
                if (auditEvent != null)
                    auditEvent.setRowCount(responseRows.size());

                if (commandType != CommandType.importRows)
                    response.put("rows", responseRows);

                // if there is any provenance information, save it here
                ProvenanceService svc = ProvenanceService.get();
                if (json.has("provenance"))
                {
                    JSONObject provenanceJSON = json.getJSONObject("provenance");
                    ProvenanceRecordingParams params = svc.createRecordingParams(getViewContext(), provenanceJSON, ProvenanceService.ADD_RECORDING);
                    RecordedAction action = svc.createRecordedAction(getViewContext(), params);
                    if (action != null && params.getRecordingId() != null)
                    {
                        // check for any row level provenance information
                        if (json.has("rows"))
                        {
                            Object rowObject = json.get("rows");
                            if (rowObject instanceof JSONArray)
                            {
                                JSONArray jsonRows = (JSONArray)rowObject;
                                // we need to match any provenance object inputs to the object outputs from the response rows, this typically would
                                // be the row lsid but it configurable in the provenance recording params
                                //
                                List<Pair<String, String>> provenanceMap = svc.createProvenanceMapFromRows(getViewContext(), params, jsonRows, responseRows);
                                if (!provenanceMap.isEmpty())
                                {
                                    action.getProvenanceMap().addAll(provenanceMap);
                                }
                                svc.addRecordingStep(getViewContext().getRequest(), params.getRecordingId(), action);
                            }
                            else
                            {
                                errors.reject(SpringActionController.ERROR_MSG, "Unable to process provenance information, the rows object was not an array");
                            }
                        }
                    }
                }
                transaction.commit();
            }
            catch (OptimisticConflictException e)
            {
                //issue 13967: provide better message for OptimisticConflictException
                errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
            }
            catch (QueryUpdateServiceException | ConversionException | DuplicateKeyException | DataIntegrityViolationException e)
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
            if (auditEvent != null)
                response.put("transactionAuditId", auditEvent.getRowId());

            response.put("rowsAffected", rowsAffected);

            return response;
        }

        protected boolean allowRowAttachments()
        {
            return false;
        }

        private void addRowAttachments(Map<String, Object> rowMap, int rowIndex)
        {
            if (getFileMap() != null)
            {
                for (Map.Entry<String, MultipartFile> fileEntry : getFileMap().entrySet())
                {
                    // allow for the fileMap key to include the row index for defining which row to attach this file to
                    // ex: "FileField::0", "FieldField::1"
                    String fieldKey = fileEntry.getKey();
                    int delimIndex = fieldKey.lastIndexOf(ROW_ATTACHMENT_INDEX_DELIM);
                    if (delimIndex > -1)
                    {
                        String fieldRowIndex = fieldKey.substring(delimIndex + 2);
                        if (!fieldRowIndex.equals(rowIndex+""))
                            continue;

                        fieldKey = fieldKey.substring(0, delimIndex);
                    }

                    SpringAttachmentFile file = new SpringAttachmentFile(fileEntry.getValue());
                    rowMap.put(fieldKey, file.isEmpty() ? null : file);
                }
            }
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

            TableInfo table = schema.getTableForInsert(queryName);
            if (table == null)
                throw new IllegalArgumentException("The query '" + queryName + "' in the schema '" + schemaName + "' does not exist.");
            return table;
        }
    }

    // Issue: 20522 - require read access to the action but executeJson will check for update privileges from the table
    //
    @RequiresPermission(ReadPermission.class) //will check below
    @ApiVersion(8.3)
    public static class UpdateRowsAction extends BaseSaveRowsAction
    {
        @Override
        public ApiResponse execute(ApiSaveRowsForm apiSaveRowsForm, BindException errors) throws Exception
        {
            JSONObject response = executeJson(getJsonObject(), CommandType.update, true, errors);
            if (response == null || errors.hasErrors())
                return null;
            return new ApiSimpleResponse(response);
        }

        @Override
        protected boolean allowRowAttachments()
        {
            return true;
        }
    }

    @RequiresAnyOf({ReadPermission.class, InsertPermission.class}) //will check below
    @ApiVersion(8.3)
    public static class InsertRowsAction extends BaseSaveRowsAction
    {
        @Override
        public ApiResponse execute(ApiSaveRowsForm apiSaveRowsForm, BindException errors) throws Exception
        {
            JSONObject response = executeJson(getJsonObject(), CommandType.insert, true, errors);
            if (response == null || errors.hasErrors())
                return null;

            return new ApiSimpleResponse(response);
        }

        @Override
        protected boolean allowRowAttachments()
        {
            return true;
        }
    }

    @RequiresPermission(ReadPermission.class) //will check below
    @ApiVersion(8.3)
    public static class ImportRowsAction extends BaseSaveRowsAction
    {
        @Override
        public ApiResponse execute(ApiSaveRowsForm apiSaveRowsForm, BindException errors) throws Exception
        {
            JSONObject response = executeJson(getJsonObject(), CommandType.importRows, true, errors);
            if (response == null || errors.hasErrors())
                return null;
            return new ApiSimpleResponse(response);
        }
    }

    @ActionNames("deleteRows, delRows")
    @RequiresPermission(ReadPermission.class) //will check below
    @ApiVersion(8.3)
    public static class DeleteRowsAction extends BaseSaveRowsAction
    {
        @Override
        public ApiResponse execute(ApiSaveRowsForm apiSaveRowsForm, BindException errors) throws Exception
        {
            JSONObject response = executeJson(getJsonObject(), CommandType.delete, true, errors);
            if (response == null || errors.hasErrors())
                return null;
            return new ApiSimpleResponse(response);
        }
    }

    @RequiresNoPermission //will check below
    public static class SaveRowsAction extends BaseSaveRowsAction
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

            JSONObject json = getJsonObject();
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
                assert scope != null;
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

    @RequiresPermission(ReadPermission.class)
    public static class ApiTestAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return new JspView<>("/org/labkey/query/view/apitest.jsp");
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("API Test");
        }
    }


    @RequiresPermission(AdminPermission.class)
    public static class AdminAction extends SimpleViewAction<QueryForm>
    {
        @SuppressWarnings("UnusedDeclaration")
        public AdminAction()
        {
        }

        public AdminAction(ViewContext ctx)
        {
            setViewContext(ctx);
        }

        @Override
        public ModelAndView getView(QueryForm form, BindException errors)
        {
            setHelpTopic("externalSchemas");
            return new JspView<>("/org/labkey/query/view/admin.jsp", form, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            new BeginAction(getViewContext()).addNavTrail(root);
            root.addChild("Schema Administration", new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer()));
        }
    }


    @SuppressWarnings({"unused", "WeakerAccess"})
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


    @RequiresPermission(AdminPermission.class)
    public static class ManageRemoteConnectionsAction extends FormViewAction<ResetRemoteConnectionsForm>
    {
        @Override
        public void validateCommand(ResetRemoteConnectionsForm target, Errors errors) {}

        @Override
        public boolean handlePost(ResetRemoteConnectionsForm form, BindException errors)
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
        public ModelAndView getView(ResetRemoteConnectionsForm queryForm, boolean reshow, BindException errors)
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
            setHelpTopic("remoteConnection");
            return new JspView<>("/org/labkey/query/view/manageRemoteConnections.jsp", connectionMap, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            new BeginAction(getViewContext()).addNavTrail(root);
            root.addChild("Manage Remote Connections", new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer()));
        }
    }

    private abstract static class BaseInsertExternalSchemaAction<F extends AbstractExternalSchemaForm<T>, T extends AbstractExternalSchemaDef> extends FormViewAction<F>
    {
        protected BaseInsertExternalSchemaAction(Class<F> commandClass)
        {
            super(commandClass);
        }

        @Override
        public void validateCommand(F form, Errors errors)
        {
            form.validate(errors);
        }

        @Override
        public boolean handlePost(F form, BindException errors) throws Exception
        {
            try (DbScope.Transaction t = QueryManager.get().getDbSchema().getScope().ensureTransaction())
            {
                form.doInsert();
                auditSchemaAdminActivity(form.getBean(), "created", getContainer(), getUser());
                QueryManager.get().updateExternalSchemas(getContainer());

                t.commit();
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

        @Override
        public ActionURL getSuccessURL(F form)
        {
            return new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            new AdminAction(getViewContext()).addNavTrail(root);
            root.addChild("Define Schema", new ActionURL(getClass(), getContainer()));
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public static class InsertLinkedSchemaAction extends BaseInsertExternalSchemaAction<LinkedSchemaForm, LinkedSchemaDef>
    {
        public InsertLinkedSchemaAction()
        {
            super(LinkedSchemaForm.class);
        }

        @Override
        public ModelAndView getView(LinkedSchemaForm form, boolean reshow, BindException errors)
        {
            setHelpTopic("filterSchema");
            return new JspView<>("/org/labkey/query/view/linkedSchema.jsp", new LinkedSchemaBean(getContainer(), form.getBean(), true), errors);
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public static class InsertExternalSchemaAction extends BaseInsertExternalSchemaAction<ExternalSchemaForm, ExternalSchemaDef>
    {
        public InsertExternalSchemaAction()
        {
            super(ExternalSchemaForm.class);
        }

        @Override
        public ModelAndView getView(ExternalSchemaForm form, boolean reshow, BindException errors)
        {
            setHelpTopic("externalSchemas");
            return new JspView<>("/org/labkey/query/view/externalSchema.jsp", new ExternalSchemaBean(getContainer(), form.getBean(), true), errors);
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public static class DeleteSchemaAction extends ConfirmAction<SchemaForm>
    {
        @Override
        public String getConfirmText()
        {
            return "Delete";
        }

        @Override
        public ModelAndView getConfirmView(SchemaForm form, BindException errors)
        {
            if (getPageConfig().getTitle() == null)
                setTitle("Delete Schema");

            AbstractExternalSchemaDef def = ExternalSchemaDefCache.getSchemaDef(getContainer(), form.getExternalSchemaId(), AbstractExternalSchemaDef.class);
            if (def == null)
                throw new NotFoundException();

            String schemaName = isBlank(def.getUserSchemaName()) ? "this schema" : "the schema '" + def.getUserSchemaName() + "'";
            return new HtmlView(HtmlString.of("Are you sure you want to delete " + schemaName + "? The tables and queries defined in this schema will no longer be accessible."));
        }

        @Override
        public boolean handlePost(SchemaForm form, BindException errors)
        {
            AbstractExternalSchemaDef def = ExternalSchemaDefCache.getSchemaDef(getContainer(), form.getExternalSchemaId(), AbstractExternalSchemaDef.class);
            if (def == null)
                throw new NotFoundException();

            try (DbScope.Transaction t = QueryManager.get().getDbSchema().getScope().ensureTransaction())
            {
                auditSchemaAdminActivity(def, "deleted", getContainer(), getUser());
                QueryManager.get().delete(def);
                t.commit();
            }
            return true;
        }

        @Override
        public void validateCommand(SchemaForm form, Errors errors)
        {
        }

        @Override
        @NotNull
        public ActionURL getSuccessURL(SchemaForm form)
        {
            return new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer());
        }
    }

    private static void auditSchemaAdminActivity(AbstractExternalSchemaDef def, String action, Container container, User user)
    {
        String comment = StringUtils.capitalize(def.getSchemaType().toString()) + " schema '" + def.getUserSchemaName() + "' " + action;
        AuditTypeEvent event = new AuditTypeEvent(ContainerAuditProvider.CONTAINER_AUDIT_EVENT, container, comment);
        AuditLogService.get().addEvent(user, event);
    }


    private abstract static class BaseEditSchemaAction<F extends AbstractExternalSchemaForm<T>, T extends AbstractExternalSchemaDef> extends FormViewAction<F>
    {
        protected BaseEditSchemaAction(Class<F> commandClass)
        {
            super(commandClass);
        }

        @Override
        public void validateCommand(F form, Errors errors)
        {
            form.validate(errors);
        }

        @Nullable
        protected abstract T getCurrent(int externalSchemaId);

        @NotNull
        protected T getDef(F form, boolean reshow)
        {
            T def;
            Container defContainer;

            if (reshow)
            {
                def = form.getBean();
                T current = getCurrent(def.getExternalSchemaId());
                if (current == null)
                    throw new NotFoundException();

                defContainer = current.lookupContainer();
            }
            else
            {
                form.refreshFromDb();
                if (!form.isDataLoaded())
                    throw new NotFoundException();

                def = form.getBean();
                if (def == null)
                    throw new NotFoundException();

                defContainer = def.lookupContainer();
            }

            if (!getContainer().equals(defContainer))
                throw new UnauthorizedException();

            return def;
        }

        @Override
        public boolean handlePost(F form, BindException errors) throws Exception
        {
            T def = form.getBean();
            T fromDb = getCurrent(def.getExternalSchemaId());

            // Unauthorized if def in the database reports a different container
            if (!getContainer().equals(fromDb.lookupContainer()))
                throw new UnauthorizedException();

            try (DbScope.Transaction t = QueryManager.get().getDbSchema().getScope().ensureTransaction())
            {
                form.doUpdate();
                auditSchemaAdminActivity(def, "updated", getContainer(), getUser());
                QueryManager.get().updateExternalSchemas(getContainer());
                t.commit();
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

        @Override
        public ActionURL getSuccessURL(F externalSchemaForm)
        {
            return new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            new AdminAction(getViewContext()).addNavTrail(root);
            root.addChild("Edit Schema", new ActionURL(getClass(), getContainer()));
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public static class EditLinkedSchemaAction extends BaseEditSchemaAction<LinkedSchemaForm, LinkedSchemaDef>
    {
        public EditLinkedSchemaAction()
        {
            super(LinkedSchemaForm.class);
        }

        @Nullable
        @Override
        protected LinkedSchemaDef getCurrent(int externalId)
        {
            return QueryManager.get().getLinkedSchemaDef(getContainer(), externalId);
        }

        @Override
        public ModelAndView getView(LinkedSchemaForm form, boolean reshow, BindException errors)
        {
            LinkedSchemaDef def = getDef(form, reshow);

            setHelpTopic("filterSchema");
            return new JspView<>("/org/labkey/query/view/linkedSchema.jsp", new LinkedSchemaBean(getContainer(), def, false), errors);
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public static class EditExternalSchemaAction extends BaseEditSchemaAction<ExternalSchemaForm, ExternalSchemaDef>
    {
        public EditExternalSchemaAction()
        {
            super(ExternalSchemaForm.class);
        }

        @Nullable
        @Override
        protected ExternalSchemaDef getCurrent(int externalId)
        {
            return QueryManager.get().getExternalSchemaDef(getContainer(), externalId);
        }

        @Override
        public ModelAndView getView(ExternalSchemaForm form, boolean reshow, BindException errors)
        {
            ExternalSchemaDef def = getDef(form, reshow);

            setHelpTopic("externalSchemas");
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
            return sourceName != null ? sourceName.equals(that.sourceName) : that.sourceName == null;
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
            return new QueryUrlsImpl().urlDeleteSchema(_c, _def);
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

        @Override
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

                    if (null != moduleLoader.getModule(scope, schemaName))
                        continue;

                    schemaNames.add(schemaName);
                }

                DataSourceInfo source = new DataSourceInfo(scope);
                _sourcesAndSchemas.put(source, schemaNames);
                _sourcesAndSchemasIncludingSystem.put(source, schemaNamesIncludingSystem);
            }
        }
    }


    @SuppressWarnings({"unused", "WeakerAccess"})
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
    public static class GetTablesAction extends ReadOnlyApiAction<GetTablesForm>
    {
        @Override
        public ApiResponse execute(GetTablesForm form, BindException errors)
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


    @SuppressWarnings({"unused", "WeakerAccess"})
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
    public static class SchemaTemplateAction extends ReadOnlyApiAction<SchemaTemplateForm>
    {
        @Override
        public ApiResponse execute(SchemaTemplateForm form, BindException errors)
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
    public static class SchemaTemplatesAction extends ReadOnlyApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object form, BindException errors)
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

    @RequiresPermission(AdminPermission.class)
    public static class ReloadExternalSchemaAction extends FormHandlerAction<SchemaForm>
    {
        private String _userSchemaName;

        @Override
        public void validateCommand(SchemaForm form, Errors errors)
        {
        }

        @Override
        public boolean handlePost(SchemaForm form, BindException errors)
        {
            ExternalSchemaDef def = ExternalSchemaDefCache.getSchemaDef(getContainer(), form.getExternalSchemaId(), ExternalSchemaDef.class);
            if (def == null)
                throw new NotFoundException();

            QueryManager.get().reloadExternalSchema(def);
            _userSchemaName = def.getUserSchemaName();

            return true;
        }

        @Override
        public ActionURL getSuccessURL(SchemaForm form)
        {
            return new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer(), _userSchemaName);
        }
    }


    @RequiresPermission(AdminPermission.class)
    public static class ReloadAllUserSchemas extends FormHandlerAction<Object>
    {
        @Override
        public void validateCommand(Object target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(Object o, BindException errors)
        {
            QueryManager.get().reloadAllExternalSchemas(getContainer());
            return true;
        }

        @Override
        public URLHelper getSuccessURL(Object o)
        {
            return new QueryUrlsImpl().urlExternalSchemaAdmin(getContainer(), "ALL");
        }
    }


    @RequiresPermission(ReadPermission.class)
    public static class TableInfoAction extends SimpleViewAction<TableInfoForm>
    {
        @Override
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

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }


    // Issue 18870: Guest user can't revert unsaved custom view changes
    // Permission will be checked inline (guests are allowed to delete their session custom views)
    @RequiresNoPermission
    @Action(ActionType.Configure.class)
    public static class DeleteViewAction extends MutatingApiAction<DeleteViewForm>
    {
        @Override
        public ApiResponse execute(DeleteViewForm form, BindException errors)
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

    @SuppressWarnings({"unused", "WeakerAccess"})
    public static class SaveSessionViewForm extends QueryForm
    {
        private String newName;
        private boolean inherit;
        private boolean shared;
        private boolean hidden;
        private boolean replace;
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

        public boolean isHidden()
        {
            return hidden;
        }

        public void setHidden(boolean hidden)
        {
            this.hidden = hidden;
        }

        public boolean isReplace()
        {
            return replace;
        }

        public void setReplace(boolean replace)
        {
            this.replace = replace;
        }
    }

    // Moves a session view into the database.
    @RequiresPermission(ReadPermission.class)
    public static class SaveSessionViewAction extends MutatingApiAction<SaveSessionViewForm>
    {
        @Override
        public ApiResponse execute(SaveSessionViewForm form, BindException errors)
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

                // save a new private view if shared is false but existing view is shared
                if (existingView != null && !form.isShared() && existingView.getOwner() == null)
                {
                    existingView = null;
                }

                if (existingView != null && !form.isReplace() && !StringUtils.isEmpty(form.getNewName()))
                    throw new IllegalArgumentException("A saved view by the name \"" + form.getNewName() + "\" already exists. ");

                if (existingView == null || (existingView instanceof ModuleCustomView && existingView.isEditable()))
                {
                    User owner = form.isShared() ? null : getUser();

                    CustomViewImpl viewCopy = new CustomViewImpl(form.getQueryDef(), owner, form.getNewName());
                    viewCopy.setColumns(view.getColumns());
                    viewCopy.setCanInherit(form.isInherit());
                    viewCopy.setFilterAndSort(view.getFilterAndSort());
                    viewCopy.setColumnProperties(view.getColumnProperties());
                    viewCopy.setIsHidden(form.isHidden());
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
                    existingView.setIsHidden(form.isHidden());

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

    /** Minimalist, secret UI to help users recover if they've created a broken view somehow */
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

        @Override
        public ModelAndView getView(QueryForm form, BindException errors)
        {
            return new JspView<>("/org/labkey/query/view/manageViews.jsp", form, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            new BeginAction(getViewContext()).addNavTrail(root);
            root.addChild("Manage Views", QueryController.this.getViewContext().getActionURL());
        }
    }


    /** Minimalist, secret UI to help users recover if they've created a broken view somehow */
    @RequiresPermission(AdminPermission.class)
    public class InternalDeleteView extends ConfirmAction<InternalViewForm>
    {
        @Override
        public ModelAndView getConfirmView(InternalViewForm form, BindException errors)
        {
            return new JspView<>("/org/labkey/query/view/internalDeleteView.jsp", form, errors);
        }

        @Override
        public boolean handlePost(InternalViewForm form, BindException errors)
        {
            CstmView view = form.getViewAndCheckPermission();
            QueryManager.get().delete(view);
            return true;
        }

        @Override
        public void validateCommand(InternalViewForm internalViewForm, Errors errors)
        {
        }

        @Override
        @NotNull
        public ActionURL getSuccessURL(InternalViewForm internalViewForm)
        {
            return new ActionURL(ManageViewsAction.class, getContainer());
        }
    }

    /** Minimalist, secret UI to help users recover if they've created a broken view somehow */
    @RequiresPermission(AdminPermission.class)
    public class InternalSourceViewAction extends FormViewAction<InternalSourceViewForm>
    {
        @Override
        public void validateCommand(InternalSourceViewForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(InternalSourceViewForm form, boolean reshow, BindException errors)
        {
            CstmView view = form.getViewAndCheckPermission();
            form.ff_inherit = QueryManager.get().canInherit(view.getFlags());
            form.ff_hidden = QueryManager.get().isHidden(view.getFlags());
            form.ff_columnList = view.getColumns();
            form.ff_filter = view.getFilter();
            return new JspView<>("/org/labkey/query/view/internalSourceView.jsp", form, errors);
        }

        @Override
        public boolean handlePost(InternalSourceViewForm form, BindException errors)
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

        @Override
        public ActionURL getSuccessURL(InternalSourceViewForm form)
        {
            return new ActionURL(ManageViewsAction.class, getContainer());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            new ManageViewsAction(getViewContext()).addNavTrail(root);
            root.addChild("Edit source of Grid View");
        }
    }

    /** Minimalist, secret UI to help users recover if they've created a broken view somehow */
    @RequiresPermission(AdminPermission.class)
    public class InternalNewViewAction extends FormViewAction<InternalNewViewForm>
    {
        int _customViewId = 0;

        @Override
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

        @Override
        public ModelAndView getView(InternalNewViewForm form, boolean reshow, BindException errors)
        {
            return new JspView<>("/org/labkey/query/view/internalNewView.jsp", form, errors);
        }

        @Override
        public boolean handlePost(InternalNewViewForm form, BindException errors)
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
                    LogManager.getLogger(QueryController.class).error("Error", e);
                    errors.reject(ERROR_MSG, "An exception occurred: " + e);
                    return false;
                }
                _customViewId = view.getCustomViewId();
            }
            return true;
        }

        @Override
        public ActionURL getSuccessURL(InternalNewViewForm form)
        {
            ActionURL forward = new ActionURL(InternalSourceViewAction.class, getContainer());
            forward.addParameter("customViewId", Integer.toString(_customViewId));
            return forward;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Create New Grid View");
         }
    }


    @ActionNames("clearSelected, selectNone")
    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectData.class)
    public static class SelectNoneAction extends MutatingApiAction<SelectForm>
    {
        @Override
        public void validateForm(SelectForm form, Errors errors)
        {
            if (form.getSchemaName().isEmpty() != (form.getQueryName() == null))
            {
                errors.reject(ERROR_MSG, "Both schemaName and queryName are required");
            }
        }

        @Override
        public ApiResponse execute(final SelectForm form, BindException errors) throws Exception
        {
            if (form.getQueryName() == null)
            {
                DataRegionSelection.clearAll(getViewContext(), form.getKey());
                return new DataRegionSelection.SelectionResponse(0);
            }
            else
            {
                int count = DataRegionSelection.setSelectionForAll(form, false);
                return new DataRegionSelection.SelectionResponse(count);
            }
        }
    }


    @SuppressWarnings({"unused", "WeakerAccess"})
    public static class SelectForm extends QueryForm
    {
        protected boolean clearSelected;
        protected String key;

        public boolean isClearSelected()
        {
            return clearSelected;
        }

        public void setClearSelected(boolean clearSelected)
        {
            this.clearSelected = clearSelected;
        }

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
        @Override
        public void validateForm(QueryForm form, Errors errors)
        {
            if (form.getSchemaName().isEmpty() || form.getQueryName() == null)
            {
                errors.reject(ERROR_MSG, "schemaName and queryName required");
            }
        }

        @Override
        public ApiResponse execute(final QueryForm form, BindException errors) throws Exception
        {
            int count = DataRegionSelection.setSelectionForAll(form, true);
            return new DataRegionSelection.SelectionResponse(count);
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class GetSelectedAction extends ReadOnlyApiAction<SelectForm>
    {
        @Override
        public void validateForm(SelectForm form, Errors errors)
        {
            if (form.getSchemaName().isEmpty() != (form.getQueryName() == null))
            {
                errors.reject(ERROR_MSG, "Both schemaName and queryName are required");
            }
        }

        @Override
        public ApiResponse execute(final SelectForm form, BindException errors) throws Exception
        {
            if (form.getQueryName() == null)
            {
                Set<String> selected = DataRegionSelection.getSelected(getViewContext(), form.getKey(), form.isClearSelected());
                return new ApiSimpleResponse("selected", selected);
            }
            else
            {
                List<String> selected = DataRegionSelection.getSelected(form, form.isClearSelected());
                return new ApiSimpleResponse("selected", selected);
            }
        }
    }

    @ActionNames("setSelected, setCheck")
    @RequiresPermission(ReadPermission.class)
    public static class SetCheckAction extends MutatingApiAction<SetCheckForm>
    {
        @Override
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

            int count;
            if (form.getQueryName() != null && form.isValidateIds() && form.isChecked())
            {
                selection = DataRegionSelection.getValidatedIds(selection, form);
            }

            count = DataRegionSelection.setSelected(
                    getViewContext(), form.getKey(),
                    selection, form.isChecked());

            return new DataRegionSelection.SelectionResponse(count);
        }
    }


    @SuppressWarnings({"unused", "WeakerAccess"})
    public static class SetCheckForm extends SelectForm
    {
        protected String[] ids;
        protected boolean checked;
        protected boolean validateIds;

        public String[] getId(HttpServletRequest request)
        {
            // 5025 : DataRegion checkbox names may contain comma
            // Beehive parses a single parameter value with commas into an array
            // which is not what we want.
            String[] paramIds = request.getParameterValues("id");
            return  paramIds == null ? ids: paramIds;
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

        public boolean isValidateIds()
        {
            return validateIds;
        }

        public void setValidateIds(boolean validateIds)
        {
            this.validateIds = validateIds;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class ReplaceSelectedAction extends MutatingApiAction<SetCheckForm>
    {
        @Override
        public ApiResponse execute(final SetCheckForm form, BindException errors)
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


            DataRegionSelection.clearAll(getViewContext(), form.getKey());
            int count = DataRegionSelection.setSelected(
                    getViewContext(), form.getKey(),
                    selection, true);
            return new DataRegionSelection.SelectionResponse(count);
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class SetSnapshotSelectionAction extends MutatingApiAction<SetCheckForm>
    {
        @Override
        public ApiResponse execute(final SetCheckForm form, BindException errors)
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

            DataRegionSelection.clearAll(getViewContext(), form.getKey(), true);
            int count = DataRegionSelection.setSelected(
                getViewContext(), form.getKey(),
                selection, true, true);
            return new DataRegionSelection.SelectionResponse(count);
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class GetSnapshotSelectionAction extends ReadOnlyApiAction<SelectForm>
    {
        @Override
        public void validateForm(SelectForm form, Errors errors)
        {
            if (StringUtils.isEmpty(form.getKey()))
            {
                errors.reject(ERROR_MSG, "Selection key is required");
            }
        }

        @Override
        public ApiResponse execute(final SelectForm form, BindException errors) throws Exception
        {
            Set<String> selected = DataRegionSelection.getSnapshotSelected(getViewContext(), form.getKey());
            return new ApiSimpleResponse("selected", selected);
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

        @SuppressWarnings("unused")
        public void setSchemaName(SchemaKey schemaName)
        {
            _schemaName = schemaName;
        }

        public boolean isIncludeHidden()
        {
            return _includeHidden;
        }

        @SuppressWarnings("unused")
        public void setIncludeHidden(boolean includeHidden)
        {
            _includeHidden = includeHidden;
        }
    }


    @RequiresPermission(ReadPermission.class)
    @ApiVersion(12.3)
    public static class GetSchemasAction extends ReadOnlyApiAction<GetSchemasForm>
    {
        @Override
        protected long getLastModified(GetSchemasForm form)
        {
            return QueryService.get().metadataLastModified();
        }

        @Override
        public ApiResponse execute(GetSchemasForm form, BindException errors)
        {
            final Container container = getContainer();
            final User user = getUser();

            final boolean includeHidden = form.isIncludeHidden();
            if (getRequestedApiVersion() >= 9.3)
            {
                SimpleSchemaTreeVisitor<Void, JSONObject> visitor = new SimpleSchemaTreeVisitor<>(includeHidden)
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

                // Ensure consistent exception as other query actions
                QueryForm.ensureSchemaNotNull(schema);

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

    @SuppressWarnings({"unused", "WeakerAccess"})
    public static class GetQueriesForm
    {
        private String _schemaName;
        private boolean _includeUserQueries = true;
        private boolean _includeSystemQueries = true;
        private boolean _includeColumns = true;
        private boolean _queryDetailColumns = false;

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

        public boolean isQueryDetailColumns()
        {
            return _queryDetailColumns;
        }

        public void setQueryDetailColumns(boolean queryDetailColumns)
        {
            _queryDetailColumns = queryDetailColumns;
        }
    }


    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectMetaData.class)
    public static class GetQueriesAction extends ReadOnlyApiAction<GetQueriesForm>
    {
        @Override
        protected long getLastModified(GetQueriesForm form)
        {
            return QueryService.get().metadataLastModified();
        }

        @Override
        public ApiResponse execute(GetQueriesForm form, BindException errors)
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
                for (QueryDefinition qdef : uschema.getQueryDefs().values())
                {
                    if (!qdef.isTemporary())
                    {
                        ActionURL viewDataUrl = uschema.urlFor(QueryAction.executeQuery, qdef);
                        qinfos.add(getQueryProps(qdef, viewDataUrl, true, uschema, form.isIncludeColumns(), form.isQueryDetailColumns()));
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
                        qinfos.add(getQueryProps(qdef, viewDataUrl, false, uschema, form.isIncludeColumns(), form.isQueryDetailColumns()));
                    }
                }
            }
            response.put("queries", qinfos);

            return response;
        }

        protected Map<String, Object> getQueryProps(QueryDefinition qdef, ActionURL viewDataUrl, boolean isUserDefined, UserSchema schema, boolean includeColumns, boolean useQueryDetailColumns)
        {
            Map<String, Object> qinfo = new HashMap<>();
            qinfo.put("hidden", qdef.isHidden());
            qinfo.put("snapshot", qdef.isSnapshot());
            qinfo.put("inherit", qdef.canInherit());
            qinfo.put("isUserDefined", isUserDefined);
            boolean canEdit = qdef.canEdit(getUser());
            qinfo.put("canEdit", canEdit);
            qinfo.put("canEditSharedViews", getContainer().hasPermission(getUser(), EditSharedViewPermission.class));
            // CONSIDER: do we want to separate the 'canEditMetadata' property and 'isMetadataOverridable' properties to differentiate between cabability and the permission check?
            qinfo.put("isMetadataOverrideable", qdef.isMetadataEditable() && qdef.canEditMetadata(getUser()));

            if (isUserDefined)
                qinfo.put("moduleName", qdef.getModuleName());
            boolean isInherited = qdef.canInherit() && !getContainer().equals(qdef.getDefinitionContainer());
            qinfo.put("isInherited", isInherited);
            if (isInherited)
                qinfo.put("containerPath", qdef.getDefinitionContainer().getPath());
            qinfo.put("isIncludedForLookups", qdef.isIncludedForLookups());

            if (null != qdef.getDescription())
                qinfo.put("description", qdef.getDescription());
            if (viewDataUrl != null)
                qinfo.put("viewDataUrl", viewDataUrl);

            String title = qdef.getName();
            String name = qdef.getName();
            try
            {
                //get the table info if the user requested column info
                TableInfo table = qdef.getTable(schema, null, true);

                if (null != table)
                {
                    if (includeColumns)
                    {
                        Collection<Map<String, Object>> columns;

                        if (useQueryDetailColumns)
                        {
                            columns = JsonWriter
                                    .getNativeColProps(table, Collections.emptyList(), null, false, false)
                                    .values();
                        }
                        else
                        {
                            columns = new ArrayList<>();
                            for (ColumnInfo col : table.getColumns())
                            {
                                Map<String, Object> cinfo = new HashMap<>();
                                cinfo.put("name", col.getName());
                                if (null != col.getLabel())
                                    cinfo.put("caption", col.getLabel());
                                if (null != col.getShortLabel())
                                    cinfo.put("shortCaption", col.getShortLabel());
                                if (null != col.getDescription())
                                    cinfo.put("description", col.getDescription());

                                columns.add(cinfo);
                            }
                        }

                        if (columns.size() > 0)
                            qinfo.put("columns", columns);
                    }
                    name = table.getPublicName();
                    title = table.getTitle();
                }
            }
            catch(Exception e)
            {
                //may happen due to query failing parse
            }

            qinfo.put("title", title);
            qinfo.put("name", name);
            return qinfo;
        }
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
    public static class GetQueryViewsForm
    {
        private String _schemaName;
        private String _queryName;
        private String _viewName;
        private boolean _metadata;
        private boolean _excludeSessionView;

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

        public boolean isExcludeSessionView()
        {
            return _excludeSessionView;
        }

        public void setExcludeSessionView(boolean excludeSessionView)
        {
            _excludeSessionView = excludeSessionView;
        }
    }


    @RequiresPermission(ReadPermission.class)
    @Action(ActionType.SelectMetaData.class)
    public static class GetQueryViewsAction extends ReadOnlyApiAction<GetQueryViewsForm>
    {
        @Override
        protected long getLastModified(GetQueryViewsForm form)
        {
            return QueryService.get().metadataLastModified();
        }

        @Override
        public ApiResponse execute(GetQueryViewsForm form, BindException errors)
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

            Map<String, CustomView> views = querydef.getCustomViews(getUser(), getViewContext().getRequest(), true, false, form.isExcludeSessionView());
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
    public static class GetServerDateAction extends ReadOnlyApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object o, BindException errors)
        {
            return new ApiSimpleResponse("date", new Date());
        }
    }


    @SuppressWarnings({"unused", "WeakerAccess"})
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
    public static class SaveApiTestAction extends MutatingApiAction<SaveApiTestForm>
    {
        @Override
        public ApiResponse execute(SaveApiTestForm form, BindException errors)
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


    private abstract static class ParseAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
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
                LogManager.getLogger(QueryController.class).debug(expr,x);
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
        public void addNavTrail(NavTree root)
        {
        }

        abstract QNode _parse(String e, List<QueryParseException> errors);
        abstract Tree _tree(String e) throws Exception;
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public static class ParseExpressionAction extends ParseAction
    {
        @Override
        QNode _parse(String s, List<QueryParseException> errors)
        {
            return new SqlParser().parseExpr(s, true, errors);
        }

        @Override
        Tree _tree(String e)
        {
            return null;
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public static class ParseQueryAction extends ParseAction
    {
        @Override
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
    public static class ValidateQueryMetadataAction extends ReadOnlyApiAction<QueryForm>
    {
        @Override
        public ApiResponse execute(QueryForm form, BindException errors)
        {
            UserSchema schema = form.getSchema();

            if (null == schema)
            {
                errors.reject(ERROR_MSG, "could not resolve schema: " + form.getSchemaName());
                return null;
            }

            List<QueryParseException> parseErrors = new ArrayList<>();
            List<QueryParseException> parseWarnings = new ArrayList<>();
            ApiSimpleResponse response = new ApiSimpleResponse();

            try
            {
                TableInfo table = schema.getTable(form.getQueryName(), null);

                if (null == table)
                {
                    errors.reject(ERROR_MSG, "could not resolve table: " + form.getQueryName());
                    return null;
                }

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
            }
            catch (QueryParseException e)
            {
                parseErrors.add(e);
            }

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

        @Override
        protected ApiResponseWriter createResponseWriter() throws IOException
        {
            ApiResponseWriter result = super.createResponseWriter();
            // Issue 44875 - don't send a 400 or 500 response code when there's a bogus query or metadata
            result.setErrorResponseStatus(HttpServletResponse.SC_OK);
            return result;
        }
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
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
    public static class QueryExportAuditRedirectAction extends SimpleRedirectAction<QueryExportAuditForm>
    {
        @Override
        public URLHelper getRedirectURL(QueryExportAuditForm form)
        {
            if (form.getRowId() == 0)
                throw new NotFoundException("Query export audit rowid required");

            UserSchema auditSchema = QueryService.get().getUserSchema(getUser(), getContainer(), AbstractAuditTypeProvider.QUERY_SCHEMA_NAME);
            TableInfo queryExportAuditTable = auditSchema.getTable(QueryExportAuditProvider.QUERY_AUDIT_EVENT, null);
            if (null == queryExportAuditTable)
                throw new NotFoundException();

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

            ActionURL url = new ActionURL(ExecuteQueryAction.class, getContainer());

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

            return url;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class AuditHistoryAction extends SimpleViewAction<QueryForm>
    {
        @Override
        public ModelAndView getView(QueryForm form, BindException errors)
        {
            return QueryUpdateAuditProvider.createHistoryQueryView(getViewContext(), form, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Audit History");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class AuditDetailsAction extends SimpleViewAction<QueryDetailsForm>
    {
        @Override
        public ModelAndView getView(QueryDetailsForm form, BindException errors)
        {
            return QueryUpdateAuditProvider.createDetailsQueryView(getViewContext(), form, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Audit History");
        }
    }

    @SuppressWarnings({"unused", "WeakerAccess"})
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
    @Action(ActionType.Export.class)
    public static class ExportTablesAction extends FormViewAction<ExportTablesForm>
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
        public ModelAndView getView(ExportTablesForm form, boolean reshow, BindException errors)
        {
            // When exporting the zip to the browser, the base action will attempt to reshow the view since we returned
            // null as the success URL; returning null here causes the base action to stop pestering the action.
            if (reshow && !errors.hasErrors())
                return null;

            return new JspView<>("/org/labkey/query/view/exportTables.jsp", form, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Export Tables");
        }

        @Override
        public ActionURL getSuccessURL(ExportTablesForm form)
        {
            return _successUrl;
        }
    }


    @SuppressWarnings({"unused", "WeakerAccess"})
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

        @Override
        public @NotNull BindException bindParameters(PropertyValues values)
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
    public static class SaveNamedSetAction extends MutatingApiAction<NamedSetForm>
    {
        @Override
        public Object execute(NamedSetForm namedSetForm, BindException errors)
        {
            QueryService.get().saveNamedSet(namedSetForm.getSetName(), namedSetForm.parseSetList());
            return new ApiSimpleResponse("success", true);
        }
    }


    @SuppressWarnings({"unused", "WeakerAccess"})
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

        public List<String> parseSetList()
        {
            return Arrays.asList(setList);
        }
    }


    @RequiresPermission(ReadPermission.class)
    public static class DeleteNamedSetAction extends MutatingApiAction<NamedSetForm>
    {

        @Override
        public Object execute(NamedSetForm namedSetForm, BindException errors)
        {
            QueryService.get().deleteNamedSet(namedSetForm.getSetName());
            return new ApiSimpleResponse("success", true);
        }
    }


    @SuppressWarnings({"unused", "WeakerAccess"})
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

        public void setSourceSchema(String sourceSchema)
        {
            this.sourceSchema = sourceSchema;
        }

        public String getTargetSchema()
        {
            return targetSchema;
        }

        public void setTargetSchema(String targetSchema)
        {
            this.targetSchema = targetSchema;
        }

        public String getPathInScript()
        {
            return pathInScript;
        }

        public void setPathInScript(String pathInScript)
        {
            this.pathInScript = pathInScript;
        }

        public String getSourceDataSource()
        {
            return sourceDataSource;
        }

        public void setSourceDataSource(String sourceDataSource)
        {
            this.sourceDataSource = sourceDataSource;
        }

        public String getOutputDir()
        {
            return outputDir;
        }

        public void setOutputDir(String outputDir)
        {
            this.outputDir = outputDir;
        }
    }


    @RequiresPermission(AdminOperationsPermission.class)
    public static class GenerateSchemaAction extends FormViewAction<GenerateSchemaForm>
    {
        @Override
        public void validateCommand(GenerateSchemaForm form, Errors errors)
        {
            // TODO validate schemaNames and dataSources are real
            // TODO validate path is not empty string
        }

        @Override
        public ModelAndView getView(GenerateSchemaForm form, boolean reshow, BindException errors)
        {
            return new JspView<>("/org/labkey/query/view/generateSchema.jsp", form, errors);
        }

        @Override
        public boolean handlePost(GenerateSchemaForm form, BindException errors) throws SQLException, IOException
        {
            StringBuilder importScript = new StringBuilder();

            // NOTE: should we add any kind of dialect tags to the importScript output?
            DbSchema sourceSchema = DbSchema.createFromMetaData(DbScope.getDbScope(form.getSourceDataSource()), form.getSourceSchema(), DbSchemaType.Bare);
            String targetSchema = isBlank(form.getTargetSchema()) ? form.getSourceSchema() : form.getTargetSchema();
            String pathInScript = isBlank(form.getPathInScript()) ? "" : form.getPathInScript();
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
                        try (var factory = new StashingResultsFactory(()->new TableSelector(table).getResults(false)))
                        {
                            Results results = factory.get();
                            if (results.isBeforeFirst()) // only export tables with data
                            {
                                File outputFile = new File(form.getOutputDir(), tableName + ".tsv.gz");
                                GZIPOutputStream outputStream = new GZIPOutputStream(new BufferedOutputStream(new FileOutputStream(outputFile), 64 * 1024), 64 * 1024);
                                try (TSVGridWriter tsv = new TSVGridWriter(factory))
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

                try (PrintWriter writer = PrintWriters.getPrintWriter(new File(form.getOutputDir(), form.getSourceSchema() + "_updateScript.sql")))
                {
                    writer.print(importScript.toString());
                }
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
        public void addNavTrail(NavTree root)
        {
            root.addChild("Generate Schema");
        }
    }


    @RequiresPermission(AdminPermission.class)
    public static class GetSchemasWithDataSourcesAction extends ReadOnlyApiAction
    {
        @Override
        public Object execute(Object o, BindException errors)
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
    public static class TestSQLAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            getPageConfig().addClientDependency(ClientDependency.fromPath("internal/jQuery"));
            getPageConfig().addClientDependency(ClientDependency.fromPath("clientapi"));
            return new HtmlView("<script src='" + AppProps.getInstance().getContextPath() + "/query/testquery.js'></script><div id=testQueryDiv style='min-height:600px;min-width:800px;'></div>");
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }


    @RequiresPermission(ReadPermission.class)
    public static class AnalyzeQueriesAction extends ReadOnlyApiAction<Object>
    {
        @Override
        public Object execute(Object o, BindException errors) throws Exception
        {
            JSONObject ret = new JSONObject();

            try
            {
                QueryService.QueryAnalysisService analysisService = QueryService.get().getQueryAnalysisService();
                if (analysisService != null)
                {
                    DefaultSchema start = DefaultSchema.get(getUser(), getContainer());
                    var deps = new HashSetValuedHashMap<QueryService.DependencyObject, QueryService.DependencyObject>();

                    analysisService.analyzeFolder(start, deps);
                    ret.put("success", true);

                    JSONObject objects = new JSONObject();
                    for (var from : deps.keySet())
                    {
                        objects.put(from.getKey(), from.toJSON());
                        for (var to : deps.get(from))
                            objects.put(to.getKey(), to.toJSON());
                    }
                    ret.put("objects", objects);

                    JSONArray dependants = new JSONArray();
                    for (var from : deps.keySet())
                    {
                        for (var to : deps.get(from))
                            dependants.put(new String[] {from.getKey(), to.getKey()});
                    }
                    ret.put("graph", dependants);
                }
                else
                {
                    ret.put("success", false);
                }
                return ret;
            }
            catch (Throwable e)
            {
                LOG.error(e);
                throw UnexpectedException.wrap(e);
            }
        }
    }

    @Marshal(Marshaller.Jackson)
    @RequiresPermission(ReadPermission.class)
    public class GetQueryEditorMetadataAction extends ReadOnlyApiAction<QueryForm>
    {
        @Override
        protected ObjectMapper createRequestObjectMapper()
        {
            PropertyService propertyService = PropertyService.get();
            if (null != propertyService)
            {
                ObjectMapper mapper = JsonUtil.DEFAULT_MAPPER.copy();
                mapper.addMixIn(GWTPropertyDescriptor.class, MetadataTableJSONMixin.class);
                return mapper;
            }
            else
            {
                throw new RuntimeException("Could not serialize request object");
            }
        }

        @Override
        protected ObjectMapper createResponseObjectMapper()
        {
            return createRequestObjectMapper();
        }

        @Override
        public Object execute(QueryForm queryForm, BindException errors) throws Exception
        {
            QueryDefinition queryDef = queryForm.getQueryDef();
            return MetadataTableJSON.getMetadata(queryDef.getSchema().getSchemaName(), queryDef.getName(), getUser(), getContainer());
        }
    }

    @Marshal(Marshaller.Jackson)
    @RequiresAllOf({EditQueriesPermission.class, UpdatePermission.class})
    public class SaveQueryMetadataAction extends MutatingApiAction<QueryMetadataApiForm>
    {
        @Override
        protected ObjectMapper createRequestObjectMapper()
        {
            PropertyService propertyService = PropertyService.get();
            if (null != propertyService)
            {
                ObjectMapper mapper = JsonUtil.DEFAULT_MAPPER.copy();
                propertyService.configureObjectMapper(mapper, null);
                return mapper;
            }
            else
            {
                throw new RuntimeException("Could not serialize request object");
            }
        }

        @Override
        protected ObjectMapper createResponseObjectMapper()
        {
            return createRequestObjectMapper();
        }

        @Override
        public Object execute(QueryMetadataApiForm queryMetadataApiForm, BindException errors) throws Exception
        {
            String schemaName = queryMetadataApiForm.getSchemaName();
            MetadataTableJSON domain = queryMetadataApiForm.getDomain();
            domain.setUserDefinedQuery(queryMetadataApiForm.isUserDefinedQuery());
            MetadataTableJSON savedDomain = domain.saveMetadata(schemaName, getUser(), getContainer());

            ApiSimpleResponse resp = new ApiSimpleResponse();
            resp.put("success", true);
            resp.put("domain", savedDomain);
            return resp;
        }
    }

    @Marshal(Marshaller.Jackson)
    @RequiresAllOf({EditQueriesPermission.class, UpdatePermission.class})
    public class ResetQueryMetadataAction extends MutatingApiAction<QueryForm>
    {
        @Override
        public Object execute(QueryForm queryForm, BindException errors) throws Exception
        {
            QueryDefinition queryDef = queryForm.getQueryDef();
            return MetadataTableJSON.resetToDefault(queryDef.getSchema().getSchemaName(), queryDef.getName(), getUser(), getContainer());
        }
    }

    private static class QueryMetadataApiForm
    {
        private MetadataTableJSON _domain;
        private String _schemaName;
        private boolean _userDefinedQuery;

        public MetadataTableJSON getDomain()
        {
            return _domain;
        }

        @SuppressWarnings("unused")
        public void setDomain(MetadataTableJSON domain)
        {
            _domain = domain;
        }

        public String getSchemaName()
        {
            return _schemaName;
        }

        @SuppressWarnings("unused")
        public void setSchemaName(String schemaName)
        {
            _schemaName = schemaName;
        }

        public boolean isUserDefinedQuery()
        {
            return _userDefinedQuery;
        }

        @SuppressWarnings("unused")
        public void setUserDefinedQuery(boolean userDefinedQuery)
        {
            _userDefinedQuery = userDefinedQuery;
        }
    }

    public static class TestCase extends AbstractActionPermissionTest
    {
        @Override
        public void testActionPermissions()
        {
            User user = TestContext.get().getUser();
            assertTrue(user.hasSiteAdminPermission());

            QueryController controller = new QueryController();

            // @RequiresPermission(ReadPermission.class)
            assertForReadPermission(user, false,
                new BrowseAction(),
                new BeginAction(),
                controller.new SchemaAction(),
                controller.new SourceQueryAction(),
                controller.new ExecuteQueryAction(),
                controller.new PrintRowsAction(),
                new ExportScriptAction(),
                new ExportRowsExcelAction(),
                new ExportRowsXLSXAction(),
                new ExportQueriesXLSXAction(),
                new ExportExcelTemplateAction(),
                new ExportRowsTsvAction(),
                controller.new ExcelWebQueryDefinitionAction(),
                controller.new SaveQueryViewsAction(),
                controller.new PropertiesQueryAction(),
                controller.new SelectRowsAction(),
                new GetDataAction(),
                controller.new ExecuteSqlAction(),
                controller.new SelectDistinctAction(),
                controller.new GetColumnSummaryStatsAction(),
                controller.new ImportAction(),
                new ExportSqlAction(),
                new UpdateRowsAction(),
                new ImportRowsAction(),
                new DeleteRowsAction(),
                new TableInfoAction(),
                new SaveSessionViewAction(),
                new GetSchemasAction(),
                new GetQueriesAction(),
                new GetQueryViewsAction(),
                new SaveApiTestAction(),
                new ValidateQueryMetadataAction(),
                new AuditHistoryAction(),
                new AuditDetailsAction(),
                new ExportTablesAction(),
                new SaveNamedSetAction(),
                new DeleteNamedSetAction(),
                new ApiTestAction()
            );


            // submitter should be allowed for InsertRows
            assertForReadPermission(user, true, new InsertRowsAction());

            // @RequiresPermission(DeletePermission.class)
            assertForUpdateOrDeletePermission(user,
                new DeleteQueryRowsAction()
            );

            // @RequiresPermission(AdminPermission.class)
            assertForAdminPermission(user,
                new DeleteQueryAction(),
                controller.new MetadataQueryAction(),
                controller.new NewQueryAction(),
                new SaveSourceQueryAction(),

                new TruncateTableAction(),
                new AdminAction(),
                new ManageRemoteConnectionsAction(),
                new ReloadExternalSchemaAction(),
                new ReloadAllUserSchemas(),
                controller.new ManageViewsAction(),
                controller.new InternalDeleteView(),
                controller.new InternalSourceViewAction(),
                controller.new InternalNewViewAction(),
                new QueryExportAuditRedirectAction(),
                new GetSchemasWithDataSourcesAction()
            );

            // @RequiresPermission(AdminOperationsPermission.class)
            assertForAdminOperationsPermission(user,
                new EditRemoteConnectionAction(),
                new DeleteRemoteConnectionAction(),
                new TestRemoteConnectionAction(),
                controller.new RawTableMetaDataAction(),
                controller.new RawSchemaMetaDataAction(),
                new InsertLinkedSchemaAction(),
                new InsertExternalSchemaAction(),
                new DeleteSchemaAction(),
                new EditLinkedSchemaAction(),
                new EditExternalSchemaAction(),
                new GetTablesAction(),
                new GenerateSchemaAction(),
                new SchemaTemplateAction(),
                new SchemaTemplatesAction(),
                new ParseExpressionAction(),
                new ParseQueryAction()
            );

            // @AdminConsoleAction
            assertForAdminPermission(ContainerManager.getRoot(), user,
                new DataSourceAdminAction()
            );

            // In addition to administrators (tested above), trusted analysts who are editors can create and edit queries
            assertTrustedEditorPermission(
                new DeleteQueryAction(),
                controller.new MetadataQueryAction(),
                controller.new NewQueryAction(),
                new SaveSourceQueryAction()
            );
        }
    }
}
