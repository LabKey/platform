/*
 * Copyright (c) 2017-2019 LabKey Corporation
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

import org.jetbrains.annotations.Nullable;
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
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.study.Dataset;
import org.labkey.api.study.publish.StudyPublishService;
import org.labkey.api.view.UnauthorizedException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import static java.util.Collections.singleton;

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
        for (ExpMaterial material: materials)
        {
            ExpSampleType sampleType = material.getSampleType();
            if (sampleType != null)
            {
                for (Dataset dataset: StudyPublishService.get().getDatasetsForPublishSource(sampleType.getRowId(), Dataset.PublishSource.SampleType))
                {
                    TableInfo t = dataset.getTableInfo(user);
                    if (null == t || !t.hasPermission(user, DeletePermission.class))
                    {
                        throw new UnauthorizedException("Cannot delete rows from dataset " + dataset);
                    }

                    UserSchema schema = QueryService.get().getUserSchema(user, dataset.getContainer(), "study");
                    TableInfo tableInfo = schema.getTable(dataset.getName());

                    SimpleFilter filter = new SimpleFilter(FieldKey.fromParts(ExpMaterialTable.Column.RowId.toString()), material.getRowId());
                    String lsid = new TableSelector(tableInfo, singleton("LSID"), filter, null).getObject(String.class);

                    if (lsid != null)
                    {
                        StudyPublishService.get().addRecallAuditEvent(material.getContainer(), user, dataset, 1, null);
                        dataset.deleteDatasetRows(user, Arrays.asList(lsid));
                    }
                }
            }
        }
    }
}
