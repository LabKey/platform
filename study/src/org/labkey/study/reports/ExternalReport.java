/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnHeaderType;
import org.labkey.api.data.ResultsFactory;
import org.labkey.api.data.TSVGridWriter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.Readers;
import org.labkey.api.reader.TabLoader;
import org.labkey.api.reports.report.AbstractReport;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.util.GUID;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HtmlView;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.VBox;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.PrintWriters;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.reports.ReportsController;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.StudyQuerySchema;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * User: migra
 * Date: Mar 9, 2006
 * Time: 11:21:04 AM
 */
public class ExternalReport extends AbstractReport
{
    public static final String TYPE = "Study.externalReport";

    private RecomputeWhen recomputeWhen = RecomputeWhen.Always;
    public static final String REPORT_DIR = "reports_temp";
    public static final String DATA_FILE_SUBST = "${DATA_FILE}";
    public static final String REPORT_FILE_SUBST = "${REPORT_FILE}";
    private static final String DATA_FILE_SUFFIX = "Data.tsv";
    private static final MimeMap mimeMap = new MimeMap();

    @Override
    public void setDescriptor(ReportDescriptor descriptor)
    {
        super.setDescriptor(descriptor);

        // strange, but this report is only bound to study
        descriptor.setProperty(ReportDescriptor.Prop.schemaName, StudySchema.getInstance().getSchemaName());
    }

    @Override
    public String getType()
    {
        return TYPE;
    }

    @Override
    public String getTypeDescription()
    {
        return "Advanced Report";
    }

    public @Nullable String getProgram()
    {
        return getDescriptor().getProperty("program");
    }

    public void setProgram(String program)
    {
        getDescriptor().setProperty("program", program);
    }

    public @Nullable String getArguments()
    {
        return getDescriptor().getProperty("arguments");
    }

    public void setArguments(String arguments)
    {
        getDescriptor().setProperty("arguments", arguments);
    }

    public String getCommandLine()
    {
        return getDescriptor().getProperty("commandLine");
    }

    public void setCommandLine(String commandLine)
    {
        getDescriptor().setProperty("commandLine", commandLine);
    }

    public String getFileExtension()
    {
        return getDescriptor().getProperty("fileExtension");
    }

    public void setFileExtension(String fileExtension)
    {
        getDescriptor().setProperty("fileExtension", fileExtension);
    }

    public Integer getDatasetId()
    {
        return NumberUtils.toInt(getDescriptor().getProperty("datasetId"));
    }

    public void setDatasetId(Integer datasetId)
    {
        getDescriptor().setProperty("datasetId", String.valueOf(datasetId));
    }

    public int getVisitRowId()
    {
        return NumberUtils.toInt(getDescriptor().getProperty("visitRowId"));
    }

    public void setVisitId(int visitRowId)
    {
        getDescriptor().setProperty("visitRowId", String.valueOf(visitRowId));
    }

    public String getQueryName()
    {
        return getDescriptor().getProperty("queryName");
    }

    public void setQueryName(String queryName)
    {
        getDescriptor().setProperty("queryName", queryName);
    }

    public enum RecomputeWhen
    {
        Always,
        Hourly,
        Daily,
        Never
    }

/*
    @Override
    public boolean canHavePermissions()
    {
        return true;
    }

*/
    @Override
    public HttpView renderReport(ViewContext viewContext)
    {
        String ext = getFileExtension() == null ? "txt" : getFileExtension();
        if (ext.charAt(0) == '.')
            ext = ext.substring(1);

        if (null == StringUtils.trimToNull(getProgram()) || ((null == getDatasetId() || 0 == getDatasetId()) && null == getQueryName()))
            return new HtmlView("Program and datasetId must be provided");

        File resultFile = null;
        File outFile = null;
        File errFile = null;
        File dataFile = null;
        try
        {
            dataFile = File.createTempFile(getFilePrefix(), DATA_FILE_SUFFIX, getReportDir(viewContext));
            String dataFileName = dataFile.getName();

            ResultsFactory factory;
            if (null == getQueryName())
                factory = ()->ReportManager.get().getReportResultSet(viewContext, getDatasetId(), getVisitRowId());
            else
            {
                UserSchema schema = getStudyQuerySchema(viewContext.getUser(), ReadPermission.class, viewContext);
                TableInfo mainTable = schema.getTable(getQueryName());
                if (mainTable == null)
                    return new HtmlView("Unable to get TableInfo for query: " + getQueryName());
                factory = new TableSelector(mainTable);
            }

            // TSVGridWriter generates and closes the Results
            try (TSVGridWriter tsv = new TSVGridWriter(factory))
            {
                tsv.setColumnHeaderType(ColumnHeaderType.Name); // CONSIDER: Use FieldKey instead
                tsv.write(dataFile);
            }

            List<String> params = new LinkedList<>();
            params.add(getProgram());

            String arguments = getArguments();

            if (null != arguments)
                params.addAll(Arrays.asList(StringUtils.split(arguments)));

            for (int i = 0; i < params.size(); i++)
            {
                String param = params.get(i);
                if (DATA_FILE_SUBST.equalsIgnoreCase(param))
                    params.set(i, dataFile.getName());
                else if (REPORT_FILE_SUBST.equalsIgnoreCase(param))
                {
                    String resultFileName = dataFile.getName();
                    resultFileName = resultFileName.substring(0, resultFileName.length() - DATA_FILE_SUFFIX.length()) + "Result." + ext;
                    resultFile = new File(getReportDir(viewContext), resultFileName);
                    params.set(i, resultFileName);
                }
            }

            // Display stderr separate from stdout, #34902. If somebody cares (e.g., they want errors to be shown inline with
            // the output), we could add this as an option in the report designer. For now, always keep them separate.
            boolean displayErrorsSeparately = true;

            //outFile is stdout. If proc writes to stdout use file extension hint
            String outFileExt = resultFile == null ? ext : "out";
            outFile = new File(getReportDir(viewContext), dataFileName.substring(0, dataFileName.length() - DATA_FILE_SUFFIX.length()) + "." + outFileExt);

            errFile = displayErrorsSeparately ? new File(getReportDir(viewContext), dataFileName.substring(0, dataFileName.length() - DATA_FILE_SUFFIX.length()) + ".err") : null;

            ProcessBuilder pb = new ProcessBuilder(params);
            pb = pb.directory(getReportDir(viewContext));

            int resultCode = runProcess(pb, outFile, displayErrorsSeparately, errFile);
            if (resultCode != 0)
            {
                String err = "<font color='red'>Error " + resultCode + " executing command</font> " +
                        PageFlowUtil.filter(getProgram()) + "&nbsp;" + PageFlowUtil.filter(getArguments()) + "<br><pre>" +
                        PageFlowUtil.filter(PageFlowUtil.getFileContentsAsString(outFile)) + "</pre>";

                return new HtmlView(err);
            }
            else
            {
                File reportFile = null == resultFile ? outFile : resultFile;
                HttpView outputView = getOutputView(reportFile, ext);

                return displayErrorsSeparately ? new VBox(new InlineReportView(errFile), outputView) : outputView;
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException("Could not create file.", e);
        }
        finally
        {
            if (null != dataFile && dataFile.exists())
                dataFile.delete();
            //If for some reason file never gets rendered, mark for delete on exit.
            if (null != outFile && outFile.exists())
                outFile.deleteOnExit();
            if (null != errFile && errFile.exists())
                errFile.deleteOnExit();
            if (null != resultFile && resultFile.exists())
                resultFile.deleteOnExit();
        }
    }

    private HttpView getOutputView(File reportFile, String ext)
    {
        if (ext.equalsIgnoreCase("tsv"))
            return new TabReportView(reportFile);
        else if (mimeMap.getContentType(ext) != null && mimeMap.getContentType(ext).startsWith("image/"))
            return new ImgReportView(reportFile);
        else
            return new InlineReportView(reportFile);
    }

    protected StudyQuerySchema getStudyQuerySchema(User user, Class<? extends Permission> perm, ViewContext context)
    {
        if (perm != ReadPermission.class)
            throw new IllegalArgumentException("only ReadPermission supported");
        StudyImpl study = StudyManager.getInstance().getStudy(context.getContainer());
        return StudyQuerySchema.createSchema(study, user);
    }

    private String getFilePrefix()
    {
        if (null != getDescriptor().getReportName())
            return getDescriptor().getReportName();

        return "rpt";
    }

    @SuppressWarnings("ConstantConditions")
    private int runProcess(ProcessBuilder pb, File outFile, boolean displayErrorsSeparately, @Nullable File errFile)
    {
        Process proc;
        try
        {
            pb.redirectErrorStream(!displayErrorsSeparately);
            proc = pb.start();
        }
        catch (SecurityException e)
        {
            throw new RuntimeException(e);
        }
        catch (IOException eio)
        {
            Map<String, String> env = pb.environment();
            throw new RuntimeException("Failed starting process '" + pb.command() + "'. " +
                    "Must be on server path. (PATH=" + env.get("PATH") + ")", eio);
        }

        String suffix = " for process in '" + pb.directory().getPath() + "'.";
        outputToFile("output" + suffix, proc.getInputStream(), outFile);

        if (displayErrorsSeparately)
            outputToFile("errors" + suffix, proc.getErrorStream(), errFile);

        try
        {
            return proc.waitFor();
        }
        catch (InterruptedException ei)
        {
            throw new RuntimeException("Interrupted process for '" + pb.command() + " in " + pb.directory() + "'.", ei);
        }
    }


    private void outputToFile(String what, InputStream is, File outputFile)
    {
        try (PrintWriter writer = PrintWriters.getPrintWriter(outputFile); BufferedReader procReader = Readers.getReader(is))
        {
            String line;
            while ((line = procReader.readLine()) != null)
            {
                writer.write(line);
                writer.write("\n");
            }
        }
        catch (IOException eio)
        {
            throw new RuntimeException("Failed writing " + what, eio);
        }
    }


    private File getReportDir(ViewContext viewContext)
    {
        PipeRoot pipelineRoot = PipelineService.get().findPipelineRoot(viewContext.getContainer());
        if (null == pipelineRoot)
            throw new IllegalStateException("Pipeline root has not been set. Please ask an administrator to set one up for you");

        File reportDir = pipelineRoot.resolvePath(REPORT_DIR);
        if (!reportDir.exists())
            reportDir.mkdirs();

        return reportDir;
    }

    public class InlineReportView extends HttpView
    {
        private final File _file;

        private InlineReportView(File file)
        {
            _file = file;
        }

        @Override
        protected void renderInternal(Object model, PrintWriter out)
        {
            out.write("<pre>");
            out.write(PageFlowUtil.filter(PageFlowUtil.getFileContentsAsString(_file)));
            out.write("</pre>");

            if (recomputeWhen == RecomputeWhen.Always)
                _file.delete();
        }
    }

    public class TabReportView extends HttpView
    {
        private final File _file;

        private TabReportView(File file)
        {
            _file = file;
        }

        @Override
        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            TabLoader tabLoader = new TabLoader(_file);
            ColumnDescriptor[] cols = tabLoader.getColumns();
            List<Map<String, Object>> data = tabLoader.load();
            out.write("<table><tr>");
            for (ColumnDescriptor col : cols)
            {
                out.write("<td class='labkey-header'>");
                out.write(PageFlowUtil.filter(col.name));
                out.write("</td>");
            }
            out.write("</tr>");
            for (Map<String, Object> m : data)
            {
                out.write("<tr>");
                for (ColumnDescriptor col : cols)
                {
                    out.write("<td");
                    if (Number.class.isAssignableFrom(col.clazz))
                        out.write(" align='right'");
                    out.write(">");
                    Object colVal = m.get(col.name);
                    if (null != colVal)
                        out.write(PageFlowUtil.filter(ConvertUtils.convert(colVal)));
                    else
                        out.write("&nbsp;");
                    out.write("</td>");
                }
                out.write("</tr>");
            }
            out.write("</table>");

            if (recomputeWhen == RecomputeWhen.Always)
                _file.delete();
        }
    }

    public class ImgReportView extends HttpView
    {
        File file;

        ImgReportView(File file)
        {
            this.file = file;
        }

        @Override
        protected void renderInternal(Object model, PrintWriter out)
        {
            String key = "temp:" + GUID.makeGUID();
            getViewContext().getRequest().getSession(true).setAttribute(key, file);

//            out.write("<img src=\"");
//            out.write(getViewContext().getActionURL().relativeUrl("streamFile", PageFlowUtil.map("sessionKey", key), "Study-Reports", true));
//            out.write("\">");
//
            out.write("<img src=\"");
            out.write(PageFlowUtil.filter(new ActionURL(ReportsController.StreamFileAction.class, getViewContext().getContainer()).addParameter("sessionKey", key)));
            out.write("\">");
        }
    }

    @Override
    public boolean isSandboxed()
    {
        return false;
    }
}

