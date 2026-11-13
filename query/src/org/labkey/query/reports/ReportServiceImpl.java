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

package org.labkey.query.reports;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.collections4.MultiSet;
import org.apache.commons.collections4.multiset.HashMultiSet;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.Logger;
import org.apache.xmlbeans.XmlObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.junit.Test;
import org.labkey.api.admin.FolderExportContext;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.StaticLoggerGetter;
import org.labkey.api.audit.AuditLogService;
import org.labkey.api.collections.MultiSetUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.Module;
import org.labkey.api.moduleeditor.api.ModuleEditorService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.SimpleValidationError;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reports.LabKeyScriptEngineManager;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.model.ReportPropsManager;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.model.ViewCategoryListener;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.reports.report.AbstractReportIdentifier;
import org.labkey.api.reports.report.DbReportIdentifier;
import org.labkey.api.reports.report.ModuleJavaScriptReportDescriptor;
import org.labkey.api.reports.report.JavaScriptReport;
import org.labkey.api.reports.report.ModuleReportDescriptor;
import org.labkey.api.reports.report.ModuleReportIdentifier;
import org.labkey.api.reports.report.QueryReport;
import org.labkey.api.reports.report.ReportDB;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.reports.report.ReportIdentifierConverter;
import org.labkey.api.reports.report.ScriptEngineReport;
import org.labkey.api.reports.report.ScriptReport;
import org.labkey.api.reports.report.ScriptReportDescriptor;
import org.labkey.api.reports.report.python.ModuleIpynbReportDescriptor;
import org.labkey.api.reports.report.python.IpynbReport;
import org.labkey.api.reports.report.r.ModuleRReportDescriptor;
import org.labkey.api.reports.report.r.RReportDescriptor;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.AbstractContainerScopingTest;
import org.labkey.api.security.permissions.AdminPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.AuthorRole;
import org.labkey.api.security.roles.FolderAdminRole;
import org.labkey.api.security.roles.PlatformDeveloperRole;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.usageMetrics.UsageMetricsService;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.GUID;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.StringUtilsLabKey;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.util.SystemMaintenance.MaintenanceTask;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.util.XmlBeansUtil;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.util.logging.LogHelper;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.visualization.GenericChartReport;
import org.labkey.api.visualization.VisualizationReportDescriptor;
import org.labkey.api.writer.ContainerUser;
import org.labkey.api.writer.DefaultContainerUser;
import org.labkey.api.writer.MemoryVirtualFile;
import org.labkey.api.writer.VirtualFile;
import org.labkey.folder.xml.FolderDocument;
import org.labkey.query.xml.ReportDescriptorDocument;
import org.labkey.query.xml.ReportDescriptorType;

import javax.script.ScriptEngine;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.labkey.api.reports.report.ScriptReportDescriptor.REPORT_METADATA_EXTENSION;

public class ReportServiceImpl implements ContainerManager.ContainerListener, ReportService
{
    private static final Logger _log = LogHelper.getLogger(ReportService.class, "Errors and warnings with reports");
    private static final List<UIProvider> _uiProviders = new CopyOnWriteArrayList<>();
    private static final Map<String, UIProvider> _typeToProviderMap = new ConcurrentHashMap<>();
    private static final List<String> _globalItemFilterTypes = new CopyOnWriteArrayList<>();

    /**
     * maps descriptor types to providers
     */
    private final Map<String, Class<?>> _descriptors = new ConcurrentHashMap<>();

    /**
     * maps report types to implementations
     */
    private final Map<String, Class<?>> _reports = new ConcurrentHashMap<>();

    private final static ReportServiceImpl INSTANCE = new ReportServiceImpl();

    public static ReportServiceImpl getInstance()
    {
        return INSTANCE;
    }

    private ReportServiceImpl()
    {
        ContainerManager.addContainerListener(this);
        ContainerManager.addSecurableResourceProvider((c, u) -> {
            List<ReportDescriptor> ret = new ArrayList<>();
            for (Report report : ReportService.get().getReports(u, c))
            {
                if (report.getDescriptor().hasPermission(u, AdminPermission.class))
                    ret.add(report.getDescriptor());
            }
            return ret;
        });
        ConvertUtils.register(new ReportIdentifierConverter(), ReportIdentifier.class);
        ReportQueryChangeListener listener = new ReportQueryChangeListener();
        QueryService.get().addQueryListener(listener);
        QueryService.get().addCustomViewListener(listener);
        SystemMaintenance.addTask(new ReportServiceMaintenanceTask());
        ViewCategoryManager.addCategoryListener(new CategoryListener(this));
    }

    @Override
    public void registerDescriptor(ReportDescriptor descriptor)
    {
        if (descriptor == null)
            throw new IllegalArgumentException("Invalid descriptor instance");

        if (null != _descriptors.putIfAbsent(descriptor.getDescriptorType(), descriptor.getClass()))
            _log.warn("Descriptor type : " + descriptor.getDescriptorType() + " has previously been registered.");
    }

    @Override
    public ReportDescriptor createDescriptorInstance(String typeName)
    {
        if (typeName == null)
        {
            _log.error("createDescriptorInstance : typeName cannot be null");
            return null;
        }
        Class<?> clazz = _descriptors.get(typeName);

        if (null == clazz)
            return null;

        try
        {
            if (ReportDescriptor.class.isAssignableFrom(clazz))
            {
                return (ReportDescriptor)clazz.getDeclaredConstructor().newInstance();
            }

            throw new IllegalArgumentException("The specified class: " + clazz.getName() + " is not an instance of ReportDescriptor");
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("The specified class could not be created: " + clazz.getName());
        }
    }

    @Override
    @Nullable
    public ReportDescriptor getModuleReportDescriptor(Module module, String path)
    {
        return ModuleReportCache.getModuleReportDescriptor(module, path);
    }

    @Override
    @NotNull
    public List<ReportDescriptor> getModuleReportDescriptors(Module module, @Nullable String path)
    {
        return ModuleReportCache.getModuleReportDescriptors(module, path);
    }

    @Override
    public void registerReport(Report report)
    {
        if (report == null)
            throw new IllegalArgumentException("Invalid report instance");

        if (null != _reports.putIfAbsent(report.getType(), report.getClass()))
            _log.warn("Report type : {} has previously been registered.", report.getType());
    }

    public Set<String> getScriptReportTypes()
    {
        return _reports.entrySet().stream()
            .filter(e -> ScriptReport.class.isAssignableFrom(e.getValue()))
            .map(Map.Entry::getKey)
            .collect(Collectors.toSet());
    }

    @Override
    @Nullable
    public Report createReportInstance(String typeName)
    {
        // ConcurrentHashMap doesn't support null keys, so do the extra check ourselves
        if (typeName == null)
        {
            return null;
        }

        Class<?> clazz = _reports.get(typeName);

        if (null == clazz)
            return null;

        try
        {
            if (Report.class.isAssignableFrom(clazz))
            {
                Report report = (Report)clazz.getDeclaredConstructor().newInstance();
                report.getDescriptor().setReportType(typeName);

                return report;
            }

            throw new IllegalArgumentException("The specified class: " + clazz.getName() + " is not an instance of Report");
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("The specified class could not be created: " + clazz.getName());
        }
    }

    @Override
    @Nullable
    public Report createReportInstance(ReportDescriptor descriptor)
    {
        Report report = createReportInstance(descriptor.getReportType());
        report.setDescriptor(descriptor);
        return report;
    }

    private static TableInfo getTable()
    {
        return CoreSchema.getInstance().getTableInfoReport();
    }

    @Override
    public void containerDeleted(Container c, User user)
    {
        ContainerUtil.purgeTable(getTable(), c, "ContainerId");
        DatabaseReportCache.uncache(c);
    }

    @Override
    public Report createFromQueryString(String queryString)
    {
        for (Pair<String, String> param : PageFlowUtil.fromQueryString(queryString))
        {
            if (ReportDescriptor.Prop.reportType.toString().equals(param.getKey()))
            {
                if (param.getValue() != null)
                {
                    Report report = createReportInstance(param.getValue());
                    report.getDescriptor().initFromQueryString(queryString);
                    return report;
                }
            }
        }
        return null;
    }

    @Override
    public void deleteReport(ContainerUser context, Report report)
    {
        //ensure that descriptor id is a DbReportIdentifier
        DbReportIdentifier reportId;

        if (report.getDescriptor().getReportId() instanceof DbReportIdentifier)
            reportId = (DbReportIdentifier)(report.getDescriptor().getReportId());
        else
            throw new RuntimeException("Can't delete a report that is not stored in the database!");

        DbScope scope = getTable().getSchema().getScope();

        try (DbScope.Transaction tx = scope.ensureTransaction())
        {
            report.beforeDelete(context);

            final ReportDescriptor descriptor = report.getDescriptor();
            _deleteReport(context.getContainer(), context.getUser(), reportId.getRowId(), descriptor);
            SecurityPolicyManager.deletePolicy(descriptor);
            tx.commit();
        }
    }

    private void _deleteReport(Container c, User u, int reportId, ReportDescriptor descriptor)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ContainerId"), c.getId());
        filter.addCondition(FieldKey.fromParts("RowId"), reportId);
        Table.delete(getTable(), filter);
        DatabaseReportCache.uncache(c);

        ReportAuditProvider.ReportAuditEvent event = new ReportAuditProvider.ReportAuditEvent(reportId, descriptor, c, "Report deleted");
        AuditLogService.get().addEvent(u, event);
    }

    @Override
    public ReportIdentifier saveReportEx(ContainerUser context, String key, Report report, boolean skipValidation)
    {
        ReportIdentifier id = report.getDescriptor().getReportId();
        if (null == id || id instanceof DbReportIdentifier)
        {
            if (report.getDescriptor().isModuleBased())
                throw new IllegalStateException();
            int rowid = _saveDbReport(context, key, report, skipValidation).getRowId();
            return new DbReportIdentifier(rowid);
        }

        ReportDescriptor descriptor = report.getDescriptor();

        // NOTE there are module reports other than R
        if (id instanceof ModuleReportIdentifier &&
                (descriptor instanceof ModuleRReportDescriptor || descriptor instanceof ModuleJavaScriptReportDescriptor || descriptor instanceof ModuleIpynbReportDescriptor))
        {
            return _saveModuleReport(context, key, report, skipValidation);
        }
        else
        {
            throw new RuntimeException("Can't save this kind of module report yet.");
        }
    }

    @Override
    public void validateReportPermissions(ContainerUser context, Report report)
    {
        List<ValidationError> errors = new ArrayList<>();

        tryValidateReportPermissions(context, report, errors);

        if (!errors.isEmpty())
        {
            StringBuilder sb = new StringBuilder();
            for (ValidationError error : errors)
            {
                if (!sb.isEmpty())
                    sb.append("\n");

                sb.append(error.getMessage());
            }

            throw new UnauthorizedException(sb.toString());
        }
    }

    @Override
    public boolean tryValidateReportPermissions(ContainerUser context, Report report, List<ValidationError> errors)
    {
        final ReportDescriptor descriptor = report.getDescriptor();
        Report stored = null;

        if (descriptor.isNew())
        {
            validateScriptCreation(context, report, errors);
            if (descriptor.isShared())
                report.canShare(context.getUser(), context.getContainer(), errors);
        }
        else
        {
            stored = getStoredReport(descriptor.getReportId());
            if (null != stored && !context.getContainer().getId().equals(stored.getContainerId()))
            {
                errors.add(new SimpleValidationError("A report can only be saved from the folder that it belongs to."));
                return false;
            }

            // canEdit checks the stored report, so a save that changes the type must pass the new type's creation check
            if (descriptor.getReportId() instanceof DbReportIdentifier && (null == stored || !stored.getType().equals(report.getType())))
                validateScriptCreation(context, report, errors);

            Report toCheck = null != stored ? stored : report;

            if (toCheck.canEdit(context.getUser(), context.getContainer(), errors))
            {
                if (descriptor.isShared())
                    toCheck.canShare(context.getUser(), context.getContainer(), errors);
            }
        }

        // GH Issue 1649: generic report actions, such as study-reports-saveReportView, can write a chart's config too
        descriptor.validateContent(context.getUser(), null != stored ? stored.getDescriptor() : null, errors);

        return errors.isEmpty();
    }

    private void validateScriptCreation(ContainerUser context, Report report, List<ValidationError> errors)
    {
        if (!(report instanceof ScriptReport))
            return;

        String error = scriptAuthoringError(context, report);
        if (null != error)
            errors.add(new SimpleValidationError(error));
    }

    /**
     * Shared by the action-level and database-level checks, so they agree. Returns null when the user may author the
     * script. With no engine configured, only Platform Developers pass, since the script runs once one is configured.
     */
    private static @Nullable String scriptAuthoringError(ContainerUser context, Report report)
    {
        ScriptEngine engine = getScriptEngine(context.getContainer(), report);
        if (ReportUtil.canCreateScript(context, engine))
            return null;
        return null == engine ? "Unable to find a configured script engine for this report." :
            "You must be either a PlatformDeveloper or TrustedAnalyst to create or change the script of a script report.";
    }

    static @Nullable ScriptEngine getScriptEngine(Container c, Report report)
    {
        return switch (report)
        {
            case ScriptEngineReport r -> r.getScriptEngine(c);
            case JavaScriptReport _ -> LabKeyScriptEngineManager.get().getEngineByExtension(c, "js");
            case IpynbReport _ -> LabKeyScriptEngineManager.get().getEngineByExtension(c, IpynbReport.EXTENSION);
            default -> null;
        };
    }

    private ReportDB _saveDbReport(ContainerUser context, String key, Report report, boolean skipValidation)
    {
        DbScope scope = getTable().getSchema().getScope();
        ReportDescriptor descriptor;
        ReportDB r;
        try (DbScope.Transaction tx = scope.ensureTransaction())
        {
            report.getDescriptor().setContainer(context.getContainer().getId());
            report.beforeSave(context);

            descriptor = report.getDescriptor();

            // last chance to validate permissions, this should be done in the controller actions, so
            // just throw an exception if validation fails
            if (!skipValidation)
                validateReportPermissions(context, report);

            r = _saveDbReport(context.getUser(), context.getContainer(), key, descriptor);
            tx.commit();
        }
        _saveReportProperties(context.getContainer(), r.getEntityId(), descriptor);
        return r;
    }

    private ReportDB _saveDbReport(User user, Container c, String key, ReportDescriptor descriptor)
    {
        ReportDB reportDB = new ReportDB(c, key, descriptor);

        //ensure that descriptor id is a DbReportIdentifier
        DbReportIdentifier reportId;
        if (null == descriptor.getReportId() || descriptor.getReportId() instanceof DbReportIdentifier)
        {
            reportId = (DbReportIdentifier)(descriptor.getReportId());
            if (reportId != null)
                reportDB.setRowId(reportId.getRowId());
        }
        else
            throw new RuntimeException("Can't save a report that is not stored in the database!");

        // A descriptor's reportId can come straight from client input, so it may name a row in any container.
        ReportDB existing = null != reportId ? getReportDB(reportId.getRowId()) : null;
        if (null != existing && !c.getId().equals(existing.getContainerId()))
            throw new UnauthorizedException("A report can only be saved from the folder that it belongs to.");
        Report stored = null != existing ? _getInstance(existing) : null;
        validateScriptAuthorship(user, c, descriptor, stored);
        validateDescriptorContent(user, descriptor, stored);

        boolean reportExists = null != existing;
        if (reportExists)
            reportDB = Table.update(user, getTable(), reportDB, reportId.getRowId(),
                    new SimpleFilter(FieldKey.fromParts("ContainerId"), c.getId()), Level.WARN);
        else
            reportDB = Table.insert(user, getTable(), reportDB);

        DatabaseReportCache.uncache(c);

        ReportAuditProvider.ReportAuditEvent event = new ReportAuditProvider.ReportAuditEvent(reportDB, descriptor, c, reportExists ? "Report updated" : "Report created");
        AuditLogService.get().addEvent(user, event);

        return reportDB;
    }

    // Properties that decide what code runs; rmarkdownOutputOptions is passed into the rmarkdown::render() call
    private static final Set<ScriptReportDescriptor.Prop> CODE_PROPS = EnumSet.of(ScriptReportDescriptor.Prop.script,
        ScriptReportDescriptor.Prop.scriptExtension, ScriptReportDescriptor.Prop.scriptDependencies,
        ScriptReportDescriptor.Prop.includedReports, ScriptReportDescriptor.Prop.knitrFormat,
        ScriptReportDescriptor.Prop.rmarkdownOutputOptions);

    /**
     * GH Issue 1910: every database report write passes here, so callers that skip validateReportPermissions can't
     * introduce script content. Saves that leave the code properties alone pass, so system rewrites still work.
     */
    private void validateScriptAuthorship(User user, Container c, ReportDescriptor descriptor, @Nullable Report stored)
    {
        Report report = createReportInstance(descriptor.getReportType());
        if (!(report instanceof ScriptReport))
            return;
        report.setDescriptor(descriptor);

        boolean introducesScript = null == stored || !stored.getType().equals(report.getType()) ||
            CODE_PROPS.stream().anyMatch(prop -> !Objects.equals(
                scriptPropValue(stored.getDescriptor(), prop), scriptPropValue(descriptor, prop)));

        String error = introducesScript ? scriptAuthoringError(new DefaultContainerUser(c, user), report) : null;
        if (null != error)
            throw new UnauthorizedException(error);
    }

    /**
     * GH Issue 1649: the database-level twin of the validateContent check in tryValidateReportPermissions, so folder
     * import and skipValidation saves can't add chart JavaScript either. The persisted report type picks the client
     * renderer while the descriptor class (descriptorType, also from the input) picks the server's config parser, so
     * when they disagree, also check the config with the type's own descriptor class.
     */
    private void validateDescriptorContent(User user, ReportDescriptor descriptor, @Nullable Report stored)
    {
        ReportDescriptor storedDescriptor = null != stored ? stored.getDescriptor() : null;
        List<ValidationError> errors = new ArrayList<>();
        descriptor.validateContent(user, storedDescriptor, errors);

        Report typeReport = createReportInstance(descriptor.getReportType());
        if (null != typeReport && !typeReport.getDescriptor().getClass().equals(descriptor.getClass()))
        {
            ReportDescriptor typeDescriptor = typeReport.getDescriptor();
            typeDescriptor.setProperty(ReportDescriptor.Prop.json, descriptor.getProperty(ReportDescriptor.Prop.json));
            typeDescriptor.validateContent(user, storedDescriptor, errors);
        }

        if (!errors.isEmpty())
            throw new UnauthorizedException(errors.getFirst().getMessage());
    }

    // Treats missing and empty values alike, and ignores line endings, which XML storage normalizes
    private static @Nullable Object scriptPropValue(ReportDescriptor descriptor, ScriptReportDescriptor.Prop prop)
    {
        Object value = descriptor.getProperties().get(prop.name());
        if (value instanceof String s)
            return s.isEmpty() ? null : s.replace("\r\n", "\n");
        if (value instanceof Collection<?> values && values.isEmpty())
            return null;
        return value;
    }

    private void _saveReportProperties(Container c, String entityId, ReportDescriptor descriptor)
    {
        // consider: make this more generic instead of picking out these specific properties
        try {
            if (null != descriptor.getAuthorAsObject())
                ReportPropsManager.get().setPropertyValue(entityId, c, ReportDescriptor.Prop.author.name(), descriptor.getAuthorAsObject());
            if (null != descriptor.getStatus())
                ReportPropsManager.get().setPropertyValue(entityId, c, ReportDescriptor.Prop.status.name(), descriptor.getStatus());
            if (null != descriptor.getRefreshDateAsObject())
                ReportPropsManager.get().setPropertyValue(entityId, c, ReportDescriptor.Prop.refreshDate.name(), descriptor.getRefreshDateAsObject());
        }
        catch (ValidationException e) {
            throw new RuntimeException(e);
        }
    }


    private ReportIdentifier _saveModuleReport(ContainerUser context, String key, Report report, boolean skipValidation)
    {
        if (!(report.getDescriptor() instanceof ModuleReportDescriptor descriptor) || !(report.getDescriptor().getReportId() instanceof ModuleReportIdentifier))
            throw new IllegalStateException("This should be a module report!");

        User user = context.getUser();
        Container c = context.getContainer();

        report.beforeSave(context);

        // last chance to validate permissions, this should be done in the controller actions, so
        // just throw an exception if validation fails
        if (!skipValidation)
            validateReportPermissions(context, report);

        File scriptFile = ModuleEditorService.get().getFileForModuleResource(descriptor.getModule(), descriptor.getSourceFile().getPath());
        if (null == scriptFile || !scriptFile.canWrite())
            throw new RuntimeException("This module resource can not be edited");   // shouldn't happen
        File xmlFile;
        if (null != descriptor.getMetaDataFile())
        {
            xmlFile = ModuleEditorService.get().getFileForModuleResource(descriptor.getModule(), descriptor.getMetaDataFile().getPath());
        }
        else
        {
            xmlFile = new File(scriptFile.getParentFile(), descriptor.getReportName() + REPORT_METADATA_EXTENSION);
        }
        if (!(xmlFile.exists() ? xmlFile.isFile() && xmlFile.canWrite() : xmlFile.getParentFile().canWrite()))
            throw new RuntimeException("This module resource can not be edited");   // shouldn't happen

        try
        {
            String script = report.getDescriptor().getProperty(ScriptReportDescriptor.Prop.script);
            String reportXml;
            // we want this to act like folder export (don't persist script property), not like database save, so use getDescriptorDocument(FolderExportContext)
            FolderExportContext ex = new FolderExportContext(user, c, null, null, null);
            ReportDescriptorDocument reportDoc = report.getDescriptor().getDescriptorDocument(ex);
            try (StringWriter writer = new StringWriter())
            {
                reportDoc.save(writer, XmlBeansUtil.getDefaultSaveOptions());
                reportXml = writer.toString();
            }
            FileUtils.write(scriptFile, script, StringUtilsLabKey.DEFAULT_CHARSET);
            FileUtils.write(xmlFile, reportXml, StringUtilsLabKey.DEFAULT_CHARSET);
        }
        catch (IOException x)
        {
            throw UnexpectedException.wrap(x);
        }

        // TODO save report.xml
        //  _saveReportProperties(context.getContainer(), r.getEntityId(), descriptor);

        // avoid having a race between reselecting the report, and the file watcher
        ModuleReportCache.uncache(descriptor.getModule());
        return descriptor.getReportId();
    }


    public @Nullable Report _getInstance(ReportDB r)
    {
        if (r != null)
        {
            try
            {
                ReportDescriptor descriptor = ReportDescriptor.createFromXML(r.getDescriptorXML());

                if (descriptor != null)
                {
                    BeanUtils.copyProperties(descriptor, r);
                    descriptor.setReportId(new DbReportIdentifier(r.getRowId()));
                    descriptor.setOwner(r.getReportOwner());
                    descriptor.setDisplayOrder(r.getDisplayOrder());

                    if (r.getCategoryId() != null)
                        descriptor.setCategoryId(r.getCategoryId());

                    descriptor.initProperties();

                    String type = descriptor.getReportType();
                    Report report = createReportInstance(type);
                    if (report != null)
                        report.setDescriptor(descriptor);
                    return report;
                }
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }

        return null;
    }

    @Override
    public void setReportDisplayOrder(ContainerUser context, Report report, int displayOrder)
    {
        ReportIdentifier reportIdentifier = report.getDescriptor().getReportId();
        if (reportIdentifier != null && reportIdentifier.getRowId() != 0)
        {
            TableInfo table = getTable();
            SQLFragment sql = new SQLFragment("UPDATE ").append(table, "");
            sql.append(" SET DisplayOrder = ? WHERE RowId = ?");
            sql.addAll(displayOrder, reportIdentifier.getRowId());

            new SqlExecutor(table.getSchema()).execute(sql);

            DatabaseReportCache.uncache(context.getContainer());
        }
    }

    @Override
    public Report getReportByEntityId(Container c, String entityId)
    {
        if (StringUtils.isBlank(entityId))
            return null;
        if (GUID.isGUID(entityId))
        {
            return DatabaseReportCache.getReportByEntityId(c, entityId);
        }
        else
        {
            // TODO hack: see ModuleRReportDescriptor.getEntityId()
            ReportIdentifier id = getReportIdentifier(PageFlowUtil.decode(entityId), null, c);
            if (null == id)
                return null;
            return id.getReport(new DefaultContainerUser(c, null));
        }
    }

    @Override
    public Report getReport(Container c, int rowId)
    {
        Report report = DatabaseReportCache.getReport(c, rowId);

        if (null != report)
            return report;

        while (!c.isRoot())
        {
            c = c.getParent();
            report = DatabaseReportCache.getReport(c, rowId);

            if (null != report)
            {
                if (report.getDescriptor().isInheritable())
                    return report;
                else
                    return null;
            }
        }

        // Look for this report in the shared project
        return (!ContainerManager.getSharedContainer().equals(c)) ? DatabaseReportCache.getReport(ContainerManager.getSharedContainer(), rowId) : null;
    }

    @Override
    public ReportIdentifier getReportIdentifier(String reportId, @Nullable User user, @Nullable Container container)
    {
        return AbstractReportIdentifier.fromString(reportId, user, container);
    }

    @Override
    public Collection<Report> getReports(@Nullable User user, @NotNull Container c)
    {
        List<Report> reportsList = new ArrayList<>(DatabaseReportCache.getReports(c));
        return getSortedReadableReports(reportsList, user);
    }

    @Override
    public Collection<Report> getReports(@Nullable User user, @NotNull Container c, @Nullable String key)
    {
        List<ReportDescriptor> moduleReportDescriptors = new ArrayList<>();

        for (Module module : c.getActiveModules())
        {
            moduleReportDescriptors.addAll(getModuleReportDescriptors(module, key));
        }

        List<Report> reports = new ArrayList<>();

        for (ReportDescriptor descriptor : moduleReportDescriptors)
        {
            String type = descriptor.getReportType();
            Report report = createReportInstance(type);

            if (report != null)
            {
                report.setDescriptor(descriptor);
                reports.add(report);
            }
        }

        if (key == null)
        {
            reports.addAll(DatabaseReportCache.getReports(c));
        }
        else
        {
            reports.addAll(DatabaseReportCache.getReportsByReportKey(c, key));
        }

        return getSortedReadableReports(reports, user);
    }

    @Override
    @Deprecated
    public Collection<Report> getInheritableReports(User user, Container c, @Nullable String reportKey)
    {
        Collection<Report> inheritable = DatabaseReportCache.getInheritableReports(c);

        // If reportKey is specified then grab just those from the inheritable reports
        if (null != reportKey)
        {
            inheritable = inheritable.stream()
                .filter(report -> reportKey.equals(report.getDescriptor().getReportKey()))
                .toList();
        }

        List<Report> reportsList = new ArrayList<>(inheritable);

        return getSortedReadableReports(reportsList, user);
    }

    private static Collection<Report> getSortedReadableReports(List<Report> reports, @Nullable User user)
    {
        List<Report> readableReports;

        if (null == user)
        {
            readableReports = reports;
        }
        else
        {
            readableReports = reports
                .stream()
                .filter(report -> report.getDescriptor().isModuleBased() || report.hasPermission(user, report.getDescriptor().getResourceContainer(), ReadPermission.class))
                .collect(Collectors.toCollection(ArrayList::new));
        }

        // must re-sort to allow file-based reports to show in proper positions
        // NOTE: currently, the only way for file-based reports to appear in the middle of a category is to share a
        //       displayOrder number with a report already in the cache (all indices in a range are used when
        //       persisting); therefore the file-based report's order can never be fully guaranteed
        readableReports.sort(Comparator.comparingInt(r -> r.getDescriptor().getDisplayOrder()));

        return readableReports;
    }

    @Override
    @Nullable
    public Report getReport(ReportDB reportDB)
    {
        return _getInstance(reportDB);
    }

    @Override
    public void addUIProvider(UIProvider provider)
    {
        _uiProviders.add(provider);
    }

    @Override
    public List<UIProvider> getUIProviders()
    {
        return Collections.unmodifiableList(_uiProviders);
    }

    @Override
    public void addGlobalItemFilterType(String type)
    {
        _globalItemFilterTypes.add(type);
    }

    @Override
    public List<String> getGlobalItemFilterTypes()
    {
        return Collections.unmodifiableList(_globalItemFilterTypes);
    }

    @Override
    public @NotNull String getIconPath(Report report)
    {
        if (report != null)
        {
            String reportType = report.getType();

            UIProvider claimingProvider = _typeToProviderMap.get(reportType);

            if (null != claimingProvider)
            {
                String iconPath = claimingProvider.getIconPath(report);

                if (null == iconPath)
                    throw new IllegalStateException(reportType + " is claimed by " + claimingProvider + " but iconPath is null");

                return iconPath;
            }

            for (UIProvider provider : _uiProviders)
            {
                String iconPath = provider.getIconPath(report);

                if (iconPath != null)
                {
                    _typeToProviderMap.put(reportType, provider);
                    return iconPath;
                }
            }
        }

        // No UIProvider claimed this report type... so fall-back on blank image
        return "/_.gif";
    }

    @Override
    public @Nullable String getIconCls(Report report)
    {
        if (report != null)
        {
            String reportType = report.getType();

            UIProvider claimingProvider = _typeToProviderMap.get(reportType);

            if (null != claimingProvider)
            {
                return claimingProvider.getIconCls(report);
            }

            for (UIProvider provider : _uiProviders)
            {
                String iconClass = provider.getIconCls(report);

                if (iconClass != null)
                {
                    _typeToProviderMap.put(reportType, provider);
                    return iconClass;
                }
            }
        }

        // No report provider claimed this, so don't return an icon (we should always have an image icon to fall back on anyway)
        return null;
    }

    /** Unscoped by container on purpose: callers need the row's own container to decide whether they may touch it. */
    private @Nullable ReportDB getReportDB(int reportId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RowId"), reportId);
        return new TableSelector(getTable(), filter, null).getObject(ReportDB.class);
    }

    /** The saved report with this rowId in any container, or null if the row no longer exists */
    public @Nullable Report getReportInAnyContainer(int rowId)
    {
        return _getInstance(getReportDB(rowId));
    }

    /** The persisted report a descriptor's reportId names, or null if it names no database row. */
    private @Nullable Report getStoredReport(@Nullable ReportIdentifier reportId)
    {
        return reportId instanceof DbReportIdentifier dbReportId ? _getInstance(getReportDB(dbReportId.getRowId())) : null;
    }

    @Nullable
    private Report _deserialize(Container container, User user, XmlObject reportXml) throws IOException, XmlValidationException
    {
        ReportDescriptor descriptor = ReportDescriptor.createFromXmlObject(container, user, reportXml);

        if (descriptor != null)
        {
            //descriptor.setReportId(new DbReportIdentifier(r.getRowId()));
            //descriptor.setOwner(r.getReportOwner());

            String type = descriptor.getReportType();
            Report report = createReportInstance(type);

            if (report != null)
            {
                report.setDescriptor(descriptor);
                report.afterImport(container, user);
            }

            return report;
        }

        return null;
    }

    @Nullable
    private Report deserialize(Container container, User user, XmlObject reportXml, VirtualFile root, String xmlFileName) throws IOException, XmlValidationException
    {
        if (null != reportXml)
        {
            Report report = _deserialize(container, user, reportXml);

            // reset any report identifier, we want to treat an imported report as a new
            // report instance
            if (report != null)
            {
                ReportDescriptor descriptor = report.getDescriptor();
                descriptor.setReportId(new DbReportIdentifier(-1));

                // if this is an R report look for report source in separate file
                if (descriptor instanceof RReportDescriptor && xmlFileName.toLowerCase().endsWith(".report.xml"))
                {
                    String baseName = xmlFileName.substring(0, xmlFileName.length() - ".report.xml".length());
                    InputStream is = null;
                    try
                    {
                        is = root.getInputStream(baseName + ".R");
                        if (null == is)
                            is = root.getInputStream(baseName + ".r");
                        if (null != is)
                        {
                            String script = IOUtils.toString(is, StringUtilsLabKey.DEFAULT_CHARSET);
                            if (!StringUtils.isBlank(script))
                                descriptor.setProperty(ScriptReportDescriptor.Prop.script, script);
                        }
                    }
                    finally
                    {
                        // not using try with resources because of trying to open .R and .r
                        IOUtils.closeQuietly(is);
                    }
                }
            }

            return report;
        }

        throw new IllegalArgumentException("Report XML file does not exist.");
    }

    @Override @Nullable
    public Report importReport(FolderImportContext ctx, XmlObject reportXml, VirtualFile root, String xmlFileName) throws IOException, XmlValidationException
    {
        Report report = deserialize(ctx.getContainer(), ctx.getUser(), reportXml, root, xmlFileName);
        if (report != null)
        {
            ReportDescriptor descriptor = report.getDescriptor();
            String key = descriptor.getReportKey();
            if (StringUtils.isBlank(key))
            {
                // use the default key used by query views
                key = ReportUtil.getReportKey(descriptor.getProperty(ReportDescriptor.Prop.schemaName), descriptor.getProperty(ReportDescriptor.Prop.queryName));
            }

            // In 13.2, there was a change to use dataset names instead of labels for query references in reports, views, etc.
            // We used to fix these up, but we no longer support that. For now, log an error to alert admins.
            if (ctx.getArchiveVersion() != null && ctx.getArchiveVersion() < 13.11)
            {
                String schema = descriptor.getProperty(ReportDescriptor.Prop.schemaName);
                StudyService svc = StudyService.get();
                Study study = svc != null ? svc.getStudy(ctx.getContainer()) : null;
                if (study != null && schema != null && schema.equals("study"))
                {
                    String queryName = descriptor.getProperty(ReportDescriptor.Prop.queryName);
                    Dataset dataset = study.getDatasetByLabel(queryName);
                    if (dataset != null && !dataset.getName().equals(dataset.getLabel()))
                    {
                        ctx.getLogger().error("Report \"" + xmlFileName + "\" could not be imported. Its queryName is \"" + queryName +
                            ",\" which is a dataset label. Dataset labels are no longer supported; queryName should be set to the dataset name (\"" + dataset.getName() + "\") instead.");
                        return null;
                    }
                }
            }

            // Save before deleting the same-named report so a save refused by validateScriptAuthorship or validateDescriptorContent leaves it in place
            int rowId;
            try
            {
                rowId = _saveDbReport(ctx.getUser(), ctx.getContainer(), key, descriptor).getRowId();
            }
            catch (UnauthorizedException e)
            {
                // Deserializing imported the report's tag properties, which nothing will reference
                if (null != descriptor.getEntityId())
                    ReportPropsManager.get().deleteProperties(descriptor.getEntityId(), ctx.getContainer());
                ctx.getLogger().error("Report \"{}\" ({}) was not imported. {}", descriptor.getReportName(), xmlFileName, e.getMessage());
                return null;
            }

            for (Report existingReport : getReports(ctx.getUser(), ctx.getContainer(), key))
            {
                if (Strings.CI.equals(existingReport.getDescriptor().getReportName(), descriptor.getReportName()) && existingReport.getDescriptor().getReportId().getRowId() != rowId)
                {
                    // Don't delete reports we just added. This can happen if the reportKey is not unique and
                    // we have two or more reports with the same name. This also works in the reload case since
                    // existing reports will not have the same report ids as the newly imported/created ones.
                    boolean shouldDelete = !ctx.isImportedReport(existingReport.getDescriptor());

                    if (shouldDelete)
                        deleteReport(new DefaultContainerUser(ctx.getContainer(), ctx.getUser()), existingReport);
                }
            }

            descriptor.setReportId(new DbReportIdentifier(rowId));

            // re-load the report to get the updated property information (i.e container, etc.)
            report = ReportService.get().getReport(ctx.getContainer(), rowId);

            // copy over the serialized report name
            report.getDescriptor().setProperty(ReportDescriptor.Prop.serializedReportName,
                    descriptor.getProperty(ReportDescriptor.Prop.serializedReportName));

            report.afterSave(ctx.getContainer(), ctx.getUser(), root);

            // remember that we imported this report so we don't try to delete it if
            // we are importing another report with the same reportKey and name.
            ctx.addImportedReport(report.getDescriptor());

            // import any security role assignments
            if (reportXml instanceof ReportDescriptorDocument doc)
            {
                ReportDescriptorType descriptorType = doc.getReportDescriptor();

                if (descriptorType.isSetRoleAssignments())
                {
                    MutableSecurityPolicy policy = new MutableSecurityPolicy(report.getDescriptor());
                    SecurityPolicyManager.importRoleAssignments(ctx, policy, descriptorType.getRoleAssignments());
                }
            }
        }
        return report;
    }

    @Override
    public boolean reportNameExists(ViewContext context, String reportName, String key)
    {
        try
        {
            for (Report report : getReports(context.getUser(), context.getContainer(), key))
            {
                if (Strings.CS.equals(reportName, report.getDescriptor().getReportName()))
                    return true;
            }
            return false;
        }
        catch (Exception e)
        {
            return false;
        }
    }

    @Override
    public void maintenance(Logger log)
    {
        ScriptEngineReport.scheduledFileCleanup(log);
    }

    private static class CategoryListener implements ViewCategoryListener
    {
        private final ReportServiceImpl _instance;

        private CategoryListener(ReportServiceImpl instance)
        {
            _instance = instance;
        }

        @Override
        public void categoryDeleted(User user, ViewCategory category)
        {
            for (Report report : getDatabaseReportsForCategory(category))
            {
                Container c = ContainerManager.getForId(category.getContainerId());
                report.getDescriptor().setCategoryId(null);
                
                if (c != null)
                    _instance.saveReportEx(new DefaultContainerUser(c, user), report.getDescriptor().getReportKey(), report, true);
            }
        }

        @Override
        public void categoryCreated(User user, ViewCategory category)
        {}

        @Override
        public void categoryUpdated(User user, ViewCategory category)
        {}

        private Collection<Report> getDatabaseReportsForCategory(ViewCategory category)
        {
            if (category != null)
            {
                Integer categoryId = category.getRowId();
                return DatabaseReportCache.getReports(category.lookupContainer())
                    .stream()
                    .filter(report -> categoryId.equals(report.getDescriptor().getCategoryId()))  // These are all database reports, so we can use getCategoryId()
                    .collect(Collectors.toList());
            }
            return Collections.emptyList();
        }
    }

    private static class ReportServiceMaintenanceTask implements MaintenanceTask
    {
        @Override
        public String getDescription()
        {
            return "Report Service Maintenance";
        }

        @Override
        public String getName()
        {
            return "ReportService";
        }

        @Override
        public void run(Logger log)
        {
            ReportService.get().maintenance(log);
        }
    }

    public static void registerUsageMetrics(String moduleName)
    {
        UsageMetricsService svc = UsageMetricsService.get();
        if (null != svc)
        {
            svc.registerUsageMetrics(moduleName, () -> {
                // Iterate all the database reports once and produce two occurrence maps: all reports by type and just the charts by render type
                MultiSet<GenericChartReport.RenderType> chartCountsByRenderType = new HashMultiSet<>();
                AtomicInteger genericChartWithTrendlineTypeCount = new AtomicInteger();
                AtomicInteger genericChartWithErrorBarsCount = new AtomicInteger();
                Map<String, Long> countsByType = ContainerManager.getAllChildren(ContainerManager.getRoot()).stream()
                    .flatMap(c -> ReportService.get().getReports(null, c).stream())
                    .peek(report -> {
                        if (report instanceof GenericChartReport chart)
                        {
                            chartCountsByRenderType.add(chart.getRenderType());
                            if (chart.getDescriptor() instanceof VisualizationReportDescriptor descriptor)
                            {
                                String configJson = descriptor.getJSON();
                                if (configJson.contains("\"trendlineType\":") && !configJson.contains("\"trendlineType\":\"\""))
                                    genericChartWithTrendlineTypeCount.getAndIncrement();
                                if (configJson.contains("\"errorBars\":\"SD\"") || configJson.contains("\"errorBars\":\"SEM\""))
                                    genericChartWithErrorBarsCount.getAndIncrement();
                            }
                        }
                    })
                    .collect(Collectors.groupingBy(Report::getType, Collectors.counting()));

                return Map.of(
                    "reportCountsByType", countsByType,
                    "genericChartCountsByRenderType", MultiSetUtils.getOccurrenceMap(chartCountsByRenderType),
                    "genericChartWithTrendlineTypeCount", genericChartWithTrendlineTypeCount,
                    "genericChartWithErrorBarsCount", genericChartWithErrorBarsCount
                );
            });
        }
    }

    /** Streams script reports saved in every existing container; the caller must close the stream */
    public Stream<Report> streamScriptReports()
    {
        return streamReportRows().map(this::_getInstance).filter(report -> report instanceof ScriptReport);
    }

    /** Streams report rows in existing containers; the caller must close the stream */
    private static Stream<ReportDB> streamReportRows()
    {
        TableInfo table = getTable();
        SQLFragment sql = SQLFragment.unsafe("SELECT * FROM ").append(table, "r")
            .append(SQLFragment.unsafe(" WHERE r.ContainerId IN (SELECT EntityId FROM ")).append(CoreSchema.getInstance().getTableInfoContainers(), "c").append(SQLFragment.unsafe(")"));
        return new SqlSelector(table.getSchema(), sql).uncachedStream(ReportDB.class);
    }

    // JavaScript reports, since that engine is always configured
    public static class ScriptAuthorshipTestCase extends AbstractContainerScopingTest
    {
        private static final String KEY = "script-authorship-key";

        @Test
        public void testFolderAdminImportSkipsScriptReport() throws Exception
        {
            Container folder = createContainer("A");
            User folderAdmin = createUserInRole(folder, FolderAdminRole.class);
            String name = "import-script-report-" + GUID.makeGUID();
            Report existing = saveScriptReport(folder, name);

            assertNull("Import should skip the script report", importReport(folder, folderAdmin, newScriptReport(name)));
            assertEquals("Existing same-named report should survive", List.of(existing.getDescriptor().getReportId()), reportIds(folder));
        }

        @Test
        public void testPlatformDeveloperImportsScriptReport() throws Exception
        {
            Container folder = createContainer("B");
            User developer = createUserInRole(folder, FolderAdminRole.class);
            grantRootRole(developer, PlatformDeveloperRole.class);
            String name = "import-script-report-" + GUID.makeGUID();
            Report existing = saveScriptReport(folder, name);

            Report imported = importReport(folder, developer, newScriptReport(name));
            assertNotNull("Import should save the script report", imported);
            assertEquals("Import should replace the same-named report", List.of(imported.getDescriptor().getReportId()), reportIds(folder));
            assertNotEquals(existing.getDescriptor().getReportId(), imported.getDescriptor().getReportId());
        }

        @Test
        public void testUnvalidatedSaveCannotCreateScriptReport() throws Exception
        {
            Container folder = createContainer("C");
            ContainerUser folderAdmin = new DefaultContainerUser(folder, createUserInRole(folder, FolderAdminRole.class));
            Report report = newScriptReport("script-authorship-" + GUID.makeGUID());

            assertThrows(UnauthorizedException.class, () -> ReportService.get().saveReportEx(folderAdmin, KEY, report, true));
            assertEquals(List.of(), reportIds(folder));
        }

        @Test
        public void testUnvalidatedSaveCanRewriteButNotChangeScript() throws Exception
        {
            Container folder = createContainer("D");
            ContainerUser folderAdmin = new DefaultContainerUser(folder, createUserInRole(folder, FolderAdminRole.class));
            ReportIdentifier id = saveScriptReport(folder, "script-authorship-" + GUID.makeGUID()).getDescriptor().getReportId();

            // As ReportQueryChangeListener does after a query rename
            Report renamed = Objects.requireNonNull(getInstance().getStoredReport(id));
            renamed.getDescriptor().setProperty(ReportDescriptor.Prop.queryName, "renamedQuery");
            renamed.getDescriptor().setProperty(ScriptReportDescriptor.Prop.runInBackground, true);
            ReportService.get().saveReportEx(folderAdmin, KEY, renamed, true);

            Report edited = Objects.requireNonNull(getInstance().getStoredReport(id));
            assertEquals("renamedQuery", edited.getDescriptor().getProperty(ReportDescriptor.Prop.queryName));
            assertEquals("Non-code settings should save", "true", edited.getDescriptor().getProperty(ScriptReportDescriptor.Prop.runInBackground));
            edited.getDescriptor().setProperty(ScriptReportDescriptor.Prop.script, "// changed");
            assertThrows(UnauthorizedException.class, () -> ReportService.get().saveReportEx(folderAdmin, KEY, edited, true));
            assertEquals("// imported", Objects.requireNonNull(getInstance().getStoredReport(id)).getDescriptor().getProperty(ScriptReportDescriptor.Prop.script));
        }

        @Test
        public void testUnvalidatedSaveCannotChangeTypeToScript() throws Exception
        {
            Container folder = createContainer("E");
            ContainerUser folderAdmin = new DefaultContainerUser(folder, createUserInRole(folder, FolderAdminRole.class));
            Report queryReport = Objects.requireNonNull(ReportService.get().createReportInstance(QueryReport.TYPE));
            queryReport.getDescriptor().setReportName("type-change-" + GUID.makeGUID());
            ReportIdentifier id = saveAsAdmin(folder, queryReport).getDescriptor().getReportId();

            // Keeps the stored report's (empty) code properties, so only the type change can trip the check
            Report changed = Objects.requireNonNull(getInstance().getStoredReport(id));
            changed.getDescriptor().setReportType(JavaScriptReport.TYPE);
            assertThrows(UnauthorizedException.class, () -> ReportService.get().saveReportEx(folderAdmin, KEY, changed, true));
            assertEquals(QueryReport.TYPE, Objects.requireNonNull(getInstance().getStoredReport(id)).getType());
        }

        @Test
        public void testAdminCanChangeAccessOfAnotherUsersPrivateReport() throws Exception
        {
            Container folder = createContainer("I");
            User owner = createUserInRole(folder, FolderAdminRole.class);
            User reader = createUserInRole(folder, FolderAdminRole.class);
            User folderAdmin = createUserInRole(folder, FolderAdminRole.class);
            ReportIdentifier id = savePrivateReport(folder, owner);
            ViewContext context = ViewContext.getMockViewContext(folderAdmin, folder, folder.getStartURL(folderAdmin), false);

            // A validated save still requires the owner, and the rejected save mustn't leave the new policy behind
            Report custom = Objects.requireNonNull(getInstance().getStoredReport(id));
            assertThrows(UnauthorizedException.class, () -> ReportUtil.setReportSecurityPolicy(context, custom, Set.of(), Set.of(reader.getUserId())));
            Report unchanged = Objects.requireNonNull(getInstance().getStoredReport(id));
            assertEquals(owner.getUserId(), (int) unchanged.getDescriptor().getOwner());
            assertTrue("Policy should roll back with the report save",
                SecurityPolicyManager.getPolicy(unchanged.getDescriptor(), false).getAssignedRoles(reader).findAny().isEmpty());

            // As study-reportPermissions does, which requires admin
            ReportUtil.resetReportSecurityPolicy(context, unchanged, null, folderAdmin, true);
            Report shared = Objects.requireNonNull(getInstance().getStoredReport(id));
            assertNull(shared.getDescriptor().getOwner());
            assertTrue(shared.getDescriptor().isShared());
        }

        @Test
        public void testShareReportStillRequiresOwner() throws Exception
        {
            Container folder = createContainer("J");
            User owner = createUserInRole(folder, AuthorRole.class);
            User otherAuthor = createUserInRole(folder, AuthorRole.class);
            User recipient = createUserInRole(folder, AuthorRole.class);
            ReportIdentifier id = savePrivateReport(folder, owner);

            // As query-shareReport does, which needs only ShareReportPermission, so it must keep the validated save
            Report notMine = Objects.requireNonNull(getInstance().getStoredReport(id));
            ViewContext otherContext = ViewContext.getMockViewContext(otherAuthor, folder, folder.getStartURL(otherAuthor), false);
            assertThrows(UnauthorizedException.class, () -> ReportUtil.updateReportSecurityPolicy(otherContext, notMine, recipient.getUserId(), true));
            assertEquals("A rejected save should restore the owner on the passed-in (possibly cached) instance",
                owner.getUserId(), (int) notMine.getDescriptor().getOwner());
            Report unchanged = Objects.requireNonNull(getInstance().getStoredReport(id));
            assertEquals(owner.getUserId(), (int) unchanged.getDescriptor().getOwner());
            assertTrue("Policy should roll back with the report save",
                SecurityPolicyManager.getPolicy(unchanged.getDescriptor(), false).getAssignedRoles(recipient).findAny().isEmpty());

            // The owner can still share it
            ViewContext ownerContext = ViewContext.getMockViewContext(owner, folder, folder.getStartURL(owner), false);
            ReportUtil.updateReportSecurityPolicy(ownerContext, unchanged, recipient.getUserId(), true);
            Report shared = Objects.requireNonNull(getInstance().getStoredReport(id));
            assertNull(shared.getDescriptor().getOwner());
            assertFalse(SecurityPolicyManager.getPolicy(shared.getDescriptor(), false).getAssignedRoles(recipient).findAny().isEmpty());
        }

        @Test
        public void testFolderAdminImportSkipsPointClickFnChart() throws Exception
        {
            Container folder = createContainer("F");
            User folderAdmin = createUserInRole(folder, FolderAdminRole.class);
            String name = "import-chart-" + GUID.makeGUID();
            Report existing = saveAsAdmin(folder, newChart(name, "function(){}"));

            assertNull("Import should skip the chart", importReport(folder, folderAdmin, newChart(name, "function(){alert(1)}")));
            assertEquals("Existing same-named chart should survive", List.of(existing.getDescriptor().getReportId()), reportIds(folder));
            assertNotNull("A chart without a pointClickFn should import", importReport(folder, folderAdmin, newChart("plain-chart-" + GUID.makeGUID(), null)));
        }

        @Test
        public void testMismatchedDescriptorCannotHidePointClickFn() throws Exception
        {
            Container folder = createContainer("K");
            ContainerUser folderAdmin = new DefaultContainerUser(folder, createUserInRole(folder, FolderAdminRole.class));
            User developer = createUserInRole(folder, FolderAdminRole.class);
            grantRootRole(developer, PlatformDeveloperRole.class);

            // The query descriptor's validateContent ignores the config, so only the chart type's own descriptor sees the function
            assertThrows(UnauthorizedException.class, () -> ReportService.get().saveReportEx(folderAdmin, KEY, newMismatchedChart("function(){alert(1)}"), false));
            assertNull("Import should skip the chart", importReport(folder, folderAdmin.getUser(), newMismatchedChart("function(){alert(1)}")));
            assertEquals(List.of(), reportIds(folder));
            assertNotNull("A mismatched chart without a pointClickFn should still save",
                ReportService.get().saveReportEx(folderAdmin, KEY, newMismatchedChart(null), false));

            // A trusted browser developer may import it, which also shows the import keeps the mismatch
            Report imported = importReport(folder, developer, newMismatchedChart("function(){alert(1)}"));
            assertNotNull("Import should save the chart", imported);
            assertEquals(ReportService.GENERIC_CHART_REPORT_TYPE, imported.getType());
            assertFalse(imported.getDescriptor().getClass().equals(Objects.requireNonNull(ReportService.get().createReportInstance(ReportService.GENERIC_CHART_REPORT_TYPE)).getDescriptor().getClass()));

            // A folder admin's system rewrite of that chart (as CategoryListener does) leaves the config alone, so it passes
            Report renamed = Objects.requireNonNull(getInstance().getStoredReport(imported.getDescriptor().getReportId()));
            renamed.getDescriptor().setProperty(ReportDescriptor.Prop.queryName, "renamedQuery");
            ReportService.get().saveReportEx(folderAdmin, KEY, renamed, true);

            // But changing the function still fails
            Report edited = Objects.requireNonNull(getInstance().getStoredReport(imported.getDescriptor().getReportId()));
            edited.getDescriptor().setProperty(ReportDescriptor.Prop.json, chartJson("function(){alert(2)}"));
            assertThrows(UnauthorizedException.class, () -> ReportService.get().saveReportEx(folderAdmin, KEY, edited, true));
        }

        @Test
        public void testUnparseableStoredConfigAllowsSystemRewrite() throws Exception
        {
            Container folder = createContainer("L");
            ContainerUser folderAdmin = new DefaultContainerUser(folder, createUserInRole(folder, FolderAdminRole.class));
            Report chart = newChart("bad-json-chart-" + GUID.makeGUID(), null);
            chart.getDescriptor().setProperty(ReportDescriptor.Prop.json, "{\"chartConfig\": {}, \"chartConfig\": {}}");
            ReportIdentifier id = saveAsAdmin(folder, chart).getDescriptor().getReportId();

            // The config is unchanged, so the rewrite doesn't need to parse it
            Report renamed = Objects.requireNonNull(getInstance().getStoredReport(id));
            renamed.getDescriptor().setProperty(ReportDescriptor.Prop.queryName, "renamedQuery");
            ReportService.get().saveReportEx(folderAdmin, KEY, renamed, true);

            // A different unparseable config still fails closed
            Report edited = Objects.requireNonNull(getInstance().getStoredReport(id));
            edited.getDescriptor().setProperty(ReportDescriptor.Prop.json, "{\"a\": 1, \"a\": 2}");
            assertThrows(UnauthorizedException.class, () -> ReportService.get().saveReportEx(folderAdmin, KEY, edited, true));
        }

        @Test
        public void testPlatformDeveloperImportsPointClickFnChart() throws Exception
        {
            Container folder = createContainer("G");
            User developer = createUserInRole(folder, FolderAdminRole.class);
            grantRootRole(developer, PlatformDeveloperRole.class);

            assertNotNull(importReport(folder, developer, newChart("import-chart-" + GUID.makeGUID(), "function(){alert(1)}")));
        }

        @Test
        public void testUnvalidatedSaveCanRewriteButNotChangePointClickFn() throws Exception
        {
            Container folder = createContainer("H");
            ContainerUser folderAdmin = new DefaultContainerUser(folder, createUserInRole(folder, FolderAdminRole.class));
            ReportIdentifier id = saveAsAdmin(folder, newChart("chart-" + GUID.makeGUID(), "function(){}")).getDescriptor().getReportId();

            // As CategoryListener does after a category delete
            Report renamed = Objects.requireNonNull(getInstance().getStoredReport(id));
            renamed.getDescriptor().setProperty(ReportDescriptor.Prop.queryName, "renamedQuery");
            ReportService.get().saveReportEx(folderAdmin, KEY, renamed, true);

            Report edited = Objects.requireNonNull(getInstance().getStoredReport(id));
            assertEquals("renamedQuery", edited.getDescriptor().getProperty(ReportDescriptor.Prop.queryName));
            edited.getDescriptor().setProperty(ReportDescriptor.Prop.json, chartJson("function(){alert(1)}"));
            assertThrows(UnauthorizedException.class, () -> ReportService.get().saveReportEx(folderAdmin, KEY, edited, true));
            assertEquals(chartJson("function(){}"), Objects.requireNonNull(getInstance().getStoredReport(id)).getDescriptor().getProperty(ReportDescriptor.Prop.json));
        }

        private Report saveScriptReport(Container folder, String name)
        {
            return saveAsAdmin(folder, newScriptReport(name));
        }

        // Ownership is the report's creator, so save as the owner; the deprecated owner column marks it private
        private ReportIdentifier savePrivateReport(Container folder, User owner)
        {
            Report report = Objects.requireNonNull(ReportService.get().createReportInstance(QueryReport.TYPE));
            report.getDescriptor().setReportName("private-" + GUID.makeGUID());
            report.getDescriptor().setOwner(owner.getUserId());
            return ReportService.get().saveReportEx(new DefaultContainerUser(folder, owner), KEY, report, true);
        }

        private Report saveAsAdmin(Container folder, Report report)
        {
            ReportIdentifier id = ReportService.get().saveReportEx(new DefaultContainerUser(folder, getAdmin()), KEY, report, true);
            return id.getReport(new DefaultContainerUser(folder, getAdmin()));
        }

        private static Report newChart(String name, @Nullable String pointClickFn)
        {
            Report report = Objects.requireNonNull(ReportService.get().createReportInstance(ReportService.GENERIC_CHART_REPORT_TYPE));
            report.getDescriptor().setReportName(name);
            report.getDescriptor().setReportKey(KEY);
            report.getDescriptor().setProperty(ReportDescriptor.Prop.json, chartJson(pointClickFn));
            return report;
        }

        // A chart type whose descriptor is a query report's, as an import's descriptorType can arrange
        private static Report newMismatchedChart(@Nullable String pointClickFn)
        {
            Report report = Objects.requireNonNull(ReportService.get().createReportInstance(QueryReport.TYPE));
            report.getDescriptor().setReportType(ReportService.GENERIC_CHART_REPORT_TYPE);
            report.getDescriptor().setReportName("mismatched-chart-" + GUID.makeGUID());
            report.getDescriptor().setReportKey(KEY);
            report.getDescriptor().setProperty(ReportDescriptor.Prop.json, chartJson(pointClickFn));
            return report;
        }

        private static String chartJson(@Nullable String pointClickFn)
        {
            JSONObject measures = new JSONObject();
            if (null != pointClickFn)
                measures.put("pointClickFn", pointClickFn);
            return new JSONObject().put("chartConfig", new JSONObject().put("measures", measures)).toString();
        }

        private static Report newScriptReport(String name)
        {
            Report report = Objects.requireNonNull(ReportService.get().createReportInstance(JavaScriptReport.TYPE));
            report.getDescriptor().setReportName(name);
            report.getDescriptor().setReportKey(KEY);
            report.getDescriptor().setProperty(ScriptReportDescriptor.Prop.script, "// imported");
            return report;
        }

        private @Nullable Report importReport(Container folder, User user, Report report) throws Exception
        {
            String name = report.getDescriptor().getReportName();
            XmlObject xml = ReportDescriptorDocument.Factory.parse(report.getDescriptor().serialize(folder));
            VirtualFile root = new MemoryVirtualFile();
            FolderImportContext ctx = new FolderImportContext(user, folder, FolderDocument.Factory.newInstance(), null,
                new StaticLoggerGetter(LogHelper.getLogger(ScriptAuthorshipTestCase.class, "Script report authorship test")), root);
            return ReportService.get().importReport(ctx, xml, root, name + ".report.xml");
        }

        private List<ReportIdentifier> reportIds(Container folder)
        {
            return ReportService.get().getReports(getAdmin(), folder, KEY).stream().map(r -> r.getDescriptor().getReportId()).toList();
        }
    }
}
