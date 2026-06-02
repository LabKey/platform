/*
 * Copyright (c) 2021-2026 LabKey Corporation
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
