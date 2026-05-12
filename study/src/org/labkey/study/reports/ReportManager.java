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

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.collections.CaseInsensitiveHashMap;
import org.labkey.api.data.Container;
import org.labkey.api.query.CustomView;
import org.labkey.api.query.CustomViewInfo;
import org.labkey.api.query.QueryDefinition;
import org.labkey.api.query.QueryParam;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.reports.Report;
import org.labkey.api.reports.ReportService;
import org.labkey.api.reports.report.view.ReportUtil;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.specimen.SpecimenMigrationService;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.Study;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;
import org.labkey.study.StudySchema;
import org.labkey.study.controllers.StudyController;
import org.labkey.study.model.DatasetDefinition;
import org.labkey.study.model.DatasetManager;
import org.labkey.study.model.StudyManager;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class ReportManager implements DatasetManager.DatasetListener
{
    private static final ReportManager instance = new ReportManager();
    private static final Logger _log = LogManager.getLogger(ReportManager.class);

    public static ReportManager get()
    {
        return instance;
    }

    private ReportManager()
    {
        DatasetManager.addDatasetListener(this);
    }

    public List<Pair<String, String>> getReportLabelsForDataset(ViewContext context, Dataset def)
    {
        List<Pair<String, String>> labels = new ArrayList<>();
        String reportKey = ReportUtil.getReportKey(StudySchema.getInstance().getSchemaName(), def.getName());

        for (Report report : ReportUtil.getReportsIncludingInherited(context.getContainer(), context.getUser(), reportKey))
        {
            String label = report.getDescriptor().getReportName();
            labels.add(new Pair<>(label, report.getDescriptor().getReportId().toString()));
        }

        // add any custom query views
        UserSchema schema = QueryService.get().getUserSchema(context.getUser(), context.getContainer(), "study");
        QueryDefinition qd = QueryService.get().getQueryDef(context.getUser(), def.getContainer(), "study", def.getName());
        if (null == qd)
            qd = schema.getQueryDefForTable(def.getName());
        Map<String, CustomView> views = qd.getCustomViews(context.getUser(), context.getRequest(), false, false);
        if (views != null)
        {
            labels.addAll(views.values()
                .stream()
                .filter(view -> null != view.getName())
                .map(view -> new Pair<>(view.getName(), view.getName()))
                .toList());
        }

        labels.sort((o1, o2) ->
        {
            if (o1.getKey() == null && o2.getKey() == null) return 0;
            if (o1.getKey() == null) return -1;
            if (o2.getKey() == null) return 1;

            return o1.getKey().compareTo(o2.getKey());
        });

        // add the default grid as the first element
        labels.addFirst(new Pair<>("Default Grid View", ""));

        return labels;
    }

    public Report getReport(Container c, int reportId)
    {
        return ReportService.get().getReport(c, reportId);
    }

    @Nullable
    public Report createReport(String reportType)
    {
        return ReportService.get().createReportInstance(reportType);
    }

    public static class StudyReportFilter extends ReportUtil.DefaultReportFilter
    {
        Map<String, DatasetDefinition> _datasets;
        boolean _editOnly;

        public StudyReportFilter(boolean editOnly)
        {
            _editOnly = editOnly;
        }

        @Override
        public boolean accept(Report report, Container c, User user)
        {
            if (_editOnly && !report.canEdit(user, c))
                return false;
            return ReportManager.get().canReadReport(user, c, report);
        }

        private Map<String, DatasetDefinition> getDatasets(Container c)
        {
            if (_datasets == null)
            {
                _datasets = new CaseInsensitiveHashMap<>();

                Study study = StudyManager.getInstance().getStudy(c);
                if (study == null)
                    return Collections.emptyMap();
                for (DatasetDefinition ds : StudyManager.getInstance().getDatasetDefinitions(study))
                    _datasets.put(ds.getName(), ds);
            }

            return _datasets;
        }

        @Override
        public ActionURL getViewRunURL(User user, Container c, CustomViewInfo view)
        {
            Map<String, DatasetDefinition> datasets = getDatasets(c);

            if (datasets.containsKey(view.getQueryName()))
            {
                return new ActionURL(StudyController.DatasetReportAction.class, c).
                        addParameter(Dataset.DATASET_KEY, datasets.get(view.getQueryName()).getDatasetId()).
                        addParameter(StudyController.DATASET_VIEW_NAME_PARAMETER_NAME, view.getName());
            }

            // any specimen views
            if ("SpecimenDetail".equals(view.getQueryName()))
            {
                return SpecimenMigrationService.get().getSpecimensURL(c).
                        addParameter("showVials", "true").
                        addParameter("SpecimenDetail." + QueryParam.viewName, view.getName());
            }
            else if ("SpecimenSummary".equals(view.getQueryName()))
            {
                return SpecimenMigrationService.get().getSpecimensURL(c).
                        addParameter("SpecimenSummary." + QueryParam.viewName, view.getName());
            }

            return super.getViewRunURL(user, c, view);
        }
    }

    /**
     * Checks both dataset and explicit permissions on a report to determine if a user has read
     * access.
     */
    public boolean canReadReport(User user, Container c, Report report)
    {
/*
        SecurityPolicy policy = SecurityPolicyManager.getPolicy(report.getDescriptor(), false);

        if (policy.isEmpty())
        {
            StudyImpl study = StudyManager.getInstance().getStudy(c);

            if (study != null && (study.getSecurityType() == SecurityType.ADVANCED_READ ||
                    study.getSecurityType() == SecurityType.ADVANCED_WRITE))
            {
                // dataset permissions
                String datasetId = report.getDescriptor().getProperty(DatasetDefinition.DATASETKEY);
                String queryName = report.getDescriptor().getProperty(QueryParam.queryName.toString());

                if (NumberUtils.isDigits(datasetId))
                {
                    DatasetDefinition dsDef = StudyManager.getInstance().getDatasetDefinition(study, NumberUtils.toInt(datasetId));
                    if (dsDef != null)
                        return dsDef.canRead(user);
                }
                else if (queryName != null)
                {
                    // try query name, which is synonymous to dataset in study-land
                    DatasetDefinition dsDef = StudyManager.getInstance().getDatasetDefinitionByQueryName(study, queryName);
                    if (dsDef != null)
                        return dsDef.canRead(user);
                }
                return true;
            }
        }
*/
        return report.hasPermission(user, c, ReadPermission.class);
    }

    @Override
    public void datasetChanged(final Dataset def)
    {
        if (def != null)
        {
            _log.debug("Cache cleared notification on dataset : {}", def.getDatasetId());
            String reportKey = ReportUtil.getReportKey(StudySchema.getInstance().getSchemaName(), def.getName());
            for (Report report : ReportUtil.getReportsIncludingInherited(def.getContainer(), null, reportKey))
            {
                report.clearCache();
            }
        }
    }
}
