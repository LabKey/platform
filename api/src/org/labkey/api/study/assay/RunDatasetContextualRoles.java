/*
 * Copyright (c) 2009-2016 LabKey Corporation
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
package org.labkey.api.study.assay;

import org.apache.commons.lang3.math.NumberUtils;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.HasContextualRoles;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.StudyService;
import org.labkey.api.view.ViewContext;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: kevink
 * Date: Jun 1, 2009 1:02:56 PM
 */
public class RunDatasetContextualRoles implements HasContextualRoles
{
    /**
     * Returns a contextual ReaderRole if the user has permission to
     * <b>at least one of</b> the study datasets that the run results have
     * been copied to.
     *
     * @return a singleton ReaderRole set or null
     */
    @Nullable
    public Set<Role> getContextualRoles(ViewContext context)
    {
        // skip the check if the user has ReadPermission to the container
        Container container = context.getContainer();
        User user = context.getUser();
        if (container.hasPermission(user, ReadPermission.class))
            return null;

        String rowIdStr = context.getRequest().getParameter("rowId");
        if (rowIdStr != null)
        {
            int runRowId = NumberUtils.toInt(rowIdStr);
            return RunDatasetContextualRoles.getContextualRolesForRun(container, user, runRowId);
        }
        return null;
    }

    /**
     * Returns a contextual ReaderRole if the user has permission to
     * <b>at least one of</b> the study datasets that the run results have
     * been copied to.
     *
     * @param container the container
     * @param user the user
     * @param runId the run to check
     * @return a singleton ReaderRole set or null
     */
    @Nullable
    public static Set<Role> getContextualRolesForRun(Container container, User user, int runId)
    {
        ExpRun run = ExperimentService.get().getExpRun(runId);
        if (run == null)
            return null;

        return getContextualRolesForRun(container, user, run, FieldKey.fromParts("runid"));
    }

    /** caller should have already checked that the user does not have ReadPermission to the container */
    @Nullable
    public static Set<Role> getContextualRolesForRun(Container container, User user, ExpRun run, FieldKey runIdFieldKey)
    {
        if (container == null || user == null)
            return null;

        ExpProtocol protocol = run.getProtocol();
        if (protocol == null)
            return null;

        AssayProvider provider = AssayService.get().getProvider(protocol);
        if (provider == null)
            return null;

        AssayProtocolSchema schema = provider.createProtocolSchema(user, container, protocol, null);
        if (schema == null)
            return null;

        // get the results table and the set of dataset columns
        TableInfo resultsTable = schema.createDataTable();
        if (resultsTable == null)
            return null;

        Set<String> columnNames = resultsTable.getColumnNameSet();
        Set<String> datasetColumnNames = new LinkedHashSet<>();
        for (String columnName : columnNames)
        {
            if (columnName.startsWith("dataset"))
                datasetColumnNames.add(columnName);
        }

        // table contains no dataset columns if results haven't been copied
        if (datasetColumnNames.size() == 0)
            return null;

        Map<String, Object>[] results = new TableSelector(resultsTable, datasetColumnNames, new SimpleFilter(runIdFieldKey, run.getRowId()), null).getMapArray();

        if (results.length == 0)
            return null;

        List<ColumnInfo> datasetColumns = resultsTable.getColumns(datasetColumnNames.toArray(new String[datasetColumnNames.size()]));

        for (Map<String, Object> result : results)
        {
            for (ColumnInfo datasetColumn : datasetColumns)
            {
                if (!(datasetColumn instanceof StudyDatasetColumn))
                    continue;
                Integer datasetId = (Integer)result.get(datasetColumn.getName());
                if (datasetId == null)
                    continue;

                Container studyContainer = ((StudyDatasetColumn)datasetColumn).getStudyContainer();
                Dataset dataset = StudyService.get().getDataset(studyContainer, datasetId.intValue());
                SecurityPolicy policy = dataset.getPolicy();
                if (policy.hasPermission(user, ReadPermission.class))
                    return Collections.singleton(new ReaderRole());
            }
        }

        return null;
    }
}
