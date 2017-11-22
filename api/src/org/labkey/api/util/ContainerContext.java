/*
 * Copyright (c) 2009-2013 LabKey Corporation
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
package org.labkey.api.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.RenderContext;
import org.labkey.api.query.FieldKey;

import java.util.Map;

/**
 * User: matthewb
 * Date: Sep 27, 2009
 *
 * This is a helper class for DetailsURL.  Rather than needing to subclass DetailsURL to provide a
 * container value, you may provide a ContainerContext instead.
 */
public interface ContainerContext
{
    Container getContainer(Map context);

    /** Determines the container to use based on the value of a column in the results for each row */
    class FieldKeyContext implements ContainerContext
    {
        @NotNull
        final FieldKey _key;

        public FieldKeyContext(@NotNull FieldKey key)
        {
            _key = key;
        }

        public FieldKeyContext copy()
        {
            return new FieldKeyContext(_key);
        }

        @Override
        public Container getContainer(Map context)
        {
            if (context == null)
                return null;

            Object o = context.get(_key);
            if (null == o && null == _key.getParent())
                o = context.get(_key.getName());
            if (o instanceof Container)
                return (Container)o;
            if (o instanceof String)
                return ContainerManager.getForId((String)o);
            if (o instanceof Integer)
                return ContainerManager.getForRowId((Integer)o);

            // We couldn't resolve a container in the row of data we're rendering, so fall back to the current container
            // for the request in general, if available. This can happen if a custom query doesn't pull a Container
            // column from the source table to make available in the query's results.
            if (context instanceof RenderContext)
                return ((RenderContext)context).getViewContext().getContainer();

            return null;
        }

        public FieldKey getFieldKey()
        {
            return _key;
        }
    }
}
