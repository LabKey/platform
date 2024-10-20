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

package org.labkey.api.exp.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.security.User;
import org.labkey.api.util.GUID;
import org.labkey.api.util.Pair;

import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Set;

/**
 * An individual step inside an {@link ExpRun}, which can consume or produce {@link ExpData} or {@link ExpMaterial}
 */
public interface ExpProtocolApplication extends ExpObject
{
    String DEFAULT_CPAS_TYPE = "ProtocolApplication";

    @NotNull
    List<? extends ExpDataRunInput> getDataInputs();
    @NotNull
    List<? extends ExpDataRunInput> getDataOutputs();
    @NotNull
    List<? extends ExpData> getInputDatas();
    @NotNull
    List<? extends ExpData> getOutputDatas();
    @NotNull
    List<? extends ExpMaterialRunInput> getMaterialInputs();
    @NotNull
    List<? extends ExpMaterialRunInput> getMaterialOutputs();
    @NotNull
    List<? extends ExpMaterial> getInputMaterials();

    @NotNull
    List<? extends ExpMaterial> getOutputMaterials();

    ExpProtocol getProtocol();

    /**
     * Add a data input
     * @param inputRole optional argument specifying the input role name
     * @return the newly inserted DataInput edge
     */
    @NotNull ExpDataRunInput addDataInput(User user, ExpData input, String inputRole);
    @NotNull ExpDataRunInput addDataInput(User user, ExpData input, String inputRole, @Nullable ExpDataProtocolInput protocolInput);
    void removeAllDataInputs(User user);
    void removeDataInput(User user, ExpData data);
    void removeDataInputs(User user, Collection<Integer> rowIds);

    /**
     * Add a material input
     * @param inputRole optional argument specifying the input role name
     * @return the newly inserted MaterialInput edge
     */
    @NotNull ExpMaterialRunInput addMaterialInput(User user, ExpMaterial material, @Nullable String inputRole);
    @NotNull ExpMaterialRunInput addMaterialInput(User user, ExpMaterial material, @Nullable String inputRole, @Nullable ExpMaterialProtocolInput protocolInput);
    void addMaterialInputs(User user, Collection<Integer> materialRowIds, @Nullable String inputRole, @Nullable ExpMaterialProtocolInput protocolInput);
    void removeAllMaterialInputs(User user);
    void removeMaterialInput(User user, ExpMaterial material);
    void removeMaterialInputs(User user, Collection<Integer> rowIds);

    void addProvenanceInput(Set<String> lsids);
    void addProvenanceMapping(Set<Pair<String, String>> lsidPairs);
    Set<Pair<String, String>> getProvenanceMapping();

    ExpRun getRun();
    int getActionSequence();
    ExpProtocol.ApplicationType getApplicationType();

    Date getActivityDate();

    Date getStartTime();

    Date getEndTime();

    Integer getRecordCount();

    String getComments();

    GUID getEntityId();

    void setRun(ExpRun run);

    void setActionSequence(int actionSequence);

    void setProtocol(ExpProtocol protocol);

    void setActivityDate(Date date);

    void setStartTime(Date date);

    void setEndTime(Date date);

    void setRecordCount(Integer recordCount);

    void setComments(String comments);

    void setEntityId(GUID entityId);

    /** Override to signal that we never throw BatchValidationExceptions */
    @Override
    void save(User user);

}
