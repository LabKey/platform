/*
 * Copyright (c) 2005-2026 Fred Hutchinson Cancer Research Center
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

/**
 * Bean class for the exp.protocolaction table.
 * User: migra
 * Date: Sep 9, 2005
 */
public class ProtocolAction
{
    private int _sequence;
    private long _rowId;
    private long _parentProtocolId;
    private long _childProtocolId;

    public int getSequence()
    {
        return _sequence;
    }

    public void setSequence(int sequence)
    {
        _sequence = sequence;
    }

    public long getRowId()
    {
        return _rowId;
    }

    public void setRowId(long rowId)
    {
        _rowId = rowId;
    }

    public long getParentProtocolId()
    {
        return _parentProtocolId;
    }

    public void setParentProtocolId(long parentProtocolId)
    {
        _parentProtocolId = parentProtocolId;
    }

    public long getChildProtocolId()
    {
        return _childProtocolId;
    }

    public void setChildProtocolId(long childProtocolId)
    {
        _childProtocolId = childProtocolId;
    }
}
