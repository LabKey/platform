/*
 * Copyright (c) 2006-2015 LabKey Corporation
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
import org.jetbrains.annotations.NotNull;
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
import org.labkey.api.security.User;
import org.labkey.api.util.URLHelper;
import org.labkey.api.util.XmlValidationException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;
import org.labkey.api.writer.VirtualFile;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Collection;
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
        void registerReport(Report report);

        /**
         * A descriptor class must be registered with the service in order to be valid.
         */
        void registerDescriptor(ReportDescriptor descriptor);

        /**
         * creates new descriptor or report instances.
         */
        ReportDescriptor createDescriptorInstance(String typeName);
        ReportDescriptor getModuleReportDescriptor(Module module, Container container, User user, String path);
        List<ReportDescriptor> getModuleReportDescriptors(Module module, Container container, User user, @Nullable String path);

        Report createReportInstance(String typeName);
        Report createReportInstance(ReportDescriptor descriptor);

        void deleteReport(ContainerUser context, Report report);

        /**
         * Note: almost all cases of saveReport will want to use the version that does not skip validation.
         *       One example of where we skip validation is in the StudyUpgradeCode which has a method to fix report properties
         *       across all reports in the database (regardless of user)
         */
        int saveReport(ContainerUser context, String key, Report report, boolean skipValidation);
        int saveReport(ContainerUser context, String key, Report report);

        Report getReport(Container c, int reportId);
        Report getReportByEntityId(Container c, String entityId);

        Collection<Report> getReports(@Nullable User user, @NotNull Container c);

        Collection<Report> getReports(User user, Container c, String key);

        // TODO: This is only used by ReportUtils... remove from interface?
        Collection<Report> getInheritableReports(User user, Container c, String reportKey);

        @Deprecated
        Report[] getReports(Filter filter);

        @Nullable
        Report getReport(ReportDB reportDB);

        ReportIdentifier getReportIdentifier(String reportId);

        void addUIProvider(UIProvider provider);
        List<UIProvider> getUIProviders();

        Report createFromQueryString(String queryString) throws Exception;

        @NotNull String getIconPath(Report report);

        /**
         * Imports a serialized report into the database using the specified user and container
         * parameters. Imported reports are always treated as new reports even if they were exported from
         * the same container.
         */
        Report importReport(ImportContext ctx, XmlObject reportXml, VirtualFile root) throws IOException, SQLException, XmlValidationException;

        /**
         * Runs maintenance on the report service.
         */
        void maintenance();

        /**
         * Validates whether a user has the appropriate permissions to save the report with the changed
         * settings.  Use tryValidateReportPermissions if you don't want to throw a runtime exception
         * on permissions failure
         */
        void validateReportPermissions(ContainerUser context, Report report);
        boolean tryValidateReportPermissions(ContainerUser context, Report report, List<ValidationError> errors);
    }

    public interface DesignerInfo
    {
        /** the report type this builder is associated with */
        String getReportType();

        ActionURL getDesignerURL();

        /** the label to appear on any UI */
        String getLabel();

        String getDescription();

        boolean isDisabled();

        /** returns an id for automated testing purposes */
        String getId();

        @Nullable URLHelper getIconURL();

        DesignerType getType();
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
        List<DesignerInfo> getDesignerInfo(ViewContext context);

        /**
         * Allows providers to add UI for creating reports that may be associated with a query
         * (eg: the view/create button on a queryView).
         */
        List<DesignerInfo> getDesignerInfo(ViewContext context, QuerySettings settings);

        /**
         * Returns simple path to browser accessible static icon image in the webapp, e.g., "/reports/chart.gif". Callers must
         * turn this into a valid URL by pre-pending context path and adding look-and-feel revision (e.g., use ResourceURL).
         * Returns null if this UIProvider does not support this report.
         */
        @Nullable String getIconPath(Report report);
    }

    public interface ItemFilter
    {
        boolean accept(String type, String label);
    }

    public static ItemFilter EMPTY_ITEM_LIST = new ItemFilter() {
        public boolean accept(String type, String label)
        {
            return false;
        }
    };
}
