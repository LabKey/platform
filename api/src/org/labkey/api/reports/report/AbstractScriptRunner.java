/*
 * Copyright (c) 2007-2009 LabKey Corporation
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

package org.labkey.api.reports.report;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.labkey.api.data.*;
import org.labkey.api.reports.report.r.ParamReplacement;
import org.labkey.api.reports.report.r.ParamReplacementSvc;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.util.Pair;

import java.io.File;
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

        List<DisplayColumn> dataColumns = new ArrayList<DisplayColumn>();
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
        StringBuffer labkey = new StringBuffer();
        labkey.append("labkey.data <- read.table(\"${input_data}\", header=TRUE, sep=\"\\t\", quote=\"\", comment.char=\"\")\n" +
            "labkey.url <- function (controller, action, list){paste(labkey.url.base,controller,labkey.url.path,action,\".view?\",paste(names(list),list,sep=\"=\",collapse=\"&\"),sep=\"\")}\n" +
            "labkey.resolveLSID <- function(lsid){paste(labkey.url.base,\"experiment/resolveLSID.view?lsid=\",lsid,sep=\"\");}\n");
        labkey.append("labkey.user.email=\"").append(_context.getUser().getEmail()).append("\"\n");

        ActionURL url = _context.getActionURL();
        labkey.append("labkey.url.path=\"").append(url.getExtraPath()).append("/\"\n");
        labkey.append("labkey.url.base=\"").append(url.getBaseServerURI()).append(_context.getContextPath()).append("/\"\n");

        // url parameters
        Pair<String, String>[] params = url.getParameters();
        if (params.length > 0)
        {
            String sep = "";
            labkey.append("labkey.url.params <- list(");
            for (Pair<String, String> param : params)
            {
                labkey.append(sep);
                labkey.append("\"");
                labkey.append(param.getKey());
                labkey.append("\"");
                labkey.append("=");
                labkey.append("\"");
                labkey.append(param.getValue());
                labkey.append("\"");
                sep = ",";
            }
            labkey.append(")\n");
        }
        else
            labkey.append("labkey.url.params <- NULL\n");

        // session information
        if (_context.getRequest() != null)
        {
            labkey.append("labkey.sessionCookieName = \"JSESSIONID\"\n");
            labkey.append("labkey.sessionCookieContents = \"");
            labkey.append(PageFlowUtil.getCookieValue(_context.getRequest().getCookies(), "JSESSIONID", ""));
            labkey.append("\"\n");
        }
        return labkey.toString();
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

    protected String processOutputReplacements(String script, List<ParamReplacement> replacements) throws Exception
    {
        return ParamReplacementSvc.get().processParamReplacement(script, _report.getReportDir(), replacements);
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
