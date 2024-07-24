/*
 * Copyright (c) 2017-2019 LabKey Corporation
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
package org.labkey.api;

import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterRegistration;
import jakarta.servlet.MultipartConfigElement;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletRegistration;
import org.apache.catalina.filters.CorsFilter;
import org.apache.commons.collections4.Factory;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.labkey.api.action.ApiXmlWriter;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.admin.SubfolderWriter;
import org.labkey.api.assay.AssayFileWriter;
import org.labkey.api.assay.AssayResultsFileWriter;
import org.labkey.api.assay.ReplacedRunFilter;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.attachments.ImageServlet;
import org.labkey.api.attachments.LookAndFeelResourceType;
import org.labkey.api.attachments.SecureDocumentType;
import org.labkey.api.cache.BlockingCache;
import org.labkey.api.collections.ArrayListMap;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.collections.CaseInsensitiveMapWrapper;
import org.labkey.api.collections.CollectionUtils;
import org.labkey.api.collections.LabKeyCollectors;
import org.labkey.api.collections.Sampler;
import org.labkey.api.collections.SwapQueue;
import org.labkey.api.compliance.ComplianceService;
import org.labkey.api.data.AbstractForeignKey;
import org.labkey.api.data.Aggregate;
import org.labkey.api.data.AtomicDatabaseInteger;
import org.labkey.api.data.BooleanFormat;
import org.labkey.api.data.BuilderObjectFactory;
import org.labkey.api.data.CompareType;
import org.labkey.api.data.ContainerDisplayColumn;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ConvertHelper;
import org.labkey.api.data.DatabaseCache;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.DbSequenceManager;
import org.labkey.api.data.ExcelColumn;
import org.labkey.api.data.InlineInClauseGenerator;
import org.labkey.api.data.JsonTest;
import org.labkey.api.data.MaterializedQueryHelper;
import org.labkey.api.data.MultiValuedRenderContext;
import org.labkey.api.data.NameGenerator;
import org.labkey.api.data.PropertyManager;
import org.labkey.api.data.ResultSetSelectorTestCase;
import org.labkey.api.data.RowTrackingResultSetWrapper;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlScanner;
import org.labkey.api.data.SqlSelectorTestCase;
import org.labkey.api.data.StatementUtils;
import org.labkey.api.data.TSVMapWriter;
import org.labkey.api.data.TSVWriter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableSelectorTestCase;
import org.labkey.api.data.TempTableInClauseGenerator;
import org.labkey.api.data.WorkbookContainerType;
import org.labkey.api.data.dialect.JdbcMetaDataTest;
import org.labkey.api.data.dialect.ParameterSubstitutionTest;
import org.labkey.api.data.dialect.StandardDialectStringHandler;
import org.labkey.api.data.measurement.Measurement;
import org.labkey.api.dataiterator.CachingDataIterator;
import org.labkey.api.dataiterator.DataIteratorUtil;
import org.labkey.api.dataiterator.DiskCachingDataIterator;
import org.labkey.api.dataiterator.ExistingRecordDataIterator;
import org.labkey.api.dataiterator.GenerateUniqueDataIterator;
import org.labkey.api.dataiterator.RemoveDuplicatesDataIterator;
import org.labkey.api.dataiterator.ResultSetDataIterator;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.dataiterator.StatementDataIterator;
import org.labkey.api.exp.api.ExperimentJSONConverter;
import org.labkey.api.exp.property.DomainTemplateGroup;
import org.labkey.api.files.FileSystemWatcherImpl;
import org.labkey.api.iterator.MarkableIterator;
import org.labkey.api.markdown.MarkdownService;
import org.labkey.api.mbean.LabKeyManagement;
import org.labkey.api.mbean.OperationsMXBean;
import org.labkey.api.module.CodeOnlyModule;
import org.labkey.api.module.FolderTypeManager;
import org.labkey.api.module.JavaVersion;
import org.labkey.api.module.ModuleContext;
import org.labkey.api.module.ModuleDependencySorter;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleHtmlViewDefinition;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.module.ModuleLoader.StartupPropertyStartupListener;
import org.labkey.api.module.ModuleXml;
import org.labkey.api.module.TomcatVersion;
import org.labkey.api.query.AbstractQueryUpdateService;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.SchemaKey;
import org.labkey.api.reader.ExcelFactory;
import org.labkey.api.reader.ExcelLoader;
import org.labkey.api.reader.JSONDataLoader;
import org.labkey.api.reader.MapLoader;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.reports.report.ReportType;
import org.labkey.api.reports.report.r.RReport;
import org.labkey.api.security.ApiKeyManager;
import org.labkey.api.security.ApiKeyManager.ApiKeyMaintenanceTask;
import org.labkey.api.security.AuthenticationConfiguration;
import org.labkey.api.security.AuthenticationLogoType;
import org.labkey.api.security.AuthenticationManager;
import org.labkey.api.security.AvatarType;
import org.labkey.api.security.Encryption;
import org.labkey.api.security.EntropyPasswordValidator;
import org.labkey.api.security.GroupManager;
import org.labkey.api.security.LimitedUser;
import org.labkey.api.security.NestedGroupsTest;
import org.labkey.api.security.PasswordExpiration;
import org.labkey.api.security.RoleSet;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.settings.AdminConsole;
import org.labkey.api.settings.AppProps;
import org.labkey.api.settings.AppPropsTestCase;
import org.labkey.api.settings.LookAndFeelProperties;
import org.labkey.api.settings.OptionalFeatureStartupListener;
import org.labkey.api.settings.WriteableLookAndFeelProperties;
import org.labkey.api.util.ChecksumUtil;
import org.labkey.api.util.Compress;
import org.labkey.api.util.ContextListener;
import org.labkey.api.util.DateUtil;
import org.labkey.api.util.DomTestCase;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.ExtUtil;
import org.labkey.api.util.FileStream;
import org.labkey.api.util.FileType;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.JSoupUtil;
import org.labkey.api.util.JsonUtil;
import org.labkey.api.util.JspTestCase;
import org.labkey.api.util.MailHelper;
import org.labkey.api.util.MemTracker;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.NumberUtilsLabKey;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.ResultSetUtil;
import org.labkey.api.util.SessionHelper;
import org.labkey.api.util.StringExpressionFactory;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.util.URIUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.emailTemplate.EmailTemplate;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.FileServlet;
import org.labkey.api.view.JspTemplate;
import org.labkey.api.view.LabKeyKaptchaServlet;
import org.labkey.api.view.Portal;
import org.labkey.api.view.RedirectorServlet;
import org.labkey.api.view.ViewServlet;
import org.labkey.api.view.WebPartFactory;
import org.labkey.api.webdav.WebdavResolverImpl;
import org.labkey.api.writer.ContainerUser;
import org.labkey.filters.ContentSecurityPolicyFilter;

import javax.management.StandardMBean;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.util.EnumSet.allOf;
import static org.labkey.api.settings.LookAndFeelProperties.Properties.applicationMenuDisplayMode;
import static org.labkey.api.settings.SiteSettingsProperties.allowApiKeys;
import static org.labkey.api.settings.SiteSettingsProperties.allowSessionKeys;
import static org.labkey.api.settings.SiteSettingsProperties.apiKeyExpirationSeconds;

/**
 * {@link org.labkey.api.module.Module} implementation for the API module itself, registering some of the basic
 * resource types within LabKey Server.
 */
public class ApiModule extends CodeOnlyModule
{
    private static final String CORS_PREFIX = "cors.";
    private static final String CORS_FILTER_NAME = "CorsFilter";

    @Override
    protected void init()
    {
        AttachmentService.get().registerAttachmentType(ReportType.get());
        AttachmentService.get().registerAttachmentType(LookAndFeelResourceType.get());
        AttachmentService.get().registerAttachmentType(AuthenticationLogoType.get());
        AttachmentService.get().registerAttachmentType(AvatarType.get());
        AttachmentService.get().registerAttachmentType(SecureDocumentType.get());

        PropertyManager.registerEncryptionMigrationHandler();
        AuthenticationManager.registerEncryptionMigrationHandler();

        LabKeyManagement.register(new StandardMBean(new OperationsMXBeanImpl(), OperationsMXBean.class, true), "Operations");

        AdminConsole.addExperimentalFeatureFlag(FileStream.STAGE_FILE_UPLOADS, "Stage file uploads before moving to final destination", "When using a non-local file system, using a specific API that requires a locally staged copy of the file as the source can sometimes be significantly faster than streaming the uploaded file directly", false);
    }

    @NotNull
    @Override
    protected Collection<? extends WebPartFactory> createWebPartFactories()
    {
        return Collections.emptyList();
    }

    @Override
    protected void doStartup(ModuleContext moduleContext)
    {
        SystemMaintenance.addTask(new ApiKeyMaintenanceTask());
        AuthenticationManager.registerMetricsProvider();
        ApiKeyManager.get().handleStartupProperties();
        MailHelper.init();
        // Handle experimental feature startup properties as late as possible; we want all experimental features to be registered first
        ContextListener.addStartupListener(new OptionalFeatureStartupListener());
        ContextListener.addStartupListener(new StartupPropertyStartupListener());
    }

    @Override
    public void registerFilters(ServletContext servletCtx)
    {
        addCSPFilter(servletCtx, "csp.enforce", "enforce", "EnforceContentSecurityPolicyFilter");
        addCSPFilter(servletCtx, "csp.report", "report", "ReportContentSecurityPolicyFilter");

        addCorsFilter(servletCtx);
    }

    private void addCorsFilter(ServletContext servletCtx)
    {
        String allowedOrigins = servletCtx.getInitParameter(CORS_PREFIX + "allowedOrigins");
        String allowedMethods = servletCtx.getInitParameter(CORS_PREFIX + "allowedMethods");
        String allowedHeaders = servletCtx.getInitParameter(CORS_PREFIX + "allowedHeaders");
        String exposedHeaders = servletCtx.getInitParameter(CORS_PREFIX + "exposedHeaders");
        String supportCredentials = servletCtx.getInitParameter(CORS_PREFIX + "supportCredentials");
        String preflightMaxAge = servletCtx.getInitParameter(CORS_PREFIX + "preflightMaxAge");
        String requestDecorate = servletCtx.getInitParameter(CORS_PREFIX + "requestDecorate");
        String urlPattern = servletCtx.getInitParameter(CORS_PREFIX + "urlPattern");

        if (allowedOrigins != null || allowedMethods != null || allowedHeaders != null ||
            exposedHeaders != null || supportCredentials != null || urlPattern != null ||
            requestDecorate != null || preflightMaxAge != null)
        {
            FilterRegistration registration = servletCtx.addFilter(CORS_FILTER_NAME, new CorsFilter());
            registration.addMappingForUrlPatterns(allOf(DispatcherType.class), false, urlPattern == null ? "/*" : urlPattern);
            Map<String, String> params = new HashMap<>();
            addParam(params, "cors.allowed.origins", allowedOrigins);
            addParam(params, "cors.allowed.methods", allowedMethods);
            addParam(params, "cors.allowed.headers", allowedHeaders);
            addParam(params, "cors.exposed.headers", exposedHeaders);
            addParam(params, "cors.support.credentials", supportCredentials);
            addParam(params, "cors.preflight.maxage", preflightMaxAge);
            addParam(params, "cors.request.decorate", requestDecorate);
            registration.setInitParameters(params);
        }
    }

    private void addParam(Map<String, String> params, String name, String value)
    {
        if (value != null)
        {
            params.put(name, value);
        }
    }

    private void addCSPFilter(ServletContext servletCtx, String parameterName, String disposition, String filterName)
    {
        String policy = servletCtx.getInitParameter(parameterName);
        if (null != policy)
        {
            FilterRegistration registration = servletCtx.addFilter(filterName, new ContentSecurityPolicyFilter());
            registration.addMappingForUrlPatterns(allOf(DispatcherType.class), false, "/*");
            registration.setInitParameters(Map.of("policy", policy, "disposition", disposition));
        }
    }

    @Override
    public void registerServlets(ServletContext servletCtx)
    {
        servletCtx.addListener(UserManager.SessionListener.class);

        ServletRegistration.Dynamic viewServletDynamic = servletCtx.addServlet("ViewServlet", new ViewServlet());
        viewServletDynamic.setLoadOnStartup(1);
        viewServletDynamic.setMultipartConfig(new MultipartConfigElement(SpringActionController.getTempUploadDir().getPath()));
        viewServletDynamic.addMapping("*.view");
        viewServletDynamic.addMapping("*.api");
        viewServletDynamic.addMapping("*.post");

        servletCtx.addServlet("ImageServlet", new ImageServlet()).
                addMapping("*.image");

        ServletRegistration.Dynamic kaptchaDynamic = servletCtx.addServlet("Kaptcha", new LabKeyKaptchaServlet());
        kaptchaDynamic.setInitParameter("kaptcha.textproducer.char.length", "6");
        kaptchaDynamic.addMapping("/kaptcha.jpg");

        // File Servlet. Maps standard requests for files onto the filecontent module.
        // Requests like http://host/labkey/files/home/test.pdf become
        // http://host/labkey/filecontent/home/sendFile.view?fileName=test.pdf
        // see http://host/labkey/filecontent/home/begin.view for description of how to
        // set up a static web parallel to the labkey folder hierarchy
        // maps http://host/labkey/files/proj/dir/test.html to
        // http://host/labkey/filecontent/proj/dir/sendFile.view?file=test.html
        servletCtx.addServlet("FileServlet", new FileServlet()).
            addMapping("/files/*");

        String legacyContextPath = servletCtx.getInitParameter("legacyContextPath");
        if (legacyContextPath != null)
        {
            ServletRegistration.Dynamic redirectorDynamic = servletCtx.addServlet("RedirectorServlet", new RedirectorServlet(legacyContextPath));
            redirectorDynamic.addMapping(legacyContextPath + "/*");
            redirectorDynamic.setMultipartConfig(new MultipartConfigElement(SpringActionController.getTempUploadDir().getPath()));
        }
    }

    @Override
    public @NotNull Set<Class> getUnitTests()
    {
        return Set.of(
            Aggregate.TestCase.class,
            ApiXmlWriter.TestCase.class,
            ArrayListMap.TestCase.class,
            BooleanFormat.TestCase.class,
            BuilderObjectFactory.TestCase.class,
            CachingDataIterator.ScrollTestCase.class,
            CaseInsensitiveHashMap.TestCase.class,
            CaseInsensitiveHashSet.TestCase.class,
            CaseInsensitiveMapWrapper.TestCase.class,
            ChecksumUtil.TestCase.class,
            CollectionUtils.TestCase.class,
            Compress.TestCase.class,
            Constants.TestCase.class,
            ConvertHelper.TestCase.class,
            DataIteratorUtil.TestCase.class,
            DatabaseCache.TestCase.class,
            DateUtil.TestCase.class,
            DbScope.DialectTestCase.class,
            DetailsURL.TestCase.class,
            DiskCachingDataIterator.DiskTestCase.class,
            EmailTemplate.TestCase.class,
            EntropyPasswordValidator.TestCase.class,
            ExcelFactory.ExcelFactoryTestCase.class,
            ExcelLoader.ExcelLoaderTestCase.class,
            ExistingRecordDataIterator.TestCase.class,
            ExperimentJSONConverter.TestCase.class,
            ExtUtil.TestCase.class,
            FieldKey.TestCase.class,
            FileType.TestCase.class,
            FileUtil.TestCase.class,
            GenerateUniqueDataIterator.TestCase.class,
            HelpTopic.TestCase.class,
            InlineInClauseGenerator.TestCase.class,
            JSONDataLoader.HeaderMatchTest.class,
            JSONDataLoader.MetadataTest.class,
            JSONDataLoader.RowTest.class,
            JSoupUtil.TestCase.class,
            JavaVersion.TestCase.class,
            JsonTest.class,
            JsonUtil.TestCase.class,
            LimitedUser.TestCase.class,
            MarkableIterator.TestCase.class,
            MaterializedQueryHelper.TestCase.class,
            Measurement.TestCase.class,
            Measurement.Unit.TestCase.class,
            MemTracker.TestCase.class,
            ModuleContext.TestCase.class,
            ModuleDependencySorter.TestCase.class,
            ModuleHtmlViewDefinition.TestCase.class,
            MultiValuedRenderContext.TestCase.class,
            NameGenerator.TestCase.class,
            NumberUtilsLabKey.TestCase.class,
            PageFlowUtil.TestCase.class,
            Pair.TestCase.class,
            PasswordExpiration.TestCase.class,
            Path.TestCase.class,
            RReport.TestCase.class,
            RemoveDuplicatesDataIterator.DeDuplicateTestCase.class,
            ReplacedRunFilter.TestCase.class,
            ResultSetUtil.TestCase.class,
            SQLFragment.UnitTestCase.class,
            Sampler.TestCase.class,
            SchemaKey.TestCase.class,
            SessionHelper.TestCase.class,
            SimpleFilter.BetweenClauseTestCase.class,
            SimpleFilter.FilterTestCase.class,
            SimpleFilter.InClauseTestCase.class,
            SqlScanner.TestCase.class,
            StringExpressionFactory.TestCase.class,
            StringUtilsLabKey.TestCase.class,
            SubfolderWriter.TestCase.class,
            SwapQueue.TestCase.class,
            TSVMapWriter.Tests.class,
            TSVWriter.TestCase.class,
            TabLoader.HeaderMatchTest.class,
            Table.IsSelectTestCase.class,
            ValidEmail.TestCase.class,
            URIUtil.TestCase.class,
            AssayFileWriter.TestCase.class,
            AssayResultsFileWriter.TestCase.class
        );
    }

    @Override
    public @NotNull Collection<Factory<Class<?>>> getIntegrationTestFactories()
    {
        List<Factory<Class<?>>> list = new ArrayList<>(super.getIntegrationTestFactories());
        list.add(new JspTestCase("/org/labkey/api/module/testSimpleModule.jsp"));
        list.add(new JspTestCase("/org/labkey/api/module/actionAndFormTest.jsp"));
        return list;
    }

    @Override
    public @NotNull Set<Class> getIntegrationTests()
    {
        return Set.of(
            AbstractForeignKey.TestCase.class,
            AbstractQueryUpdateService.TestCase.class,
            ActionURL.TestCase.class,
            AliasManager.TestCase.class,
            ApiKeyManager.TestCase.class,
            AppPropsTestCase.class,
            AtomicDatabaseInteger.TestCase.class,
            BlockingCache.BlockingCacheTest.class,
            CompareType.TestCase.class,
            ContainerDisplayColumn.TestCase.class,
            ContainerFilter.TestCase.class,
            ContainerManager.TestCase.class,
            DbSchema.DDLMethodsTestCase.class,
            DbSchema.SchemaCasingTestCase.class,
            DbSchema.TableSelectTestCase.class,
            DbSchema.TransactionTestCase.class,
            DbScope.GroupConcatTestCase.class,
            DbScope.SchemaNameTestCase.class,
            DbScope.TransactionTestCase.class,
            DbSequenceManager.TestCase.class,
            DomTestCase.class,
            DomainTemplateGroup.TestCase.class,
            Encryption.TestCase.class,
            ExcelColumn.TestCase.class,
            ExceptionUtil.TestCase.class,
            FileSystemWatcherImpl.TestCase.class,
            FolderTypeManager.TestCase.class,
            GroupManager.TestCase.class,
            JdbcMetaDataTest.class,
            JspTemplate.TestCase.class,
            LabKeyCollectors.TestCase.class,
            MapLoader.MapLoaderTestCase.class,
            MarkdownService.TestCase.class,
            MimeMap.TestCase.class,
            ModuleHtmlView.TestCase.class,
            ModuleXml.TestCase.class,
            NestedGroupsTest.class,
            ParameterSubstitutionTest.class,
            Portal.TestCase.class,
            PropertyManager.TestCase.class,
            //RateLimiter.TestCase.class,
            ResultSetDataIterator.TestCase.class,
            ResultSetSelectorTestCase.class,
            RoleSet.TestCase.class,
            RowTrackingResultSetWrapper.TestCase.class,
            SQLFragment.IntegrationTestCase.class,
            SecurityManager.TestCase.class,
            SimpleTranslator.TranslateTestCase.class,
            SqlSelectorTestCase.class,
            StandardDialectStringHandler.TestCase.class,
            StatementDataIterator.TestCase.class,
            StatementUtils.TestCase.class,
            TabLoader.TabLoaderTestCase.class,
            Table.DataIteratorTestCase.class,
            Table.TestCase.class,
            TableSelectorTestCase.class,
            TempTableInClauseGenerator.TestCase.class,
            TomcatVersion.TestCase.class,
            URLHelper.TestCase.class,
            UserManager.TestCase.class,
            ViewCategoryManager.TestCase.class,
            WebdavResolverImpl.TestCase.class,
            WorkbookContainerType.TestCase.class,
            WriteableLookAndFeelProperties.TestCase.class
        );
    }

    @Override
    public JSONObject getPageContextJson(ContainerUser context)
    {
        JSONObject json = new JSONObject(getDefaultPageContextJson(context.getContainer()));

        AuthenticationConfiguration.SSOAuthenticationConfiguration config = AuthenticationManager.getAutoRedirectSSOAuthConfiguration();
        if (config != null)
            json.put("AutoRedirectSSOAuthConfiguration", config.getDescription());

        JSONObject complianceSettings = ComplianceService.get().getPageContextJson();
        if (complianceSettings != null)
            json.put("compliance", complianceSettings);

        LookAndFeelProperties properties = LookAndFeelProperties.getInstance(context.getContainer());
        json.put(applicationMenuDisplayMode.name(), properties.getApplicationMenuDisplayMode());
        json.put(allowApiKeys.name(), AppProps.getInstance().isAllowApiKeys());
        json.put(apiKeyExpirationSeconds.name(), AppProps.getInstance().getApiKeyExpirationSeconds());
        json.put(allowSessionKeys.name(), AppProps.getInstance().isAllowSessionKeys());

        json.put("moduleNames", ModuleLoader.getInstance().getModules().stream().map(module -> module.getName().toLowerCase()).toArray());
        return json;
    }

    @Override
    public void startBackgroundThreads()
    {
        Encryption.initEncryptionKeyTest();
    }
}
