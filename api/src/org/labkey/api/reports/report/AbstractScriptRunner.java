package org.labkey.api.reports.report;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.*;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.common.util.Pair;

import java.io.File;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Created by IntelliJ IDEA.
 * User: Karl Lum
 * Date: Aug 12, 2007
 */
public abstract class AbstractScriptRunner implements RScriptRunner
{
    protected static Logger _log = Logger.getLogger(RScriptRunner.class);
    protected RReport _report;
    protected boolean _deleteTempFiles;
    protected ViewContext _context;
    protected File _data;

    public AbstractScriptRunner(RReport report, ViewContext context)
    {
        _report = report;
        _context = context;
    }

    public void setReport(RReport report)
    {
        _report = report;
    }

    public void setViewContext(ViewContext context)
    {
        _context = context;
    }

    public void setSourceData(File data)
    {
        _data = data;
    }

    public void setDeleteTempFiles(boolean deleteTempFiles)
    {
        _deleteTempFiles = deleteTempFiles;
    }

    protected static TSVGridWriter createGridWriter(ResultSet rs) throws SQLException
    {
        ResultSetMetaData md = rs.getMetaData();
        ColumnInfo cols[] = new ColumnInfo[md.getColumnCount()];

        List<DisplayColumn> dataColumns = new ArrayList();
        for (int i = 0; i < cols.length; i++)
        {
            int sqlColumn = i + 1;
            dataColumns.add(new RDisplayColumn( new ColumnInfo(md, sqlColumn)));
        }
        TSVGridWriter tsv = new TSVGridWriter(rs, dataColumns);
        tsv.setColumnHeaderType(TSVGridWriter.ColumnHeaderType.propertyName);

        return tsv;
    }


    protected String labkeyObjectProlog()
    {
        String labkey =
            "labkey.data <- read.table(\"${input_data}\", header=TRUE, sep=\"\\t\", quote=\"\", comment.char=\"\")\n" +
            "labkey.url <- function (controller, action, list){paste(labkey.url.base,controller,labkey.url.path,action,\".view?\",paste(names(list),list,sep=\"=\",collapse=\"&\"),sep=\"\")}\n" +
            "labkey.resolveLSID <- function(lsid){paste(labkey.url.base,\"experiment/resolveLSID.view?lsid=\",lsid,sep=\"\");}\n";
        labkey += "labkey.user.email=\"" + _context.getUser().getEmail() + "\"\n";
        ActionURL url = _context.getActionURL();
        labkey += "labkey.url.path=\"" + url.getExtraPath() + "/\"\n";
        labkey += "labkey.url.base=\"" + url.getBaseServerURI() + _context.getContextPath() + "/\"\n";
        return labkey;
    }


    protected String processInputReplacement(String script, File inputFile) throws Exception
    {
        Matcher m = RReport.scriptPattern.matcher(script);
        String inputFileName = inputFile.getAbsolutePath();
        inputFileName = inputFileName.replaceAll("\\\\", "/");

        while (m.find())
        {
            String value = m.group(1);

            if (RReport.INPUT_FILE_TSV.equals(value))
            {
                script = m.replaceFirst(inputFileName);
                m = RReport.scriptPattern.matcher(script);
            }
        }
        return script;
    }

    protected String processOutputReplacements(String script, List<Pair<String, String>> replacements) throws IOException
    {
        Matcher m = RReport.scriptPattern.matcher(script);
        File resultFile;
        String resultFileName;

        while (m.find())
        {
            String value = m.group(1);

            Pair<String, String> info = getReplacementInfo(value);
            if (info != null)
            {
                replacements.add(info);
                script = m.replaceFirst(info.getKey());
                m = RReport.scriptPattern.matcher(script);
            }
        }
        return script;
    }

    protected Pair<String, String> getReplacementInfo(String token) throws IOException
    {
        File resultFile = null;
        String viewType = null;

        if (token.startsWith(RReport.OUTPUT_FILE_TXT))
        {
            resultFile = File.createTempFile(RReport.FILE_PREFIX, "Result.txt", _report.getReportDir());
            viewType = RReport.TextOutputView._type;
        }
        else if (token.startsWith(RReport.OUTPUT_FILE_TSV))
        {
            resultFile = File.createTempFile(RReport.FILE_PREFIX, "Result.tsv", _report.getReportDir());
            viewType = RReport.TabReportView._type;
        }
        else if (token.startsWith(RReport.OUTPUT_FILE_IMG))
        {
            resultFile = File.createTempFile(RReport.FILE_PREFIX, "Result.jpg", _report.getReportDir());
            viewType = RReport.ImgReportView._type;
        }
        else if (token.startsWith(RReport.OUTPUT_FILE_PDF))
        {
            String name = token.substring(RReport.OUTPUT_FILE_PDF.length(), token.length()).concat(".pdf");
            resultFile = new File(_report.getReportDir(), name);
            viewType = RReport.PdfReportView._type;
        }
        else if (token.startsWith(RReport.OUTPUT_FILE_FILE))
        {
            String name = token.substring(RReport.OUTPUT_FILE_FILE.length(), token.length()).concat(".txt");
            resultFile = new File(_report.getReportDir(), name);
            viewType = RReport.FileoutReportView._type;
        }
        else if (token.startsWith(RReport.OUTPUT_FILE_POSTSCRIPT))
        {
            String name = token.substring(RReport.OUTPUT_FILE_POSTSCRIPT.length(), token.length()).concat(".ps");
            resultFile = new File(_report.getReportDir(), name);
            viewType = RReport.PostscriptReportView._type;
        }
        else if (token.startsWith(RReport.OUTPUT_FILE_HTML))
        {
            resultFile = File.createTempFile(RReport.FILE_PREFIX, "Result.txt", _report.getReportDir());
            viewType = RReport.HtmlReportView._type;
        }

        if (resultFile != null)
        {
            String resultFileName = resultFile.getAbsolutePath();
            resultFileName = resultFileName.replaceAll("\\\\", "/");

            return new Pair(resultFileName, viewType);
        }
        return null;
    }

    protected boolean isDebug()
    {
        if (_context != null)
        {
            //return _context.get("showDebug") != null;
        }
        return true;
    }

    public static class RDisplayColumn extends DataColumn
    {
        public RDisplayColumn(ColumnInfo col)
        {
            super(col);
        }

        public String getTsvFormattedValue(RenderContext ctx)
        {
            String value = super.getTsvFormattedValue(ctx);

            if (StringUtils.isEmpty(value))
                return "NA";
            return value;
        }
    }
}
