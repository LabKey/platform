/*
 * Copyright (c) 2006-2012 LabKey Corporation
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

package org.labkey.study.reports;

import org.apache.commons.lang3.math.NumberUtils;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.time.Week;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ChartReport;
import org.labkey.api.reports.report.ChartReportDescriptor;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.User;
import org.labkey.api.study.DataSet;
import org.labkey.api.study.Study;
import org.labkey.api.view.*;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.reports.ReportsController;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;

import javax.imageio.ImageIO;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintWriter;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;

/**
 * User: Matthew
 * Date: Feb 22, 2006
 * Time: 1:11:18 PM
 */
public class EnrollmentReport extends ChartReport implements Report.ImageReport
{
    public static final String TYPE = "Study.enrollmentReport";

    public String getType()
    {
        return TYPE;
    }

    public void setDescriptor(ReportDescriptor descriptor)
    {
        super.setDescriptor(descriptor);
        getDescriptor().setProperty(ReportDescriptor.Prop.reportName, "Enrollment");
    }

    @Override
    public String getTypeDescription()
    {
        return "enrollment view";
    }

    @Override
    public ActionURL getRunReportURL(ViewContext context)
    {
        return new ActionURL(ReportsController.EnrollmentReportAction.class, context.getContainer());
    }

    @Override
    public void renderImage(ViewContext viewContext) throws Exception
    {
        String errorMessage = null;
        ReportDescriptor reportDescriptor = getDescriptor();
        if (reportDescriptor instanceof ChartReportDescriptor)
        {
            try
            {
                ChartReportDescriptor descriptor = (ChartReportDescriptor)reportDescriptor;
                HttpServletResponse response = viewContext.getResponse();

                final Study study = StudyManager.getInstance().getStudy(viewContext.getContainer());
                int datasetId = NumberUtils.createInteger(descriptor.getProperty(DataSetDefinition.DATASETKEY));
                double sequenceNum = VisitImpl.parseSequenceNum(descriptor.getProperty(VisitImpl.SEQUENCEKEY));

                if (ReportManager.get().canReadReport(viewContext.getUser(), viewContext.getContainer(), this))
                {
                    ResultSet rs = getVisitDateResultSet(study, datasetId, sequenceNum, viewContext.getUser());
                    try
                    {
                        int indexX = 1;

                        //
                        // Chart
                        //
                        ArrayList<Date> dates = new ArrayList<>();
                        Date tomorrow = new Date(System.currentTimeMillis() + CacheManager.DAY);
                        while (rs.next())
                        {
                            Timestamp t = rs.getTimestamp(indexX);
                            if (t == null)
                                dates.add(tomorrow);
                            else
                                dates.add(t);
                        }
                        Collections.sort(dates);
                        Week lastWeek = dates.isEmpty() ? new Week(new Date()) : new Week(dates.get(dates.size()-1));
                        Week firstWeek = (Week)(dates.isEmpty() ? lastWeek : (new Week(dates.get(0))).previous());
                        // add a sentinal date (after last)
                        dates.add((lastWeek.next()).getStart());

                        TimeSeries seriesTotal = new TimeSeries("Enrollment", Week.class);
                        TimeSeries seriesPeriod = new TimeSeries("Weekly", Week.class);
                        double runningCount = 0;
                        double periodCount = 0;
                        Week curr = firstWeek;
                        for (Date d : dates)
                        {
                            Week w = new Week(d);
                            for ( ; curr.compareTo(w) < 0 ; curr = (Week)curr.next())
                            {
                                seriesTotal.add(curr, runningCount);
                                seriesPeriod.add(curr, periodCount);
                                periodCount = 0;
                            }
                            ++runningCount;
                            ++periodCount;
                        }

                        TimeSeriesCollection col = new TimeSeriesCollection();
                        col.addSeries(seriesTotal);
                        col.addSeries(seriesPeriod);

                        DataSet ds = study.getDataSet(datasetId);
                        byte[] bytes = generateTimeChart("Dataset: " + ds.getLabel(), col, "Visit Date", "", null);
                        response.setContentType("image/png");
                        response.setContentLength(bytes.length);
                        response.getOutputStream().write(bytes);
                    }
                    finally
                    {
                        rs.close();
                    }
                }
                else
                    errorMessage = "No permission to view this chart";
            }
            catch (Exception e)
            {
                errorMessage = e.getMessage();
            }
        }
        else
        {
            errorMessage = "Invalid report params: The ReportDescriptor must be an instance of ChartReportDescriptor";
        }

        if (errorMessage != null)
        {
            ReportUtil.renderErrorImage(viewContext.getResponse().getOutputStream(), this, errorMessage);
            //return new VBox(ExceptionUtil.getErrorView(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, errorMessage, null, viewContext.getRequest(), false));
        }
    }

    public HttpView renderReport(ViewContext context) throws Exception
    {
        ActionURL url = ReportUtil.getPlotChartURL(context, this);
        return new HtmlView("<img src='" + url.getLocalURIString() + "'>");
    }

    public static byte[] generateTimeChart(String label, TimeSeriesCollection collection, String timeLabel, String valueLabel, Dimension size)
            throws IOException
    {
        JFreeChart chart = ChartFactory.createTimeSeriesChart
                (
                label,
                timeLabel, valueLabel,
                collection,
                false, false, false
                );
        if (null == size)
            size = new Dimension(640,640);
        BufferedImage img = chart.createBufferedImage(size.width, size.height);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }

    private static ResultSet getVisitDateResultSet(Study study, int datasetId, double sequenceNum, User user) throws ServletException, SQLException
    {
        DataSetDefinition def = StudyManager.getInstance().getDataSetDefinition(study, datasetId);
        TableInfo ti = def.getTableInfo(user);
        SQLFragment sql = new SQLFragment();
        sql.append(
                "SELECT PV.VisitDate\n" +
                "FROM study.ParticipantVisit PV JOIN ").append(ti.getFromSQL("SD")).append(" ON PV.ParticipantId=SD.ParticipantID AND PV.SequenceNum=SD.SequenceNum\n" +
                "WHERE PV.Container=? AND PV.SequenceNum=? AND SD.SequenceNum=?");
        sql.add(study.getContainer());
        sql.add(sequenceNum);
        sql.add(sequenceNum);
        ResultSet rs = Table.executeQuery(StudySchema.getInstance().getSchema(), sql);
        return rs;
    }

    public static class EnrollmentView extends WebPartView
    {
        private Report _report;

        public EnrollmentView(Report report)
        {
            super("Enrollment Report");
            _report = report;
            setFrame(FrameType.NONE);
        }

        @Override
        protected void renderView(Object model, PrintWriter out) throws Exception
        {
            if (_report != null)
            {
                ActionURL configure = new ActionURL(ReportsController.ConfigureEnrollmentReportAction.class, getViewContext().getContainer());

                final ReportDescriptor descriptor = _report.getDescriptor();

                ActionURL url = ReportUtil.getPlotChartURL(getViewContext(), _report);
                out.println("&nbsp;<br><img src=\"" + url.getLocalURIString() + "\"><br>");

                final Study study = StudyManager.getInstance().getStudy(getViewContext().getContainer());
                int datasetId = NumberUtils.toInt(descriptor.getProperty(DataSetDefinition.DATASETKEY));
                if (descriptor.getProperty(VisitImpl.SEQUENCEKEY) != null)
                {
                    double sequenceNum = VisitImpl.parseSequenceNum(descriptor.getProperty(VisitImpl.SEQUENCEKEY));
                    ResultSet rs = getVisitDateResultSet(study, datasetId, sequenceNum, getViewContext().getUser());

                    try
                    {
                        int indexX = 1;
                        int countAll = 0;
                        int countNull = 0;

                        while (rs.next())
                        {
                            countAll++;
                            Timestamp t = rs.getTimestamp(indexX);
                            if (t == null)
                                countNull++;
                        }

                        if (countNull != 0)
                        {
                            out.println("<br><font class=labkey-error>" + countNull + " participants (out of " + countAll + ") do not have VisitDate properly recorded.</font>");
                        }
                    }
                    finally
                    {
                        rs.close();
                    }
                }
                out.println("<br>[&nbsp;<a href=\"" + configure + "\">configure</a>&nbsp;]");
            }
            else
                out.println("Unable to create an Enrollment report.");
        }
    }

    public static Report getEnrollmentReport(User user, Study study, boolean create) throws Exception
    {
        if (study != null)
        {
            Report[] reports = ReportService.get().getReports(user, study.getContainer(), "enrollmentReport");
            if (reports.length > 0)
            {
                assert (reports.length == 1);
                return reports[0];
            }
            if (create)
                return ReportService.get().createReportInstance(EnrollmentReport.TYPE);
        }
        return null;
    }

    public static void saveEnrollmentReport(ViewContext context, Report report) throws SQLException
    {
        ReportService.get().saveReport(context, "enrollmentReport", report);
    }
}
