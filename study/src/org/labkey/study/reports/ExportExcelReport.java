/*
 * Copyright (c) 2006-2009 LabKey Corporation
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

import jxl.write.WritableWorkbook;
import jxl.write.WriteException;
import org.labkey.api.data.*;
import org.labkey.api.security.User;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.ActionURL;
import org.labkey.api.study.Study;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.reports.ReportsController;
import org.labkey.study.model.DataSetDefinition;
import org.labkey.study.model.StudyImpl;
import org.apache.commons.lang.math.NumberUtils;

import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;

/**
 * User: Matthew
 * Date: Jun 14, 2006
 * Time: 12:49:42 PM
 */
public class ExportExcelReport extends RedirectReport
{
    public static final String TYPE = "Study.exportExcelReport";
    public static final String SITE_ID = "siteId";

    public String getType()
    {
        return TYPE;
    }

    public String getTypeDescription()
    {
        return "Study Export Excel Report";
    }

    public boolean canHavePermissions()
    {
        return true;
    }

    protected Container getContainer()
    {
        return ContainerManager.getForId(getDescriptor().getContainerId());
    }

    @Override
    public String getUrl(ViewContext context)
    {
        Container c = getContainer();

        ActionURL url = new ActionURL(ReportsController.ExportExcelAction.class, c);
        url.addParameter("reportId", getReportId().toString());


        return url.toString();
    }


    /*
     * Since this report returns an attachment rather than HTML we redirect to an action that
     * will generate the file
     context
     * @param context
     * @return
     * @throws ServletException
    @Override
    public HttpView getView(ViewContext context) throws ServletException
    {
        Container c = context.getContainer();
        this.redirectUrl = ActionURL.toPathString("Study-Reports", "exportExcel", c) + "?reportId=" + getReportId();
        return super.getView(context);
    }
     */



    public void setSiteId(int siteId)
    {
        getDescriptor().setProperty(SITE_ID, String.valueOf(siteId));
    }

    protected int getSiteId()
    {
        return NumberUtils.toInt(getDescriptor().getProperty(SITE_ID));
    }

    @Override
    public String getParams()
    {
        int siteId = getSiteId();
        return siteId == 0 ? "" : "siteId=" + siteId;
    }


    @Override
    public void setParams(String params)
    {
        Map m = PageFlowUtil.mapFromQueryString(params);
        String siteIdStr = (String)m.get("siteId");
        if (null != siteIdStr)
        {
            try
            {
                setSiteId(Integer.valueOf(siteIdStr));
            }
            catch (NumberFormatException x)
            {
                setSiteId(0);
            }
        }
    }


    public void runExportToExcel(HttpServletResponse response, StudyImpl study, User user)
            throws IOException, ServletException, SQLException, WriteException
    {
        // TODO: wire up the security
        boolean checkUserPermissions = true;//mustCheckDatasetPermissions(user, ACL.PERM_READ);

        StudySchema studySchema = StudySchema.getInstance();
        DataSetDefinition[] defs = study.getDataSets();

        //
        // DATASETS
        //

        SimpleFilter siteFilter = null;
        final int siteId = getSiteId();
        if (siteId != 0)
        {
            siteFilter = new SimpleFilter();
            siteFilter.addWhereClause("ParticipantId IN (SELECT ParticipantId FROM study.Participant WHERE CurrentSiteId=" + siteId + ")", new Object[0], new String[0]);
        }

        ServletOutputStream outputStream = ExcelWriter.getOutputStream(response, study.getLabel());
        WritableWorkbook workbook = ExcelWriter.getWorkbook(outputStream);
        ExcelWriter writer = new ExcelWriter();

        for (DataSetDefinition def : defs)
        {
            if (def.getTypeURI() == null)
                continue;

            if (checkUserPermissions && !def.canRead(user))
                continue;

            TableInfo tinfo = def.getTableInfo(user, checkUserPermissions, true);
            Sort sort = new Sort("ParticipantId,SequenceNum");
            ResultSet rs = Table.select(tinfo, Table.ALL_COLUMNS, siteFilter, sort);

            String label = def.getLabel() != null ? def.getLabel() : String.valueOf(def.getDataSetId());

            // Filter out labkey-specific columns, lsid and sourcelsid
            List<ColumnInfo> columns = tinfo.getColumns();
            List<ColumnInfo> destColumns = new ArrayList<ColumnInfo>(columns.size() - 2);
            for (ColumnInfo column : columns)
            {
                String name = column.getName();
                if (!("lsid".equals(name) || "sourcelsid".equals(name)))
                {
                    destColumns.add(column);
                }
            }

            writer.setColumns(destColumns);
            renderSheet(workbook, writer, label, rs);
        }

        if (workbook.getNumberOfSheets() == 0)
        {
            writer.setHeaders(Arrays.asList("No permissions"));
            writer.renderNewSheet(workbook);
        }
        else
        {
            //
            // PARTICIPANTS
            //
            ResultSet rs = Table.executeQuery(studySchema.getSchema(),
                    "SELECT ParticipantId, COALESCE(Label,CAST(RowId AS VARCHAR(10))) AS Site FROM study.Participant LEFT OUTER JOIN study.Site ON study.Participant.CurrentSiteId = study.Site.RowId\n" +
                    "WHERE study.Participant.container='" + study.getContainer().getId() + "'\n" +
                    (siteId == 0 ? "" : "AND study.Participant.CurrentSiteId=" + siteId + "\n") +
                    "ORDER BY 1",
                    null);

            writer.createColumns(rs.getMetaData());
            renderSheet(workbook, writer, "Participants", rs);
        }

        ExcelWriter.closeWorkbook(workbook, outputStream);
    }


    private static void renderSheet(WritableWorkbook workbook, ExcelWriter writer, String label, ResultSet rs)
    {
        writer.setResultSet(rs);
        writer.setAutoSize(true);
        writer.setCaptionRowVisible(true);

        if (label.length() > 0)
            writer.setSheetName(label);

        writer.renderNewSheet(workbook);
    }
}
