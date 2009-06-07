package org.labkey.api.study.assay;

import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.security.ACL;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * User: jeckels
* Date: Jun 6, 2009
*/
public class StudyContainerFilter extends ContainerFilter
{
    private Set<String> _ids;
    private final AssaySchema _schema;

    public StudyContainerFilter(AssaySchema schema)
    {

        _schema = schema;
    }

    public Collection<String> getIds(Container currentContainer)
    {
        if (_ids == null)
        {
            if (_schema.getTargetStudy() != null)
            {
                _ids = Collections.singleton(_schema.getTargetStudy().getId());
            }
            else
            {
                _ids = ContainerFilter.toIds(AssayPublishService.get().getValidPublishTargets(_schema.getUser(), ACL.PERM_READ).keySet());
            }
        }
        return _ids;
    }

    public Type getType()
    {
        throw new UnsupportedOperationException();
    }
}
