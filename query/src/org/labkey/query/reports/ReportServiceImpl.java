/*
 * Copyright (c) 2007-2014 LabKey Corporation
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
import org.labkey.api.cache.Cache;
import org.labkey.api.cache.CacheLoader;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.data.Filter;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.module.DefaultModule;
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
import org.labkey.api.reports.report.ModuleQueryJavaScriptReportDescriptor;
import org.labkey.api.reports.report.ModuleQueryRReportDescriptor;
import org.labkey.api.reports.report.ModuleQueryReportDescriptor;
import org.labkey.api.reports.report.ReportDB;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.reports.report.ReportIdentifierConverter;
import org.labkey.api.reports.report.ScriptEngineReport;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.SecurityPolicyManager;
import org.labkey.api.security.User;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.study.StudyService;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.Path;
import org.labkey.api.util.SystemMaintenance;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.view.UnauthorizedException;
import org.labkey.api.writer.ContainerUser;
import org.labkey.api.writer.DefaultContainerUser;
import org.labkey.api.writer.VirtualFile;

import java.io.IOException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * User: Karl Lum
 * Date: Dec 21, 2007
 */
public class ReportServiceImpl extends ContainerManager.AbstractContainerListener implements ReportService.I
{
    private static final String SCHEMA_NAME = "core";
    private static final String TABLE_NAME = "Report";
    private static final Logger _log = Logger.getLogger(ReportService.class);
    private static List<ReportService.ViewFactory> _viewFactories = new ArrayList<>();
    private static List<ReportService.UIProvider> _uiProviders = new ArrayList<>();
    private static Map<String, ReportService.UIProvider> _typeToProviderMap = new HashMap<>();

    private static final Cache<Integer, ReportDB> REPORT_DB_CACHE = CacheManager.getCache(CacheManager.UNLIMITED, CacheManager.DAY, "Database Report Cache");

    /** maps descriptor types to providers */
    private final ConcurrentMap<String, Class> _descriptors = new ConcurrentHashMap<>();

    /** maps report types to implementations */
    private final ConcurrentMap<String, Class> _reports = new ConcurrentHashMap<>();

    private final ReportQueryChangeListener _listener = new ReportQueryChangeListener();

    public ReportServiceImpl()
    {
        ContainerManager.addContainerListener(this);
        ConvertUtils.register(new ReportIdentifierConverter(), ReportIdentifier.class);
        QueryService.get().addQueryListener(_listener);
        QueryService.get().addCustomViewListener(_listener);
        SystemMaintenance.addTask(new ReportServiceMaintenanceTask());
        ViewCategoryManager.addCategoryListener(new CategoryListener(this));
    }

    private DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME);
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
    public ReportDescriptor getModuleReportDescriptor(Module module, Container container, User user, String path)
    {
        List<ReportDescriptor> ds = getModuleReportDescriptors(module, container, user, path);
        if (ds.size() == 1)
            return ds.get(0);
        return null;
    }

    @NotNull
    private List<ReportDescriptor> getAllModuleReportDescriptors(Module module, Container container, User user)
    {
        Set<Resource> reportFiles = module.getReportFiles();

        ArrayList<ReportDescriptor> list = new ArrayList<>(reportFiles.size());

        // Keep files that might be Query reports (end in .xml);
        // below we'll remove ones that are associated with R or JS reports
        HashMap<String, Resource> possibleQueryReportFiles = new HashMap<>();
        for (Resource file : reportFiles)
        {
            if (file.getName().toLowerCase().endsWith(ModuleQueryReportDescriptor.FILE_EXTENSION))
                possibleQueryReportFiles.put(file.getName(), file);
        }

        for (Resource file : reportFiles)
        {
            if (!DefaultModule.moduleReportFilter.accept(null, file.getName()))
                continue;

            ReportDescriptor descriptor = module.getCachedReport(file.getPath());
            if ((null == descriptor || descriptor.isStale()) && file.exists())
            {
                // NOTE: reportKeyToLegalFile() is not a two-way mapping, this can cause inconsistencies
                // so don't cache files with _ (underscore) in path
                descriptor = createModuleReportDescriptorInstance(module, file, container, user);
                if (!file.getPath().toString().contains("_"))
                    module.cacheReport(file.getPath(), descriptor);
            }

            if (null != descriptor)
            {
                descriptor.setContainer(container.getId());
                list.add(descriptor);
                if (null != descriptor.getMetaDataFile())
                    possibleQueryReportFiles.remove(descriptor.getMetaDataFile().getName());
            }
        }

        // Anything left if this map should be a Query Report
        list.addAll(getDescriptorsHelper(possibleQueryReportFiles.values(), module, container, user));

        return list;
    }

    @NotNull
    public List<ReportDescriptor> getModuleReportDescriptors(Module module, Container container, User user, @Nullable String path)
    {
        if (module.getReportFiles().isEmpty())
        {
            return Collections.emptyList();
        }
        else if (null == path)
        {
            return getAllModuleReportDescriptors(module, container, user);
        }

        Path legalPath = Path.parse(path);
        legalPath = getLegalFilePath(legalPath);

        // module relative file path
        Resource reportDirectory = module.getModuleResource(legalPath);
        Path moduleReportDirectory = getQueryReportsDirectory(module).getPath();

        // report folder relative file path
        if (null == reportDirectory)
        {
            reportDirectory = module.getModuleResource(moduleReportDirectory.append(legalPath));

            // The directory does not exist
            if (null == reportDirectory)
            {
                // 15966 -- check to see if it is resolving from parent report directory
                reportDirectory = module.getModuleResource(moduleReportDirectory.getParent().append(legalPath));

                if (null == reportDirectory)
                    return Collections.emptyList();
            }
        }

        // Check if it is a file
        if (!reportDirectory.isFile())
        {

            // Not a file so must be within the valid module report path
            if (!reportDirectory.getPath().startsWith(moduleReportDirectory))
                return Collections.emptyList();
        }
        else
        {
            // cannot access files outside of report directory
            if (!reportDirectory.getPath().startsWith(moduleReportDirectory))
                return Collections.emptyList();

            // It is a file so iterate across all files within this file's parent folder.
            reportDirectory = module.getModuleResource(reportDirectory.getPath().getParent());
        }

        HashMap<String, Resource> possibleQueryReportFiles = new HashMap<>();
        for (Resource file : reportDirectory.list())
        {
            if (file.getName().toLowerCase().endsWith(ModuleQueryReportDescriptor.FILE_EXTENSION))
                possibleQueryReportFiles.put(file.getName(), file);
        }

        List<ReportDescriptor> reportDescriptors = new ArrayList<>();
        ReportDescriptor descriptor;
        for (Resource file : reportDirectory.list())
        {
            if (!DefaultModule.moduleReportFilter.accept(null, file.getName()))
                continue;

            descriptor = module.getCachedReport(file.getPath());

            // cache miss
            if (null == descriptor || descriptor.isStale())
            {
                descriptor = createModuleReportDescriptorInstance(module, file, container, user);

                // NOTE: getLegalFilePath() is not a two-way mapping, this can cause inconsistencies
                // so don't cache files with _ (underscore) in path
                if (!file.getPath().toString().contains("_"))
                    module.cacheReport(file.getPath(), descriptor);
            }

            descriptor.setContainer(container.getId());

            // Return one file
            if (legalPath.getName().equals(file.getPath().getName()))
            {
                reportDescriptors.clear();
                reportDescriptors.add(descriptor);
                return reportDescriptors;
            }

            if (null != descriptor.getMetaDataFile())
                possibleQueryReportFiles.remove(descriptor.getMetaDataFile().getName());

            reportDescriptors.add(descriptor);
        }

        // Anything left if this map should be a Query Report
        for (Resource file : possibleQueryReportFiles.values())
        {
            descriptor = module.getCachedReport(file.getPath());

            // cache miss
            if (null == descriptor || descriptor.isStale())
            {
                descriptor = createModuleReportDescriptorInstance(module, file, container, user);

                // NOTE: getLegalFilePath() is not a two-way mapping, this can cause inconsistencies
                // so don't cache files with _ (underscore) in path
                if (!file.getPath().toString().contains("_"))
                    module.cacheReport(file.getPath(), descriptor);
            }

            // TODO: Make a copy of the cached ReportDescriptor rather than setting the container
            // as this does not hold up in race conditions.
            descriptor.setContainer(container.getId());

            // Return one file
            if (legalPath.getName().equals(file.getPath().getName()))
            {
                reportDescriptors.clear();
                reportDescriptors.add(descriptor);
                return reportDescriptors;
            }

            reportDescriptors.add(descriptor);
        }

        return reportDescriptors;
    }

    @NotNull
    private List<ReportDescriptor> getDescriptorsHelper(Collection<? extends Resource> files, Module module, Container container, User user)
    {
        List<ReportDescriptor> list = new ArrayList<>();
        ReportDescriptor descriptor;

        for (Resource file : files)
        {
            descriptor = module.getCachedReport(file.getPath());

            // cache miss
            if (null == descriptor || descriptor.isStale())
            {
                descriptor = createModuleReportDescriptorInstance(module, file, container, user);

                // NOTE: getLegalFilePath() is not a two-way mapping, this can cause inconsistencies
                // so don't cache files with _ (underscore) in path
                if (!file.getPath().toString().contains("_"))
                    module.cacheReport(file.getPath(), descriptor);
            }

            descriptor.setContainer(container.getId());

            list.add(descriptor);
        }

        return list;
    }

    @NotNull
    private ReportDescriptor createModuleReportDescriptorInstance(Module module, Resource reportFile, Container container, User user)
    {
        Path path = reportFile.getPath();
        String parent = path.getParent().toString("","");
        String lower = path.toString().toLowerCase();

        // Create R Report Descriptor
        if (ModuleQueryRReportDescriptor.accept(lower))
            return new ModuleQueryRReportDescriptor(module, parent, reportFile, path, container, user);

        // Create JS Report Descriptor
        if (lower.endsWith(ModuleQueryJavaScriptReportDescriptor.FILE_EXTENSION))
            return new ModuleQueryJavaScriptReportDescriptor(module, parent, reportFile, path, container, user);

         // Create Query Report Descriptor
        return new ModuleQueryReportDescriptor(module, parent, reportFile, path, container, user);
    }

    private Resource getQueryReportsDirectory(Module module)
    {
        return module.getModuleResource("reports/schemas");
    }

    private Path getLegalFilePath(@NotNull Path key)
    {
        Path legalPath = Path.emptyPath;

        for (int idx = 0; idx < key.size() ; ++idx)
            legalPath = legalPath.append(FileUtil.makeLegalName(key.get(idx)));

        return legalPath;
    }

    public void registerReport(Report report)
    {
        if (report == null)
            throw new IllegalArgumentException("Invalid report instance");

        if (null != _reports.putIfAbsent(report.getType(), report.getClass()))
            _log.warn("Report type : " + report.getType() + " has previously been registered.");
    }

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

    public Report createReportInstance(ReportDescriptor descriptor)
    {
        Report report = createReportInstance(descriptor.getReportType());
        report.setDescriptor(descriptor);
        return report;
    }

    public TableInfo getTable()
    {
        return getSchema().getTable(TABLE_NAME);
    }

    public void containerDeleted(Container c, User user)
    {
        ContainerUtil.purgeTable(getTable(), c, "ContainerId");
        REPORT_DB_CACHE.clear();
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
        REPORT_DB_CACHE.remove(reportId);
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
        ReportDB reportDB = new ReportDB(c, user.getUserId(), key, descriptor);

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

        REPORT_DB_CACHE.remove(reportDB.getRowId());

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

    private Report _getInstance(ReportDB r)
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
                        descriptor.setCategory(ViewCategoryManager.getInstance().getCategory(r.getCategoryId()));

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

    public Report getReportByEntityId(Container c, String entityId)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ContainerId"), c.getId());
        filter.addCondition(FieldKey.fromParts("EntityId"), entityId);

        ReportDB report = new TableSelector(getTable(), filter, null).getObject(ReportDB.class);
        return _getInstance(report);
    }

    public Report getReport(int reportId)
    {
        ReportDB reportDB = REPORT_DB_CACHE.get(reportId, null, new CacheLoader<Integer, ReportDB>()
        {
            @Override
            public ReportDB load(Integer key, @Nullable Object argument)
            {
                return new TableSelector(getTable(), new SimpleFilter(FieldKey.fromParts("RowId"), key), null).getObject(ReportDB.class);
            }
        });

        return _getInstance(reportDB);
    }

    public ReportIdentifier getReportIdentifier(String reportId)
    {
        return AbstractReportIdentifier.fromString(reportId);
    }

    private Report[] _getReports(User user, SimpleFilter filter)
    {
        if (filter != null && user != null)
            filter.addWhereClause("ReportOwner IS NULL OR CreatedBy = ?", new Object[]{user.getUserId()});

        return getReports(filter);
    }

    private Report[] _createReports(ReportDB[] rawReports)
    {
        if (rawReports.length > 0)
        {
            List<Report> descriptors = new ArrayList<>();
            for (ReportDB r : rawReports)
            {
                Report report = _getInstance(r);
                if (report != null)
                    descriptors.add(report);
            }
            if (descriptors.size() > 0)
                Collections.sort(descriptors, ReportComparator.getInstance());
            return descriptors.toArray(new Report[descriptors.size()]);
        }
        return EMPTY_REPORT;
    }

    public Report[] getReports(User user)
    {
        return _getReports(user, new SimpleFilter());
    }

    public Report[] getReports(User user, Container c)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ContainerId"), c.getId());
        return _getReports(user, filter);
    }

    public Report[] getReports(User user, Container c, String key)
    {
        List<ReportDescriptor> moduleReportDescriptors = new ArrayList<>();

        List<ReportDescriptor> descriptors;
        for (Module module : c.getActiveModules())
        {
            descriptors = getModuleReportDescriptors(module, c, user, key);
//            descriptors = module.getReportDescriptors(key, c, user);
            moduleReportDescriptors.addAll(descriptors);
        }

        List<Report> reports = new ArrayList<>();

        for (ReportDescriptor descriptor : moduleReportDescriptors)
        {
            String type = descriptor.getReportType();
            Report report = createReportInstance(type);

            if (report != null)
            {
                // the descriptor is a securable resource, so it must have a non-null container id, since file-based
                // module reports don't have a container associated, default to the current container security

                // issue 14552 : there is a problem with the way file modules cache report descriptors, it could cause
                // a situation where the module returns a descriptor for a folder that has been previously deleted.
                if (descriptor.getContainerId() == null || ContainerManager.getForId(descriptor.getContainerId()) == null)
                    descriptor.setContainerId(c.getId());

                report.setDescriptor(descriptor);
                reports.add(report);
            }
        }

        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ContainerId"), c.getId());

        if (key != null)
            filter.addCondition(FieldKey.fromParts("ReportKey"), key);

        reports.addAll(Arrays.asList(_getReports(user, filter)));
        return reports.toArray(new Report[reports.size()]);
    }

    public Report[] getReports(User user, Container c, String key, int flagMask, int flagValue)
    {
        SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ContainerId"), c.getId());

        if (key != null)
            filter.addCondition(FieldKey.fromParts("ReportKey"), key);

        SQLFragment ret = new SQLFragment("(((Flags");
        ret.append(") &");
        ret.append(flagMask);
        ret.append(") = ");
        ret.append(flagValue);
        ret.append(")");
        filter.addWhereClause(ret.getSQL(), ret.getParams().toArray(), FieldKey.fromParts("Flag"));

        return _getReports(user, filter);
    }

    public Report[] getReports(Filter filter)
    {
        ReportDB[] reports = new TableSelector(getTable(), filter, null).getArray(ReportDB.class);
        return _createReports(reports);
    }

    @Nullable
    public Report getReport(ReportDB reportDB)
    {
        ReportDB[] rawReportDBs = new ReportDB[1];
        rawReportDBs[0] = reportDB;
        Report[] reports = _createReports(rawReportDBs);
        if (reports.length > 0)
            return reports[0];
        return null;
    }

    /**
     * Provides a module specific way to add ui to the report designers.
     */
    public void addViewFactory(ReportService.ViewFactory vf)
    {
        _viewFactories.add(vf);
    }

    public List<ReportService.ViewFactory> getViewFactories()
    {
        return _viewFactories;
    }

    public void addUIProvider(ReportService.UIProvider provider)
    {
        _uiProviders.add(provider);
    }

    public List<ReportService.UIProvider> getUIProviders()
    {
        return Collections.unmodifiableList(_uiProviders);
    }

    public @NotNull String getIconPath(Report report)
    {
        if (report != null)
        {
            String reportType = report.getType();

            ReportService.UIProvider claimingProvider = _typeToProviderMap.get(reportType);

            if (null != claimingProvider)
            {
                String iconPath = claimingProvider.getIconPath(report);

                if (null == iconPath)
                    throw new IllegalStateException(reportType + " is claimed by " + claimingProvider + " but iconPath is null");

                return iconPath;
            }

            for (ReportService.UIProvider provider : _uiProviders)
            {
                String iconPath = provider.getIconPath(report);

                if (iconPath != null)
                {
                    _typeToProviderMap.put(reportType, provider);
                    return iconPath;
                }
            }
        }

        // No UIPRovider claimed this report type... so fall-back on blank image
        return "/_.gif";
    }

    private static final Report[] EMPTY_REPORT = new Report[0];

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
            {
                // befor resetting the report identifier, remember the serialized value
                String serializedReportId = report.getDescriptor().getProperty(ReportDescriptor.Prop.reportId);
                report.getDescriptor().setProperty(ReportDescriptor.Prop.serializedReportId, serializedReportId);

                report.getDescriptor().setReportId(new DbReportIdentifier(-1));
            }

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
            existingReports.addAll(Arrays.asList(getReports(ctx.getUser(), ctx.getContainer(), key)));

            // in 13.2, there was a change to use dataset names instead of label for query references in reports, views, etc.
            // so if we are importing an older study archive, we need to also check for existing reports using the query name (i.e. dataset name)
            // NOTE: this will then be fixed up in the ReportImporter.postProcess
            if (ctx.getArchiveVersion() != null && ctx.getArchiveVersion() < 13.11)
            {
                String schema = descriptor.getProperty(ReportDescriptor.Prop.schemaName);
                Study study = StudyService.get().getStudy(ctx.getContainer());
                if (study != null && schema != null && schema.equals("study"))
                {
                    DataSet dataset = study.getDatasetByLabel(descriptor.getProperty(ReportDescriptor.Prop.queryName));
                    if (dataset != null && !dataset.getName().equals(dataset.getLabel()))
                    {
                        String newKey = ReportUtil.getReportKey(schema, dataset.getName());
                        existingReports.addAll(Arrays.asList(getReports(ctx.getUser(), ctx.getContainer(), newKey)));
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
            report = ReportService.get().getReport(rowId);

            // copy over the serialized report id
            report.getDescriptor().setProperty(ReportDescriptor.Prop.serializedReportId,
                    descriptor.getProperty(ReportDescriptor.Prop.serializedReportId));

            report.afterSave(ctx.getContainer(), ctx.getUser(), root);

            if (ctx instanceof FolderImportContext)
            {
                // remember that we imported this report so we don't try to delete it if
                // we are importing another report with the same reportKey and name.
                ((FolderImportContext)ctx).addImportedReport(report.getDescriptor());
            }
        }
        return report;
    }

    @Override
    public void maintenance()
    {
        ScriptEngineReport.scheduledFileCleanup();
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
            for (Report report : getReportsForCategory(category))
            {
                Container c = ContainerManager.getForId(category.getContainerId());
                report.getDescriptor().setCategory(null);
                
                if (c != null)
                    _instance.saveReport(new DefaultContainerUser(c, user), report.getDescriptor().getReportKey(), report, true);
            }
        }

        @Override
        public void categoryCreated(User user, ViewCategory category) throws Exception {}

        @Override
        public void categoryUpdated(User user, ViewCategory category) throws Exception {}

        private Report[] getReportsForCategory(ViewCategory category)
        {
            if (category != null)
            {
                SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("ContainerId"), category.getContainerId());
                filter.addCondition(FieldKey.fromParts("CategoryId"), category.getRowId());
                return _instance.getReports(filter);
            }
            return new Report[0];
        }
    }

    private static class ReportComparator implements Comparator<Report>
    {
        private static final ReportComparator _instance = new ReportComparator();

        private ReportComparator(){}

        public static ReportComparator getInstance()
        {
            return _instance;
        }

        public int compare(Report o1, Report o2)
        {

            return o1.getDescriptor().getReportId().toString().compareToIgnoreCase(o2.getDescriptor().getReportId().toString());
        }
    }

    private static class ReportServiceMaintenanceTask implements SystemMaintenance.MaintenanceTask
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

        @Override
        public boolean canDisable()
        {
            return true;
        }

        @Override
        public boolean hideFromAdminPage() { return false; }

        public void run()
        {
            ReportService.get().maintenance();
        }
    }
}
