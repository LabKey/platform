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

package org.labkey.query.reports;

import org.apache.log4j.Logger;
import org.labkey.api.data.*;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ReportDB;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.security.User;
import org.labkey.api.util.ContainerUtil;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ViewContext;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleLoader;
import org.labkey.common.util.Pair;

import java.beans.PropertyChangeEvent;
import java.sql.SQLException;
import java.util.*;
import java.io.File;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Dec 21, 2007
 */
public class ReportServiceImpl implements ReportService.I, ContainerManager.ContainerListener
{
    private static final String SCHEMA_NAME = "core";
    private static final String TABLE_NAME = "Report";
    private static final Logger _log = Logger.getLogger(ReportService.class);
    private static List<ReportService.ViewFactory> _viewFactories = new ArrayList<ReportService.ViewFactory>();
    private static List<ReportService.UIProvider> _uiProviders = new ArrayList<ReportService.UIProvider>();
    private static Map<String, String> _reportIcons = new HashMap<String, String>();

    /** maps descriptor types to providers */
    private final Map<String, Class> _descriptors = new HashMap<String, Class>();

    /** maps report types to implementations */
    private final Map<String, Class> _reports = new HashMap<String, Class>();

    public ReportServiceImpl()
    {
        ContainerManager.addContainerListener(this);
    }

    private DbSchema getSchema()
    {
        return DbSchema.get(SCHEMA_NAME);
    }

    public void registerDescriptor(ReportDescriptor descriptor)
    {
        if (descriptor == null)
            throw new IllegalArgumentException("Invalid descriptor instance");
        synchronized(_descriptors)
        {
            if (_descriptors.containsKey(descriptor.getDescriptorType()))
                 _log.warn("Descriptor type : " + descriptor.getDescriptorType() + " has previously been registered.");
                //throw new IllegalStateException("Descriptor type : " + descriptor.getDescriptorType() + " has previously been registered.");

            _descriptors.put(descriptor.getDescriptorType(), descriptor.getClass());
        }
    }

    public ReportDescriptor createDescriptorInstance(String typeName)
    {
        synchronized(_descriptors)
        {
            if (_descriptors.containsKey(typeName))
            {
                Class clazz = _descriptors.get(typeName);
                try {
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
        }
        return null;
    }

    public void registerReport(Report report)
    {
        if (report == null)
            throw new IllegalArgumentException("Invalid report instance");
        synchronized(_reports)
        {
            if (_reports.containsKey(report.getType()))
                _log.warn("Report type : " + report.getType() + " has previously been registered.");
                //throw new IllegalStateException("Report type : " + report.getType() + " has previously been registered.");

            _reports.put(report.getType(), report.getClass());
        }
    }

    public Report createReportInstance(String typeName)
    {
        synchronized(_reports)
        {
            if (_reports.containsKey(typeName))
            {
                Class clazz = _reports.get(typeName);
                try {
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
        }
        return null;
    }

    public TableInfo getTable()
    {
        return getSchema().getTable(TABLE_NAME);
    }

    public void containerCreated(Container c) {}
    public void propertyChange(PropertyChangeEvent evt) {}

    public void containerDeleted(Container c, User user)
    {
        try {
            ContainerUtil.purgeTable(getTable(), c, "ContainerId");
        }
        catch (SQLException x)
        {
            _log.error("Error occured deleting reports for container", x);
        }
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

    public void deleteReport(ViewContext context, Report report) throws SQLException
    {
        DbScope scope = getTable().getSchema().getScope();
        boolean ownsTransaction = !scope.isTransactionActive();
        try
        {
            if (ownsTransaction)
                scope.beginTransaction();
            report.beforeDelete(context);

            final ReportDescriptor descriptor = report.getDescriptor();
            _deleteReport(context.getContainer(), descriptor.getReportId());
            final Container c =ContainerManager.getForId(descriptor.getContainerId());
            org.labkey.api.security.SecurityManager.removeACL(c, descriptor.getEntityId());
            if (ownsTransaction)
                scope.commitTransaction();
        }
        finally
        {
            if (ownsTransaction)
                scope.closeConnection();
        }
    }

    private void _deleteReport(Container c, int reportId) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("ContainerId", c.getId());
        filter.addCondition("RowId", reportId);
        Table.delete(getTable(), filter);
    }


    private Report _createReport(Class reportClass) throws Exception
    {
        if (Report.class.isAssignableFrom(reportClass))
        {
            return (Report)reportClass.newInstance();
        }
        throw new IllegalArgumentException("The specified class: " + reportClass.getName() + " does not implement the org.labkey.api.reports.Report interface");
    }

    public int saveReport(ViewContext context, String key, Report report) throws SQLException
    {
        return _saveReport(context, key, report).getRowId();
    }

    private ReportDB _saveReport(ViewContext context, String key, Report report) throws SQLException
    {
        DbScope scope = getTable().getSchema().getScope();
        boolean ownsTransaction = !scope.isTransactionActive();
        try
        {
            if (ownsTransaction)
                scope.beginTransaction();
            report.beforeSave(context);

            final ReportDescriptor descriptor = report.getDescriptor();
            final ReportDB r = _saveReport(context.getUser(), context.getContainer(), key, descriptor);
            if (ownsTransaction)
                scope.commitTransaction();
            return r;
        }
        finally
        {
            if (ownsTransaction)
                scope.closeConnection();
        }
    }

    private ReportDB _saveReport(User user, Container c, String key, ReportDescriptor descriptor) throws SQLException
    {
        ReportDB reportDB = new ReportDB(c, user.getUserId(), key, descriptor);
        if (descriptor.getReportId() != -1 && reportExists(descriptor.getReportId()))
            reportDB = Table.update(user, getTable(), reportDB, descriptor.getReportId(), null);
        else
            reportDB = Table.insert(user, getTable(), reportDB);

        return reportDB;
    }

    private Report _getInstance(ReportDB r)
    {
        if (r != null)
        {
            ReportDescriptor descriptor = ReportDescriptor.createFromXML(r.getDescriptorXML());
            if (descriptor != null)
            {
                descriptor.setReportId(r.getRowId());
                descriptor.setReportKey(r.getReportKey());
                descriptor.setContainerId(r.getContainerId());
                descriptor.setEntityId(r.getEntityId());
                descriptor.setOwner(r.getReportOwner());
                descriptor.setModifiedBy(r.getModifiedBy());
                descriptor.setCreatedBy(r.getCreatedBy());
                descriptor.setFlags(r.getFlags());

                String type = descriptor.getReportType();
                Report report = createReportInstance(type);
                if (report != null)
                    report.setDescriptor(descriptor);
                return report;
            }
        }
        return null;
    }

    public Report getReportByEntityId(Container c, String entityId) throws Exception
    {
        SimpleFilter filter = new SimpleFilter("ContainerId", c.getId());
        filter.addCondition("EntityId", entityId);

        ReportDB report = Table.selectObject(getTable(), filter, null, ReportDB.class);
        return _getInstance(report);
    }

    public Report getReport(int reportId) throws SQLException
    {
        ReportDB report = Table.selectObject(getTable(), new SimpleFilter("RowId", reportId), null, ReportDB.class);
        return _getInstance(report);
    }

    private Report[] _getReports(User user, SimpleFilter filter) throws SQLException
    {
        if (filter != null && user != null)
            filter.addWhereClause("ReportOwner IS NULL OR ReportOwner = ?", new Object[]{user.getUserId()});

        return getReports(filter);
    }

    private Report[] _createReports(ReportDB[] rawReports)
    {
        if (rawReports.length > 0)
        {
            List<Report> descriptors = new ArrayList<Report>();
            for (ReportDB r : rawReports)
            {
                Report report = _getInstance(r);
                if (report != null)
                    descriptors.add(report);
            }
            if (descriptors.size() > 0)
                Collections.sort(descriptors, ReportComparator.getInstance());
            return descriptors.toArray(new Report[0]);
        }
        return EMPTY_REPORT;
    }

    public Report[] getReports(User user) throws SQLException
    {
        return _getReports(user, new SimpleFilter());
    }

    public Report[] getReports(User user, Container c) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("ContainerId", c.getId());
        return _getReports(user, filter);
    }

    public Report[] getReports(User user, Container c, String key) throws SQLException
    {
        List<ReportDescriptor> moduleReportDescriptors = new ArrayList<ReportDescriptor>();
        for(Module module : ModuleLoader.getInstance().getModules())
        {
            List<ReportDescriptor> descriptors = module.getReportDescriptors(key);
            if(null != descriptors)
                moduleReportDescriptors.addAll(descriptors);
        }

        List<Report> reports = new ArrayList<Report>();
        for(ReportDescriptor descriptor : moduleReportDescriptors)
        {
            String type = descriptor.getReportType();
            Report report = createReportInstance(type);
            if (report != null)
            {
                report.setDescriptor(descriptor);
                reports.add(report);
            }
        }

        SimpleFilter filter = new SimpleFilter("ContainerId", c.getId());
        if (key != null)
            filter.addCondition("ReportKey", key);
        reports.addAll(Arrays.asList(_getReports(user, filter)));
        return reports.toArray(new Report[reports.size()]);
    }

    public Report[] getReports(User user, Container c, String key, int flagMask, int flagValue) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("ContainerId", c.getId());
        if (key != null)
            filter.addCondition("ReportKey", key);

        SQLFragment ret = new SQLFragment("(((Flags");
        ret.append(") &");
        ret.append(flagMask);
        ret.append(") = ");
        ret.append(flagValue);
        ret.append(")");
        filter.addWhereClause(ret.getSQL(), ret.getParams().toArray(), "Flag");

        return _getReports(user, filter);
    }

    public Report[] getReports(Filter filter) throws SQLException
    {
        ReportDB[] reports = Table.select(getTable(), Table.ALL_COLUMNS, filter, null, ReportDB.class);
        return _createReports(reports);
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

    public String getReportIcon(ViewContext context, String reportType)
    {
        if (_reportIcons.containsKey(reportType))
            return _reportIcons.get(reportType);

        for (ReportService.UIProvider provider : _uiProviders)
        {
            String iconPath = provider.getReportIcon(context, reportType);
            if (iconPath != null)
            {
                _reportIcons.put(reportType, iconPath);
                return iconPath;
            }
        }
        return null;
    }

    private static final Report[] EMPTY_REPORT = new Report[0];

    private boolean reportExists(int reportId) throws SQLException
    {
        SimpleFilter filter = new SimpleFilter("RowId", reportId);
        ReportDB report = Table.selectObject(getTable(), filter, null, ReportDB.class);

        return (report != null);
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
            return o1.getDescriptor().getReportId() - o2.getDescriptor().getReportId();
        }
    }
}
