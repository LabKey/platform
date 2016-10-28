/*
 * Copyright (c) 2006-2016 LabKey Corporation
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
import org.labkey.api.data.DataRegion;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.DbReportIdentifier;
import org.labkey.api.reports.report.QueryReport;
import org.labkey.api.reports.report.QueryReportDescriptor;
import org.labkey.api.reports.report.ReportDescriptor;
import org.labkey.api.reports.report.ReportIdentifier;
import org.labkey.api.reports.report.ReportUrls;
import org.labkey.api.reports.report.view.ReportQueryView;
import org.labkey.api.security.User;
import org.labkey.api.study.Study;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.StudyManager;
import org.labkey.study.query.DatasetQueryView;
import org.springframework.mock.web.MockHttpServletRequest;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import java.sql.SQLException;
import java.util.List;

/**
 * User: brittp
 * Date: Aug 29, 2006
 * Time: 10:15:10 AM
 */
public class StudyQueryReport extends QueryReport
{
    public static final String TYPE = "Study.queryReport";

    public String getType()
    {
        return TYPE;
    }

    public void beforeSave(ContainerUser context)
    {
        ReportDescriptor reportDescriptor = getDescriptor();
        if (reportDescriptor instanceof QueryReportDescriptor)
        {
            try {
                String queryName = getDescriptor().getProperty(QueryParam.queryName.name());
                QueryDefinition def = QueryService.get().getQueryDef(context.getUser(), context.getContainer(), StudySchema.getInstance().getSchemaName(), queryName);
                if (def == null)
                {
                    // not a custom query definition, try a table based definition
                    UserSchema schema = ReportQueryViewFactory.getStudyQuerySchema(context, getDescriptor());
                    def = QueryService.get().createQueryDefForTable(schema, queryName);                                            
                }

                if (def != null)
                {
                    HttpServletRequest request = new MockHttpServletRequest();
                    String viewName = getDescriptor().getProperty(QueryParam.viewName.toString());
                    if (def.getSharedCustomView(viewName) == null)
                    {
                        CustomView view = def.createSharedCustomView(viewName);
                        view.setIsHidden(true);
                        view.save(context.getUser(), request);
                    }
                }
            }
            catch (Exception e)
            {
                throw new RuntimeException(e);
            }
        }

        super.beforeSave(context);
    }

    @Override
    protected UserSchema getSchema(ViewContext context, String schemaName)
    {
        try {
            return ReportQueryViewFactory.getStudyQuerySchema(context, getDescriptor());
        }
        catch (ServletException e)
        {
            throw new RuntimeException(e);
        }
    }

    public QueryReportDescriptor.QueryViewGenerator getQueryViewGenerator()
    {
        return new QueryReportDescriptor.QueryViewGenerator() {
            public ReportQueryView generateQueryView(ViewContext context, ReportDescriptor descriptor) throws Exception
            {
                ReportQueryView view = ReportQueryViewFactory.get().generateQueryView(context, descriptor,
                        descriptor.getProperty(ReportDescriptor.Prop.queryName),
                        descriptor.getProperty(ReportDescriptor.Prop.viewName));
                view.setButtonBarPosition(DataRegion.ButtonBarPosition.TOP);
                view.setUseQueryViewActionExportURLs(true);
                view.getSettings().setMaxRows(100);

                int datasetId = NumberUtils.toInt(getDescriptor().getProperty("showWithDataset"), -1);
                Study study = StudyManager.getInstance().getStudy(context.getContainer());
                DatasetDefinition datasetDef = StudyManager.getInstance().getDatasetDefinition(study, datasetId);
                if (datasetDef != null && !datasetDef.canRead(context.getUser()))
                    view.getSettings().setAllowCustomizeView(false);

                return view;
            }
        };
    }

    @Deprecated
    public ActionURL getRunReportURL(ViewContext context)
    {
        int datasetId = NumberUtils.toInt(getDescriptor().getProperty("showWithDataset"), -1);
        if (datasetId != -1)
        {
            Study study = StudyManager.getInstance().getStudy(context.getContainer());
            DatasetDefinition datasetDef = StudyManager.getInstance().getDatasetDefinition(study, datasetId);
            if (datasetDef != null)
                return new ActionURL(StudyController.DatasetReportAction.class, context.getContainer()).
                            addParameter(DatasetDefinition.DATASETKEY, datasetId).
                            addParameter(StudyController.DATASET_REPORT_ID_PARAMETER_NAME, getDescriptor().getReportId().toString());
        }

        return PageFlowUtil.urlProvider(ReportUrls.class).urlQueryReport(context.getContainer(), this);
    }
}
