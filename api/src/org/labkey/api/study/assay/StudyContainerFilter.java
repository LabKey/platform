/*
 * Copyright (c) 2009-2017 LabKey Corporation
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
package org.labkey.api.study.assay;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.Study;
import org.labkey.api.util.GUID;

import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * User: jeckels
* Date: Jun 6, 2009
*/
public class StudyContainerFilter extends ContainerFilter
{
    private Collection<GUID> _ids;
    private final AssaySchema _schema;

    public StudyContainerFilter(AssaySchema schema)
    {

        _schema = schema;
    }

    public Collection<GUID> getIds(Container currentContainer)
    {
        if (_ids == null)
        {
            if (_schema.getUser().hasRootAdminPermission())
            {
                // Administrators can see everything
                return null;
            }
            
            if (_schema.getTargetStudy() != null)
            {
                if (_schema.getTargetStudy().hasPermission(_schema.getUser(), ReadPermission.class))
                    _ids = Collections.singleton(_schema.getTargetStudy().getEntityId());
                else
                    return Collections.emptySet();
            }
            else
            {
                Set<Study> studies = AssayPublishService.get().getValidPublishTargets(_schema.getUser(), ReadPermission.class);
                Set<GUID> ids = new HashSet<>();
                for (Study study : studies)
                    ids.add(study.getContainer().getEntityId());
                _ids = ids;
            }
        }
        return _ids;
    }

    public Type getType()
    {
        return null;
    }
}
