package org.labkey.study.model;

import org.labkey.api.data.Entity;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Jun 10, 2011
 * Time: 3:16:27 PM
 */
public class ParticipantGroup extends Entity
{
    private int _rowId;
    private String _label;
    private int _classificationId;  // fk to participant classification

    private List<String> _participantIds = new ArrayList<String>();

    public boolean isNew()
    {
        return _rowId == 0;
    }

    public int getRowId()
    {
        return _rowId;
    }

    public void setRowId(int rowId)
    {
        _rowId = rowId;
    }

    public String getLabel()
    {
        return _label;
    }

    public void setLabel(String label)
    {
        _label = label;
    }

    public int getClassificationId()
    {
        return _classificationId;
    }

    public void setClassificationId(int classificationId)
    {
        _classificationId = classificationId;
    }

    public List<String> getParticipantIds()
    {
        return _participantIds;
    }

    public void setParticipantIds(List<String> participantIds)
    {
        _participantIds = participantIds;
    }

    public void addParticipantId(String participantId)
    {
        _participantIds.add(participantId);
    }
}
