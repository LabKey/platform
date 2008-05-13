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

/**
 * User: jeckels
 * Date: Nov 22, 2005
 */
public class ProtocolActionPredecessor
{
    private String _parentProtocolLSID;
    private String _childProtocolLSID;
    private int _actionSequence;
    private String _predecessorParentLSID;
    private String _predecessorChildLSID;
    private int _predecessorSequence;

    public int getPredecessorSequence()
    {
        return _predecessorSequence;
    }

    public void setPredecessorSequence(int predecessorSequence)
    {
        _predecessorSequence = predecessorSequence;
    }

    public String getPredecessorChildLSID()
    {
        return _predecessorChildLSID;
    }

    public void setPredecessorChildLSID(String predecessorChildLSID)
    {
        _predecessorChildLSID = predecessorChildLSID;
    }

    public String getPredecessorParentLSID()
    {
        return _predecessorParentLSID;
    }

    public void setPredecessorParentLSID(String predecessorParentLSID)
    {
        _predecessorParentLSID = predecessorParentLSID;
    }

    public int getActionSequence()
    {
        return _actionSequence;
    }

    public void setActionSequence(int actionSequence)
    {
        _actionSequence = actionSequence;
    }

    public String getChildProtocolLSID()
    {
        return _childProtocolLSID;
    }

    public void setChildProtocolLSID(String childProtocolLSID)
    {
        _childProtocolLSID = childProtocolLSID;
    }

    public String getParentProtocolLSID()
    {
        return _parentProtocolLSID;
    }

    public void setParentProtocolLSID(String parentProtocolLSID)
    {
        _parentProtocolLSID = parentProtocolLSID;
    }
}
