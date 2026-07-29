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
        AssayImport("assay types", "Imported assay data"),
        DeriveSamples("derivation sample type parameters", "Derived samples"),
        AliquotSamples("aliquot sample type parameters", "Aliquot samples"),
        PoolSamples("pooling sample type parameters", "Pooled samples"),
        AddToStorage("input parameters", "Added samples to storage"),
        MoveInStorage("input parameters", "Moved samples in storage"),
        CheckOut("input parameters", "Checked out samples"),
        CheckIn("input parameters", "Checked in samples"),
        RemoveFromStorage("sample status value", "Removed samples from storage"),
        UpdateSampleStatus("sample status value", "Updated sample status"),
        DeriveSamplesFromSources("derivation source type parameters", "Derived samples from sources"),
        DeriveSources("derivation source type parameters", "Derives sources");

        private final String _inputDescription;
        private final String _auditMessage;

        ActionType(String inputDescription, String auditMessage)
        {
            _inputDescription = inputDescription;
            _auditMessage = auditMessage;
        }

        public String getInputDescription()
        {
            return _inputDescription;
        }

        public String getAuditMessage()
        {
            return _auditMessage;
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

    DataIteratorBuilder getSampleCreationDataIteratorBuilder(DataIteratorBuilder data, Container container, User user);

    DataIteratorBuilder getSourceCreationDataIteratorBuilder(DataIteratorBuilder data, Container container, User user);

    DataIteratorBuilder getActionAuditDataIteratorBuilder(DataIteratorBuilder data, Container container, User user);

    @Nullable
    Job getJob(Long jobId);

    @Nullable
    Job getELNReferencePlaceholderJob(Container container);
}
