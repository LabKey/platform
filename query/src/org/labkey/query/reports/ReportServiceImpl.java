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

package org.labkey.query.reports;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.FolderImportContext;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ContainerManager.AbstractContainerListener;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.SqlExecutor;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.Module;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.ValidationError;
import org.labkey.api.query.ValidationException;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.model.ReportPropsManager;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.reports.model.ViewCategoryListener;
import org.labkey.api.reports.model.ViewCategoryManager;
import org.labkey.api.reports.report.AbstractReportIdentifier;
import org.labkey.api.reports.report.DbReportIdentifier;
import org.labkey.api.reports.report.ReportDB;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.reports.report.ReportIdentifierConverter;
import org.labkey.api.reports.report.ScriptEngineReport;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.GroupManager;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.UserPrincipal;
import org.labkey.api.security.ValidEmail;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.Role;
import org.labkey.api.security.roles.RoleManager;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.util.SystemMaintenance.MaintenanceTask;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;
import org.labkey.api.writer.DefaultContainerUser;
import org.labkey.api.writer.VirtualFile;
import org.labkey.query.xml.ReportDescriptorDocument;
import org.labkey.query.xml.ReportDescriptorType;
import org.labkey.security.xml.GroupRefType;
import org.labkey.security.xml.UserRefType;
import org.labkey.security.xml.roleAssignment.RoleAssignmentType;
import org.labkey.security.xml.roleAssignment.RoleAssignmentsType;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

/**
 * User: Karl Lum
 * Date: Dec 21, 2007
 */
public class ReportServiceImpl extends AbstractContainerListener implements ReportService
{
    private static final Logger _log = Logger.getLogger(ReportService.class);
    private static final List<UIProvider> _uiProviders = new CopyOnWriteArrayList<>();
    private static final Map<String, UIProvider> _typeToProviderMap = new ConcurrentHashMap<>();

    /**
     * maps descriptor types to providers
     */
    private final Map<String, Class> _descriptors = new ConcurrentHashMap<>();

    /**
     * maps report types to implementations
     */
    private final Map<String, Class> _reports = new ConcurrentHashMap<>();

    private final static ReportServiceImpl INSTANCE = new ReportServiceImpl();

    public static ReportServiceImpl getInstance()
    {
        return INSTANCE;
    }

    private ReportServiceImpl()
    {
        ContainerManager.addContainerListener(this);
        ConvertUtils.register(new ReportIdentifierConverter(), ReportIdentifier.class);
        ReportQueryChangeListener listener = new ReportQueryChangeListener();
        QueryService.get().addQueryListener(listener);
        QueryService.get().addCustomViewListener(listener);
        SystemMaintenance.addTask(new ReportServiceMaintenanceTask());
        ViewCategoryManager.addCategoryListener(new CategoryListener(this));
    }

    public void registerDescriptor(ReportDescriptor descriptor)
    {
        if (descriptor == null)
            throw new IllegalArgumentException("Invalid descriptor instance");

        if (null != _descriptors.putIfAbsent(descriptor.getDescriptorType(), descriptor.getClass()))
            _log.warn("Descriptor type : " + descriptor.getDescriptorType() + " has previously been registered.");
    }

    public ReportDescriptor createDescriptorInstance(String typeName)
    {
        if (typeName == null)
        {
            _log.error("createDescriptorInstace : typeName cannot be null");
            return null;
        }
        Class clazz = _descriptors.get(typeName);

        if (null == clazz)
            return null;

        try
        {
            if (ReportDescriptor.class.isAssignableFrom(clazz))
            {
                return (ReportDescriptor)clazz.newInstance();
            }

            throw new IllegalArgumentException("The specified class: " + clazz.getName() + " is not an instance of ReportDescriptor");
        }
        catch (Exception e)
        {
            throw new IllegalArgumentException("The specified class could not be created: " + clazz.getName());
        }
    }

    @Nullable
    public ReportDescriptor getModuleReportDescriptor(Module module, String path)
    {
        return ModuleReportCache.getModuleReportDescriptor(module, path);
    }

    @NotNull
    public List<ReportDescriptor> getModuleReportDescriptors(Module module, @Nullable String path)
    {
        return ModuleReportCache.getModuleReportDescriptors(module, path);
    }

    public void registerReport(Report report)
    {
        if (report == null)
            throw new IllegalArgumentException("Invalid report instance");

        if (null != _reports.putIfAbsent(report.getType(), report.getClass()))
            _log.warn("Report type : " + report.getType() + " has previously been registered.");
    }

    @Nullable
    public Report createReportInstance(String typeName)
    {
        // ConcurrentHashMap doesn't support null keys, so do the extra check ourselves
        if (typeName == null)
        {
            return null;
        }

        Class clazz = _reports.get(typeName);

        if (null == clazz)
            return null;

        try
        {
            if (Report.class.isAssignableFrom(clazz))
            {
                Report report = (Report)clazz.newInstance();
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

    public void containerDeleted(Container c, User user)
    {
        ContainerUtil.purgeTable(getTable(), c, "ContainerId");
        DatabaseReportCache.uncache(c);
    }

    public Report createFromQueryString(String queryString) throws Exception
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
            _deleteReport(context.getContainer(), reportId.getRowId());
            SecurityPolicyManager.deletePolicy(descriptor);
            tx.commit();
        }
    }

    private void _deleteReport(Container c, int reportId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ContainerId"), c.getId());
        filter.addCondition(FieldKey.fromParts("RowId"), reportId);
        Table.delete(getTable(), filter);
        DatabaseReportCache.uncache(c);
    }

    @Override
    public int saveReport(ContainerUser context, String key, Report report, boolean skipValidation)
    {
        return _saveReport(context, key, report, skipValidation).getRowId();
    }

    public int saveReport(ContainerUser context, String key, Report report)
    {
        return _saveReport(context, key, report).getRowId();
    }

    public void validateReportPermissions(ContainerUser context, Report report)
    {
        List<ValidationError> errors = new ArrayList<>();

        tryValidateReportPermissions(context, report, errors);

        if (!errors.isEmpty())
        {
            StringBuilder sb = new StringBuilder();
            for (ValidationError error : errors)
            {
                if (sb.length() > 0)
                    sb.append("\n");

                sb.append(error.getMessage());
            }

            throw new UnauthorizedException(sb.toString());
        }
    }

    public boolean tryValidateReportPermissions(ContainerUser context, Report report, List<ValidationError> errors)
    {
        final ReportDescriptor descriptor = report.getDescriptor();

        if (descriptor.isNew())
        {
            if (descriptor.isShared())
                report.canShare(context.getUser(), context.getContainer(), errors);
        }
        else
        {
            if (report.canEdit(context.getUser(), context.getContainer(), errors))
            {
                if (descriptor.isShared())
                    report.canShare(context.getUser(), context.getContainer(), errors);
            }
        }

        return errors.isEmpty();
    }

    private ReportDB _saveReport(ContainerUser context, String key, Report report)
    {
        return _saveReport(context, key, report, false);
    }

    private ReportDB _saveReport(ContainerUser context, String key, Report report, boolean skipValidation)
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

            r = _saveReport(context.getUser(), context.getContainer(), key, descriptor);
            tx.commit();
        }
        _saveReportProperties(context.getContainer(), r.getEntityId(), descriptor);
        return r;
    }

    private ReportDB _saveReport(User user, Container c, String key, ReportDescriptor descriptor)
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


        if (null != reportId && reportExists(reportId.getRowId()))
            reportDB = Table.update(user, getTable(), reportDB, reportId.getRowId());
        else
            reportDB = Table.insert(user, getTable(), reportDB);

        DatabaseReportCache.uncache(c);

        return reportDB;
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

    public Report _getInstance(ReportDB r)
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

    public Report getReportByEntityId(Container c, String entityId)
    {
        return DatabaseReportCache.getReportByEntityId(c, entityId);
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
                if ((report.getDescriptor().getFlags() & ReportDescriptor.FLAG_INHERITABLE) != 0)
                    return report;
                else
                    return null;
            }
        }

        // Look for this report in the shared project
        return (!ContainerManager.getSharedContainer().equals(c)) ? DatabaseReportCache.getReport(ContainerManager.getSharedContainer(), rowId) : null;
    }

    public ReportIdentifier getReportIdentifier(String reportId)
    {
        return AbstractReportIdentifier.fromString(reportId);
    }

    public Collection<Report> getReports(@Nullable User user, @NotNull Container c)
    {
        List<Report> reportsList = new ArrayList<>();
        reportsList.addAll(DatabaseReportCache.getReports(c));
        return getSortedReadableReports(reportsList, user);
    }

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

    @Deprecated
    public Collection<Report> getInheritableReports(User user, Container c, @Nullable String reportKey)
    {
        Collection<Report> inheritable = DatabaseReportCache.getInheritableReports(c);

        // If reportKey is specified then grab just those from the inheritable reports
        if (null != reportKey)
        {
            inheritable = inheritable.stream()
                .filter(report -> reportKey.equals(report.getDescriptor().getReportKey()))
                .collect(Collectors.toList());
        }

        List<Report> reportsList = new ArrayList<>();
        reportsList.addAll(inheritable);

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

    @Nullable
    public Report getReport(ReportDB reportDB)
    {
        return _getInstance(reportDB);
    }

    public void addUIProvider(UIProvider provider)
    {
        _uiProviders.add(provider);
    }

    public List<UIProvider> getUIProviders()
    {
        return Collections.unmodifiableList(_uiProviders);
    }

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

    public @Nullable String getIconCls(Report report)
    {
        if (report != null)
        {
            String reportType = report.getType();

            UIProvider claimingProvider = _typeToProviderMap.get(reportType);

            if (null != claimingProvider)
            {
                String iconClass = claimingProvider.getIconCls(report);  // may be null if report does not support CSS icons

                return iconClass;
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

    private boolean reportExists(int reportId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("RowId"), reportId);
        ReportDB report = new TableSelector(getTable(), filter, null).getObject(ReportDB.class);

        return (report != null);
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
    private Report deserialize(Container container, User user, XmlObject reportXml) throws IOException, XmlValidationException
    {
        if (null != reportXml)
        {
            Report report = _deserialize(container, user, reportXml);

            // reset any report identifier, we want to treat an imported report as a new
            // report instance
            if (report != null)
                report.getDescriptor().setReportId(new DbReportIdentifier(-1));

            return report;
        }

        throw new IllegalArgumentException("Report XML file does not exist.");
    }

    @Override @Nullable
    public Report importReport(ImportContext ctx, XmlObject reportXml, VirtualFile root) throws IOException, SQLException, XmlValidationException
    {
        Report report = deserialize(ctx.getContainer(), ctx.getUser(), reportXml);
        if (report != null)
        {
            ReportDescriptor descriptor = report.getDescriptor();
            String key = descriptor.getReportKey();
            if (StringUtils.isBlank(key))
            {
                // use the default key used by query views
                key = ReportUtil.getReportKey(descriptor.getProperty(ReportDescriptor.Prop.schemaName), descriptor.getProperty(ReportDescriptor.Prop.queryName));
            }

            List<Report> existingReports = new ArrayList<>();
            existingReports.addAll(getReports(ctx.getUser(), ctx.getContainer(), key));

            // in 13.2, there was a change to use dataset names instead of label for query references in reports, views, etc.
            // so if we are importing an older study archive, we need to also check for existing reports using the query name (i.e. dataset name)
            // NOTE: this will then be fixed up in the ReportImporter.postProcess
            if (ctx.getArchiveVersion() != null && ctx.getArchiveVersion() < 13.11)
            {
                String schema = descriptor.getProperty(ReportDescriptor.Prop.schemaName);
                StudyService svc = StudyService.get();
                Study study = svc != null ? svc.getStudy(ctx.getContainer()) : null;
                if (study != null && schema != null && schema.equals("study"))
                {
                    Dataset dataset = study.getDatasetByLabel(descriptor.getProperty(ReportDescriptor.Prop.queryName));
                    if (dataset != null && !dataset.getName().equals(dataset.getLabel()))
                    {
                        String newKey = ReportUtil.getReportKey(schema, dataset.getName());
                        existingReports.addAll(getReports(ctx.getUser(), ctx.getContainer(), newKey));
                    }
                }
            }

            for (Report existingReport : existingReports)
            {
                if (StringUtils.equalsIgnoreCase(existingReport.getDescriptor().getReportName(), descriptor.getReportName()))
                {
                    boolean shouldDelete = true;
                    if (ctx instanceof FolderImportContext)
                    {
                        // Don't delete reports we just added.  This can happen if the reportKey is not unique and
                        // we have two or more reports with the same name.  This also works in the reload case since
                        // existing reports will not have the same report ids as the newly imported/created ones.
                        if (((FolderImportContext)ctx).isImportedReport(existingReport.getDescriptor()))
                            shouldDelete = false;
                    }

                    if (shouldDelete)
                        deleteReport(new DefaultContainerUser(ctx.getContainer(), ctx.getUser()), existingReport);
                }
            }

            int rowId = _saveReport(ctx.getUser(), ctx.getContainer(), key, descriptor).getRowId();
            descriptor.setReportId(new DbReportIdentifier(rowId));

            // re-load the report to get the updated property information (i.e container, etc.)
            report = ReportService.get().getReport(ctx.getContainer(), rowId);

            // copy over the serialized report name
            report.getDescriptor().setProperty(ReportDescriptor.Prop.serializedReportName,
                    descriptor.getProperty(ReportDescriptor.Prop.serializedReportName));

            report.afterSave(ctx.getContainer(), ctx.getUser(), root);

            if (ctx instanceof FolderImportContext)
            {
                // remember that we imported this report so we don't try to delete it if
                // we are importing another report with the same reportKey and name.
                ((FolderImportContext)ctx).addImportedReport(report.getDescriptor());
            }

            // import any security role assignments
            if (reportXml instanceof ReportDescriptorDocument)
            {
                ReportDescriptorDocument doc = ((ReportDescriptorDocument)reportXml);
                ReportDescriptorType descriptorType = doc.getReportDescriptor();

                if (descriptorType.isSetRoleAssignments())
                {
                    RoleAssignmentsType assignments = descriptorType.getRoleAssignments();

                    MutableSecurityPolicy policy = new MutableSecurityPolicy(report.getDescriptor());
                    for (RoleAssignmentType assignmentXml : assignments.getRoleAssignmentArray())
                    {
                        Role role = RoleManager.getRole(assignmentXml.getRole().getName());
                        if (role != null)
                        {
                            if (assignmentXml.isSetGroups())
                            {
                                for (GroupRefType groupRef : assignmentXml.getGroups().getGroupArray())
                                {
                                    UserPrincipal principal = GroupManager.getGroup(ctx.getContainer(), groupRef.getName(), groupRef.getType());
                                    if (principal == null)
                                    {
                                        ctx.getLogger().warn("Non-existent group in role assignment for role " + assignmentXml.getRole().getName() + " will be ignored: " + groupRef.getName());
                                    }
                                    else
                                    {
                                        policy.addRoleAssignment(principal, role);
                                    }
                                }
                            }
                            if (assignmentXml.isSetUsers())
                            {
                                for (UserRefType userRef : assignmentXml.getUsers().getUserArray())
                                {
                                    try
                                    {
                                        ValidEmail validEmail = new ValidEmail(userRef.getName());
                                        UserPrincipal principal = UserManager.getUser(validEmail);

                                        if (principal == null)
                                        {
                                            ctx.getLogger().warn("Non-existent user in role assignment for role " + assignmentXml.getRole() + " will be ignored: " + userRef.getName());
                                        }
                                        else
                                        {
                                            policy.addRoleAssignment(principal, role);
                                        }
                                    }
                                    catch (ValidEmail.InvalidEmailException e)
                                    {
                                        ctx.getLogger().error("Invalid email in role assignment for role " + assignmentXml.getRole());
                                    }
                                }
                            }
                        }
                        else
                        {
                            ctx.getLogger().warn("Invalid role name ignored: " + assignmentXml.getRole());
                        }
                        SecurityPolicyManager.savePolicy(policy);
                    }
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
                if (StringUtils.equals(reportName, report.getDescriptor().getReportName()))
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
        private ReportServiceImpl _instance;

        private CategoryListener(ReportServiceImpl instance)
        {
            _instance = instance;
        }

        @Override
        public void categoryDeleted(User user, ViewCategory category) throws Exception
        {
            for (Report report : getDatabaseReportsForCategory(category))
            {
                Container c = ContainerManager.getForId(category.getContainerId());
                report.getDescriptor().setCategoryId(null);
                
                if (c != null)
                    _instance.saveReport(new DefaultContainerUser(c, user), report.getDescriptor().getReportKey(), report, true);
            }
        }

        @Override
        public void categoryCreated(User user, ViewCategory category) throws Exception {}

        @Override
        public void categoryUpdated(User user, ViewCategory category) throws Exception {}

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
        public String getDescription()
        {
            return "Report Service Maintenance";
        }

        @Override
        public String getName()
        {
            return "ReportService";
        }

        public void run(Logger log)
        {
            ReportService.get().maintenance(log);
        }
    }
}
