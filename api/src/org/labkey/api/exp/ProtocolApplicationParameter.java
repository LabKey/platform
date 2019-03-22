/*
 * Copyright (c) 2005-2010 LabKey Corporation
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
package org.labkey.api.exp;

/**
 * User: jeckels
 * Date: Sep 28, 2005
 */
public class ProtocolApplicationParameter extends AbstractParameter
{

    private Integer _runId;
    private Integer _protocolApplicationId;

    public Integer getRunId()
    {
        return _runId;
    }

    public void setRunId(Integer runId)
    {
        _runId = runId;
    }

    public Integer getProtocolApplicationId()
    {
        return _protocolApplicationId;
    }

    public void setProtocolApplicationId(Integer protocolApplicationId)
    {
        _protocolApplicationId = protocolApplicationId;
    }
}
