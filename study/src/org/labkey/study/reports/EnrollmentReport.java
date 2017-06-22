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

package org.labkey.study.reports;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.mutable.MutableInt;
import org.jfree.chart.ChartFactory;
import org.jfree.chart.JFreeChart;
import org.jfree.data.time.TimeSeries;
import org.jfree.data.time.TimeSeriesCollection;
import org.jfree.data.time.Week;
import org.labkey.api.cache.CacheManager;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Selector;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.ChartReport;
import org.labkey.api.reports.report.ChartReportDescriptor;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.User;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.reports.ReportsController;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyManager;
import org.labkey.study.model.VisitImpl;

import javax.imageio.ImageIO;
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
import java.util.Collection;
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
    public void renderImage(ViewContext viewContext) throws IOException
    {
        String errorMessage = null;
        ReportDescriptor reportDescriptor = getDescriptor();
        HttpServletResponse response = viewContext.getResponse();

        renderBlock:
        {
            if (!(reportDescriptor instanceof ChartReportDescriptor))
            {
                errorMessage = "Invalid report params: The ReportDescriptor must be an instance of ChartReportDescriptor";
                break renderBlock;
            }
            ChartReportDescriptor descriptor = (ChartReportDescriptor) reportDescriptor;

            if (null == descriptor.getProperty(DatasetDefinition.DATASETKEY) ||
                    null == descriptor.getProperty(VisitImpl.SEQUENCEKEY))
            {
                errorMessage = "Invalid report params: " + DatasetDefinition.DATASETKEY + " and " + VisitImpl.SEQUENCEKEY + " must be specified.";
                break renderBlock;
            }

            final Study study = StudyManager.getInstance().getStudy(viewContext.getContainer());
            int datasetId = NumberUtils.createInteger(descriptor.getProperty(DatasetDefinition.DATASETKEY));
            double sequenceNum = VisitImpl.parseSequenceNum(descriptor.getProperty(VisitImpl.SEQUENCEKEY));

            if (!ReportManager.get().canReadReport(viewContext.getUser(), viewContext.getContainer(), this))
            {
                errorMessage = "No permission to view this chart";
                break renderBlock;
            }

            final ArrayList<Date> dates = new ArrayList<>();
            final int indexX = 1;
            final Date tomorrow = new Date(System.currentTimeMillis() + CacheManager.DAY);

            getVisitDateSelector(study, datasetId, sequenceNum, viewContext.getUser()).forEach(new Selector.ForEachBlock<ResultSet>()
            {
                @Override
                public void exec(ResultSet rs) throws SQLException
                {
                    Timestamp t = rs.getTimestamp(indexX);
                    if (t == null)
                        dates.add(tomorrow);
                    else
                        dates.add(t);
                }
            });

            Collections.sort(dates);
            Week lastWeek = dates.isEmpty() ? new Week(new Date()) : new Week(dates.get(dates.size() - 1));
            Week firstWeek = (Week) (dates.isEmpty() ? lastWeek : (new Week(dates.get(0))).previous());
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
                for (; curr.compareTo(w) < 0; curr = (Week) curr.next())
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

            Dataset ds = study.getDataset(datasetId);
            byte[] bytes = generateTimeChart("Dataset: " + ds.getLabel(), col, "Visit Date", "", null);
            response.setContentType("image/png");
            response.setContentLength(bytes.length);
            response.getOutputStream().write(bytes);
        }

        if (errorMessage != null)
        {
            ReportUtil.renderErrorImage(viewContext.getResponse().getOutputStream(), this, errorMessage);
            //return new VBox(ExceptionUtil.getErrorView(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, errorMessage, null, viewContext.getRequest(), false));
        }
    }

    public HttpView renderReport(ViewContext context)
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

    private static Selector getVisitDateSelector(Study study, int datasetId, double sequenceNum, User user)
    {
        DatasetDefinition def = StudyManager.getInstance().getDatasetDefinition(study, datasetId);
        TableInfo ti = def.getTableInfo(user);
        SQLFragment sql = new SQLFragment();
        sql.append(
                "SELECT PV.VisitDate\n" +
                "FROM study.ParticipantVisit PV JOIN ").append(ti.getFromSQL("SD")).append(" ON PV.ParticipantId=SD.ParticipantID AND PV.SequenceNum=SD.SequenceNum\n" +
                "WHERE PV.Container=? AND PV.SequenceNum=? AND SD.SequenceNum=?");
        sql.add(study.getContainer());
        sql.add(sequenceNum);
        sql.add(sequenceNum);

        return new SqlSelector(StudySchema.getInstance().getSchema(), sql);
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
        protected void renderView(Object model, PrintWriter out)
        {
            if (_report != null)
            {
                ActionURL configure = new ActionURL(ReportsController.ConfigureEnrollmentReportAction.class, getViewContext().getContainer());

                final ReportDescriptor descriptor = _report.getDescriptor();

                ActionURL url = ReportUtil.getPlotChartURL(getViewContext(), _report);
                out.println("&nbsp;<br><img src=\"" + url.getLocalURIString() + "\"><br>");

                final Study study = StudyManager.getInstance().getStudy(getViewContext().getContainer());
                int datasetId = NumberUtils.toInt(descriptor.getProperty(DatasetDefinition.DATASETKEY));

                if (descriptor.getProperty(VisitImpl.SEQUENCEKEY) != null)
                {
                    double sequenceNum = VisitImpl.parseSequenceNum(descriptor.getProperty(VisitImpl.SEQUENCEKEY));
                    final int indexX = 1;
                    final MutableInt countAll = new MutableInt(0);
                    final MutableInt countNull = new MutableInt(0);

                    getVisitDateSelector(study, datasetId, sequenceNum, getViewContext().getUser()).forEach(new Selector.ForEachBlock<ResultSet>()
                    {
                        @Override
                        public void exec(ResultSet rs) throws SQLException
                        {
                            countAll.increment();
                            Timestamp t = rs.getTimestamp(indexX);
                            if (t == null)
                                countNull.increment();
                        }
                    });

                    if (countNull.intValue() != 0)
                    {
                        out.println("<br><font class=labkey-error>" + countNull.intValue() + " participants (out of " + countAll.intValue() + ") do not have VisitDate properly recorded.</font>");
                    }
                }
                out.println("<br>[&nbsp;<a href=\"" + configure + "\">configure</a>&nbsp;]");
            }
            else
                out.println("Unable to create an Enrollment report.");
        }
    }

    public static Report getEnrollmentReport(User user, Study study, boolean create)
    {
        if (study != null)
        {
            Collection<Report> reports = ReportService.get().getReports(user, study.getContainer(), "enrollmentReport");
            if (!reports.isEmpty())
            {
                assert (reports.size() == 1);
                return reports.iterator().next();
            }
            if (create)
                return ReportService.get().createReportInstance(EnrollmentReport.TYPE);
        }
        return null;
    }

    public static void saveEnrollmentReport(ViewContext context, Report report)
    {
        ReportService.get().saveReport(context, "enrollmentReport", report);
    }
}
