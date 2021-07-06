package org.labkey.specimen.actions;

import org.labkey.api.study.EditDatasetRowForm;

public class ParticipantCommentForm extends EditDatasetRowForm
{
    private String _participantId;
    private int _visitId;
    private String _comment;
    private String _oldComment;
    private int[] _vialCommentsToClear = new int[0];

    public enum params
    {
        participantId,
        visitId,
        comment,
        oldComment,
        vialCommentsToClear,
        lsid,
        datasetId,
        returnUrl,
    }

    public String getParticipantId()
    {
        return _participantId;
    }

    public void setParticipantId(String participantId)
    {
        _participantId = participantId;
    }

    public int getVisitId()
    {
        return _visitId;
    }

    public void setVisitId(int visitId)
    {
        _visitId = visitId;
    }

    public String getComment()
    {
        return _comment;
    }

    public void setComment(String comment)
    {
        _comment = comment;
    }

    public String getOldComment()
    {
        return _oldComment;
    }

    public void setOldComment(String oldComment)
    {
        _oldComment = oldComment;
    }

    public int[] getVialCommentsToClear()
    {
        return _vialCommentsToClear;
    }

    public void setVialCommentsToClear(int[] vialsCommentsToClear)
    {
        _vialCommentsToClear = vialsCommentsToClear;
    }
}
