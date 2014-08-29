/*
 * Copyright (c) 2006-2014 LabKey Corporation
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

package org.labkey.api.reports;

import org.apache.xmlbeans.XmlObject;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.data.Filter;
import org.labkey.api.module.Module;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.ValidationError;
import org.labkey.api.reports.report.ReportDB;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.reports.report.view.ScriptReportBean;
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;
import org.labkey.api.writer.VirtualFile;

import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

/**
 * User: Karl Lum
 * Date: Dec 21, 2007
 */
public class ReportService
{
    static private I _instance;

    public static synchronized ReportService.I get()
    {
        return _instance;
    }

    private ReportService(){}

    static public synchronized void registerProvider(I provider)
    {
        // only one provider for now
        if (_instance != null)
            throw new IllegalStateException("A report service provider :" + _instance.getClass().getName() + " has already been registered");

        _instance = provider;
    }

    public interface I
    {
        /**
         * Registers a report type, reports must be registered in order to be created.
         */
        public void registerReport(Report report);

        /**
         * A descriptor class must be registered with the service in order to be valid.
         */
        public void registerDescriptor(ReportDescriptor descriptor);

        /**
         * creates new descriptor or report instances.
         */
        public ReportDescriptor createDescriptorInstance(String typeName);
        public ReportDescriptor getModuleReportDescriptor(Module module, Container container, User user, String path);
        public List<ReportDescriptor> getModuleReportDescriptors(Module module, Container container, User user, @Nullable String path);

        public Report createReportInstance(String typeName);
        public Report createReportInstance(ReportDescriptor descriptor);

        public void deleteReport(ContainerUser context, Report report);

        /**
         * Note: almost all cases of saveReport will want to use the version that does not skip validation.
         *       One example of where we skip validation is in the StudyUpgradeCode which has a method to fix report properties
         *       across all reports in the database (regardless of user)
         */
        public int saveReport(ContainerUser context, String key, Report report, boolean skipValidation);
        public int saveReport(ContainerUser context, String key, Report report);

        public Report getReport(int reportId);
        public Report getReportByEntityId(Container c, String entityId);
        public ReportIdentifier getReportIdentifier(String reportId);
        public Report[] getReports(User user);
        public Report[] getReports(User user, Container c);
        public Report[] getReports(User user, Container c, String key);
        public Report[] getReports(User user, Container c, String key, int flagMask, int flagValue);
        public Report[] getReports(Filter filter);
        @Nullable public Report getReport(ReportDB reportDB);

        /**
         * Provides a module specific way to add ui to the report designers.
         */
        public void addViewFactory(ViewFactory vf);
        public List<ViewFactory> getViewFactories();

        public void addUIProvider(UIProvider provider);
        public List<UIProvider> getUIProviders();

        public Report createFromQueryString(String queryString) throws Exception;

        public String getIconPath(Report report);

        /**
         * Imports a serialized report into the database using the specified user and container
         * parameters. Imported reports are always treated as new reports even if they were exported from
         * the same container.
         */
        public Report importReport(ImportContext ctx, XmlObject reportXml, VirtualFile root) throws IOException, SQLException, XmlValidationException;

        /**
         * Runs maintenance on the report service.
         */
        public void maintenance();

        /**
         * Validates whether a user has the appropriate permissions to save the report with the changed
         * settings.  Use tryValidateReportPermissions if you don't want to throw a runtime exception
         * on permissions failure
         */
        public void validateReportPermissions(ContainerUser context, Report report);
        public boolean tryValidateReportPermissions(ContainerUser context, Report report, List<ValidationError> errors);
    }

    public interface ViewFactory
    {
        public String getExtraFormHtml(ViewContext ctx, ScriptReportBean bean) throws ServletException;
    }

    public interface DesignerInfo
    {
        /** the report type this builder is associated with */
        public String getReportType();

        public ActionURL getDesignerURL();

        /** the label to appear on any UI */
        public String getLabel();

        public String getDescription();

        public boolean isDisabled();

        /** returns an id for automated testing purposes */
        public String getId();

        public @Nullable URLHelper getIconURL();

        public DesignerType getType();
    }

    public enum DesignerType
    {
        DEFAULT, VISUALIZATION
    }

    public interface UIProvider
    {
        /**
         * Allows providers to add UI for creating reports not associated with a query
         */
        public List<DesignerInfo> getDesignerInfo(ViewContext context);

        /**
         * Allows providers to add UI for creating reports that may be associated with a query
         * (eg: the view/create button on a queryView).
         */
        public List<DesignerInfo> getDesignerInfo(ViewContext context, QuerySettings settings);

        /**
         * Returns simple path to browser accessible static icon image in the webapp, e.g., "/reports/chart.gif". Callers must
         * turn this into a valid URL by pre-pending context path and adding look-and-feel revision (e.g., use ResourceURL).
         * Returns null if this UIProvider does not support this report.
         */
        public @Nullable String getIconPath(Report report);
    }

    public interface ItemFilter
    {
        public boolean accept(String type, String label);
    }

    public static ItemFilter EMPTY_ITEM_LIST = new ItemFilter() {
        public boolean accept(String type, String label)
        {
            return false;
        }
    };
}
