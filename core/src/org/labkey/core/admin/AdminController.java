/*
 * Copyright (c) 2008-2026 LabKey Corporation
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
package org.labkey.core.admin;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Joiner;
import com.google.common.util.concurrent.UncheckedExecutionException;
import jakarta.mail.Message;
import jakarta.mail.MessagingException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.collections4.map.LRUMap;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.EnumUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.xmlbeans.XmlOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.plot.PlotOrientation;
import org.jfree.data.category.DefaultCategoryDataset;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.Constants;
import org.labkey.api.action.ApiResponse;
import org.labkey.api.action.ApiSimpleResponse;
import org.labkey.api.action.ApiUsageException;
import org.labkey.api.action.BaseApiAction;
import org.labkey.api.action.BaseViewAction;
import org.labkey.api.action.ConfirmAction;
import org.labkey.api.action.ExportAction;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.FormViewAction;
import org.labkey.api.action.HasViewContext;
import org.labkey.api.action.IgnoresAllocationTracking;
import org.labkey.api.action.LabKeyError;
import org.labkey.api.action.Marshal;
import org.labkey.api.action.Marshaller;
import org.labkey.api.action.MutatingApiAction;
import org.labkey.api.action.QueryViewAction;
import org.labkey.api.action.QueryViewAction.QueryExportForm;
import org.labkey.api.action.ReadOnlyApiAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleApiJsonForm;
import org.labkey.api.action.SimpleErrorView;
import org.labkey.api.action.SimpleRedirectAction;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.AbstractFolderContext.ExportType;
import org.labkey.api.admin.AdminBean;
import org.labkey.api.admin.AdminUrls;
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.admin.FolderSerializationRegistry;
import org.labkey.api.admin.FolderWriter;
import org.labkey.api.admin.FolderWriterImpl;
import org.labkey.api.admin.HealthCheck;
import org.labkey.api.admin.HealthCheckRegistry;
import org.labkey.api.admin.ImportOptions;
import org.labkey.api.admin.StaticLoggerGetter;
import org.labkey.api.admin.TableXmlUtils;
import org.labkey.api.admin.sitevalidation.SiteValidationResult;
import org.labkey.api.admin.sitevalidation.SiteValidationResultList;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.audit.provider.ContainerAuditProvider;
import org.labkey.api.audit.provider.SiteSettingsAuditProvider;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.cache.CacheStats;
import org.labkey.api.cache.TrackingCache;
import org.labkey.api.cloud.CloudStoreService;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.CaseInsensitiveHashSetValuedMap;
import org.labkey.api.collections.LabKeyCollectors;
import org.labkey.api.compliance.ComplianceFolderSettings;
import org.labkey.api.compliance.ComplianceService;
import org.labkey.api.compliance.PhiColumnBehavior;
import org.labkey.api.data.ButtonBar;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ConnectionWrapper;
import org.labkey.api.data.Container;
import org.labkey.api.data.Container.ContainerException;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerType;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.DataRegion;
import org.labkey.api.data.DataRegionSelection;
import org.labkey.api.data.DatabaseTableType;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbSchemaType;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DisplayColumn;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MenuButton;
import org.labkey.api.data.MvUtil;
import org.labkey.api.data.NormalContainerType;
import org.labkey.api.data.PHI;
import org.labkey.api.data.RenderContext;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TransactionFilter;
import org.labkey.api.data.WorkbookContainerType;
import org.labkey.api.data.dialect.BasePostgreSqlDialect;
import org.labkey.api.data.dialect.SqlDialect.ExecutionPlanType;
import org.labkey.api.data.queryprofiler.QueryProfiler;
import org.labkey.api.data.queryprofiler.QueryProfiler.QueryStatTsvWriter;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.api.StorageProvisioner;
import org.labkey.api.exp.property.Lookup;
import org.labkey.api.files.FileContentService;
import org.labkey.api.mcp.McpService;
import org.labkey.api.message.settings.AbstractConfigTypeProvider.EmailConfigFormImpl;
import org.labkey.api.message.settings.MessageConfigService;
import org.labkey.api.message.settings.MessageConfigService.ConfigTypeProvider;
import org.labkey.api.message.settings.MessageConfigService.NotificationOption;
import org.labkey.api.message.settings.MessageConfigService.UserPreference;
import org.labkey.api.miniprofiler.RequestInfo;
import org.labkey.api.module.AllowedBeforeInitialUserIsSet;
import org.labkey.api.module.AllowedDuringUpgrade;
import org.labkey.api.module.DefaultModule;
import org.labkey.api.module.FolderType;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.module.IgnoresForbiddenProjectCheck;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleLoader.SchemaActions;
import org.labkey.api.module.ModuleLoader.SchemaAndModule;
import org.labkey.api.module.SimpleModule;
import org.labkey.api.moduleeditor.api.ModuleEditorService;
import org.labkey.api.pipeline.DirectoryNotDeletedException;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineJob;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.pipeline.PipelineStatusFile;
import org.labkey.api.pipeline.PipelineStatusUrls;
import org.labkey.api.pipeline.PipelineUrls;
import org.labkey.api.pipeline.PipelineValidationException;
import org.labkey.api.pipeline.view.SetupForm;
import org.labkey.api.products.ProductRegistry;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QuerySchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.RuntimeValidationException;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reports.ExternalScriptEngineDefinition;
import org.labkey.api.reports.LabKeyScriptEngineManager;
import org.labkey.api.search.SearchService;
import org.labkey.api.secrets.SecretService;
import org.labkey.api.secrets.SecretStatus;
import org.labkey.api.security.ActionNames;
import org.labkey.api.security.AdminConsoleAction;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.Directive;
import org.labkey.api.security.ElevatedUser;
import org.labkey.api.security.Encryption;
import org.labkey.api.security.Group;
import org.labkey.api.security.GroupManager;
import org.labkey.api.security.IgnoresTermsOfUse;
import org.labkey.api.security.LoginUrls;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.PermissionsContext;
import org.labkey.api.security.RequiresLogin;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.RequiresSiteAdmin;
import org.labkey.api.security.RoleAssignment;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.SecurityUrls;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.impersonation.GroupImpersonationContextFactory;
import org.labkey.api.security.impersonation.RoleImpersonationContextFactory;
import org.labkey.api.security.impersonation.UserImpersonationContextFactory;
import org.labkey.api.security.permissions.AbstractActionPermissionTest;
import org.labkey.api.security.permissions.AbstractContainerScopingTest;
import org.labkey.api.security.permissions.AdminOperationsPermission;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ApplicationAdminPermission;
import org.labkey.api.security.permissions.CreateProjectPermission;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.PlatformDeveloperPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.SiteAdminPermission;
import org.labkey.api.security.permissions.TroubleshooterPermission;
import org.labkey.api.security.permissions.UploadFileBasedModulePermission;
import org.labkey.api.security.roles.EditorRole;
import org.labkey.api.security.roles.FolderAdminRole;
import org.labkey.api.security.roles.ProjectAdminRole;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.security.roles.SharedViewEditorRole;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.settings.AbstractWriteableSettingsGroup;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.ConceptURIProperties;
import org.labkey.api.settings.DateParsingMode;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.settings.LookAndFeelPropertiesManager.ResourceType;
import org.labkey.api.settings.NetworkDriveProps;
import org.labkey.api.settings.OptionalFeatureService;
import org.labkey.api.settings.OptionalFeatureService.FeatureType;
import org.labkey.api.settings.ProductConfiguration;
import org.labkey.api.settings.WriteableAppProps;
import org.labkey.api.settings.WriteableFolderLookAndFeelProperties;
import org.labkey.api.settings.WriteableLookAndFeelProperties;
import org.labkey.api.util.ButtonBuilder;
import org.labkey.api.util.ConfigurationException;
import org.labkey.api.util.DOM;
import org.labkey.api.util.DOM.Renderable;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.DebugInfoDumper;
import org.labkey.api.util.ExceptionReportingLevel;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.FolderDisplayMode;
import org.labkey.api.util.GUID;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.HtmlString;
import org.labkey.api.util.HtmlStringBuilder;
import org.labkey.api.util.HttpsUtil;
import org.labkey.api.util.JsonUtil;
import org.labkey.api.util.LabKeyProcessBuilder;
import org.labkey.api.util.LinkBuilder;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.MemTracker.HeldReference;
import org.labkey.api.util.MothershipReport;
import org.labkey.api.util.NetworkDrive;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.ResponseHelper;
import org.labkey.api.util.SafeToRenderEnum;
import org.labkey.api.util.SessionAppender;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.util.SystemMaintenance.SystemMaintenanceProperties;
import org.labkey.api.util.SystemMaintenanceJob;
import org.labkey.api.util.TestContext;
import org.labkey.api.util.Tuple3;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.UniqueID;
import org.labkey.api.util.UsageReportingLevel;
import org.labkey.api.util.emailTemplate.EmailTemplate;
import org.labkey.api.util.emailTemplate.EmailTemplateService;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.DataView;
import org.labkey.api.view.FolderManagement.FolderManagementViewAction;
import org.labkey.api.view.FolderManagement.FolderManagementViewPostAction;
import org.labkey.api.view.FolderManagement.ProjectSettingsViewAction;
import org.labkey.api.view.FolderManagement.ProjectSettingsViewPostAction;
import org.labkey.api.view.FolderManagement.TYPE;
import org.labkey.api.view.FolderTab;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.Portal;
import org.labkey.api.view.ShortURLRecord;
import org.labkey.api.view.ShortURLService;
import org.labkey.api.view.TabStripView;
import org.labkey.api.view.URLException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewBackgroundInfo;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.view.WebPartView;
import org.labkey.api.view.template.ClientDependency;
import org.labkey.api.view.template.EmptyView;
import org.labkey.api.view.template.PageConfig;
import org.labkey.api.view.template.PageConfig.Template;
import org.labkey.api.wiki.WikiRendererType;
import org.labkey.api.wiki.WikiRenderingService;
import org.labkey.api.writer.FileSystemFile;
import org.labkey.api.writer.HtmlWriter;
import org.labkey.api.writer.ZipFile;
import org.labkey.api.writer.ZipUtil;
import org.labkey.bootstrap.ExplodedModuleService;
import org.labkey.core.admin.miniprofiler.MiniProfilerController;
import org.labkey.core.admin.sitevalidation.SiteValidationJob;
import org.labkey.core.admin.sql.SqlScriptController;
import org.labkey.core.login.LoginController;
import org.labkey.core.portal.CollaborationFolderType;
import org.labkey.core.portal.ProjectController;
import org.labkey.core.query.CoreQuerySchema;
import org.labkey.core.query.PostgresUserSchema;
import org.labkey.core.reports.ExternalScriptEngineDefinitionImpl;
import org.labkey.core.security.AllowedExternalResourceHosts;
import org.labkey.core.security.AllowedExternalResourceHosts.AllowedHost;
import org.labkey.core.security.BlockListFilter;
import org.labkey.core.security.SecurityController;
import org.labkey.data.xml.TablesDocument;
import org.labkey.filters.ContentSecurityPolicyFilter;
import org.labkey.filters.ContentSecurityPolicyFilter.ContentSecurityPolicyType;
import org.labkey.security.xml.GroupEnumType;
import org.labkey.vfs.FileLike;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import java.awt.*;
import java.beans.Introspector;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringWriter;
import java.lang.management.BufferPoolMXBean;
import java.lang.management.ClassLoadingMXBean;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryType;
import java.lang.management.MemoryUsage;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.RuntimeMXBean;
import java.lang.management.ThreadMXBean;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.isBlank;
import static org.apache.commons.lang3.StringUtils.isNotBlank;
import static org.labkey.api.data.MultiValuedRenderContext.VALUE_DELIMITER_REGEX;
import static org.labkey.api.settings.AdminConsole.SettingsLinkType.Configuration;
import static org.labkey.api.settings.AdminConsole.SettingsLinkType.Diagnostics;
import static org.labkey.api.util.DOM.A;
import static org.labkey.api.util.DOM.Attribute.href;
import static org.labkey.api.util.DOM.Attribute.method;
import static org.labkey.api.util.DOM.Attribute.name;
import static org.labkey.api.util.DOM.Attribute.src;
import static org.labkey.api.util.DOM.Attribute.style;
import static org.labkey.api.util.DOM.Attribute.title;
import static org.labkey.api.util.DOM.Attribute.type;
import static org.labkey.api.util.DOM.Attribute.value;
import static org.labkey.api.util.DOM.B;
import static org.labkey.api.util.DOM.BR;
import static org.labkey.api.util.DOM.DIV;
import static org.labkey.api.util.DOM.IMG;
import static org.labkey.api.util.DOM.LI;
import static org.labkey.api.util.DOM.P;
import static org.labkey.api.util.DOM.PRE;
import static org.labkey.api.util.DOM.SPAN;
import static org.labkey.api.util.DOM.STRONG;
import static org.labkey.api.util.DOM.STYLE;
import static org.labkey.api.util.DOM.TABLE;
import static org.labkey.api.util.DOM.TD;
import static org.labkey.api.util.DOM.TH;
import static org.labkey.api.util.DOM.TR;
import static org.labkey.api.util.DOM.UL;
import static org.labkey.api.util.DOM.at;
import static org.labkey.api.util.DOM.cl;
import static org.labkey.api.util.DOM.createHtmlFragment;
import static org.labkey.api.util.HtmlString.NBSP;
import static org.labkey.api.util.logging.LogHelper.getLabKeyLogDir;
import static org.labkey.api.view.FolderManagement.EVERY_CONTAINER;
import static org.labkey.api.view.FolderManagement.FOLDERS_AND_PROJECTS;
import static org.labkey.api.view.FolderManagement.FOLDERS_ONLY;
import static org.labkey.api.view.FolderManagement.NOT_ROOT;
import static org.labkey.api.view.FolderManagement.PROJECTS_ONLY;
import static org.labkey.api.view.FolderManagement.ROOT;
import static org.labkey.api.view.FolderManagement.addTab;

public class AdminController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(
        AdminController.class,
        FileListAction.class,
        FilesSiteSettingsAction.class,
        UpdateFilePathsAction.class
    );

    private static final Logger LOG = LogHelper.getLogger(AdminController.class, "Admin-related UI and APIs");
    private static final Logger CLIENT_LOG = LogHelper.getLogger(LogAction.class, "Client/browser logging submitted to server");
    private static final String HEAP_MEMORY_KEY = "Total Heap Memory";

    private static long _errorMark = 0;
    private static long _primaryLogMark = 0;

    public static void registerAdminConsoleLinks()
    {
        Container root = ContainerManager.getRoot();

        // Configuration
        AdminConsole.addLink(Configuration, "authentication", urlProvider(LoginUrls.class).getConfigureURL());
        AdminConsole.addLink(Configuration, "email customization", new ActionURL(CustomizeEmailAction.class, root), AdminPermission.class);
        AdminConsole.addLink(Configuration, "deprecated features", new ActionURL(OptionalFeaturesAction.class, root).addParameter("type", FeatureType.Deprecated.name()), TroubleshooterPermission.class);
        AdminConsole.addLink(Configuration, "experimental features", new ActionURL(OptionalFeaturesAction.class, root).addParameter("type", FeatureType.Experimental.name()), TroubleshooterPermission.class);
        AdminConsole.addLink(Configuration, "optional features", new ActionURL(OptionalFeaturesAction.class, root).addParameter("type", FeatureType.Optional.name()), TroubleshooterPermission.class);
        if (!ProductRegistry.getProducts().isEmpty())
            AdminConsole.addLink(Configuration, "product configuration", new ActionURL(ProductConfigurationAction.class, root), AdminOperationsPermission.class);
        // TODO move to FileContentModule
        if (ModuleLoader.getInstance().hasModule("FileContent"))
            AdminConsole.addLink(Configuration, "files", new ActionURL(FilesSiteSettingsAction.class, root), AdminOperationsPermission.class);
        AdminConsole.addLink(Configuration, "folder types", new ActionURL(FolderTypesAction.class, root), AdminPermission.class);
        AdminConsole.addLink(Configuration, "look and feel settings", new ActionURL(LookAndFeelSettingsAction.class, root));
        AdminConsole.addLink(Configuration, "missing value indicators", new AdminUrlsImpl().getMissingValuesURL(root), AdminPermission.class);
        AdminConsole.addLink(Configuration, "project display order", new ActionURL(ReorderFoldersAction.class, root), AdminPermission.class);
        AdminConsole.addLink(Configuration, "short urls", new ActionURL(ShortURLAdminAction.class, root), TroubleshooterPermission.class);
        AdminConsole.addLink(Configuration, "site settings", new AdminUrlsImpl().getCustomizeSiteURL());
        AdminConsole.addLink(Configuration, "system maintenance", new ActionURL(ConfigureSystemMaintenanceAction.class, root));
        AdminConsole.addLink(Configuration, "allowed external redirect hosts", new ActionURL(AllowListAction.class, root).addParameter("type", AllowListType.Redirect.name()), TroubleshooterPermission.class);
        AdminConsole.addLink(Configuration, "allowed external resource hosts", new ActionURL(ExternalSourcesAction.class, root), TroubleshooterPermission.class);
        AdminConsole.addLink(Configuration, "allowed file extensions", new ActionURL(AllowListAction.class, root).addParameter("type", AllowListType.FileExtension.name()), TroubleshooterPermission.class);

        // Diagnostics
        AdminConsole.addLink(Diagnostics, "actions", new ActionURL(ActionsAction.class, root));
        AdminConsole.addLink(Diagnostics, "attachments", new ActionURL(AttachmentsAction.class, root));
        AdminConsole.addLink(Diagnostics, "caches", new ActionURL(CachesAction.class, root));
        AdminConsole.addLink(Diagnostics, "check database", new ActionURL(DbCheckerAction.class, root), AdminOperationsPermission.class);
        AdminConsole.addLink(Diagnostics, "credits", new ActionURL(CreditsAction.class, root));
        AdminConsole.addLink(Diagnostics, "dump heap", new ActionURL(DumpHeapAction.class, root));
        AdminConsole.addLink(Diagnostics, "environment variables", new ActionURL(EnvironmentVariablesAction.class, root), SiteAdminPermission.class);
        AdminConsole.addLink(Diagnostics, "memory usage", new ActionURL(MemTrackerAction.class, root));

        if (CoreSchema.getInstance().getSqlDialect().isPostgreSQL())
        {
            AdminConsole.addLink(Diagnostics, "postgres activity", new ActionURL(PostgresStatActivityAction.class, root));
            AdminConsole.addLink(Diagnostics, "postgres locks", new ActionURL(PostgresLocksAction.class, root));
            AdminConsole.addLink(Diagnostics, "postgres table sizes", new ActionURL(PostgresTableSizesAction.class, root));
        }

        AdminConsole.addLink(Diagnostics, "profiler", new ActionURL(MiniProfilerController.ManageAction.class, root));
        AdminConsole.addLink(Diagnostics, "queries", getQueriesURL(null));
        AdminConsole.addLink(Diagnostics, "reset site errors", new ActionURL(ResetErrorMarkAction.class, root), AdminPermission.class);
        AdminConsole.addLink(Diagnostics, "running threads", new ActionURL(ShowThreadsAction.class, root));
        AdminConsole.addLink(Diagnostics, "secrets", new ActionURL(SecretsAction.class, root), SiteAdminPermission.class);
        AdminConsole.addLink(Diagnostics, "site validation", new ActionURL(ConfigureSiteValidationAction.class, root), AdminPermission.class);
        AdminConsole.addLink(Diagnostics, "sql scripts", new ActionURL(SqlScriptController.ScriptsAction.class, root), AdminOperationsPermission.class);
        AdminConsole.addLink(Diagnostics, "suspicious activity", new ActionURL(SuspiciousAction.class, root));
        AdminConsole.addLink(Diagnostics, "system properties", new ActionURL(SystemPropertiesAction.class, root), SiteAdminPermission.class);
        AdminConsole.addLink(Diagnostics, "test email configuration", new ActionURL(EmailTestAction.class, root), AdminOperationsPermission.class);
        AdminConsole.addLink(Diagnostics, "view all site errors", new ActionURL(ShowAllErrorsAction.class, root));
        AdminConsole.addLink(Diagnostics, "view all site errors since reset", new ActionURL(ShowErrorsSinceMarkAction.class, root));
        AdminConsole.addLink(Diagnostics, "view csp report log file", new ActionURL(ShowCspReportLogAction.class, root));
        AdminConsole.addLink(Diagnostics, "view primary site log file", new ActionURL(ShowPrimaryLogAction.class, root));
    }

    public static void registerManagementTabs()
    {
        addTab(TYPE.FolderManagement, "Folder Tree", "folderTree", EVERY_CONTAINER, ManageFoldersAction.class);
        addTab(TYPE.FolderManagement, "Folder Type", "folderType", NOT_ROOT, FolderTypeAction.class);
        addTab(TYPE.FolderManagement, "Missing Values", "mvIndicators", EVERY_CONTAINER, MissingValuesAction.class);
        addTab(TYPE.FolderManagement, "Module Properties", "props", c -> {
            if (!c.isRoot())
            {
                // Show module properties tab only if a module w/ properties to set is present for current folder
                for (Module m : c.getActiveModules())
                    if (!m.getModuleProperties().isEmpty())
                        return true;
            }

            return false;
        }, ModulePropertiesAction.class);
        addTab(TYPE.FolderManagement, "Concepts", "concepts", c -> {
            // Show Concepts tab only if the experiment module is enabled in this container
            return c.getActiveModules().contains(ModuleLoader.getInstance().getModule(ExperimentService.MODULE_NAME));
        }, AdminController.ConceptsAction.class);
        // Show Notifications tab only if we have registered notification providers
        addTab(TYPE.FolderManagement, "Notifications", "notifications", c -> NOT_ROOT.test(c) && !MessageConfigService.get().getConfigTypes().isEmpty(), NotificationsAction.class);
        addTab(TYPE.FolderManagement, "Export", "export", NOT_ROOT, ExportFolderAction.class);
        addTab(TYPE.FolderManagement, "Import", "import", NOT_ROOT, ImportFolderAction.class);
        addTab(TYPE.FolderManagement, "Files", "files", FOLDERS_AND_PROJECTS, FileRootsAction.class);
        addTab(TYPE.FolderManagement, "Formats", "settings", FOLDERS_ONLY, FolderSettingsAction.class);
        addTab(TYPE.FolderManagement, "Information", "info", NOT_ROOT, FolderInformationAction.class);
        addTab(TYPE.FolderManagement, "R Config", "rConfig", NOT_ROOT, RConfigurationAction.class);

        addTab(TYPE.ProjectSettings, "Properties", "properties", PROJECTS_ONLY, ProjectSettingsAction.class);
        addTab(TYPE.ProjectSettings, "Resources", "resources", PROJECTS_ONLY, ResourcesAction.class);
        addTab(TYPE.ProjectSettings, "Menu Bar", "menubar", PROJECTS_ONLY, MenuBarAction.class);
        addTab(TYPE.ProjectSettings, "Files", "files", PROJECTS_ONLY, FilesAction.class);

        addTab(TYPE.LookAndFeelSettings, "Properties", "properties", ROOT, LookAndFeelSettingsAction.class);
        addTab(TYPE.LookAndFeelSettings, "Resources", "resources", ROOT, AdminConsoleResourcesAction.class);
    }

    public AdminController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresNoPermission
    public static class BeginAction extends SimpleRedirectAction<Object>
    {
        @Override
        public ActionURL getRedirectURL(Object o)
        {
            return getShowAdminURL();
        }
    }

    private void addAdminNavTrail(NavTree root, String childTitle, @NotNull Class<? extends Controller> action)
    {
        addAdminNavTrail(root, childTitle, action, getContainer());
    }

    private static void addAdminNavTrail(NavTree root, @NotNull Container container)
    {
        if (container.isRoot())
            root.addChild("Admin Console", getShowAdminURL().setFragment("links"));
    }

    private static void addAdminNavTrail(NavTree root, String childTitle, @NotNull Class<? extends Controller> action, @NotNull Container container)
    {
        addAdminNavTrail(root, container);
        root.addChild(childTitle, new ActionURL(action, container));
    }

    public static ActionURL getShowAdminURL()
    {
        return new ActionURL(ShowAdminAction.class, ContainerManager.getRoot());
    }

    @Override
    protected void beforeAction(Controller action) throws ServletException
    {
        super.beforeAction(action);
        if (action instanceof BaseViewAction<?> viewaction)
            viewaction.getPageConfig().setRobotsNone();
    }

    @AdminConsoleAction
    public static class ShowAdminAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return new JspView<>("/org/labkey/core/admin/admin.jsp");
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            URLHelper returnUrl = getViewContext().getActionURL().getReturnUrl();
            if (null != returnUrl)
                root.addChild("Return to Project", returnUrl);
            root.addChild("Admin Console");
            setHelpTopic("siteManagement");
        }
    }

    @RequiresPermission(TroubleshooterPermission.class)
    public class ShowModuleErrorsAction extends SimpleViewAction<Object>
    {
        @Override
        public void addNavTrail(NavTree root)
        {
            addAdminNavTrail(root, "Module Errors", this.getClass());
        }

        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return new JspView<>("/org/labkey/core/admin/moduleErrors.jsp");
        }
    }

    public static class AdminUrlsImpl implements AdminUrls
    {
        @Override
        public ActionURL getModuleErrorsURL()
        {
            return new ActionURL(ShowModuleErrorsAction.class, ContainerManager.getRoot());
        }

        @Override
        public ActionURL getAdminConsoleURL()
        {
            return getShowAdminURL();
        }

        @Override
        public ActionURL getModuleStatusURL(URLHelper returnUrl)
        {
            return AdminController.getModuleStatusURL(returnUrl);
        }

        @Override
        public ActionURL getCustomizeSiteURL()
        {
            return new ActionURL(CustomizeSiteAction.class, ContainerManager.getRoot());
        }

        @Override
        public ActionURL getCustomizeSiteURL(boolean upgradeInProgress)
        {
            ActionURL url = getCustomizeSiteURL();

            if (upgradeInProgress)
                url.addParameter("upgradeInProgress", "1");

            return url;
        }

        @Override
        public ActionURL getProjectSettingsURL(Container c)
        {
            return new ActionURL(ProjectSettingsAction.class, LookAndFeelProperties.getSettingsContainer(c));
        }

        ActionURL getLookAndFeelResourcesURL(Container c)
        {
            return c.isRoot() ? new ActionURL(AdminConsoleResourcesAction.class, c) : new ActionURL(ResourcesAction.class, LookAndFeelProperties.getSettingsContainer(c));
        }

        @Override
        public ActionURL getProjectSettingsMenuURL(Container c)
        {
            return new ActionURL(MenuBarAction.class, LookAndFeelProperties.getSettingsContainer(c));
        }

        @Override
        public ActionURL getProjectSettingsFileURL(Container c)
        {
            return new ActionURL(FilesAction.class, LookAndFeelProperties.getSettingsContainer(c));
        }

        @Override
        public ActionURL getCustomizeEmailURL(@NotNull Container c, @Nullable Class<? extends EmailTemplate> selectedTemplate, @Nullable URLHelper returnUrl)
        {
            return getCustomizeEmailURL(c, selectedTemplate == null ? null : selectedTemplate.getName(), returnUrl);
        }

        public ActionURL getCustomizeEmailURL(@NotNull Container c, @Nullable String selectedTemplate, @Nullable URLHelper returnUrl)
        {
            ActionURL url = new ActionURL(CustomizeEmailAction.class, c);
            if (selectedTemplate != null)
            {
                url.addParameter("templateClass", selectedTemplate);
            }
            if (returnUrl != null)
            {
                url.addReturnUrl(returnUrl);
            }
            return url;
        }

        public ActionURL getResetLookAndFeelPropertiesURL(Container c)
        {
            return new ActionURL(ResetPropertiesAction.class, c);
        }

        @Override
        public ActionURL getMaintenanceURL(URLHelper returnUrl)
        {
            ActionURL url = new ActionURL(MaintenanceAction.class, ContainerManager.getRoot());
            if (returnUrl != null)
                url.addReturnUrl(returnUrl);
            return url;
        }

        @Override
        public ActionURL getModulesDetailsURL()
        {
            return new ActionURL(ModulesAction.class, ContainerManager.getRoot());
        }

        @Override
        public ActionURL getDeleteModuleURL(String moduleName)
        {
            return new ActionURL(DeleteModuleAction.class, ContainerManager.getRoot()).addParameter("name", moduleName);
        }

        @Override
        public ActionURL getManageFoldersURL(Container c)
        {
            return new ActionURL(ManageFoldersAction.class, c);
        }

        @Override
        public ActionURL getFolderTypeURL(Container c)
        {
            return new ActionURL(FolderTypeAction.class, c);
        }

        @Override
        public ActionURL getExportFolderURL(Container c)
        {
            return new ActionURL(ExportFolderAction.class, c);
        }

        @Override
        public ActionURL getImportFolderURL(Container c)
        {
            return new ActionURL(ImportFolderAction.class, c);
        }

        @Override
        public ActionURL getCreateProjectURL(@Nullable ActionURL returnUrl)
        {
            return getCreateFolderURL(ContainerManager.getRoot(), returnUrl);
        }

        @Override
        public ActionURL getCreateFolderURL(Container c, @Nullable ActionURL returnUrl)
        {
            ActionURL result = new ActionURL(CreateFolderAction.class, c);
            if (returnUrl != null)
            {
                result.addReturnUrl(returnUrl);
            }
            return result;
        }

        public ActionURL getSetFolderPermissionsURL(Container c)
        {
            return new ActionURL(SetFolderPermissionsAction.class, c);
        }

        @Override
        public void addAdminNavTrail(NavTree root, @NotNull Container container)
        {
            AdminController.addAdminNavTrail(root, container);
        }

        @Override
        public void addAdminNavTrail(NavTree root, String childTitle, @NotNull Class<? extends Controller> action, @NotNull Container container)
        {
            AdminController.addAdminNavTrail(root, childTitle, action, container);
        }

        @Override
        public void addModulesNavTrail(NavTree root, String childTitle, @NotNull Container container)
        {
            if (container.isRoot())
                addAdminNavTrail(root, "Modules", ModulesAction.class, container);

            root.addChild(childTitle);
        }

        @Override
        public ActionURL getFileRootsURL(Container c)
        {
            return new ActionURL(FileRootsAction.class, c);
        }

        @Override
        public ActionURL getLookAndFeelSettingsURL(Container c)
        {
            if (c.isRoot())
                return getSiteLookAndFeelSettingsURL();
            else if (c.isProject())
                return getProjectSettingsURL(c);
            else
                return getFolderSettingsURL(c);
        }

        @Override
        public ActionURL getSiteLookAndFeelSettingsURL()
        {
            return new ActionURL(LookAndFeelSettingsAction.class, ContainerManager.getRoot());
        }

        @Override
        public ActionURL getFolderSettingsURL(Container c)
        {
            return new ActionURL(FolderSettingsAction.class, c);
        }

        @Override
        public ActionURL getNotificationsURL(Container c)
        {
            return new ActionURL(NotificationsAction.class, c);
        }

        @Override
        public ActionURL getModulePropertiesURL(Container c)
        {
            return new ActionURL(ModulePropertiesAction.class, c);
        }

        @Override
        public ActionURL getMissingValuesURL(Container c)
        {
            return new ActionURL(MissingValuesAction.class, c);
        }

        public ActionURL getInitialFolderSettingsURL(Container c)
        {
            return new ActionURL(SetInitialFolderSettingsAction.class, c);
        }

        @Override
        public ActionURL getMemTrackerURL()
        {
            return new ActionURL(MemTrackerAction.class, ContainerManager.getRoot());
        }

        @Override
        public ActionURL getFilesSiteSettingsURL()
        {
            return new ActionURL(FilesSiteSettingsAction.class, ContainerManager.getRoot());
        }

        @Override
        public ActionURL getSessionLoggingURL()
        {
            return new ActionURL(SessionLoggingAction.class, ContainerManager.getRoot());
        }

        @Override
        public ActionURL getTrackedAllocationsViewerURL()
        {
            return new ActionURL(TrackedAllocationsViewerAction.class, ContainerManager.getRoot());
        }

        @Override
        public ActionURL getSystemMaintenanceURL()
        {
            return new ActionURL(ConfigureSystemMaintenanceAction.class, ContainerManager.getRoot());
        }

        @Override
        public ActionURL getCspReportToURL()
        {
            return new ActionURL(ContentSecurityPolicyReportToAction.class, ContainerManager.getRoot());
        }

        @Override
        public ActionURL getAllowedExternalRedirectHostsURL()
        {
            return new ActionURL(AllowListAction.class, ContainerManager.getRoot())
                .addParameter("type", AllowListType.Redirect.name());
        }

        @Override
        public ActionURL getDeleteEncryptedContentURL()
        {
            return new ActionURL(DeleteEncryptedContentAction.class, ContainerManager.getRoot());
        }

        public static ActionURL getDeprecatedFeaturesURL()
        {
            return new ActionURL(OptionalFeaturesAction.class, ContainerManager.getRoot()).addParameter("type", FeatureType.Deprecated.name());
        }
    }

    public static class MaintenanceBean
    {
        public HtmlString content;
        public ActionURL loginURL;
    }

    /**
     * During upgrade, startup, or maintenance mode, the user will be redirected to
     * MaintenanceAction and only admin users will be allowed to log into the server.
     * The maintenance.jsp page checks startup is complete or adminOnly mode is turned off
     * and will redirect to the returnUrl or the loginURL.
     * See Issue 18758 for more information.
     */
    @RequiresNoPermission
    @AllowedDuringUpgrade
    @IgnoresAllocationTracking
    public static class MaintenanceAction extends SimpleViewAction<ReturnUrlForm>
    {
        private String _title = "Maintenance in progress";

        @Override
        public ModelAndView getView(ReturnUrlForm form, BindException errors)
        {
            if (!getUser().hasSiteAdminPermission())
            {
                getViewContext().getResponse().setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            }
            getPageConfig().setTemplate(Template.Dialog);

            boolean upgradeInProgress = ModuleLoader.getInstance().isUpgradeInProgress();
            boolean startupInProgress = ModuleLoader.getInstance().isStartupInProgress();
            boolean maintenanceMode = AppProps.getInstance().isUserRequestedAdminOnlyMode();

            HtmlString content = HtmlString.of("This site is currently undergoing maintenance, only site admins may login at this time.");
            if (upgradeInProgress)
            {
                _title = "Upgrade in progress";
                content = HtmlString.of("Upgrade in progress: only site admins may login at this time. Your browser will be redirected when startup is complete.");
            }
            else if (startupInProgress)
            {
                _title = "Startup in progress";
                content = HtmlString.of("Startup in progress: only site admins may login at this time. Your browser will be redirected when startup is complete.");
            }
            else if (maintenanceMode)
            {
                WikiRenderingService wikiService = WikiRenderingService.get();
                content = wikiService.getFormattedHtml(WikiRendererType.RADEOX, ModuleLoader.getInstance().getAdminOnlyMessage(), "Admin only message");
            }

            if (content == null)
                content = HtmlString.of(_title);

            ActionURL loginURL = null;
            if (getUser().isGuest())
            {
                URLHelper returnUrl = form.getReturnUrlHelper();
                if (returnUrl != null)
                    loginURL = urlProvider(LoginUrls.class).getLoginURL(ContainerManager.getRoot(), returnUrl);
                else
                    loginURL = urlProvider(LoginUrls.class).getLoginURL();
            }

            MaintenanceBean bean = new MaintenanceBean();
            bean.content = content;
            bean.loginURL = loginURL;

            JspView<MaintenanceBean> view = new JspView<>("/org/labkey/core/admin/maintenance.jsp", bean, errors);
            view.setTitle(_title);
            return view;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild(_title);
        }
    }

    /**
     * Similar to SqlScriptController.GetModuleStatusAction except that Guest is allowed to check that the startup is complete.
     */
    @RequiresNoPermission
    @AllowedDuringUpgrade
    @IgnoresAllocationTracking
    public static class StartupStatusAction extends ReadOnlyApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object o, BindException errors)
        {
            JSONObject result = new JSONObject();
            result.put("startupComplete", ModuleLoader.getInstance().isStartupComplete());
            result.put("adminOnly", AppProps.getInstance().isUserRequestedAdminOnlyMode());

            return new ApiSimpleResponse(result);
        }
    }

    @RequiresSiteAdmin
    @IgnoresTermsOfUse
    public static class GetPendingRequestCountAction extends ReadOnlyApiAction<Object>
    {
        @Override
        public ApiResponse execute(Object o, BindException errors)
        {
            JSONObject result = new JSONObject();
            result.put("pendingRequestCount", TransactionFilter.getPendingRequestCount() - 1 /* Exclude this request */);

            return new ApiSimpleResponse(result);
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class GetModulesAction extends ReadOnlyApiAction<GetModulesForm>
    {
        @Override
        public ApiResponse execute(GetModulesForm form, BindException errors)
        {
            Container c = ContainerManager.getForPath(getContainer().getPath());

            ApiSimpleResponse response = new ApiSimpleResponse();

            List<Map<String, Object>> qinfos = new ArrayList<>();

            FolderType folderType = c.getFolderType();
            List<Module> allModules = new ArrayList<>(ModuleLoader.getInstance().getModules());
            allModules.sort(Comparator.comparing(module -> module.getTabName(getViewContext()), String.CASE_INSENSITIVE_ORDER));

            //note: this has been altered to use Container.getRequiredModules() instead of FolderType
            //this is b/c a parent container must consider child workbooks when determining the set of requiredModules
            Set<Module> requiredModules = c.getRequiredModules(); //folderType.getActiveModules() != null ? folderType.getActiveModules() : new HashSet<Module>();
            Set<Module> activeModules = c.getActiveModules(getUser());

            for (Module m : allModules)
            {
                Map<String, Object> qinfo = new HashMap<>();

                qinfo.put("name", m.getName());
                qinfo.put("required", requiredModules.contains(m));
                qinfo.put("active", activeModules.contains(m) || requiredModules.contains(m));
                qinfo.put("enabled", (m.getTabDisplayMode() == Module.TabDisplayMode.DISPLAY_USER_PREFERENCE ||
                        m.getTabDisplayMode() == Module.TabDisplayMode.DISPLAY_USER_PREFERENCE_DEFAULT) && !requiredModules.contains(m));
                qinfo.put("tabName", m.getTabName(getViewContext()));
                qinfo.put("requireSitePermission", m.getRequireSitePermission());
                qinfos.add(qinfo);
            }

            response.put("modules", qinfos);
            response.put("folderType", folderType.getName());

            return response;
        }
    }

    public static class GetModulesForm
    {
    }

    @RequiresNoPermission
    @AllowedDuringUpgrade
    // This action is invoked by HttpsUtil.checkSslRedirectConfiguration(), often while upgrade is in progress
    public static class GuidAction extends ExportAction<Object>
    {
        @Override
        public void export(Object o, HttpServletResponse response, BindException errors) throws Exception
        {
            response.getWriter().write(GUID.makeGUID());
        }
    }

    /**
     * Preform health checks corresponding to the given categories.
     */
    @Marshal(Marshaller.Jackson)
    @RequiresNoPermission
    @AllowedDuringUpgrade
    @AllowedBeforeInitialUserIsSet
    public static class HealthCheckAction extends ReadOnlyApiAction<HealthCheckForm>
    {
        @Override
        public ApiResponse execute(HealthCheckForm form, BindException errors) throws Exception
        {
            if (!ModuleLoader.getInstance().isStartupComplete())
                return new ApiSimpleResponse("healthy", false);

            Collection<String> categories = form.getCategories() == null ? Collections.singleton(HealthCheckRegistry.DEFAULT_CATEGORY) : Arrays.asList(form.getCategories().split(","));
            HealthCheck.Result checkResult = HealthCheckRegistry.get().checkHealth(categories);

            checkResult.getDetails().put("healthy", checkResult.isHealthy());

            if (getUser().hasRootAdminPermission())
            {
                return new ApiSimpleResponse(checkResult.getDetails());
            }
            else
            {
                if (!checkResult.isHealthy())
                {
                    try (var writer = createResponseWriter())
                    {
                        writer.writeResponse(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Server isn't ready yet");
                    }
                    return null;
                }

                return new ApiSimpleResponse("healthy", checkResult.isHealthy());
            }
        }
    }

    public static class HealthCheckForm
    {
        private String _categories; // if null, all categories will be checked.

        public String getCategories()
        {
            return _categories;
        }

        @SuppressWarnings("unused")
        public void setCategories(String categories)
        {
            _categories = categories;
        }
    }

    // No security checks... anyone (even guests) can view the credits page
    @RequiresNoPermission
    public class CreditsAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            VBox views = new VBox();
            List<Module> modules = new ArrayList<>(ModuleLoader.getInstance().getModules());
            modules.sort(Comparator.comparing(Module::getName, String.CASE_INSENSITIVE_ORDER));

            addCreditsViews(views, modules, "jars.txt", "JAR");
            addCreditsViews(views, modules, "scripts.txt", "Script, Icon and Font");
            addCreditsViews(views, modules, "source.txt", "Java Source Code");
            addCreditsViews(views, modules, "executables.txt", "Executable");

            return views;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addAdminNavTrail(root, "Credits", this.getClass());
        }
    }

    private void addCreditsViews(VBox views, List<Module> modules, String creditsFile, String fileType) throws IOException
    {
        for (Module module : modules)
        {
            String wikiSource = getCreditsFile(module, creditsFile);

            if (null != wikiSource)
            {
                String title = fileType + " Files Distributed with the " + module.getName() + " Module";
                CreditsView credits = new CreditsView(wikiSource, title);
                views.addView(credits);
            }
        }
    }

    private static class CreditsView extends WebPartView<Object>
    {
        private Renderable _html;

        CreditsView(@Nullable String wikiSource, String title)
        {
            super(title);

            wikiSource = StringUtils.trimToEmpty(wikiSource);

            if (StringUtils.isNotEmpty(wikiSource))
            {
                WikiRenderingService wikiService = WikiRenderingService.get();
                HtmlString html = wikiService.getFormattedHtml(WikiRendererType.RADEOX, wikiSource, "Credits page");
                _html = DOM.createHtmlFragment(STYLE(at(type, "text/css"), "tr.table-odd td { background-color: #EEEEEE; }"), html);
            }
        }

        @Override
        public void renderView(Object model, HtmlWriter out)
        {
            out.write(_html);
        }
    }

    private static String getCreditsFile(Module module, String filename) throws IOException
    {
        // credits files are in /resources/credits
        InputStream is = module.getResourceStream("credits/" + filename);

        return null == is ? null : PageFlowUtil.getStreamContentsAsString(is);
    }

    private void validateNetworkDrive(NetworkDriveForm form, Errors errors)
    {
        if (isBlank(form.getNetworkDriveUser()) || isBlank(form.getNetworkDrivePath()) ||
                isBlank(form.getNetworkDrivePassword()) || isBlank(form.getNetworkDriveLetter()))
        {
            errors.reject(ERROR_MSG, "All fields are required");
        }
        else if (form.getNetworkDriveLetter().trim().length() > 1)
        {
            errors.reject(ERROR_MSG, "Network drive letter must be a single character");
        }
        else
        {
            char letter = form.getNetworkDriveLetter().trim().toLowerCase().charAt(0);

            if (letter < 'a' || letter > 'z')
            {
                errors.reject(ERROR_MSG, "Network drive letter must be a letter");
            }
        }
    }

    public static class ResourceForm
    {
        private String _resource;

        public String getResource()
        {
            return _resource;
        }

        public void setResource(String resource)
        {
            _resource = resource;
        }

        public ResourceType getResourceType()
        {
            return ResourceType.valueOf(_resource);
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class ResetResourceAction extends FormHandlerAction<ResourceForm>
    {
        @Override
        public void validateCommand(ResourceForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(ResourceForm form, BindException errors) throws Exception
        {
            form.getResourceType().delete(getContainer(), getUser());
            WriteableAppProps.incrementLookAndFeelRevisionAndSave();
            return true;
        }

        @Override
        public URLHelper getSuccessURL(ResourceForm form)
        {
            return new AdminUrlsImpl().getLookAndFeelResourcesURL(getContainer());
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class ResetPropertiesAction extends FormHandlerAction<Object>
    {
        private URLHelper _returnUrl;

        @Override
        public void validateCommand(Object target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            Container c = getContainer();
            boolean folder = !(c.isRoot() || c.isProject());
            boolean hasAdminOpsPerm = c.hasPermission(getUser(), AdminOperationsPermission.class);

            WriteableFolderLookAndFeelProperties props = folder ? LookAndFeelProperties.getWriteableFolderInstance(c) : LookAndFeelProperties.getWriteableInstance(c);
            props.clear(hasAdminOpsPerm);
            props.save();
            // TODO: Audit log?

            AdminUrls urls = new AdminUrlsImpl();

            // Folder-level settings are just display formats and measure/dimension flags -- no need to increment L&F revision
            if (!folder)
                WriteableAppProps.incrementLookAndFeelRevisionAndSave();

            _returnUrl = urls.getLookAndFeelSettingsURL(c);

            return true;
        }

        @Override
        public URLHelper getSuccessURL(Object o)
        {
            return _returnUrl;
        }
    }

    @AdminConsoleAction(AdminOperationsPermission.class)
    public class CustomizeSiteAction extends FormViewAction<SiteSettingsForm>
    {
        @Override
        public ModelAndView getView(SiteSettingsForm form, boolean reshow, BindException errors)
        {
            if (form.isUpgradeInProgress())
                getPageConfig().setTemplate(Template.Dialog);

            SiteSettingsBean bean = new SiteSettingsBean(form.isUpgradeInProgress());
            setHelpTopic("configAdmin");
            return new JspView<>("/org/labkey/core/admin/customizeSite.jsp", bean, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addAdminNavTrail(root, "Customize Site", this.getClass());
        }

        @Override
        public void validateCommand(SiteSettingsForm form, Errors errors)
        {
            if (form.isShowRibbonMessage() && StringUtils.isEmpty(form.getRibbonMessage()))
            {
                errors.reject(ERROR_MSG, "Cannot enable the ribbon message without providing a message to show");
            }
            if (form.getMaxBLOBSize() < 0)
            {
                errors.reject(ERROR_MSG, "Maximum BLOB size cannot be negative");
            }
            int hardCap = Math.max(WriteableAppProps.SOFT_MAX_BLOB_SIZE, AppProps.getInstance().getMaxBLOBSize());
            if (form.getMaxBLOBSize() > hardCap)
            {
                errors.reject(ERROR_MSG, "Maximum BLOB size cannot be set higher than " + hardCap + " bytes");
            }
            if (form.getSslPort() < 1 || form.getSslPort() > 65535)
            {
                errors.reject(ERROR_MSG, "HTTPS port must be between 1 and 65,535");
            }
            if (form.getReadOnlyHttpRequestTimeout() < 0)
            {
                errors.reject(ERROR_MSG, "HTTP timeout must be non-negative");
            }
            if (form.getMemoryUsageDumpInterval() < 0)
            {
                errors.reject(ERROR_MSG, "Memory logging frequency must be non-negative");
            }
            if (form.getScriptExecutionTimeout() < 0)
            {
                errors.reject(ERROR_MSG, "Script execution timeout must be non-negative");
            }
        }

        @Override
        public boolean handlePost(SiteSettingsForm form, BindException errors) throws Exception
        {
            HttpServletRequest request = getViewContext().getRequest();

            // We only need to check that SSL is running if the user isn't already using SSL
            if (form.isSslRequired() && !(request.isSecure() && (form.getSslPort() == request.getServerPort())))
            {
                URL testURL = new URL("https", request.getServerName(), form.getSslPort(), AppProps.getInstance().getContextPath());
                Pair<String, Integer> sslResponse = HttpsUtil.testHttpsUrl(testURL, "Ensure that the web server is configured for SSL and the port is correct. If SSL is enabled, try saving these settings while connected via SSL.");

                if (sslResponse != null)
                {
                    errors.reject(ERROR_MSG, sslResponse.first);
                    return false;
                }
            }

            if (form.getReadOnlyHttpRequestTimeout() < 0)
            {
                errors.reject(ERROR_MSG, "Read only HTTP request timeout must be non-negative");
            }

            WriteableAppProps props = AppProps.getWriteableInstance();

            props.setPipelineToolsDir(form.getPipelineToolsDirectory());
            props.setNavAccessOpen(form.isNavAccessOpen());
            props.setSSLRequired(form.isSslRequired());
            boolean sslSettingChanged = AppProps.getInstance().isSSLRequired() != form.isSslRequired();
            props.setSSLPort(form.getSslPort());
            props.setMemoryUsageDumpInterval(form.getMemoryUsageDumpInterval());
            props.setReadOnlyHttpRequestTimeout(form.getReadOnlyHttpRequestTimeout());
            props.setScriptExecutionTimeout(form.getScriptExecutionTimeout());
            props.setMaxBLOBSize(form.getMaxBLOBSize());
            props.setSelfReportExceptions(form.isSelfReportExceptions());

            props.setAdminOnlyMessage(form.getAdminOnlyMessage());
            props.setShowRibbonMessage(form.isShowRibbonMessage());
            props.setRibbonMessage(form.getRibbonMessage());
            props.setUserRequestedAdminOnlyMode(form.isAdminOnlyMode());

            props.setAllowApiKeys(form.isAllowApiKeys());
            props.setApiKeyExpirationSeconds(form.getApiKeyExpirationSeconds());
            props.setAllowSessionKeys(form.isAllowSessionKeys());

            try
            {
                ExceptionReportingLevel level = ExceptionReportingLevel.valueOf(form.getExceptionReportingLevel());
                props.setExceptionReportingLevel(level);
            }
            catch (IllegalArgumentException ignored)
            {
            }

            try
            {
                if (form.getUsageReportingLevel() != null)
                {
                    UsageReportingLevel level = UsageReportingLevel.valueOf(form.getUsageReportingLevel());
                    props.setUsageReportingLevel(level);
                }
            }
            catch (IllegalArgumentException ignored)
            {
            }

            props.setAdministratorContactEmail(form.getAdministratorContactEmail() == null ? null : form.getAdministratorContactEmail().trim());

            if (null != form.getBaseServerURL())
            {
                if (form.isSslRequired() && !form.getBaseServerURL().startsWith("https"))
                {
                    errors.reject(ERROR_MSG, "Invalid Base Server URL. SSL connection is required. Consider https://.");
                    return false;
                }

                try
                {
                    props.setBaseServerUrl(form.getBaseServerURL());
                }
                catch (URISyntaxException e)
                {
                    errors.reject(ERROR_MSG, "Invalid Base Server URL, \"" + e.getMessage() + "\"." +
                            "Please enter a valid base URL containing the protocol, hostname, and port if required. " +
                            "The webapp context path should not be included. " +
                            "For example: \"https://www.example.com\" or \"http://www.labkey.org:8080\" and not \"http://www.example.com/labkey/\"");
                    return false;
                }
            }

            props.setIncludeServerHttpHeader(form.isIncludeServerHttpHeader());

            props.setTermsOfUseFrequencySeconds(form.getTermsOfUseFrequencySeconds());

            props.save(getViewContext().getUser());
            UsageReportingLevel.reportNow();
            if (sslSettingChanged)
                ContentSecurityPolicyFilter.regenerateSubstitutionMap();

            return true;
        }

        @Override
        public ActionURL getSuccessURL(SiteSettingsForm form)
        {
            if (form.isUpgradeInProgress())
            {
                return AppProps.getInstance().getHomePageActionURL();
            }
            else
            {
                return new AdminUrlsImpl().getAdminConsoleURL();
            }
        }
    }

    // Note: records don't work with JSON binding yet
    public static class TermsFrequency
    {
        public int getSeconds()
        {
            return _seconds;
        }

        public void setSeconds(int seconds)
        {
            _seconds = seconds;
        }

        private int _seconds;
    }

    // For SiteWideTermsOfUseTest - must be invoked in root, as with CustomizeSiteAction
    @AdminConsoleAction(AdminOperationsPermission.class)
    public static class SetTermsOfUseFrequencyAction extends MutatingApiAction<TermsFrequency>
    {
        @Override
        public Object execute(TermsFrequency tf, BindException errors) throws Exception
        {
            WriteableAppProps props = AppProps.getWriteableInstance();
            props.setTermsOfUseFrequencySeconds(tf.getSeconds());
            props.save(getUser());

            return null;
        }
    }

    public static class NetworkDriveForm
    {
        private String _networkDriveLetter;
        private String _networkDrivePath;
        private String _networkDriveUser;
        private String _networkDrivePassword;

        public String getNetworkDriveLetter()
        {
            return _networkDriveLetter;
        }

        public void setNetworkDriveLetter(String networkDriveLetter)
        {
            _networkDriveLetter = networkDriveLetter;
        }

        public String getNetworkDrivePassword()
        {
            return _networkDrivePassword;
        }

        public void setNetworkDrivePassword(String networkDrivePassword)
        {
            _networkDrivePassword = networkDrivePassword;
        }

        public String getNetworkDrivePath()
        {
            return _networkDrivePath;
        }

        public void setNetworkDrivePath(String networkDrivePath)
        {
            _networkDrivePath = networkDrivePath;
        }

        public String getNetworkDriveUser()
        {
            return _networkDriveUser;
        }

        public void setNetworkDriveUser(String networkDriveUser)
        {
            _networkDriveUser = networkDriveUser;
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    @AdminConsoleAction
    public class MapNetworkDriveAction extends FormViewAction<NetworkDriveForm>
    {
        @Override
        public void validateCommand(NetworkDriveForm form, Errors errors)
        {
            validateNetworkDrive(form, errors);
        }

        @Override
        public ModelAndView getView(NetworkDriveForm form, boolean reshow, BindException errors)
        {
            return new JspView<>("/org/labkey/core/admin/mapNetworkDrive.jsp", null, errors);
        }

        @Override
        public boolean handlePost(NetworkDriveForm form, BindException errors) throws Exception
        {
            NetworkDriveProps.setNetworkDriveLetter(form.getNetworkDriveLetter().trim());
            NetworkDriveProps.setNetworkDrivePath(form.getNetworkDrivePath().trim());
            NetworkDriveProps.setNetworkDriveUser(form.getNetworkDriveUser().trim());
            NetworkDriveProps.setNetworkDrivePassword(form.getNetworkDrivePassword().trim());

            return true;
        }

        @Override
        public URLHelper getSuccessURL(NetworkDriveForm siteSettingsForm)
        {
            return new ActionURL(FilesSiteSettingsAction.class, getContainer());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("setRoots#map");
            addAdminNavTrail(root, "Map Network Drive", this.getClass());
        }
    }

    public static class SiteSettingsBean
    {
        public final boolean _upgradeInProgress;
        public final boolean _showSelfReportExceptions;

        private SiteSettingsBean(boolean upgradeInProgress)
        {
            _upgradeInProgress = upgradeInProgress;
            _showSelfReportExceptions = MothershipReport.isShowSelfReportExceptions();
        }

        public HtmlString getSiteSettingsHelpLink(String fragment)
        {
            return new HelpTopic("configAdmin", fragment).getSimpleLinkHtml("more info...");
        }
    }

    public static class SetRibbonMessageForm
    {
        private Boolean _show = null;
        private String _message = null;

        public Boolean isShow()
        {
            return _show;
        }

        public void setShow(Boolean show)
        {
            _show = show;
        }

        public String getMessage()
        {
            return _message;
        }

        public void setMessage(String message)
        {
            _message = message;
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public static class SetRibbonMessageAction extends MutatingApiAction<SetRibbonMessageForm>
    {
        @Override
        public Object execute(SetRibbonMessageForm form, BindException errors) throws Exception
        {
            if (form.isShow() != null || form.getMessage() != null)
            {
                WriteableAppProps props = AppProps.getWriteableInstance();

                if (form.isShow() != null)
                    props.setShowRibbonMessage(form.isShow());

                if (form.getMessage() != null)
                    props.setRibbonMessage(form.getMessage());

                props.save(getViewContext().getUser());
            }

            return null;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ConfigureSiteValidationAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/core/admin/sitevalidation/configureSiteValidation.jsp");
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("siteValidation");
            addAdminNavTrail(root, "Configure " + (getContainer().isRoot() ? "Site" : "Folder") + " Validation", getClass());
        }
    }

    public static class SiteValidationForm
    {
        private List<String> _providers;
        private boolean _includeSubfolders = false;
        private transient Consumer<String> _logger = _ -> {
        }; // No-op by default

        public List<String> getProviders()
        {
            return _providers;
        }

        public void setProviders(List<String> providers)
        {
            _providers = providers;
        }

        public boolean isIncludeSubfolders()
        {
            return _includeSubfolders;
        }

        public void setIncludeSubfolders(boolean includeSubfolders)
        {
            _includeSubfolders = includeSubfolders;
        }

        public Consumer<String> getLogger()
        {
            return _logger;
        }

        public void setLogger(Consumer<String> logger)
        {
            _logger = logger;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class SiteValidationAction extends SimpleViewAction<SiteValidationForm>
    {
        @Override
        public ModelAndView getView(SiteValidationForm form, BindException errors)
        {
            return new JspView<>("/org/labkey/core/admin/sitevalidation/siteValidation.jsp", form);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("siteValidation");
            addAdminNavTrail(root, (getContainer().isRoot() ? "Site" : "Folder") + " Validation", getClass());
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class SiteValidationBackgroundAction extends FormHandlerAction<SiteValidationForm>
    {
        private ActionURL _redirectUrl;

        @Override
        public void validateCommand(SiteValidationForm form, Errors errors)
        {
        }

        @Override
        public boolean handlePost(SiteValidationForm form, BindException errors) throws PipelineValidationException
        {
            ViewBackgroundInfo vbi = new ViewBackgroundInfo(getContainer(), getUser(), null);
            PipeRoot root = PipelineService.get().findPipelineRoot(getContainer());
            SiteValidationJob job = new SiteValidationJob(vbi, root, form);
            PipelineService.get().queueJob(job);
            String jobGuid = job.getJobGUID();

            if (null == jobGuid)
                throw new NotFoundException("Unable to determine pipeline job GUID");

            Long jobId = PipelineService.get().getJobId(getUser(), getContainer(), jobGuid);

            if (null == jobId)
                throw new NotFoundException("Unable to determine pipeline job ID");

            PipelineStatusUrls urls = urlProvider(PipelineStatusUrls.class);
            _redirectUrl = urls.urlDetails(getContainer(), jobId);

            return true;
        }

        @Override
        public URLHelper getSuccessURL(SiteValidationForm form)
        {
            return _redirectUrl;
        }
    }

    public static class ViewValidationResultsForm
    {
        private int _rowId;

        public int getRowId()
        {
            return _rowId;
        }

        public void setRowId(int rowId)
        {
            _rowId = rowId;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ViewValidationResultsAction extends SimpleViewAction<ViewValidationResultsForm>
    {
        @Override
        public ModelAndView getView(ViewValidationResultsForm form, BindException errors) throws Exception
        {
            PipelineStatusFile statusFile = PipelineService.get().getStatusFile(form.getRowId());
            if (null == statusFile)
                throw new NotFoundException("Status file not found");
            if (!getContainer().equals(statusFile.lookupContainer()))
                throw new UnauthorizedException("Wrong container");

            String logFilePath = statusFile.getFilePath();
            String htmlFilePath = FileUtil.getBaseName(logFilePath) + ".html";
            File htmlFile = new File(htmlFilePath);

            if (!htmlFile.exists())
                throw new NotFoundException("Results file not found");
            return new HtmlView(HtmlString.unsafe(PageFlowUtil.getFileContentsAsString(htmlFile)));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("siteValidation");
            addAdminNavTrail(root, "View " + (getContainer().isRoot() ? "Site" : "Folder") + " Validation Results", getClass());
        }
    }

    public interface FileManagementForm
    {
        String getFolderRootPath();

        void setFolderRootPath(String folderRootPath);

        String getFileRootOption();

        void setFileRootOption(String fileRootOption);

        String getConfirmMessage();

        void setConfirmMessage(String confirmMessage);

        boolean isDisableFileSharing();

        boolean hasSiteDefaultRoot();

        String[] getEnabledCloudStore();

        @SuppressWarnings("unused")
        void setEnabledCloudStore(String[] enabledCloudStore);

        boolean isCloudFileRoot();

        @Nullable
        String getCloudRootName();

        void setCloudRootName(String cloudRootName);

        void setFileRootChanged(boolean changed);

        void setEnabledCloudStoresChanged(boolean changed);

        String getMigrateFilesOption();

        void setMigrateFilesOption(String migrateFilesOption);

        default boolean isFolderSetup()
        {
            return false;
        }
    }

    public enum MigrateFilesOption implements SafeToRenderEnum
    {
        leave
                {
                    @Override
                    public String description()
                    {
                        return "Source files not copied or moved";
                    }
                },
        copy
                {
                    @Override
                    public String description()
                    {
                        return "Copy source files to destination";
                    }
                },
        move
                {
                    @Override
                    public String description()
                    {
                        return "Move source files to destination";
                    }
                };

        public abstract String description();
    }

    public static class ProjectSettingsForm extends FolderSettingsForm
    {
        // Site-only properties
        private String _dateParsingMode;
        private String _customWelcome;

        // Site & project properties
        private boolean _shouldInherit; // new subfolders should inherit parent permissions
        private String _systemDescription;
        private boolean _systemDescriptionInherited;
        private String _systemShortName;
        private boolean _systemShortNameInherited;
        private String _themeName;
        private boolean _themeNameInherited;
        private String _folderDisplayMode;
        private boolean _folderDisplayModeInherited;
        private String _applicationMenuDisplayMode;
        private boolean _applicationMenuDisplayModeInherited;
        private boolean _helpMenuEnabled;
        private boolean _helpMenuEnabledInherited;
        private String _logoHref;
        private boolean _logoHrefInherited;
        private String _companyName;
        private boolean _companyNameInherited;
        private String _systemEmailAddress;
        private boolean _systemEmailAddressInherited;
        private String _reportAProblemPath;
        private boolean _reportAProblemPathInherited;
        private String _supportEmail;
        private boolean _supportEmailInherited;
        private String _customLogin;
        private boolean _customLoginInherited;

        // Site-only properties

        public String getDateParsingMode()
        {
            return _dateParsingMode;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setDateParsingMode(String dateParsingMode)
        {
            _dateParsingMode = dateParsingMode;
        }

        public String getCustomWelcome()
        {
            return _customWelcome;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setCustomWelcome(String customWelcome)
        {
            _customWelcome = customWelcome;
        }

        // Site & project properties

        public boolean getShouldInherit()
        {
            return _shouldInherit;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setShouldInherit(boolean b)
        {
            _shouldInherit = b;
        }

        public String getSystemDescription()
        {
            return _systemDescription;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setSystemDescription(String systemDescription)
        {
            _systemDescription = systemDescription;
        }

        public boolean isSystemDescriptionInherited()
        {
            return _systemDescriptionInherited;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setSystemDescriptionInherited(boolean systemDescriptionInherited)
        {
            _systemDescriptionInherited = systemDescriptionInherited;
        }

        public String getSystemShortName()
        {
            return _systemShortName;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setSystemShortName(String systemShortName)
        {
            _systemShortName = systemShortName;
        }

        public boolean isSystemShortNameInherited()
        {
            return _systemShortNameInherited;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setSystemShortNameInherited(boolean systemShortNameInherited)
        {
            _systemShortNameInherited = systemShortNameInherited;
        }

        public String getThemeName()
        {
            return _themeName;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setThemeName(String themeName)
        {
            _themeName = themeName;
        }

        public boolean isThemeNameInherited()
        {
            return _themeNameInherited;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setThemeNameInherited(boolean themeNameInherited)
        {
            _themeNameInherited = themeNameInherited;
        }

        public String getFolderDisplayMode()
        {
            return _folderDisplayMode;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setFolderDisplayMode(String folderDisplayMode)
        {
            _folderDisplayMode = folderDisplayMode;
        }

        public boolean isFolderDisplayModeInherited()
        {
            return _folderDisplayModeInherited;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setFolderDisplayModeInherited(boolean folderDisplayModeInherited)
        {
            _folderDisplayModeInherited = folderDisplayModeInherited;
        }

        public String getApplicationMenuDisplayMode()
        {
            return _applicationMenuDisplayMode;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setApplicationMenuDisplayMode(String displayMode)
        {
            _applicationMenuDisplayMode = displayMode;
        }

        public boolean isApplicationMenuDisplayModeInherited()
        {
            return _applicationMenuDisplayModeInherited;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setApplicationMenuDisplayModeInherited(boolean applicationMenuDisplayModeInherited)
        {
            _applicationMenuDisplayModeInherited = applicationMenuDisplayModeInherited;
        }

        public boolean isHelpMenuEnabled()
        {
            return _helpMenuEnabled;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setHelpMenuEnabled(boolean helpMenuEnabled)
        {
            _helpMenuEnabled = helpMenuEnabled;
        }

        public boolean isHelpMenuEnabledInherited()
        {
            return _helpMenuEnabledInherited;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setHelpMenuEnabledInherited(boolean helpMenuEnabledInherited)
        {
            _helpMenuEnabledInherited = helpMenuEnabledInherited;
        }

        public String getLogoHref()
        {
            return _logoHref;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setLogoHref(String logoHref)
        {
            _logoHref = logoHref;
        }

        public boolean isLogoHrefInherited()
        {
            return _logoHrefInherited;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setLogoHrefInherited(boolean logoHrefInherited)
        {
            _logoHrefInherited = logoHrefInherited;
        }

        public String getReportAProblemPath()
        {
            return _reportAProblemPath;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setReportAProblemPath(String reportAProblemPath)
        {
            _reportAProblemPath = reportAProblemPath;
        }

        public boolean isReportAProblemPathInherited()
        {
            return _reportAProblemPathInherited;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setReportAProblemPathInherited(boolean reportAProblemPathInherited)
        {
            _reportAProblemPathInherited = reportAProblemPathInherited;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setSupportEmail(String supportEmail)
        {
            _supportEmail = supportEmail;
        }

        public String getSupportEmail()
        {
            return _supportEmail;
        }

        public boolean isSupportEmailInherited()
        {
            return _supportEmailInherited;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setSupportEmailInherited(boolean supportEmailInherited)
        {
            _supportEmailInherited = supportEmailInherited;
        }

        public String getSystemEmailAddress()
        {
            return _systemEmailAddress;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setSystemEmailAddress(String systemEmailAddress)
        {
            _systemEmailAddress = systemEmailAddress;
        }

        public boolean isSystemEmailAddressInherited()
        {
            return _systemEmailAddressInherited;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setSystemEmailAddressInherited(boolean systemEmailAddressInherited)
        {
            _systemEmailAddressInherited = systemEmailAddressInherited;
        }

        public String getCompanyName()
        {
            return _companyName;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setCompanyName(String companyName)
        {
            _companyName = companyName;
        }

        public boolean isCompanyNameInherited()
        {
            return _companyNameInherited;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setCompanyNameInherited(boolean companyNameInherited)
        {
            _companyNameInherited = companyNameInherited;
        }

        public String getCustomLogin()
        {
            return _customLogin;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setCustomLogin(String customLogin)
        {
            _customLogin = customLogin;
        }

        public boolean isCustomLoginInherited()
        {
            return _customLoginInherited;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setCustomLoginInherited(boolean customLoginInherited)
        {
            _customLoginInherited = customLoginInherited;
        }
    }

    public enum FileRootProp implements SafeToRenderEnum
    {
        disable,
        siteDefault,
        folderOverride,
        cloudRoot
    }

    public static class FilesForm extends SetupForm implements FileManagementForm
    {
        private boolean _fileRootChanged;
        private boolean _enabledCloudStoresChanged;
        private String _cloudRootName;
        private String _migrateFilesOption;
        private String[] _enabledCloudStore;
        private String _fileRootOption;
        private String _folderRootPath;

        public boolean isFileRootChanged()
        {
            return _fileRootChanged;
        }

        @Override
        public void setFileRootChanged(boolean changed)
        {
            _fileRootChanged = changed;
        }

        public boolean isEnabledCloudStoresChanged()
        {
            return _enabledCloudStoresChanged;
        }

        @Override
        public void setEnabledCloudStoresChanged(boolean enabledCloudStoresChanged)
        {
            _enabledCloudStoresChanged = enabledCloudStoresChanged;
        }

        @Override
        public boolean isDisableFileSharing()
        {
            return FileRootProp.disable.name().equals(getFileRootOption());
        }

        @Override
        public boolean hasSiteDefaultRoot()
        {
            return FileRootProp.siteDefault.name().equals(getFileRootOption());
        }

        @Override
        public String[] getEnabledCloudStore()
        {
            return _enabledCloudStore;
        }

        @Override
        public void setEnabledCloudStore(String[] enabledCloudStore)
        {
            _enabledCloudStore = enabledCloudStore;
        }

        @Override
        public boolean isCloudFileRoot()
        {
            return FileRootProp.cloudRoot.name().equals(getFileRootOption());
        }

        @Override
        @Nullable
        public String getCloudRootName()
        {
            return _cloudRootName;
        }

        @Override
        public void setCloudRootName(String cloudRootName)
        {
            _cloudRootName = cloudRootName;
        }

        @Override
        public String getMigrateFilesOption()
        {
            return _migrateFilesOption;
        }

        @Override
        public void setMigrateFilesOption(String migrateFilesOption)
        {
            _migrateFilesOption = migrateFilesOption;
        }

        @Override
        public String getFolderRootPath()
        {
            return _folderRootPath;
        }

        @Override
        public void setFolderRootPath(String folderRootPath)
        {
            _folderRootPath = folderRootPath;
        }

        @Override
        public String getFileRootOption()
        {
            return _fileRootOption;
        }

        @Override
        public void setFileRootOption(String fileRootOption)
        {
            _fileRootOption = fileRootOption;
        }
    }

    @SuppressWarnings("unused")
    public static class SiteSettingsForm
    {
        private boolean _upgradeInProgress = false;

        private String _pipelineToolsDirectory;
        private boolean _sslRequired;
        private boolean _adminOnlyMode;
        private boolean _showRibbonMessage;
        private boolean _selfReportExceptions;
        private String _adminOnlyMessage;
        private String _ribbonMessage;
        private int _sslPort;
        private int _memoryUsageDumpInterval;
        private int _readOnlyHttpRequestTimeout;
        private int _scriptExecutionTimeout;
        private int _maxBLOBSize;
        private String _exceptionReportingLevel;
        private String _usageReportingLevel;
        private String _administratorContactEmail;

        private String _baseServerURL;
        private String _callbackPassword;
        private boolean _allowApiKeys;
        private int _apiKeyExpirationSeconds;
        private boolean _allowSessionKeys;
        private boolean _navAccessOpen;

        private boolean _includeServerHttpHeader;
        private int _termsOfUseFrequencySeconds;

        public String getPipelineToolsDirectory()
        {
            return _pipelineToolsDirectory;
        }

        public void setPipelineToolsDirectory(String pipelineToolsDirectory)
        {
            _pipelineToolsDirectory = pipelineToolsDirectory;
        }

        public boolean isNavAccessOpen()
        {
            return _navAccessOpen;
        }

        public void setNavAccessOpen(boolean navAccessOpen)
        {
            _navAccessOpen = navAccessOpen;
        }

        public boolean isSslRequired()
        {
            return _sslRequired;
        }

        public void setSslRequired(boolean sslRequired)
        {
            _sslRequired = sslRequired;
        }

        public int getSslPort()
        {
            return _sslPort;
        }

        public void setSslPort(int sslPort)
        {
            _sslPort = sslPort;
        }

        public boolean isAdminOnlyMode()
        {
            return _adminOnlyMode;
        }

        public void setAdminOnlyMode(boolean adminOnlyMode)
        {
            _adminOnlyMode = adminOnlyMode;
        }

        public String getAdminOnlyMessage()
        {
            return _adminOnlyMessage;
        }

        public void setAdminOnlyMessage(String adminOnlyMessage)
        {
            _adminOnlyMessage = adminOnlyMessage;
        }

        public boolean isSelfReportExceptions()
        {
            return _selfReportExceptions;
        }

        public void setSelfReportExceptions(boolean selfReportExceptions)
        {
            _selfReportExceptions = selfReportExceptions;
        }

        public String getExceptionReportingLevel()
        {
            return _exceptionReportingLevel;
        }

        public void setExceptionReportingLevel(String exceptionReportingLevel)
        {
            _exceptionReportingLevel = exceptionReportingLevel;
        }

        public String getUsageReportingLevel()
        {
            return _usageReportingLevel;
        }

        public void setUsageReportingLevel(String usageReportingLevel)
        {
            _usageReportingLevel = usageReportingLevel;
        }

        public String getAdministratorContactEmail()
        {
            return _administratorContactEmail;
        }

        public void setAdministratorContactEmail(String administratorContactEmail)
        {
            _administratorContactEmail = administratorContactEmail;
        }

        public boolean isUpgradeInProgress()
        {
            return _upgradeInProgress;
        }

        public void setUpgradeInProgress(boolean upgradeInProgress)
        {
            _upgradeInProgress = upgradeInProgress;
        }

        public int getMemoryUsageDumpInterval()
        {
            return _memoryUsageDumpInterval;
        }

        public void setMemoryUsageDumpInterval(int memoryUsageDumpInterval)
        {
            _memoryUsageDumpInterval = memoryUsageDumpInterval;
        }

        public int getReadOnlyHttpRequestTimeout()
        {
            return _readOnlyHttpRequestTimeout;
        }

        public int getScriptExecutionTimeout()
        {
            return _scriptExecutionTimeout;
        }

        public void setScriptExecutionTimeout(int timeout)
        {
            _scriptExecutionTimeout = timeout;
        }

        public void setReadOnlyHttpRequestTimeout(int timeout)
        {
            _readOnlyHttpRequestTimeout = timeout;
        }

        public int getMaxBLOBSize()
        {
            return _maxBLOBSize;
        }

        public void setMaxBLOBSize(int maxBLOBSize)
        {
            _maxBLOBSize = maxBLOBSize;
        }

        public String getBaseServerURL()
        {
            return _baseServerURL;
        }

        public void setBaseServerURL(String baseServerURL)
        {
            _baseServerURL = baseServerURL;
        }

        public String getCallbackPassword()
        {
            return _callbackPassword;
        }

        public void setCallbackPassword(String callbackPassword)
        {
            _callbackPassword = callbackPassword;
        }

        public boolean isShowRibbonMessage()
        {
            return _showRibbonMessage;
        }

        public void setShowRibbonMessage(boolean showRibbonMessage)
        {
            _showRibbonMessage = showRibbonMessage;
        }

        public String getRibbonMessage()
        {
            return _ribbonMessage;
        }

        public void setRibbonMessage(String ribbonMessage)
        {
            _ribbonMessage = ribbonMessage;
        }

        public boolean isAllowApiKeys()
        {
            return _allowApiKeys;
        }

        public void setAllowApiKeys(boolean allowApiKeys)
        {
            _allowApiKeys = allowApiKeys;
        }

        public int getApiKeyExpirationSeconds()
        {
            return _apiKeyExpirationSeconds;
        }

        public void setApiKeyExpirationSeconds(int apiKeyExpirationSeconds)
        {
            _apiKeyExpirationSeconds = apiKeyExpirationSeconds;
        }

        public boolean isAllowSessionKeys()
        {
            return _allowSessionKeys;
        }

        public void setAllowSessionKeys(boolean allowSessionKeys)
        {
            _allowSessionKeys = allowSessionKeys;
        }

        public boolean isIncludeServerHttpHeader()
        {
            return _includeServerHttpHeader;
        }

        public void setIncludeServerHttpHeader(boolean includeServerHttpHeader)
        {
            _includeServerHttpHeader = includeServerHttpHeader;
        }

        public int getTermsOfUseFrequencySeconds()
        {
            return _termsOfUseFrequencySeconds;
        }

        public void setTermsOfUseFrequencySeconds(int termsOfUseFrequencySeconds)
        {
            _termsOfUseFrequencySeconds = termsOfUseFrequencySeconds;
        }
    }


    @AdminConsoleAction
    public class ShowThreadsAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            // Log to labkey.log as well as showing through the browser
            DebugInfoDumper.dumpThreads(3);
            return new JspView<>("/org/labkey/core/admin/threads.jsp", new ThreadsBean());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("dumpDebugging#threads");
            addAdminNavTrail(root, "Current Threads", this.getClass());
        }
    }

    private abstract class AbstractPostgresAction extends AbstractAdminQueryAction
    {
        protected AbstractPostgresAction(String queryName)
        {
            super("query", queryName);
        }

        @Override
        protected UserSchema getUserSchema()
        {
            return new PostgresUserSchema(getUser(), getContainer());
        }

        @Override
        protected QueryView createQueryView(QueryExportForm form, BindException errors, boolean forExport, @Nullable String dataRegion) throws Exception
        {
            if (!getContainer().isRoot())
            {
                throw new NotFoundException("Available only in the root container");
            }

            if (!CoreSchema.getInstance().getSqlDialect().isPostgreSQL())
            {
                throw new NotFoundException("Available only with Postgres as the primary database");
            }

            return super.createQueryView(form, errors, forExport, dataRegion);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("postgresActivity");
            addAdminNavTrail(root, "Postgres " + getQueryName(), this.getClass());
        }
    }

    // This allows Troubleshooters to GET and POST to the action, supporting export to Excel and script, e.g.
    @RequiresPermission(TroubleshooterPermission.class)
    public class PostgresStatActivityAction extends AbstractPostgresAction
    {
        public PostgresStatActivityAction()
        {
            super(BasePostgreSqlDialect.POSTGRES_STAT_ACTIVITY_TABLE_NAME);
        }
    }

    @RequiresPermission(TroubleshooterPermission.class)
    public class PostgresLocksAction extends AbstractPostgresAction
    {
        public PostgresLocksAction()
        {
            super(BasePostgreSqlDialect.POSTGRES_LOCKS_TABLE_NAME);
        }
    }

    @RequiresPermission(TroubleshooterPermission.class)
    public class PostgresTableSizesAction extends AbstractPostgresAction
    {
        public PostgresTableSizesAction()
        {
            super(BasePostgreSqlDialect.POSTGRES_TABLE_SIZES_TABLE_NAME);
        }
    }

    @AdminConsoleAction
    public class DumpHeapAction extends ConfirmAction<Object>
    {
        private File _destination;

        @Override
        public ModelAndView getConfirmView(Object o, BindException errors)
        {
            setTitle("Dump Heap");
            return HtmlView.of("Are you sure you want to dump the JVM heap to disk? Heap dumps are useful for troubleshooting memory leaks or OutOfMemoryErrors. This may temporarily slow the server and will consume significant disk space.");
        }

        @Override
        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            _destination = DebugInfoDumper.dumpHeap();
            return true;
        }

        @Override
        public ModelAndView getSuccessView(Object o)
        {
            return new HtmlView(HtmlString.of("Heap dumped to " + _destination.getAbsolutePath()));
        }

        @Override
        public void validateCommand(Object o, Errors errors)
        {
        }

        @Override
        public @NotNull URLHelper getSuccessURL(Object o)
        {
            return getShowAdminURL();
        }
    }

    public static class ThreadsBean
    {
        public Map<Thread, Set<Integer>> spids;
        public List<Thread> threads;
        public Map<Thread, StackTraceElement[]> stackTraces;

        ThreadsBean()
        {
            stackTraces =  Thread.getAllStackTraces();
            threads = new ArrayList<>(stackTraces.keySet());
            threads.sort(Comparator.comparing(Thread::getName, String.CASE_INSENSITIVE_ORDER));

            spids = new HashMap<>();

            for (Thread t : threads)
            {
                spids.put(t, ConnectionWrapper.getSPIDsForThread(t));
            }
        }
    }


    @RequiresPermission(AdminOperationsPermission.class)
    public class ShowNetworkDriveTestAction extends SimpleViewAction<NetworkDriveForm>
    {
        @Override
        public void validate(NetworkDriveForm form, BindException errors)
        {
            validateNetworkDrive(form, errors);
        }

        @Override
        public ModelAndView getView(NetworkDriveForm form, BindException errors)
        {
            NetworkDrive testDrive = new NetworkDrive();
            testDrive.setPassword(form.getNetworkDrivePassword());
            testDrive.setPath(form.getNetworkDrivePath());
            testDrive.setUser(form.getNetworkDriveUser());
            TestNetworkDriveBean bean = new TestNetworkDriveBean();

            if (!errors.hasErrors())
            {
                char driveLetter = form.getNetworkDriveLetter().trim().charAt(0);
                try
                {
                    String mountError = testDrive.mount(driveLetter);
                    if (mountError != null)
                    {
                        errors.reject(ERROR_MSG, mountError);
                    }
                    else
                    {
                        File f = new File(driveLetter + ":\\");
                        if (!f.exists())
                        {
                            errors.reject(ERROR_MSG, "Could not access network drive");
                        }
                        else
                        {
                            String[] fileNames = f.list();
                            if (fileNames == null)
                                fileNames = new String[0];
                            Arrays.sort(fileNames);
                            bean.setFiles(fileNames);
                        }
                    }
                }
                catch (IOException | InterruptedException e)
                {
                    errors.reject(ERROR_MSG, "Error mounting drive: " + e);
                }
                try
                {
                    testDrive.unmount(driveLetter);
                }
                catch (IOException | InterruptedException e)
                {
                    errors.reject(ERROR_MSG, "Error mounting drive: " + e);
                }
            }

            getPageConfig().setTemplate(Template.Dialog);
            return new JspView<>("/org/labkey/core/admin/testNetworkDrive.jsp", bean, errors);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Test Mapping Network Drive");
        }
    }


    @AdminConsoleAction(ApplicationAdminPermission.class)
    public class ResetErrorMarkAction extends ConfirmAction<Object>
    {
        @Override
        public ModelAndView getConfirmView(Object o, BindException errors)
        {
            return HtmlView.of("Are you sure you want to reset the site errors?");
        }

        @Override
        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            File errorLogFile = getErrorLogFile();
            _errorMark = errorLogFile.length();

            return true;
        }

        @Override
        public void validateCommand(Object o, Errors errors)
        {
        }

        @Override
        public @NotNull URLHelper getSuccessURL(Object o)
        {
            return getShowAdminURL();
        }
    }

    abstract public static class ShowLogAction extends ExportAction<Object>
    {
        @Override
        public final void export(Object o, HttpServletResponse response, BindException errors) throws IOException
        {
            getPageConfig().setNoIndex();
            export(response);
        }

        protected abstract void export(HttpServletResponse response) throws IOException;
    }

    @AdminConsoleAction
    public class ShowErrorsSinceMarkAction extends ShowLogAction
    {
        @Override
        protected void export(HttpServletResponse response) throws IOException
        {
            PageFlowUtil.streamLogFile(response, _errorMark, getErrorLogFile());
        }
    }

    @AdminConsoleAction
    public class ShowAllErrorsAction extends ShowLogAction
    {
        @Override
        protected void export(HttpServletResponse response) throws IOException
        {
            PageFlowUtil.streamLogFile(response, 0, getErrorLogFile());
        }
    }

    @AdminConsoleAction(ApplicationAdminPermission.class)
    public class ResetPrimaryLogMarkAction extends MutatingApiAction<Object>
    {
        @Override
        public Object execute(Object o, BindException errors) throws Exception
        {
            File logFile = getPrimaryLogFile();
            _primaryLogMark = logFile.length();
            return null;
        }
    }

    @AdminConsoleAction
    public class ShowPrimaryLogSinceMarkAction extends ShowLogAction
    {
        @Override
        protected void export(HttpServletResponse response) throws IOException
        {
            PageFlowUtil.streamLogFile(response, _primaryLogMark, getPrimaryLogFile());
        }
    }

    @AdminConsoleAction
    public class ShowPrimaryLogAction extends ShowLogAction
    {
        @Override
        protected void export(HttpServletResponse response) throws IOException
        {
            PageFlowUtil.streamLogFile(response, 0, getPrimaryLogFile());
        }
    }

    @AdminConsoleAction
    public class ShowCspReportLogAction extends ShowLogAction
    {
        @Override
        protected void export(HttpServletResponse response) throws IOException
        {
            PageFlowUtil.streamLogFile(response, 0, getCspReportLogFile());
        }
    }

    private File getErrorLogFile()
    {
        return new File(getLabKeyLogDir(), "labkey-errors.log");
    }

    private File getPrimaryLogFile()
    {
        return new File(getLabKeyLogDir(), "labkey.log");
    }

    private File getCspReportLogFile()
    {
        return new File(getLabKeyLogDir(), "csp-report.log");
    }

    private static ActionURL getActionsURL()
    {
        return new ActionURL(ActionsAction.class, ContainerManager.getRoot());
    }


    @AdminConsoleAction
    public class ActionsAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return new ActionsTabStrip();
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("actionsDiagnostics");
            addAdminNavTrail(root, "Actions", this.getClass());
        }
    }

    private static class ActionsTabStrip extends TabStripView
    {
        @Override
        public List<NavTree> getTabList()
        {
            List<NavTree> tabs = new ArrayList<>(3);

            tabs.add(new TabInfo("Summary", "summary", getActionsURL()));
            tabs.add(new TabInfo("Details", "details", getActionsURL()));
            tabs.add(new TabInfo("Exceptions", "exceptions", getActionsURL()));

            return tabs;
        }

        @Override
        public HttpView<?> getTabView(String tabId)
        {
            if ("exceptions".equals(tabId))
                return new ActionsExceptionsView();
            return new ActionsView(!"details".equals(tabId));
        }
    }

    @AdminConsoleAction
    public static class ExportActionsAction extends ExportAction<Object>
    {
        @Override
        public void export(Object form, HttpServletResponse response, BindException errors) throws Exception
        {
            try (ActionsTsvWriter writer = new ActionsTsvWriter())
            {
                writer.write(response);
            }
        }
    }

    private static ActionURL getQueriesURL(@Nullable String statName)
    {
        ActionURL url = new ActionURL(QueriesAction.class, ContainerManager.getRoot());

        if (null != statName)
            url.addParameter("stat", statName);

        return url;
    }


    @AdminConsoleAction
    public class QueriesAction extends SimpleViewAction<QueriesForm>
    {
        @Override
        public ModelAndView getView(QueriesForm form, BindException errors)
        {
            String buttonHTML = "";
            if (getUser().hasRootAdminPermission())
                buttonHTML += PageFlowUtil.button("Reset All Statistics").href(getResetQueryStatisticsURL()).usePost() + "&nbsp;";
            buttonHTML += PageFlowUtil.button("Export").href(getExportQueriesURL()) + "<br/><br/>";

            return QueryProfiler.getInstance().getReportView(form.getStat(), buttonHTML, AdminController::getQueriesURL,
                    AdminController::getQueryStackTracesURL);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("queryPerf");
            addAdminNavTrail(root, "Queries", this.getClass());
        }
    }

    public static class QueriesForm
    {
        private String _stat = "Count";

        public String getStat()
        {
            return _stat;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setStat(String stat)
        {
            _stat = stat;
        }
    }


    private static ActionURL getQueryStackTracesURL(String sqlHash)
    {
        ActionURL url = new ActionURL(QueryStackTracesAction.class, ContainerManager.getRoot());
        url.addParameter("sqlHash", sqlHash);
        return url;
    }


    @AdminConsoleAction
    public class QueryStackTracesAction extends SimpleViewAction<QueryForm>
    {
        @Override
        public ModelAndView getView(QueryForm form, BindException errors)
        {
            return QueryProfiler.getInstance().getStackTraceView(form.getSqlHash(), AdminController::getExecutionPlanURL);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addAdminNavTrail(root, "Queries", QueriesAction.class);
            root.addChild("Query Stack Traces");
        }
    }


    private static ActionURL getExecutionPlanURL(String sqlHash)
    {
        ActionURL url = new ActionURL(ExecutionPlanAction.class, ContainerManager.getRoot());
        url.addParameter("sqlHash", sqlHash);
        return url;
    }


    @AdminConsoleAction
    public class ExecutionPlanAction extends SimpleViewAction<QueryForm>
    {
        private String _sqlHash;
        private ExecutionPlanType _type;

        @Override
        public ModelAndView getView(QueryForm form, BindException errors)
        {
            _sqlHash = form.getSqlHash();
            _type = EnumUtils.getEnum(ExecutionPlanType.class, form.getType());
            if (null == _type)
                throw new NotFoundException("Unknown execution plan type");

            return QueryProfiler.getInstance().getExecutionPlanView(form.getSqlHash(), _type, form.isLog());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addAdminNavTrail(root, "Queries", QueriesAction.class);
            root.addChild("Query Stack Traces", getQueryStackTracesURL(_sqlHash));
            root.addChild(_type.getDescription());
        }
    }


    public static class QueryForm
    {
        private String _sqlHash;
        private String _type = "Estimated"; // All dialects support Estimated
        private boolean _log = false;

        public String getSqlHash()
        {
            return _sqlHash;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setSqlHash(String sqlHash)
        {
            _sqlHash = sqlHash;
        }

        public String getType()
        {
            return _type;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setType(String type)
        {
            _type = type;
        }

        public boolean isLog()
        {
            return _log;
        }

        public void setLog(boolean log)
        {
            _log = log;
        }
    }


    private ActionURL getExportQueriesURL()
    {
        return new ActionURL(ExportQueriesAction.class, ContainerManager.getRoot());
    }


    @AdminConsoleAction
    public static class ExportQueriesAction extends ExportAction<Object>
    {
        @Override
        public void export(Object o, HttpServletResponse response, BindException errors) throws Exception
        {
            try (QueryStatTsvWriter writer = new QueryStatTsvWriter())
            {
                writer.setFilenamePrefix("SQL_Queries");
                writer.write(response);
            }
        }
    }

    private static ActionURL getResetQueryStatisticsURL()
    {
        return new ActionURL(ResetQueryStatisticsAction.class, ContainerManager.getRoot());
    }


    @RequiresPermission(AdminPermission.class)
    public static class ResetQueryStatisticsAction extends FormHandlerAction<QueriesForm>
    {
        @Override
        public void validateCommand(QueriesForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(QueriesForm form, BindException errors) throws Exception
        {
            QueryProfiler.getInstance().resetAllStatistics();
            return true;
        }

        @Override
        public URLHelper getSuccessURL(QueriesForm form)
        {
            return getQueriesURL(form.getStat());
        }
    }


    @AdminConsoleAction
    public class CachesAction extends SimpleViewAction<MemForm>
    {
        private final DecimalFormat commaf0 = new DecimalFormat("#,##0");
        private final DecimalFormat percent = new DecimalFormat("0%");

        @Override
        public ModelAndView getView(MemForm form, BindException errors)
        {
            getPageConfig().addClientDependency(ClientDependency.fromPath("admin/caches.js"));

            List<TrackingCache<?, ?>> caches = CacheManager.getKnownCaches();

            List<CacheStats> cacheStats = new ArrayList<>();
            List<CacheStats> transactionStats = new ArrayList<>();

            for (TrackingCache<?, ?> cache : caches)
            {
                cacheStats.add(CacheManager.getCacheStats(cache));
                transactionStats.add(CacheManager.getTransactionCacheStats(cache));
            }

            HtmlStringBuilder html = HtmlStringBuilder.of();

            html.append(createHtmlFragment(A(cl("labkey-text-link").at(href, "#").id("clearAllCaches"), "Clear Caches and Refresh")));
            html.append(createHtmlFragment(A(cl("labkey-text-link").at(href, "#").id("refreshPage"), "Refresh")));
            html.append(createHtmlFragment(
                SPAN(at(style, "display:none; margin-left:8px;").id("cacheSpinner"),
                    SPAN(at(style, "display:inline-block; width:14px; height:14px; border:2px solid #ccc; border-top-color:#333; border-radius:50%; animation:lk-spin 0.7s linear infinite; vertical-align:middle;"))),
                STYLE("@keyframes lk-spin { to { transform: rotate(360deg); } }"),
                DIV(cl("labkey-error").at(style, "display:none;").id("cacheError"))
            ));

            html.append(createHtmlFragment(BR(), BR()));
            appendStats(html, "Caches", cacheStats, false);

            html.append(createHtmlFragment(BR(), BR()));
            appendStats(html, "Transaction Caches", transactionStats, true);

            return new HtmlView(html);
        }

        private void appendStats(HtmlStringBuilder html, String title, List<CacheStats> allStats, boolean skipUnusedCaches)
        {
            List<CacheStats> stats = skipUnusedCaches ?
                allStats.stream()
                    .filter(stat -> stat.getMaxSize() > 0)
                    .collect(Collectors.toCollection((Supplier<List<CacheStats>>) ArrayList::new)) :
                allStats;

            Collections.sort(stats);

            long size = stats.stream().mapToLong(CacheStats::getSize).sum();
            long gets = stats.stream().mapToLong(CacheStats::getGets).sum();
            long misses = stats.stream().mapToLong(CacheStats::getMisses).sum();
            long puts = stats.stream().mapToLong(CacheStats::getPuts).sum();
            long expirations = stats.stream().mapToLong(CacheStats::getExpirations).sum();
            long evictions = stats.stream().mapToLong(CacheStats::getEvictions).sum();
            long removes = stats.stream().mapToLong(CacheStats::getRemoves).sum();
            long clears = stats.stream().mapToLong(CacheStats::getClears).sum();
            double ratio = gets != 0 ? misses / (double) gets : 0;

            AtomicInteger rowCount = new AtomicInteger();
            html.append(createHtmlFragment(
                P(B(title + " (" + stats.size() + ")")),
                TABLE(cl("labkey-data-region-legacy", "labkey-show-borders", "labkey-data-region-header-lock"),
                    TR(
                        TD(cl("labkey-column-header"), "Debug Name"),
                        TD(cl("labkey-column-header"), "Limit"),
                        TD(cl("labkey-column-header"), "Max Size"),
                        TD(cl("labkey-column-header"), "Current Size"),
                        TD(cl("labkey-column-header"), "Gets"),
                        TD(cl("labkey-column-header"), "Misses"),
                        TD(cl("labkey-column-header"), "Puts"),
                        TD(cl("labkey-column-header"), "Expirations"),
                        TD(cl("labkey-column-header"), "Evictions"),
                        TD(cl("labkey-column-header"), "Removes"),
                        TD(cl("labkey-column-header"), "Clears"),
                        TD(cl("labkey-column-header"), "Miss Percentage"),
                        TD(cl("labkey-column-header"), "Clear")
                    ),
                    stats.stream().map(stat -> {
                        Long limit = stat.getLimit();
                        long maxSize = stat.getMaxSize();

                        String clearId = "clear_" + UniqueID.getServerSessionScopedUID();
                        HttpView.currentPageConfig().addHandler(clearId, "click",
                            "LABKEY.Admin.Caches.clearSingle(" + PageFlowUtil.jsString(stat.getDescription()) + "); return false;");

                        return TR(cl(rowCount.getAndIncrement() % 2 == 0 ? "labkey-alternate-row" : "labkey-row"),
                            descriptionCell(stat.getDescription(), stat.getCreationStackTrace()),
                            longCells(limit, maxSize, stat.getSize(), stat.getGets(), stat.getMisses(), stat.getPuts(), stat.getExpirations(), stat.getEvictions(), stat.getRemoves(), stat.getClears()),
                            doubleCell(stat.getMissRatio()),
                            TD(A(cl("labkey-text-link").at(href, "#").id(clearId), "Clear")),
                            null != limit && maxSize >= limit ? TD(SPAN(cl("labkey-error"), "This cache has been limited")) : null
                        );
                    }),
                    TR(cl("labkey-row"),
                        TD(B("Total")),
                        longCells(null, null, size, gets, misses, puts, expirations, evictions, removes, clears),
                        doubleCell(ratio)
                        )
                    )
                )
            );
        }

        private static final List<String> PREFIXES_TO_SKIP = List.of(
            "java.base/java.lang.Thread.getStackTrace",
            "org.labkey.api.cache.CacheManager",
            "org.labkey.api.cache.Throttle",
            "org.labkey.api.data.DatabaseCache",
            "org.labkey.api.module.ModuleResourceCache"
        );

        private Renderable descriptionCell(String description, @Nullable StackTraceElement[] creationStackTrace)
        {
            StringBuilder sb = new StringBuilder();

            if (creationStackTrace != null)
            {
                boolean trimming = true;
                for (StackTraceElement element : creationStackTrace)
                {
                    // Skip the first few uninteresting stack trace elements to highlight the caller we care about
                    if (trimming)
                    {
                        if (PREFIXES_TO_SKIP.stream().anyMatch(prefix -> element.toString().startsWith(prefix)))
                            continue;

                        trimming = false;
                    }
                    sb.append(element);
                    sb.append("\n");
                }
            }

            if (sb.isEmpty())
                return TD(description);

            String message = PageFlowUtil.jsString(sb);
            String id = "id" + UniqueID.getServerSessionScopedUID();
            HttpView.currentPageConfig().addHandler(id, "click", "alert(" + message + ");return false;");
            return TD(A(at(href, "#").id(id), description));
        }

        private List<Renderable> longCells(Long... stats)
        {
            List<Renderable> cells = new ArrayList<>();
            for (Long stat : stats)
            {
                if (null == stat)
                    cells.add(TD(NBSP));
                else
                    cells.add(TD(at(style, "text-align:right"), commaf0.format(stat)));
            }
            return cells;
        }

        private Renderable doubleCell(double stat)
        {
            return TD(at(style, "text-align:right"), percent.format(stat));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("cachesDiagnostics");
            addAdminNavTrail(root, "Cache Statistics", this.getClass());
        }
    }

    @AdminConsoleAction
    public static class ClearCachesAction extends MutatingApiAction<MemForm>
    {
        @Override
        public ApiResponse execute(MemForm form, BindException errors)
        {
            if (form.getDebugName() != null)
            {
                for (TrackingCache<?, ?> cache : CacheManager.getKnownCaches())
                {
                    if (form.getDebugName().equals(cache.getDebugName()))
                    {
                        LOG.info("Purging cache: " + cache.getDebugName());
                        cache.clear();
                    }
                }
            }
            else if (form.isClearCaches() && form.isGc())
            {
                long before = doGc();
                doClearCaches();
                long cacheMemoryUsed = before - doGc();
                String cacheMemUsed = cacheMemoryUsed > 0 ? FileUtils.byteCountToDisplaySize(cacheMemoryUsed) : "Unknown";
                LOG.info("Estimate of cache memory used: " + cacheMemUsed);
                lastCacheMemUsed = cacheMemUsed;
            }
            else if (form.isClearCaches())
            {
                doClearCaches();
            }
            else if (form.isGc())
            {
                doGc();
            }
            return new ApiSimpleResponse("success", true);
        }

        private long doGc()
        {
            LOG.info("Garbage collecting");
            System.gc();
            return Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        }

        private void doClearCaches()
        {
            LOG.info("Clearing Introspector caches");
            Introspector.flushCaches();
            LOG.info("Purging all caches");
            CacheManager.clearAllKnownCaches();
            SearchService.get().purgeQueues();
        }
    }

    @RequiresSiteAdmin
    public class EnvironmentVariablesAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            Map<String, String> env = new LinkedHashMap<>(System.getenv());
            env.replaceAll((name, value) -> LabKeyProcessBuilder.isSecret(name) ? "[REDACTED]" : value);
            return new JspView<>("/org/labkey/core/admin/properties.jsp", env);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addAdminNavTrail(root, "Environment Variables", this.getClass());
        }
    }

    @RequiresSiteAdmin
    public class SystemPropertiesAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return new JspView<Map<String, String>>("/org/labkey/core/admin/properties.jsp", new HashMap(System.getProperties()));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addAdminNavTrail(root, "System Properties", this.getClass());
        }
    }

    @RequiresSiteAdmin
    public class SecretsAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            List<SecretStatus> statuses = SecretService.get().getSecretStatuses();

            List<Renderable> rows = new ArrayList<>();

            if (statuses.isEmpty())
            {
                return new HtmlView(DIV("No secrets have been registered with SecretService."));
            }

            MutableInt rowIndex = new MutableInt(0);

            Renderable table = DOM.TABLE(cl("table-condensed labkey-data-region table-bordered").at(style, "border:none"),
                    TR(
                        TH(at(style, "border:none; width:220px;"), STRONG("Secret Name")),
                        TH(at(style, "border:none; width:300px;"), STRONG("Description")),
                        TH(at(style, "border:none;"), STRONG("Source"))
                    ),
                    statuses.stream().map(status ->
                        TR(
                            cl(rowIndex.getAndIncrement() % 2 == 0 ? "labkey-alternate-row" : "labkey-row"),
                            TD(status.name()),
                            TD(status.description()),
                            status.isSet()
                                    ? TD(status.source())
                                    : TD(SPAN(cl("labkey-error"), "Not configured"))
                        )));
            return new HtmlView(table);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addAdminNavTrail(root, "Secrets", this.getClass());
        }
    }

    // NOTE let the Professional Module register this action (there is no ProfessionalController)

    @RequiresPermission(TroubleshooterPermission.class)
    public class AssistantStatusAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            return McpService.get().getAssistantStatusView();
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addAdminNavTrail(root, "AI Assistant Status", this.getClass());
        }
    }



    public static class ConfigureSystemMaintenanceForm
    {
        private String _maintenanceTime;
        private Set<String> _enable = Collections.emptySet();
        private boolean _enableSystemMaintenance = true;

        public String getMaintenanceTime()
        {
            return _maintenanceTime;
        }

        @SuppressWarnings("unused")
        public void setMaintenanceTime(String maintenanceTime)
        {
            _maintenanceTime = maintenanceTime;
        }

        public Set<String> getEnable()
        {
            return _enable;
        }

        @SuppressWarnings("unused")
        public void setEnable(Set<String> enable)
        {
            _enable = enable;
        }

        public boolean isEnableSystemMaintenance()
        {
            return _enableSystemMaintenance;
        }

        @SuppressWarnings("unused")
        public void setEnableSystemMaintenance(boolean enableSystemMaintenance)
        {
            _enableSystemMaintenance = enableSystemMaintenance;
        }
    }

    @AdminConsoleAction(AdminOperationsPermission.class)
    public class ConfigureSystemMaintenanceAction extends FormViewAction<ConfigureSystemMaintenanceForm>
    {
        @Override
        public void validateCommand(ConfigureSystemMaintenanceForm form, Errors errors)
        {
            Date date = SystemMaintenance.parseSystemMaintenanceTime(form.getMaintenanceTime());

            if (null == date)
                errors.reject(ERROR_MSG, "Invalid format for system maintenance time");
        }

        @Override
        public ModelAndView getView(ConfigureSystemMaintenanceForm form, boolean reshow, BindException errors)
        {
            SystemMaintenanceProperties prop = SystemMaintenance.getProperties();
            return new JspView<>("/org/labkey/core/admin/systemMaintenance.jsp", prop, errors);
        }

        @Override
        public boolean handlePost(ConfigureSystemMaintenanceForm form, BindException errors)
        {
            SystemMaintenance.setTimeDisabled(getContainer(), getUser(), !form.isEnableSystemMaintenance());
            SystemMaintenance.setProperties(getContainer(), getUser(), form.getEnable(), form.getMaintenanceTime());

            return true;
        }

        @Override
        public URLHelper getSuccessURL(ConfigureSystemMaintenanceForm form)
        {
            return new AdminUrlsImpl().getAdminConsoleURL();
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addAdminNavTrail(root, "Configure System Maintenance", this.getClass());
        }
    }

    @AdminConsoleAction(AdminOperationsPermission.class)
    public static class ResetSystemMaintenanceAction extends FormHandlerAction<Object>
    {
        @Override
        public void validateCommand(Object target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            SystemMaintenance.clearProperties();
            return true;
        }

        @Override
        public URLHelper getSuccessURL(Object o)
        {
            return new AdminUrlsImpl().getAdminConsoleURL();
        }
    }

    public static class SystemMaintenanceForm
    {
        private String _taskName;
        private boolean _test = false;

        public String getTaskName()
        {
            return _taskName;
        }

        @SuppressWarnings("unused")
        public void setTaskName(String taskName)
        {
            _taskName = taskName;
        }

        public boolean isTest()
        {
            return _test;
        }

        public void setTest(boolean test)
        {
            _test = test;
        }
    }

    @RequiresSiteAdmin
    public class SystemMaintenanceAction extends FormHandlerAction<SystemMaintenanceForm>
    {
        private Long _jobId = null;
        private URLHelper _url = null;

        @Override
        public void validateCommand(SystemMaintenanceForm form, Errors errors)
        {
        }

        @Override
        public ModelAndView getSuccessView(SystemMaintenanceForm form) throws IOException
        {
            // Send the pipeline job details absolute URL back to the test
            sendPlainText(_url.getURIString());

            // Suppress templates, divs, etc.
            getPageConfig().setTemplate(Template.None);
            return new EmptyView();
        }

        @Override
        public boolean handlePost(SystemMaintenanceForm form, BindException errors)
        {
            String jobGuid = new SystemMaintenanceJob(form.getTaskName(), getUser()).call();

            if (null != jobGuid)
                _jobId = PipelineService.get().getJobId(getUser(), getContainer(), jobGuid);

            PipelineStatusUrls urls = urlProvider(PipelineStatusUrls.class);
            _url = null != _jobId ? urls.urlDetails(getContainer(), _jobId) : urls.urlBegin(getContainer());

            return true;
        }

        @Override
        public URLHelper getSuccessURL(SystemMaintenanceForm form)
        {
            // In the standard case, redirect to the pipeline details URL
            // If the test is invoking system maintenance then return the URL instead
            return form.isTest() ? null : _url;
        }
    }

    private abstract static class AbstractAdminQueryAction extends QueryViewAction<QueryExportForm, QueryView>
    {
        private final String _schemaName;
        private final String _queryName;

        protected AbstractAdminQueryAction(String schemaName, String queryName)
        {
            super(QueryExportForm.class);
            _schemaName = schemaName;
            _queryName = queryName;
        }

        @Override
        protected QueryView createQueryView(QueryExportForm form, BindException errors, boolean forExport, @Nullable String dataRegion) throws Exception
        {
            QuerySettings qSettings = new QuerySettings(getViewContext(), _schemaName, _queryName);
            if (qSettings.getContainerFilterName() == null)
                qSettings.setContainerFilterName(ContainerFilter.Type.AllFolders.name());
            QueryView result = new QueryView(getUserSchema(), qSettings, errors);
            result.setTitle(_queryName);
            result.setFrame(WebPartView.FrameType.PORTAL);
            return result;
        }

        protected String getQueryName()
        {
            return _queryName;
        }

        abstract protected UserSchema getUserSchema();
    }

    private abstract static class AbstractAttachmentQueryAction extends AbstractAdminQueryAction
    {
        public AbstractAttachmentQueryAction(String queryName)
        {
            super("core", queryName);
        }

        @Override
        public void setViewContext(ViewContext context)
        {
            // Give Troubleshooters (and ImpersonatingTroubleshooters) read permissions in all containers so they can
            // see attachment counts by parent type plus details. I don't love poking an elevated user into the
            // ViewContext, but this is the only way I could get DataRegion to see read permission on tables that are
            // wrapped by a query (e.g., core.Documents used by DocumentsGroupedByParentType.sql).
            context.setUser(ElevatedUser.getElevatedUser(context.getUser(), ReaderRole.class));
            super.setViewContext(context);
        }

        @Override
        protected UserSchema getUserSchema()
        {
            return new CoreQuerySchema(getUser(), getContainer(), false);
        }
    }

    // This allows Troubleshooters to GET and POST to the action, supporting export to Excel and script, e.g.
    @RequiresPermission(TroubleshooterPermission.class)
    public class AttachmentsAction extends AbstractAttachmentQueryAction
    {
        @SuppressWarnings("unused") // Invoked via reflection
        public AttachmentsAction()
        {
            super("DocumentsGroupedByParentTypeAdmin");
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addAdminNavTrail(root, "Documents Grouped by Parent Type", getClass());
        }
    }

    @SuppressWarnings("unused") // Linked from core.DocumentsGroupedByParentTypeAdmin
    @RequiresPermission(TroubleshooterPermission.class)
    public class AttachmentsForTypeAction extends AbstractAttachmentQueryAction
    {
        @SuppressWarnings("unused") // Invoked via reflection
        public AttachmentsForTypeAction()
        {
            super("Documents");
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            String parentType = getViewContext().getActionURL().getParameter("core.ParentType~eq");
            addAdminNavTrail(root, parentType != null ? "Documents Belonging to Parent Type \"" + parentType + "\"" : "Documents", getClass());
        }
    }

    // Left behind with no link in the UI; this could be useful to track down orphaned attachments during the migration
    // process. Should delete after attachment migration is complete (late 2026?).
    @AdminConsoleAction
    public class FindAttachmentParentsAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return AttachmentService.get().getFindAttachmentParentsView();
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addAdminNavTrail(root, "Find Attachment Parents", getClass());
        }
    }

    @AdminConsoleAction
    public static class LogOrphanedAttachmentsAction extends ReadOnlyApiAction<Object>
    {
        @Override
        public Object execute(Object o, BindException errors) throws Exception
        {
            int count = 0;
            AttachmentService svc = AttachmentService.get();
            if (svc != null)
                count = svc.logOrphanedAttachments();
            return Map.of("count", count);
        }
    }

    private static volatile String lastCacheMemUsed = null;

    @AdminConsoleAction
    public class MemTrackerAction extends SimpleViewAction<MemForm>
    {
        @Override
        public ModelAndView getView(MemForm form, BindException errors)
        {
            Set<Object> objectsToIgnore = MemTracker.getInstance().beforeReport();
            return new JspView<>("/org/labkey/core/admin/memTracker.jsp", new MemBean(getViewContext().getRequest(), objectsToIgnore));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("memTracker");
            addAdminNavTrail(root, "Memory usage -- " + DateUtil.formatDateTime(getContainer()), this.getClass());
        }
    }

    public static class MemForm
    {
        private boolean _clearCaches = false;
        private boolean _gc = false;
        private String _debugName;

        public boolean isClearCaches()
        {
            return _clearCaches;
        }

        @SuppressWarnings("unused")
        public void setClearCaches(boolean clearCaches)
        {
            _clearCaches = clearCaches;
        }

        public boolean isGc()
        {
            return _gc;
        }

        @SuppressWarnings("unused")
        public void setGc(boolean gc)
        {
            _gc = gc;
        }

        public String getDebugName()
        {
            return _debugName;
        }

        @SuppressWarnings("unused")
        public void setDebugName(String debugName)
        {
            _debugName = debugName;
        }
    }

    public static class MemBean
    {
        public final List<Tuple3<Boolean, String, MemoryUsageSummary>> memoryUsages = new ArrayList<>();
        public final List<Pair<String, Object>> systemProperties = new ArrayList<>();
        public final List<HeldReference> references;
        public final List<String> graphNames = new ArrayList<>();
        public final List<String> activeThreads = new LinkedList<>();

        public boolean assertsEnabled = false;

        private MemBean(HttpServletRequest request, Set<Object> objectsToIgnore)
        {
            MemTracker memTracker = MemTracker.getInstance();
            List<HeldReference> all = memTracker.getReferences();
            long threadId = Thread.currentThread().threadId();

            // Attempt to detect other threads running labkey code -- mem tracker page will warn if any are found
            for (Thread thread : new ThreadsBean().threads)
            {
                if (thread.threadId() == threadId)
                    continue;

                Thread.State state = thread.getState();

                if (state == Thread.State.RUNNABLE || state == Thread.State.BLOCKED)
                {
                    boolean labkeyThread = false;

                    if (memTracker.shouldDisplay(thread))
                    {
                        for (StackTraceElement element : thread.getStackTrace())
                        {
                            String className = element.getClassName();

                            if (className.startsWith("org.labkey") || className.startsWith("org.fhcrc"))
                            {
                                labkeyThread = true;
                                break;
                            }
                        }
                    }

                    if (labkeyThread)
                    {
                        String threadInfo = thread.getName();
                        TransactionFilter.RequestTracker uri = TransactionFilter.getRequestSummary(thread);
                        if (null != uri)
                            threadInfo += "; processing URL " + uri.toLogString();
                        activeThreads.add(threadInfo);
                    }
                }
            }

            // ignore recently allocated
            long start = ViewServlet.getRequestStartTime(request) - 5000;
            references = new ArrayList<>(all.size());

            for (HeldReference r : all)
            {
                if (r.getAllocationTime() >= start)
                    continue;

                if (objectsToIgnore.contains(r.getReference()))
                    continue;

                references.add(r);
            }

            // memory:
            graphNames.add("Heap");
            graphNames.add("Non Heap");

            MemoryMXBean membean = ManagementFactory.getMemoryMXBean();
            if (membean != null)
            {
                memoryUsages.add(Tuple3.of(true, HEAP_MEMORY_KEY, getUsage(membean.getHeapMemoryUsage())));
            }

            List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
            for (MemoryPoolMXBean pool : pools)
            {
                if (pool.getType() == MemoryType.HEAP)
                {
                    memoryUsages.add(Tuple3.of(false, pool.getName() + " " + pool.getType(), getUsage(pool)));
                    graphNames.add(pool.getName());
                }
            }

            if (membean != null)
            {
                memoryUsages.add(Tuple3.of(true, "Total Non-heap Memory", getUsage(membean.getNonHeapMemoryUsage())));
            }

            for (MemoryPoolMXBean pool : pools)
            {
                if (pool.getType() == MemoryType.NON_HEAP)
                {
                    memoryUsages.add(Tuple3.of(false, pool.getName() + " " + pool.getType(), getUsage(pool)));
                    graphNames.add(pool.getName());
                }
            }

            for (BufferPoolMXBean pool : ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class))
            {
                memoryUsages.add(Tuple3.of(true, "Buffer pool " + pool.getName(), new MemoryUsageSummary(pool)));
                graphNames.add(pool.getName());
            }

            DecimalFormat commaf0 = new DecimalFormat("#,##0");


            // class loader:
            ClassLoadingMXBean classbean = ManagementFactory.getClassLoadingMXBean();
            if (classbean != null)
            {
                systemProperties.add(new Pair<>("Loaded Class Count", commaf0.format(classbean.getLoadedClassCount())));
                systemProperties.add(new Pair<>("Unloaded Class Count", commaf0.format(classbean.getUnloadedClassCount())));
                systemProperties.add(new Pair<>("Total Loaded Class Count", commaf0.format(classbean.getTotalLoadedClassCount())));
            }

            // runtime:
            RuntimeMXBean runtimeBean = ManagementFactory.getRuntimeMXBean();
            if (runtimeBean != null)
            {
                systemProperties.add(new Pair<>("VM Start Time", DateUtil.formatIsoDateShortTime(new Date(runtimeBean.getStartTime()))));
                long upTime = runtimeBean.getUptime(); // round to sec
                upTime = upTime - (upTime % 1000);
                systemProperties.add(new Pair<>("VM Uptime", DateUtil.formatDuration(upTime)));
                systemProperties.add(new Pair<>("VM Version", runtimeBean.getVmVersion()));
                systemProperties.add(new Pair<>("VM Classpath", runtimeBean.getClassPath()));
            }

            // threads:
            ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
            if (threadBean != null)
            {
                systemProperties.add(new Pair<>("Thread Count", threadBean.getThreadCount()));
                systemProperties.add(new Pair<>("Peak Thread Count", threadBean.getPeakThreadCount()));
                long[] deadlockedThreads = threadBean.findMonitorDeadlockedThreads();
                systemProperties.add(new Pair<>("Deadlocked Thread Count", deadlockedThreads != null ? deadlockedThreads.length : 0));
            }

            // threads:
            List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
            for (GarbageCollectorMXBean gcBean : gcBeans)
            {
                systemProperties.add(new Pair<>(gcBean.getName() + " GC count", gcBean.getCollectionCount()));
                systemProperties.add(new Pair<>(gcBean.getName() + " GC time", DateUtil.formatDuration(gcBean.getCollectionTime())));
            }

            String cacheMem = lastCacheMemUsed;

            if (null != cacheMem)
                systemProperties.add(new Pair<>("Most Recent Estimated Cache Memory Usage", cacheMem));

            OperatingSystemMXBean osBean = ManagementFactory.getOperatingSystemMXBean();
            if (osBean != null)
            {
                systemProperties.add(new Pair<>("CPU count", osBean.getAvailableProcessors()));

                DecimalFormat f3 = new DecimalFormat("0.000");

                if (osBean instanceof com.sun.management.OperatingSystemMXBean sunOsBean)
                {
                    systemProperties.add(new Pair<>("Total OS memory", FileUtils.byteCountToDisplaySize(sunOsBean.getTotalMemorySize())));
                    systemProperties.add(new Pair<>("Free OS memory", FileUtils.byteCountToDisplaySize(sunOsBean.getFreeMemorySize())));
                    systemProperties.add(new Pair<>("OS CPU load", f3.format(sunOsBean.getCpuLoad())));
                    systemProperties.add(new Pair<>("JVM CPU load", f3.format(sunOsBean.getProcessCpuLoad())));
                }
            }

            //noinspection ConstantConditions
            assert assertsEnabled = true;
        }
    }

    private static MemoryUsageSummary getUsage(MemoryPoolMXBean pool)
    {
        try
        {
            return getUsage(pool.getUsage());
        }
        catch (IllegalArgumentException x)
        {
            // sometimes we get usage>committed exception with older versions of JRockit
            return null;
        }
    }

    public static class MemoryUsageSummary
    {

        public final long _init;
        public final long _used;
        public final long _committed;
        public final long _max;

        public MemoryUsageSummary(MemoryUsage usage)
        {
            _init = usage.getInit();
            _used = usage.getUsed();
            _committed = usage.getCommitted();
            _max = usage.getMax();
        }

        public MemoryUsageSummary(BufferPoolMXBean pool)
        {
            _init = -1;
            _used = pool.getMemoryUsed();
            _committed = _used;
            _max = pool.getTotalCapacity();
        }
    }

    private static MemoryUsageSummary getUsage(MemoryUsage usage)
    {
        if (null == usage)
            return null;

        try
        {
            return new MemoryUsageSummary(usage);
        }
        catch (IllegalArgumentException x)
        {
            // sometime we get usage>committed exception with older verions of JRockit
            return null;
        }
    }

    public static class ChartForm
    {
        private String _type;

        public String getType()
        {
            return _type;
        }

        public void setType(String type)
        {
            _type = type;
        }
    }

    private static class MemoryCategory implements Comparable<MemoryCategory>
    {
        private final String _type;
        private final double _mb;

        public MemoryCategory(String type, double mb)
        {
            _type = type;
            _mb = mb;
        }

        @Override
        public int compareTo(@NotNull MemoryCategory o)
        {
            return Double.compare(getMb(), o.getMb());
        }

        public String getType()
        {
            return _type;
        }

        public double getMb()
        {
            return _mb;
        }
    }

    @AdminConsoleAction
    public static class MemoryChartAction extends ExportAction<ChartForm>
    {
        @Override
        public void export(ChartForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            MemoryUsage usage = null;
            boolean showLegend = false;
            String title = form.getType();
            if ("Heap".equals(form.getType()))
            {
                usage = ManagementFactory.getMemoryMXBean().getHeapMemoryUsage();
                showLegend = true;
            }
            else if ("Non Heap".equals(form.getType()))
                usage = ManagementFactory.getMemoryMXBean().getNonHeapMemoryUsage();
            else
            {
                List<MemoryPoolMXBean> pools = ManagementFactory.getMemoryPoolMXBeans();
                for (Iterator<MemoryPoolMXBean> it = pools.iterator(); it.hasNext() && usage == null;)
                {
                    MemoryPoolMXBean pool = it.next();
                    if (form.getType().equals(pool.getName()))
                        usage = pool.getUsage();
                }
            }

            Pair<Long, String> divisor = null;

            List<MemoryCategory> types = new ArrayList<>(4);

            if (usage == null)
            {
                boolean found = false;
                for (Iterator<BufferPoolMXBean> it = ManagementFactory.getPlatformMXBeans(BufferPoolMXBean.class).iterator(); it.hasNext() && !found;)
                {
                    BufferPoolMXBean pool = it.next();
                    if (form.getType().equals(pool.getName()))
                    {
                        long total = pool.getTotalCapacity();
                        long used = pool.getMemoryUsed();

                        divisor = getDivisor(total);

                        title = "Buffer pool " + title;

                        if (total > 0 || used > 0)
                        {
                            types.add(new MemoryCategory("Used", used / divisor.first));
                            types.add(new MemoryCategory("Max", total / divisor.first));
                        }
                        found = true;
                    }
                }
                if (!found)
                {
                    throw new NotFoundException();
                }
            }
            else
            {
                if (usage.getInit() > 0 || usage.getUsed() > 0 || usage.getCommitted() > 0 || usage.getMax() > 0)
                {
                    divisor = getDivisor(Math.max(usage.getInit(), Math.max(usage.getUsed(), Math.max(usage.getCommitted(), usage.getMax()))));

                    types.add(new MemoryCategory("Init", (double) usage.getInit() / divisor.first));
                    types.add(new MemoryCategory("Used", (double) usage.getUsed() / divisor.first));
                    types.add(new MemoryCategory("Committed", (double) usage.getCommitted() / divisor.first));
                    types.add(new MemoryCategory("Max", (double) usage.getMax() / divisor.first));
                }
            }

            if (divisor != null)
            {
                title += " (" + divisor.second + ")";
            }

            DefaultCategoryDataset dataset = new DefaultCategoryDataset();

            Collections.sort(types);

            for (int i = 0; i < types.size(); i++)
            {
                double mbPastPrevious = i > 0 ? types.get(i).getMb() - types.get(i - 1).getMb() : types.get(i).getMb();
                dataset.addValue(mbPastPrevious, types.get(i).getType(), "");
            }

            JFreeChart chart = ChartFactory.createStackedBarChart(title, null, null, dataset, PlotOrientation.HORIZONTAL, showLegend, false, false);
            chart.getTitle().setFont(new Font("SansSerif", Font.BOLD, 14));
            response.setContentType("image/png");

            ChartUtilities.writeChartAsPNG(response.getOutputStream(), chart, showLegend ? 800 : 398, showLegend ? 100 : 70);
        }

        private Pair<Long, String> getDivisor(long l)
        {
            if (l > 4096L * 1024L * 1024L)
            {
                return Pair.of(1024L * 1024L * 1024L, "GB");
            }
            if (l > 4096L * 1024L)
            {
                return Pair.of(1024L * 1024L, "MB");
            }
            if (l > 4096L)
            {
                return Pair.of(1024L, "KB");
            }

            return Pair.of(1L, "bytes");

        }
    }

    public static class MemoryStressForm
    {
        private int _threads = 3;
        private int _arraySize = 20_000;
        private int _arrayCount = 10_000;
        private float _percentChurn = 0.50f;
        private int _delay = 20;
        private int _iterations = 500;

        public int getThreads()
        {
            return _threads;
        }

        public void setThreads(int threads)
        {
            _threads = threads;
        }

        public int getArraySize()
        {
            return _arraySize;
        }

        public void setArraySize(int arraySize)
        {
            _arraySize = arraySize;
        }

        public int getArrayCount()
        {
            return _arrayCount;
        }

        public void setArrayCount(int arrayCount)
        {
            _arrayCount = arrayCount;
        }

        public float getPercentChurn()
        {
            return _percentChurn;
        }

        public void setPercentChurn(float percentChurn)
        {
            _percentChurn = percentChurn;
        }

        public int getDelay()
        {
            return _delay;
        }

        public void setDelay(int delay)
        {
            _delay = delay;
        }

        public int getIterations()
        {
            return _iterations;
        }

        public void setIterations(int iterations)
        {
            _iterations = iterations;
        }
    }

    @RequiresSiteAdmin
    public class MemoryStressTestAction extends FormViewAction<MemoryStressForm>
    {
        @Override
        public void validateCommand(MemoryStressForm target, Errors errors)
        {

        }

        @Override
        public ModelAndView getView(MemoryStressForm memoryStressForm, boolean reshow, BindException errors) throws Exception
        {
            return new HtmlView(
                    DOM.LK.FORM(at(method, "POST"),
                            DOM.LK.ERRORS(errors.getBindingResult()),
                            DOM.BR(), DOM.BR(),
                            "This utility action will do a lot of memory allocation to test the memory configuration of the host.",
                            DOM.BR(), DOM.BR(),
                            "It spins up threads, all of which allocate a specified number byte arrays of specified length.",
                            DOM.BR(),
                            "The threads sleep for the delay period, and then replace the specified percent of arrays with new ones.",
                            DOM.BR(),
                            "They continue for the specified number of allocations.",
                            DOM.BR(),
                            "The memory actively held is approximately (threads * array count * array length).",
                            DOM.BR(),
                            "The memory turnover is based on the churn percentage, array length, delay, and iterations.",
                            DOM.BR(), DOM.BR(),
                            DOM.TABLE(
                                    DOM.TR(DOM.TD("Thread count"), DOM.TD(DOM.INPUT(at(name, "threads", value, memoryStressForm._threads)))),
                                    DOM.TR(DOM.TD("Byte array count"), DOM.TD(DOM.INPUT(at(name, "arrayCount", value, memoryStressForm._arrayCount)))),
                                    DOM.TR(DOM.TD("Byte array size"), DOM.TD(DOM.INPUT(at(name, "arraySize", value, memoryStressForm._arraySize)))),
                                    DOM.TR(DOM.TD("Iterations"), DOM.TD(DOM.INPUT(at(name, "iterations", value, memoryStressForm._iterations)))),
                                    DOM.TR(DOM.TD("Delay between iterations (ms)"), DOM.TD(DOM.INPUT(at(name, "delay", value, memoryStressForm._delay)))),
                                    DOM.TR(DOM.TD("Percent churn per iteration (0.0 - 1.0)"), DOM.TD(DOM.INPUT(at(name, "percentChurn", value, memoryStressForm._percentChurn))))
                            ),
                            new ButtonBuilder("Perform stress test").submit(true).build())
            );
        }

        @Override
        public boolean handlePost(MemoryStressForm memoryStressForm, BindException errors) throws Exception
        {
            List<Thread> threads = new ArrayList<>();
            for (int i = 0; i < memoryStressForm._threads; i++)
            {
                Thread t = new Thread(() ->
                {
                    Random r = new Random();
                    byte[][] arrays = new byte[memoryStressForm._arrayCount][];
                    // Initialize the arrays
                    for (int a = 0; a < arrays.length; a++)
                    {
                        arrays[a] = new byte[memoryStressForm._arraySize];
                    }

                    for (int iter = 0; iter < memoryStressForm._iterations; iter++)
                    {
                        try
                        {
                            Thread.sleep(memoryStressForm._delay);
                        }
                        catch (InterruptedException ignored) {}

                        // Swap the contents based on our desired percent churn
                        for (int a = 0; a < arrays.length; a++)
                        {
                            if (r.nextFloat() <= memoryStressForm._percentChurn)
                            {
                                arrays[a] = new byte[memoryStressForm._arraySize];
                            }
                        }
                    }
                });
                t.setUncaughtExceptionHandler((_, e) -> {
                    LOG.error("Stress test exception", e);
                    errors.reject(null, "Stress test exception: " + e);
                });
                t.start();
                threads.add(t);
            }

            for (Thread thread : threads)
            {
                thread.join();
            }

            return !errors.hasErrors();
        }

        @Override
        public URLHelper getSuccessURL(MemoryStressForm memoryStressForm)
        {
            return new ActionURL(MemTrackerAction.class, getContainer());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addAdminNavTrail(root, "Memory Usage", MemTrackerAction.class);
            root.addChild("Memory Stress Test");
        }
    }

    public static ActionURL getModuleStatusURL(URLHelper returnUrl)
    {
        ActionURL url = new ActionURL(ModuleStatusAction.class, ContainerManager.getRoot());
        if (returnUrl != null)
            url.addReturnUrl(returnUrl);
        return url;
    }

    public static class ModuleStatusBean
    {
        public String verb;
        public String verbing;
        public ActionURL nextURL;
    }

    @RequiresPermission(TroubleshooterPermission.class)
    @AllowedDuringUpgrade
    @IgnoresAllocationTracking
    public static class ModuleStatusAction extends SimpleViewAction<ReturnUrlForm>
    {
        @Override
        public ModelAndView getView(ReturnUrlForm form, BindException errors)
        {
            ModuleLoader loader = ModuleLoader.getInstance();
            VBox vbox = new VBox();
            ModuleStatusBean bean = new ModuleStatusBean();

            if (loader.isNewInstall())
                bean.nextURL = new ActionURL(NewInstallSiteSettingsAction.class, ContainerManager.getRoot());
            else if (form.getReturnUrl() != null)
            {
                try
                {
                    bean.nextURL = form.getReturnActionURL();
                }
                catch (URLException x)
                {
                    // might not be an ActionURL e.g. /labkey/_webdav/home
                }
            }
            if (null == bean.nextURL)
                bean.nextURL = new ActionURL(InstallCompleteAction.class, ContainerManager.getRoot());

            if (loader.isNewInstall())
                bean.verb = "Install";
            else if (loader.isUpgradeRequired() || loader.isUpgradeInProgress())
                bean.verb = "Upgrade";
            else
                bean.verb = "Start";

            if (loader.isNewInstall())
                bean.verbing = "Installing";
            else if (loader.isUpgradeRequired() || loader.isUpgradeInProgress())
                bean.verbing = "Upgrading";
            else
                bean.verbing = "Starting";

            JspView<ModuleStatusBean> statusView = new JspView<>("/org/labkey/core/admin/moduleStatus.jsp", bean, errors);
            vbox.addView(statusView);

            getPageConfig().setNavTrail(getInstallUpgradeWizardSteps());

            getPageConfig().setTemplate(Template.Wizard);
            getPageConfig().setTitle(bean.verb + " Modules");
            setHelpTopic(ModuleLoader.getInstance().isNewInstall() ? "config" : "upgrade");

            return vbox;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    public static class NewInstallSiteSettingsForm extends FileSettingsForm
    {
        private String _notificationEmail;
        private String _siteName;

        public String getNotificationEmail()
        {
            return _notificationEmail;
        }

        public void setNotificationEmail(String notificationEmail)
        {
            _notificationEmail = notificationEmail;
        }

        public String getSiteName()
        {
            return _siteName;
        }

        public void setSiteName(String siteName)
        {
            _siteName = siteName;
        }
    }

    @RequiresSiteAdmin
    public static class NewInstallSiteSettingsAction extends AbstractFileSiteSettingsAction<NewInstallSiteSettingsForm>
    {
        public NewInstallSiteSettingsAction()
        {
            super(NewInstallSiteSettingsForm.class);
        }

        @Override
        public void validateCommand(NewInstallSiteSettingsForm form, Errors errors)
        {
            super.validateCommand(form, errors);

            if (isBlank(form.getNotificationEmail()))
            {
                errors.reject(SpringActionController.ERROR_MSG, "Notification email address may not be blank.");
            }
            try
            {
                ValidEmail email = new ValidEmail(form.getNotificationEmail());
            }
            catch (ValidEmail.InvalidEmailException e)
            {
                errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
            }
        }

        @Override
        public boolean handlePost(NewInstallSiteSettingsForm form, BindException errors) throws Exception
        {
            boolean success = super.handlePost(form, errors);
            if (success)
            {
                WriteableLookAndFeelProperties lafProps = LookAndFeelProperties.getWriteableInstance(ContainerManager.getRoot());
                try
                {
                    lafProps.setSystemEmailAddress(new ValidEmail(form.getNotificationEmail()));
                }
                catch (ValidEmail.InvalidEmailException e)
                {
                    errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
                    return false;
                }
                lafProps.setSystemShortName(form.getSiteName());
                lafProps.save();

                // Send an immediate report now that they've set up their account and defaults, and then every 24 hours after.
                UsageReportingLevel.reportNow();

                return true;
            }
            return false;
        }

        @Override
        public ModelAndView getView(NewInstallSiteSettingsForm form, boolean reshow, BindException errors)
        {
            if (!reshow)
            {
                File root = _svc.getSiteDefaultRoot();

                if (root.exists())
                    form.setRootPath(FileUtil.getAbsoluteCaseSensitiveFile(root).getAbsolutePath());

                LookAndFeelProperties props = LookAndFeelProperties.getInstance(ContainerManager.getRoot());
                form.setSiteName(props.getShortName());
                form.setNotificationEmail(props.getSystemEmailAddress());
            }

            JspView<NewInstallSiteSettingsForm> view = new JspView<>("/org/labkey/core/admin/newInstallSiteSettings.jsp", form, errors);

            getPageConfig().setNavTrail(getInstallUpgradeWizardSteps());
            getPageConfig().setTitle("Set Defaults");
            getPageConfig().setTemplate(Template.Wizard);

            return view;
        }

        @Override
        public URLHelper getSuccessURL(NewInstallSiteSettingsForm form)
        {
            return new ActionURL(InstallCompleteAction.class, ContainerManager.getRoot());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    @RequiresSiteAdmin
    public static class InstallCompleteAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            JspView<Object> view = new JspView<>("/org/labkey/core/admin/installComplete.jsp");

            getPageConfig().setNavTrail(getInstallUpgradeWizardSteps());
            getPageConfig().setTitle("Complete");
            getPageConfig().setTemplate(Template.Wizard);

            return view;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    public static List<NavTree> getInstallUpgradeWizardSteps()
    {
        List<NavTree> navTrail = new ArrayList<>();
        if (ModuleLoader.getInstance().isNewInstall())
        {
            navTrail.add(new NavTree("Account Setup"));
            navTrail.add(new NavTree("Install Modules"));
            navTrail.add(new NavTree("Set Defaults"));
        }
        else if (ModuleLoader.getInstance().isUpgradeRequired() || ModuleLoader.getInstance().isUpgradeInProgress())
        {
            navTrail.add(new NavTree("Upgrade Modules"));
        }
        else
        {
            navTrail.add(new NavTree("Start Modules"));
        }
        navTrail.add(new NavTree("Complete"));
        return navTrail;
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class DbCheckerAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return new JspView<>("/org/labkey/core/admin/checkDatabase.jsp", new DataCheckForm());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addAdminNavTrail(root, "Database Check Tools", this.getClass());
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class DoCheckAction extends SimpleViewAction<DataCheckForm>
    {
        @Override
        public ModelAndView getView(DataCheckForm form, BindException errors)
        {
            try (var ignore=SpringActionController.ignoreSqlUpdates())
            {
                ActionURL currentUrl = getViewContext().cloneActionURL();
                String fixRequested = currentUrl.getParameter("_fix");
                HtmlStringBuilder contentBuilder = HtmlStringBuilder.of(HtmlString.unsafe("<table class=\"DataRegion\"><tr><td>"));

                if (null != fixRequested)
                {
                    HtmlString sqlCheck = HtmlString.EMPTY_STRING;
                    if (fixRequested.equalsIgnoreCase("container"))
                        sqlCheck = DbSchema.checkAllContainerCols(getUser(), true);
                    else if (fixRequested.equalsIgnoreCase("descriptor"))
                        sqlCheck = OntologyManager.doProjectColumnCheck(true);
                    contentBuilder.append(sqlCheck);
                }
                else
                {
                    LOG.info("Starting database check"); // Debugging test timeout
                    LOG.info("Checking container column references"); // Debugging test timeout
                    contentBuilder.unsafeAppend("\n<br/><br/>")
                        .append("Checking Container Column References...");
                    HtmlString strTemp = DbSchema.checkAllContainerCols(getUser(), false);
                    if (!strTemp.isEmpty())
                    {
                        contentBuilder.append(strTemp);
                        currentUrl = getViewContext().cloneActionURL();
                        currentUrl.addParameter("_fix", "container");
                        contentBuilder.unsafeAppend("<br/><br/>&nbsp;&nbsp;&nbsp;&nbsp;")
                            .append(" click ")
                            .append(LinkBuilder.simpleLink("here", currentUrl))
                            .append(" to attempt recovery.");
                    }

                    LOG.info("Checking PropertyDescriptor and DomainDescriptor consistency"); // Debugging test timeout
                    contentBuilder.unsafeAppend("\n<br/><br/>")
                        .append("Checking PropertyDescriptor and DomainDescriptor consistency...");
                    strTemp = OntologyManager.doProjectColumnCheck(false);
                    if (!strTemp.isEmpty())
                    {
                        contentBuilder.append(strTemp);
                        currentUrl = getViewContext().cloneActionURL();
                        currentUrl.addParameter("_fix", "descriptor");
                        contentBuilder.unsafeAppend("<br/><br/>&nbsp;&nbsp;&nbsp;&nbsp;")
                            .append(" click ")
                            .append(LinkBuilder.simpleLink("here", currentUrl))
                            .append(" to attempt recovery.");
                    }

                    LOG.info("Checking Schema consistency with tableXML"); // Debugging test timeout
                    contentBuilder.unsafeAppend("\n<br/><br/>")
                        .append("Checking Schema consistency with tableXML.")
                        .unsafeAppend("<br><br>");
                    Set<DbSchema> schemas = DbSchema.getAllSchemasToTest();

                    for (DbSchema schema : schemas)
                    {
                        SiteValidationResultList schemaResult = TableXmlUtils.compareXmlToMetaData(schema, form.getFull(), false, true);
                        List<SiteValidationResult> results = schemaResult.getResults(null);
                        if (results.isEmpty())
                        {
                            contentBuilder.unsafeAppend("<b>")
                                .append(schema.getDisplayName())
                                .append(": OK")
                                .unsafeAppend("</b><br>");
                        }
                        else
                        {
                            contentBuilder.unsafeAppend("<b>")
                                .append(schema.getDisplayName())
                                .unsafeAppend("</b><br<ul style=\"list-style: none;\">");
                            for (var r : results)
                            {
                                HtmlString item = r.getMessage().isEmpty() ? NBSP : r.getMessage();
                                contentBuilder.unsafeAppend("<li>")
                                    .append(item)
                                    .unsafeAppend("</li>\n");
                            }
                            contentBuilder.unsafeAppend("</ul>");
                        }
                    }

                    LOG.info("Checking consistency of provisioned storage"); // Debugging test timeout
                    contentBuilder.unsafeAppend("\n<br/><br/>")
                        .append("Checking Consistency of Provisioned Storage...\n");
                    StorageProvisioner.ProvisioningReport pr = StorageProvisioner.get().getProvisioningReport();
                    contentBuilder.append(String.format("%d domains use Storage Provisioner", pr.getProvisionedDomains().size()));
                    for (StorageProvisioner.ProvisioningReport.DomainReport dr : pr.getProvisionedDomains())
                    {
                        for (String error : dr.getErrors())
                        {
                            contentBuilder.unsafeAppend("<div class=\"warning\">")
                                .append(error)
                                .unsafeAppend("</div>");
                        }
                    }
                    for (String error : pr.getGlobalErrors())
                    {
                        contentBuilder.unsafeAppend("<div class=\"warning\">")
                            .append(error)
                            .unsafeAppend("</div>");
                    }

                    LOG.info("Database check complete"); // Debugging test timeout
                    contentBuilder.unsafeAppend("\n<br/><br/>")
                        .append("Database Consistency checker complete");
                }

                contentBuilder.unsafeAppend("</td></tr></table>");

                return new HtmlView(contentBuilder);
            }
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addAdminNavTrail(root, "Database Tools", this.getClass());
        }
    }

    public static class DataCheckForm
    {
        private String _dbSchema = "";
        private boolean _full = false;

        public List<Module> modules = ModuleLoader.getInstance().getModules();
        public DataCheckForm(){}

        public List<Module> getModules() { return modules;  }
        public String getDbSchema() { return _dbSchema; }
        @SuppressWarnings("unused")
        public void setDbSchema(String dbSchema){ _dbSchema = dbSchema; }
        public boolean getFull() { return _full; }
        public void setFull(boolean full) { _full = full; }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public static class GetSchemaXmlDocAction extends ExportAction<DataCheckForm>
    {
        @Override
        public void export(DataCheckForm form, HttpServletResponse response, BindException errors) throws Exception
        {
            String fullyQualifiedSchemaName = form.getDbSchema();
            if (null == fullyQualifiedSchemaName || fullyQualifiedSchemaName.isEmpty())
            {
                throw new NotFoundException("Must specify dbSchema parameter");
            }

            boolean bFull = form.getFull();

            Pair<DbScope, String> scopeAndSchemaName = DbSchema.getDbScopeAndSchemaName(fullyQualifiedSchemaName);
            TablesDocument tdoc = TableXmlUtils.createXmlDocumentFromDatabaseMetaData(scopeAndSchemaName.first, scopeAndSchemaName.second, bFull);
            StringWriter sw = new StringWriter();

            XmlOptions xOpt = new XmlOptions();
            xOpt.setSavePrettyPrint();
            xOpt.setUseDefaultNamespace();

            tdoc.save(sw, xOpt);

            sw.flush();
            PageFlowUtil.streamFileBytes(response, fullyQualifiedSchemaName + ".xml", sw.toString().getBytes(StringUtilsLabKey.DEFAULT_CHARSET), true);
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class FolderInformationAction extends FolderManagementViewAction
    {
        @Override
        protected HtmlView getTabView()
        {
            Container c = getContainer();
            User currentUser = getUser();

            User createdBy = UserManager.getUser(c.getCreatedBy());
            Map<String, Object> propValueMap = new LinkedHashMap<>();
            propValueMap.put("Path", c.getPath());
            propValueMap.put("Name", c.getName());
            propValueMap.put("Displayed Title", c.getTitle());
            propValueMap.put("EntityId", c.getId());
            propValueMap.put("RowId", c.getRowId());
            propValueMap.put("Created", DateUtil.formatDateTime(c, c.getCreated()));
            propValueMap.put("Created By", (createdBy != null ? createdBy.getDisplayName(currentUser) : "<" + c.getCreatedBy() + ">"));
            propValueMap.put("Folder Type", c.getFolderType().getName());
            propValueMap.put("Description", c.getDescription());

            return new HtmlView(PageFlowUtil.getDataRegionHtmlForPropertyObjects(propValueMap));
        }
    }

    public static class MissingValuesForm
    {
        private boolean _inheritMvIndicators;
        private String[] _mvIndicators;
        private String[] _mvLabels;

        public boolean isInheritMvIndicators()
        {
            return _inheritMvIndicators;
        }

        public void setInheritMvIndicators(boolean inheritMvIndicators)
        {
            _inheritMvIndicators = inheritMvIndicators;
        }

        public String[] getMvIndicators()
        {
            return _mvIndicators;
        }

        public void setMvIndicators(String[] mvIndicators)
        {
            _mvIndicators = mvIndicators;
        }

        public String[] getMvLabels()
        {
            return _mvLabels;
        }

        public void setMvLabels(String[] mvLabels)
        {
            _mvLabels = mvLabels;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class MissingValuesAction extends FolderManagementViewPostAction<MissingValuesForm>
    {
        @Override
        protected JspView<?> getTabView(MissingValuesForm form, boolean reshow, BindException errors)
        {
            return new JspView<>("/org/labkey/core/admin/mvIndicators.jsp", form, errors);
        }

        @Override
        public void validateCommand(MissingValuesForm form, Errors errors)
        {
        }

        @Override
        public boolean handlePost(MissingValuesForm form, BindException errors)
        {
            Container c = getContainer();
            SiteSettingsAuditProvider.SiteSettingsAuditEvent event = new SiteSettingsAuditProvider.SiteSettingsAuditEvent(c, "The missing value indicators were changed (see details).");
            StringBuilder html = new StringBuilder("<table>");
            if (form.isInheritMvIndicators())
            {
                MvUtil.inheritMvIndicators(c);
                AbstractWriteableSettingsGroup.appendDiffRow(html, "Inherit settings", null, "TRUE");
            }
            else
            {
                // Javascript should have enforced any constraints
                MvUtil.assignMvIndicators(c, form.getMvIndicators(), form.getMvLabels());
                for (int i=0; i < form.getMvIndicators().length; i++)
                    AbstractWriteableSettingsGroup.appendDiffRow(html, form.getMvIndicators()[i], null, form.getMvLabels()[i]);
            }
            html.append("</table>");
            event.setChanges(html.toString());
            AuditLogService.get().addEvent(getUser(), event);

            return true;
        }
    }

    @SuppressWarnings("unused")
    public static class RConfigForm
    {
        private Integer _reportEngine;
        private Integer _pipelineEngine;
        private boolean _overrideDefault;

        public Integer getReportEngine()
        {
            return _reportEngine;
        }

        public void setReportEngine(Integer reportEngine)
        {
            _reportEngine = reportEngine;
        }

        public Integer getPipelineEngine()
        {
            return _pipelineEngine;
        }

        public void setPipelineEngine(Integer pipelineEngine)
        {
            _pipelineEngine = pipelineEngine;
        }

        public boolean getOverrideDefault()
        {
            return _overrideDefault;
        }

        public void setOverrideDefault(String overrideDefault)
        {
            _overrideDefault = "override".equals(overrideDefault);
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class RConfigurationAction extends FolderManagementViewPostAction<RConfigForm>
    {
        @Override
        protected HttpView<?> getTabView(RConfigForm form, boolean reshow, BindException errors)
        {
            return new JspView<>("/org/labkey/core/admin/rConfiguration.jsp", form, errors);
        }

        @Override
        public void validateCommand(RConfigForm form, Errors errors)
        {
            if (form.getOverrideDefault())
            {
                if (form.getReportEngine() == null)
                    errors.reject(ERROR_MSG, "Please select a valid report engine configuration");
                if (form.getPipelineEngine() == null)
                    errors.reject(ERROR_MSG, "Please select a valid pipeline engine configuration");
            }
        }

        @Override
        public URLHelper getSuccessURL(RConfigForm rConfigForm)
        {
            return getContainer().getStartURL(getUser());
        }

        @Override
        public boolean handlePost(RConfigForm rConfigForm, BindException errors) throws Exception
        {
            LabKeyScriptEngineManager mgr = LabKeyScriptEngineManager.get();
            if (null != mgr)
            {
                try (DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
                {
                    if (rConfigForm.getOverrideDefault())
                    {
                        ExternalScriptEngineDefinition reportEngine = mgr.getEngineDefinition(rConfigForm.getReportEngine(), ExternalScriptEngineDefinition.Type.R);
                        ExternalScriptEngineDefinition pipelineEngine = mgr.getEngineDefinition(rConfigForm.getPipelineEngine(), ExternalScriptEngineDefinition.Type.R);

                        if (reportEngine != null)
                            mgr.setEngineScope(getContainer(), reportEngine, LabKeyScriptEngineManager.EngineContext.report);
                        if (pipelineEngine != null)
                            mgr.setEngineScope(getContainer(), pipelineEngine, LabKeyScriptEngineManager.EngineContext.pipeline);
                    }
                    else
                    {
                        // need to clear the current scope (if any)
                        ExternalScriptEngineDefinition reportEngine = mgr.getScopedEngine(getContainer(), "r", LabKeyScriptEngineManager.EngineContext.report, false);
                        ExternalScriptEngineDefinition pipelineEngine = mgr.getScopedEngine(getContainer(), "r", LabKeyScriptEngineManager.EngineContext.pipeline, false);

                        if (reportEngine != null)
                            mgr.removeEngineScope(getContainer(), reportEngine, LabKeyScriptEngineManager.EngineContext.report);
                        if (pipelineEngine != null)
                            mgr.removeEngineScope(getContainer(), pipelineEngine, LabKeyScriptEngineManager.EngineContext.pipeline);
                    }
                    transaction.commit();
                }
                return true;
            }
            return false;
        }
    }

    @SuppressWarnings("unused")
    public static class ExportFolderForm
    {
        private String[] _types;
        private int _location;
        private String _format = "new"; // As of 14.3, this is the only supported format. But leave in place for the future.
        private String _exportType;
        private boolean _includeSubfolders;
        private PHI _exportPhiLevel;    // Input: max level when viewing form
        private boolean _shiftDates;
        private boolean _alternateIds;
        private boolean _maskClinic;

        public String[] getTypes()
        {
            return _types;
        }

        public void setTypes(String[] types)
        {
            _types = types;
        }

        public int getLocation()
        {
            return _location;
        }

        public void setLocation(int location)
        {
            _location = location;
        }

        public String getFormat()
        {
            return _format;
        }

        public void setFormat(String format)
        {
            _format = format;
        }

        public ExportType getExportType()
        {
            if ("study".equals(_exportType))
                return ExportType.STUDY;
            else
                return ExportType.ALL;
        }

        public void setExportType(String exportType)
        {
            _exportType = exportType;
        }

        public boolean isIncludeSubfolders()
        {
            return _includeSubfolders;
        }

        public void setIncludeSubfolders(boolean includeSubfolders)
        {
            _includeSubfolders = includeSubfolders;
        }

        public PHI getExportPhiLevel()
        {
            return null != _exportPhiLevel ? _exportPhiLevel : PHI.NotPHI;
        }

        public void setExportPhiLevel(PHI exportPhiLevel)
        {
            _exportPhiLevel = exportPhiLevel;
        }

        public boolean isShiftDates()
        {
            return _shiftDates;
        }

        public void setShiftDates(boolean shiftDates)
        {
            _shiftDates = shiftDates;
        }

        public boolean isAlternateIds()
        {
            return _alternateIds;
        }

        public void setAlternateIds(boolean alternateIds)
        {
            _alternateIds = alternateIds;
        }

        public boolean isMaskClinic()
        {
            return _maskClinic;
        }

        public void setMaskClinic(boolean maskClinic)
        {
            _maskClinic = maskClinic;
        }
    }

    public enum ExportOption
    {
        PipelineRootAsFiles("file root as multiple files")
                {
                    @Override
                    public ActionURL initiateExport(Container container, BindException errors, FolderWriterImpl writer, FolderExportContext ctx, HttpServletResponse response) throws Exception
                    {
                        PipeRoot root = PipelineService.get().findPipelineRoot(container);
                        if (root == null || !root.isValid())
                        {
                            throw new NotFoundException("No valid pipeline root found");
                        }
                        else if (root.isCloudRoot())
                        {
                            errors.reject(ERROR_MSG, "Cannot export as individual files when root is in the cloud");
                        }
                        else
                        {
                            File exportDir = root.resolvePath(PipelineService.EXPORT_DIR);
                            try
                            {
                                writer.write(container, ctx, new FileSystemFile(exportDir));
                            }
                            catch (ContainerException e)
                            {
                                errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
                            }
                            return urlProvider(PipelineUrls.class).urlBrowse(container);
                        }
                        return null;
                    }
                },

        PipelineRootAsZip("file root as a single zip file")
        {
            @Override
            public ActionURL initiateExport(Container container, BindException errors, FolderWriterImpl writer, FolderExportContext ctx, HttpServletResponse response) throws Exception
            {
                PipeRoot root = PipelineService.get().findPipelineRoot(container);
                if (root == null || !root.isValid())
                {
                    throw new NotFoundException("No valid pipeline root found");
                }
                Path exportDir = root.resolveToNioPath(PipelineService.EXPORT_DIR);
                FileUtil.createDirectories(exportDir);
                exportFolderToFile(exportDir, container, writer, ctx, errors);
                return urlProvider(PipelineUrls.class).urlBrowse(container);
            }
        },
        DownloadAsZip("browser download as a zip file")
                {
                    @Override
                    public ActionURL initiateExport(Container container, BindException errors, FolderWriterImpl writer, FolderExportContext ctx, HttpServletResponse response) throws Exception
                    {
                        try
                        {
                            // Export to a temporary file first so exceptions are displayed by the standard error page, Issue #44152
                            // Same pattern as ExportListArchiveAction
                            Path tempDir = FileUtil.getTempDirectory().toPath();
                            Path tempZipFile = exportFolderToFile(tempDir, container, writer, ctx, errors);

                            // No exceptions, so stream the resulting zip file to the browser and delete it
                            try (OutputStream os = ZipFile.getOutputStream(response, tempZipFile.getFileName().toString()))
                            {
                                Files.copy(tempZipFile, os);
                            }
                            finally
                            {
                                Files.delete(tempZipFile);
                            }
                        }
                        catch (ContainerException e)
                        {
                            errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
                        }
                        return null;
                    }
                };

        private final String _description;

        ExportOption(String description)
        {
            _description = description;
        }

        public String getDescription()
        {
            return _description;
        }

        public abstract ActionURL initiateExport(Container container, BindException errors, FolderWriterImpl writer, FolderExportContext ctx, HttpServletResponse response) throws Exception;

        Path exportFolderToFile(Path exportDir, Container container, FolderWriterImpl writer, FolderExportContext ctx, BindException errors) throws Exception
        {
            String filename = FileUtil.makeFileNameWithTimestamp(container.getName(), "folder.zip");

            try (ZipFile zip = new ZipFile(exportDir, filename))
            {
                writer.write(container, ctx, zip);
            }
            catch (Container.ContainerException e)
            {
                errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
            }

            return exportDir.resolve(filename);
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class ExportFolderAction extends FolderManagementViewPostAction<ExportFolderForm>
    {
        private ActionURL _successURL = null;

        @Override
        public ModelAndView getView(ExportFolderForm exportFolderForm, boolean reshow, BindException errors) throws Exception
        {
            // In export-to-browser do nothing (leave the export page in place). We just exported to the response, so
            // rendering a view would throw.
            return reshow && !errors.hasErrors() ? null : super.getView(exportFolderForm, reshow, errors);
        }

        @Override
        protected HttpView<?> getTabView(ExportFolderForm form, boolean reshow, BindException errors)
        {
            form.setExportType(PageFlowUtil.filter(getViewContext().getActionURL().getParameter("exportType")));

            ComplianceFolderSettings settings = ComplianceService.get().getFolderSettings(getContainer(), User.getAdminServiceUser());
            PhiColumnBehavior columnBehavior = null==settings ? PhiColumnBehavior.show : settings.getPhiColumnBehavior();
            PHI maxAllowedPhiForExport = PhiColumnBehavior.show == columnBehavior ? PHI.Restricted : ComplianceService.get().getMaxAllowedPhi(getContainer(), getUser());
            form.setExportPhiLevel(maxAllowedPhiForExport);

            return new JspView<>("/org/labkey/core/admin/exportFolder.jsp", form, errors);
        }

        @Override
        public void validateCommand(ExportFolderForm form, Errors errors)
        {
        }

        @Override
        public boolean handlePost(ExportFolderForm form, BindException errors) throws Exception
        {
            Container container = getContainer();
            if (container.isRoot())
            {
                throw new NotFoundException();
            }

            ExportOption exportOption = null;
            if (form.getLocation() >= 0 && form.getLocation() < ExportOption.values().length)
            {
                exportOption = ExportOption.values()[form.getLocation()];
            }
            if (exportOption == null)
            {
                throw new NotFoundException("Invalid export location: " + form.getLocation());
            }
            ContainerManager.checkContainerValidity(container);

            FolderWriterImpl writer = new FolderWriterImpl();
            FolderExportContext ctx = new FolderExportContext(getUser(), container, PageFlowUtil.set(form.getTypes()),
                form.getFormat(), form.isIncludeSubfolders(), form.getExportPhiLevel(), form.isShiftDates(),
                form.isAlternateIds(), form.isMaskClinic(), new StaticLoggerGetter(FolderWriterImpl.LOG));

            AuditTypeEvent event = new AuditTypeEvent(ContainerAuditProvider.CONTAINER_AUDIT_EVENT, container, "Folder export initiated to " + exportOption.getDescription() + " " + (form.isIncludeSubfolders() ? "including" : "excluding") + " subfolders.");
            AuditLogService.get().addEvent(getUser(), event);

            _successURL = exportOption.initiateExport(container, errors, writer, ctx, getViewContext().getResponse());

            return !errors.hasErrors();
        }

        @Override
        public URLHelper getSuccessURL(ExportFolderForm exportFolderForm)
        {
            return _successURL;
        }
    }

    public static class ImportFolderForm
    {
        private boolean _createSharedDatasets;
        private boolean _validateQueries;
        private boolean _failForUndefinedVisits;
        private String _sourceTemplateFolder;
        private String _sourceTemplateFolderId;
        private String _origin;

        public boolean isCreateSharedDatasets()
        {
            return _createSharedDatasets;
        }

        public void setCreateSharedDatasets(boolean createSharedDatasets)
        {
            _createSharedDatasets = createSharedDatasets;
        }

        public boolean isValidateQueries()
        {
            return _validateQueries;
        }

        public boolean isFailForUndefinedVisits()
        {
            return _failForUndefinedVisits;
        }

        public void setFailForUndefinedVisits(boolean failForUndefinedVisits)
        {
            _failForUndefinedVisits = failForUndefinedVisits;
        }

        public void setValidateQueries(boolean validateQueries)
        {
            _validateQueries = validateQueries;
        }

        public String getSourceTemplateFolder()
        {
            return _sourceTemplateFolder;
        }

        @SuppressWarnings("unused")
        public void setSourceTemplateFolder(String sourceTemplateFolder)
        {
            _sourceTemplateFolder = sourceTemplateFolder;
        }

        public String getSourceTemplateFolderId()
        {
            return _sourceTemplateFolderId;
        }

        @SuppressWarnings("unused")
        public void setSourceTemplateFolderId(String sourceTemplateFolderId)
        {
            _sourceTemplateFolderId = sourceTemplateFolderId;
        }

        public String getOrigin()
        {
            return _origin;
        }

        public void setOrigin(String origin)
        {
            _origin = origin;
        }

        public Container getSourceTemplateFolderContainer()
        {
            if (null == getSourceTemplateFolderId())
                return null;
            return ContainerManager.getForId(getSourceTemplateFolderId().replace(',', ' ').trim());
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ImportFolderAction extends FolderManagementViewPostAction<ImportFolderForm>
    {
        private ActionURL _successURL;

        @Override
        protected HttpView<?> getTabView(ImportFolderForm form, boolean reshow, BindException errors)
        {
            // default the createSharedDatasets and validateQueries to true if this is not a form error reshow
            if (!errors.hasErrors())
            {
                form.setCreateSharedDatasets(true);
                form.setValidateQueries(true);
            }

            return new JspView<>("/org/labkey/core/admin/importFolder.jsp", form, errors);
        }

        @Override
        public void validateCommand(ImportFolderForm form, Errors errors)
        {
            // don't allow import into the root container
            if (getContainer().isRoot())
            {
                throw new NotFoundException();
            }
        }

        @Override
        public boolean handlePost(ImportFolderForm form, BindException errors) throws Exception
        {
            ViewContext context = getViewContext();
            ActionURL url = context.getActionURL();
            User user = getUser();
            Container container = getContainer();
            PipeRoot pipelineRoot;
            FileLike pipelineUnzipDir;  // Should be local & writable

            if (form.getOrigin() == null)
            {
                form.setOrigin("Folder");
            }

            // make sure that the pipeline root is valid for this container
            pipelineRoot = PipelineService.get().findPipelineRoot(container);
            if (!PipelineService.get().hasValidPipelineRoot(container) || pipelineRoot == null)
            {
                errors.reject("folderImport", "Pipeline root not set or does not exist on disk.");
                return false;
            }

            // make sure we are able to delete any existing unzip dir in the pipeline root
            try
            {
                pipelineUnzipDir = pipelineRoot.deleteImportDirectory(null);
            }
            catch (DirectoryNotDeletedException e)
            {
                errors.reject("studyImport", "Import failed: Could not delete the directory \"" + PipelineService.UNZIP_DIR + "\"");
                return false;
            }

            FolderImportConfig fiConfig;
            if (!StringUtils.isEmpty(form.getSourceTemplateFolder()))
            {
                fiConfig = getFolderImportConfigFromTemplateFolder(form, pipelineUnzipDir, errors);
                if (fiConfig == null || errors.hasErrors())
                {
                    return false;
                }
            }
            else
            {
                fiConfig = getFolderFromZipArchive(pipelineUnzipDir, errors);
                if (fiConfig == null || errors.hasErrors())
                {
                    return false;
                }
            }

            // get the folder.xml file from the unzipped import archive
            FileLike archiveXml = pipelineUnzipDir.resolveChild("folder.xml");
            if (!archiveXml.exists())
            {
                errors.reject("folderImport", "This archive doesn't contain a folder.xml file.");
                return false;
            }

            ImportOptions options = new ImportOptions(getContainer().getId(), user.getUserId());
            options.setSkipQueryValidation(!form.isValidateQueries());
            options.setCreateSharedDatasets(form.isCreateSharedDatasets());
            options.setFailForUndefinedVisits(form.isFailForUndefinedVisits());
            options.setActivity(ComplianceService.get().getCurrentActivity(getViewContext()));

            // finally, create the study or folder import pipeline job
            _successURL = urlProvider(PipelineUrls.class).urlBegin(container);
            PipelineService.get().runFolderImportJob(container, user, url, archiveXml, fiConfig.originalFileName, pipelineRoot, options);

            return !errors.hasErrors();
        }

        private @Nullable FolderImportConfig getFolderFromZipArchive(FileLike pipelineUnzipDir, BindException errors)
        {
            // user chose to import from a zip file
            Map<String, MultipartFile> map = getFileMap();

            // make sure we have a single file selected for import
            if (map.size() != 1)
            {
                errors.reject("folderImport", "You must select a valid zip archive (folder or study).");
                return null;
            }

            // make sure the file is not empty and that it has a .zip extension
            MultipartFile zipFile = map.values().iterator().next();
            String originalFilename = zipFile.getOriginalFilename();
            if (0 == zipFile.getSize() || isBlank(originalFilename) || !originalFilename.toLowerCase().endsWith(".zip"))
            {
                errors.reject("folderImport", "You must select a valid zip archive (folder or study).");
                return null;
            }

            // copy and unzip the uploaded import archive zip file to the pipeline unzip dir
            try
            {
                FileLike pipelineUnzipFile = pipelineUnzipDir.resolveFile(org.labkey.api.util.Path.parse(originalFilename));
                // Check that the resolved file is under the pipelineUnzipDir
                if (!pipelineUnzipFile.toNioPathForRead().normalize().startsWith(pipelineUnzipDir.toNioPathForRead().normalize()))
                {
                    errors.reject("folderImport", "Invalid file path - must be within the unzip directory");
                    return null;
                }

                FileUtil.createDirectories(pipelineUnzipFile.getParent()); // Non-pipeline import sometimes fails here on Windows (shrug)
                FileUtil.createNewFile(pipelineUnzipFile, true);
                try (OutputStream os = pipelineUnzipFile.openOutputStream())
                {
                    FileUtil.copyData(zipFile.getInputStream(), os);
                }
                ZipUtil.unzipToDirectory(pipelineUnzipFile, pipelineUnzipDir);

                return new FolderImportConfig(
                    false,
                    originalFilename,
                    pipelineUnzipFile,
                    pipelineUnzipFile
                );
            }
            catch (FileNotFoundException e)
            {
                LOG.debug("Failed to import '{}'.", originalFilename, e);
                errors.reject("folderImport", "File not found.");
                return null;
            }
            catch (IOException e)
            {
                LOG.debug("Failed to import '{}'.", originalFilename, e);
                errors.reject("folderImport", "Unable to unzip folder archive.");
                return null;
            }
        }

        private FolderImportConfig getFolderImportConfigFromTemplateFolder(final ImportFolderForm form, final FileLike pipelineUnzipDir, final BindException errors) throws Exception
        {
            // user chose to import from a template source folder
            Container sourceContainer = form.getSourceTemplateFolderContainer();

            if (sourceContainer == null || !sourceContainer.hasPermission(getUser(), AdminPermission.class))
            {
                errors.reject(SpringActionController.ERROR_MSG, "You do not have permission to import from the specified source folder.");
                return null;
            }

            // To support the Advanced import options to import into multiple target folders we need to zip
            // the source template folder so that the zip file can be passed to the pipeline processes.
            FolderExportContext ctx = new FolderExportContext(getUser(), sourceContainer,
                    getRegisteredFolderWritersForImplicitExport(sourceContainer), "new", false,
                    PHI.NotPHI, false, false, false, new StaticLoggerGetter(LogManager.getLogger(FolderWriterImpl.class)));
            FolderWriterImpl writer = new FolderWriterImpl();
            String zipFileName = FileUtil.makeFileNameWithTimestamp(sourceContainer.getName(), "folder.zip");
            FileLike implicitZipFile = pipelineUnzipDir.resolveChild(zipFileName);
            if (!pipelineUnzipDir.isDirectory())
                pipelineUnzipDir.mkdirs();
            implicitZipFile.createFile();
            try (OutputStream out = implicitZipFile.openOutputStream();
                    ZipFile zip = new ZipFile(out, false))
            {
                writer.write(sourceContainer, ctx, zip);
            }
            catch (Container.ContainerException e)
            {
                errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
            }

            // To support the simple import option unzip the zip file to the pipeline unzip dir of the current container
            ZipUtil.unzipToDirectory(implicitZipFile, pipelineUnzipDir);

            return new FolderImportConfig(
                StringUtils.isNotEmpty(form.getSourceTemplateFolderId()),
                implicitZipFile.getName(),
                implicitZipFile,
                null
            );
        }

        private static class FolderImportConfig {
            FileLike pipelineUnzipFile;
            String originalFileName;
            FileLike archiveFile;
            boolean fromTemplateSourceFolder;

            public FolderImportConfig(boolean fromTemplateSourceFolder, String originalFileName, FileLike archiveFile, @Nullable FileLike pipelineUnzipFile)
            {
                this.originalFileName = originalFileName;
                this.archiveFile = archiveFile;
                this.fromTemplateSourceFolder = fromTemplateSourceFolder;
                this.pipelineUnzipFile = pipelineUnzipFile;
            }
        }

        @Override
        public URLHelper getSuccessURL(ImportFolderForm importFolderForm)
        {
            return _successURL;
        }
    }

    private Set<String> getRegisteredFolderWritersForImplicitExport(Container sourceContainer)
    {
        // this method is very similar to CoreController.GetRegisteredFolderWritersAction.execute() method, but instead of
        // of building up a map of Writer object names to display in the UI, we are instead adding them to the list of Writers
        // to apply during the implicit export.
        Set<String> registeredFolderWriters = new HashSet<>();
        FolderSerializationRegistry registry = FolderSerializationRegistry.get();
        if (null == registry)
        {
            throw new RuntimeException();
        }
        Collection<FolderWriter> registeredWriters = registry.getRegisteredFolderWriters();
        for (FolderWriter writer : registeredWriters)
        {
            String dataType = writer.getDataType();
            boolean excludeForDataspace = sourceContainer.isDataspace() && "Study".equals(dataType);
            boolean excludeForTemplate = !writer.includeWithTemplate();

            if (dataType != null && writer.show(sourceContainer) && !excludeForDataspace && !excludeForTemplate)
            {
                registeredFolderWriters.add(dataType);

                // for each Writer also determine if there are related children Writers, if so include them also
                Collection<org.labkey.api.writer.Writer<?, ?>> childWriters = writer.getChildren(true, true);
                if (!childWriters.isEmpty())
                {
                    for (org.labkey.api.writer.Writer<?, ?> child : childWriters)
                    {
                        dataType = child.getDataType();
                        if (dataType != null)
                            registeredFolderWriters.add(dataType);
                    }
                }
            }
        }
        return registeredFolderWriters;
    }

    public static class FolderSettingsForm
    {
        private String _defaultDateFormat;
        private boolean _defaultDateFormatInherited;
        private String _defaultDateTimeFormat;
        private boolean _defaultDateTimeFormatInherited;
        private String _defaultTimeFormat;
        private boolean _defaultTimeFormatInherited;
        private String _defaultNumberFormat;
        private boolean _defaultNumberFormatInherited;
        private boolean _restrictedColumnsEnabled;
        private boolean _restrictedColumnsEnabledInherited;

        public String getDefaultDateFormat()
        {
            return _defaultDateFormat;
        }

        @SuppressWarnings("unused")
        public void setDefaultDateFormat(String defaultDateFormat)
        {
            _defaultDateFormat = defaultDateFormat;
        }

        public boolean isDefaultDateFormatInherited()
        {
            return _defaultDateFormatInherited;
        }

        @SuppressWarnings("unused")
        public void setDefaultDateFormatInherited(boolean defaultDateFormatInherited)
        {
            _defaultDateFormatInherited = defaultDateFormatInherited;
        }

        public String getDefaultDateTimeFormat()
        {
            return _defaultDateTimeFormat;
        }

        @SuppressWarnings("unused")
        public void setDefaultDateTimeFormat(String defaultDateTimeFormat)
        {
            _defaultDateTimeFormat = defaultDateTimeFormat;
        }

        public boolean isDefaultDateTimeFormatInherited()
        {
            return _defaultDateTimeFormatInherited;
        }

        @SuppressWarnings("unused")
        public void setDefaultDateTimeFormatInherited(boolean defaultDateTimeFormatInherited)
        {
            _defaultDateTimeFormatInherited = defaultDateTimeFormatInherited;
        }

        public String getDefaultTimeFormat()
        {
            return _defaultTimeFormat;
        }

        @SuppressWarnings("UnusedDeclaration")
        public void setDefaultTimeFormat(String defaultTimeFormat)
        {
            _defaultTimeFormat = defaultTimeFormat;
        }

        public boolean isDefaultTimeFormatInherited()
        {
            return _defaultTimeFormatInherited;
        }

        @SuppressWarnings("unused")
        public void setDefaultTimeFormatInherited(boolean defaultTimeFormatInherited)
        {
            _defaultTimeFormatInherited = defaultTimeFormatInherited;
        }

        public String getDefaultNumberFormat()
        {
            return _defaultNumberFormat;
        }

        @SuppressWarnings("unused")
        public void setDefaultNumberFormat(String defaultNumberFormat)
        {
            _defaultNumberFormat = defaultNumberFormat;
        }

        public boolean isDefaultNumberFormatInherited()
        {
            return _defaultNumberFormatInherited;
        }

        @SuppressWarnings("unused")
        public void setDefaultNumberFormatInherited(boolean defaultNumberFormatInherited)
        {
            _defaultNumberFormatInherited = defaultNumberFormatInherited;
        }

        public boolean areRestrictedColumnsEnabled()
        {
            return _restrictedColumnsEnabled;
        }

        @SuppressWarnings("unused")
        public void setRestrictedColumnsEnabled(boolean restrictedColumnsEnabled)
        {
            _restrictedColumnsEnabled = restrictedColumnsEnabled;
        }

        public boolean isRestrictedColumnsEnabledInherited()
        {
            return _restrictedColumnsEnabledInherited;
        }

        @SuppressWarnings("unused")
        public void setRestrictedColumnsEnabledInherited(boolean restrictedColumnsEnabledInherited)
        {
            _restrictedColumnsEnabledInherited = restrictedColumnsEnabledInherited;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class FolderSettingsAction extends FolderManagementViewPostAction<FolderSettingsForm>
    {
        @Override
        protected LookAndFeelView getTabView(FolderSettingsForm form, boolean reshow, BindException errors)
        {
            return new LookAndFeelView(errors);
        }

        @Override
        public void validateCommand(FolderSettingsForm form, Errors errors)
        {
        }

        @Override
        public boolean handlePost(FolderSettingsForm form, BindException errors)
        {
            return saveFolderSettings(getContainer(), getUser(), LookAndFeelProperties.getWriteableFolderInstance(getContainer()), form, errors);
        }
    }

    // Validate and populate the folder settings; save & log all changes
    private static boolean saveFolderSettings(Container c, User user, WriteableFolderLookAndFeelProperties props, FolderSettingsForm form, BindException errors)
    {
        validateAndSaveFormat(form.getDefaultDateFormat(), form.isDefaultDateFormatInherited(), props::clearDefaultDateFormat, props::setDefaultDateFormat, errors, "date display format");
        validateAndSaveFormat(form.getDefaultDateTimeFormat(), form.isDefaultDateTimeFormatInherited(), props::clearDefaultDateTimeFormat, props::setDefaultDateTimeFormat, errors, "date-time display format");
        validateAndSaveFormat(form.getDefaultTimeFormat(), form.isDefaultTimeFormatInherited(), props::clearDefaultTimeFormat, props::setDefaultTimeFormat, errors, "time display format");
        validateAndSaveFormat(form.getDefaultNumberFormat(), form.isDefaultNumberFormatInherited(), props::clearDefaultNumberFormat, props::setDefaultNumberFormat, errors, "number display format");

        setProperty(form.isRestrictedColumnsEnabledInherited(), props::clearRestrictedColumnsEnabled, () -> props.setRestrictedColumnsEnabled(form.areRestrictedColumnsEnabled()));

        if (!errors.hasErrors())
        {
            props.save();

            //write an audit log event
            props.writeAuditLogEvent(c, user);
        }

        return !errors.hasErrors();
    }

    private interface FormatSaver
    {
        void save(String format) throws IllegalArgumentException;
    }

    private static void validateAndSaveFormat(String format, boolean inherited, Runnable clearer, FormatSaver saver, BindException errors, String what)
    {
        String defaultFormat = StringUtils.trimToNull(format);
        if (inherited)
        {
            clearer.run();
        }
        else
        {
            try
            {
                saver.save(defaultFormat);
            }
            catch (IllegalArgumentException e)
            {
                errors.reject(ERROR_MSG, "Invalid " + what + ": " + e.getMessage());
            }
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class ModulePropertiesAction extends FolderManagementViewAction
    {
        @Override
        protected JspView<?> getTabView()
        {
            return new JspView<>("/org/labkey/core/project/modulePropertiesAdmin.jsp");
        }
    }

    @SuppressWarnings("unused")
    public static class FolderTypeForm
    {
        private String[] _activeModules = new String[ModuleLoader.getInstance().getModules().size()];
        private String _defaultModule;
        private String _folderType;
        private boolean _wizard;

        public String[] getActiveModules()
        {
            return _activeModules;
        }

        public void setActiveModules(String[] activeModules)
        {
            _activeModules = activeModules;
        }

        public String getDefaultModule()
        {
            return _defaultModule;
        }

        public void setDefaultModule(String defaultModule)
        {
            _defaultModule = defaultModule;
        }

        public String getFolderType()
        {
            return _folderType;
        }

        public void setFolderType(String folderType)
        {
            _folderType = folderType;
        }

        public boolean isWizard()
        {
            return _wizard;
        }

        public void setWizard(boolean wizard)
        {
            _wizard = wizard;
        }
    }

    @RequiresPermission(AdminPermission.class)
    @IgnoresTermsOfUse  // At the moment, compliance configuration is very sensitive to active modules, so allow those adjustments
    public static class FolderTypeAction extends FolderManagementViewPostAction<FolderTypeForm>
    {
        private ActionURL _successURL = null;

        @Override
        protected JspView<?> getTabView(FolderTypeForm form, boolean reshow, BindException errors)
        {
            return new JspView<>("/org/labkey/core/admin/folderType.jsp", form, errors);
        }

        @Override
        public void validateCommand(FolderTypeForm form, Errors errors)
        {
            boolean fEmpty = true;
            for (String module : form._activeModules)
            {
                if (module != null)
                {
                    fEmpty = false;
                    break;
                }
            }
            if (fEmpty && "None".equals(form.getFolderType()))
            {
                errors.reject(SpringActionController.ERROR_MSG, "Error: Please select at least one module to display.");
            }
        }

        @Override
        public boolean handlePost(FolderTypeForm form, BindException errors)
        {
            Container container = getContainer();
            if (container.isRoot())
            {
                throw new NotFoundException();
            }

            String[] modules = form.getActiveModules();

            if (modules.length == 0)
            {
                errors.reject(null, "At least one module must be selected");
                return false;
            }

            Set<Module> activeModules = new HashSet<>();
            for (String moduleName : modules)
            {
                Module module = ModuleLoader.getInstance().getModule(moduleName);
                if (module != null)
                    activeModules.add(module);
            }

            if (null == StringUtils.trimToNull(form.getFolderType()) || FolderType.NONE.getName().equals(form.getFolderType()))
            {
                container.setFolderType(FolderType.NONE, getUser(), errors, activeModules);
                Module defaultModule = ModuleLoader.getInstance().getModule(form.getDefaultModule());
                container.setDefaultModule(defaultModule);
            }
            else
            {
                FolderType folderType = FolderTypeManager.get().getFolderType(form.getFolderType());
                if (container.isContainerTab() && folderType.hasContainerTabs())
                    errors.reject(null, "You cannot set a tab folder to a folder type that also has tab folders");
                else
                    container.setFolderType(folderType, getUser(), errors, activeModules);
            }
            if (errors.hasErrors())
                return false;

            if (form.isWizard())
            {
                _successURL = urlProvider(SecurityUrls.class).getContainerURL(container);
                _successURL.addParameter("wizard", Boolean.TRUE.toString());
            }
            else
                _successURL = container.getFolderType().getStartURL(container, getUser());

            return true;
        }

        @Override
        public URLHelper getSuccessURL(FolderTypeForm folderTypeForm)
        {
            return _successURL;
        }
    }

    @SuppressWarnings("unused")
    public static class FileRootsForm extends SetupForm implements FileManagementForm
    {
        private String _folderRootPath;
        private String _fileRootOption;
        private String _cloudRootName;
        private boolean _isFolderSetup;
        private boolean _fileRootChanged;
        private boolean _enabledCloudStoresChanged;
        private String _migrateFilesOption;

        // cloud settings
        private String[] _enabledCloudStore;
        //file management
        @Override
        public String getFolderRootPath()
        {
            return _folderRootPath;
        }

        @Override
        public void setFolderRootPath(String folderRootPath)
        {
            _folderRootPath = folderRootPath;
        }

        @Override
        public String getFileRootOption()
        {
            return _fileRootOption;
        }

        @Override
        public void setFileRootOption(String fileRootOption)
        {
            _fileRootOption = fileRootOption;
        }

        @Override
        public String[] getEnabledCloudStore()
        {
            return _enabledCloudStore;
        }

        @Override
        public void setEnabledCloudStore(String[] enabledCloudStore)
        {
            _enabledCloudStore = enabledCloudStore;
        }

        @Override
        public boolean isDisableFileSharing()
        {
            return FileRootProp.disable.name().equals(getFileRootOption());
        }

        @Override
        public boolean hasSiteDefaultRoot()
        {
            return FileRootProp.siteDefault.name().equals(getFileRootOption());
        }

        @Override
        public boolean isCloudFileRoot()
        {
            return FileRootProp.cloudRoot.name().equals(getFileRootOption());
        }

        @Override
        @Nullable
        public String getCloudRootName()
        {
            return _cloudRootName;
        }

        @Override
        public void setCloudRootName(String cloudRootName)
        {
            _cloudRootName = cloudRootName;
        }

        @Override
        public boolean isFolderSetup()
        {
            return _isFolderSetup;
        }

        public void setFolderSetup(boolean folderSetup)
        {
            _isFolderSetup = folderSetup;
        }

        public boolean isFileRootChanged()
        {
            return _fileRootChanged;
        }

        @Override
        public void setFileRootChanged(boolean changed)
        {
            _fileRootChanged = changed;
        }

        public boolean isEnabledCloudStoresChanged()
        {
            return _enabledCloudStoresChanged;
        }

        @Override
        public void setEnabledCloudStoresChanged(boolean enabledCloudStoresChanged)
        {
            _enabledCloudStoresChanged = enabledCloudStoresChanged;
        }

        @Override
        public String getMigrateFilesOption()
        {
            return _migrateFilesOption;
        }

        @Override
        public void setMigrateFilesOption(String migrateFilesOption)
        {
            _migrateFilesOption = migrateFilesOption;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class FileRootsStandAloneAction extends FormViewAction<FileRootsForm>
    {
        @Override
        public ModelAndView getView(FileRootsForm form, boolean reShow, BindException errors)
        {
            JspView<?> view = getFileRootsView(form, errors, getReshow());
            view.setFrame(WebPartView.FrameType.NONE);

            getPageConfig().setNavTrail(ContainerManager.getCreateContainerWizardSteps(getContainer(), getContainer().getParent()));
            getPageConfig().setTemplate(PageConfig.Template.Wizard);
            getPageConfig().setTitle("Change File Root");
            return view;
        }

        @Override
        public void validateCommand(FileRootsForm form, Errors errors)
        {
            validateCloudFileRoot(form, getContainer(), errors);
        }

        @Override
        public boolean handlePost(FileRootsForm form, BindException errors) throws Exception
        {
            return handleFileRootsPost(form, errors);
        }

        @Override
        public ActionURL getSuccessURL(FileRootsForm form)
        {
            ActionURL url = new ActionURL(FileRootsStandAloneAction.class, getContainer())
                    .addParameter("folderSetup", true)
                    .addReturnUrl(getViewContext().getActionURL().getReturnUrl());

            if (form.isFileRootChanged())
                url.addParameter("rootSet", form.getMigrateFilesOption());
            if (form.isEnabledCloudStoresChanged())
                url.addParameter("cloudChanged", true);
            return url;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    /**
     * This standalone file root management action can be used on folder types that do not support
     * the normal 'Manage Folder' UI. Not currently linked in the UI, but available for direct URL
     * navigation when a workbook needs it.
     */
    @RequiresPermission(AdminPermission.class)
    public class ManageFileRootAction extends FormViewAction<FileRootsForm>
    {
        @Override
        public ModelAndView getView(FileRootsForm form, boolean reShow, BindException errors)
        {
            JspView<?> view = getFileRootsView(form, errors, getReshow());
            getPageConfig().setTitle("Manage File Root");
            return view;
        }

        @Override
        public void validateCommand(FileRootsForm form, Errors errors)
        {
            validateCloudFileRoot(form, getContainer(), errors);
        }

        @Override
        public boolean handlePost(FileRootsForm form, BindException errors) throws Exception
        {
            return handleFileRootsPost(form, errors);
        }

        @Override
        public ActionURL getSuccessURL(FileRootsForm form)
        {
            ActionURL url = getContainer().getStartURL(getUser());

            if (getViewContext().getActionURL().getReturnUrl() != null)
            {
                url.addReturnUrl(getViewContext().getActionURL().getReturnUrl());
            }

            return url;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class FileRootsAction extends FolderManagementViewPostAction<FileRootsForm>
    {
        @Override
        protected HttpView<?> getTabView(FileRootsForm form, boolean reshow, BindException errors)
        {
            return getFileRootsView(form, errors, getReshow());
        }

        @Override
        public void validateCommand(FileRootsForm form, Errors errors)
        {
            validateCloudFileRoot(form, getContainer(), errors);
        }

        @Override
        public boolean handlePost(FileRootsForm form, BindException errors) throws Exception
        {
            return handleFileRootsPost(form, errors);
        }

        @Override
        public ActionURL getSuccessURL(FileRootsForm form)
        {
            ActionURL url = new AdminController.AdminUrlsImpl().getFileRootsURL(getContainer());

            if (form.isFileRootChanged())
                url.addParameter("rootSet", form.getMigrateFilesOption());
            if (form.isEnabledCloudStoresChanged())
                url.addParameter("cloudChanged", true);
            return url;
        }
    }

    private JspView<?> getFileRootsView(FileRootsForm form, BindException errors, boolean reshow)
    {
        JspView<?> view = new JspView<>("/org/labkey/core/admin/view/filesProjectSettings.jsp", form, errors);
        String title = "Configure File Root";
        if (CloudStoreService.get() != null)
            title += " And Enable Cloud Stores";
        view.setTitle(title);
        view.setFrame(WebPartView.FrameType.DIV);
        try
        {
            if (!reshow)
                setFormAndConfirmMessage(getViewContext(), form);
        }
        catch (IllegalArgumentException e)
        {
            errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
        }

        return view;
    }

    private boolean handleFileRootsPost(FileRootsForm form, BindException errors) throws Exception
    {
        if (form.isPipelineRootForm())
        {
            return PipelineService.get().savePipelineSetup(getViewContext(), form, errors);
        }
        else
        {
            setFileRootFromForm(getViewContext(), form, errors);
            setEnabledCloudStores(getViewContext(), form, errors);
            return !errors.hasErrors();
        }
    }

    public static void validateCloudFileRoot(FileManagementForm form, Container container, Errors errors)
    {
        FileContentService service = FileContentService.get();
        if (null != service)
        {
            boolean isOrDefaultsToCloudRoot = form.isCloudFileRoot();
            String cloudRootName = form.getCloudRootName();
            if (!isOrDefaultsToCloudRoot && form.hasSiteDefaultRoot())
            {
                Path defaultRootPath = service.getDefaultRootPath(container, false);
                cloudRootName = service.getDefaultRootInfo(container).getCloudName();
                isOrDefaultsToCloudRoot = (null != defaultRootPath && FileUtil.hasCloudScheme(defaultRootPath));
            }

            if (isOrDefaultsToCloudRoot && null != cloudRootName)
            {
                if (null != form.getEnabledCloudStore())
                {
                    for (String storeName : form.getEnabledCloudStore())
                    {
                        if (Strings.CI.equals(cloudRootName, storeName))
                            return;
                    }
                }
                // Didn't find cloud root in enabled list
                errors.reject(ERROR_MSG, "Cannot disable cloud store used as File Root.");
            }
        }
    }

    public static void setFileRootFromForm(ViewContext ctx, FileManagementForm form, BindException errors)
    {
        boolean changed = false;
        boolean shouldCopyMove = false;
        FileContentService service = FileContentService.get();
        if (null != service)
        {
            // If we need to copy/move files based on the FileRoot change, we need to check children that use the default and move them, too.
            // And we need to capture the source roots for each of those, because changing this parent file root changes the child source roots.
            MigrateFilesOption migrateFilesOption = null != form.getMigrateFilesOption() ?
                    MigrateFilesOption.valueOf(form.getMigrateFilesOption()) :
                    MigrateFilesOption.leave;
            List<Pair<Container, String>> sourceInfos =
                    ((MigrateFilesOption.leave.equals(migrateFilesOption) && !form.isFolderSetup()) || form.isDisableFileSharing()) ?
                            Collections.emptyList() :
                            getCopySourceInfo(service, ctx.getContainer());

            if (form.isDisableFileSharing())
            {
                if (!service.isFileRootDisabled(ctx.getContainer()))
                {
                    service.disableFileRoot(ctx.getContainer());
                    changed = true;
                }
            }
            else if (form.hasSiteDefaultRoot())
            {
                if (service.isFileRootDisabled(ctx.getContainer()) || !service.isUseDefaultRoot(ctx.getContainer()))
                {
                    service.setIsUseDefaultRoot(ctx.getContainer(), true);
                    changed = true;
                    shouldCopyMove = true;
                }
            }
            else if (form.isCloudFileRoot())
            {
                throwIfUnauthorizedFileRootChange(ctx, service, form);
                String cloudRootName = form.getCloudRootName();
                if (null != cloudRootName &&
                        (!service.isCloudRoot(ctx.getContainer()) ||
                                !cloudRootName.equalsIgnoreCase(service.getCloudRootName(ctx.getContainer()))))
                {
                    service.setIsUseDefaultRoot(ctx.getContainer(), false);
                    service.setCloudRoot(ctx.getContainer(), cloudRootName);
                    PipelineService.get().setPipelineRoot(ctx.getUser(), ctx.getContainer(), PipelineService.PRIMARY_ROOT, false);
                    if (form.isFolderSetup() && !sourceInfos.isEmpty())
                    {
                        // File root was set to cloud storage, remove folder created
                        Path fromPath = FileUtil.stringToPath(sourceInfos.getFirst().first, sourceInfos.getFirst().second);    // sourceInfos paths should be encoded
                        if (FileContentService.FILES_LINK.equals(FileUtil.getFileName(fromPath)))
                        {
                            try
                            {
                                Files.deleteIfExists(fromPath.getParent());
                            }
                            catch (IOException e)
                            {
                                LOG.warn("Could not delete directory '{}'", FileUtil.pathToString(fromPath.getParent()));
                            }
                        }
                    }
                    changed = true;
                    shouldCopyMove = true;
                }
            }
            else
            {
                throwIfUnauthorizedFileRootChange(ctx, service, form);
                String root = StringUtils.trimToNull(form.getFolderRootPath());
                if (root != null)
                {
                    URI uri = FileUtil.createUri(root, false);          // root is unencoded
                    Path path = FileUtil.getPath(ctx.getContainer(), uri);
                    if (null == path || !Files.exists(path))
                    {
                        errors.reject(ERROR_MSG, "File root '" + root + "' does not appear to be a valid directory accessible to the server at " + ctx.getRequest().getServerName() + ".");
                    }
                    else
                    {
                        Path currentFileRootPath = service.getFileRootPath(ctx.getContainer());
                        if (null == currentFileRootPath || !root.equalsIgnoreCase(currentFileRootPath.toAbsolutePath().toString()))
                        {
                            service.setIsUseDefaultRoot(ctx.getContainer(), false);
                            service.setFileRootPath(ctx.getContainer(), root);
                            changed = true;
                            shouldCopyMove = true;
                        }
                    }
                }
                else
                {
                    service.setFileRootPath(ctx.getContainer(), null);
                    changed = true;
                }
            }

            if (!errors.hasErrors())
            {
                if (changed && shouldCopyMove && !MigrateFilesOption.leave.equals(migrateFilesOption))
                {
                    // Make sure we have pipeRoot before starting jobs, even though each subfolder needs to get its own
                    PipeRoot pipeRoot = PipelineService.get().findPipelineRoot(ctx.getContainer());
                    if (null != pipeRoot)
                    {
                        try
                        {
                            initiateCopyFilesPipelineJobs(ctx, sourceInfos, pipeRoot, migrateFilesOption);
                        }
                        catch (PipelineValidationException e)
                        {
                            throw new RuntimeValidationException(e);
                        }
                    }
                    else
                    {
                        LOG.warn("Change File Root: Can't copy or move files with no pipeline root");
                    }
                }

                form.setFileRootChanged(changed);
                if (changed && null != ctx.getUser())
                {
                    setFormAndConfirmMessage(ctx.getContainer(), form, true, false, migrateFilesOption.name());
                    String comment = (ctx.getContainer().isProject() ? "Project " : "Folder ") + ctx.getContainer().getPath() + ": " + form.getConfirmMessage();
                    AuditTypeEvent event = new AuditTypeEvent(ContainerAuditProvider.CONTAINER_AUDIT_EVENT, ctx.getContainer(), comment);
                    AuditLogService.get().addEvent(ctx.getUser(), event);
                }
            }
        }
    }

    private static List<Pair<Container, String>> getCopySourceInfo(FileContentService service, Container container)
    {

        List<Pair<Container, String>> sourceInfo = new ArrayList<>();
        addCopySourceInfo(service, container, sourceInfo, true);
        return sourceInfo;
    }

    private static void addCopySourceInfo(FileContentService service, Container container, List<Pair<Container, String>> sourceInfo, boolean isRoot)
    {
        if (isRoot || service.isUseDefaultRoot(container))
        {
            Path sourceFileRootDir = service.getFileRootPath(container, FileContentService.ContentType.files);
            if (null != sourceFileRootDir)
            {
                String pathStr = FileUtil.pathToString(sourceFileRootDir);
                if (null != pathStr)
                    sourceInfo.add(new Pair<>(container, pathStr));
                else
                    throw new RuntimeValidationException("Unexpected error converting path to string");
            }
        }
        for (Container childContainer : container.getChildren())
            addCopySourceInfo(service, childContainer, sourceInfo, false);
    }

    private static void initiateCopyFilesPipelineJobs(ViewContext ctx, @NotNull List<Pair<Container, String>> sourceInfos, PipeRoot pipeRoot,
                                                      MigrateFilesOption migrateFilesOption) throws PipelineValidationException
    {
        CopyFileRootPipelineJob job = new CopyFileRootPipelineJob(ctx.getContainer(), ctx.getUser(), sourceInfos, pipeRoot, migrateFilesOption);
        PipelineService.get().queueJob(job);
    }

    private static void throwIfUnauthorizedFileRootChange(ViewContext ctx, FileContentService service, FileManagementForm form)
    {
        // test permissions. only site admins are able to turn on a custom file root for a folder
        // this is only relevant if the folder is either being switched to a custom file root,
        // or if the file root is changed.
        if (!service.isUseDefaultRoot(ctx.getContainer()))
        {
            Path fileRootPath = service.getFileRootPath(ctx.getContainer());
            if (null != fileRootPath)
            {
                String absolutePath = FileUtil.getAbsolutePath(ctx.getContainer(), fileRootPath);
                if (Strings.CI.equals(absolutePath, form.getFolderRootPath()))
                {
                    if (!ctx.getUser().hasRootPermission(AdminOperationsPermission.class))
                        throw new UnauthorizedException("Only site admins can change file roots");
                }
            }
        }
    }

    public static void setEnabledCloudStores(ViewContext ctx, FileManagementForm form, BindException errors)
    {
        String[] enabledCloudStores = form.getEnabledCloudStore();
        CloudStoreService cloud = CloudStoreService.get();
        if (cloud != null)
        {
            Set<String> enabled = Collections.emptySet();
            if (enabledCloudStores != null)
                enabled = new HashSet<>(Arrays.asList(enabledCloudStores));

            try
            {
                // Check if anything changed
                boolean changed = false;
                Collection<String> storeNames = cloud.getEnabledCloudStores(ctx.getContainer());
                if (enabled.size() != storeNames.size())
                    changed = true;
                else
                    if (!enabled.containsAll(storeNames))
                        changed = true;
                if (changed)
                    cloud.setEnabledCloudStores(ctx.getContainer(), enabled);
                form.setEnabledCloudStoresChanged(changed);
            }
            catch (UncheckedExecutionException e)
            {
                LOG.debug("Failed to configure cloud store(s).", e);
                // UncheckedExecutionException with cause org.jclouds.blobstore.ContainerNotFoundException
                // is what BlobStore hands us if bucket (S3 container) does not exist
                if (null != e.getCause())
                    errors.reject(ERROR_MSG, e.getCause().getMessage());
                else
                    throw e;
            }
            catch (RuntimeException e)
            {
                LOG.debug("Failed to configure cloud store(s).", e);
                errors.reject(ERROR_MSG, e.getMessage());
            }
        }
    }


    public static void setFormAndConfirmMessage(ViewContext ctx, FileManagementForm form) throws IllegalArgumentException
    {
        String rootSetParam = ctx.getActionURL().getParameter("rootSet");
        boolean fileRootChanged = null != rootSetParam && !"false".equalsIgnoreCase(rootSetParam);
        String cloudChangedParam = ctx.getActionURL().getParameter("cloudChanged");
        boolean enabledCloudChanged = "true".equalsIgnoreCase(cloudChangedParam);
        setFormAndConfirmMessage(ctx.getContainer(), form, fileRootChanged, enabledCloudChanged, rootSetParam);
    }

    public static void setFormAndConfirmMessage(Container container, FileManagementForm form, boolean fileRootChanged, boolean enabledCloudChanged,
                                                String migrateFilesOption) throws IllegalArgumentException
    {
        FileContentService service = FileContentService.get();
        String confirmMessage = null;

        String migrateFilesMessage = "";
        if (fileRootChanged && !form.isFolderSetup())
        {
            if (MigrateFilesOption.leave.name().equals(migrateFilesOption))
                migrateFilesMessage = ". Existing files not copied or moved.";
            else if (MigrateFilesOption.copy.name().equals(migrateFilesOption))
            {
                migrateFilesMessage = ". Existing files copied.";
                form.setMigrateFilesOption(migrateFilesOption);
            }
            else if (MigrateFilesOption.move.name().equals(migrateFilesOption))
            {
                migrateFilesMessage = ". Existing files moved.";
                form.setMigrateFilesOption(migrateFilesOption);
            }
        }

        if (service != null)
        {
            if (service.isFileRootDisabled(container))
            {
                form.setFileRootOption(FileRootProp.disable.name());
                if (fileRootChanged)
                    confirmMessage = "File sharing has been disabled for this " + container.getContainerNoun();
            }
            else if (service.isUseDefaultRoot(container))
            {
                form.setFileRootOption(FileRootProp.siteDefault.name());
                Path root = service.getFileRootPath(container);
                if (root != null && Files.exists(root) && fileRootChanged)
                    confirmMessage = "The file root is set to a default of: " + FileUtil.getAbsolutePath(container, root) + migrateFilesMessage;
            }
            else if (!service.isCloudRoot(container))
            {
                Path root = service.getFileRootPath(container);

                form.setFileRootOption(FileRootProp.folderOverride.name());
                if (root != null)
                {
                    String absolutePath = FileUtil.getAbsolutePath(container, root);
                    form.setFolderRootPath(absolutePath);
                    if (Files.exists(root))
                    {
                        if (fileRootChanged)
                            confirmMessage = "The file root is set to: " + absolutePath + migrateFilesMessage;
                    }
                }
            }
            else
            {
                form.setFileRootOption(FileRootProp.cloudRoot.name());
                form.setCloudRootName(service.getCloudRootName(container));
                Path root = service.getFileRootPath(container);
                if (root != null && fileRootChanged)
                {
                    confirmMessage = "The file root is set to: " + FileUtil.getCloudRootPathString(form.getCloudRootName()) + migrateFilesMessage;
                }
            }
        }

        if (fileRootChanged && confirmMessage != null)
            form.setConfirmMessage(confirmMessage);
        else if (enabledCloudChanged)
            form.setConfirmMessage("The enabled cloud stores changed.");
    }

    @RequiresPermission(AdminPermission.class)
    public static class ManageFoldersAction extends FolderManagementViewAction
    {
        @Override
        protected HttpView<?> getTabView()
        {
            return new JspView<>("/org/labkey/core/admin/manageFolders.jsp");
        }
    }

    public static class NotificationsForm
    {
        private String _provider;

        public String getProvider()
        {
            return _provider;
        }

        public void setProvider(String provider)
        {
            _provider = provider;
        }
    }

    private static final String DATA_REGION_NAME = "Users";

    @RequiresPermission(AdminPermission.class)
    public static class NotificationsAction extends FolderManagementViewPostAction<NotificationsForm>
    {
        @Override
        protected HttpView<?> getTabView(NotificationsForm form, boolean reshow, BindException errors)
        {
            final String key = DataRegionSelection.getSelectionKey("core", CoreQuerySchema.USERS_MSG_SETTINGS_TABLE_NAME, null, DATA_REGION_NAME);
            DataRegionSelection.clearAll(getViewContext(), key);

            QuerySettings settings = new QuerySettings(getViewContext(), DATA_REGION_NAME, CoreQuerySchema.USERS_MSG_SETTINGS_TABLE_NAME);
            settings.setAllowChooseView(true);
            settings.getBaseSort().insertSortColumn(FieldKey.fromParts("DisplayName"));

            UserSchema schema = QueryService.get().getUserSchema(getViewContext().getUser(), getViewContext().getContainer(), SchemaKey.fromParts(CoreQuerySchema.NAME));
            QueryView queryView = new QueryView(schema, settings, errors)
            {
                @Override
                public List<DisplayColumn> getDisplayColumns()
                {
                    List<DisplayColumn> columns = new ArrayList<>();
                    SecurityPolicy policy = getContainer().getPolicy();
                    Set<String> assignmentSet = new HashSet<>();

                    for (RoleAssignment assignment : policy.getAssignments())
                    {
                        Group g = SecurityManager.getGroup(assignment.getUserId());
                        if (g != null)
                            assignmentSet.add(g.getName());
                    }

                    for (DisplayColumn col : super.getDisplayColumns())
                    {
                        if (col.getName().equalsIgnoreCase("Groups"))
                            columns.add(new FolderGroupColumn(assignmentSet, col.getColumnInfo()));
                        else
                            columns.add(col);
                    }
                    return columns;
                }

                @Override
                protected void populateButtonBar(DataView dataView, ButtonBar bar)
                {
                    try
                    {
                        // add the provider configuration menu items to the admin panel button
                        MenuButton adminButton = new MenuButton("Update user settings");
                        adminButton.setRequiresSelection(true);
                        for (ConfigTypeProvider provider : MessageConfigService.get().getConfigTypes())
                            adminButton.addMenuItem("For " + provider.getName().toLowerCase(), "userSettings_"+provider.getName()+"(LABKEY.DataRegions.Users.getSelectionCount())" );

                        bar.add(adminButton);
                        super.populateButtonBar(dataView, bar);
                    }
                    catch (Exception e)
                    {
                        throw new RuntimeException(e);
                    }
                }
            };
            queryView.setShadeAlternatingRows(true);
            queryView.setShowBorders(true);
            queryView.setShowDetailsColumn(false);
            queryView.setShowRecordSelectors(true);
            queryView.setFrame(WebPartView.FrameType.NONE);
            queryView.disableContainerFilterSelection();
            queryView.setButtonBarPosition(DataRegion.ButtonBarPosition.TOP);

            VBox defaultsView = new VBox(
                HtmlView.unsafe(
                    "<div class=\"labkey-announcement-title\"><span>Default settings</span></div><div class=\"labkey-title-area-line\"></div>" +
                        "You can change this folder's default settings for email notifications here.")
            );

            PanelConfig config = new PanelConfig(getViewContext().getActionURL().clone(), key);
            for (ConfigTypeProvider provider : MessageConfigService.get().getConfigTypes())
            {
                defaultsView.addView(new JspView<>("/org/labkey/core/admin/view/notifySettings.jsp", provider.createConfigForm(getViewContext(), config)));
            }

            return new VBox(
                new JspView<>("/org/labkey/core/admin/view/folderSettingsHeader.jsp", null, errors),
                defaultsView,
                new VBox(
                    HtmlView.unsafe(
                        "<div class='labkey-announcement-title'><span>User settings</span></div><div class='labkey-title-area-line'></div>" +
                            "The list below contains all users with read access to this folder who are able to receive notifications. Each user's current<br/>" +
                            "notification setting is visible in the appropriately named column.<br/><br/>" +
                            "To bulk edit individual settings: select one or more users, click the 'Update user settings' menu, and select the notification type."),
                    queryView
                )
            );
        }

        @Override
        public void validateCommand(NotificationsForm form, Errors errors)
        {
            ConfigTypeProvider provider = MessageConfigService.get().getConfigType(form.getProvider());

            if (provider != null)
                provider.validateCommand(getViewContext(), errors);
        }

        @Override
        public boolean handlePost(NotificationsForm form, BindException errors) throws Exception
        {
            ConfigTypeProvider provider = MessageConfigService.get().getConfigType(form.getProvider());

            if (provider != null)
            {
                return provider.handlePost(getViewContext(), errors);
            }
            errors.reject(SpringActionController.ERROR_MSG, "Unable to find the selected config provider");
            return false;
        }
    }

    public static class NotifyOptionsForm
    {
        private String _type;

        public String getType()
        {
            return _type;
        }

        public void setType(String type)
        {
            _type = type;
        }

        public ConfigTypeProvider getProvider()
        {
            return MessageConfigService.get().getConfigType(getType());
        }
    }

    /**
     * Action to populate an Ext store with email notification options for admin settings
     */
    @RequiresPermission(AdminPermission.class)
    public static class GetEmailOptionsAction extends ReadOnlyApiAction<NotifyOptionsForm>
    {
        @Override
        public ApiResponse execute(NotifyOptionsForm form, BindException errors)
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();

            ConfigTypeProvider provider = form.getProvider();
            if (provider != null)
            {
                List<Map<?, ?>> options = new ArrayList<>();

                // if the list of options is not for the folder default, add an option to use the folder default
                if (getViewContext().get("isDefault") == null)
                    options.add(PageFlowUtil.map("id", -1, "label", "Folder default"));

                for (NotificationOption option : provider.getOptions())
                {
                    options.add(PageFlowUtil.map("id", option.getEmailOptionId(), "label", option.getEmailOption()));
                }
                resp.put("success", true);
                if (!options.isEmpty())
                    resp.put("options", options);
            }
            else
                resp.put("success", false);

            return resp;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class SetBulkEmailOptionsAction extends MutatingApiAction<EmailConfigFormImpl>
    {
        @Override
        public ApiResponse execute(EmailConfigFormImpl form, BindException errors)
        {
            ApiSimpleResponse resp = new ApiSimpleResponse();
            ConfigTypeProvider provider = form.getProvider();
            String srcIdentifier = getContainer().getId();

            Set<String> selections = DataRegionSelection.getSelected(getViewContext(), form.getDataRegionSelectionKey(), true);

            if (!selections.isEmpty() && provider != null)
            {
                int newOption = form.getIndividualEmailOption();

                for (String user : selections)
                {
                    User projectUser = UserManager.getUser(Integer.parseInt(user));
                    UserPreference pref = provider.getPreference(getContainer(), projectUser, srcIdentifier);

                    int currentEmailOption = pref != null ? pref.getEmailOptionId() : -1;

                    //has this projectUser's option changed? if so, update
                    //creating new record in EmailPrefs table if there isn't one, or deleting if set back to folder default
                    if (currentEmailOption != newOption)
                    {
                        provider.savePreference(getUser(), getContainer(), projectUser, newOption, srcIdentifier);
                    }
                }
                resp.put("success", true);
            }
            else
            {
                resp.put("success", false);
                resp.put("message", "There were no users selected");
            }
            return resp;
        }
    }

    /** Renders only the groups that are assigned roles in this container */
    private static class FolderGroupColumn extends DataColumn
    {
        private final Set<String> _assignmentSet;

        public FolderGroupColumn(Set<String> assignmentSet, ColumnInfo col)
        {
            super(col);
            _assignmentSet = assignmentSet;
        }

        @Override
        public void renderGridCellContents(RenderContext ctx, HtmlWriter out)
        {
            String value = (String)ctx.get(getBoundColumn().getDisplayField().getFieldKey());

            if (value != null)
            {
                out.write(Arrays.stream(value.split(VALUE_DELIMITER_REGEX))
                    .filter(_assignmentSet::contains)
                    .map(HtmlString::of)
                    .collect(LabKeyCollectors.joining(HtmlString.unsafe(",<br>"))));
            }
        }
    }

    private static class PanelConfig implements MessageConfigService.PanelInfo
    {
        private final ActionURL _returnUrl;
        private final String _dataRegionSelectionKey;

        public PanelConfig(ActionURL returnUrl, String selectionKey)
        {
            _returnUrl = returnUrl;
            _dataRegionSelectionKey = selectionKey;
        }

        @Override
        public ActionURL getReturnUrl()
        {
            return _returnUrl;
        }

        @Override
        public String getDataRegionSelectionKey()
        {
            return _dataRegionSelectionKey;
        }
    }

    public static class ConceptsForm
    {
        private String _conceptURI;
        private String _containerId;
        private String _schemaName;
        private String _queryName;

        public String getConceptURI()
        {
            return _conceptURI;
        }

        public void setConceptURI(String conceptURI)
        {
            _conceptURI = conceptURI;
        }

        public String getContainerId()
        {
            return _containerId;
        }

        public void setContainerId(String containerId)
        {
            _containerId = containerId;
        }

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

    @RequiresPermission(AdminPermission.class)
    public static class ConceptsAction extends FolderManagementViewPostAction<ConceptsForm>
    {
        @Override
        protected HttpView<?> getTabView(ConceptsForm form, boolean reshow, BindException errors)
        {
            return new JspView<>("/org/labkey/core/admin/manageConcepts.jsp", form, errors);
        }

        @Override
        public void validateCommand(ConceptsForm form, Errors errors)
        {
            // validate that the required input fields are provided
            String missingRequired = "", sep = "";
            if (form.getConceptURI() == null)
            {
                missingRequired += "conceptURI";
                sep = ", ";
            }
            if (form.getSchemaName() == null)
            {
                missingRequired += sep + "schemaName";
                sep = ", ";
            }
            if (form.getQueryName() == null)
                missingRequired += sep + "queryName";
            if (!missingRequired.isEmpty())
                errors.reject(SpringActionController.ERROR_MSG, "Missing required field(s): " + missingRequired + ".");

            // validate that, if provided, the containerId matches an existing container
            Container postContainer = null;
            if (form.getContainerId() != null)
            {
                postContainer = ContainerManager.getForId(form.getContainerId());
                if (postContainer == null)
                    errors.reject(SpringActionController.ERROR_MSG, "Container does not exist for containerId provided.");
            }

            // validate that the schema and query names provided exist
            if (form.getSchemaName() != null && form.getQueryName() != null)
            {
                Container c = postContainer != null ? postContainer : getContainer();
                UserSchema schema = QueryService.get().getUserSchema(getUser(), c, form.getSchemaName());
                if (schema == null)
                    errors.reject(SpringActionController.ERROR_MSG, "UserSchema '" + form.getSchemaName() + "' not found.");
                else if (schema.getTable(form.getQueryName()) == null)
                    errors.reject(SpringActionController.ERROR_MSG, "Table '" + form.getSchemaName() + "." + form.getQueryName() + "' not found.");
            }
        }

        @Override
        public boolean handlePost(ConceptsForm form, BindException errors)
        {
            Lookup lookup = new Lookup(ContainerManager.getForId(form.getContainerId()), form.getSchemaName(), form.getQueryName());
            ConceptURIProperties.setLookup(getContainer(), form.getConceptURI(), lookup);

            return true;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class FolderAliasesAction extends FormViewAction<FolderAliasesForm>
    {
        @Override
        public void validateCommand(FolderAliasesForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(FolderAliasesForm form, boolean reshow, BindException errors)
        {
            return new JspView<ViewContext>("/org/labkey/core/admin/folderAliases.jsp");
        }

        @Override
        public boolean handlePost(FolderAliasesForm form, BindException errors)
        {
            List<String> aliases = new ArrayList<>();
            if (form.getAliases() != null)
            {
                StringTokenizer st = new StringTokenizer(form.getAliases(), "\n\r", false);
                while (st.hasMoreTokens())
                {
                    String alias = st.nextToken().trim();
                    if (!alias.startsWith("/"))
                    {
                        alias = "/" + alias;
                    }
                    while (alias.endsWith("/"))
                    {
                        alias = alias.substring(0, alias.lastIndexOf('/'));
                    }
                    aliases.add(alias);
                }
            }
            ContainerManager.saveAliasesForContainer(getContainer(), aliases, getUser());

            return true;
        }

        @Override
        public ActionURL getSuccessURL(FolderAliasesForm form)
        {
            return getManageFoldersURL();
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addAdminNavTrail(root, "Folder Aliases: " + getContainer().getPath(), this.getClass());
        }
    }

    public static class FolderAliasesForm
    {
        private String _aliases;

        public String getAliases()
        {
            return _aliases;
        }

        @SuppressWarnings("unused")
        public void setAliases(String aliases)
        {
            _aliases = aliases;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class CustomizeEmailAction extends FormViewAction<CustomEmailForm>
    {
        @Override
        public void validateCommand(CustomEmailForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(CustomEmailForm form, boolean reshow, BindException errors)
        {
            JspView<CustomEmailForm> result = new JspView<>("/org/labkey/core/admin/customizeEmail.jsp", form, errors);
            result.setTitle("Email Template");
            return result;
        }

        @Override
        public boolean handlePost(CustomEmailForm form, BindException errors)
        {
            if (form.getTemplateClass() != null)
            {
                EmailTemplate template = EmailTemplateService.get().createTemplate(form.getTemplateClass());

                template.setSubject(form.getEmailSubject());
                template.setSenderName(form.getEmailSender());
                template.setReplyToEmail(form.getEmailReplyTo());
                template.setBody(form.getEmailMessage());

                String[] errorStrings = new String[1];
                if (template.isValid(errorStrings))  // TODO: Pass in errors collection directly?  Should also build a list of all validation errors and display them all.
                    EmailTemplateService.get().saveEmailTemplate(template, getContainer());
                else
                    errors.reject(ERROR_MSG, errorStrings[0]);
            }

            return !errors.hasErrors();
        }

        @Override
        public ActionURL getSuccessURL(CustomEmailForm form)
        {
            ActionURL result = new ActionURL(CustomizeEmailAction.class, getContainer());
            result.replaceParameter("templateClass", form.getTemplateClass());
            if (form.getReturnActionURL() != null)
            {
                result.replaceParameter(ActionURL.Param.returnUrl, form.getReturnUrl());
            }
            return result;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("customEmail");
            addAdminNavTrail(root, "Customize " + (getContainer().isRoot() ? "Site-Wide" : StringUtils.capitalize(getContainer().getContainerNoun()) + "-Level") + " Email", this.getClass());
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class DeleteCustomEmailAction extends FormHandlerAction<CustomEmailForm>
    {
        @Override
        public void validateCommand(CustomEmailForm target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(CustomEmailForm form, BindException errors) throws Exception
        {
            if (form.getTemplateClass() != null)
            {
                EmailTemplate template = EmailTemplateService.get().createTemplate(form.getTemplateClass());
                template.setSubject(form.getEmailSubject());
                template.setBody(form.getEmailMessage());

                EmailTemplateService.get().deleteEmailTemplate(template, getContainer());
            }
            return true;
        }

        @Override
        public URLHelper getSuccessURL(CustomEmailForm form)
        {
            return new AdminUrlsImpl().getCustomizeEmailURL(getContainer(), form.getTemplateClass(), form.getReturnUrlHelper());
        }
    }

    @SuppressWarnings("unused")
    public static class CustomEmailForm extends ReturnUrlForm
    {
        private String _templateClass;
        private String _emailSubject;
        private String _emailSender;
        private String _emailReplyTo;
        private String _emailMessage;
        private String _templateDescription;

        public void setTemplateClass(String name){_templateClass = name;}
        public String getTemplateClass(){return _templateClass;}
        public void setEmailSubject(String subject){_emailSubject = subject;}
        public String getEmailSubject(){return _emailSubject;}
        public void setEmailSender(String sender){_emailSender = sender;}
        public String getEmailSender(){return _emailSender;}
        public void setEmailMessage(String body){_emailMessage = body;}
        public String getEmailMessage(){return _emailMessage;}
        public String getEmailReplyTo(){return _emailReplyTo;}
        public void setEmailReplyTo(String emailReplyTo){_emailReplyTo = emailReplyTo;}

        public String getTemplateDescription()
        {
            return _templateDescription;
        }

        public void setTemplateDescription(String templateDescription)
        {
            _templateDescription = templateDescription;
        }
    }

    private ActionURL getManageFoldersURL()
    {
        return new AdminUrlsImpl().getManageFoldersURL(getContainer());
    }

    public static class ManageFoldersForm extends ReturnUrlForm
    {
        private String name;
        private String title;
        private boolean titleSameAsName;
        private String folder;
        private String target;
        private String folderType;
        private String defaultModule;
        private String[] activeModules;
        private boolean hasLoaded = false;
        private boolean showAll;
        private boolean confirmed = false;
        private boolean addAlias = false;
        private String templateSourceId;
        private String[] templateWriterTypes;
        private boolean templateIncludeSubfolders = false;
        private String[] targets;
        private PHI _exportPhiLevel = PHI.NotPHI;

        public boolean getHasLoaded()
        {
            return hasLoaded;
        }

        public void setHasLoaded(boolean hasLoaded)
        {
            this.hasLoaded = hasLoaded;
        }

        public String[] getActiveModules()
        {
            return activeModules;
        }

        public void setActiveModules(String[] activeModules)
        {
            this.activeModules = activeModules;
        }

        public String getDefaultModule()
        {
            return defaultModule;
        }

        public void setDefaultModule(String defaultModule)
        {
            this.defaultModule = defaultModule;
        }

        public boolean isShowAll()
        {
            return showAll;
        }

        public void setShowAll(boolean showAll)
        {
            this.showAll = showAll;
        }

        public String getFolder()
        {
            return folder;
        }

        public void setFolder(String folder)
        {
            this.folder = folder;
        }

        public String getName()
        {
            return name;
        }

        public String getTitle()
        {
            return title;
        }

        public void setTitle(String title)
        {
            this.title = title;
        }

        public boolean isTitleSameAsName()
        {
            return titleSameAsName;
        }

        public void setTitleSameAsName(boolean updateTitle)
        {
            this.titleSameAsName = updateTitle;
        }
        public void setName(String name)
        {
            this.name = name;
        }

        public boolean isConfirmed()
        {
            return confirmed;
        }

        public void setConfirmed(boolean confirmed)
        {
            this.confirmed = confirmed;
        }

        public String getFolderType()
        {
            return folderType;
        }

        public void setFolderType(String folderType)
        {
            this.folderType = folderType;
        }

        public boolean isAddAlias()
        {
            return addAlias;
        }

        public void setAddAlias(boolean addAlias)
        {
            this.addAlias = addAlias;
        }

        public String getTarget()
        {
            return target;
        }

        public void setTarget(String target)
        {
            this.target = target;
        }

        public void setTemplateSourceId(String templateSourceId)
        {
            this.templateSourceId = templateSourceId;
        }

        public String getTemplateSourceId()
        {
            return templateSourceId;
        }

        public Container getTemplateSourceContainer()
        {
            if (null == getTemplateSourceId())
                return null;
            return ContainerManager.getForId(getTemplateSourceId());
        }

        public String[] getTemplateWriterTypes()
        {
            return templateWriterTypes;
        }

        public void setTemplateWriterTypes(String[] templateWriterTypes)
        {
            this.templateWriterTypes = templateWriterTypes;
        }

        public boolean getTemplateIncludeSubfolders()
        {
            return templateIncludeSubfolders;
        }

        public void setTemplateIncludeSubfolders(boolean templateIncludeSubfolders)
        {
            this.templateIncludeSubfolders = templateIncludeSubfolders;
        }

        public String[] getTargets()
        {
            return targets;
        }

        public void setTargets(String[] targets)
        {
            this.targets = targets;
        }

        public PHI getExportPhiLevel()
        {
            return _exportPhiLevel;
        }

        public void setExportPhiLevel(PHI exportPhiLevel)
        {
            _exportPhiLevel = exportPhiLevel;
        }

        /**
         * Note: this is designed to allow code to specify a set of children to delete in bulk. The main use-case is workbooks,
         * but it will work for non-workbook children as well.
         */
        public List<Container> getTargetContainers(final Container currentContainer) throws IllegalArgumentException
        {
            if (getTargets() != null)
            {
                final List<Container> targets = new ArrayList<>();
                final List<Container> directChildren = ContainerManager.getChildren(currentContainer);

                Arrays.stream(getTargets()).forEach(x -> {
                    Container c = ContainerManager.getForId(x);
                    if (c == null)
                    {
                        try
                        {
                            Integer rowId = ConvertHelper.convert(x, Integer.class);
                            if (rowId > 0)
                                c = ContainerManager.getForRowId(rowId);
                        }
                        catch (ConversionException e)
                        {
                            //ignore
                        }
                    }

                    if (c != null)
                    {
                        if (!c.equals(currentContainer))
                        {
                            if (!directChildren.contains(c))
                            {
                                throw new IllegalArgumentException("Folder " + c.getPath() + " is not a direct child of the current folder: " + currentContainer.getPath());
                            }

                            if (c.getContainerType().canHaveChildren())
                            {
                                throw new IllegalArgumentException("Multi-folder delete is not supported for containers of type: " + c.getContainerType().getName());
                            }
                        }

                        targets.add(c);
                    }
                    else
                    {
                        throw new IllegalArgumentException("Unable to find folder with ID or RowId of: " + x);
                    }
                });

                return targets;
            }
            else
            {
                return Collections.singletonList(currentContainer);
            }
        }
    }

    public static class RenameContainerForm
    {
        private String name;
        private String title;
        private boolean addAlias = true;

        public String getName()
        {
            return name;
        }

        public void setName(String name)
        {
            this.name = name;
        }

        public String getTitle()
        {
            return title;
        }

        public void setTitle(String title)
        {
            this.title = title;
        }

        public boolean isAddAlias()
        {
            return addAlias;
        }

        public void setAddAlias(boolean addAlias)
        {
            this.addAlias = addAlias;
        }
    }

    // Note that validation checks occur in ContainerManager.rename()
    @RequiresPermission(AdminPermission.class)
    public static class RenameContainerAction extends MutatingApiAction<RenameContainerForm>
    {
        @Override
        public ApiResponse execute(RenameContainerForm form, BindException errors)
        {
            Container container = getContainer();
            String name = StringUtils.trimToNull(form.getName());
            String title = StringUtils.trimToNull(form.getTitle());

            String nameValue = name;
            String titleValue = title;
            if (name == null && title == null)
            {
                errors.reject(ERROR_MSG, "Please specify a name or a title.");
                return new ApiSimpleResponse("success", false);
            }
            else if (name != null && title == null)
            {
                titleValue = name;
            }
            else if (name == null)
            {
                nameValue = container.getName();
            }

            boolean addAlias = form.isAddAlias();

            try
            {
                Container c = ContainerManager.rename(container, getUser(), nameValue, titleValue, addAlias);
                return new ApiSimpleResponse(c.toJSON(getUser()));
            }
            catch (Exception e)
            {
                errors.reject(ERROR_MSG, e.getMessage() != null ? e.getMessage() : "Failed to rename folder. An error has occurred.");
                return new ApiSimpleResponse("success", false);
            }
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class RenameFolderAction extends FormViewAction<ManageFoldersForm>
    {
        private ActionURL _returnUrl;

        @Override
        public void validateCommand(ManageFoldersForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(ManageFoldersForm form, boolean reshow, BindException errors)
        {
            return new JspView<>("/org/labkey/core/admin/renameFolder.jsp", form, errors);
        }

        @Override
        public boolean handlePost(ManageFoldersForm form, BindException errors)
        {
            try
            {
                String title = form.isTitleSameAsName() ? null : StringUtils.trimToNull(form.getTitle());
                Container c = ContainerManager.rename(getContainer(), getUser(), form.getName(), title, form.isAddAlias());
                _returnUrl = new AdminUrlsImpl().getManageFoldersURL(c);
                return true;
            }
            catch (Exception e)
            {
                errors.reject(ERROR_MSG, e.getMessage() != null ? e.getMessage() : "Failed to rename folder. An error has occurred.");
            }

            return false;
        }

        @Override
        public ActionURL getSuccessURL(ManageFoldersForm form)
        {
            return _returnUrl;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            getPageConfig().setFocusId("name");
            String containerType = getContainer().isProject() ? "Project" : "Folder";
            addAdminNavTrail(root, "Change " + containerType  + " Name Settings", this.getClass());
        }
    }

    public static class MoveFolderTreeView extends JspView<ManageFoldersForm>
    {
        private MoveFolderTreeView(ManageFoldersForm form, BindException errors)
        {
            super("/org/labkey/core/admin/moveFolder.jsp", form, errors);
        }
    }

    @RequiresPermission(AdminPermission.class)
    @ActionNames("ShowMoveFolderTree,MoveFolder")
    public class MoveFolderAction extends FormViewAction<ManageFoldersForm>
    {
        boolean showConfirmPage = false;
        boolean moveFailed = false;

        @Override
        public void validateCommand(ManageFoldersForm form, Errors errors)
        {
            Container c = getContainer();

            if (c.isRoot())
                throw new NotFoundException("Can't move the root folder.");  // Don't show move tree from root

            if (c.equals(ContainerManager.getSharedContainer()) || c.equals(ContainerManager.getHomeContainer()))
                errors.reject(ERROR_MSG, "Moving /Shared or /home is not possible.");

            Container newParent = isBlank(form.getTarget()) ? null : ContainerManager.getForPath(form.getTarget());
            if (null == newParent)
            {
                errors.reject(ERROR_MSG, "Target '" + form.getTarget() + "' folder does not exist.");
            }
            else if (!newParent.hasPermission(getUser(), AdminPermission.class))
            {
                throw new UnauthorizedException();
            }
            else if (newParent.hasChild(c.getName()))
            {
                errors.reject(ERROR_MSG, "Error: The selected folder already has a folder with that name. Please select a different location (or Cancel).");
            }
        }

        @Override
        public ModelAndView getView(ManageFoldersForm form, boolean reshow, BindException errors) throws Exception
        {
            if (showConfirmPage)
                return new JspView<>("/org/labkey/core/admin/confirmProjectMove.jsp", form);
            if (moveFailed)
                return new SimpleErrorView(errors);
            else
                return new MoveFolderTreeView(form, errors);
        }

        @Override
        public boolean handlePost(ManageFoldersForm form, BindException errors) throws Exception
        {
            Container c = getContainer();
            Container newParent = ContainerManager.getForPath(form.getTarget());
            Container oldProject = c.getProject();
            Container newProject = newParent.isRoot() ? c : newParent.getProject();

            if (!oldProject.getId().equals(newProject.getId()) && !form.isConfirmed())
            {
                showConfirmPage = true;
                return false;   // reshow
            }

            try
            {
                ContainerManager.move(c, newParent, getUser());
            }
            catch (ValidationException e)
            {
                moveFailed = true;
                getPageConfig().setTemplate(Template.Dialog);
                for (ValidationError validationError : e.getErrors())
                {
                    errors.addError(new LabKeyError(validationError.getMessage()));
                }
                if (!errors.hasErrors())
                    errors.addError(new LabKeyError("Move failed"));
                return false;
            }

            if (form.isAddAlias())
            {
                List<String> newAliases = new ArrayList<>(ContainerManager.getAliasesForContainer(c));
                newAliases.add(c.getPath());
                ContainerManager.saveAliasesForContainer(c, newAliases, getUser());
            }
            return true;
        }

        @Override
        public URLHelper getSuccessURL(ManageFoldersForm manageFoldersForm)
        {
            Container c = getContainer();
            c = ContainerManager.getForId(c.getId());      // Reload container to populate new location
            return new AdminUrlsImpl().getManageFoldersURL(c);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Folder Management", getManageFoldersURL());
            root.addChild("Move Folder");
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class ConfirmProjectMoveAction extends SimpleViewAction<ManageFoldersForm>
    {
        @Override
        public ModelAndView getView(ManageFoldersForm form, BindException errors)
        {
            getPageConfig().setTemplate(Template.Dialog);
            return new JspView<>("/org/labkey/core/admin/confirmProjectMove.jsp", form);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Confirm Project Move");
        }
    }

    private static abstract class AbstractCreateFolderAction<FORM extends ManageFoldersForm> extends FormViewAction<FORM>
    {
        private ActionURL _successURL;

        @Override
        public void validateCommand(FORM target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(FORM form, boolean reshow, BindException errors)
        {
            VBox vbox = new VBox();

            if (!reshow)
            {
                FolderType folderType = FolderTypeManager.get().getDefaultFolderType();
                if (null != folderType)
                {
                    // If a default folder type has been configured by a site admin set that as the default folder type choice
                    form.setFolderType(folderType.getName());
                }
                form.setExportPhiLevel(ComplianceService.get().getMaxAllowedPhi(getContainer(), getUser()));
            }
            JspView<FORM> statusView = new JspView<>("/org/labkey/core/admin/createFolder.jsp", form, errors);
            vbox.addView(statusView);

            Container c = getViewContext().getContainerNoTab();         // Cannot create subfolder of tab folder

            setHelpTopic("createProject");
            getPageConfig().setNavTrail(ContainerManager.getCreateContainerWizardSteps(null, c));
            getPageConfig().setTemplate(Template.Wizard);

            if (c.isRoot())
                getPageConfig().setTitle("Create Project");
            else
            {
                String title = "Create Folder";

                title += " in /";
                if (c == ContainerManager.getHomeContainer())
                    title += "Home";
                else
                    title += c.getName();

                getPageConfig().setTitle(title);
            }

            return vbox;
        }

        @Override
        public boolean handlePost(FORM form, BindException errors) throws Exception
        {
            Container parent = getViewContext().getContainerNoTab();
            String folderName = StringUtils.trimToNull(form.getName());
            String folderTitle = (form.isTitleSameAsName() || folderName.equals(form.getTitle())) ? null : form.getTitle();
            StringBuilder error = new StringBuilder();
            Consumer<Container> afterCreateHandler = getAfterCreateHandler(form);

            Container container;

            if (Container.isLegalName(folderName, parent.isRoot(), error))
            {
                if (parent.hasChild(folderName))
                {
                    if (parent.isRoot())
                    {
                        error.append("The server already has a project with this name.");
                    }
                    else
                    {
                        error.append("The ").append(parent.isProject() ? "project " : "folder ").append(parent.getPath()).append(" already has a folder with this name.");
                    }
                }
                else
                {
                    String folderType = form.getFolderType();

                    if (null == folderType)
                    {
                        errors.reject(null, "Folder type must be specified");
                        return false;
                    }

                    if ("Template".equals(folderType)) // Create folder from selected template
                    {
                        Container sourceContainer = form.getTemplateSourceContainer();
                        if (null == sourceContainer)
                        {
                            errors.reject(null, "Source template folder not selected");
                            return false;
                        }
                        else if (!sourceContainer.hasPermission(getUser(), AdminPermission.class))
                        {
                            errors.reject(null, "User does not have administrator permissions to the source container");
                            return false;
                        }
                        else if (!sourceContainer.hasEnableRestrictedModules(getUser()) && sourceContainer.hasRestrictedActiveModule(sourceContainer.getActiveModules()))
                        {
                            errors.reject(null, "The source folder has a restricted module for which you do not have permission.");
                            return false;
                        }

                        FolderExportContext exportCtx = new FolderExportContext(getUser(), sourceContainer, PageFlowUtil.set(form.getTemplateWriterTypes()), "new",
                                form.getTemplateIncludeSubfolders(), form.getExportPhiLevel(), false, false, false,
                                new StaticLoggerGetter(LogManager.getLogger(FolderWriterImpl.class)));

                        container = ContainerManager.createContainerFromTemplate(parent, folderName, folderTitle, sourceContainer, getUser(), exportCtx, afterCreateHandler);
                    }
                    else
                    {
                        FolderType type = FolderTypeManager.get().getFolderType(folderType);

                        if (type == null)
                        {
                            errors.reject(null, "Folder type not recognized");
                            return false;
                        }

                        String[] modules = form.getActiveModules();

                        if (null == StringUtils.trimToNull(folderType) || FolderType.NONE.getName().equals(folderType))
                        {
                            if (null == modules || modules.length == 0)
                            {
                                errors.reject(null, "At least one module must be selected");
                                return false;
                            }
                        }

                        // Work done in this lambda will not fire container events.  Only fireCreateContainer() will be called.
                        Consumer<Container> configureContainer = (newContainer) ->
                        {
                            afterCreateHandler.accept(newContainer);
                            newContainer.setFolderType(type, getUser(), errors);

                            if (null == StringUtils.trimToNull(folderType) || FolderType.NONE.getName().equals(folderType))
                            {
                                Set<Module> activeModules = new HashSet<>();
                                for (String moduleName : modules)
                                {
                                    Module module = ModuleLoader.getInstance().getModule(moduleName);
                                    if (module != null)
                                        activeModules.add(module);
                                }

                                newContainer.setFolderType(FolderType.NONE, getUser(), errors, activeModules);
                                Module defaultModule = ModuleLoader.getInstance().getModule(form.getDefaultModule());
                                newContainer.setDefaultModule(defaultModule);
                            }
                        };
                        container = ContainerManager.createContainer(parent, folderName, folderTitle, null, NormalContainerType.NAME, getUser(), null, configureContainer);
                    }

                    _successURL = new AdminUrlsImpl().getSetFolderPermissionsURL(container);
                    _successURL.addParameter("wizard", Boolean.TRUE.toString());

                    return true;
                }
            }

            errors.reject(ERROR_MSG, "Error: " + error + " Please enter a different name.");
            return false;
        }

        /**
         * Return a Consumer that provides post-creation handling on the new Container
         */
        abstract public Consumer<Container> getAfterCreateHandler(FORM form);

        @Override
        protected String getCommandClassMethodName()
        {
            return "getAfterCreateHandler";
        }

        @Override
        public ActionURL getSuccessURL(FORM form)
        {
            return _successURL;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class CreateFolderAction extends AbstractCreateFolderAction<ManageFoldersForm>
    {
        @Override
        public Consumer<Container> getAfterCreateHandler(ManageFoldersForm form)
        {
            // No special handling
            return container -> {};
        }
    }

    public static class CreateProjectForm extends ManageFoldersForm
    {
        private boolean _assignProjectAdmin = false;

        public boolean isAssignProjectAdmin()
        {
            return _assignProjectAdmin;
        }

        @SuppressWarnings("unused")
        public void setAssignProjectAdmin(boolean assignProjectAdmin)
        {
            _assignProjectAdmin = assignProjectAdmin;
        }
    }

    @RequiresPermission(CreateProjectPermission.class)
    public static class CreateProjectAction extends AbstractCreateFolderAction<CreateProjectForm>
    {
        @Override
        public void validateCommand(CreateProjectForm target, Errors errors)
        {
            super.validateCommand(target, errors);
            if (!getContainer().isRoot())
                errors.reject(ERROR_MSG, "Must be invoked from the root");
        }

        @Override
        public Consumer<Container> getAfterCreateHandler(CreateProjectForm form)
        {
            if (form.isAssignProjectAdmin())
            {
                return c -> {
                    MutableSecurityPolicy policy = new MutableSecurityPolicy(c.getPolicy());
                    policy.addRoleAssignment(getUser(), ProjectAdminRole.class);
                    User savePolicyUser = getUser();
                    if (c.isProject() && !c.hasPermission(savePolicyUser, AdminPermission.class) && ContainerManager.getRoot().hasPermission(savePolicyUser, CreateProjectPermission.class))
                    {
                        // Special case for project creators who don't necessarily yet have permission to save the policy of
                        // the project they just created
                        savePolicyUser = User.getAdminServiceUser();
                    }

                    SecurityPolicyManager.savePolicy(policy, savePolicyUser);
                };
            }
            else
            {
                return c -> {};
            }
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class SetFolderPermissionsAction extends FormViewAction<SetFolderPermissionsForm>
    {
        private ActionURL _successURL;

        @Override
        public void validateCommand(SetFolderPermissionsForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(SetFolderPermissionsForm form, boolean reshow, BindException errors)
        {
            VBox vbox = new VBox();

            JspView<Object> statusView = new JspView<>("/org/labkey/core/admin/setFolderPermissions.jsp", form, errors);
            vbox.addView(statusView);

            Container c = getContainer();
            getPageConfig().setTitle("Users / Permissions");
            getPageConfig().setNavTrail(ContainerManager.getCreateContainerWizardSteps(c, c.getParent()));
            getPageConfig().setTemplate(Template.Wizard);
            setHelpTopic("createProject");

            return vbox;
        }

        @Override
        public boolean handlePost(SetFolderPermissionsForm form, BindException errors)
        {
            Container c = getContainer();
            String permissionType = form.getPermissionType();

            if(c.isProject()){
                _successURL = new AdminUrlsImpl().getInitialFolderSettingsURL(c);
            }
            else
            {
                List<NavTree> extraSteps = getContainer().getFolderType().getExtraSetupSteps(getContainer());
                if (extraSteps.isEmpty())
                {
                    if (form.isAdvanced())
                    {
                        _successURL = new SecurityController.SecurityUrlsImpl().getPermissionsURL(getContainer());
                    }
                    else
                    {
                        _successURL = getContainer().getStartURL(getUser());
                    }
                }
                else
                {
                    _successURL = new ActionURL(extraSteps.getFirst().getHref());
                }
            }

            if(permissionType == null){
                errors.reject(ERROR_MSG, "You must select one of the options for permissions.");
                return false;
            }

            switch (permissionType)
            {
                case "CurrentUser" -> {
                    MutableSecurityPolicy policy = new MutableSecurityPolicy(c);
                    Role role = RoleManager.getRole(c.isProject() ? ProjectAdminRole.class : FolderAdminRole.class);
                    policy.addRoleAssignment(getUser(), role);
                    SecurityPolicyManager.savePolicy(policy, getUser());
                }
                case "Inherit" -> SecurityManager.setInheritPermissions(c);
                case "CopyExistingProject" -> {
                    String targetProject = form.getTargetProject();
                    if (targetProject == null)
                    {
                        errors.reject(ERROR_MSG, "In order to copy permissions from an existing project, you must pick a project.");
                        return false;
                    }
                    Container source = ContainerManager.getForId(targetProject);
                    if (source == null)
                    {
                        source = ContainerManager.getForPath(targetProject);
                    }
                    if (source == null)
                    {
                        throw new NotFoundException("An unknown project was specified to copy permissions from: " + targetProject);
                    }
                    if (!source.hasPermission(getUser(), AdminPermission.class))
                        throw new UnauthorizedException("You do not have permission to copy permissions from the specified project.");
                    Map<UserPrincipal, UserPrincipal> groupMap = GroupManager.copyGroupsToContainer(source, c, getUser());

                    //copy role assignments
                    SecurityPolicy op = SecurityPolicyManager.getPolicy(source);
                    MutableSecurityPolicy np = new MutableSecurityPolicy(c);
                    for (RoleAssignment assignment : op.getAssignments())
                    {
                        int userId = assignment.getUserId();
                        UserPrincipal p = SecurityManager.getPrincipal(userId);
                        Role r = assignment.getRole();

                        if (p instanceof Group g)
                        {
                            if (!g.isProjectGroup())
                            {
                                np.addRoleAssignment(p, r, false);
                            }
                            else
                            {
                                np.addRoleAssignment(groupMap.get(p), r, false);
                            }
                        }
                        else
                        {
                            np.addRoleAssignment(p, r, false);
                        }
                    }
                    SecurityPolicyManager.savePolicy(np, getUser());
                }
                default -> throw new UnsupportedOperationException("An Unknown permission type was supplied: " + permissionType);
            }
            _successURL.addParameter("wizard", Boolean.TRUE.toString());

            return true;
        }

        @Override
        public ActionURL getSuccessURL(SetFolderPermissionsForm form)
        {
            return _successURL;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            getPageConfig().setFocusId("name");
        }
    }

    public static class SetFolderPermissionsForm
    {
        private String targetProject;
        private String permissionType;
        private boolean advanced;

        public String getPermissionType()
        {
            return permissionType;
        }

        @SuppressWarnings("unused")
        public void setPermissionType(String permissionType)
        {
            this.permissionType = permissionType;
        }

        public String getTargetProject()
        {
            return targetProject;
        }

        @SuppressWarnings("unused")
        public void setTargetProject(String targetProject)
        {
            this.targetProject = targetProject;
        }

        public boolean isAdvanced()
        {
            return advanced;
        }

        @SuppressWarnings("unused")
        public void setAdvanced(boolean advanced)
        {
            this.advanced = advanced;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class SetInitialFolderSettingsAction extends FormViewAction<FilesForm>
    {
        private ActionURL _successURL;

        @Override
        public void validateCommand(FilesForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(FilesForm form, boolean reshow, BindException errors)
        {
            VBox vbox = new VBox();
            Container c = getContainer();

            JspView<FilesForm> statusView = new JspView<>("/org/labkey/core/admin/setInitialFolderSettings.jsp", form, errors);
            vbox.addView(statusView);

            getPageConfig().setNavTrail(ContainerManager.getCreateContainerWizardSteps(c, c.getParent()));
            getPageConfig().setTemplate(Template.Wizard);

            String noun = c.isProject() ? "Project": "Folder";
            getPageConfig().setTitle(noun + " Settings");

            return vbox;
        }

        @Override
        public boolean handlePost(FilesForm form, BindException errors)
        {
            Container c = getContainer();
            String folderRootPath = StringUtils.trimToNull(form.getFolderRootPath());
            String fileRootOption = form.getFileRootOption() != null ? form.getFileRootOption() : "default";

            if(folderRootPath == null && !fileRootOption.equals("default"))
            {
                errors.reject(ERROR_MSG, "Error: Must supply a default file location.");
                return false;
            }

            FileContentService service = FileContentService.get();
            if(fileRootOption.equals("default"))
            {
                service.setIsUseDefaultRoot(c, true);
            }
            // Requires AdminOperationsPermission to set file root
            else if (c.hasPermission(getUser(), AdminOperationsPermission.class))
            {
                if (!service.isValidProjectRoot(folderRootPath))
                {
                    errors.reject(ERROR_MSG, "File root '" + folderRootPath + "' does not appear to be a valid directory accessible to the server at " + getViewContext().getRequest().getServerName() + ".");
                    return false;
                }

                service.setIsUseDefaultRoot(c.getProject(), false);
                service.setFileRootPath(c.getProject(), folderRootPath);
            }

            List<NavTree> extraSteps = getContainer().getFolderType().getExtraSetupSteps(getContainer());
            if (extraSteps.isEmpty())
            {
                _successURL = getContainer().getStartURL(getUser());
            }
            else
            {
                _successURL = new ActionURL(extraSteps.getFirst().getHref());
            }

            return true;
        }

        @Override
        public ActionURL getSuccessURL(FilesForm form)
        {
            return _successURL;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            getPageConfig().setFocusId("name");
            setHelpTopic("createProject");
        }
    }

    @RequiresPermission(DeletePermission.class)
    public static class DeleteWorkbooksAction extends SimpleRedirectAction<ReturnUrlForm>
    {
        public void validateCommand(ReturnUrlForm target, Errors errors)
        {
            Set<String> ids = DataRegionSelection.getSelected(getViewContext(), true);
            if (ids.isEmpty())
            {
                errors.reject(ERROR_MSG, "No IDs provided");
            }
        }

        @Override
        public @Nullable ActionURL getRedirectURL(ReturnUrlForm form) throws Exception
        {
            Set<String> ids = DataRegionSelection.getSelected(getViewContext(), true);

            ActionURL ret = new ActionURL(DeleteFolderAction.class, getContainer());
            ids.forEach(id -> ret.addParameter("targets", id));

            ret.replaceParameter(ActionURL.Param.returnUrl, form.getReturnUrl());

            return ret;
        }
    }

    //NOTE: some types of containers can be deleted by non-admin users, provided they have DeletePermission on the parent
    @RequiresPermission(DeletePermission.class)
    public static class DeleteFolderAction extends FormViewAction<ManageFoldersForm>
    {
        private final List<Container> _deleted = new ArrayList<>();

        @Override
        public void validateCommand(ManageFoldersForm form, Errors errors)
        {
            try
            {
                List<Container> targets = form.getTargetContainers(getContainer());
                for (Container target : targets)
                {
                    if (!ContainerManager.isDeletable(target))
                        errors.reject(ERROR_MSG, "The path " + target.getPath() + " is not deletable.");

                    if (target.isProject() && !getUser().hasRootAdminPermission())
                    {
                        throw new UnauthorizedException();
                    }

                    Class<? extends Permission> permClass = target.getPermissionNeededToDelete();
                    if (!target.hasPermission(getUser(), permClass))
                    {
                        Permission perm = RoleManager.getPermission(permClass);
                        throw new UnauthorizedException("Cannot delete folder: " + target.getName() + ". " + perm.getName() + " permission required");
                    }

                    if (target.hasChildren() && !ContainerManager.hasTreePermission(target, getUser(), AdminPermission.class))
                    {
                        throw new UnauthorizedException("Deleting the " + target.getContainerNoun() + " " + target.getName() + " requires admin permissions on that folder and all children. You do not have admin permission on all subfolders.");
                    }

                    if (target.equals(ContainerManager.getSharedContainer()) || target.equals(ContainerManager.getHomeContainer()))
                        errors.reject(ERROR_MSG, "Deleting /Shared or /home is not possible.");
                }
            }
            catch (IllegalArgumentException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
            }
        }

        @Override
        public ModelAndView getView(ManageFoldersForm form, boolean reshow, BindException errors)
        {
            getPageConfig().setTemplate(Template.Dialog);
            return new JspView<>("/org/labkey/core/admin/deleteFolder.jsp", form);
        }

        @Override
        public boolean handlePost(ManageFoldersForm form, BindException errors)
        {
            List<Container> targets = form.getTargetContainers(getContainer());

            // Must be site/app admin to delete a project
            for (Container c : targets)
            {
                ContainerManager.deleteAll(c, getUser());
            }

            _deleted.addAll(targets);

            return true;
        }

        @Override
        public ActionURL getSuccessURL(ManageFoldersForm form)
        {
            // Note: because in some scenarios we might be deleting children of the current contaner, in those cases we remain in this folder:
            // If we just deleted a project then redirect to the home page, otherwise back to managing the project folders
            if (_deleted.size() == 1 && _deleted.getFirst().equals(getContainer()))
            {
                Container c = getContainer();
                if (c.isProject())
                    return AppProps.getInstance().getHomePageActionURL();
                else
                    return new AdminUrlsImpl().getManageFoldersURL(c.getParent());
            }
            else
            {
                if (form.getReturnUrl() != null)
                {
                    return form.getReturnActionURL();
                }
                else
                {
                    return getContainer().getStartURL(getUser());
                }
            }
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Confirm " + getContainer().getContainerNoun() + " deletion");
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ReorderFoldersAction extends FormViewAction<FolderReorderForm>
    {
        @Override
        public void validateCommand(FolderReorderForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(FolderReorderForm folderReorderForm, boolean reshow, BindException errors)
        {
            return new JspView<ViewContext>("/org/labkey/core/admin/reorderFolders.jsp");
        }

        @Override
        public boolean handlePost(FolderReorderForm form, BindException errors)
        {
            return ReorderFolders(form, errors);
        }

        @Override
        public ActionURL getSuccessURL(FolderReorderForm folderReorderForm)
        {
            if (getContainer().isRoot())
                return getShowAdminURL();
            else
                return getManageFoldersURL();
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            String title = "Reorder " + (getContainer().isRoot() || getContainer().getParent().isRoot() ? "Projects" : "Folders");
            addAdminNavTrail(root, title, this.getClass());
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class ReorderFoldersApiAction extends MutatingApiAction<FolderReorderForm>
    {
        @Override
        public ApiResponse execute(FolderReorderForm form, BindException errors)
        {
            return new ApiSimpleResponse("success", ReorderFolders(form, errors));
        }
    }

    private boolean ReorderFolders(FolderReorderForm form, BindException errors)
    {
        Container parent = getContainer().isRoot() ? getContainer() : getContainer().getParent();
        if (form.isResetToAlphabetical())
            ContainerManager.setChildOrderToAlphabetical(parent);
        else if (form.getOrder() != null)
        {
            List<Container> children = parent.getChildren();
            String[] order = form.getOrder().split(";");
            Map<String, Container> nameToContainer = new HashMap<>();
            for (Container child : children)
                nameToContainer.put(child.getName(), child);
            List<Container> sorted = new ArrayList<>(children.size());
            for (String childName : order)
            {
                Container child = nameToContainer.get(childName);
                sorted.add(child);
            }

            try
            {
                ContainerManager.setChildOrder(parent, sorted);
            }
            catch (ContainerException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                return false;
            }
        }

        return true;
    }

    public static class FolderReorderForm
    {
        private String _order;
        private boolean _resetToAlphabetical;

        public String getOrder()
        {
            return _order;
        }

        @SuppressWarnings("unused")
        public void setOrder(String order)
        {
            _order = order;
        }

        public boolean isResetToAlphabetical()
        {
            return _resetToAlphabetical;
        }

        @SuppressWarnings("unused")
        public void setResetToAlphabetical(boolean resetToAlphabetical)
        {
            _resetToAlphabetical = resetToAlphabetical;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class RevertFolderAction extends MutatingApiAction<RevertFolderForm>
    {
        @Override
        public ApiResponse execute(RevertFolderForm form, BindException errors)
        {
            if (isBlank(form.getContainerPath()))
                throw new NotFoundException();

            boolean success = false;
            Container revertContainer = ContainerManager.getForPath(form.getContainerPath());
            if (null != revertContainer)
            {
                if (!revertContainer.hasPermission(getUser(), AdminPermission.class))
                    throw new UnauthorizedException();

                if (revertContainer.isContainerTab())
                {
                    FolderTab tab = revertContainer.getParent().getFolderType().findTab(revertContainer.getName());
                    if (null != tab)
                    {
                        FolderType origFolderType = tab.getFolderType();
                        if (null != origFolderType)
                        {
                            revertContainer.setFolderType(origFolderType, getUser(), errors);
                            if (!errors.hasErrors())
                                success = true;
                        }
                    }
                }
                else if (revertContainer.getFolderType().hasContainerTabs())
                {
                    try (DbScope.Transaction transaction = CoreSchema.getInstance().getSchema().getScope().ensureTransaction())
                    {
                        List<Container> children = revertContainer.getChildren();
                        for (Container container : children)
                        {
                            if (container.isContainerTab())
                            {
                                FolderTab tab = revertContainer.getFolderType().findTab(container.getName());
                                if (null != tab)
                                {
                                    FolderType origFolderType = tab.getFolderType();
                                    if (null != origFolderType)
                                    {
                                        container.setFolderType(origFolderType, getUser(), errors);
                                    }
                                }
                            }
                        }
                        if (!errors.hasErrors())
                        {
                            transaction.commit();
                            success = true;
                        }
                    }
                }
            }
            return new ApiSimpleResponse("success", success);
        }
    }

    public static class RevertFolderForm
    {
        private String _containerPath;

        public String getContainerPath()
        {
            return _containerPath;
        }

        public void setContainerPath(String containerPath)
        {
            _containerPath = containerPath;
        }
    }

    public static class EmailTestForm
    {
        private String _to;
        private String _body;
        private ConfigurationException _exception;
        private boolean _success;
        private boolean _attachmentSuccess;

        public boolean isSuccess()
        {
            return _success;
        }

        public void setSuccess(boolean success)
        {
            _success = success;
        }

        public boolean isAttachmentSuccess()
        {
            return _attachmentSuccess;
        }

        public void setAttachmentSuccess(boolean attachmentSuccess)
        {
            _attachmentSuccess = attachmentSuccess;
        }

        public String getTo()
        {
            return _to;
        }

        public void setTo(String to)
        {
            _to = to;
        }

        public String getBody()
        {
            return _body;
        }

        public void setBody(String body)
        {
            _body = body;
        }

        public ConfigurationException getException()
        {
            return _exception;
        }

        public void setException(ConfigurationException exception)
        {
            _exception = exception;
        }

        public String getFrom(Container c)
        {
            LookAndFeelProperties props = LookAndFeelProperties.getInstance(c);
            return props.getSystemEmailAddress();
        }
    }

    @AdminConsoleAction
    @RequiresPermission(AdminOperationsPermission.class)
    public class EmailTestAction extends FormViewAction<EmailTestForm>
    {
        private static final String EMAIL_TEST_SUCCESS_KEY = "EmailTestAction.success";

        @Override
        public void validateCommand(EmailTestForm form, Errors errors)
        {
            if(null == form.getTo() || form.getTo().isEmpty())
            {
                errors.reject(ERROR_MSG, "To field cannot be blank.");
                form.setException(new ConfigurationException("To field cannot be blank"));
                return;
            }

            try
            {
                ValidEmail email = new ValidEmail(form.getTo());
            }
            catch(ValidEmail.InvalidEmailException e)
            {
                errors.reject(ERROR_MSG, e.getMessage());
                form.setException(new ConfigurationException(e.getMessage()));
            }
        }

        @Override
        public ModelAndView getView(EmailTestForm form, boolean reshow, BindException errors)
        {
            // Check for flash message from previous successful send
            if (Boolean.TRUE.equals(getViewContext().getSession().getAttribute(EMAIL_TEST_SUCCESS_KEY)))
            {
                getViewContext().getSession().removeAttribute(EMAIL_TEST_SUCCESS_KEY);
                form.setSuccess(true);
            }
            if (Boolean.TRUE.equals(getViewContext().getSession().getAttribute(GraphEmailTestWithAttachmentAction.EMAIL_TEST_ATTACHMENT_SUCCESS_KEY)))
            {
                getViewContext().getSession().removeAttribute(GraphEmailTestWithAttachmentAction.EMAIL_TEST_ATTACHMENT_SUCCESS_KEY);
                form.setAttachmentSuccess(true);
            }
            String attachmentError = (String) getViewContext().getSession().getAttribute(GraphEmailTestWithAttachmentAction.EMAIL_TEST_ATTACHMENT_ERROR_KEY);
            if (attachmentError != null)
            {
                getViewContext().getSession().removeAttribute(GraphEmailTestWithAttachmentAction.EMAIL_TEST_ATTACHMENT_ERROR_KEY);
                form.setException(new ConfigurationException(attachmentError));
            }

            JspView<EmailTestForm> testView = new JspView<>("/org/labkey/core/admin/emailTest.jsp", form);
            testView.setTitle("Send a Test Email");

            if(MailHelper.hasActiveProvider())
            {
                JspView<?> emailPropsView = new JspView<>("/org/labkey/core/admin/emailProps.jsp");
                emailPropsView.setTitle("Current Email Settings");

                return new VBox(emailPropsView, testView);
            }
            else
                return testView;
        }

        @Override
        public boolean handlePost(EmailTestForm form, BindException errors) throws Exception
        {
            if (errors.hasErrors())
            {
                return false;
            }

            LookAndFeelProperties props = LookAndFeelProperties.getInstance(getContainer());
                try
                {
                    MailHelper.ViewMessage msg = MailHelper.createMessage(props.getSystemEmailAddress(), new ValidEmail(form.getTo()).toString());
                    msg.setSubject("Test email message sent from " + props.getShortName());
                    msg.setText(PageFlowUtil.filter(form.getBody()));

                    try
                    {
                        MailHelper.send(msg, getUser(), getContainer());
                        getViewContext().getSession().setAttribute(EMAIL_TEST_SUCCESS_KEY, Boolean.TRUE);
                    }
                    catch (ConfigurationException e)
                    {
                        form.setException(e);
                        return false;
                    }
                    catch (Exception e)
                    {
                        form.setException(new ConfigurationException(e.getMessage()));
                        return false;
                    }
                }
                catch (MessagingException e)
                {
                    errors.reject(ERROR_MSG, e.getMessage());
                    return false;
                }
            return true;
        }

        @Override
        public URLHelper getSuccessURL(EmailTestForm emailTestForm)
        {
            return new ActionURL(EmailTestAction.class, getContainer());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addAdminNavTrail(root, "Test Email Configuration", getClass());
        }
    }

    /**
     * Tests the Microsoft Graph API email transport with HTML content and a large attachment.
     * This action tests the Graph API upload session workflow for attachments over 3MB,
     * and data URI to CID attachment conversion (embedded base64 images in HTML).
     * Use {@link EmailTestAction} for general email testing for both SMTP and Graph API.
     */
    @AdminConsoleAction
    @RequiresPermission(AdminOperationsPermission.class)
    public class GraphEmailTestWithAttachmentAction extends FormHandlerAction<EmailTestForm>
    {
        private static final String EMAIL_TEST_ATTACHMENT_SUCCESS_KEY = "GraphEmailTestWithAttachmentAction.success";
        private static final String EMAIL_TEST_ATTACHMENT_ERROR_KEY = "GraphEmailTestWithAttachmentAction.error";

        @Override
        public void validateCommand(EmailTestForm form, Errors errors)
        {
            if (form.getTo() == null || form.getTo().isEmpty())
            {
                errors.reject(ERROR_MSG, "To field cannot be blank.");
            }
        }

        @Override
        public boolean handlePost(EmailTestForm form, BindException errors) throws Exception
        {
            if (errors.hasErrors())
                return false;

            if (!MailHelper.hasActiveProvider())
            {
                form.setException(new ConfigurationException("No email provider configured"));
                return false;
            }

            ValidEmail recipient = new ValidEmail(form.getTo());

            // Create a temp file for attachment, deleting any leftover from previous test
            File tempFile = FileUtil.appendPath(FileUtil.getTempDirectory(), org.labkey.api.util.Path.parse("email_test_attachment.txt"));
            FileUtil.deleteTempFile(tempFile);

            // Generate ~4MB of content to test Graph API upload session chunked upload
            StringBuilder sb = new StringBuilder();
            sb.append("LabKey Server Email Attachment Test\n");
            sb.append("Provider: ").append(MailHelper.getActiveProvider().getName()).append("\n");
            sb.append("Timestamp: ").append(java.time.Instant.now()).append("\n\n");
            String line = "This is test data for email attachment upload testing. ";
            while (sb.length() < 4 * 1024 * 1024)
            {
                sb.append(line);
            }
            java.nio.file.Files.writeString(tempFile.toPath(), sb.toString());//creates the actual file and writes the content

            try
            {
                // Use the Graph provider's configured from address
                String fromAddress = MailHelper.getActiveProvider().getProperties().getProperty("mail.graph.fromAddress");
                MailHelper.MultipartMessage msg = MailHelper.createMultipartMessage();
                msg.setFrom(fromAddress);
                msg.addRecipient(Message.RecipientType.TO, recipient.getAddress());
                msg.setSubject("Test Email with HTML and Attachment");

                // Test both external URL images and data URI images.
                // External URLs should be left unchanged, while data URIs should be converted to CID attachments.

                // External image URL served by the running LabKey server (should NOT be converted - left as-is)
                String logoUrl = "https://www.labkey.org/_webdav/Documentation/%40files/badge.png";

                // Data URI image built from actual webapp image (should be converted to CID attachment by GraphTransportProvider)
                FileLike imagesDir = ModuleLoader.getInstance().getModule("Core").getStaticFileDirectories().stream()
                    .map(dir -> dir.resolveChild("_images"))
                    .filter(FileLike::isDirectory)
                    .findFirst()
                    .orElseThrow(() -> new ConfigurationException("Could not find _images directory in core module"));
                String gifDataUri = "data:image/gif;base64," + java.util.Base64.getEncoder().encodeToString(java.nio.file.Files.readAllBytes(imagesDir.resolveChild("paperclip.gif").toNioPathForRead()));

                msg.setEncodedHtmlContent(createHtmlFragment(
                    DIV("This is a ", SPAN(at(style, "font-weight:bold"), "test email"), " with HTML content, attachment, and multiple images."),
                    DIV(SPAN(at(style, "font-weight:bold"), "External URL image"), " (should remain unchanged):"),
                    IMG(at(src, logoUrl, style, "height:30px")),
                    DIV(SPAN(at(style, "font-weight:bold"), "Data URI image"), " (should be converted to CID attachment):"),
                    IMG(at(src, gifDataUri, style, "height:30px")),
                    DIV("Sent via ", MailHelper.getActiveProvider().getName(), ".")
                ).toString());
                msg.addAttachment(tempFile);

                MailHelper.send(msg, getUser(), getContainer());
                getViewContext().getSession().setAttribute(EMAIL_TEST_ATTACHMENT_SUCCESS_KEY, Boolean.TRUE);
            }
            catch (Exception e)
            {
                getViewContext().getSession().setAttribute(EMAIL_TEST_ATTACHMENT_ERROR_KEY, e.getMessage());
                return true;
            }

            return true;
        }

        @Override
        public URLHelper getSuccessURL(EmailTestForm form)
        {
            return new ActionURL(EmailTestAction.class, getContainer());
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public static class RecreateViewsAction extends ConfirmAction<Object>
    {
        @Override
        public ModelAndView getConfirmView(Object o, BindException errors)
        {
            getPageConfig().setShowHeader(false);
            getPageConfig().setTitle("Recreate Views?");
            return new HtmlView(HtmlString.of("Are you sure you want to drop and recreate all module views?"));
        }

        @Override
        public boolean handlePost(Object o, BindException errors)
        {
            ModuleLoader.getInstance().recreateViews();
            return true;
        }

        @Override
        public void validateCommand(Object o, Errors errors)
        {
        }

        @Override
        public @NotNull ActionURL getSuccessURL(Object o)
        {
            return AppProps.getInstance().getHomePageActionURL();
        }
    }

    static public class LoggingForm
    {
        public boolean isLogging()
        {
            return logging;
        }

        public void setLogging(boolean logging)
        {
            this.logging = logging;
        }

        public boolean logging = false;
    }

    @RequiresLogin
    @AllowedDuringUpgrade
    @AllowedBeforeInitialUserIsSet
    public static class GetSessionLogEventsAction extends ReadOnlyApiAction<Object>
    {
        @Override
        public void checkPermissions()
        {
            super.checkPermissions();
            if (!getUser().isPlatformDeveloper())
                throw new UnauthorizedException();
        }

        @Override
        public ApiResponse execute(Object o, BindException errors)
        {
            Integer eventId = null;
            try
            {
                String s = getViewContext().getRequest().getParameter("eventId");
                if (null != s)
                    eventId = Integer.parseInt(s);
            }
            catch (NumberFormatException ignored) {}
            ApiSimpleResponse res = new ApiSimpleResponse();
            res.put("success", true);
            res.put("events", SessionAppender.getLoggingEvents(getViewContext().getRequest(), eventId));
            return res;
        }
    }

    @RequiresLogin
    @AllowedBeforeInitialUserIsSet
    @AllowedDuringUpgrade
    @IgnoresAllocationTracking  /* ignore so that we don't get an update in the UI for each time it requests the newest data */
    public static class GetTrackedAllocationsAction extends ReadOnlyApiAction<Object>
    {
        @Override
        public void checkPermissions()
        {
            super.checkPermissions();
            if (!getUser().isPlatformDeveloper())
                throw new UnauthorizedException();
        }

        @Override
        public ApiResponse execute(Object o, BindException errors)
        {
            long requestId = 0;
            try
            {
                String s = getViewContext().getRequest().getParameter("requestId");
                if (null != s)
                    requestId = Long.parseLong(s);
            }
            catch (NumberFormatException ignored) {}
            List<RequestInfo> requests = MemTracker.getInstance().getNewRequests(requestId);
            List<Map<String, Object>> jsonRequests = new ArrayList<>(requests.size());
            for (RequestInfo requestInfo : requests)
            {
                Map<String, Object> m = new HashMap<>();
                m.put("requestId", requestInfo.getId());
                m.put("url", requestInfo.getUrl());
                m.put("date", requestInfo.getDate());


                List<Map.Entry<String, Integer>> sortedObjects = sortByCounts(requestInfo);

                List<Map<String, Object>> jsonObjects = new ArrayList<>(sortedObjects.size());
                for (Map.Entry<String, Integer> entry : sortedObjects)
                {
                    Map<String, Object> jsonObject = new HashMap<>();
                    jsonObject.put("name", entry.getKey());
                    jsonObject.put("count", entry.getValue());
                    jsonObjects.add(jsonObject);
                }
                m.put("objects", jsonObjects);
                jsonRequests.add(m);
            }
            return new ApiSimpleResponse("requests", jsonRequests);
        }

        private List<Map.Entry<String, Integer>> sortByCounts(RequestInfo requestInfo)
        {
            List<Map.Entry<String, Integer>> objects = new ArrayList<>(requestInfo.getObjects().entrySet());
            objects.sort(Map.Entry.comparingByValue(Comparator.reverseOrder()));
            return objects;
        }
    }

    @RequiresLogin
    @AllowedDuringUpgrade
    @AllowedBeforeInitialUserIsSet
    public static class TrackedAllocationsViewerAction extends SimpleViewAction<Object>
    {
        @Override
        public void checkPermissions()
        {
            super.checkPermissions();
            if (!getUser().isPlatformDeveloper())
                throw new UnauthorizedException();
        }

        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            getPageConfig().setTemplate(Template.Print);
            return new JspView<>("/org/labkey/core/admin/memTrackerViewer.jsp");
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    @RequiresLogin
    @AllowedDuringUpgrade
    @AllowedBeforeInitialUserIsSet
    public static class SessionLoggingAction extends FormViewAction<LoggingForm>
    {
        @Override
        public void checkPermissions()
        {
            super.checkPermissions();
            if (!getContainer().hasPermission(getUser(), PlatformDeveloperPermission.class))
                throw new UnauthorizedException();
        }

        @Override
        public boolean handlePost(LoggingForm form, BindException errors)
        {
            boolean on = SessionAppender.isLogging(getViewContext().getRequest());
            if (form.logging != on)
            {
                if (!form.logging)
                    LogManager.getLogger(AdminController.class).info("turn session logging OFF");
                SessionAppender.setLoggingForSession(getViewContext().getRequest(), form.logging);
                if (form.logging)
                    LogManager.getLogger(AdminController.class).info("turn session logging ON");
            }
            return true;
        }

        @Override
        public void validateCommand(LoggingForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(LoggingForm o, boolean reshow, BindException errors)
        {
            SessionAppender.setLoggingForSession(getViewContext().getRequest(), true);
            getPageConfig().setTemplate(Template.Print);
            return new LoggingView();
        }

        @Override
        public ActionURL getSuccessURL(LoggingForm o)
        {
            return new ActionURL(SessionLoggingAction.class, getContainer());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Admin Console", new ActionURL(ShowAdminAction.class, getContainer()).getLocalURIString());
            root.addChild("View Event Log");
        }
    }

    static class LoggingView extends JspView<Object>
    {
        LoggingView()
        {
            super("/org/labkey/core/admin/logging.jsp", null);
        }
    }

    public static class LogForm
    {
        private String _message;
        private String _level;

        public String getMessage()
        {
            return _message;
        }

        public void setMessage(String message)
        {
            _message = message;
        }

        public String getLevel()
        {
            return _level;
        }

        public void setLevel(String level)
        {
            _level = level;
        }
    }


    // Simple action that writes "message" parameter to the labkey log. Used by the test harness to indicate when
    // each test begins and ends. Message parameter is output as sent, except that \n is translated to newline.
    @RequiresLogin
    public static class LogAction extends MutatingApiAction<LogForm>
    {
        @Override
        public ApiResponse execute(LogForm logForm, BindException errors)
        {
            // Could use %A0 for newline in the middle of the message, however, parameter values get trimmed so translate
            // \n to newlines to allow them at the beginning or end of the message as well.
            StringBuilder message = new StringBuilder();
            message.append(StringUtils.replace(logForm.getMessage(), "\\n", "\n"));

            Level level = Level.toLevel(logForm.getLevel(), Level.INFO);
            CLIENT_LOG.log(level, message);
            return new ApiSimpleResponse("success", true);
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class ValidateDomainsAction extends FormHandlerAction<Object>
    {
        @Override
        public void validateCommand(Object target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            // Find a valid pipeline root - we don't really care which one, we just need somewhere to write the log file
            for (Container project : Arrays.asList(ContainerManager.getSharedContainer(), ContainerManager.getHomeContainer()))
            {
                PipeRoot root = PipelineService.get().findPipelineRoot(project);
                if (root != null && root.isValid())
                {
                    ViewBackgroundInfo info = getViewBackgroundInfo();
                    PipelineJob job = new ValidateDomainsPipelineJob(info, root);
                    PipelineService.get().queueJob(job);
                    return true;
                }
            }
            return false;
        }

        @Override
        public URLHelper getSuccessURL(Object o)
        {
            return urlProvider(PipelineUrls.class).urlBegin(ContainerManager.getRoot());
        }
    }

    public static class ModulesForm
    {
        private double[] _ignore = new double[0];  // Module versions to ignore (filter out of the results)
        private boolean _managedOnly = false;
        private boolean _unmanagedOnly = false;

        public double[] getIgnore()
        {
            return _ignore;
        }

        public void setIgnore(double[] ignore)
        {
            _ignore = ignore;
        }

        private Set<Double> getIgnoreSet()
        {
            return new LinkedHashSet<>(Arrays.asList(ArrayUtils.toObject(_ignore)));
        }

        public boolean isManagedOnly()
        {
            return _managedOnly;
        }

        @SuppressWarnings("unused")
        public void setManagedOnly(boolean managedOnly)
        {
            _managedOnly = managedOnly;
        }

        public boolean isUnmanagedOnly()
        {
            return _unmanagedOnly;
        }

        @SuppressWarnings("unused")
        public void setUnmanagedOnly(boolean unmanagedOnly)
        {
            _unmanagedOnly = unmanagedOnly;
        }
    }

    public enum ManageFilter
    {
        ManagedOnly
        {
            @Override
            public boolean accept(Module module)
            {
                return null != module && module.shouldManageVersion();
            }
        },
        UnmanagedOnly
        {
            @Override
            public boolean accept(Module module)
            {
                return null != module && !module.shouldManageVersion();
            }
        },
        All
        {
            @Override
            public boolean accept(Module module)
            {
                return true;
            }
        };

        public abstract boolean accept(Module module);
    }

    @AdminConsoleAction(AdminOperationsPermission.class)
    public class ModulesAction extends SimpleViewAction<ModulesForm>
    {
        @Override
        public ModelAndView getView(ModulesForm form, BindException errors)
        {
            ModuleLoader ml = ModuleLoader.getInstance();
            boolean hasAdminOpsPerm = getContainer().hasPermission(getUser(), AdminOperationsPermission.class);

            Collection<ModuleContext> unknownModules = ml.getUnknownModuleContexts().values();
            Collection<ModuleContext> knownModules = ml.getAllModuleContexts();
            knownModules.removeAll(unknownModules);

            Set<Double> ignoreSet = form.getIgnoreSet();
            HtmlString managedLink = HtmlString.EMPTY_STRING;
            HtmlString unmanagedLink = HtmlString.EMPTY_STRING;

            // Option to filter out all modules whose version shouldn't be managed, or whose version matches the previous release
            // version or 0.00. This can be helpful during the end-of-release consolidation process. Show the link only in dev mode.
            if (AppProps.getInstance().isDevMode())
            {
                if (ignoreSet.isEmpty() && !form.isManagedOnly())
                {
                    String lowestSchemaVersion = ModuleContext.formatVersion(Constants.getLowestSchemaVersion());
                    ActionURL url = new ActionURL(ModulesAction.class, ContainerManager.getRoot());
                    url.addParameter("ignore", "0.00," + lowestSchemaVersion);
                    url.addParameter("managedOnly", true);
                    managedLink = LinkBuilder.labkeyLink("Click here to ignore null, " + lowestSchemaVersion + " and unmanaged modules", url).getHtmlString();
                }
                else
                {
                    List<String> ignore = ignoreSet
                        .stream()
                        .map(ModuleContext::formatVersion)
                        .collect(Collectors.toCollection(LinkedList::new));

                    String ignoreString = ignore.isEmpty() ? null : ignore.toString();
                    String unmanaged = form.isManagedOnly() ? "unmanaged" : null;

                    managedLink = HtmlString.of("(Currently ignoring " + Joiner.on(" and ").skipNulls().join(new String[]{ignoreString, unmanaged}) + ") ");
                }

                if (!form.isUnmanagedOnly())
                {
                    ActionURL url = new ActionURL(ModulesAction.class, ContainerManager.getRoot());
                    url.addParameter("unmanagedOnly", true);
                    unmanagedLink = LinkBuilder.labkeyLink("Click here to show unmanaged modules only", url).getHtmlString();
                }
                else
                {
                    unmanagedLink = HtmlString.of("(Currently showing unmanaged modules only)");
                }
            }

            ManageFilter filter = form.isManagedOnly() ? ManageFilter.ManagedOnly : (form.isUnmanagedOnly() ? ManageFilter.UnmanagedOnly : ManageFilter.All);

            HtmlStringBuilder deleteInstructions = HtmlStringBuilder.of();
            if (hasAdminOpsPerm)
            {
                deleteInstructions.unsafeAppend("<br><br>").append(
                        "To delete a module that does not have a delete link, first delete its .module file and exploded module directory from your Labkey deployment directory, and restart the server. " +
                        "Module files are typically deployed in <labkey_deployment_root>/modules and <labkey_deployment_root>/externalModules.")
                    .unsafeAppend("<br><br>").append(
                                LinkBuilder.labkeyLink("Create new empty module", getCreateURL()));
            }

            HtmlStringBuilder docLink = HtmlStringBuilder.of();
            docLink.unsafeAppend("<br><br>").append("Additional modules available, click ").append(new HelpTopic("defaultModules").getSimpleLinkHtml("here")).append(" to learn more.");

            HtmlStringBuilder knownDescription = HtmlStringBuilder.of()
                .append("Each of these modules is installed and has a valid module file. ").append(managedLink).append(unmanagedLink).append(deleteInstructions).append(docLink);
            HttpView known = new ModulesView(knownModules, "Known", knownDescription.getHtmlString(), null, ignoreSet, filter);

            HtmlStringBuilder unknownDescription = HtmlStringBuilder.of()
                .append(1 == unknownModules.size() ? "This module" : "Each of these modules").append(" has been installed on this server " +
                "in the past but the corresponding module file is currently missing or invalid. Possible explanations: the " +
                "module is no longer part of the deployed distribution, the module has been renamed, the server location where the module " +
                "is stored is not accessible, or the module file is corrupted.")
                .unsafeAppend("<br><br>").append("The delete links below will remove all record of a module from the database tables.");
            HtmlString noModulesDescription = HtmlString.of("A module is considered \"unknown\" if it was installed on this server " +
                    "in the past but the corresponding module file is currently missing or invalid. This server has no unknown modules.");
            HttpView unknown = new ModulesView(unknownModules, "Unknown", unknownDescription.getHtmlString(), noModulesDescription, Collections.emptySet(), filter);

            return new VBox(known, unknown);
        }

        private class ModulesView extends WebPartView
        {
            private final Collection<ModuleContext> _contexts;
            private final HtmlString _descriptionHtml;
            private final HtmlString _noModulesDescriptionHtml;
            private final Set<Double> _ignoreVersions;
            private final ManageFilter _manageFilter;

            private ModulesView(Collection<ModuleContext> contexts, String type, HtmlString descriptionHtml, HtmlString noModulesDescriptionHtml, Set<Double> ignoreVersions, ManageFilter manageFilter)
            {
                super(FrameType.PORTAL);
                List<ModuleContext> sorted = new ArrayList<>(contexts);
                sorted.sort(Comparator.comparing(ModuleContext::getName, String.CASE_INSENSITIVE_ORDER));

                _contexts = sorted;
                _descriptionHtml = descriptionHtml;
                _noModulesDescriptionHtml = noModulesDescriptionHtml;
                _ignoreVersions = ignoreVersions;
                _manageFilter = manageFilter;
                setTitle(type + " Modules");
            }

            @Override
            protected void renderView(Object model, HtmlWriter out)
            {
                boolean isDevMode = AppProps.getInstance().isDevMode();
                boolean hasAdminOpsPerm = getUser().hasRootPermission(AdminOperationsPermission.class);
                boolean hasUploadModulePerm = getUser().hasRootPermission(UploadFileBasedModulePermission.class);
                final AtomicInteger rowCount = new AtomicInteger();
                ExplodedModuleService moduleService = !hasUploadModulePerm ? null : ServiceRegistry.get().getService(ExplodedModuleService.class);
                final File externalModulesDir = moduleService==null ? null : moduleService.getExternalModulesDirectory();
                final Path relativeRoot = ModuleLoader.getInstance().getCoreModule().getExplodedPath().getParentFile().getParentFile().toPath();

                if (_contexts.isEmpty())
                {
                    out.write(_noModulesDescriptionHtml);
                }
                else
                {
                    DIV(
                        DIV(_descriptionHtml),
                        TABLE(cl("labkey-data-region-legacy","labkey-show-borders","labkey-data-region-header-lock"),
                            TR(
                                TD(cl("labkey-column-header"),"Name"),
                                TD(cl("labkey-column-header"),"Release Version"),
                                TD(cl("labkey-column-header"),"Schema Version"),
                                TD(cl("labkey-column-header"),"Class"),
                                TD(cl("labkey-column-header"),"Location"),
                                TD(cl("labkey-column-header"),"Schemas"),
                                !AppProps.getInstance().isDevMode() ? null : TD(cl("labkey-column-header"),""),    // edit actions
                                null == externalModulesDir ? null : TD(cl("labkey-column-header"),""),    // upload actions
                                !hasAdminOpsPerm ? null : TD(cl("labkey-column-header"),"")     // delete actions
                            ),
                            _contexts.stream()
                                .filter(moduleContext -> !_ignoreVersions.contains(moduleContext.getInstalledVersion()))
                                .map(moduleContext -> new Pair<>(moduleContext,ModuleLoader.getInstance().getModule(moduleContext.getName())))
                                .filter(pair -> _manageFilter.accept(pair.getValue()))
                                .map(pair ->
                                {
                                    ModuleContext moduleContext = pair.getKey();
                                    Module module = pair.getValue();
                                    List<String> schemas = moduleContext.getSchemaList();
                                    Double schemaVersion = moduleContext.getSchemaVersion();
                                    boolean replaceableModule = false;
                                    if (null != module && module.getClass() == SimpleModule.class && schemas.isEmpty())
                                    {
                                        File zip = module.getZippedPath();
                                        if (null != zip && zip.getParentFile().equals(externalModulesDir))
                                            replaceableModule = true;
                                    }
                                    boolean deleteableModule = replaceableModule || null == module;
                                    String className = StringUtils.trimToEmpty(moduleContext.getClassName());
                                    String fullPathToModule = "";
                                    String shortPathToModule = "";
                                    if (null != module)
                                    {
                                        Path p = module.getExplodedPath().toPath();
                                        if (null != module.getZippedPath())
                                            p = module.getZippedPath().toPath();
                                        if (isDevMode && ModuleEditorService.get().canEditSourceModule(module))
                                            if (!isBlank(module.getSourcePath()) && !module.getExplodedPath().getPath().equals(module.getSourcePath()))
                                                p = Paths.get(module.getSourcePath());
                                        fullPathToModule = p.toString();
                                        shortPathToModule = fullPathToModule;
                                        Path rel = relativeRoot.relativize(p);
                                        if (!rel.startsWith(".."))
                                            shortPathToModule = rel.toString();
                                    }
                                    ActionURL moduleEditorUrl = getModuleEditorURL(moduleContext.getName());

                                    return TR(cl(rowCount.getAndIncrement()%2==0 ? "labkey-alternate-row" : "labkey-row").at(style,"vertical-align:top;"),
                                        TD(moduleContext.getName()),
                                        TD(at(style,"white-space:nowrap;"), null != module ? module.getReleaseVersion() : NBSP),
                                        TD(null != schemaVersion ? ModuleContext.formatVersion(schemaVersion) : NBSP),
                                        TD(SPAN(at(title,className), className.substring(className.lastIndexOf(".")+1))),
                                        TD(SPAN(at(title,fullPathToModule),shortPathToModule)),
                                        TD(schemas.stream().map(s -> createHtmlFragment(s, BR()))),
                                        !AppProps.getInstance().isDevMode() ? null : TD((null == moduleEditorUrl) ? NBSP : LinkBuilder.labkeyLink("Edit module", moduleEditorUrl)),
                                        null == externalModulesDir ? null : TD(!replaceableModule ? NBSP : LinkBuilder.labkeyLink("Upload Module", getUpdateURL(moduleContext.getName()))),
                                        !hasAdminOpsPerm ? null : TD(!deleteableModule ? NBSP : LinkBuilder.labkeyLink("Delete Module" + (schemas.isEmpty() ? "" : (" and Schema" + (schemas.size() > 1 ? "s" : ""))), getDeleteURL(moduleContext.getName())))
                                    );
                                })
                        )
                    ).appendTo(out);
                }
            }
        }

        private ActionURL getDeleteURL(String name)
        {
            ActionURL url = ModuleEditorService.get().getDeleteModuleURL(name);
            if (null != url)
                return url;
            url = new ActionURL(DeleteModuleAction.class, ContainerManager.getRoot());
            url.addParameter("name", name);
            return url;
        }

        private ActionURL getUpdateURL(String name)
        {
            ActionURL url = ModuleEditorService.get().getUpdateModuleURL(name);
            if (null != url)
                return url;
            url = new ActionURL(UpdateModuleAction.class, ContainerManager.getRoot());
            url.addParameter("name", name);
            return url;
        }

        private ActionURL getModuleEditorURL(String name)
        {
            return ModuleEditorService.get().getModuleEditorURL(name);
        }

        private ActionURL getCreateURL()
        {
            ActionURL url = ModuleEditorService.get().getCreateModuleURL();
            if (null != url)
                return url;
            url = new ActionURL(CreateModuleAction.class, ContainerManager.getRoot());
            return url;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("defaultModules");
            addAdminNavTrail(root, "Modules", getClass());
        }
    }

    public static class SchemaVersionTestCase extends Assert
    {
        @Test
        public void verifyMinimumSchemaVersion()
        {
            List<Module> modulesTooLow = ModuleLoader.getInstance().getModules().stream()
                .filter(ManageFilter.ManagedOnly::accept)
                .filter(m -> null != m.getSchemaVersion())
                .filter(m -> m.getSchemaVersion() > 0.00 && m.getSchemaVersion() < Constants.getLowestSchemaVersion())
                .toList();

            if (!modulesTooLow.isEmpty())
                fail("The following module" + (1 == modulesTooLow.size() ? " needs its schema version" : "s need their schema versions") + " increased to " + ModuleContext.formatVersion(Constants.getLowestSchemaVersion()) + ": " + modulesTooLow);
        }

        @Test
        public void modulesWithSchemaVersionButNoScripts()
        {
            // Flag all modules that have a schema version but don't have scripts. Their schema version should be null.
            List<String> moduleNames = ModuleLoader.getInstance().getModules().stream()
                .filter(m -> m.getSchemaVersion() != null)
                .filter(m -> m instanceof DefaultModule dm && !dm.hasScripts())
                .map(m -> m.getName() + ": " + m.getSchemaVersion())
                .toList();

            if (!moduleNames.isEmpty())
                fail("The following module" + (1 == moduleNames.size() ? "" : "s") + " should have a null schema version: " + moduleNames);
        }
    }

    public static class ModuleForm
    {
        private String _name;

        public String getName()
        {
            return _name;
        }

        @SuppressWarnings({"UnusedDeclaration"})
        public void setName(String name)
        {
            _name = name;
        }

        @NotNull
        private ModuleContext getModuleContext()
        {
            ModuleLoader ml = ModuleLoader.getInstance();
            ModuleContext ctx = ml.getModuleContextFromDatabase(getName());

            if (null == ctx)
                throw new NotFoundException("Module not found");

            return ctx;
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public class DeleteModuleAction extends ConfirmAction<ModuleForm>
    {
        @Override
        public void validateCommand(ModuleForm form, Errors errors)
        {
        }

        @Override
        public ModelAndView getConfirmView(ModuleForm form, BindException errors)
        {
            if (getPageConfig().getTitle() == null)
                setTitle("Delete Module");

            ModuleContext ctx = form.getModuleContext();
            Module module = ModuleLoader.getInstance().getModule(ctx.getName());
            boolean hasSchemas = !ctx.getSchemaList().isEmpty();
            boolean hasFiles = false;
            if (null != module)
                hasFiles = null!=module.getExplodedPath() && module.getExplodedPath().isDirectory() || null!=module.getZippedPath() && module.getZippedPath().isFile();

            HtmlStringBuilder description = HtmlStringBuilder.of("\"" + ctx.getName() + "\" module");
            HtmlStringBuilder skippedSchemas = HtmlStringBuilder.of();
            if (hasSchemas)
            {
                SchemaActions schemaActions = ModuleLoader.getInstance().getSchemaActions(module, ctx);
                List<String> deleteList = schemaActions.deleteList();
                List<SchemaAndModule> skipList = schemaActions.skipList();

                // List all the schemas that will be deleted
                if (!deleteList.isEmpty())
                {
                    description.append(" and delete all data in ");
                    description.append(deleteList.size() > 1 ? "these schemas: " + StringUtils.join(deleteList, ", ") : "the \"" + deleteList.getFirst() + "\" schema");
                }

                // For unknown modules, also list the schemas that won't be deleted
                if (!skipList.isEmpty())
                {
                    skippedSchemas.append(HtmlString.BR);
                    skipList.forEach(sam -> skippedSchemas.append(HtmlString.BR)
                        .append("Note: Schema \"")
                        .append(sam.schema())
                        .append("\" will not be deleted because it's in use by module \"")
                        .append(sam.module())
                        .append("\""));
                }
            }

            return new HtmlView(DIV(
                !hasFiles ? null : DIV(cl("labkey-warning-messages"),
                        "This module still has files on disk. Consider, first stopping the server, deleting these files, and restarting the server before continuing.",
                        null==module.getExplodedPath()?null:UL(LI(module.getExplodedPath().getPath())),
                        null==module.getZippedPath()?null:UL(LI(module.getZippedPath().getPath()))
                    ),
                BR(),
                "Are you sure you want to remove the ", description, "? ",
                "This operation cannot be undone!",
                skippedSchemas,
                BR(),
                !hasFiles ? null : "Deleting modules on a running server could leave it in an unpredictable state; be sure to restart your server."
            ));
        }

        @Override
        public boolean handlePost(ModuleForm form, BindException errors)
        {
            ModuleLoader.getInstance().removeModule(form.getModuleContext());

            return true;
        }

        @Override
        public @NotNull URLHelper getSuccessURL(ModuleForm form)
        {
            return new ActionURL(ModulesAction.class, ContainerManager.getRoot());
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public static class UpdateModuleAction extends SimpleViewAction<ModuleForm>
    {
        @Override
        public ModelAndView getView(ModuleForm moduleForm, BindException errors) throws Exception
        {
            return new HtmlView(HtmlString.of("This is a premium feature, please refer to our documentation on www.labkey.org"));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public static class CreateModuleAction extends SimpleViewAction<ModuleForm>
    {
        @Override
        public ModelAndView getView(ModuleForm moduleForm, BindException errors) throws Exception
        {
            return new HtmlView(HtmlString.of("This is a premium feature, please refer to our documentation on www.labkey.org"));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
        }
    }

    public static class OptionalFeatureForm
    {
        private String feature;
        private boolean enabled;

        public String getFeature()
        {
            return feature;
        }

        public void setFeature(String feature)
        {
            this.feature = feature;
        }

        public boolean isEnabled()
        {
            return enabled;
        }

        public void setEnabled(boolean enabled)
        {
            this.enabled = enabled;
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    @ActionNames("OptionalFeature, ExperimentalFeature")
    public static class OptionalFeatureAction extends BaseApiAction<OptionalFeatureForm>
    {
        @Override
        protected ModelAndView handleGet() throws Exception
        {
            return handlePost(); // 'execute' ensures that only POSTs are mutating
        }

        @Override
        public ApiResponse execute(OptionalFeatureForm form, BindException errors)
        {
            String feature = StringUtils.trimToNull(form.getFeature());
            if (feature == null)
                throw new ApiUsageException("feature is required");

            OptionalFeatureService svc = OptionalFeatureService.get();
            if (svc == null)
                throw new IllegalStateException();

            Map<String, Object> ret = new HashMap<>();
            ret.put("feature", feature);

            if (isPost())
            {
                ret.put("previouslyEnabled", svc.isFeatureEnabled(feature));
                svc.setFeatureEnabled(feature, form.isEnabled(), getUser());
            }

            ret.put("enabled", svc.isFeatureEnabled(feature));
            return new ApiSimpleResponse(ret);
        }
    }

    public static class OptionalFeaturesForm
    {
        private String _type;
        private boolean _showHidden;

        public String getType()
        {
            return _type;
        }

        @SuppressWarnings("unused")
        public void setType(String type)
        {
            _type = type;
        }

        public @NotNull FeatureType getTypeEnum()
        {
            return EnumUtils.getEnum(FeatureType.class, getType(), FeatureType.Experimental);
        }

        public boolean isShowHidden()
        {
            return _showHidden;
        }

        @SuppressWarnings("unused")
        public void setShowHidden(boolean showHidden)
        {
            _showHidden = showHidden;
        }
    }

    @RequiresPermission(TroubleshooterPermission.class)
    public class OptionalFeaturesAction extends SimpleViewAction<OptionalFeaturesForm>
    {
        private FeatureType _type;

        @Override
        public ModelAndView getView(OptionalFeaturesForm form, BindException errors)
        {
            _type = form.getTypeEnum();
            JspView<Object> view = new JspView<>("/org/labkey/core/admin/optionalFeatures.jsp", form);
            view.setFrame(WebPartView.FrameType.NONE);
            return view;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("experimental");
            addAdminNavTrail(root, _type.name() + " Features", getClass());
        }
    }

    @RequiresPermission(AdminOperationsPermission.class)
    public static class ProductFeatureAction extends BaseApiAction<ProductConfigForm>
    {
        @Override
        protected ModelAndView handleGet() throws Exception
        {
            return handlePost(); // 'execute' ensures that only POSTs are mutating
        }

        @Override
        public ApiResponse execute(ProductConfigForm form, BindException errors)
        {
            String productKey = StringUtils.trimToNull(form.getProductKey());

            Map<String, Object> ret = new HashMap<>();

            if (isPost())
            {
               ProductConfiguration.setProductKey(productKey);
            }

            ret.put("productKey", new ProductConfiguration().getCurrentProductKey());
            return new ApiSimpleResponse(ret);
        }
    }

    public static class ProductConfigForm
    {
        private String productKey;

        public String getProductKey()
        {
            return productKey;
        }

        public void setProductKey(String productKey)
        {
            this.productKey = productKey;
        }

    }

    @AdminConsoleAction
    @RequiresPermission(AdminOperationsPermission.class)
    public class ProductConfigurationAction extends SimpleViewAction<Object>
    {
        @Override
        public void addNavTrail(NavTree root)
        {
            addAdminNavTrail(root, "Product Configuration", getClass());
        }

        @Override
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            JspView<Object> view = new JspView<>("/org/labkey/core/admin/productConfiguration.jsp");
            view.setFrame(WebPartView.FrameType.NONE);
            return view;
        }
    }


    public static class FolderTypesBean
    {
        private final Collection<FolderType> _allFolderTypes;
        private final Collection<FolderType> _enabledFolderTypes;
        private final FolderType _defaultFolderType;

        public FolderTypesBean(Collection<FolderType> allFolderTypes, Collection<FolderType> enabledFolderTypes, FolderType defaultFolderType)
        {
            _allFolderTypes = allFolderTypes;
            _enabledFolderTypes = enabledFolderTypes;
            _defaultFolderType = defaultFolderType;
        }

        public Collection<FolderType> getAllFolderTypes()
        {
            return _allFolderTypes;
        }

        public Collection<FolderType> getEnabledFolderTypes()
        {
            return _enabledFolderTypes;
        }

        public FolderType getDefaultFolderType()
        {
            return _defaultFolderType;
        }
    }

    @AdminConsoleAction
    @RequiresPermission(AdminPermission.class)
    public class FolderTypesAction extends FormViewAction<Object>
    {
        @Override
        public void validateCommand(Object form, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(Object form, boolean reshow, BindException errors)
        {
            FolderTypesBean bean;
            if (reshow)
            {
                bean = getOptionsFromRequest();
            }
            else
            {
                FolderTypeManager manager = FolderTypeManager.get();
                var defaultFolderType = manager.getDefaultFolderType();
                // If a default folder type has not yet been configuration use "Collaboration" folder type as the default
                defaultFolderType = defaultFolderType != null ? defaultFolderType : manager.getFolderType(CollaborationFolderType.TYPE_NAME);
                boolean userHasEnableRestrictedModulesPermission = getContainer().hasEnableRestrictedModules(getUser());
                bean = new FolderTypesBean(manager.getAllFolderTypes(), manager.getEnabledFolderTypes(userHasEnableRestrictedModulesPermission), defaultFolderType);
            }

            return new JspView<>("/org/labkey/core/admin/enabledFolderTypes.jsp", bean, errors);
        }

        @Override
        public boolean handlePost(Object form, BindException errors)
        {
            FolderTypesBean bean = getOptionsFromRequest();
            var defaultFolderType = bean.getDefaultFolderType();
            if (defaultFolderType == null)
            {
                errors.reject(ERROR_MSG, "Please select a default folder type.");
                return false;
            }
            var enabledFolderTypes = bean.getEnabledFolderTypes();
            if (!enabledFolderTypes.contains(defaultFolderType))
            {
                errors.reject(ERROR_MSG, "Folder type selected as the default, '" + defaultFolderType.getName() + "', must be enabled.");
                return false;
            }

            FolderTypeManager.get().setEnabledFolderTypes(enabledFolderTypes, defaultFolderType);
            return true;
        }

        private FolderTypesBean getOptionsFromRequest()
        {
            var allFolderTypes = FolderTypeManager.get().getAllFolderTypes();
            List<FolderType> enabledFolderTypes = new ArrayList<>();
            FolderType defaultFolderType = null;
            String defaultFolderTypeParam = getViewContext().getRequest().getParameter(FolderTypeManager.FOLDER_TYPE_DEFAULT);

            for (FolderType folderType : FolderTypeManager.get().getAllFolderTypes())
            {
                boolean enabled = Boolean.TRUE.toString().equalsIgnoreCase(getViewContext().getRequest().getParameter(folderType.getName()));
                if (enabled)
                {
                    enabledFolderTypes.add(folderType);
                }
                if (folderType.getName().equals(defaultFolderTypeParam))
                {
                    defaultFolderType = folderType;
                }
            }
            return new FolderTypesBean(allFolderTypes, enabledFolderTypes, defaultFolderType);
        }

        @Override
        public URLHelper getSuccessURL(Object form)
        {
            return getShowAdminURL();
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addAdminNavTrail(root, "Folder Types", getClass());
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class CustomizeMenuAction extends MutatingApiAction<CustomizeMenuForm>
    {
        @Override
        public ApiResponse execute(CustomizeMenuForm form, BindException errors)
        {
            if (null != form.getUrl())
            {
                String errorMessage = StringExpressionFactory.validateURL(form.getUrl());
                if (null != errorMessage)
                {
                    errors.reject(ERROR_MSG, errorMessage);
                    return new ApiSimpleResponse("success", false);
                }
            }

            setCustomizeMenuForm(form, getContainer(),  getUser());
            return new ApiSimpleResponse("success", true);
        }
    }

    protected static final String CUSTOMMENU_SCHEMA = "customMenuSchemaName";
    protected static final String CUSTOMMENU_QUERY = "customMenuQueryName";
    protected static final String CUSTOMMENU_VIEW = "customMenuViewName";
    protected static final String CUSTOMMENU_COLUMN = "customMenuColumnName";
    protected static final String CUSTOMMENU_FOLDER = "customMenuFolderName";
    protected static final String CUSTOMMENU_TITLE = "customMenuTitle";
    protected static final String CUSTOMMENU_URL = "customMenuUrl";
    protected static final String CUSTOMMENU_ROOTFOLDER = "customMenuRootFolder";
    protected static final String CUSTOMMENU_FOLDERTYPES = "customMenuFolderTypes";
    protected static final String CUSTOMMENU_CHOICELISTQUERY = "customMenuChoiceListQuery";
    protected static final String CUSTOMMENU_INCLUDEALLDESCENDANTS = "customIncludeAllDescendants";
    protected static final String CUSTOMMENU_CURRENTPROJECTONLY = "customCurrentProjectOnly";

    public static CustomizeMenuForm getCustomizeMenuForm(Portal.WebPart webPart)
    {
        CustomizeMenuForm form = new CustomizeMenuForm();
        Map<String, String> menuProps = webPart.getPropertyMap();

        String schemaName = menuProps.get(CUSTOMMENU_SCHEMA);
        String queryName = menuProps.get(CUSTOMMENU_QUERY);
        String columnName = menuProps.get(CUSTOMMENU_COLUMN);
        String viewName = menuProps.get(CUSTOMMENU_VIEW);
        String folderName = menuProps.get(CUSTOMMENU_FOLDER);
        String title = menuProps.get(CUSTOMMENU_TITLE); if (null == title) title = "My Menu";
        String urlBottom = menuProps.get(CUSTOMMENU_URL);
        String rootFolder = menuProps.get(CUSTOMMENU_ROOTFOLDER);
        String folderTypes = menuProps.get(CUSTOMMENU_FOLDERTYPES);
        String choiceListQueryString = menuProps.get(CUSTOMMENU_CHOICELISTQUERY);
        boolean choiceListQuery = null == choiceListQueryString || choiceListQueryString.equalsIgnoreCase("true");
        String includeAllDescendantsString = menuProps.get(CUSTOMMENU_INCLUDEALLDESCENDANTS);
        boolean includeAllDescendants = null == includeAllDescendantsString || includeAllDescendantsString.equalsIgnoreCase("true");
        String currentProjectOnlyString = menuProps.get(CUSTOMMENU_CURRENTPROJECTONLY);
        boolean currentProjectOnly = null != currentProjectOnlyString && currentProjectOnlyString.equalsIgnoreCase("true");

        form.setSchemaName(schemaName);
        form.setQueryName(queryName);
        form.setColumnName(columnName);
        form.setViewName(viewName);
        form.setFolderName(folderName);
        form.setTitle(title);
        form.setUrl(urlBottom);
        form.setRootFolder(rootFolder);
        form.setFolderTypes(folderTypes);
        form.setChoiceListQuery(choiceListQuery);
        form.setIncludeAllDescendants(includeAllDescendants);
        form.setCurrentProjectOnly(currentProjectOnly);

        form.setWebPartIndex(webPart.getIndex());
        form.setPageId(webPart.getPageId());
        return form;
    }

    private static void setCustomizeMenuForm(CustomizeMenuForm form, Container container, User user)
    {
        Portal.WebPart webPart = Portal.getPart(container, form.getPageId(), form.getWebPartIndex());
        if (null == webPart)
            throw new NotFoundException();
        Map<String, String> menuProps = webPart.getPropertyMap();

        menuProps.put(CUSTOMMENU_SCHEMA, form.getSchemaName());
        menuProps.put(CUSTOMMENU_QUERY, form.getQueryName());
        menuProps.put(CUSTOMMENU_COLUMN, form.getColumnName());
        menuProps.put(CUSTOMMENU_VIEW, form.getViewName());
        menuProps.put(CUSTOMMENU_FOLDER, form.getFolderName());
        menuProps.put(CUSTOMMENU_TITLE, form.getTitle());
        menuProps.put(CUSTOMMENU_URL, form.getUrl());

        // If root folder not specified, set as current container
        menuProps.put(CUSTOMMENU_ROOTFOLDER, StringUtils.trimToNull(form.getRootFolder()) != null ? form.getRootFolder() : container.getPath());
        menuProps.put(CUSTOMMENU_FOLDERTYPES, form.getFolderTypes());
        menuProps.put(CUSTOMMENU_CHOICELISTQUERY, form.isChoiceListQuery() ? "true" : "false");
        menuProps.put(CUSTOMMENU_INCLUDEALLDESCENDANTS, form.isIncludeAllDescendants() ? "true" : "false");
        menuProps.put(CUSTOMMENU_CURRENTPROJECTONLY, form.isCurrentProjectOnly() ? "true" : "false");

        Portal.updatePart(user, webPart);
    }

    @RequiresPermission(AdminPermission.class)
    public static class AddTabAction extends MutatingApiAction<TabActionForm>
    {
        public void validateCommand(TabActionForm form, Errors errors)
        {
            Container tabContainer = getContainer().getContainerFor(ContainerType.DataType.tabParent);
            if(tabContainer.getFolderType() == FolderType.NONE)
            {
                errors.reject(ERROR_MSG, "Cannot add tabs to custom folder types.");
            }
            else
            {
                String name = form.getTabName();
                if (StringUtils.isEmpty(name))
                {
                    errors.reject(ERROR_MSG, "A tab name must be specified.");
                    return;
                }

                // Note: The name, which shows up on the url, is trimmed to 50 characters. The caption, which is derived
                // from the name, and is editable, is allowed to be 64 characters, so we only error if passed something
                // longer than 64 characters.
                if (name.length() > 64)
                {
                    errors.reject(ERROR_MSG, "Tab name cannot be longer than 64 characters.");
                    return;
                }

                if (name.length() > 50)
                    name = StringUtilsLabKey.leftSurrogatePairFriendly(name, 50).trim();

                CaseInsensitiveHashMap<Portal.PortalPage> pages = new CaseInsensitiveHashMap<>(Portal.getPages(tabContainer, true));
                CaseInsensitiveHashMap<FolderTab> folderTabMap = new CaseInsensitiveHashMap<>();

                for (FolderTab tab : tabContainer.getFolderType().getDefaultTabs())
                {
                    folderTabMap.put(tab.getName(), tab);
                }

                if (pages.containsKey(name))
                {
                    errors.reject(ERROR_MSG, "A tab of the same name already exists in this folder.");
                    return;
                }

                for (Portal.PortalPage page : pages.values())
                {
                    if (page.getCaption() != null && page.getCaption().equals(name))
                    {
                        errors.reject(ERROR_MSG, "A tab of the same name already exists in this folder.");
                        return;
                    }
                    else if (folderTabMap.containsKey(page.getPageId()))
                    {
                        if (folderTabMap.get(page.getPageId()).getCaption(getViewContext()).equalsIgnoreCase(name))
                        {
                            errors.reject(ERROR_MSG, "A tab of the same name already exists in this folder.");
                            return;
                        }
                    }
                }
            }
        }

        @Override
        public ApiResponse execute(TabActionForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();

            validateCommand(form, errors);

            if(errors.hasErrors())
            {
                return response;
            }

            Container container = getContainer().getContainerFor(ContainerType.DataType.tabParent);
            String name = form.getTabName();
            String caption = form.getTabName();

            // The name, which shows up on the url, is trimmed to 50 characters. The caption, which is derived from the
            // name, and is editable, is allowed to be 64 characters.
            if (name.length() > 50)
                name = StringUtilsLabKey.leftSurrogatePairFriendly(name, 50).trim();

            Portal.saveParts(container, name);
            Portal.addProperty(container, name, Portal.PROP_CUSTOMTAB);

            if (!name.equals(caption))
            {
                // If we had to truncate the name then we want to set the caption to the un-truncated version of the name.
                CaseInsensitiveHashMap<Portal.PortalPage> pages = new CaseInsensitiveHashMap<>(Portal.getPages(container, true));
                Portal.PortalPage page = pages.get(name);
                // Get a mutable copy
                page = page.copy();
                page.setCaption(caption);
                Portal.updatePortalPage(container, page);
            }

            ActionURL tabURL = new ActionURL(ProjectController.BeginAction.class, container);
            tabURL.addParameter("pageId", name);
            response.put("url", tabURL);
            response.put("success", true);
            return response;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class ShowTabAction extends MutatingApiAction<TabActionForm>
    {
        public void validateCommand(TabActionForm form, Errors errors)
        {
            CaseInsensitiveHashMap<Portal.PortalPage> pages = new CaseInsensitiveHashMap<>(Portal.getPages(getContainer().getContainerFor(ContainerType.DataType.tabParent), true));

            if (form.getTabPageId() == null)
            {
                errors.reject(ERROR_MSG, "PageId cannot be blank.");
            }

            if (!pages.containsKey(form.getTabPageId()))
            {
                errors.reject(ERROR_MSG, "Page cannot be found. Check with your system administrator.");
            }
        }

        @Override
        public ApiResponse execute(TabActionForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            Container tabContainer = getContainer().getContainerFor(ContainerType.DataType.tabParent);

            validateCommand(form, errors);
            if (errors.hasErrors())
                return response;

            Portal.showPage(tabContainer, form.getTabPageId());
            ActionURL tabURL = new ActionURL(ProjectController.BeginAction.class, tabContainer);
            tabURL.addParameter("pageId", form.getTabPageId());
            response.put("url", tabURL);
            response.put("success", true);
            return response;
        }
    }


    public static class TabActionForm extends ReturnUrlForm
    {
        // This class is used for tab related actions (add, rename, show, etc.)
        String _tabName;
        String _tabPageId;

        public String getTabName()
        {
            return _tabName;
        }

        public void setTabName(String name)
        {
            _tabName = name;
        }

        public String getTabPageId()
        {
            return _tabPageId;
        }

        public void setTabPageId(String tabPageId)
        {
            _tabPageId = tabPageId;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public class MoveTabAction extends MutatingApiAction<MoveTabForm>
    {
        @Override
        public ApiResponse execute(MoveTabForm form, BindException errors)
        {
            final Map<String, Object> properties = new HashMap<>();
            Container tabContainer = getContainer().getContainerFor(ContainerType.DataType.tabParent);
            CaseInsensitiveHashMap<Portal.PortalPage> pages = new CaseInsensitiveHashMap<>(Portal.getPages(tabContainer, true));
            Portal.PortalPage tab = pages.get(form.getPageId());

            if (null != tab)
            {
                int oldIndex = tab.getIndex();
                Portal.PortalPage pageToSwap = handleMovePortalPage(tabContainer, getUser(), tab, form.getDirection());

                if (null != pageToSwap)
                {
                    properties.put("oldIndex", oldIndex);
                    properties.put("newIndex", tab.getIndex());
                    properties.put("pageId", tab.getPageId());
                    properties.put("pageIdToSwap", pageToSwap.getPageId());
                }
                else
                {
                    properties.put("error", "Unable to move tab.");
                }
            }
            else
            {
                properties.put("error", "Requested tab does not exist.");
            }

            return new ApiSimpleResponse(properties);
        }
    }

    public static class MoveTabForm implements HasViewContext
    {
        private int _direction;
        private String _pageId;
        private ViewContext _viewContext;

        public int getDirection()
        {
            // 0 moves left, 1 moves right.
            return _direction;
        }

        public void setDirection(int direction)
        {
            _direction = direction;
        }

        public String getPageId()
        {
            return _pageId;
        }

        public void setPageId(String pageId)
        {
            _pageId = pageId;
        }

        @Override
        public ViewContext getViewContext()
        {
            return _viewContext;
        }

        @Override
        public void setViewContext(ViewContext viewContext)
        {
            _viewContext = viewContext;
        }
    }

    private Portal.PortalPage handleMovePortalPage(Container c, User user, Portal.PortalPage page, int direction)
    {
        Map<String, Portal.PortalPage> pageMap = new CaseInsensitiveHashMap<>();
        for (Portal.PortalPage pp : Portal.getTabPages(c, true))
            pageMap.put(pp.getPageId(), pp);

        for (FolderTab folderTab : c.getFolderType().getDefaultTabs())
        {
            if (pageMap.containsKey(folderTab.getName()))
            {
                // Issue 46233 : folder tabs can conditionally hide/show themselves at render time, these need to
                // be excluded when adjusting the relative indexes.
                if (!folderTab.isVisible(c, user))
                    pageMap.remove(folderTab.getName());
            }
        }
        List<Portal.PortalPage> pagesList = new ArrayList<>(pageMap.values());
        pagesList.sort(Comparator.comparingInt(Portal.PortalPage::getIndex));

        int visibleIndex;
        for (visibleIndex = 0; visibleIndex < pagesList.size(); visibleIndex++)
        {
            if (pagesList.get(visibleIndex).getIndex() == page.getIndex())
            {
                break;
            }
        }

        if (visibleIndex == pagesList.size())
        {
            return null;
        }

        if (direction == Portal.MOVE_DOWN)
        {
            if (visibleIndex == pagesList.size() - 1)
            {
                return page;
            }

            Portal.PortalPage nextPage = pagesList.get(visibleIndex + 1);

            if (null == nextPage)
                return null;
            Portal.swapPageIndexes(c, page, nextPage);
            return nextPage;
        }
        else
        {
            if (visibleIndex < 1)
            {
                return page;
            }

            Portal.PortalPage prevPage = pagesList.get(visibleIndex - 1);

            if (null == prevPage)
                return null;
            Portal.swapPageIndexes(c, page, prevPage);
            return prevPage;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class RenameTabAction extends MutatingApiAction<TabActionForm>
    {
        public void validateCommand(TabActionForm form, Errors errors)
        {
            Container tabContainer = getContainer().getContainerFor(ContainerType.DataType.tabParent);

            if (tabContainer.getFolderType() == FolderType.NONE)
            {
                errors.reject(ERROR_MSG, "Cannot change tab names in custom folder types.");
            }
            else
            {
                String name = form.getTabName();
                if (StringUtils.isEmpty(name))
                {
                    errors.reject(ERROR_MSG, "A tab name must be specified.");
                    return;
                }

                if (name.length() > 64)
                {
                    errors.reject(ERROR_MSG, "Tab name cannot be longer than 64 characters.");
                    return;
                }

                CaseInsensitiveHashMap<Portal.PortalPage> pages = new CaseInsensitiveHashMap<>(Portal.getPages(tabContainer, true));
                Portal.PortalPage pageToChange = pages.get(form.getTabPageId());
                if (null == pageToChange)
                {
                    errors.reject(ERROR_MSG, "Page cannot be found. Check with your system administrator.");
                    return;
                }

                for (Portal.PortalPage page : pages.values())
                {
                    if (!page.equals(pageToChange))
                    {
                        if (null != page.getCaption() && page.getCaption().equalsIgnoreCase(name))
                        {
                            errors.reject(ERROR_MSG, "A tab with the same name already exists in this folder.");
                            return;
                        }
                        if (page.getPageId().equalsIgnoreCase(name))
                        {
                            if (null != page.getCaption() || Portal.DEFAULT_PORTAL_PAGE_ID.equalsIgnoreCase(name))
                                errors.reject(ERROR_MSG, "You cannot change a tab's name to another tab's original name even if the original name is not visible.");
                            else
                                errors.reject(ERROR_MSG, "A tab with the same name already exists in this folder.");
                            return;
                        }
                    }
                }

                List<FolderTab> folderTabs = tabContainer.getFolderType().getDefaultTabs();
                for (FolderTab folderTab : folderTabs)
                {
                    String folderTabCaption = folderTab.getCaption(getViewContext());
                    if (!folderTab.getName().equalsIgnoreCase(pageToChange.getPageId()) && null != folderTabCaption && folderTabCaption.equalsIgnoreCase(name))
                    {
                        errors.reject(ERROR_MSG, "You cannot change a tab's name to another tab's original name even if the original name is not visible.");
                        return;
                    }
                }
            }
        }

        @Override
        public ApiResponse execute(TabActionForm form, BindException errors)
        {
            ApiSimpleResponse response = new ApiSimpleResponse();
            validateCommand(form, errors);

            if (errors.hasErrors())
            {
                return response;
            }

            Container container = getContainer().getContainerFor(ContainerType.DataType.tabParent);
            CaseInsensitiveHashMap<Portal.PortalPage> pages = new CaseInsensitiveHashMap<>(Portal.getPages(container, true));
            Portal.PortalPage page = pages.get(form.getTabPageId());
            page = page.copy();
            page.setCaption(form.getTabName());
            // Update the page the caption is saved.
            Portal.updatePortalPage(container, page);

            response.put("success", true);
            return response;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class ClearDeletedTabFoldersAction extends MutatingApiAction<DeletedFoldersForm>
    {
        @Override
        public ApiResponse execute(DeletedFoldersForm form, BindException errors)
        {
            if (isBlank(form.getContainerPath()))
                throw new NotFoundException();
            Container container = ContainerManager.getForPath(form.getContainerPath());
            if (container == null)
                throw new NotFoundException();
            if (!container.hasPermission(getUser(), AdminPermission.class))
                throw new UnauthorizedException();
            for (String tabName : form.getResurrectFolders())
            {
                ContainerManager.clearContainerTabDeleted(container, tabName, form.getNewFolderType());
            }
            return new ApiSimpleResponse("success", true);
        }
    }

    @SuppressWarnings("unused")
    public static class DeletedFoldersForm
    {
        private String _containerPath;
        private String _newFolderType;
        private List<String> _resurrectFolders;

        public List<String> getResurrectFolders()
        {
            return _resurrectFolders;
        }

        public void setResurrectFolders(List<String> resurrectFolders)
        {
            _resurrectFolders = resurrectFolders;
        }

        public String getContainerPath()
        {
            return _containerPath;
        }

        public void setContainerPath(String containerPath)
        {
            _containerPath = containerPath;
        }

        public String getNewFolderType()
        {
            return _newFolderType;
        }

        public void setNewFolderType(String newFolderType)
        {
            _newFolderType = newFolderType;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public static class GetFolderTabsAction extends ReadOnlyApiAction<Object>
    {
        @Override
        public Object execute(Object form, BindException errors) throws Exception
        {
            var data = getContainer()
                    .getFolderType()
                    .getAppBar(getViewContext(), getPageConfig())
                    .getButtons()
                    .stream()
                    .map(this::getProperties)
                    .toList();

            return success(data);
        }

        private Map<String, Object> getProperties(NavTree navTree)
        {
            Map<String, Object> props = new HashMap<>();
            props.put("id", navTree.getId());
            props.put("text", navTree.getText());
            props.put("href", navTree.getHref());
            props.put("disabled", navTree.isDisabled());
            return props;
        }
    }

    @SuppressWarnings("unused")
    public static class ShortURLForm
    {
        private String _shortURL;
        private String _fullURL;
        private boolean _delete;

        private List<ShortURLRecord> _savedShortURLs;

        public void setShortURL(String shortURL)
        {
            _shortURL = shortURL;
        }

        public void setFullURL(String fullURL)
        {
            _fullURL = fullURL;
        }

        public void setDelete(boolean delete)
        {
            _delete = delete;
        }

        public String getShortURL()
        {
            return _shortURL;
        }

        public String getFullURL()
        {
            return _fullURL;
        }

        public boolean isDelete()
        {
            return _delete;
        }
    }

    public abstract static class AbstractShortURLAdminAction extends FormViewAction<ShortURLForm>
    {
        @Override
        public void validateCommand(ShortURLForm target, Errors errors) {}

        @Override
        public boolean handlePost(ShortURLForm form, BindException errors) throws Exception
        {
            String shortURL = StringUtils.trimToEmpty(form.getShortURL());
            if (StringUtils.isEmpty(shortURL))
            {
                errors.addError(new LabKeyError("Short URL must not be blank"));
            }
            if (shortURL.endsWith(".url"))
                shortURL = shortURL.substring(0,shortURL.length()-".url".length());
            if (shortURL.contains("#") || shortURL.contains("/") || shortURL.contains("."))
            {
                errors.addError(new LabKeyError("Short URLs may not contain '#' or '/' or '.'"));
            }
            URLHelper fullURL = null;
            if (!form.isDelete())
            {
                String trimmedFullURL = StringUtils.trimToNull(form.getFullURL());
                if (trimmedFullURL == null)
                {
                    errors.addError(new LabKeyError("Target URL must not be blank"));
                }
                else
                {
                    try
                    {
                        fullURL = new URLHelper(trimmedFullURL);
                    }
                    catch (URISyntaxException e)
                    {
                        errors.addError(new LabKeyError("Invalid Target URL. " + e.getMessage()));
                    }
                }
            }
            if (errors.getErrorCount() > 0)
            {
                return false;
            }

            ShortURLService service = ShortURLService.get();
            if (form.isDelete())
            {
                ShortURLRecord shortURLRecord = service.resolveShortURL(shortURL);
                if (shortURLRecord == null)
                {
                    throw new NotFoundException("No such short URL: " + shortURL);
                }
                try
                {
                    service.deleteShortURL(shortURLRecord, getUser());
                }
                catch (ValidationException e)
                {
                    errors.addError(new LabKeyError("Error deleting short URL:"));
                    for(ValidationError error: e.getErrors())
                    {
                        errors.addError(new LabKeyError(error.getMessage()));
                    }
                }

                if (errors.getErrorCount() > 0)
                {
                    return false;
                }
            }
            else
            {
                ShortURLRecord shortURLRecord = service.saveShortURL(shortURL, fullURL, getUser());
                MutableSecurityPolicy policy = new MutableSecurityPolicy(SecurityPolicyManager.getPolicy(shortURLRecord));
                // Add a role assignment to let another group manage the URL. This grants permission to the journal
                // to change where the URL redirects you to after they copy the data
                SecurityPolicyManager.savePolicy(policy, getUser());
            }
            return true;
        }
    }

    @AdminConsoleAction
    public class ShortURLAdminAction extends AbstractShortURLAdminAction
    {
        @Override
        public ModelAndView getView(ShortURLForm form, boolean reshow, BindException errors)
        {
            JspView<ShortURLForm> newView = new JspView<>("/org/labkey/core/admin/createNewShortURL.jsp", form, errors);
            boolean isAppAdmin = getUser().hasRootPermission(ApplicationAdminPermission.class);
            newView.setTitle(isAppAdmin ? "Create New Short URL" : "Short URLs");
            newView.setFrame(WebPartView.FrameType.PORTAL);

            QuerySettings qSettings = new QuerySettings(getViewContext(), "ShortURL", CoreQuerySchema.SHORT_URL_TABLE_NAME);
            qSettings.setBaseSort(new Sort("-Created"));
            QueryView existingView = new QueryView(new CoreQuerySchema(getUser(), getContainer()), qSettings, null);
            if (!isAppAdmin)
            {
                existingView.setButtonBarPosition(DataRegion.ButtonBarPosition.NONE);
            }
            existingView.setTitle("Existing Short URLs");
            existingView.setFrame(WebPartView.FrameType.PORTAL);

            return new VBox(newView, existingView);
        }

        @Override
        public URLHelper getSuccessURL(ShortURLForm form)
        {
            return new ActionURL(ShortURLAdminAction.class, getContainer());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("shortURL");
            addAdminNavTrail(root, "Short URL Admin", getClass());
        }
    }

    @RequiresPermission(ApplicationAdminPermission.class)
    public class UpdateShortURLAction extends AbstractShortURLAdminAction
    {
        @Override
        public ModelAndView getView(ShortURLForm form, boolean reshow, BindException errors)
        {
            var shortUrlRecord = ShortURLService.get().resolveShortURL(form.getShortURL());
            if (shortUrlRecord == null)
            {
                errors.addError(new LabKeyError("Short URL does not exist: " + form.getShortURL()));
                return new SimpleErrorView(errors);
            }
            form.setFullURL(shortUrlRecord.getFullURL());

            JspView<ShortURLForm> view = new JspView<>("/org/labkey/core/admin/updateShortURL.jsp", form, errors);
            view.setTitle("Update Short URL");
            view.setFrame(WebPartView.FrameType.PORTAL);
            return view;
        }

        @Override
        public URLHelper getSuccessURL(ShortURLForm form)
        {
            return new ActionURL(ShortURLAdminAction.class, getContainer());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("shortURL");
            addAdminNavTrail(root, "Update Short URL", getClass());
        }
    }

    // API for reporting client-side exceptions.
    // UNDONE: Throttle by IP to avoid DOS from buggy clients.
    @Marshal(Marshaller.Jackson)
    @SuppressWarnings("UnusedDeclaration")
    @RequiresLogin // Issue 52520: Prevent bots from submitting reports
    @IgnoresForbiddenProjectCheck // Skip the "forbidden project" check since it disallows root
    public static class LogClientExceptionAction extends MutatingApiAction<ExceptionForm>
    {
        @Override
        public Object execute(ExceptionForm form, BindException errors)
        {
            String errorCode = ExceptionUtil.logClientExceptionToMothership(
                form.getStackTrace(),
                form.getExceptionMessage(),
                form.getBrowser(),
                null,
                form.getRequestURL(),
                form.getReferrerURL(),
                form.getUsername()
            );

            Map<String, Object> results = new HashMap<>();
            results.put("errorCode", errorCode);
            results.put("loggedToMothership", errorCode != null);

            return success(results);
        }
    }

    @SuppressWarnings("unused")
    public static class ExceptionForm
    {
        private String _exceptionMessage;
        private String _stackTrace;
        private String _requestURL;
        private String _browser;
        private String _username;
        private String _referrerURL;
        private String _file;
        private String _line;
        private String _platform;

        public String getExceptionMessage()
        {
            return _exceptionMessage;
        }

        public void setExceptionMessage(String exceptionMessage)
        {
            _exceptionMessage = exceptionMessage;
        }

        public String getUsername()
        {
            return _username;
        }

        public void setUsername(String username)
        {
            _username = username;
        }

        public String getStackTrace()
        {
            return _stackTrace;
        }

        public void setStackTrace(String stackTrace)
        {
            _stackTrace = stackTrace;
        }

        public String getRequestURL()
        {
            return _requestURL;
        }

        public void setRequestURL(String requestURL)
        {
            _requestURL = requestURL;
        }

        public String getBrowser()
        {
            return _browser;
        }

        public void setBrowser(String browser)
        {
            _browser = browser;
        }

        public String getReferrerURL()
        {
            return _referrerURL;
        }

        public void setReferrerURL(String referrerURL)
        {
            _referrerURL = referrerURL;
        }

        public String getFile()
        {
            return _file;
        }

        public void setFile(String file)
        {
            _file = file;
        }

        public String getLine()
        {
            return _line;
        }

        public void setLine(String line)
        {
            _line = line;
        }

        public String getPlatform()
        {
            return _platform;
        }

        public void setPlatform(String platform)
        {
            _platform = platform;
        }
    }


    /** generate URLS to seed web-site scanner */
    @SuppressWarnings("UnusedDeclaration")
    @RequiresSiteAdmin
    public static class SpiderAction extends SimpleViewAction<Object>
    {
        @Override
        public void addNavTrail(NavTree root)
        {
            root.addChild("Spider Initialization");
        }

        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            List<String> urls = new ArrayList<>(1000);

            if (getContainer().equals(ContainerManager.getRoot()))
            {
                for (Container c : ContainerManager.getAllChildren(ContainerManager.getRoot()))
                {
                    urls.add(c.getStartURL(getUser()).toString());
                    urls.add(new ActionURL(SpiderAction.class, c).toString());
                }

                Container home = ContainerManager.getHomeContainer();
                for (ActionDescriptor d : SpringActionController.getRegisteredActionDescriptors())
                {
                    ActionURL url = new ActionURL(d.getControllerName(), d.getPrimaryName(), home);
                    urls.add(url.toString());
                }
            }
            else
            {
                DefaultSchema def = DefaultSchema.get(getUser(), getContainer());
                def.getSchemaNames().forEach(name ->
                {
                    QuerySchema q = def.getSchema(name);
                    if (null == q)
                        return;
                    var tableNames = q.getTableNames();
                    if (null == tableNames)
                        return;
                    tableNames.forEach(table ->
                    {
                        try
                        {
                            var t = q.getTable(table);
                            if (null != t)
                            {
                                ActionURL grid = t.getGridURL(getContainer());
                                if (null != grid)
                                    urls.add(grid.toString());
                                else
                                    urls.add(new ActionURL("query", "executeQuery.view", getContainer())
                                            .addParameter("schemaName", q.getSchemaName())
                                            .addParameter("query.queryName", t.getName())
                                            .toString());
                            }
                        }
                        catch (Exception x)
                        {
                            // pass
                        }
                    });
                });

                ModuleLoader.getInstance().getModules().forEach(m ->
                {
                    ActionURL url = m.getTabURL(getContainer(), getUser());
                    if (null != url)
                        urls.add(url.toString());
                });
            }

            return new HtmlView(DIV(urls.stream().map(url -> createHtmlFragment(A(at(href,url),url),BR()))));
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    @RequiresPermission(TroubleshooterPermission.class)
    public static class TestMothershipReportAction extends ReadOnlyApiAction<MothershipReportSelectionForm>
    {
        @Override
        public Object execute(MothershipReportSelectionForm form, BindException errors) throws Exception
        {
            MothershipReport report;
            MothershipReport.Target target = form.isTestMode() ? MothershipReport.Target.test : MothershipReport.Target.local;
            if (MothershipReport.Type.CheckForUpdates.toString().equals(form.getType()))
            {
                report = UsageReportingLevel.generateReport(UsageReportingLevel.valueOf(form.getLevel()), target);
            }
            else
            {
                report = ExceptionUtil.createReportFromThrowable(getViewContext().getRequest(),
                        new SQLException("Intentional exception for testing purposes", "400"),
                        (String)getViewContext().getRequest().getAttribute(ViewServlet.ORIGINAL_URL_STRING),
                        target,
                        ExceptionReportingLevel.valueOf(form.getLevel()), null, null, null);
            }

            final Map<String, Object> params;
            if (report == null)
            {
                params = new LinkedHashMap<>();
            }
            else
            {
                params = report.getJsonFriendlyParams();
                if (form.isSubmit())
                {
                    report.setForwardedFor(form.getForwardedFor());
                    report.run();
                    if (null != report.getUpgradeMessage())
                        params.put("upgradeMessage", report.getUpgradeMessage());
                }
            }
            if (form.isDownload())
            {
                ResponseHelper.setContentDisposition(getViewContext().getResponse(), ResponseHelper.ContentDispositionType.attachment, "metrics.json");
            }
            return new ApiSimpleResponse(params);
        }
    }


    static class MothershipReportSelectionForm
    {
        private String _type = MothershipReport.Type.CheckForUpdates.toString();
        private String _level = UsageReportingLevel.ON.toString();
        private boolean _submit = false;
        private boolean _download = false;
        private String _forwardedFor = null;
        // indicates action is being invoked for dev/test
        private boolean _testMode = false;

        public String getType()
        {
            return _type;
        }

        public void setType(String type)
        {
            _type = type;
        }

        public String getLevel()
        {
            return _level;
        }

        public void setLevel(String level)
        {
            _level = StringUtils.upperCase(level);
        }

        public boolean isSubmit()
        {
            return _submit;
        }

        public void setSubmit(boolean submit)
        {
            _submit = submit;
        }

        public String getForwardedFor()
        {
            return _forwardedFor;
        }

        public void setForwardedFor(String forwardedFor)
        {
            _forwardedFor = forwardedFor;
        }

        public boolean isTestMode()
        {
            return _testMode;
        }

        public void setTestMode(boolean testMode)
        {
            _testMode = testMode;
        }

        public boolean isDownload()
        {
            return _download;
        }

        public void setDownload(boolean download)
        {
            _download = download;
        }
    }


    @RequiresPermission(TroubleshooterPermission.class)
    public class SuspiciousAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            Collection<BlockListFilter.Suspicious> list = BlockListFilter.reportSuspicious();
            HtmlStringBuilder html = HtmlStringBuilder.of();
            if (list.isEmpty())
            {
                html.append("No suspicious activity.\n");
            }
            else
            {
                html.unsafeAppend("<table class='table'>")
                    .unsafeAppend("<thead><th>host (user)</th><th>user-agent</th><th>count</th></thead>\n");
                for (BlockListFilter.Suspicious s : list)
                {
                    html.unsafeAppend("<tr><td>")
                        .append(s.host);
                    if (!isBlank(s.user))
                        html.append(HtmlString.NBSP).append("(" + s.user + ")");
                    html.unsafeAppend("</td><td>")
                        .append(s.userAgent)
                        .unsafeAppend("</td><td>")
                        .append(s.count)
                        .unsafeAppend("</td></tr>\n");
                }
                html.unsafeAppend("</table>");
            }
            return new HtmlView(html);
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addAdminNavTrail(root, "Suspicious activity", SuspiciousAction.class);
        }
    }

    /** This is a very crude API right now, mostly using default serialization of pre-existing objects
     * NOTE: callers should expect that the return shape of this method may and will change in non-backward-compatible ways
     */
    @Marshal(Marshaller.Jackson)
    @RequiresNoPermission
    @AllowedBeforeInitialUserIsSet
    public static class ConfigurationSummaryAction extends ReadOnlyApiAction<Object>
    {
        @Override
        public Object execute(Object o, BindException errors)
        {
            if (!getContainer().isRoot())
                throw new NotFoundException("Must be invoked in the root");

            // requires site-admin, unless there are no users
            if (!UserManager.hasNoRealUsers() && !getContainer().hasPermission(getUser(), AdminOperationsPermission.class))
                throw new UnauthorizedException();

            return getConfigurationJson();
        }

        @Override
        protected ObjectMapper createResponseObjectMapper()
        {
            ObjectMapper result = JsonUtil.createDefaultMapper();
            result.addMixIn(ExternalScriptEngineDefinitionImpl.class, IgnorePasswordMixIn.class);
            return result;
        }

        /* returns a jackson serializable object that reports superset of information returned in admin console */
        private JSONObject getConfigurationJson()
        {
            JSONObject res = new JSONObject();

            res.put("server", AdminBean.getPropertyMap());

            final Map<String,Map<String,Object>> sets = new TreeMap<>();
            var sql = new SQLFragment("SELECT category, name, value FROM prop.propertysets PS inner join prop.properties P on PS.\"set\" = P.\"set\"\n")
                    .append("WHERE objectid = ").appendValue(ContainerManager.getRoot()).append(" AND category IN ('SiteConfig') AND encryption='None' AND LOWER(name) NOT LIKE '%password%'");
            new SqlSelector(CoreSchema.getInstance().getScope(), sql)
                    .forEachMap(m ->
                {
                    String category = (String)m.get("category");
                    String name = (String)m.get("name");
                    Object value = m.get("value");
                    if (!sets.containsKey(category))
                        sets.put(category, new TreeMap<>());
                    sets.get(category).put(name,value);
                }
            );
            res.put("siteSettings", sets);

            HealthCheck.Result result = HealthCheckRegistry.get().checkHealth(Arrays.asList("all"));
            res.put("health", result);

            LabKeyScriptEngineManager mgr = LabKeyScriptEngineManager.get();
            res.put("scriptEngines", mgr.getEngineDefinitions());

            return res;
        }
    }

    @JsonIgnoreProperties(value = { "password", "changePassword", "configuration" })
    private static class IgnorePasswordMixIn
    {
    }

    @AdminConsoleAction()
    public class AllowListAction extends FormViewAction<AllowListForm>
    {
        private AllowListType _type;

        @Override
        public void validateCommand(AllowListForm target, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(AllowListForm form, boolean reshow, BindException errors)
        {
            _type = form.getTypeEnum();

            form.setExistingValuesList(form.getTypeEnum().getValues());

            JspView<AllowListForm> newView = new JspView<>("/org/labkey/core/admin/addNewListValue.jsp", form, errors);
            newView.setTitle("Register New " + form.getTypeEnum().getTitle());
            newView.setFrame(WebPartView.FrameType.PORTAL);
            JspView<AllowListForm> existingView = new JspView<>("/org/labkey/core/admin/existingListValues.jsp", form, errors);
            existingView.setTitle("Existing " + form.getTypeEnum().getTitle() + "s");
            existingView.setFrame(WebPartView.FrameType.PORTAL);

            return new VBox(newView, existingView);
        }

        @Override
        public boolean handlePost(AllowListForm form, BindException errors) throws Exception
        {
            AllowListType allowListType = form.getTypeEnum();
            //handle delete of existing value
            if (form.isDelete())
            {
                String urlToDelete = form.getExistingValue();
                List<String> values = new ArrayList<>(allowListType.getValues());
                for (String value : values)
                {
                    if (null != urlToDelete && urlToDelete.trim().equalsIgnoreCase(value.trim()))
                    {
                        values.remove(value);
                        allowListType.setValues(values, getUser());
                        break;
                    }
                }
            }
            //handle updates - clicking on Save button under Existing will save the updated urls
            else if (form.isSaveAll())
            {
                Set<String> validatedValues = form.validateValues(errors);
                if (errors.hasErrors())
                    return false;

                allowListType.setValues(validatedValues.stream().toList(), getUser());
            }
            //save new external value
            else if (form.isSaveNew())
            {
                Set<String> valueSet = form.validateNewValue(errors);
                if (errors.hasErrors())
                    return false;

                allowListType.setValues(valueSet, getUser());
            }

            return true;
        }

        @Override
        public URLHelper getSuccessURL(AllowListForm form)
        {
            return form.getTypeEnum().getSuccessURL(getContainer());
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic(_type.getHelpTopic());
            addAdminNavTrail(root, String.format("%1$s Admin", _type.getTitle()), getClass());
        }
    }

    public static class AllowListForm
    {
        private String _newValue;
        private String _existingValue;
        private boolean _delete;
        private String _existingValues;
        private boolean _saveAll;
        private boolean _saveNew;
        private String _type;

        private List<String> _existingValuesList;

        public String getNewValue()
        {
            return _newValue;
        }

        @SuppressWarnings("unused")
        public void setNewValue(String newValue)
        {
            _newValue = newValue;
        }

        public String getExistingValue()
        {
            return _existingValue;
        }

        @SuppressWarnings("unused")
        public void setExistingValue(String existingValue)
        {
            _existingValue = existingValue;
        }

        public boolean isDelete()
        {
            return _delete;
        }

        @SuppressWarnings("unused")
        public void setDelete(boolean delete)
        {
            _delete = delete;
        }

        public String getExistingValues()
        {
            return _existingValues;
        }

        @SuppressWarnings("unused")
        public void setExistingValues(String existingValues)
        {
            _existingValues = existingValues;
        }

        public boolean isSaveAll()
        {
            return _saveAll;
        }

        @SuppressWarnings("unused")
        public void setSaveAll(boolean saveAll)
        {
            _saveAll = saveAll;
        }

        public boolean isSaveNew()
        {
            return _saveNew;
        }

        @SuppressWarnings("unused")
        public void setSaveNew(boolean saveNew)
        {
            _saveNew = saveNew;
        }

        public List<String> getExistingValuesList()
        {
            //for updated urls that comes in as String values from the jsp/html form
            if (null != getExistingValues())
            {
                // The JavaScript delimits with "\n". Not sure where these "\r"s are coming from, but we need to strip them.
                return new ArrayList<>(Arrays.asList(getExistingValues().replace("\r", "").split("\n")));
            }
            return _existingValuesList;
        }

        public void setExistingValuesList(List<String> valuesList)
        {
            _existingValuesList = valuesList;
        }

        public String getType()
        {
            return _type;
        }

        @SuppressWarnings("unused")
        public void setType(String type)
        {
            _type = type;
        }

        @NotNull
        public AllowListType getTypeEnum()
        {
            return EnumUtils.getEnum(AllowListType.class, getType(), AllowListType.Redirect);
        }

        @JsonIgnore
        public Set<String> validateNewValue(BindException errors)
        {
            String value = StringUtils.trimToEmpty(getNewValue());
            getTypeEnum().validateValueFormat(value, errors);
            if (errors.hasErrors())
                return null;

            Set<String> valueSet = new CaseInsensitiveHashSet(getTypeEnum().getValues());
            checkDuplicatesByAddition(value, valueSet, errors);
            return valueSet;
        }

        @JsonIgnore
        public Set<String> validateValues(BindException errors)
        {
            List<String> values = getExistingValuesList(); //get values from the form, this includes updated values
            Set<String> valueSet = new CaseInsensitiveHashSet();

            if (null != values && !values.isEmpty())
            {
                for (String value : values)
                {
                    getTypeEnum().validateValueFormat(value, errors);
                    if (errors.hasErrors())
                        continue;

                    checkDuplicatesByAddition(value, valueSet, errors);
                }
            }

            return valueSet;
        }

        /**
         * Adds value to value set unless it is a duplicate, in which case it adds an error
         * @param value to check
         * @param valueSet of existing values
         * @param errors collections of errors observed
         */
        @JsonIgnore
        private void checkDuplicatesByAddition(String value, Set<String> valueSet, BindException errors)
        {
            String trimValue = StringUtils.trimToEmpty(value);
            if (!valueSet.add(trimValue))
                errors.addError(new LabKeyError(String.format("'%1$s' already exists. Duplicate values not allowed.", trimValue)));
        }
    }

    @AdminConsoleAction
    public static class DeleteAllValuesAction extends FormHandlerAction<AllowListForm>
    {
        @Override
        public void validateCommand(AllowListForm form, Errors errors)
        {
        }

        @Override
        public boolean handlePost(AllowListForm form, BindException errors) throws Exception
        {
            form.getTypeEnum().setValues(Collections.emptyList(), getUser());
            return true;
        }

        @Override
        public URLHelper getSuccessURL(AllowListForm form)
        {
            return form.getTypeEnum().getSuccessURL(getContainer());
        }
    }

    public static class ExternalSourcesForm
    {
        private boolean _delete;
        private boolean _saveNew;
        private boolean _saveAll;

        private String _newDirective;
        private String _newHost;
        private String _existingValue;
        private String _existingValues;

        public boolean isDelete()
        {
            return _delete;
        }

        @SuppressWarnings("unused")
        public void setDelete(boolean delete)
        {
            _delete = delete;
        }

        public boolean isSaveNew()
        {
            return _saveNew;
        }

        @SuppressWarnings("unused")
        public void setSaveNew(boolean saveNew)
        {
            _saveNew = saveNew;
        }

        public boolean isSaveAll()
        {
            return _saveAll;
        }

        @SuppressWarnings("unused")
        public void setSaveAll(boolean saveAll)
        {
            _saveAll = saveAll;
        }

        public String getNewDirective()
        {
            return _newDirective;
        }

        @SuppressWarnings("unused")
        public void setNewDirective(String newDirective)
        {
            _newDirective = newDirective;
        }

        public String getNewHost()
        {
            return _newHost;
        }

        @SuppressWarnings("unused")
        public void setNewHost(String newHost)
        {
            _newHost = newHost;
        }

        public String getExistingValue()
        {
            return _existingValue;
        }

        @SuppressWarnings("unused")
        public void setExistingValue(String existingValue)
        {
            _existingValue = existingValue;
        }

        public List<String> getExistingValues()
        {
            return Arrays.stream(StringUtils.trimToEmpty(_existingValues).split("\n"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        }

        @SuppressWarnings("unused")
        public void setExistingValues(String existingValues)
        {
            _existingValues = existingValues;
        }

        private AllowedHost getExistingAllowedHost(BindException errors)
        {
            return getAllowedHost(getExistingValue(), errors);
        }

        private AllowedHost getAllowedHost(String value, BindException errors)
        {
            String[] parts = value.split("\\|", 2); // Stop after the first bar to produce two parts
            if (parts.length != 2)
            {
                errors.addError(new LabKeyError("Can't parse allowed host."));
                return null;
            }
            return validateHost(parts[0], parts[1], errors);
        }

        private List<AllowedHost> getExistingAllowedHosts(BindException errors)
        {
            List<AllowedHost> existing = getExistingValues().stream()
                .map(value-> getAllowedHost(value, errors))
                .toList();

            if (errors.hasErrors())
                return null;

            return checkDuplicates(existing, errors);
        }

        private List<AllowedHost> validateNewAllowedHost(BindException errors) throws JsonProcessingException
        {
            AllowedHost newAllowedHost = validateHost(getNewDirective(), getNewHost(), errors);

            if (errors.hasErrors())
                return null;

            List<AllowedHost> hosts = getSavedAllowedHosts();
            hosts.add(newAllowedHost);

            return checkDuplicates(hosts, errors);
        }

        // Lenient for now: no unknown directives, no blank hosts or hosts with semicolons
        public static AllowedHost validateHost(String directiveString, String host, BindException errors)
        {
            AllowedHost ret = null;

            if (StringUtils.isEmpty(directiveString))
            {
                errors.addError(new LabKeyError("Directive must not be blank"));
            }
            else if (StringUtils.isEmpty(host))
            {
                errors.addError(new LabKeyError("Host must not be blank"));
            }
            else if (host.contains(";"))
            {
                errors.addError(new LabKeyError("Semicolons are not allowed in host names"));
            }
            else
            {
                Directive directive = EnumUtils.getEnum(Directive.class, directiveString);

                if (null == directive)
                {
                    errors.addError(new LabKeyError("Unknown directive: " + directiveString));
                }
                else
                {
                    ret = new AllowedHost(directive, host.trim());
                }
            }

            return ret;
        }

        /**
         * Check for duplicates in hosts: within each Directive, hosts are checked using case-insensitive comparisons

         * @param hosts a list of AllowedHost objects to check for duplicates
         * @param errors errors to populate
         * @return hosts if there are no duplicates, otherwise {@code null}
         */
        public static @Nullable List<AllowedHost> checkDuplicates(List<AllowedHost> hosts, BindException errors)
        {
            // Not a simple Set<AllowedHost> check since we want host check to be case-insensitive
            MultiValuedMap<Directive, String> map = new CaseInsensitiveHashSetValuedMap<>();

            hosts.forEach(allowedHost -> {
                String host = allowedHost.host().trim();
                if (!map.put(allowedHost.directive(), host))
                    errors.addError(new LabKeyError(String.format("'%1$s' already exists. Duplicate values are not allowed.", allowedHost)));
            });

            return errors.hasErrors() ? null : hosts;
        }

        // Returns a mutable list
        public List<AllowedHost> getSavedAllowedHosts() throws JsonProcessingException
        {
            return AllowedExternalResourceHosts.readAllowedHosts();
        }
    }

    @AdminConsoleAction()
    public class ExternalSourcesAction extends FormViewAction<ExternalSourcesForm>
    {
        @Override
        public void validateCommand(ExternalSourcesForm form, Errors errors)
        {
        }

        @Override
        public ModelAndView getView(ExternalSourcesForm form, boolean reshow, BindException errors)
        {
            VBox vbox = new VBox();

            String template = formatCsp(ContentSecurityPolicyFilter.getStashedTemplate(ContentSecurityPolicyType.Enforce), null);
            if (template != null)
            {
                String substituted = formatCsp(ContentSecurityPolicyFilter.getSubstitutedCsp(ContentSecurityPolicyType.Enforce, getViewContext().getRequest()), "Enforce CSP is disabled!");
                vbox.addView(new HtmlView("Current Enforce CSP",
                    TABLE(
                        TR(
                            TH(STRONG("With Substitution Placeholders")), TH(STRONG("With Substituted Values"))
                        ),
                        TR(
                            TD(at(style, "padding-right: 20px;"), PRE(template)), TD(PRE(substituted))
                        )
                    )
                ));
            }

            JspView<ExternalSourcesForm> newView = new JspView<>("/org/labkey/core/admin/addNewExternalSource.jsp", form, errors);
            boolean isTroubleshooter = !getContainer().hasPermission(getUser(), ApplicationAdminPermission.class);
            newView.setTitle(isTroubleshooter ? "Overview" : "Register New External Resource Host");
            newView.setFrame(WebPartView.FrameType.PORTAL);
            vbox.addView(newView);

            JspView<ExternalSourcesForm> existingView = new JspView<>("/org/labkey/core/admin/existingExternalSources.jsp", form, errors);
            existingView.setTitle("Existing External Resource Hosts");
            existingView.setFrame(WebPartView.FrameType.PORTAL);
            vbox.addView(existingView);

            return vbox;
        }

        private static final String UPGRADE_INSECURE_REQUESTS = "${UPGRADE.INSECURE.REQUESTS}";

        private String formatCsp(@Nullable String csp, String defaultValue)
        {
            if (csp != null)
            {
                // There's no semicolon at the end of this substitution, but we want it to show it on its own line
                csp = csp.replace(UPGRADE_INSECURE_REQUESTS + " ", UPGRADE_INSECURE_REQUESTS + "\n");
                return Arrays.stream(csp.split(";"))
                    .map(String::trim)
                    .collect(Collectors.joining(" ;\n"));
            }
            else
            {
                return defaultValue;
            }
        }

        private static final Object HOST_LOCK = new Object();

        @Override
        public boolean handlePost(ExternalSourcesForm form, BindException errors) throws Exception
        {
            List<AllowedHost> allowedHosts = null;

            // Multiple requests could access this in parallel, so synchronize access, Issue 53457
            synchronized (HOST_LOCK)
            {
                //handle delete of an existing value
                if (form.isDelete())
                {
                    AllowedHost subToDelete = form.getExistingAllowedHost(errors);
                    if (errors.hasErrors())
                        return false;
                    allowedHosts = form.getSavedAllowedHosts();
                    var iter = allowedHosts.listIterator();
                    while (iter.hasNext())
                    {
                        AllowedHost sub = iter.next();
                        if (sub.equals(subToDelete))
                        {
                            iter.remove();
                            break;
                        }
                    }
                }
                //handle updates - clicking on Save button under Existing will save the updated hosts
                else if (form.isSaveAll())
                {
                    allowedHosts = form.getExistingAllowedHosts(errors);
                    if (errors.hasErrors())
                        return false;
                }
                //save new external value
                else if (form.isSaveNew())
                {
                    allowedHosts = form.validateNewAllowedHost(errors);
                }

                if (errors.hasErrors())
                    return false;

                AllowedExternalResourceHosts.saveAllowedHosts(allowedHosts, getUser());
            }

            return true;
        }

        @Override
        public URLHelper getSuccessURL(ExternalSourcesForm form)
        {
            return null;
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            setHelpTopic("externalHosts");
            addAdminNavTrail(root, "Allowed External Resource Hosts", getClass());
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class ProjectSettingsAction extends ProjectSettingsViewPostAction<ProjectSettingsForm>
    {
        @Override
        protected LookAndFeelView getTabView(ProjectSettingsForm form, boolean reshow, BindException errors)
        {
            return new LookAndFeelView(errors);
        }

        @Override
        public void validateCommand(ProjectSettingsForm form, Errors errors)
        {
        }

        @Override
        public boolean handlePost(ProjectSettingsForm form, BindException errors) throws Exception
        {
            return saveProjectSettings(getContainer(), getUser(), form, errors);
        }
    }

    private static boolean saveProjectSettings(Container c, User user, ProjectSettingsForm form, BindException errors)
    {
        WriteableLookAndFeelProperties props = LookAndFeelProperties.getWriteableInstance(c);
        boolean hasAdminOpsPerm = c.hasPermission(user, AdminOperationsPermission.class);

        // Site-only properties

        if (c.isRoot())
        {
            DateParsingMode dateParsingMode = DateParsingMode.fromString(form.getDateParsingMode());
            props.setDateParsingMode(dateParsingMode);

            if (hasAdminOpsPerm)
            {
                String customWelcome = form.getCustomWelcome();
                String welcomeUrl = StringUtils.trimToNull(customWelcome);
                if ("/".equals(welcomeUrl) || AppProps.getInstance().getContextPath().equalsIgnoreCase(welcomeUrl))
                {
                    errors.reject(SpringActionController.ERROR_MSG, "Invalid welcome URL. The url cannot equal '/' or the contextPath (" + AppProps.getInstance().getContextPath() + ")");
                }
                else
                {
                    props.setCustomWelcome(welcomeUrl);
                }
            }
        }

        // Site & project properties

        boolean shouldInherit = form.getShouldInherit();
        if (shouldInherit != SecurityManager.shouldNewSubfoldersInheritPermissions(c))
        {
            SecurityManager.setNewSubfoldersInheritPermissions(c, user, shouldInherit);
        }

        setProperty(form.isSystemDescriptionInherited(), props::clearSystemDescription, () -> props.setSystemDescription(form.getSystemDescription()));
        setProperty(form.isSystemShortNameInherited(), props::clearSystemShortName, () -> props.setSystemShortName(form.getSystemShortName()));
        setProperty(form.isThemeNameInherited(), props::clearThemeName, () -> props.setThemeName(form.getThemeName()));
        setProperty(form.isFolderDisplayModeInherited(), props::clearFolderDisplayMode, () -> props.setFolderDisplayMode(FolderDisplayMode.fromString(form.getFolderDisplayMode())));
        setProperty(form.isApplicationMenuDisplayModeInherited(), props::clearApplicationMenuDisplayMode, () -> props.setApplicationMenuDisplayMode(FolderDisplayMode.fromString(form.getApplicationMenuDisplayMode())));
        setProperty(form.isHelpMenuEnabledInherited(), props::clearDocumentationMenuEnabled, () -> props.setDocumentationMenuEnabled(form.isHelpMenuEnabled()));

        // a few properties on this page should be restricted to operational permissions (i.e. site admin)
        if (hasAdminOpsPerm)
        {
            setProperty(form.isSystemEmailAddressInherited(), props::clearSystemEmailAddress, () -> {
                String systemEmailAddress = form.getSystemEmailAddress();
                try
                {
                    // this will throw an InvalidEmailException for invalid email addresses
                    ValidEmail email = new ValidEmail(systemEmailAddress);
                    props.setSystemEmailAddress(email);
                }
                catch (ValidEmail.InvalidEmailException e)
                {
                    errors.reject(SpringActionController.ERROR_MSG, "Invalid System Email Address: ["
                            + e.getBadEmail() + "]. Please enter a valid email address.");
                }
            });

            setProperty(form.isCustomLoginInherited(), props::clearCustomLogin, () -> {
                String customLogin = form.getCustomLogin();
                if (props.isValidUrl(customLogin))
                {
                    props.setCustomLogin(customLogin);
                }
                else
                {
                    errors.reject(SpringActionController.ERROR_MSG, "Invalid login URL. Should be in the form <module>-<name>.");
                }
            });
        }

        setProperty(form.isCompanyNameInherited(), props::clearCompanyName, () -> props.setCompanyName(form.getCompanyName()));
        setProperty(form.isLogoHrefInherited(), props::clearLogoHref, () -> props.setLogoHref(form.getLogoHref()));
        setProperty(form.isReportAProblemPathInherited(), props::clearReportAProblemPath, () -> props.setReportAProblemPath(form.getReportAProblemPath()));
        setProperty(form.isSupportEmailInherited(), props::clearSupportEmail, () -> {
            String supportEmail = form.getSupportEmail();

            if (!isBlank(supportEmail))
            {
                try
                {
                    // this will throw an InvalidEmailException for invalid email addresses
                    ValidEmail email = new ValidEmail(supportEmail);
                    props.setSupportEmail(email.toString());
                }
                catch (ValidEmail.InvalidEmailException e)
                {
                    errors.reject(SpringActionController.ERROR_MSG, "Invalid Support Email Address: ["
                            + e.getBadEmail() + "]. Please enter a valid email address.");
                }
            }
            else
            {
                // This stores a blank value, not null (which would mean inherit)
                props.setSupportEmail(null);
            }
        });

        boolean success = saveFolderSettings(c, user, props, form, errors);

        if (success)
        {
            // Bump the look & feel revision so browsers retrieve the new theme stylesheet
            WriteableAppProps.incrementLookAndFeelRevisionAndSave();
        }

        return success;
    }

    private static void setProperty(boolean inherited, Runnable clear, Runnable set)
    {
        if (inherited)
            clear.run();
        else
            set.run();
    }

    // Same as ProjectSettingsAction, but provides special admin console permissions handling
    @AdminConsoleAction(ApplicationAdminPermission.class)
    public static class LookAndFeelSettingsAction extends ProjectSettingsAction
    {
        @Override
        protected TYPE getType()
        {
            return TYPE.LookAndFeelSettings;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class UpdateContainerSettingsAction extends MutatingApiAction<FolderSettingsForm>
    {
        @Override
        public Object execute(FolderSettingsForm form, BindException errors)
        {
            boolean saved = saveFolderSettings(getContainer(), getUser(), LookAndFeelProperties.getWriteableFolderInstance(getContainer()), form, errors);

            ApiSimpleResponse response = new ApiSimpleResponse();
            response.put("success", saved && !errors.hasErrors());
            return response;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class ResourcesAction extends ProjectSettingsViewPostAction<Object>
    {
        @Override
        protected JspView<?> getTabView(Object o, boolean reshow, BindException errors)
        {
            LookAndFeelBean bean = new LookAndFeelBean();
            return new JspView<>("/org/labkey/core/admin/lookAndFeelResources.jsp", bean, errors);
        }

        @Override
        public void validateCommand(Object target, Errors errors)
        {
        }

        @Override
        public boolean handlePost(Object o, BindException errors)
        {
            Container c = getContainer();
            Map<String, MultipartFile> fileMap = getFileMap();

            for (ResourceType type : ResourceType.values())
            {
                MultipartFile file = fileMap.get(type.name());

                if (file != null && !file.isEmpty())
                {
                    try
                    {
                        type.save(file, c, getUser());
                    }
                    catch (Exception e)
                    {
                        errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
                        return false;
                    }
                }
            }

            // Note that audit logging happens via the attachment code, so we don't log separately here

            // Bump the look & feel revision so browsers retrieve the new logo, custom stylesheet, etc.
            WriteableAppProps.incrementLookAndFeelRevisionAndSave();

            return true;
        }
    }

    // Same as ResourcesAction, but provides special admin console permissions handling
    @AdminConsoleAction
    public static class AdminConsoleResourcesAction extends ResourcesAction
    {
        @Override
        protected TYPE getType()
        {
            return TYPE.LookAndFeelSettings;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class MenuBarAction extends ProjectSettingsViewAction
    {
        @Override
        protected HttpView<?> getTabView()
        {
            if (getContainer().isRoot())
                return HtmlView.err("Menu bar must be configured for each project separately.");

            WebPartView<?> v = new JspView<>("/org/labkey/core/admin/editMenuBar.jsp", null);
            v.setView("menubar", new VBox());
            Portal.populatePortalView(getViewContext(), Portal.DEFAULT_PORTAL_PAGE_ID, v, false, true, true, false);

            return v;
        }
    }

    @RequiresPermission(AdminPermission.class)
    public static class FilesAction extends ProjectSettingsViewPostAction<FilesForm>
    {
        @Override
        protected HttpView<?> getTabView(FilesForm form, boolean reshow, BindException errors)
        {
            Container c = getContainer();

            if (c.isRoot())
                return HtmlView.err("Files must be configured for each project separately.");

            if (!reshow || form.isPipelineRootForm())
            {
                try
                {
                    AdminController.setFormAndConfirmMessage(getViewContext(), form);
                }
                catch (IllegalArgumentException e)
                {
                    errors.reject(SpringActionController.ERROR_MSG, e.getMessage());
                }
            }
            VBox box = new VBox();
            JspView<FilesForm> view = new JspView<>("/org/labkey/core/admin/view/filesProjectSettings.jsp", form, errors);
            String title = "Configure File Root";
            if (CloudStoreService.get() != null)
                title += " And Enable Cloud Stores";
            view.setTitle(title);
            box.addView(view);

            // only site admins (i.e. AdminOperationsPermission) can configure the pipeline root
            if (c.hasPermission(getViewContext().getUser(), AdminOperationsPermission.class))
            {
                SetupForm setupForm = SetupForm.init(c);
                setupForm.setShowAdditionalOptionsLink(true);
                setupForm.setErrors(errors);
                PipeRoot pipeRoot = SetupForm.getPipelineRoot(c);

                if (pipeRoot != null)
                {
                    for (String errorMessage : pipeRoot.validate())
                        errors.addError(new LabKeyError(errorMessage));
                }
                JspView<?> pipelineView = (JspView<?>) PipelineService.get().getSetupView(setupForm);
                pipelineView.setTitle("Configure Data Processing Pipeline");
                box.addView(pipelineView);
            }

            return box;
        }

        @Override
        public void validateCommand(FilesForm form, Errors errors)
        {
            if (!form.isPipelineRootForm() && !form.isDisableFileSharing() && !form.hasSiteDefaultRoot() && !form.isCloudFileRoot())
            {
                String root = StringUtils.trimToNull(form.getFolderRootPath());
                if (root != null)
                {
                    File f = new File(root);
                    if (!f.exists() || !f.isDirectory())
                    {
                        errors.reject(SpringActionController.ERROR_MSG, "File root '" + root + "' does not appear to be a valid directory accessible to the server at " + getViewContext().getRequest().getServerName() + ".");
                    }
                }
                else
                    errors.reject(SpringActionController.ERROR_MSG, "A Project specified file root cannot be blank, to disable file sharing for this project, select the disable option.");
            }
            else if (form.isCloudFileRoot())
            {
                AdminController.validateCloudFileRoot(form, getContainer(), errors);
            }
        }

        @Override
        public boolean handlePost(FilesForm form, BindException errors) throws Exception
        {
            FileContentService service = FileContentService.get();
            if (service != null)
            {
                if (form.isPipelineRootForm())
                    return PipelineService.get().savePipelineSetup(getViewContext(), form, errors);
                else
                {
                    AdminController.setFileRootFromForm(getViewContext(), form, errors);
                }
            }

            // Cloud settings
            AdminController.setEnabledCloudStores(getViewContext(), form, errors);

            return !errors.hasErrors();
        }

        @Override
        public URLHelper getSuccessURL(FilesForm form)
        {
            ActionURL url = new AdminController.AdminUrlsImpl().getProjectSettingsFileURL(getContainer());
            if (form.isPipelineRootForm())
            {
                url.addParameter("piperootSet", true);
            }
            else
            {
                if (form.isFileRootChanged())
                    url.addParameter("rootSet", form.getMigrateFilesOption());
                if (form.isEnabledCloudStoresChanged())
                    url.addParameter("cloudChanged", true);
            }
            return url;
        }
    }

    public static class LookAndFeelView extends JspView<LookAndFeelBean>
    {
        LookAndFeelView(BindException errors)
        {
            super("/org/labkey/core/admin/lookAndFeelProperties.jsp", new LookAndFeelBean(), errors);
        }
    }


    public static class LookAndFeelBean
    {
        public final HtmlString helpLink = new HelpTopic("customizeLook").getSimpleLinkHtml("more info...");
        public final HtmlString welcomeLink = new HelpTopic("customizeLook").getSimpleLinkHtml("more info...");
        public final HtmlString customColumnRestrictionHelpLink = new HelpTopic("chartTrouble").getSimpleLinkHtml("more info...");
    }

    @RequiresPermission(SiteAdminPermission.class)
    public static class AdjustSystemTimestampsAction extends FormViewAction<AdjustTimestampsForm>
    {
        @Override
        public void addNavTrail(NavTree root)
        {
        }

        @Override
        public void validateCommand(AdjustTimestampsForm form, Errors errors)
        {
            if (form.getHourDelta() == null || form.getHourDelta() == 0)
                errors.reject(ERROR_MSG, "You must specify a non-zero value for 'Hour Delta'");
        }

        @Override
        public ModelAndView getView(AdjustTimestampsForm form, boolean reshow, BindException errors) throws Exception
        {
            return new JspView<>("/org/labkey/core/admin/adjustTimestamps.jsp", form, errors);
        }

        private void updateFields(TableInfo tInfo, Collection<String> fieldNames, int delta)
        {
            SQLFragment sql = new SQLFragment();
            DbSchema schema = tInfo.getSchema();
            String comma = "";
            List<String> updating = new ArrayList<>();

            for (String fieldName: fieldNames)
            {
                ColumnInfo col = tInfo.getColumn(FieldKey.fromParts(fieldName));
                if (col != null && col.getJdbcType() == JdbcType.TIMESTAMP)
                {
                    updating.add(fieldName);
                    if (sql.isEmpty())
                        sql.append("UPDATE ").append(tInfo, "").append(" SET ");
                    sql.append(comma)
                            .append(String.format(" %s = {fn timestampadd(SQL_TSI_HOUR, %d, %s)}", col.getSelectIdentifier(), delta, col.getSelectIdentifier()));
                    comma = ", ";
                }
            }

            if (!sql.isEmpty())
            {
                logger.info("Updating {} in table {}.{}", updating, schema.getName(), tInfo.getName());
                logger.debug(sql.toDebugString());
                int numRows = new SqlExecutor(schema).execute(sql);
                logger.info("Updated {} rows for table {}.{}", numRows, schema.getName(), tInfo.getName());
            }
        }

        @Override
        public boolean handlePost(AdjustTimestampsForm form, BindException errors) throws Exception
        {
            List<String> toUpdate = Arrays.asList("Created", "Modified", "lastIndexed", "diCreated", "diModified");
            logger.info("Adjusting all {} timestamp fields in all tables by {} hours.", toUpdate, form.getHourDelta());
            DbScope scope = DbScope.getLabKeyScope();
            try (DbScope.Transaction t = scope.ensureTransaction())
            {
                ModuleLoader.getInstance().getModules().forEach(module -> {
                    logger.info("==> Beginning update of timestamps for module: {}", module.getName());
                    module.getSchemaNames().stream().sorted().forEach(schemaName -> {
                        DbSchema schema = DbSchema.get(schemaName, DbSchemaType.Module);
                        scope.invalidateSchema(schema); // Issue 44452: assure we have a fresh set of tables to work from
                        schema.getTableNames().forEach(tableName -> {
                            TableInfo tInfo = schema.getTable(tableName);
                            if (tInfo.getTableType() == DatabaseTableType.TABLE)
                            {
                                updateFields(tInfo, toUpdate, form.getHourDelta());
                            }
                        });
                    });
                    logger.info("<== DONE updating timestamps for module: {}", module.getName());
                });
                t.commit();
            }
            logger.info("DONE Adjusting all {} timestamp fields in all tables by {} hours.", toUpdate, form.getHourDelta());
            return true;
        }

        @Override
        public URLHelper getSuccessURL(AdjustTimestampsForm adjustTimestampsForm)
        {
            return PageFlowUtil.urlProvider(AdminUrls.class).getAdminConsoleURL();
        }
    }

    public static class AdjustTimestampsForm
    {
        private Integer hourDelta;

        public Integer getHourDelta()
        {
            return hourDelta;
        }

        public void setHourDelta(Integer hourDelta)
        {
            this.hourDelta = hourDelta;
        }
    }

    @RequiresPermission(TroubleshooterPermission.class)
    public class ViewUsageStatisticsAction extends SimpleViewAction<Object>
    {
        @Override
        public ModelAndView getView(Object o, BindException errors)
        {
            return ModuleHtmlView.get(ModuleLoader.getInstance().getModule("core"), ModuleHtmlView.getGeneratedViewPath("ViewUsageStatistics"));
        }

        @Override
        public void addNavTrail(NavTree root)
        {
            addAdminNavTrail(root, "Usage Statistics", this.getClass());
        }
    }

    private static final URI LABKEY_ORG_REPORT_URI_ACTION;
    private static final URI LABKEY_ORG_REPORT_TO_ACTION;

    static
    {
        LABKEY_ORG_REPORT_URI_ACTION = URI.create("https://www.labkey.org/admin-contentSecurityPolicyReport.api");
        LABKEY_ORG_REPORT_TO_ACTION = URI.create("https://www.labkey.org/admin-contentSecurityPolicyReportTo.api");
    }

    // report-to endpoints get sent a JSON array of reports. Use Jackson to deserialize these into a List<JSONObject>.
    public static class ReportToJsonObjects extends ArrayList<JSONObject>
    {
    }

    @RequiresNoPermission
    @CSRF(CSRF.Method.NONE)
    @Marshal(Marshaller.Jackson)
    public static class ContentSecurityPolicyReportToAction extends BaseContentSecurityPolicyReportAction<ReportToJsonObjects>
    {
        @Override
        public void handleReports(ReportToJsonObjects jsonObjects, HttpServletRequest request, String userAgent) throws IOException, InterruptedException
        {
            JSONArray reportsToForward = new JSONArray();

            jsonObjects.forEach(jsonObject -> {
                if (handleOneReport(jsonObject, request, userAgent, "body", "blockedURL", "documentURL"))
                    reportsToForward.put(jsonObject);
            });

            if (!reportsToForward.isEmpty())
                forwardReports(LABKEY_ORG_REPORT_TO_ACTION, request, reportsToForward.toString(2));
        }

        @Override
        protected ObjectMapper createRequestObjectMapper()
        {
            // Annoyingly, Chrome posts an array of JSON objects but Safari posts individual JSON objects. Set a flag
            // that ensures both cases deserialize into List<JSONObject>.
            return JsonUtil.DEFAULT_MAPPER.copy().enable(DeserializationFeature.ACCEPT_SINGLE_VALUE_AS_ARRAY);
        }
    }

    @RequiresNoPermission
    @CSRF(CSRF.Method.NONE)
    public static class ContentSecurityPolicyReportAction extends BaseContentSecurityPolicyReportAction<SimpleApiJsonForm>
    {
        @Override
        public void handleReports(SimpleApiJsonForm form, HttpServletRequest request, String userAgent) throws IOException, InterruptedException
        {
            JSONObject jsonObject = form.getJsonObject();
            if (handleOneReport(jsonObject, request, userAgent, "csp-report", "blocked-uri", "document-uri"))
                forwardReports(LABKEY_ORG_REPORT_URI_ACTION, request, jsonObject.toString(2));
        }
    }

    protected abstract static class BaseContentSecurityPolicyReportAction<FORM> extends ReadOnlyApiAction<FORM>
    {
        private static final Logger _log = LogHelper.getLogger(ContentSecurityPolicyReportAction.class, "CSP warnings");

        // recent reports, to help avoid log spam
        private static final Map<String, Boolean> reports = Collections.synchronizedMap(new LRUMap<>(20));

        @Override
        protected String getCommandClassMethodName()
        {
            return "handleReports";
        }

        abstract public void handleReports(FORM form, HttpServletRequest request, String userAgent) throws IOException, InterruptedException;

        @Override
        public Object execute(FORM form, BindException errors) throws Exception
        {
            var ret = new JSONObject().put("success", true);

            // fail fast
            if (!_log.isWarnEnabled())
                return ret;

            var request = getViewContext().getRequest();
            assert null != request;

            var userAgent = request.getHeader("User-Agent");
            if (PageFlowUtil.isRobotUserAgent(userAgent) && !_log.isDebugEnabled())
                return ret;

            handleReports(form, request, userAgent);

            return ret;
        }

        // Returns true if the report should be forwarded
        protected boolean handleOneReport(JSONObject jsonObj, HttpServletRequest request, String userAgent, String bodyKey, String blockedUrlKey, String documentUrlKey)
        {
            if (null != jsonObj)
            {
                JSONObject cspReport = jsonObj.optJSONObject(bodyKey);
                if (cspReport != null)
                {
                    String blockedUrl = cspReport.optString(blockedUrlKey, null);

                    // Issue 52933 - suppress base-uri problems from a crawler or bot on labkey.org
                    if (blockedUrl != null &&
                        blockedUrl.startsWith("https://labkey.org%2C") &&
                        blockedUrl.endsWith("undefined") &&
                        !_log.isDebugEnabled())
                    {
                        return false;
                    }

                    String documentUrl = cspReport.optString(documentUrlKey, null);
                    if (documentUrl != null)
                    {
                        URLHelper documentUrlHelper;
                        try
                        {
                            documentUrlHelper = new URLHelper(documentUrl);
                        }
                        catch (URISyntaxException e)
                        {
                            throw new RuntimeException(e);
                        }

                        // URL parameter that tells us to bypass suppression of redundant logging
                        // Used to make sure that tests of CSP logging are deterministic and convenient
                        boolean bypassCspDedupe = "true".equals(documentUrlHelper.getParameter("bypassCspDedupe"));
                        String path = documentUrlHelper.deleteParameters().getURIString();
                        if (null == reports.put(path, Boolean.TRUE) || _log.isDebugEnabled() || bypassCspDedupe)
                        {
                            // Don't modify forwarded reports; they already have user, ip, user_agent, etc. from the forwarding server.
                            boolean forwarded = jsonObj.optBoolean("forwarded", false);
                            if (!forwarded)
                            {
                                jsonObj.put("labkeyVersion", AppProps.getInstance().getReleaseVersion());
                                jsonObj.put("cspVersion", ContentSecurityPolicyFilter.getCspVersion(cspReport.optString("disposition", null)));
                                User user = getUser();
                                String email = null;
                                // If the user is not logged in, we may still be able to snag the email address from our cookie
                                if (user.isGuest())
                                    email = LoginController.getEmailFromCookie(request);
                                if (null == email)
                                    email = user.getEmail();
                                jsonObj.put("user", email);
                                String ipAddress = request.getHeader("X-FORWARDED-FOR");
                                if (ipAddress == null)
                                    ipAddress = request.getRemoteAddr();
                                jsonObj.put("ip", ipAddress);
                                if (isNotBlank(userAgent) && !jsonObj.has("user_agent"))
                                    jsonObj.put("user_agent", userAgent);
                            }

                            var jsonStr = jsonObj.toString(2);
                            _log.warn("ContentSecurityPolicy warning on page: {}\n{}", documentUrl, jsonStr);

                            boolean shouldForward = !forwarded && OptionalFeatureService.get().isFeatureEnabled(ContentSecurityPolicyFilter.FEATURE_FLAG_FORWARD_CSP_REPORTS);
                            if (shouldForward)
                                jsonObj.put("forwarded", true);

                            return shouldForward;
                        }
                    }
                }
            }

            return false;
        }

        protected void forwardReports(URI destination, HttpServletRequest request, String content) throws IOException, InterruptedException
        {
            // Create an HttpClient
            try (HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build())
            {
                // Create the POST request
                HttpRequest remoteRequest = HttpRequest.newBuilder()
                    .uri(destination)
                    .header("Content-Type", request.getContentType()) // Use whatever the browser set
                    .POST(HttpRequest.BodyPublishers.ofString(content))
                    .build();

                // Send the request and get the response
                HttpResponse<String> response = client.send(remoteRequest, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() != 200)
                {
                    _log.error("ContentSecurityPolicy report forwarding to https://www.labkey.org failed: {}\n{}", response.statusCode(), response.body());
                }
                else
                {
                    JSONObject jsonResponse = new JSONObject(response.body());
                    boolean success = jsonResponse.optBoolean("success", false);
                    if (!success)
                    {
                        _log.error("ContentSecurityPolicy report forwarding to https://www.labkey.org failed: {}", jsonResponse);
                    }
                }
            }
        }
    }

    @RequiresPermission(AdminOperationsPermission.class) // Must be site administrator
    public static class DeleteEncryptedContentAction extends ConfirmAction<Object>
    {
        @Override
        public void validateCommand(Object o, Errors errors)
        {
        }

        @Override
        public ModelAndView getConfirmView(Object o, BindException errors) throws Exception
        {
            getPageConfig().setShowHeader(false);
            getPageConfig().setTitle("Delete Encrypted Content?");
            return HtmlView.of("Are you sure you want to delete all encrypted content in the server? This " +
                "content can include passwords to external systems, authentication configurations, users' TOTP " +
                "settings, etc. The encrypted content will be cleared and can't be recovered. Restart the server " +
                "after clearing encrypted content.");
        }

        @Override
        public boolean handlePost(Object o, BindException errors) throws Exception
        {
            Encryption.deleteEncryptedContent(getUser());
            return true;
        }

        @Override
        public @NotNull URLHelper getSuccessURL(Object o)
        {
            return getShowAdminURL();
        }
    }

    public static class TestCase extends AbstractActionPermissionTest
    {
        @Override
        @Test
        public void testActionPermissions()
        {
            User user = TestContext.get().getUser();
            assertTrue(user.hasSiteAdminPermission());

            AdminController controller = new AdminController();

            assertForNoPermission(user,
                new ContentSecurityPolicyReportAction(),
                new ContentSecurityPolicyReportToAction()
            );

            // @RequiresPermission(ReadPermission.class)
            assertForReadPermission(user, false,
                new GetModulesAction(),
                new GetFolderTabsAction(),
                new ClearDeletedTabFoldersAction()
            );

            // @RequiresPermission(DeletePermission.class)
            assertForUpdateOrDeletePermission(user,
                new DeleteFolderAction()
            );

            // @RequiresPermission(AdminPermission.class)
            assertForAdminPermission(user,
                controller.new CustomizeEmailAction(),
                controller.new FolderAliasesAction(),
                controller.new MoveFolderAction(),
                controller.new MoveTabAction(),
                controller.new RenameFolderAction(),
                controller.new ReorderFoldersAction(),
                controller.new ReorderFoldersApiAction(),
                controller.new SiteValidationAction(),
                new AddTabAction(),
                new ConfirmProjectMoveAction(),
                new CreateFolderAction(),
                new CustomizeMenuAction(),
                new DeleteCustomEmailAction(),
                new FilesAction(),
                new MenuBarAction(),
                new ProjectSettingsAction(),
                new RenameContainerAction(),
                new RenameTabAction(),
                new ResetPropertiesAction(),
                new ResetQueryStatisticsAction(),
                new ResetResourceAction(),
                new ResourcesAction(),
                new RevertFolderAction(),
                new SetFolderPermissionsAction(),
                new SetInitialFolderSettingsAction(),
                new ShowTabAction()
            );

            //TODO @RequiresPermission(AdminReadPermission.class)
            //controller.new TestMothershipReportAction()

            // @RequiresPermission(AdminOperationsPermission.class)
            assertForAdminOperationsPermission(ContainerManager.getRoot(), user,
                controller.new DbCheckerAction(),
                controller.new DeleteModuleAction(),
                controller.new DoCheckAction(),
                controller.new EmailTestAction(),
                controller.new ShowNetworkDriveTestAction(),
                controller.new ValidateDomainsAction(),
                new OptionalFeatureAction(),
                new GetSchemaXmlDocAction(),
                new RecreateViewsAction(),
                new DeleteEncryptedContentAction()
            );

            // @AdminConsoleAction
            assertForAdminPermission(ContainerManager.getRoot(), user,
                controller.new ActionsAction(),
                controller.new CachesAction(),
                controller.new ConfigureSystemMaintenanceAction(),
                controller.new CustomizeSiteAction(),
                controller.new DumpHeapAction(),
                controller.new ExecutionPlanAction(),
                controller.new FolderTypesAction(),
                controller.new MemTrackerAction(),
                controller.new ModulesAction(),
                controller.new QueriesAction(),
                controller.new QueryStackTracesAction(),
                controller.new ResetErrorMarkAction(),
                controller.new ShortURLAdminAction(),
                controller.new ShowAllErrorsAction(),
                controller.new ShowErrorsSinceMarkAction(),
                controller.new ShowPrimaryLogAction(),
                controller.new ShowCspReportLogAction(),
                controller.new ShowThreadsAction(),
                new ExportActionsAction(),
                new ExportQueriesAction(),
                new MemoryChartAction(),
                new ShowAdminAction()
            );

            // @RequiresSiteAdmin
            assertForRequiresSiteAdmin(user,
                controller.new EnvironmentVariablesAction(),
                controller.new SecretsAction(),
                controller.new SystemMaintenanceAction(),
                controller.new SystemPropertiesAction(),
                new GetPendingRequestCountAction(),
                new InstallCompleteAction(),
                new NewInstallSiteSettingsAction()
            );

            assertForTroubleshooterPermission(ContainerManager.getRoot(), user,
                controller.new OptionalFeaturesAction(),
                controller.new ShowModuleErrorsAction(),
                new ModuleStatusAction(),
                controller.new ViewUsageStatisticsAction()
            );
        }
    }

    public static class SerializationTest extends PipelineJob.TestSerialization
    {
        static class TestJob extends PipelineJob
        {
            PermissionsContext _permissionsContext;
            PermissionsContext _permissionsContext1;
            PermissionsContext _permissionsContext2;

            @Override
            public URLHelper getStatusHref()
            {
                return null;
            }

            @Override
            public String getDescription()
            {
                return "Test Job";
            }
        }

        @Test
        public void testSerialization()
        {
            TestJob job = new TestJob();
            TestContext ctx = TestContext.get();
            ViewContext viewContext = new ViewContext();
            viewContext.setContainer(ContainerManager.getSharedContainer());
            viewContext.setUser(ctx.getUser());
            RoleImpersonationContextFactory factory = new RoleImpersonationContextFactory(
                viewContext.getContainer(), viewContext.getUser(),
                Collections.singleton(RoleManager.getRole(SharedViewEditorRole.class)), Collections.emptySet(), null);
            job._permissionsContext = factory.getImpersonationContext();

            try
            {
                UserImpersonationContextFactory factory1 = new UserImpersonationContextFactory(viewContext.getContainer(), viewContext.getUser(),
                    UserManager.getGuestUser(), null);
                job._permissionsContext1 = factory1.getImpersonationContext();
            }
            catch (Exception e)
            {
                LOG.error("Invalid user email for impersonating.");
            }

            GroupImpersonationContextFactory factory2 = new GroupImpersonationContextFactory(viewContext.getContainer(), viewContext.getUser(),
                GroupManager.getGroup(ContainerManager.getRoot(), "Users", GroupEnumType.SITE), null);
            job._permissionsContext2 = factory2.getImpersonationContext();
            testSerialize(job, LOG);
        }
    }

    public static class WorkbookDeleteTestCase extends Assert
    {
        private static final String FOLDER_NAME = "WorkbookDeleteTestCaseFolder";
        private static final String TEST_EMAIL = "testDelete@myDomain.com";

        @Test
        public void testWorkbookDelete() throws Exception
        {
            doCleanup();

            Container project = ContainerManager.createContainer(ContainerManager.getRoot(), FOLDER_NAME, TestContext.get().getUser());
            Container workbook = ContainerManager.createContainer(project, null, "Title1", null, WorkbookContainerType.NAME, TestContext.get().getUser());

            ValidEmail email = new ValidEmail(TEST_EMAIL);
            SecurityManager.NewUserStatus newUserStatus = SecurityManager.addUser(email, null);
            User nonAdminUser = newUserStatus.getUser();
            MutableSecurityPolicy policy = new MutableSecurityPolicy(project.getPolicy());
            policy.addRoleAssignment(nonAdminUser, ReaderRole.class);
            SecurityPolicyManager.savePolicy(policy, TestContext.get().getUser());

            // User lacks any permission, throw unauthorized for parent and workbook:
            HttpServletRequest request = ViewServlet.mockRequest(RequestMethod.POST.name(), new ActionURL(DeleteFolderAction.class, project), nonAdminUser, Map.of("Content-Type", "application/json"), null);
            MockHttpServletResponse response = ViewServlet.mockDispatch(request, null);
            Assert.assertEquals("Incorrect response code", HttpServletResponse.SC_FORBIDDEN, response.getStatus());

            request = ViewServlet.mockRequest(RequestMethod.POST.name(), new ActionURL(DeleteFolderAction.class, workbook), nonAdminUser, Map.of("Content-Type", "application/json"), null);
            response = ViewServlet.mockDispatch(request, null);
            Assert.assertEquals("Incorrect response code", HttpServletResponse.SC_FORBIDDEN, response.getStatus());

            // Grant permission, should be able to delete the workbook but not parent:
            policy = new MutableSecurityPolicy(project.getPolicy());
            policy.addRoleAssignment(nonAdminUser, EditorRole.class);
            SecurityPolicyManager.savePolicy(policy, TestContext.get().getUser());

            request = ViewServlet.mockRequest(RequestMethod.POST.name(), new ActionURL(DeleteFolderAction.class, project), nonAdminUser, Map.of("Content-Type", "application/json"), null);
            response = ViewServlet.mockDispatch(request, null);
            Assert.assertEquals("Incorrect response code", HttpServletResponse.SC_FORBIDDEN, response.getStatus());

            // Hitting delete action results in a redirect:
            request = ViewServlet.mockRequest(RequestMethod.POST.name(), new ActionURL(DeleteFolderAction.class, workbook), nonAdminUser, Map.of("Content-Type", "application/json"), null);
            response = ViewServlet.mockDispatch(request, null);
            Assert.assertEquals("Incorrect response code", HttpServletResponse.SC_FOUND, response.getStatus());

            doCleanup();
        }

        protected static void doCleanup() throws Exception
        {
            Container project = ContainerManager.getForPath(FOLDER_NAME);
            if (project != null)
            {
                ContainerManager.deleteAll(project, TestContext.get().getUser());
            }

            if (UserManager.userExists(new ValidEmail(TEST_EMAIL)))
            {
                User u = UserManager.getUser(new ValidEmail(TEST_EMAIL));
                UserManager.deleteUser(u.getUserId());
            }
        }
    }

    public static class ImportFolderSourceScopingTestCase extends AbstractContainerScopingTest
    {
        @Test
        public void testImportFromTemplateRequiresSourceAdmin() throws Exception
        {
            Container dest = createContainer("Dest");
            Container source = createContainer("Source");
            User destAdminOnly = createUserInRole(dest, FolderAdminRole.class);

            ActionURL url = new ActionURL(ImportFolderAction.class, dest)
                    .addParameter("sourceTemplateFolder", source.getPath())
                    .addParameter("sourceTemplateFolderId", source.getId());
            MockHttpServletResponse resp = post(url, destAdminOnly);

            // The fix rejects the import and reshows the form (200) rather than redirecting to success (302), with a
            // source-permission error message in the rendered content.
            assertStatus(HttpServletResponse.SC_OK, resp);
            assertTrue("Expected a source-permission rejection message, content was: " + resp.getContentAsString(),
                    resp.getContentAsString().contains("permission to import from the specified source folder"));

            // Positive control performed in S3ImportTest.testS3Import(). Difficult to mock here due to pipeline job
        }
    }

    public static class RevertFolderScopingTestCase extends AbstractContainerScopingTest
    {
        @Test
        public void testRevertFolderRequiresTargetAdmin() throws Exception
        {
            User admin = getAdmin();
            Container folderA = createContainer("A");
            Container folderB = createContainer("B");

            User adminA = createUserInRole(folderA, FolderAdminRole.class);

            ActionURL foreignUrl = new ActionURL(RevertFolderAction.class, folderA)
                    .addParameter("containerPath", folderB.getPath());

            // Cross-container attempt by a caller who is not an admin of the target -> 403, before any mutation.
            assertStatus(HttpServletResponse.SC_FORBIDDEN, post(foreignUrl, adminA));

            // Positive control: a site admin (who IS an admin of the target) is allowed through even cross-container,
            // proving the fix re-checks AdminPermission on the target rather than locking to the request container.
            // folderB is a plain folder with no container tabs, so the action makes no change and returns success:false
            // at status 200 -- the point is that the guard does not reject an authorized caller.
            assertStatus(HttpServletResponse.SC_OK, post(foreignUrl, admin));
        }

        @Test
        public void testClearDeletedTabFoldersRequiresTargetAdmin() throws Exception
        {
            User admin = getAdmin();
            Container folderA = createContainer("A");
            Container folderB = createContainer("B");
            User adminA = createUserInRole(folderA, FolderAdminRole.class);

            ActionURL foreignUrl = new ActionURL(ClearDeletedTabFoldersAction.class, folderA)
                    .addParameter("containerPath", folderB.getPath())
                    .addParameter("resurrectFolders", "anyTab");

            // A folder admin in A only must not clear deleted-tab markers in folder B (resolved by containerPath) -> 403.
            assertStatus(HttpServletResponse.SC_FORBIDDEN, post(foreignUrl, adminA));

            // Positive control: a site admin (admin of the target) is allowed through -> 200.
            assertStatus(HttpServletResponse.SC_OK, post(foreignUrl, admin));
        }

        @Test
        public void testSetFolderPermissionsCopyRequiresSourceAdmin() throws Exception
        {
            Container dest = createContainer("Dest");
            Container source = createContainer("Source");
            User destAdmin = createUserInRole(dest, FolderAdminRole.class);

            // Copying groups/role assignments from a project the caller does not administer must be rejected. The
            // action's @RequiresPermission(AdminPermission.class) only proves admin on the destination container, so a
            // dest-only admin supplying another project's id as targetProject must get 403, not a copy of its security
            // configuration. (Positive control omitted: a successful copy needs real project group/policy fixtures.)
            ActionURL url = new ActionURL(SetFolderPermissionsAction.class, dest)
                    .addParameter("permissionType", "CopyExistingProject")
                    .addParameter("targetProject", source.getId());
            assertStatus(HttpServletResponse.SC_FORBIDDEN, post(url, destAdmin));
        }
    }
}
