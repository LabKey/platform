/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.data.Container;
import org.labkey.api.module.Module;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.ValidationError;
import org.labkey.api.reports.report.ReportDB;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
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
public interface ReportService
{
    String LINK_REPORT_TYPE = "ReportService.linkReport";

    static ReportService get()
    {
        return ServiceRegistry.get(ReportService.class);
    }

    static void registerProvider(ReportService provider)
    {
        ServiceRegistry.get().registerService(ReportService.class, provider);
    }

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

    /**
     * Returns a ReportDescriptor defined in the specified module at the specified path.
     * @param module Module to search for the ReportDescriptor
     * @param path Full path to a specific report, for example "reports/schemas/lists/People/Super Cool R Report.r". Note that
     *             inconsistency with getModuleReportDescriptors() below. Plus, this parameter should probably be a Path.
     * @return The ReportDescriptor matching the requested parameters or null, if no matching ReportDescriptor is found.
     */
    ReportDescriptor getModuleReportDescriptor(Module module, String path);

    /**
     * Returns a list of ReportDescriptors defined in the specified module, either those associated with the specified query path or all of them.
     * @param module Module to search for ReportDescriptors
     * @param path Path to a specific query, meaning return reports associated with that query, or null, meaning return all reports
     *             in this module. Unlike getModuleReportDescriptor() above, this path is relative to reports/schemas... for
     *             example, "list/People". Wouldn't a QueryKey be better here?
     * @return A list of ReportDescriptors matching the requested parameters.
     */
    List<ReportDescriptor> getModuleReportDescriptors(Module module, @Nullable String path);

    @Nullable
    Report createReportInstance(String typeName);

    @Nullable
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

    /**
     * Returns ONLY reports stored in the database (not module reports)
     */
    Collection<Report> getReports(@Nullable User user, @NotNull Container c);

    /**
     * Returns both database reports and module reports
     */
    Collection<Report> getReports(@Nullable User user, @NotNull Container c, @Nullable String key);

    // update the data views display order for this report
    void setReportDisplayOrder(ContainerUser context, Report report, int displayOrder);

    // TODO: This is only used by ReportUtils... remove from interface?
    Collection<Report> getInheritableReports(User user, Container c, String reportKey);

    @Nullable
    Report getReport(ReportDB reportDB);

    ReportIdentifier getReportIdentifier(String reportId);

    void addUIProvider(UIProvider provider);

    List<UIProvider> getUIProviders();

    Report createFromQueryString(String queryString) throws Exception;

    @NotNull
    String getIconPath(Report report);

    @Nullable
    String getIconCls(Report report);

    /**
     * Imports a serialized report into the database using the specified user and container
     * parameters. Imported reports are always treated as new reports even if they were exported from
     * the same container.
     */
    Report importReport(ImportContext ctx, XmlObject reportXml, VirtualFile root) throws IOException, SQLException, XmlValidationException;

    /**
     * Runs maintenance on the report service.
     */
    void maintenance(Logger log);

    boolean reportNameExists(ViewContext context, String reportName, String key);

    /**
     * Validates whether a user has the appropriate permissions to save the report with the changed
     * settings. Use tryValidateReportPermissions if you don't want to throw a runtime exception
     * on permissions failure
     */
    void validateReportPermissions(ContainerUser context, Report report);

    boolean tryValidateReportPermissions(ContainerUser context, Report report, List<ValidationError> errors);

    interface DesignerInfo
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

        @Nullable String getIconCls();

        DesignerType getType();
    }

    enum DesignerType
    {
        DEFAULT, VISUALIZATION
    }

    interface UIProvider
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

        /**
         * Returns simple path to browser accessible font icon in the webapp, e.g., "fa fa-table".
         * Returns null if this UIProvider does not support this report.
         */
        @Nullable String getIconCls(Report report);
    }

    interface ItemFilter
    {
        boolean accept(String type, String label);
    }

    ItemFilter EMPTY_ITEM_LIST = (type, label) -> false;
}
