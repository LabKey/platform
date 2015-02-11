/*
 * Copyright (c) 2006-2015 LabKey Corporation
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
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.ExcelWriter;
import org.labkey.api.data.Results;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.Sort;
import org.labkey.api.data.SqlSelector;
import org.labkey.api.data.TableInfo;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.QueryView;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reports.report.RedirectReport;
import org.labkey.api.security.User;
import org.labkey.api.study.StudyService;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.reports.ReportsController;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyImpl;
import org.labkey.study.query.DatasetQuerySettings;
import org.labkey.study.query.DatasetQueryView;
import org.labkey.study.query.StudyQuerySchema;
import org.springframework.validation.BindException;

import javax.servlet.ServletException;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Arrays;

/**
 * User: Matthew
 * Date: Jun 14, 2006
 * Time: 12:49:42 PM
 */
public class ExportExcelReport extends RedirectReport
{
    public static final String TYPE = "Study.exportExcelReport";
    public static final String LOCATION_ID = "siteId";

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
    public String getUrl(Container c)
    {
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



    public void setLocationId(int locationId)
    {
        getDescriptor().setProperty(LOCATION_ID, String.valueOf(locationId));
    }

    protected int getLocationId()
    {
        return NumberUtils.toInt(getDescriptor().getProperty(LOCATION_ID));
    }


    public void runExportToExcel(ViewContext context, StudyImpl study, User user, BindException errors)
            throws IOException, ServletException, SQLException
    {
        // TODO: wire up the security
        boolean checkUserPermissions = true;//mustCheckDatasetPermissions(user, ACL.PERM_READ);

        StudySchema studySchema = StudySchema.getInstance();

        //
        // DATASETS
        //

        SimpleFilter siteFilter = null;
        final int locationId = getLocationId();
        if (locationId != 0)
        {
            siteFilter = new SimpleFilter();
            siteFilter.addWhereClause(study.getSubjectColumnName() + " IN (SELECT ParticipantId FROM study.Participant WHERE CurrentSiteId=" + locationId + ")", new Object[0]);
        }

        ExcelWriter writer = new ExcelWriter(ExcelWriter.ExcelDocumentType.xls);

        for (DatasetDefinition def : study.getDatasets())
        {
            if (def.getTypeURI() == null)
                continue;

            if (checkUserPermissions && !def.canRead(user))
                continue;

            Sort sort = new Sort(StudyService.get().getSubjectColumnName(study.getContainer()) + ",SequenceNum");

            UserSchema schema = QueryService.get().getUserSchema(user, study.getContainer(), StudyQuerySchema.SCHEMA_NAME);
            DatasetQuerySettings settings = (DatasetQuerySettings)schema.getSettings(context, DatasetQueryView.DATAREGION, def.getName());
            settings.setBaseFilter(siteFilter);
            settings.setBaseSort(sort);

            QueryView queryView = schema.createView(context, settings, errors);
            String label = def.getLabel() != null ? def.getLabel() : String.valueOf(def.getDatasetId());

            writer.setDisplayColumns(queryView.getExportColumns(queryView.getDisplayColumns()));
            renderSheet(writer, label, queryView.getResults());
        }

        if (writer.getWorkbook().getNumberOfSheets() == 0)
        {
            writer.setHeaders(Arrays.asList("No permissions"));
            writer.renderNewSheet();
        }
        else
        {
            //
            // PARTICIPANTS
            //
            //  "SELECT ParticipantId, COALESCE(Label,CAST(RowId AS VARCHAR(10))) AS Site FROM study.Participant LEFT OUTER JOIN study.Site ON study.Participant.CurrentSiteId = study.Site.RowId\n" +
            //  "WHERE study.Participant.container='" + study.getContainer().getId() + "'\n" +
            //  (locationId == 0 ? "" : "AND study.Participant.CurrentSiteId=" + locationId + "\n") +
            //  "ORDER BY 1")
            TableInfo locationTableInfo = studySchema.getTableInfoSite(getContainer());
            TableInfo participantTableInfo = studySchema.getTableInfoParticipant();
            if (null == locationTableInfo || null == participantTableInfo)
                throw new IllegalStateException("TableInfo not found.");

            SQLFragment sql = new SQLFragment();
            sql.append("SELECT ParticipantId, COALESCE(Label,CAST(RowId AS VARCHAR(10))) AS Site FROM ")
                    .append(participantTableInfo.getFromSQL("p")).append(" LEFT OUTER JOIN ")
                    .append(locationTableInfo.getFromSQL("l")).append(" ON ")
                    .append(participantTableInfo.getColumn("CurrentSiteId").getValueSql("p"))
                    .append(locationTableInfo.getColumn("RowId").getValueSql("l")).append("\n")
                    .append("WHERE ").append(participantTableInfo.getColumn("Container").getValueSql("p")).append("=?");
            sql.add(study.getContainer());
            sql.append("\n");
            if (locationId != 0)
            {
                sql.append("AND ").append(participantTableInfo.getColumn("CurrentSiteId").getValueSql("p")).append("=?");
                sql.add(locationId);
                sql.append("\n");
            }
            sql.append("ORDER BY 1");
            ResultSet rs = new SqlSelector(studySchema.getSchema(), sql.getSQL()).getResultSet();
            writer.createColumns(rs.getMetaData());

            String label = StudyService.get().getSubjectNounPlural(study.getContainer());
            writer.setResultSet(rs);
            writer.setAutoSize(true);
            writer.setCaptionRowVisible(true);

            if (label.length() > 0)
                writer.setSheetName(label);

            writer.renderNewSheet();
        }

        writer.write(context.getResponse(), study.getLabel());
    }


    private static void renderSheet(ExcelWriter writer, String label, Results results)
    {
        writer.setResults(results);
        writer.setAutoSize(true);
        writer.setCaptionRowVisible(true);

        if (label.length() > 0)
            writer.setSheetName(label);

        writer.renderNewSheet();
    }
}
