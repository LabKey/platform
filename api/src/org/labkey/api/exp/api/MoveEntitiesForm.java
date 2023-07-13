package org.labkey.api.exp.api;

import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.view.DataViewSnapshotSelectionForm;

public class MoveEntitiesForm extends DataViewSnapshotSelectionForm
{
    private String _targetContainer;
    private String _userComment;
    private AuditBehaviorType _auditBehavior;

    public String getTargetContainer()
    {
        return _targetContainer;
    }

    public void setTargetContainer(String targetContainer)
    {
        _targetContainer = targetContainer;
    }

    public String getUserComment()
    {
        return _userComment;
    }

    public void setUserComment(String userComment)
    {
        _userComment = userComment;
    }

    public AuditBehaviorType getAuditBehavior()
    {
        return _auditBehavior;
    }

    public void setAuditBehavior(AuditBehaviorType auditBehavior)
    {
        _auditBehavior = auditBehavior;
    }

}
