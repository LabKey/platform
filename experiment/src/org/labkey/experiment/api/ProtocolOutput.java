/*
 * Copyright (c) 2005-2008 LabKey Corporation
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
package org.labkey.experiment.api;

import org.labkey.api.exp.IdentifiableBase;

/**
 * Output of a protocol, like a data file or a material
 * User: jeckels
 * Date: Oct 17, 2005
 */
public abstract class ProtocolOutput extends IdentifiableBase
{
    public abstract Integer getSourceApplicationId();

    public abstract void setSourceApplicationId(Integer sourceApplicationId);

    public abstract Integer getRunId();

    public abstract void setRunId(Integer runId);

    public abstract String getSourceProtocolLSID();

    public abstract void setSourceProtocolLSID(String s);

    public abstract int getRowId();

    public abstract void setCpasType(String cpasType);

    public abstract String getCpasType();

    public abstract String getContainer();

    public abstract void setContainer(String container);
}
