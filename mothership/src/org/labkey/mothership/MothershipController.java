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

package org.labkey.mothership;

import org.apache.commons.beanutils.ConversionException;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.routines.InetAddressValidator;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jfree.chart.ChartColor;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.ChartUtilities;
import org.jfree.chart.JFreeChart;
import org.jfree.chart.axis.NumberAxis;
import org.jfree.chart.plot.XYPlot;
import org.jfree.chart.renderer.xy.XYItemRenderer;
import org.jfree.chart.renderer.xy.XYLineAndShapeRenderer;
import org.jfree.data.time.Day;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.xy.XYDataset;
import org.labkey.api.action.FormHandlerAction;
import org.labkey.api.action.ReturnUrlForm;
import org.labkey.api.action.SimpleViewAction;
import org.labkey.api.action.SpringActionController;
import org.labkey.api.data.*;
import org.labkey.api.data.Container;
import org.labkey.api.module.AllowedDuringUpgrade;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.RuntimeValidationException;
import org.labkey.api.security.CSRF;
import org.labkey.api.security.RequiresNoPermission;
import org.labkey.api.security.RequiresPermission;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.InsertPermission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.permissions.UpdatePermission;
import org.labkey.api.util.GUID;
import org.labkey.api.util.MothershipReport;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.BadRequestException;
import org.labkey.api.view.DetailsView;
import org.labkey.api.view.GridView;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.InsertView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.RedirectException;
import org.labkey.api.view.UpdateView;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewServlet;
import org.labkey.mothership.query.MothershipSchema;
import org.springframework.validation.BindException;
import org.springframework.validation.Errors;
import org.springframework.web.servlet.ModelAndView;

import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * User: jeckels
 * Date: Apr 19, 2006
 */
public class MothershipController extends SpringActionController
{
    private static final DefaultActionResolver _actionResolver = new DefaultActionResolver(MothershipController.class);
    private static final Logger _log = Logger.getLogger(MothershipController.class);

    public MothershipController()
    {
        setActionResolver(_actionResolver);
    }

    @RequiresPermission(ReadPermission.class)
    public class BeginAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors)
        {
            ActionURL url = new ActionURL(ShowExceptionsAction.class, getContainer());
            url.addParameter(DataRegion.LAST_FILTER_PARAM, "true");
            throw new RedirectException(url);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public class ShowUpdateAction extends SimpleViewAction<SoftwareReleaseForm>
    {
        public ModelAndView getView(SoftwareReleaseForm form, BindException errors)
        {
            return new UpdateView(form, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Update Release Info");
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public class UpdateAction extends FormHandlerAction<SoftwareReleaseForm>
    {
        public void validateCommand(SoftwareReleaseForm target, Errors errors)
        {
        }

        public boolean handlePost(SoftwareReleaseForm form, BindException errors)
        {
            SoftwareRelease release = form.getBean();
            MothershipManager.get().updateSoftwareRelease(getContainer(), getUser(), release);
            return true;
        }

        public ActionURL getSuccessURL(SoftwareReleaseForm softwareReleaseForm)
        {
            return new ActionURL(ShowReleasesAction.class, getContainer());
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ShowReleasesAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors)
        {
            MothershipSchema schema = new MothershipSchema(getUser(), getContainer());
            QuerySettings settings = schema.getSettings(getViewContext(), "softwareReleases", MothershipSchema.SOFTWARE_RELEASES_TABLE_NAME);
            settings.getBaseSort().insertSortColumn("-SVNRevision");

            QueryView queryView = schema.createView(getViewContext(), settings, errors);

            return new VBox(getLinkBar(), queryView);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Installations");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ShowRegistrationInstallationGraphAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            Calendar start = new GregorianCalendar();
            start.add(Calendar.DATE, -2);
            Calendar cal = new GregorianCalendar();
            cal.set(2006, 6, 1, 0, 0);

            TimeSeries runningTotal = new TimeSeries("Total installations");
            TimeSeries registrations = new TimeSeries("Registered users");

            while (cal.compareTo(start) < 0)
            {
                registrations.add(new Day(cal.getTime()), UserManager.getUserCount(cal.getTime()));

                int totalExternalCount = 0;
                for (ServerInstallation installation : MothershipManager.get().getServerInstallationsActiveBefore(cal))
                {
                    if (!installation.getServerIP().startsWith("140.107"))
                    {
                        totalExternalCount++;
                    }
                }
                runningTotal.add(new Day(cal.getTime()), totalExternalCount);

                cal.add(Calendar.DATE, 7);
            }
            TimeSeriesCollection dataset = new TimeSeriesCollection();
            dataset.addSeries(runningTotal);
            dataset.addSeries(registrations);
            JFreeChart chart = createChart(dataset, "Total Users and Total Installations", "Count");
            XYPlot plot = chart.getXYPlot();

            XYItemRenderer renderer = plot.getRenderer();
            renderer.setSeriesPaint(0, ChartColor.RED);
            renderer.setSeriesPaint(1, ChartColor.BLUE);

            getViewContext().getResponse().setContentType("image/png");
            ChartUtilities.writeChartAsPNG(getViewContext().getResponse().getOutputStream(), chart, 800, 400);
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ShowActiveInstallationGraphAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            Calendar start = new GregorianCalendar();
            start.add(Calendar.DATE, -2);
            Calendar cal = new GregorianCalendar();
            cal.set(2006, 6, 1, 0, 0);

            TimeSeries externalPings = new TimeSeries("External active");
            TimeSeries repeatPings = new TimeSeries("External that also pinged the previous week");
            Set<String> repeatServerGUIDs = new HashSet<>();

            while (cal.compareTo(start) < 0)
            {
                Collection<ServerInstallation> installations = MothershipManager.get().getServerInstallationsActiveOn(cal);
                int externalCount = 0;
                int repeatCount = 0;
                for (ServerInstallation installation : installations)
                {
                    if (!installation.getServerIP().startsWith("140.107"))
                    {
                        externalCount++;
                    }

                    if (repeatServerGUIDs.contains(installation.getServerInstallationGUID()))
                    {
                        repeatCount++;
                    }
                }
                externalPings.add(new Day(cal.getTime()), externalCount);
                repeatPings.add(new Day(cal.getTime()), repeatCount);

                repeatServerGUIDs.clear();
                for (ServerInstallation installation : installations)
                {
                    if (!installation.getServerIP().startsWith("140.107"))
                    {
                        repeatServerGUIDs.add(installation.getServerInstallationGUID());
                    }
                }

                cal.add(Calendar.DATE, 7);
            }
            TimeSeriesCollection dataset = new TimeSeriesCollection();
            dataset.addSeries(externalPings);
            dataset.addSeries(repeatPings);
            JFreeChart chart = createChart(dataset, "Active External Installations", "Count");

            XYPlot plot = chart.getXYPlot();

            XYItemRenderer renderer = plot.getRenderer();
            renderer.setSeriesPaint(0, ChartColor.RED);
            renderer.setSeriesPaint(1, ChartColor.BLUE);

            getViewContext().getResponse().setContentType("image/png");
            ChartUtilities.writeChartAsPNG(getViewContext().getResponse().getOutputStream(), chart, 800, 400);
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    private JFreeChart createChart(final XYDataset dataset, String title, String label)
    {
        // create the chart...
        final JFreeChart chart = ChartFactory.createTimeSeriesChart(
                title,      // chart title
                "Date",                      // x axis label
                label,                      // y axis label
                dataset,                  // data
                true,                     // include legend
                true,                     // tooltips
                false                     // urls
        );

        // NOW DO SOME OPTIONAL CUSTOMISATION OF THE CHART...
        chart.setBackgroundPaint(Color.white);

//        final StandardLegend legend = (StandardLegend) chart.getLegend();
        //      legend.setDisplaySeriesShapes(true);

        // get a reference to the plot for further customisation...
        final XYPlot plot = chart.getXYPlot();
        plot.setBackgroundPaint(Color.lightGray);
        //    plot.setAxisOffset(new Spacer(Spacer.ABSOLUTE, 5.0, 5.0, 5.0, 5.0));
        plot.setDomainGridlinePaint(Color.white);
        plot.setRangeGridlinePaint(Color.white);
        plot.getRangeAxis().setLowerBound(0.0);

        final XYLineAndShapeRenderer renderer = new XYLineAndShapeRenderer();
        renderer.setLinesVisible(true);
        renderer.setShapesVisible(false);
        renderer.setStroke(new BasicStroke(2.0f));
        plot.setRenderer(renderer);

        // change the auto tick unit selection to integer units only...
        final NumberAxis rangeAxis = (NumberAxis) plot.getRangeAxis();
        rangeAxis.setStandardTickUnits(NumberAxis.createIntegerTickUnits());
        // OPTIONAL CUSTOMISATION COMPLETED.

        return chart;
    }

    @RequiresPermission(DeletePermission.class)
    public class DeleteAction extends FormHandlerAction
    {
        public void validateCommand(Object target, Errors errors)
        {
        }

        public boolean handlePost(Object o, BindException errors)
        {
            Set<Integer> releaseIds = DataRegionSelection.getSelectedIntegers(getViewContext(), true);
            for (Integer releaseId : releaseIds)
                MothershipManager.get().deleteSoftwareRelease(getContainer(), releaseId);
            return true;
        }

        public ActionURL getSuccessURL(Object o)
        {
            return new ActionURL(ShowReleasesAction.class, getContainer());
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ShowExceptionsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors)
        {
            MothershipSchema schema = new MothershipSchema(getUser(), getContainer());
            QuerySettings settings = schema.getSettings(getViewContext(), "ExceptionSummary", MothershipSchema.EXCEPTION_STACK_TRACE_TABLE_NAME);
            settings.getBaseSort().insertSortColumn(FieldKey.fromParts("ExceptionStackTraceId"), Sort.SortDirection.DESC);

            QueryView queryView = schema.createView(getViewContext(), settings, errors);
            queryView.setShowDetailsColumn(false);
            queryView.setShadeAlternatingRows(true);
            queryView.setShowBorders(true);

            return new VBox(getLinkBar(), queryView);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Exceptions");
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class ShowInsertAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors)
        {
            DataRegion region = new DataRegion();
            region.addColumns(MothershipManager.get().getTableInfoSoftwareRelease(), "SVNRevision,Description");
            return new InsertView(region, errors);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Insert Software Release");
        }
    }

    @RequiresPermission(InsertPermission.class)
    public class InsertAction extends FormHandlerAction<SoftwareReleaseForm>
    {
        public void validateCommand(SoftwareReleaseForm target, Errors errors)
        {
        }

        public boolean handlePost(SoftwareReleaseForm form, BindException errors)
        {
            MothershipManager.get().insertSoftwareRelease(getContainer(), getUser(), form.getBean());
            return true;
        }

        public ActionURL getSuccessURL(SoftwareReleaseForm softwareReleaseForm)
        {
            return new ActionURL(ShowReleasesAction.class, getContainer());
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ShowInstallationsAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors)
        {
            MothershipSchema schema = new MothershipSchema(getUser(), getContainer());
            QuerySettings settings = schema.getSettings(getViewContext(), "serverInstallations", MothershipSchema.SERVER_INSTALLATIONS_TABLE_NAME);
            settings.setSchemaName(schema.getSchemaName());
            settings.getBaseSort().insertSortColumn(FieldKey.fromParts("LastPing"), Sort.SortDirection.DESC);

            settings.addAggregates(
                new Aggregate(FieldKey.fromParts("DaysActive"), Aggregate.BaseType.MEAN),
                new Aggregate(FieldKey.fromParts("ExceptionCount"), Aggregate.BaseType.MEAN)
            );

            QueryView gridView = schema.createView(getViewContext(), settings, errors);

            return new VBox(getLinkBar(), gridView);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Installations");
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public class CreateIssueFinishedAction extends SimpleViewAction<CreateIssueFinishedForm>
    {
        public ModelAndView getView(CreateIssueFinishedForm form, BindException errors)
        {
            ExceptionStackTrace stackTrace = MothershipManager.get().getExceptionStackTrace(form.getExceptionStackTraceId(), getContainer());
            stackTrace.setBugNumber(form.getIssueId());
            stackTrace.setAssignedTo(form.getAssignedTo());
            MothershipManager.get().updateExceptionStackTrace(stackTrace, getUser());
            throw new RedirectException(new ActionURL(BeginAction.class, getContainer()));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            throw new UnsupportedOperationException();
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public class EditUpgradeMessageAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors)
        {
            UpgradeMessageForm form = new UpgradeMessageForm();

            form.setCurrentRevision(MothershipManager.get().getCurrentRevision(getContainer()));
            form.setMessage(MothershipManager.get().getUpgradeMessage(getContainer()));
            form.setCreateIssueURL(MothershipManager.get().getCreateIssueURL(getContainer()));
            form.setIssuesContainer(MothershipManager.get().getIssuesContainer(getContainer()));

            return new VBox(getLinkBar(), new JspView<>("/org/labkey/mothership/editUpgradeMessage.jsp", form));
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Upgrade Message");
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    @RequiresPermission(UpdatePermission.class)
    public class SaveUpgradeMessageAction extends FormHandlerAction<UpgradeMessageForm>
    {
        public void validateCommand(UpgradeMessageForm target, Errors errors)
        {
        }

        public boolean handlePost(UpgradeMessageForm form, BindException errors)
        {
            MothershipManager.get().setCurrentRevision(getContainer(), form.getCurrentRevision());
            MothershipManager.get().setUpgradeMessage(getContainer(), form.getMessage());
            MothershipManager.get().setCreateIssueURL(getContainer(), form.getCreateIssueURL());
            MothershipManager.get().setIssuesContainer(getContainer(), form.getIssuesContainer());
            return true;
        }

        public ActionURL getSuccessURL(UpgradeMessageForm upgradeMessageForm)
        {
            return new ActionURL(BeginAction.class, getContainer());
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ShowServerSessionDetailAction extends SimpleViewAction<ServerSessionForm>
    {
        public ModelAndView getView(ServerSessionForm form, BindException errors)
        {
            ServerSession session = form.getBean();
            if (session == null)
            {
                throw new NotFoundException();
            }
            ServerSessionDetailView detailView = new ServerSessionDetailView(form);

            MothershipSchema schema = new MothershipSchema(getUser(), getContainer());
            QuerySettings settings = new QuerySettings(getViewContext(), "ExceptionReports", MothershipSchema.EXCEPTION_REPORT_WITH_STACK_TABLE_NAME);
            settings.getBaseSort().insertSortColumn(FieldKey.fromParts("Created"), Sort.SortDirection.DESC);
            settings.getBaseFilter().addCondition(FieldKey.fromParts("ServerSessionId"), session.getServerSessionId());

            QueryView exceptionGridView = new QueryView(schema, settings, errors);
            exceptionGridView.setShadeAlternatingRows(true);
            exceptionGridView.setShowBorders(true);
            exceptionGridView.setButtonBarPosition(DataRegion.ButtonBarPosition.TOP);
            exceptionGridView.setShowExportButtons(false);

            return new VBox(detailView, exceptionGridView);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Server Session");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ShowInstallationDetailAction extends SimpleViewAction<ServerInstallationForm>
    {
        public ModelAndView getView(ServerInstallationForm form, BindException errors)
        {
            ServerInstallation installation;
            try
            {
                installation = form.getBean();
            }
            catch (Exception e)
            {
                throw new NotFoundException();
            }
            if (installation == null || null == form.getPkVal())
            {
                throw new NotFoundException();
            }

            ServerInstallationUpdateView updateView = new ServerInstallationUpdateView(form, errors);

            MothershipSchema schema = new MothershipSchema(MothershipController.this.getUser(), MothershipController.this.getContainer());
            QuerySettings settings = schema.getSettings(getViewContext(), "ServerSessions", "ServerSessions");
            settings.getBaseSort().insertSortColumn(FieldKey.fromParts("ServerSessionId"), Sort.SortDirection.DESC);
            settings.getBaseFilter().addCondition(FieldKey.fromParts("ServerInstallationId"), installation.getServerInstallationId());

            QueryView sessionGridView = schema.createView(getViewContext(), settings, errors);
            sessionGridView.setShowBorders(true);
            sessionGridView.setShowExportButtons(false);

            return new VBox(updateView, sessionGridView);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Server Installation");
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class ShowStackTraceDetailAction extends SimpleViewAction<ExceptionStackTraceForm>
    {
        public ModelAndView getView(ExceptionStackTraceForm form, BindException errors) throws Exception
        {
            ExceptionStackTrace stackTrace;
            try
            {
                stackTrace = form.getBean();
                stackTrace = MothershipManager.get().getExceptionStackTrace(stackTrace.getExceptionStackTraceId(), getContainer());
            }
            catch (ConversionException e)
            {
                throw new NotFoundException();
            }
            if (stackTrace == null)
            {
                throw new NotFoundException();
            }
            ExceptionStackTraceUpdateView updateView = new ExceptionStackTraceUpdateView(form, getViewContext().getActionURL(), getContainer(), errors);

            MothershipSchema schema = new MothershipSchema(getUser(), getContainer());
            QuerySettings settings = new QuerySettings(getViewContext(), "ExceptionReports", MothershipSchema.EXCEPTION_REPORT_TABLE_NAME);
            settings.getBaseSort().insertSortColumn(FieldKey.fromString("Created"), Sort.SortDirection.DESC);
            settings.getBaseFilter().addCondition(FieldKey.fromString("ExceptionStackTraceId"), stackTrace.getExceptionStackTraceId());

            QueryView summaryGridView = new QueryView(schema, settings, errors);
            summaryGridView.setShowBorders(true);
            summaryGridView.setShadeAlternatingRows(true);
            summaryGridView.setButtonBarPosition(DataRegion.ButtonBarPosition.TOP);
            return new VBox(updateView, summaryGridView, constructCreateIssueForm(stackTrace));
        }

        private JspView constructCreateIssueForm(ExceptionStackTrace stackTrace) throws IOException
        {
            // Moved from CreateIssueDisplayColumn. Instead of piggybacking off the ExceptionStackTraceForm,
            // we now have a separate hidden form on the page to have control over exactly which fields
            // are submitted.
            ActionURL callbackURL = getViewContext().getActionURL().clone();
            callbackURL.setAction(MothershipController.CreateIssueFinishedAction.class);
            Map<String, String> cifModel = new HashMap<>();
            cifModel.put("callbackURL", callbackURL.toString());
            String originalURL = (String)getViewContext().getRequest().getAttribute(ViewServlet.ORIGINAL_URL_STRING);
            StringBuilder body = new StringBuilder();
            body.append("Created from crash report: ");
            body.append(originalURL);
            body.append("\n\n");
            String stackTraceString = stackTrace.getStackTrace();
            body.append(stackTraceString);

            StringBuilder title = new StringBuilder();
            BufferedReader reader = new BufferedReader(new StringReader(stackTraceString));
            // Grab the exception class
            String className = reader.readLine().split("\\:")[0];
            if (className.lastIndexOf('.') != -1)
            {
                // Strip off the package name to make the title a little shorter
                className = className.substring(className.lastIndexOf('.') + 1);
            }
            title.append(className);
            String firstLocation = reader.readLine();
            String location = firstLocation;
            String separator = " in ";
            while (location != null &&
                    (!location.contains("org.labkey") || location.contains(ConnectionWrapper.class.getPackage().getName())) &&
                    !location.contains("org.fhcrc"))
            {
                location = reader.readLine();
                separator = " from ";
            }

            if (location == null)
            {
                location = firstLocation;
            }
            if (location != null)
            {
                location = location.trim();
                if (location.startsWith("at "))
                {
                    location = location.substring("at ".length());
                }
                title.append(separator);
                title.append(location.split("\\(")[0]);
                title.append("()");
            }
            cifModel.put("body", body.toString());
            cifModel.put("title", title.toString());
            cifModel.put("action", MothershipManager.get().getCreateIssueURL(getContainer()));

            return new JspView<>("/org/labkey/mothership/view/createIssue.jsp", cifModel);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Exception Reports");
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public class UpdateStackTraceAction extends FormHandlerAction<ExceptionStackTraceForm>
    {
        public void validateCommand(ExceptionStackTraceForm target, Errors errors)
        {
        }

        public boolean handlePost(ExceptionStackTraceForm form, BindException errors) throws Exception
        {
            form.doUpdate();
            return true;
        }

        public ActionURL getSuccessURL(ExceptionStackTraceForm exceptionStackTraceForm)
        {
            return new ActionURL(ShowExceptionsAction.class, getContainer()).addParameter(DataRegion.LAST_FILTER_PARAM, "true");
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public class UpdateInstallationAction extends FormHandlerAction<ServerInstallationForm>
    {
        public void validateCommand(ServerInstallationForm target, Errors errors)
        {
        }

        public boolean handlePost(ServerInstallationForm form, BindException errors) throws Exception
        {
            form.doUpdate();
            return true;
        }

        public ActionURL getSuccessURL(ServerInstallationForm form)
        {
            return new ActionURL(ShowInstallationDetailAction.class, getContainer()).addParameter("serverInstallationId", form.getPkVal().toString());
        }
    }

    @RequiresPermission(ReadPermission.class)
    public class JumpToErrorCodeAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors)
        {
            String errorCode = StringUtils.trimToNull((String)getProperty("errorCode"));
            ActionURL url;
            if (errorCode != null)
            {
                TableInfo exceptionReportTable = new MothershipSchema(getUser(), getContainer()).getTable(MothershipSchema.EXCEPTION_REPORT_TABLE_NAME);
                SimpleFilter filter = new SimpleFilter(FieldKey.fromParts("errorCode"), errorCode);
                List<String> stackTraceIds = new TableSelector(exceptionReportTable.getColumn(FieldKey.fromParts("ExceptionStackTraceId")), filter, null)
                                                .getArrayList(String.class)
                                                .stream()
                                                .distinct()
                                                .collect(Collectors.toList());
                if (stackTraceIds.size() == 1)
                {
                    url = new ActionURL(MothershipController.ShowStackTraceDetailAction.class, getContainer());
                    url.addParameter("exceptionStackTraceId", stackTraceIds.get(0));
                }
                else
                {
                    url = new ActionURL(MothershipController.ShowExceptionsAction.class, getContainer());
                    if (stackTraceIds.isEmpty())
                    {
                        url.addFilter("ExceptionSummary", FieldKey.fromParts("ExceptionStackTraceId"), CompareType.ISBLANK, null);
                    }
                    else
                    {
                        url.addFilter("ExceptionSummary", FieldKey.fromParts("ExceptionStackTraceId"), CompareType.IN, String.join(";", stackTraceIds));
                    }
                }
            }
            else
            {
                url = new ActionURL(MothershipController.ShowExceptionsAction.class, getContainer());
            }
            return HttpView.redirect(url);
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    // API for inserting exceptions reported from this or other LabKey servers.
    @SuppressWarnings("UnusedDeclaration")
    @RequiresNoPermission
    @AllowedDuringUpgrade
    @CSRF(CSRF.Method.NONE) // Shouldn't force other servers to get a CSRF token just to post an exception report
    public class ReportExceptionAction extends SimpleViewAction<ExceptionForm>
    {
        public ModelAndView getView(ExceptionForm form, BindException errors) throws Exception
        {
            try
            {
                ServerInstallation installation = new ServerInstallation();
                if (form.getServerGUID() == null)
                {
                    logger.warn("No serverGUID specified in exception report from " + installation.getServerIP() + ", making one up so we don't lose the exception");
                    installation.setServerInstallationGUID(GUID.makeGUID());
                }
                else
                {
                    ServerInstallation existingInstallation = MothershipManager.get().getServerInstallation(form.getServerGUID(), getContainer());
                    if (null != existingInstallation && Boolean.TRUE.equals(existingInstallation.getIgnoreExceptions()))
                    {
                        // Mothership is set to ignore exceptions from this installation, so just return
                        return null;
                    }
                    installation.setServerInstallationGUID(form.getServerGUID());
                }

                installation.setServerIP(getRemoteAddr(installation.getServerInstallationGUID()));
                ExceptionStackTrace stackTrace = new ExceptionStackTrace();
                stackTrace.setStackTrace(form.getStackTrace());
                stackTrace.setContainer(getContainer().getId());

                ServerSession session = form.toSession(getContainer());

                installation.setUsedInstaller(form.isUsedInstaller());
                session = MothershipManager.get().updateServerSession(form.getServerHostName(), session, installation, getContainer());
                if (form.getSvnRevision() != null && form.getSvnURL() != null)
                {
                    ExceptionReport report = new ExceptionReport();
                    report.setExceptionMessage(form.getExceptionMessage());
                    if (null == form.getExceptionMessage() && stackTrace.getStackTrace() != null)
                    {
                        // Grab the first line of the exception report so that we don't lose things like
                        // file paths or other unique info that's thrown away as part of the de-dupe process
                        // for otherwise identical stacks
                        report.setExceptionMessage(stackTrace.getStackTrace().split("[\\r\\n]")[0]);
                    }
                    report.setURL(form.getRequestURL());
                    report.setUsernameform(form.getUsername());
                    report.setReferrerURL(form.getReferrerURL());
                    report.setSQLState(form.getSqlState());
                    report.setPageflowAction(form.getPageflowAction());
                    report.setPageflowName(form.getPageflowName());
                    report.setBrowser(form.getBrowser());
                    report.setServerSessionId(session.getServerSessionId());
                    report.setErrorCode(form.getErrorCode());

                    MothershipManager.get().insertException(stackTrace, report);
                }
                setSuccessHeader();
            }
            catch (RuntimeValidationException e)
            {
                // MothershipReport was malformed and couldn't be persisted
                throw new BadRequestException(null, null);
            }
            catch (Exception e)
            {
                // Need to catch and not rethrow or this failure might submit
                // an exception report, which would fail and report an exception,
                // and continue infinitely.
                _log.error("Failed to log exception report", e);
            }
            return null;
        }

        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    @SuppressWarnings("UnusedDeclaration")
    @RequiresPermission(ReadPermission.class)
    public class ThrowExceptionAction extends SimpleViewAction
    {
        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
//            throw new UnsupportedOperationException("Intentional exception for testing purposes");
            throw new SQLException("Intentional exception for testing purposes", "400");
        }

        public NavTree appendNavTrail(NavTree root)
        {
            throw new UnsupportedOperationException("Intentional exception for testing purposes");
        }
    }

    @CSRF(CSRF.Method.NONE)
    @SuppressWarnings("UnusedDeclaration")
    @RequiresNoPermission
    public class CheckForUpdatesAction extends SimpleViewAction<UpdateCheckForm>
    {
        public ModelAndView getView(UpdateCheckForm form, BindException errors) throws Exception
        {
            // First log this installation and session
            ServerSession session = form.toSession(getContainer());
            ServerInstallation installation = new ServerInstallation();
            if (form.getServerGUID() != null)
            {
                installation.setServerInstallationGUID(form.getServerGUID());
                installation.setLogoLink(form.getLogoLink());
                installation.setOrganizationName(form.getOrganizationName());
                installation.setServerIP(getRemoteAddr(form.getServerGUID()));
                installation.setSystemDescription(form.getSystemDescription());
                installation.setSystemShortName(form.getSystemShortName());
                installation.setContainer(getContainer().getId());
                installation.setUsedInstaller(form.isUsedInstaller());
                MothershipManager.get().updateServerSession(form.getServerHostName(), session, installation, getContainer());
                setSuccessHeader();
                getViewContext().getResponse().getWriter().print(getUpgradeMessage(form.parseSvnRevision()));
            }

            return null;
        }


        public NavTree appendNavTrail(NavTree root)
        {
            return null;
        }
    }

    /**
     * @return If this server is behind a load balancer, get the original request IP instead of the load balancer's address.
     * @param serverGUID
     */
    private String getRemoteAddr(String serverGUID)
    {
        String forwardedFor = getViewContext().getRequest().getHeader(MothershipReport.X_FORWARDED_FOR);
        if (null != forwardedFor)
        {
            if (InetAddressValidator.getInstance().isValid(forwardedFor))
                return forwardedFor;
            else
                _log.warn("Invalid (spoofed?) IP address submitted in mothership report for server GUID: " + serverGUID + " . Bad IP: " + forwardedFor);

        }
        return getViewContext().getRequest().getRemoteAddr();
    }

    private void setSuccessHeader()
    {
        getViewContext().getResponse().setHeader(MothershipReport.MOTHERSHIP_STATUS_HEADER_NAME, MothershipReport.MOTHERSHIP_STATUS_SUCCESS);
    }

    @RequiresPermission(ReadPermission.class)
    public class ReportsAction extends SimpleViewAction
    {
        public NavTree appendNavTrail(NavTree root)
        {
            return root.addChild("Mothership Reports");
        }

        public ModelAndView getView(Object o, BindException errors) throws Exception
        {
            HtmlView graphView = new HtmlView("Installations", "<img src=\"mothership-showActiveInstallationGraph.view\" height=\"400\" width=\"800\" /><br/><br/><img src=\"mothership-showRegistrationInstallationGraph.view\" height=\"400\" width=\"800\" />");
            return new VBox(getLinkBar(), new UnbuggedExceptionsGridView(), new UnassignedExceptionsGridView(), graphView);
        }
    }

    private class ResultSetGridView extends GridView
    {
        public ResultSetGridView(String title, String sql) throws SQLException
        {
            super(new DataRegion(), (BindException) null);
            setTitle(title);
            ResultSet rs = new SqlSelector(MothershipManager.get().getSchema(), new SQLFragment(sql)).getResultSet();
            List<ColumnInfo> colInfos = DataRegion.colInfosFromMetaData(rs.getMetaData());
            setResults(new ResultsImpl(rs, colInfos));
            getDataRegion().setSettings(new QuerySettings(getViewContext(), title.replace("\"","")));
            getDataRegion().setColumns(colInfos);
            getDataRegion().setSortable(false);
            getDataRegion().setShowFilters(false);
            getDataRegion().setButtonBar(ButtonBar.BUTTON_BAR_EMPTY);
            getDataRegion().setShowPagination(false);
        }
    }

    private class UnbuggedExceptionsGridView extends ResultSetGridView
    {
        public UnbuggedExceptionsGridView() throws SQLException
        {
            super("\"Unbugged\" Exceptions by Owner",
                    "SELECT core.usersdata.displayname as Owner, count(*) as ExceptionCount \n" +
                            "FROM mothership.exceptionstacktrace, core.principals, core.usersdata\n" +
                            "WHERE assignedto IS NOT NULL AND bugnumber IS NULL\n" +
                            "and core.principals.userid = assignedto\n" +
                            "and core.principals.userid = core.usersdata.userid\n" +
                            "group by core.usersdata.displayname order by ExceptionCount DESC");
            getDataRegion().getDisplayColumn("Owner").setURL("mothership-showExceptions.view?ExceptionSummary.BugNumber~isblank=&ExceptionSummary.AssignedTo/DisplayName~eq=${Owner}");
            getDataRegion().getDisplayColumn("Owner").setWidth("200");
            getDataRegion().getDisplayColumn("ExceptionCount").setCaption("Exception Count");
        }
    }

    private class UnassignedExceptionsGridView extends ResultSetGridView
    {
        public UnassignedExceptionsGridView() throws SQLException
        {
            super("Unassigned Exceptions",
                    "SELECT COUNT(ExceptionStackTraceId) AS TotalCount\n" +
                            "FROM Mothership.ExceptionStackTrace AS Trace\n" +
                            "WHERE Trace.AssignedTo IS NULL AND Trace.BugNumber IS NULL");
            getDataRegion().getDisplayColumn("TotalCount").setURL("mothership-showExceptions.view?ExceptionSummary.AssignedTo/DisplayName~isblank&ExceptionSummary.BugNumber~isblank");
        }
    }

    private String getUpgradeMessage(Integer rev)
    {
        int currentRevision = MothershipManager.get().getCurrentRevision(getContainer());

        if (rev != null && rev.intValue() < currentRevision)
        {
            return MothershipManager.get().getUpgradeMessage(getContainer());
        }
        return "";
    }

    private JspView getLinkBar()
    {
        return new JspView("/org/labkey/mothership/view/linkBar.jsp");
    }

    public static abstract class ServerInfoForm
    {
        private String _svnRevision;
        private String _svnURL;
        private String _runtimeOS;
        private String _javaVersion;
        private String _databaseProductName;
        private String _databaseProductVersion;
        private String _databaseDriverName;
        private String _databaseDriverVersion;
        private String _serverSessionGUID;
        private String _serverGUID;

        private Integer _userCount;
        private Integer _activeUserCount;
        private Integer _projectCount;
        private Integer _containerCount;
        private Integer _heapSize;
        private String _administratorEmail;
        private boolean _enterprisePipelineEnabled;
        private String _servletContainer;
        private boolean _usedInstaller;
        private String _description;
        private String _distribution;
        private String _usageReportingLevel;
        private String _exceptionReportingLevel;
        private String _jsonMetrics;
        private String _serverHostName;

        public String getSvnURL()
        {
            return _svnURL;
        }

        public void setSvnURL(String svnURL)
        {
            _svnURL = svnURL;
        }

        public String getRuntimeOS()
        {
            return _runtimeOS;
        }

        public void setRuntimeOS(String runtimeOS)
        {
            _runtimeOS = runtimeOS;
        }

        public String getJavaVersion()
        {
            return _javaVersion;
        }

        public void setJavaVersion(String javaVersion)
        {
            _javaVersion = javaVersion;
        }

        public String getDatabaseProductName()
        {
            return _databaseProductName;
        }

        public void setDatabaseProductName(String databaseProductName)
        {
            _databaseProductName = databaseProductName;
        }

        public String getDatabaseProductVersion()
        {
            return _databaseProductVersion;
        }

        public void setDatabaseProductVersion(String databaseProductVersion)
        {
            _databaseProductVersion = databaseProductVersion;
        }

        public String getDatabaseDriverName()
        {
            return _databaseDriverName;
        }

        public void setDatabaseDriverName(String databaseDriverName)
        {
            _databaseDriverName = databaseDriverName;
        }

        public String getDatabaseDriverVersion()
        {
            return _databaseDriverVersion;
        }

        public void setDatabaseDriverVersion(String databaseDriverVersion)
        {
            _databaseDriverVersion = databaseDriverVersion;
        }

        public String getServerSessionGUID()
        {
            return _serverSessionGUID;
        }

        public void setServerSessionGUID(String serverSessionGUID)
        {
            _serverSessionGUID = serverSessionGUID;
        }

        public String getServerGUID()
        {
            return _serverGUID;
        }

        public void setServerGUID(String serverGUID)
        {
            _serverGUID = serverGUID;
        }

        public String getSvnRevision()
        {
            return _svnRevision;
        }

        public Integer parseSvnRevision()
        {
            try
            {
                return new Integer(getSvnRevision());
            }
            catch (NumberFormatException e)
            {
                // Probably not built from an SVN enlistment
                return null;
            }
        }

        public void setSvnRevision(String svnRevision)
        {
            _svnRevision = svnRevision;
        }

        public Integer getUserCount()
        {
            return _userCount;
        }

        public void setUserCount(Integer userCount)
        {
            _userCount = userCount;
        }

        public Integer getActiveUserCount()
        {
            return _activeUserCount;
        }

        public void setActiveUserCount(Integer activeUserCount)
        {
            _activeUserCount = activeUserCount;
        }

        public Integer getProjectCount()
        {
            return _projectCount;
        }

        public void setProjectCount(Integer projectCount)
        {
            _projectCount = projectCount;
        }

        public Integer getContainerCount()
        {
            return _containerCount;
        }

        public void setContainerCount(Integer containerCount)
        {
            _containerCount = containerCount;
        }

        public String getAdministratorEmail()
        {
            return _administratorEmail;
        }

        public void setAdministratorEmail(String administratorEmail)
        {
            _administratorEmail = administratorEmail;
        }

        public Integer getHeapSize()
        {
            return _heapSize;
        }

        public void setHeapSize(Integer heapSize)
        {
            _heapSize = heapSize;
        }

        public String getServerHostName()
        {
            return _serverHostName;
        }

        public void setServerHostName(String serverHostName)
        {
            _serverHostName = serverHostName;
        }

        public ServerSession toSession(Container container)
        {
            ServerSession session = new ServerSession();
            SoftwareRelease release = MothershipManager.get().ensureSoftwareRelease(container, parseSvnRevision(), getSvnURL(), getDescription());
            session.setSoftwareReleaseId(release.getSoftwareReleaseId());

            session.setServerSessionGUID(getServerSessionGUID());
            session.setDatabaseDriverName(getDatabaseDriverName());
            session.setDatabaseDriverVersion(getDatabaseDriverVersion());
            session.setDatabaseProductName(getDatabaseProductName());
            session.setDatabaseProductVersion(getDatabaseProductVersion());
            session.setRuntimeOS(getRuntimeOS());
            session.setJavaVersion(getJavaVersion());
            session.setContainer(container.getId());
            session.setActiveUserCount(getActiveUserCount());
            session.setUserCount(getUserCount());
            session.setProjectCount(getProjectCount());
            session.setContainerCount(getContainerCount());
            session.setAdministratorEmail(getAdministratorEmail());
            session.setEnterprisePipelineEnabled(isEnterprisePipelineEnabled());
            session.setHeapSize(getHeapSize());
            session.setServletContainer(getServletContainer());
            session.setDistribution(getDistribution());
            session.setUsageReportingLevel(getUsageReportingLevel());
            session.setExceptionReportingLevel(getExceptionReportingLevel());
            session.setJsonMetrics(getJsonMetrics());

            return session;
        }

        public boolean isEnterprisePipelineEnabled()
        {
            return _enterprisePipelineEnabled;
        }

        public void setEnterprisePipelineEnabled(boolean enterprisePipelineEnabled)
        {
            _enterprisePipelineEnabled = enterprisePipelineEnabled;
        }

        public String getServletContainer()
        {
            return _servletContainer;
        }

        public void setServletContainer(String servletContainer)
        {
            _servletContainer = servletContainer;
        }

        public boolean isUsedInstaller()
        {
            return _usedInstaller;
        }

        public void setUsedInstaller(boolean usedInstaller)
        {
            _usedInstaller = usedInstaller;
        }

        public String getDescription()
        {
            return _description;
        }

        public void setDescription(String description)
        {
            _description = description;
        }

        public String getDistribution()
        {
            return _distribution;
        }

        public void setDistribution(String distribution)
        {
            _distribution = distribution;
        }

        public String getUsageReportingLevel()
        {
            return _usageReportingLevel;
        }

        public void setUsageReportingLevel(String usageReportingLevel)
        {
            _usageReportingLevel = usageReportingLevel;
        }

        public String getExceptionReportingLevel()
        {
            return _exceptionReportingLevel;
        }

        public void setExceptionReportingLevel(String exceptionReportingLevel)
        {
            _exceptionReportingLevel = exceptionReportingLevel;
        }

        public String getJsonMetrics()
        {
            return _jsonMetrics;
        }

        public void setJsonMetrics(String jsonMetrics)
        {
            _jsonMetrics = jsonMetrics;
        }
    }

    public static class UpdateCheckForm extends ServerInfoForm
    {
        private String _systemDescription;
        private String _logoLink;
        private String _organizationName;
        private String _systemShortName;

        public String getLogoLink()
        {
            return _logoLink;
        }

        public void setLogoLink(String logoLink)
        {
            _logoLink = logoLink;
        }

        public String getOrganizationName()
        {
            return _organizationName;
        }

        public void setOrganizationName(String organizationName)
        {
            _organizationName = organizationName;
        }

        public String getSystemShortName()
        {
            return _systemShortName;
        }

        public void setSystemShortName(String systemShortName)
        {
            _systemShortName = systemShortName;
        }

        public String getSystemDescription()
        {
            return _systemDescription;
        }

        public void setSystemDescription(String systemDescription)
        {
            _systemDescription = systemDescription;
        }
    }

    public static class ExceptionForm extends ServerInfoForm
    {
        private String _stackTrace;
        private String _requestURL;
        private String _browser;
        private String _username;
        private String _referrerURL;
        private String _pageflowName;
        private String _pageflowAction;
        private String _sqlState;
        private String _errorCode;

        public String getExceptionMessage()
        {
            return _exceptionMessage;
        }

        public void setExceptionMessage(String exceptionMessage)
        {
            _exceptionMessage = exceptionMessage;
        }

        private String _exceptionMessage;

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


        public String getPageflowAction()
        {
            return _pageflowAction;
        }

        public void setPageflowAction(String pageflowAction)
        {
            _pageflowAction = pageflowAction;
        }

        public String getPageflowName()
        {
            return _pageflowName;
        }

        public void setPageflowName(String pageflowName)
        {
            _pageflowName = pageflowName;
        }

        public String getReferrerURL()
        {
            return _referrerURL;
        }

        public void setReferrerURL(String referrerURL)
        {
            _referrerURL = referrerURL;
        }

        public String getSqlState()
        {
            return _sqlState;
        }

        public void setSqlState(String sqlState)
        {
            _sqlState = sqlState;
        }

        public String getErrorCode()
        {
            return _errorCode;
        }

        public void setErrorCode(String errorCode)
        {
            _errorCode = errorCode;
        }
    }

    public static class BulkUpdateForm extends ReturnUrlForm
    {
        private Integer _userId;
        private boolean _ignore = false;

        public Integer getUserId()
        {
            return _userId;
        }

        public void setUserId(Integer userId)
        {
            _userId = userId;
        }

        public boolean isIgnore()
        {
            return _ignore;
        }

        public void setIgnore(boolean ignore)
        {
            _ignore = ignore;
        }
    }

    @RequiresPermission(UpdatePermission.class)
    public class BulkUpdateAction extends FormHandlerAction<BulkUpdateForm>
    {
        @Override
        public void validateCommand(BulkUpdateForm form, Errors errors)
        {
        }

        @Override
        public boolean handlePost(BulkUpdateForm form, BindException errors)
        {
            Set<String> keys = DataRegionSelection.getSelected(getViewContext(), true);

            User user = null;
            if (form.getUserId() != null)
            {
                user = UserManager.getUser(form.getUserId());
                if (!MothershipManager.get().getAssignedToList(getContainer()).contains(user))
                {
                    throw new NotFoundException("User not available to assign to: " + form.getUserId());
                }
            }
            else if (!form.isIgnore())
            {
                throw new IllegalStateException("Neither userId nor ignore were set");
            }

            for (String key : keys)
            {
                try
                {
                    int rowId = Integer.parseInt(key);
                    ExceptionStackTrace exceptionStackTrace = MothershipManager.get().getExceptionStackTrace(rowId, getContainer());
                    if (exceptionStackTrace != null)
                    {
                        if (user != null)
                        {
                            exceptionStackTrace.setAssignedTo(user.getUserId());
                        }
                        else
                        {
                            exceptionStackTrace.setBugNumber(-1);
                        }
                    }
                    MothershipManager.get().updateExceptionStackTrace(exceptionStackTrace, getUser());
                }
                catch (NumberFormatException e)
                {
                    throw new NotFoundException("Could not find exception stack trace with id " + key);
                }
            }

            return true;
        }

        @Override
        public URLHelper getSuccessURL(BulkUpdateForm form)
        {
            return form.getReturnActionURL(new ActionURL(BeginAction.class, getContainer()));
        }
    }

    public static class ServerSessionDetailView extends DetailsView
    {
        public ServerSessionDetailView(final ServerSessionForm form)
        {
            super(new DataRegion(), form);
            getDataRegion().setTable(MothershipManager.get().getTableInfoServerSession());
            getDataRegion().addColumns(MothershipManager.get().getTableInfoServerSession(), "ServerSessionId,ServerSessionGUID,ServerInstallationId,EarliestKnownTime,LastKnownTime,DatabaseProductName,DatabaseProductVersion,DatabaseDriverName,DatabaseDriverVersion,RuntimeOS,JavaVersion,SoftwareReleaseId,UserCount,ActiveUserCount,ProjectCount,ContainerCount,AdministratorEmail,EnterprisePipelineEnabled,ServletContainer");
            final DisplayColumn defaultServerInstallationColumn = getDataRegion().getDisplayColumn("ServerInstallationId");
            defaultServerInstallationColumn.setVisible(false);
            DataColumn replacementServerInstallationColumn = new DataColumn(defaultServerInstallationColumn.getColumnInfo())
            {
                @Override @NotNull
                public String getFormattedValue(RenderContext ctx)
                {
                    Map<String, Object> row = ctx.getRow();

                    ColumnInfo displayColumn = defaultServerInstallationColumn.getColumnInfo().getDisplayField();

                    ServerInstallation si = MothershipManager.get().getServerInstallation(((Integer) row.get("ServerInstallationId")).intValue(), ctx.getContainer());
                    if (si != null && si.getNote() != null && si.getNote().trim().length() > 0)
                    {
                        return PageFlowUtil.filter(si.getNote());
                    }
                    else
                    {
                        Object displayValue = displayColumn.getValue(ctx);
                        if (displayValue == null || "".equals(displayValue))
                        {
                            if (si != null && si.getServerHostName() != null && si.getServerHostName().trim().length() > 0)
                            {
                                return PageFlowUtil.filter(si.getServerHostName());
                            }
                            else
                            {
                                return PageFlowUtil.filter("[Unnamed]");
                            }
                        }
                    }
                    return super.getFormattedValue(ctx);
                }
            };

            replacementServerInstallationColumn.setCaption("Server Installation");
            replacementServerInstallationColumn.setURLExpression(new DetailsURL(
                    new ActionURL(ShowInstallationDetailAction.class, getViewContext().getContainer()),
                    Collections.singletonMap("serverInstallationId", "ServerInstallationId")));
            getDataRegion().addDisplayColumn(3, replacementServerInstallationColumn);

            ButtonBar bb = new ButtonBar();
            getDataRegion().setButtonBar(bb);
            setTitle("Server Session Details");
        }
    }

    public static class ExceptionStackTraceUpdateView extends UpdateView
    {
        public ExceptionStackTraceUpdateView(ExceptionStackTraceForm form, ActionURL url, Container c, BindException errors)
        {
            super(new DataRegion(), form, errors);

            ButtonBar bb = new ButtonBar();
            bb.setStyle(ButtonBar.Style.separateButtons);
            ActionURL saveURL = new ActionURL(UpdateStackTraceAction.class, c);
            ActionButton b = new ActionButton(saveURL, "Save");
            b.setDisplayPermission(UpdatePermission.class);
            bb.add(b);

            getDataRegion().setButtonBar(bb);
            getDataRegion().setFormActionUrl(saveURL);

            TableInfo exceptionStackTraceTable = new MothershipSchema(getViewContext().getUser(), c).getTable("ExceptionStackTrace");
            getDataRegion().setTable(exceptionStackTraceTable);
            getDataRegion().addColumns(exceptionStackTraceTable, "ExceptionStackTraceId,StackTrace,BugNumber,Comments");
            getDataRegion().addHiddenFormField("exceptionStackTraceId", Integer.toString(form.getBean().getExceptionStackTraceId()));
            getDataRegion().addDisplayColumn(new AssignedToDisplayColumn(exceptionStackTraceTable.getColumn("AssignedTo"), c));
            getDataRegion().getDisplayColumn(1).setVisible(false);
            getDataRegion().addDisplayColumn(new CreateIssueDisplayColumn(exceptionStackTraceTable.getColumn("StackTrace"), b));
            getDataRegion().addDisplayColumn(new StackTraceDisplayColumn(exceptionStackTraceTable.getColumn("StackTrace")));

            getDataRegion().addColumn(exceptionStackTraceTable.getColumn("ModifiedBy"));
            getDataRegion().addColumn(exceptionStackTraceTable.getColumn("Modified"));

        }
    }

    public static class ServerInstallationUpdateView extends UpdateView
    {
        public ServerInstallationUpdateView(ServerInstallationForm form, BindException errors)
        {
            super(new DataRegion(), form, errors);

            TableInfo serverInstallationTable = new MothershipSchema(getViewContext().getUser(), getViewContext().getContainer()).getTable(MothershipSchema.SERVER_INSTALLATIONS_TABLE_NAME);
            getDataRegion().setTable(serverInstallationTable);

            Collection<FieldKey> requestedColumns = new ArrayList<>();

            requestedColumns.add(FieldKey.fromParts("ServerInstallationId"));
            requestedColumns.add(FieldKey.fromParts("ServerInstallationGUID"));
            requestedColumns.add(FieldKey.fromParts("Note"));
            requestedColumns.add(FieldKey.fromParts("OrganizationName"));
            requestedColumns.add(FieldKey.fromParts("ServerHostName"));
            requestedColumns.add(FieldKey.fromParts("ServerIP"));
            requestedColumns.add(FieldKey.fromParts("LogoLink"));
            requestedColumns.add(FieldKey.fromParts("SystemDescription"));
            requestedColumns.add(FieldKey.fromParts("SystemShortName"));

            requestedColumns.add(FieldKey.fromParts("MostRecentSession", "AdministratorEmail"));
            requestedColumns.add(FieldKey.fromParts("MostRecentSession", "UserCount"));
            requestedColumns.add(FieldKey.fromParts("MostRecentSession", "ActiveUserCount"));
            requestedColumns.add(FieldKey.fromParts("MostRecentSession", "ProjectCount"));
            requestedColumns.add(FieldKey.fromParts("MostRecentSession", "ContainerCount"));

            requestedColumns.add(FieldKey.fromParts("ExceptionCount"));
            requestedColumns.add(FieldKey.fromParts("VersionCount"));
            requestedColumns.add(FieldKey.fromParts("DaysActive"));
            requestedColumns.add(FieldKey.fromParts("LastPing"));
            requestedColumns.add(FieldKey.fromParts("FirstPing"));
            requestedColumns.add(FieldKey.fromParts("UsedInstaller"));

            requestedColumns.add(FieldKey.fromParts("MostRecentSession", "Distribution"));
            requestedColumns.add(FieldKey.fromParts("MostRecentSession", "UsageReportingLevel"));
            requestedColumns.add(FieldKey.fromParts("MostRecentSession", "ExceptionReportingLevel"));
            requestedColumns.add(FieldKey.fromParts("IgnoreExceptions"));

            Map<FieldKey, ColumnInfo> columns = QueryService.get().getColumns(serverInstallationTable, requestedColumns);

            for (ColumnInfo col : columns.values())
            {
                // The 5 columns from the lookup via MostRecentSession are all user editable by default, which is
                // incorrect for their usage on this page.
                if (!("Note".equalsIgnoreCase(col.getColumnName()) || "IgnoreExceptions".equalsIgnoreCase(col.getColumnName())))
                {
                    col.setUserEditable(false);
                }
            }

            getDataRegion().addColumns(new ArrayList<>(columns.values()));

            getDataRegion().addHiddenFormField("ServerInstallationId", form.getPkVal().toString());
            ButtonBar bb = new ButtonBar();
            bb.setStyle(ButtonBar.Style.separateButtons);
            ActionButton b = new ActionButton(new ActionURL(UpdateInstallationAction.class, getViewContext().getContainer()), "Save");
            b.setDisplayPermission(UpdatePermission.class);
            bb.add(b);
            getDataRegion().setButtonBar(bb);

            setTitle("Server Installation Details");
        }
    }

    public static class ExceptionStackTraceForm extends BeanViewForm<ExceptionStackTrace>
    {
        public ExceptionStackTraceForm()
        {
            super(ExceptionStackTrace.class, MothershipManager.get().getTableInfoExceptionStackTrace());
        }

        public ExceptionStackTraceForm(ExceptionStackTrace stackTrace)
        {
            this();
            setBean(stackTrace);
        }
    }

    public static class ServerInstallationForm extends BeanViewForm<ServerInstallation>
    {
        public ServerInstallationForm(ServerInstallation installation)
        {
            this();
            setBean(installation);
        }

        public ServerInstallationForm()
        {
            super(ServerInstallation.class, MothershipManager.get().getTableInfoServerInstallation());
        }
    }

    public static class ServerSessionForm extends BeanViewForm<ServerSession>
    {
        public ServerSessionForm(ServerSession session)
        {
            this();
            setBean(session);
        }

        public ServerSessionForm()
        {
            super(ServerSession.class, MothershipManager.get().getTableInfoServerSession());
        }
    }

    public static class CreateIssueFinishedForm
    {
        private int _exceptionStackTraceId;
        private int _issueId;
        private Integer _assignedTo;

        public int getExceptionStackTraceId()
        {
            return _exceptionStackTraceId;
        }

        public void setExceptionStackTraceId(int exceptionStackTraceId)
        {
            _exceptionStackTraceId = exceptionStackTraceId;
        }

        public int getIssueId()
        {
            return _issueId;
        }

        public void setIssueId(int issueId)
        {
            _issueId = issueId;
        }

        public Integer getAssignedTo()
        {
            return _assignedTo;
        }

        public void setAssignedTo(Integer assignedTo)
        {
            _assignedTo = assignedTo;
        }
    }

    public static class SoftwareReleaseForm extends BeanViewForm<SoftwareRelease>
    {
        public SoftwareReleaseForm(SoftwareRelease release)
        {
            this();
            setBean(release);
        }

        public SoftwareReleaseForm()
        {
            super(SoftwareRelease.class, MothershipManager.get().getTableInfoSoftwareRelease());
        }
    }


    public static class UpgradeMessageForm
    {
        private int _currentRevision;
        private String _message;
        private String _createIssueURL;
        private String _issuesContainer;

        public int getCurrentRevision()
        {
            return _currentRevision;
        }

        public void setCurrentRevision(int currentRevision)
        {
            _currentRevision = currentRevision;
        }

        public String getMessage()
        {
            return _message;
        }

        public void setMessage(String message)
        {
            _message = message;
        }

        public void setCreateIssueURL(String createIssueURL)
        {
            _createIssueURL = createIssueURL;
        }

        public String getCreateIssueURL()
        {
            return _createIssueURL;
        }

        public void setIssuesContainer(String issuesContainer)
        {
            _issuesContainer = issuesContainer;
        }

        public String getIssuesContainer()
        {
            return _issuesContainer;
        }
    }
}

