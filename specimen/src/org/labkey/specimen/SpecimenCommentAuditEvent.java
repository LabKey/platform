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
package org.labkey.specimen;

import org.labkey.api.audit.AuditTypeEvent;
import org.labkey.api.data.Container;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.labkey.specimen.SpecimenCommentAuditDomainKind.SPECIMEN_COMMENT_EVENT;

public class SpecimenCommentAuditEvent extends AuditTypeEvent
{
    private String _vialId;

    public SpecimenCommentAuditEvent()
    {
        super();
    }

    public SpecimenCommentAuditEvent(Container container, String comment)
    {
        super(SPECIMEN_COMMENT_EVENT, container, comment);
    }

    public String getVialId()
    {
        return _vialId;
    }

    public void setVialId(String vialId)
    {
        _vialId = vialId;
    }

    @Override
    public Map<String, Object> getAuditLogMessageElements()
    {
        Map<String, Object> elements = new LinkedHashMap<>();
        elements.put("vialId", getVialId());
        elements.putAll(super.getAuditLogMessageElements());
        return elements;
    }
}
