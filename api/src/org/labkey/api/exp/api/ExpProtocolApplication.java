/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

import java.util.Date;
import java.util.List;

/**
 * An individual step inside of an {@link ExpRun}, which can consume or produce {@link ExpData} or {@link ExpMaterial}
 */
public interface ExpProtocolApplication extends ExpObject
{
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
    void removeDataInput(User user, ExpData data);

    /**
     * Add a material input
     * @param inputRole optional argument specifying the input role name
     * @return the newly inserted MaterialInput edge
     */
    @NotNull ExpMaterialRunInput addMaterialInput(User user, ExpMaterial material, @Nullable String inputRole);
    void removeMaterialInput(User user, ExpMaterial material);

    ExpRun getRun();
    int getActionSequence();
    ExpProtocol.ApplicationType getApplicationType();

    Date getActivityDate();

    Date getStartTime();

    Date getEndTime();

    Integer getRecordCount();

    String getComments();

    void setRun(ExpRun run);

    void setActionSequence(int actionSequence);

    void setProtocol(ExpProtocol protocol);

    void setActivityDate(Date date);

    void setStartTime(Date date);

    void setEndTime(Date date);

    void setRecordCount(Integer recordCount);
    
    void save(User user);
}
