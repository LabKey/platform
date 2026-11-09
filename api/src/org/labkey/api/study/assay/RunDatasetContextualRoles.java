/*
 * Copyright (c) 2009-2026 LabKey Corporation
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
import org.jetbrains.annotations.NotNull;
import org.labkey.api.assay.AssayProtocolSchema;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayService;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.security.roles.HasContextualRoles;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.security.roles.Role;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.StudyService;
import org.labkey.api.study.publish.StudyDatasetLinkedColumn;
import org.labkey.api.view.ViewContext;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.labkey.api.util.IntegerUtils.asInteger;

/**
 * User: kevink
 * Date: Jun 1, 2009 1:02:56 PM
 */
public class RunDatasetContextualRoles implements HasContextualRoles
{
    /**
     * Returns a contextual ReaderRole if the user has permission to
     * <b>at least one of</b> the study datasets that the run results have
     * been linked to.
     *
     * @return a singleton ReaderRole set, or an empty set if no contextual role applies
     */
    @Override
    @NotNull
    public Set<Role> getContextualRoles(ViewContext context)
    {
        // skip the check if the user has ReadPermission to the container
        Container container = context.getContainer();
        User user = context.getUser();
        if (container.hasPermission(user, ReadPermission.class))
            return Set.of();

        String rowIdStr = context.getRequestOrThrow().getParameter("rowId");
        if (rowIdStr != null)
        {
            int runRowId = NumberUtils.toInt(rowIdStr);
            return RunDatasetContextualRoles.getContextualRolesForRun(container, user, runRowId);
        }
        return Set.of();
    }

    /**
     * Returns a contextual ReaderRole if the user has permission to
     * <b>at least one of</b> the study datasets that the run results have
     * been linked to.
     *
     * @param container the container
     * @param user the user
     * @param runId the run to check
     * @return a singleton ReaderRole set, or an empty set if no contextual role applies
     */
    @NotNull
    public static Set<Role> getContextualRolesForRun(Container container, User user, int runId)
    {
        ExpRun run = ExperimentService.get().getExpRun(runId);
        if (run == null)
            return Set.of();

        return getContextualRolesForRun(container, user, run, FieldKey.fromParts("runid"));
    }

    /** caller should have already checked that the user does not have ReadPermission to the container */
    @NotNull
    public static Set<Role> getContextualRolesForRun(Container container, User user, ExpRun run, FieldKey runIdFieldKey)
    {
        if (container == null || user == null)
            return Set.of();

        ExpProtocol protocol = run.getProtocol();
        if (protocol == null)
            return Set.of();

        AssayProvider provider = AssayService.get().getProvider(protocol);
        if (provider == null)
            return Set.of();

        // use a user with elevated permissions to do the query to figure out permissions
        AssayProtocolSchema schema = provider.createProtocolSchema(User.getSearchUser(), container, protocol, null);
        if (schema == null)
            return Set.of();

        // get the results table and the set of dataset columns
        TableInfo resultsTable = schema.createDataTable(null);
        if (resultsTable == null)
            return Set.of();

        Set<String> columnNames = resultsTable.getColumnNameSet();
        Set<String> datasetColumnNames = new LinkedHashSet<>();
        for (String columnName : columnNames)
        {
            if (columnName.startsWith("dataset"))
                datasetColumnNames.add(columnName);
        }

        // table contains no dataset columns if results haven't been linked
        if (datasetColumnNames.isEmpty())
            return Set.of();

        Map<String, Object>[] results = new TableSelector(resultsTable, datasetColumnNames, new SimpleFilter(runIdFieldKey, run.getRowId()), null).getMapArray();

        if (results.length == 0)
            return Set.of();

        List<ColumnInfo> datasetColumns = resultsTable.getColumns(datasetColumnNames.toArray(new String[0]));

        for (Map<String, Object> result : results)
        {
            for (ColumnInfo datasetColumn : datasetColumns)
            {
                if (!(datasetColumn instanceof StudyDatasetLinkedColumn))
                    continue;
                Integer datasetId = asInteger(result.get(datasetColumn.getName()));
                if (datasetId == null)
                    continue;

                Container studyContainer = ((StudyDatasetLinkedColumn)datasetColumn).getStudyContainer();
                Dataset dataset = StudyService.get().getDataset(studyContainer, datasetId.intValue());
                if (null == dataset)
                    return Set.of();
                if (dataset.hasPermission(user, ReadPermission.class))
                    return Collections.singleton(new ReaderRole());
            }
        }

        return Set.of();
    }
}
