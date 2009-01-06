/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.reports.report.view.RReportBean;
import org.labkey.api.data.Container;
import org.labkey.api.data.Filter;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ActionURL;
import org.labkey.api.security.User;
import org.labkey.api.query.QuerySettings;

import java.sql.SQLException;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
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
        public Report createReportInstance(String typeName);
        public Report createReportInstance(ReportDescriptor descriptor);

        public void deleteReport(ViewContext context, Report report) throws SQLException;

        public int saveReport(ViewContext context, String key, Report report) throws SQLException;

        public Report getReport(int reportId) throws SQLException;
        public ReportIdentifier getReportIdentifier(String reportId);
        public Report[] getReports(User user) throws SQLException;
        public Report[] getReports(User user, Container c) throws SQLException;
        public Report[] getReports(User user, Container c, String key) throws SQLException;
        public Report[] getReports(User user, Container c, String key, int flagMask, int flagValue) throws SQLException;
        public Report[] getReports(Filter filter) throws SQLException;

        /**
         * Provides a module specific way to add ui to the report designers.
         */
        public void addViewFactory(ViewFactory vf);
        public List<ViewFactory> getViewFactories();

        public void addUIProvider(UIProvider provider);
        public List<UIProvider> getUIProviders();

        public Report createFromQueryString(String queryString) throws Exception;

        public String getReportIcon(ViewContext context, String reportType);
    }

    public interface ViewFactory
    {
        public HttpView createView(ViewContext context, RReportBean bean);
    }

    public interface DesignerInfo
    {
        /** the report type this builder is associated with */
        public String getReportType();

        public ActionURL getDesignerURL();

        /** the label to appear on any UI */
        public String getLabel();
    }

    public interface UIProvider
    {
        /**
         * Allows providers to add to the UI for creating reports (eg: the create view button).
         */
        public List<DesignerInfo> getReportDesignURL(ViewContext context, QuerySettings settings);

        /**
         * Returns the icon path to display for the specified report type
         */
        public String getReportIcon(ViewContext context, String reportType);
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
