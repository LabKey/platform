package org.labkey.api.reports.report;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang.BooleanUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.struts.upload.FormFile;
import org.labkey.api.attachments.*;
import org.labkey.api.data.*;
import org.labkey.api.pipeline.PipeRoot;
import org.labkey.api.pipeline.PipelineService;
import org.labkey.api.query.DefaultSchema;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QuerySettings;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.report.view.ReportQueryView;
import org.labkey.api.reports.report.view.ChartUtil;
import org.labkey.api.reports.report.view.RunRReportView;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.GUID;
import org.labkey.api.util.MimeMap;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.*;
import org.labkey.common.tools.TabLoader;
import org.labkey.common.util.Pair;

import java.io.*;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RReport extends AbstractReport implements AttachmentParent, Report.ResultSetGenerator
{
    public static final String TYPE = "ReportService.rReport";
    //private static Logger _log = Logger.getLogger(RReport.class);
    public static Pattern scriptPattern = Pattern.compile("\\$\\{(.*?)\\}");
    private File _tempFolder;
    private boolean _tempFolderPipeline;

    public static final String FILE_PREFIX = "rpt";
    public static final String REPORT_DIR = "Reports";
    public static final String SUBSTITUTION_MAP = "substitutionMap.txt";

    public static final String OUTPUT_FILE_TXT = "txtout:";
    public static final String OUTPUT_FILE_TSV = "tsvout:";
    public static final String OUTPUT_FILE_IMG = "imgout:";
    public static final String OUTPUT_FILE_PDF = "pdfout:";
    public static final String OUTPUT_FILE_POSTSCRIPT = "psout:";
    public static final String OUTPUT_FILE_FILE = "fileout:";
    public static final String OUTPUT_FILE_HTML = "htmlout:";
    public static final String INPUT_FILE_TSV = "input_data";

    public static final String DATA_FILE_SUFFIX = "Data.tsv";

    public static final String DEFAULT_R_CMD = "CMD BATCH --slave";

    private static final String R_EXE = "RReport.RExe";
    private static final String R_CMD = "RReport.RCmd";
    private static final String R_TEMP_FOLDER = "RReport.TempFolder";
    private static final String R_EDIT_PERMISSIONS = "RReport.EditPermissions";
    private static final String R_SCRIPT_HANDLER = "RReport.ScriptHandler";

    public enum reportFile {
        script,
        inputData,
        console,
        output,
        substitutionMap,
        includedScript,
    }

    private static String getProp(String prop, String defaultValue)
    {
        Map<String, String> map = UserManager.getUserPreferences(false);
        if (map != null)
        {
            String ret = map.get(prop);
            if (!StringUtils.isEmpty(ret))
                return ret;
        }
        return defaultValue;
    }

    private static void setProp(String prop, String value)
    {
        Map<String, String> map = UserManager.getUserPreferences(true);
        map.put(prop, value);
        PropertyManager.saveProperties(map);
    }

    public static String getRExe() {return getProp(R_EXE, null);}
    public static void setRExe(String programPath) {setProp(R_EXE, programPath);}
    public static String getRCmd() {return getProp(R_CMD, DEFAULT_R_CMD);}
    public static void setRCmd(String command) {setProp(R_CMD, command);}
    public static String getTempFolder() {return getProp(R_TEMP_FOLDER, null);}
    public static void setTempFolder(String folder) {setProp(R_TEMP_FOLDER, folder);}
    public static int getEditPermissions() {return NumberUtils.toInt(getProp(R_EDIT_PERMISSIONS, Integer.toString(org.labkey.api.security.SecurityManager.PermissionSet.ADMIN.getPermissions()))); }
    public static void setEditPermissions(int permissions) {setProp(R_EDIT_PERMISSIONS, Integer.toString(permissions));}
    public static String getRScriptHandler(){return getProp(R_SCRIPT_HANDLER, DefaultScriptRunner.ID);}
    public static void setRScriptHandler(String scriptHandler){setProp(R_SCRIPT_HANDLER, scriptHandler);}

    public static boolean isValidConfiguration()
    {
        return validateConfiguration(getRExe(), getRCmd(), getTempFolder(), getRScriptHandler()) == null;
    }

    public static String validateConfiguration(String programPath, String command, String tempFolder, String scriptHandler)
    {
        if (StringUtils.isEmpty(programPath))
            return "The R program location cannot be empty";

        File rexe = new File(programPath);
        if (!rexe.exists())
            return "The R program location: '" + programPath + "' does not exist";

        if (StringUtils.isEmpty(command))
            return "The R command cannot be empty";

        if (!StringUtils.isEmpty(tempFolder))
        {
            File temp = new File(tempFolder);
            if (!temp.exists())
                return "The temp folder: '" + tempFolder + "' does not exist";
        }

        if (RServeScriptRunner.ID.equals(scriptHandler))
        {
            File rserve = RServeScriptRunner.findRServeExe();
            if (rserve == null || !rserve.exists())
                return "Unable to locate RServe, please verify that RServe is installed correctly";
        }
        return null;
    }

    public static RScriptRunner createScriptRunner(RReport report, ViewContext context)
    {
        final String id = getRScriptHandler();
        if (DefaultScriptRunner.ID.equals(id))
            return new DefaultScriptRunner(report, context);
        else if (RServeScriptRunner.ID.equals(id))
            return new RServeScriptRunner(report, context);
        return null;
    }

    public String getType()
    {
        return TYPE;
    }

    public String getTypeDescription()
    {
        return "R View";
    }

    public String getDescriptorType()
    {
        return RReportDescriptor.TYPE;
    }

    public HttpView renderReport(ViewContext viewContext) throws Exception
    {
        VBox view = new VBox();
        String script = getDescriptor().getProperty(RReportDescriptor.Prop.script);

        if (validateConfiguration(getRExe(), getRCmd(), getTempFolder(), getRScriptHandler()) != null)
        {
            final String error = "The R program has not been configured to be used by the LabKey server yet, navigate to the <a href='" + ActionURL.toPathString("admin", "showAdmin.view", "") + "'>admin console</a> to configure R.";
            view.addView(new HtmlView("<span class=\"labkey-error\">" + error + "</span>"));
            return view;
        }

        List<String> errors = new ArrayList<String>();
        if (!validateScript(script, errors))
        {
            for (String error : errors)
                view.addView(new HtmlView("<span class=\"labkey-error\">" + error + "</span>"));
            return view;
        }
        RScriptRunner runner = createScriptRunner(this, viewContext);
        List<Pair<String, String>> outputSubst = new ArrayList<Pair<String, String>>();
        runner.runScript(view, outputSubst);
        renderViews(this, view, outputSubst, false);

        return view;
    }


    public void beforeDelete(ViewContext context)
    {
        try {
            // clean up any temp files
            deleteReportDir();
            AttachmentService.get().deleteAttachments(this);
            super.beforeDelete(context);
        }
        catch (SQLException se)
        {
            throw new RuntimeException(se);
        }
    }

    public static void renderViews(RReport report, VBox view, List<Pair<String, String>> outputs, boolean deleteTempFiles)
    {
        for (Pair<String, String> info : outputs)
        {
            File data = new File(info.getKey());
            if (data.exists())
            {
                if (TextOutputView._type.equals(info.getValue()))
                    view.addView(new TextOutputView(data, deleteTempFiles));
                else if (TabReportView._type.equals(info.getValue()))
                    view.addView(new TabReportView(data, deleteTempFiles));
                else if (ImgReportView._type.equals(info.getValue()))
                    view.addView(new ImgReportView(data, true));
                else if (PdfReportView._type.equals(info.getValue()))
                    view.addView(new PdfReportView(report, data, deleteTempFiles));
                else if (ConsoleOutputView._type.equals(info.getValue()))
                {
                    ConsoleOutputView console = new ConsoleOutputView(data, deleteTempFiles);
                    if (HttpView.currentContext().get("reportWebPart") != null)
                        console.setCollapse(true);
                    view.addView(console);
                }
                else if (FileoutReportView._type.equals(info.getValue()))
                    view.addView(new FileoutReportView(report, data, deleteTempFiles));
                else if (PostscriptReportView._type.equals(info.getValue()))
                    view.addView(new PostscriptReportView(report, data, deleteTempFiles));
                else if (HtmlReportView._type.equals(info.getValue()))
                    view.addView(new HtmlReportView(data, deleteTempFiles));
            }
        }
        if (!BooleanUtils.toBoolean(report.getDescriptor().getProperty(RReportDescriptor.Prop.runInBackground)))
            view.addView(new TempFileCleanup(report.getReportDir().getAbsolutePath()));
    }

    public HttpView renderDataView(ViewContext context) throws Exception
    {
        return createQueryView(context, getDescriptor());
    }

    public HttpView getRunReportView(ViewContext context) throws Exception
    {
        return new RunRReportView(this);
    }

    protected ReportQueryView createQueryView(ViewContext context, ReportDescriptor descriptor) throws Exception
    {
        final String schemaName = descriptor.getProperty(QueryParam.schemaName.toString());
        final String queryName = descriptor.getProperty(QueryParam.queryName.toString());
        final String viewName = descriptor.getProperty(QueryParam.viewName.toString());
        final String dataRegionName = descriptor.getProperty(QueryParam.dataRegionName.toString());

        if (context != null && schemaName != null)
        {
            UserSchema base = (UserSchema) DefaultSchema.get(context.getUser(), context.getContainer()).getSchema(schemaName);
            QuerySettings settings = base.getSettings(context.getActionURL(), dataRegionName);
            settings.setSchemaName(schemaName);
            settings.setQueryName(queryName);
            settings.setViewName(viewName);

            UserSchema schema = base.createView(context, settings).getSchema();
            return new ReportQueryView(schema, settings);
        }
        return null;
    }

    private boolean validateScript(String text, List<String> errors)
    {
        if (StringUtils.isEmpty(text))
        {
            errors.add("An R script must be provided.");
            return false;
        }

        Matcher m = scriptPattern.matcher(text);
        while (m.find())
        {
            String value = m.group(1);
            if (!isValidReplacement(value))
            {
                errors.add("Invalid template, the replacement parameter: " + value + " is unknown.");
                return false;
            }

        }
        return true;
    }

    private boolean isValidReplacement(String value)
    {
        if (INPUT_FILE_TSV.equals(value)) return true;
        if (value.startsWith(OUTPUT_FILE_TXT)) return true;
        if (value.startsWith(OUTPUT_FILE_TSV)) return true;
        if (value.startsWith(OUTPUT_FILE_IMG)) return true;
        if (value.startsWith(OUTPUT_FILE_PDF)) return true;
        if (value.startsWith(OUTPUT_FILE_FILE)) return true;
        if (value.startsWith(OUTPUT_FILE_POSTSCRIPT)) return true;
        if (value.startsWith(OUTPUT_FILE_HTML)) return true;

        return false;
    }

    public static File getFile(RReport report, RReport.reportFile type, String name)
    {
        switch (type)
        {
            case script:
                return new File(report.getReportDir(), "script.R");
            case inputData:
                return new File(report.getReportDir(), RReport.FILE_PREFIX  + RReport.DATA_FILE_SUFFIX );
            case console:
                return new File(report.getReportDir(), "script.Rout");
            case output:
                return new File(report.getReportDir(), "output.out");
            case substitutionMap:
                return new File(report.getReportDir(), RReport.SUBSTITUTION_MAP);
            case includedScript:
                return new File(report.getReportDir(), name);
        }
        return null;
    }

    /*
     * Create the .tsv associated with the data grid for this report.
     */
    public File createInputDataFile(ViewContext context) throws Exception
    {
        return DefaultScriptRunner.createInputDataFile(this, context);
    }

    public File getReportDir()
    {
        boolean isPipeline = BooleanUtils.toBoolean(getDescriptor().getProperty(RReportDescriptor.Prop.runInBackground));
        if (_tempFolder == null || _tempFolderPipeline != isPipeline)
        {
            File tempRoot;
            String tempFolderName = getTempFolder();
            if (StringUtils.isEmpty(tempFolderName))
            {
                try
                {
                    if (isPipeline && getDescriptor().getContainerId() != null)
                    {
                        Container c = ContainerManager.getForId(getDescriptor().getContainerId());
                        PipeRoot root = PipelineService.get().findPipelineRoot(c);
                        tempRoot = root.resolvePath(REPORT_DIR);
                        if (!tempRoot.exists())
                            tempRoot.mkdirs();
                    }
                    else
                    {
                        File file = File.createTempFile("RReport", "tmp");
                        tempRoot = new File(file.getParentFile(), REPORT_DIR);
                        if (!tempRoot.exists())
                            tempRoot.mkdirs();
                        file.delete();
                    }
                }
                catch (Exception e)
                {
                    throw new RuntimeException("Error setting up temp directory", e);
                }
            }
            else
            {
                tempRoot = new File(tempFolderName);
            }

            if (isPipeline)
                _tempFolder = new File(tempRoot, "Report_" + getDescriptor().getReportId());
            else
            {
                _tempFolder = new File(tempRoot.getAbsolutePath() + File.separator + "Report_" + getDescriptor().getReportId(), String.valueOf(Thread.currentThread().getId()));
            }
            _tempFolderPipeline = isPipeline;
            if (!_tempFolder.exists())
                _tempFolder.mkdirs();
        }
        return _tempFolder;
    }

    public void deleteReportDir()
    {
        boolean isPipeline = BooleanUtils.toBoolean(getDescriptor().getProperty(RReportDescriptor.Prop.runInBackground));
        try {
            File dir = getReportDir();
            if (!isPipeline)
                dir = dir.getParentFile();

            FileUtil.deleteDir(dir);
        }
        finally
        {
            _tempFolder = null;
        }
    }

    public String getEntityId()
    {
        return getDescriptor().getEntityId();
    }

    public String getContainerId()
    {
        return getDescriptor().getContainerId();
    }

    public void setAttachments(Collection<Attachment> attachments)
    {
    }

    public ResultSet generateResultSet(ViewContext context) throws Exception
    {
        ReportDescriptor descriptor = getDescriptor();
        ReportQueryView view = createQueryView(context, descriptor);
        if (view != null)
        {
            DataView dataView = view.createDataView();
            DataRegion rgn = dataView.getDataRegion();
            rgn.setMaxRows(0);
            RenderContext ctx = dataView.getRenderContext();

            // temporary code until we add a more generic way to specify a filter or grouping on the chart
            final String filterParam = descriptor.getProperty(ReportDescriptor.Prop.filterParam);
            if (!StringUtils.isEmpty(filterParam))
            {
                final String filterValue = (String)context.get(filterParam);
                if (filterValue != null)
                {
                    SimpleFilter filter = new SimpleFilter();
                    filter.addCondition(filterParam, filterValue, CompareType.EQUAL);

                    ctx.setBaseFilter(filter);
                }
            }
            return rgn.getResultSet(ctx);
        }
        return null;
    }

    public ActionURL getRunReportURL(ViewContext context)
    {
        return ChartUtil.getRunReportURL(context, this);
    }

    public ActionURL getEditReportURL(ViewContext context)
    {
        if (getDescriptor().canEdit(context))
        {
            return ChartUtil.getRunReportURL(context, this).addParameter(TabStripView.TAB_PARAM, RunRReportView.TAB_SOURCE);
        }
        return null;
    }

    public abstract static class ROutputView extends HttpView
    {
        private String _label;
        private boolean _collapse;

        public String getLabel()
        {
            return _label;
        }

        public void setLabel(String label)
        {
            _label = label;
        }

        public boolean isCollapse()
        {
            return _collapse;
        }

        public void setCollapse(boolean collapse)
        {
            _collapse = collapse;
        }

        protected void renderTitle(Object model, PrintWriter out) throws Exception
        {
            StringBuffer sb = new StringBuffer();

            sb.append("<tr class=\"wpHeader\"><th colspan=2 align=left>");
            sb.append("   <a href=\"#\" onclick=\"return toggleLink(this, false);\">");
            sb.append("   <img border=\"0\" src=\"");
            sb.append(getViewContext().getContextPath());
            sb.append("/_images/");
            sb.append(_collapse ? "plus.gif" : "minus.gif");
            sb.append("\"></a>&nbsp;");
            sb.append(PageFlowUtil.filter(_label));
            sb.append("</th></tr>");

            out.write(sb.toString());
        }
    }

    public static class TextOutputView extends ROutputView
    {
        public static final String _type = "Text";
        protected File _file;
        protected boolean _deleteTempFiles;

        public TextOutputView(File file, boolean deleteTempFiles)
        {
            _file = file;
            _deleteTempFiles = deleteTempFiles;
            setLabel("Text output");
        }

        @Override
        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            if (_file != null && _file.exists() && (_file.length() > 0))
            {
                out.write("<table width=\"100%\" cellspacing=\"0\" cellpadding=\"1\">");
                renderTitle(model, out);
                if (isCollapse())
                    out.write("<tr style=\"display:none\"><td><pre>");
                else
                    out.write("<tr><td><pre>");
                out.write(PageFlowUtil.filter(PageFlowUtil.getFileContentsAsString(_file), false, true));
                out.write("</pre></td></tr>");
                out.write("</table>");
            }
            if (_deleteTempFiles && null != _file)
                _file.delete();
        }
    }

    public static class ConsoleOutputView extends TextOutputView
    {
        public static final String _type = "Console";

        public ConsoleOutputView(File file, boolean deleteTempFiles)
        {
            super(file, deleteTempFiles);
            setLabel("Console output");
        }
    }

    public static class HtmlReportView extends TextOutputView
    {
        public static final String _type = "Html";

        public HtmlReportView(File file, boolean deleteTempFiles)
        {
            super(file, deleteTempFiles);
            setLabel("HTML output");
        }

        @Override
        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            if (_file != null && _file.exists() && (_file.length() > 0))
            {
                out.write("<table width=\"100%\" cellspacing=\"0\" cellpadding=\"1\">");
                renderTitle(model, out);
                if (isCollapse())
                    out.write("<tr style=\"display:none\"><td>");
                else
                    out.write("<tr><td>");
                out.write(PageFlowUtil.getFileContentsAsString(_file));
                out.write("</td></tr>");
                out.write("</table>");
            }
            if (_deleteTempFiles && null != _file)
                _file.delete();
        }
    }

    public static class TabReportView extends ROutputView
    {
        public static final String _type = "Tab";
        File _file;
        boolean _deleteTempFiles;

        TabReportView(File file, boolean deleteTempFiles)
        {
            _file = file;
            _deleteTempFiles = deleteTempFiles;
            setLabel("TSV output");
        }

        @Override
        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            if (_file != null && _file.exists() && (_file.length() > 0))
            {
                TabLoader tabLoader = new TabLoader(_file);
                tabLoader.setParseQuotes(true);
                TabLoader.ColumnDescriptor[] cols = tabLoader.getColumns();
                Map[] data = (Map[]) tabLoader.load();

                List<TabLoader.ColumnDescriptor> display = new ArrayList<TabLoader.ColumnDescriptor>();
                HashMap<String, TabLoader.ColumnDescriptor> hrefs = new HashMap<String, TabLoader.ColumnDescriptor>(tabLoader.getColumns().length * 2);
                HashMap<String, TabLoader.ColumnDescriptor> styles = new HashMap<String, TabLoader.ColumnDescriptor>(tabLoader.getColumns().length * 2);

                for (TabLoader.ColumnDescriptor col : cols)
                    hrefs.put(col.name, null);

                for (TabLoader.ColumnDescriptor col : cols)
                {
                    if (col.name.endsWith(".href"))
                    {
                        String name = col.name.substring(0,col.name.length()-".href".length());
                        if (hrefs.containsKey(name))
                        {
                            hrefs.put(name,col);
                            continue;
                        }
                    }
                    if (col.name.endsWith(".style"))
                    {
                        String name = col.name.substring(0,col.name.length()-".style".length());
                        if (hrefs.containsKey(name))
                        {
                            styles.put(name,col);
                            continue;
                        }
                    }
                    display.add(col);
                }

                int row = 0;
                out.write("<table width=\"100%\" class=\"dataRegion\" cellspacing=\"0\" cellpadding=\"1\">");
                renderTitle(model, out);
                if (isCollapse())
                    out.write("<tr style=\"display:none\"><td><table>");
                else
                    out.write("<tr><td><table>");
                out.write("<tr>");
                for (TabLoader.ColumnDescriptor col : display)
                {
                    if (Number.class.isAssignableFrom(col.getClass()))
                        out.write("<td class='header' align='right'>");
                    else
                        out.write("<td class='header'>");
                    out.write(PageFlowUtil.filter(col.name, true, true));
                    out.write("</td>");
                    row++;
                }
                out.write("</tr>");

                for (Map m : data)
                {
                    if (row % 2 == 0)
                        out.write("<tr bgcolor=#f0f0f0>");
                    else
                        out.write("<tr bgcolor=#ffffff>");
                    for (TabLoader.ColumnDescriptor col : display)
                    {
                        Object colVal = m.get(col.name);
                        if ("NA".equals(colVal))
                            colVal = null;
                        TabLoader.ColumnDescriptor hrefCol = hrefs.get(col.name);
                        String href = hrefCol == null ? null : ConvertUtils.convert((m.get(hrefCol.name)));
                        TabLoader.ColumnDescriptor styleCol = styles.get(col.name);
                        String style = styleCol == null ? null : ConvertUtils.convert((m.get(styleCol.name)));

                        out.write("<td class='ms-vb'");
                        if (Number.class.isAssignableFrom(col.clazz))
                            out.write(" align='right'");
                        if (null != style)
                        {
                            out.write(" style=\"");
                            out.write(PageFlowUtil.filter(style));
                            out.write("\"");
                        }
                        out.write(">");
                        if (null != href)
                        {
                            out.write("<a href=\"");
                            out.write(PageFlowUtil.filter(href));
                            out.write("\">");
                        }
                        if (null == colVal)
                            out.write("&nbsp");
                        else
                            out.write(PageFlowUtil.filter(ConvertUtils.convert(colVal), true, true));
                        if (null != href)
                            out.write("</a>");
                        out.write("</td>");
                    }
                    out.write("</tr>");
                    row++;
                }
                out.write("</table></td></tr>");
                out.write("</table>");
            }
            if (_deleteTempFiles && null != _file)
                _file.delete();
        }
    }

    public static class ImgReportView extends ROutputView
    {
        public static final String _type = "Image";
        File _file;
        boolean _deleteTempFiles;

        ImgReportView(File file, boolean deleteTempFiles)
        {
            _file = file;
            _deleteTempFiles = deleteTempFiles;
            setLabel("Image output");
        }

        @Override
        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            if (_file != null && _file.exists())
            {
                if (_file.length() > 0)
                {
                    File imgFile = moveToTemp(_file);
                    if (imgFile != null)
                    {
                        String key = "temp:" + GUID.makeGUID();
                        getViewContext().getRequest().getSession(true).setAttribute(key, imgFile);

                        out.write("<table width=\"100%\" cellspacing=\"0\" cellpadding=\"1\">");
                        renderTitle(model, out);
                        if (isCollapse())
                            out.write("<tr style=\"display:none\"><td>");
                        else
                            out.write("<tr><td>");
                        out.write("<img border=0 id=\"resultImage\" src=\"");

                        ActionURL url = PageFlowUtil.urlProvider(ReportUrls.class).urlStreamFile(getViewContext().getContainer());
                        url.addParameters(PageFlowUtil.map("sessionKey", key, "deleteFile", Boolean.toString(_deleteTempFiles)));

                        out.write(url.getLocalURIString());
                        out.write("\">");
                        out.write("</td></tr>");
                        out.write("</table>");
                    }
                }
                else
                    _file.delete();
            }
        }

        private File moveToTemp(File file)
        {
            try {
                File newFile = File.createTempFile("RReportImg", "tmp");
                newFile.delete();

                if (file.renameTo(newFile))
                    return newFile;
            }
            catch (IOException ioe)
            {
                throw new RuntimeException(ioe);
            }
            return null;
        }
    }

    public static class PdfReportView extends DownloadReportView
    {
        public static final String _type = "PDF";

        PdfReportView(RReport report, File file, boolean deleteTempFiles)
        {
            super(report, file, deleteTempFiles, "PDF");
        }
    }

    public static class FileoutReportView extends DownloadReportView
    {
        public static final String _type = "Fileout";

        FileoutReportView(RReport report, File file, boolean deleteTempFiles)
        {
            super(report, file, deleteTempFiles, "Text");
        }
    }

    public static class PostscriptReportView extends DownloadReportView
    {
        public static final String _type = "Postscript";

        PostscriptReportView(RReport report, File file, boolean deleteTempFiles)
        {
            super(report, file, deleteTempFiles, "Postscript");
        }
    }

    public static abstract class DownloadReportView extends ROutputView
    {
        File _file;
        boolean _deleteTempFiles;
        String _fileType;
        RReport _report;

        DownloadReportView(RReport report, File file, boolean deleteTempFiles, String fileType)
        {
            _report = report;
            _file = file;
            _deleteTempFiles = deleteTempFiles;
            _fileType = fileType;
            setLabel("View output");
        }

        @Override
        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            if (_file != null && _file.exists() && (_file.length() > 0))
            {
                if (_report != null && _report.getEntityId() != null)
                {
                    MimeMap mimeMap = new MimeMap();
                    DownloadFormFile form = new DownloadFormFile(_file);
                    form.setContentType(mimeMap.getContentTypeFor(_file.getName()));
                    AttachmentService.get().deleteAttachment(_report, _file.getName());
                    AttachmentService.get().addAttachments(getViewContext().getUser(), _report, Collections.singletonList((AttachmentFile)new StrutsAttachmentFile(form)));
                }

                String key = "temp:" + GUID.makeGUID();
                getViewContext().getRequest().getSession(true).setAttribute(key, _file);

                out.write("<table width=\"100%\" cellspacing=\"0\" cellpadding=\"1\">");
                renderTitle(model, out);
                if (isCollapse())
                    out.write("<tr style=\"display:none\"><td>");
                else
                    out.write("<tr><td>");

                if (_report != null && _report.getEntityId() != null)
                {
                    for (Attachment a : AttachmentService.get().getAttachments(_report))
                    {
                        if (_file.getName().equals(a.getName()))
                        {
                            out.write("<a href=\"");
                            out.write(a.getDownloadUrl("reports"));
                            out.write("\">");
                            out.write(_fileType);
                            out.write(" output file (click to download)</a>");
                            break;
                        }
                    }
                }
                else
                {
                    // file hasn't been saved yet
                    out.write("<a href=\"");
                    out.write(getViewContext().getActionURL().relativeUrl("streamFile", PageFlowUtil.map("sessionKey", key, "deleteFile", "false", "attachment", "true"), "reports", true));
                    out.write("\">");
                    out.write(_fileType);
                    out.write(" output file (click to download)</a>");
                }
                out.write("</td></tr>");
                out.write("</table>");
            }
        }
    }

    public static class DownloadFormFile implements FormFile
    {
        private File _file;
        private byte[] _contentData;
        private String _contentType = "text/plain";

        public DownloadFormFile(File file)
        {
            _file = file;
            try {
                _contentData = PageFlowUtil.getStreamContentsAsString(new FileInputStream(file)).getBytes();
            }
            catch (FileNotFoundException fnf)
            {
                _contentData = new byte[0];
            }
        }
        
        public String getContentType()
        {
            return _contentType;
        }

        public void setContentType(String contentType)
        {
            _contentType = contentType;
        }

        public int getFileSize()
        {
            return _contentData.length;
        }

        public void setFileSize(int i)
        {
            throw new UnsupportedOperationException();
        }

        public String getFileName()
        {
            return _file.getName();
        }

        public void setFileName(String string)
        {
            throw new UnsupportedOperationException();
        }

        public byte[] getFileData() throws IOException
        {
            return _contentData;
        }

        public InputStream getInputStream() throws IOException
        {
            return new BufferedInputStream(new ByteArrayInputStream(_contentData));
        }

        public void destroy()
        {
            throw new UnsupportedOperationException();
        }
    }

    protected static class TempFileCleanup extends HttpView
    {
        private String _path;

        public TempFileCleanup(String path)
        {
            _path = path;
        }

        protected void renderInternal(Object model, PrintWriter out) throws Exception
        {
            FileUtil.deleteDir(new File(_path));
        }
    }
}

