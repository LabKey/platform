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
