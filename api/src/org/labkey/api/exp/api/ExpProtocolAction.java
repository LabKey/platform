package org.labkey.api.exp.api;

import org.labkey.api.security.User;

public interface ExpProtocolAction
{
    int getRowId();
    ExpProtocol getParentProtocol();
    ExpProtocol getChildProtocol();
    ExpProtocolAction[] getPredecessors();
    ExpProtocolAction[] getSuccessors();
    int getActionSequence();

    void addSuccessor(User user, ExpProtocolAction successor) throws Exception;
}
