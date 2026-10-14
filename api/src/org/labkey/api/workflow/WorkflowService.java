/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.api.workflow;

import jakarta.servlet.http.HttpServletRequest;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;

import java.util.Map;

public interface WorkflowService
{
    enum WorkflowConfigs
    {
        ActionId,
        JobId,
    }

    enum ActionType
    {
        AssayImport("assay types", "Imported assay data", WorkEntity.EntityType.Sample),
        DeriveSamples("derivation sample type parameters", "Derived samples", WorkEntity.EntityType.Sample),
        AliquotSamples("aliquot sample type parameters", "Aliquot samples", WorkEntity.EntityType.Sample),
        PoolSamples("pooling sample type parameters", "Pooled samples", WorkEntity.EntityType.Sample),
        AddToStorage("input parameters", "Added samples to storage", WorkEntity.EntityType.Sample),
        MoveInStorage("input parameters", "Moved samples in storage", WorkEntity.EntityType.Sample),
        CheckOut("input parameters", "Checked out samples", WorkEntity.EntityType.Sample),
        CheckIn("input parameters", "Checked in samples", WorkEntity.EntityType.Sample),
        RemoveFromStorage("sample status value", "Removed samples from storage", WorkEntity.EntityType.Sample),
        UpdateSampleStatus("sample status value", "Updated sample status", WorkEntity.EntityType.Sample),
        DeriveSamplesFromSources("derivation source type parameters", "Derived samples from sources", WorkEntity.EntityType.Source),
        DeriveSources("derivation source type parameters", "Derived sources", WorkEntity.EntityType.Source);

        private final String _inputDescription;
        private final String _auditMessage;
        private final WorkEntity.EntityType _inputEntityType;

        ActionType(String inputDescription, String auditMessage,  WorkEntity.EntityType inputEntityType)
        {
            _inputDescription = inputDescription;
            _auditMessage = auditMessage;
            _inputEntityType = inputEntityType;
        }

        public String getInputDescription()
        {
            return _inputDescription;
        }

        public String getAuditMessage()
        {
            return _auditMessage;
        }

        public WorkEntity.EntityType getInputEntityType()
        {
            return _inputEntityType;
        }
    }

    static void setInstance(WorkflowService impl)
    {
        ServiceRegistry.get().registerService(WorkflowService.class, impl);
    }

    static WorkflowService get()
    {
        return ServiceRegistry.get().getService(WorkflowService.class);
    }

    void populateConfigParams(Map<String, Object> provided, Map<Enum, Object> configParameters) throws ValidationException;

    void populateConfigParams(HttpServletRequest request, Map<Enum, Object> configParameters) throws ValidationException;
    Map<String, Object> getConfigParameters(HttpServletRequest request) throws ValidationException;
    void onActionComplete(@NotNull Container container, @NotNull User user, @NotNull Long actionId, @Nullable String userAuditComment);
    void onActionComplete(@NotNull Container container, @NotNull User user, @NotNull Long taskId, @NotNull ActionType actionType);
    boolean actionWillAddSamples(Long actionId);
    boolean actionWillAddSources(Long actionId);

    boolean isTaskAssayType(Long taskId, Long assayId);

    DataIteratorBuilder getSampleCreationDataIteratorBuilder(DataIteratorBuilder data, Container container, User user);

    DataIteratorBuilder getSourceCreationDataIteratorBuilder(DataIteratorBuilder data, Container container, User user);

    DataIteratorBuilder getActionAuditDataIteratorBuilder(DataIteratorBuilder data, Container container, User user);

    @Nullable
    Job getJob(Long jobId);

    @Nullable
    Job getELNReferencePlaceholderJob(Container container);
}
