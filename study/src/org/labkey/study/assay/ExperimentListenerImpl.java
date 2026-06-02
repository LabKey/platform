/*
 * Copyright (c) 2017-2026 LabKey Corporation
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
package org.labkey.study.assay;

import org.labkey.api.data.CompareType;
import org.labkey.api.data.Container;
import org.labkey.api.data.SimpleFilter;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.TableSelector;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.ExpSampleType;
import org.labkey.api.exp.api.ExperimentListener;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.ElevatedUser;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.roles.ReaderRole;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.publish.StudyPublishService;
import org.labkey.api.view.UnauthorizedException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ExperimentListenerImpl implements ExperimentListener
{
    @Override
    public void afterResultDataCreated(Container container, User user, ExpRun run, ExpProtocol protocol) throws BatchValidationException
    {
        List<ValidationException> errors = new ArrayList<>();
        List<String> linkToStudyErrors = new ArrayList<>();

        StudyPublishService.get().autoLinkAssayResults(protocol, run, user, container, linkToStudyErrors);

        // copy results data to the target study if the protocol is configured to auto link
        for (String error : linkToStudyErrors)
        {
            errors.add(new ValidationException(error));
        }
        if (!errors.isEmpty())
        {
            throw new BatchValidationException(errors, null);
        }
    }

    @Override
    public void beforeMaterialDelete(List<? extends ExpMaterial> materials, Container container, User user)
    {
        // Check for datasets that need rows deleted due to a linked Sample Type row-level deletion

        // It's likely that we'll have multiple materials from the same sample type, so group them for efficient processing

        Map<ExpSampleType, Map<Container, List<ExpMaterial>>> typeToMaterials = new HashMap<>();

        for (ExpMaterial material: materials)
        {
            ExpSampleType sampleType = material.getSampleType();
            if (sampleType != null)
            {
                Container materialContainer = material.getContainer();
                typeToMaterials.
                        computeIfAbsent(sampleType, k -> new HashMap<>()).
                        computeIfAbsent(materialContainer, k -> new ArrayList<>()).
                        add(material);
            }
        }

        for (Map.Entry<ExpSampleType, Map<Container, List<ExpMaterial>>> entry : typeToMaterials.entrySet())
        {
            for (Dataset dataset: StudyPublishService.get().getDatasetsForPublishSource(entry.getKey().getRowId(), Dataset.PublishSource.SampleType))
            {
                Map<Container, List<ExpMaterial>> containerSamples = entry.getValue();

                // Need Read permission to check for linked samples
                User userWithReadPerm = ElevatedUser.getElevatedUser(user, ReaderRole.class);

                UserSchema schemaWithReadPerm = QueryService.get().getUserSchema(userWithReadPerm, dataset.getContainer(), "study");
                TableInfo tableInfoForRead = schemaWithReadPerm.getTable(dataset.getName());
                if (null == tableInfoForRead)
                    throw new UnauthorizedException("Cannot delete rows from dataset " + dataset);

                for (Map.Entry<Container, List<ExpMaterial>> containerEntry : containerSamples.entrySet())
                {
                    Container sampleContainer = containerEntry.getKey();
                    List<ExpMaterial> samples = containerEntry.getValue();

                    // GitHub Issue 1028: Can't delete a sample when any sample in the sample type has been linked to study
                    // check if samples are linked to the dataset, if not, skip the permission check for DeletePermission since we won't be deleting any rows
                    SimpleFilter filter = new SimpleFilter(FieldKey.fromParts(ExpMaterialTable.Column.RowId.toString()), samples.stream().map(ExpMaterial::getRowId).toList(), CompareType.IN);
                    Map<String, Object>[] linkedLsidRowIds = new TableSelector(tableInfoForRead, Set.of("LSID", "RowId"), filter, null).getMapArray();

                    Set<String> linkedLsids = Arrays.stream(linkedLsidRowIds).map(m -> (String)m.get("LSID")).collect(java.util.stream.Collectors.toSet());
                    Set<Long> linkedRowIds = Arrays.stream(linkedLsidRowIds).map(m -> ((Integer)m.get("RowId")).longValue()).collect(java.util.stream.Collectors.toSet());
                    if (linkedLsids.isEmpty())
                        continue;

                    TableInfo tableInfo = dataset.getTableInfo(user);
                    if (null == tableInfo || !tableInfo.hasPermission(user, DeletePermission.class))
                    {
                        throw new UnauthorizedException("Cannot delete rows from dataset " + dataset);
                    }

                    StudyPublishService.get().addRecallAuditEvent(sampleContainer, user, dataset, linkedLsids.size(), linkedRowIds);
                    dataset.deleteDatasetRows(user, linkedLsids);
                }
            }
        }
    }
}
