/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.security.User;

import java.io.File;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.sql.SQLException;

public interface ExpRun extends ExpObject
{
    public ExpExperiment[] getExperiments();
    public ExpProtocol getProtocol();
    public ExpData[] getOutputDatas(DataType type);
    public ExpData[] getInputDatas(String inputRole, ExpProtocol.ApplicationType appType);
    public String getFilePathRoot();
    public void setFilePathRoot(File filePathRoot);
    public void save(User user);
    public void setProtocol(ExpProtocol protocol);
    public ExpProtocolApplication addProtocolApplication(User user, ExpProtocolAction action, ExpProtocol.ApplicationType type, String name);

    String getComments();

    void setComments(String comments);

    void setEntityId(String entityId);

    Map<ExpMaterial, String> getMaterialInputs();

    Map<ExpData, String> getDataInputs();

    List<ExpMaterial> getMaterialOutputs();

    List<ExpData> getDataOutputs();

    ExpProtocolApplication[] getProtocolApplications();

    /** Get the protocol application that marks all of the inputs to the run as a whole */
    ExpProtocolApplication getInputProtocolApplication();

    /** Get the protocol application that marks all of the outputs to the run as a whole */
    ExpProtocolApplication getOutputProtocolApplication();

    /**
     * @return Map from PropertyURI to ObjectProperty
     */
    Map<String, ObjectProperty> getObjectProperties();

}
