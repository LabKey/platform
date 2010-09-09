/*
 * Copyright (c) 2010 LabKey Corporation
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
package org.labkey.api.data;

import org.apache.commons.collections15.iterators.ArrayIterator;
import org.labkey.api.query.FieldKey;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * User: adam
 * Date: Sep 7, 2010
 * Time: 10:02:25 AM
 */
public class MultiValuedRenderContext extends RenderContextDecorator
{
    private final Map<FieldKey, Iterator<String>> _iterators = new HashMap<FieldKey, Iterator<String>>();
    private final Map<FieldKey, String> _currentValues = new HashMap<FieldKey, String>();

    public MultiValuedRenderContext(RenderContext ctx, Set<FieldKey> requiredFieldKeys)
    {
        super(ctx);

        // For each required column (e.g., display value, rowId), retrieve the concatenated values, split them, and
        // create stash away an iterator of those values.
        for (FieldKey fieldKey : requiredFieldKeys)
        {
            String valueString = (String)ctx.get(fieldKey);
            String[] values = valueString.split(",");
            _iterators.put(fieldKey, new ArrayIterator<String>(values));
        }
    }

    // Advance all the iterators, if another value is present.  Check that all iterators are in lock step.
    public boolean next()
    {
        Boolean previousHasNext = null;

        for (Map.Entry<FieldKey, Iterator<String>> entry : _iterators.entrySet())
        {
            Iterator<String> iter = entry.getValue();
            boolean hasNext = iter.hasNext();

            if (hasNext)
                _currentValues.put(entry.getKey(), iter.next());

            if (null == previousHasNext)
                previousHasNext = hasNext;
            else
                assert previousHasNext == hasNext;
        }

        return null != previousHasNext && previousHasNext;
    }

    @Override
    public Object get(Object key)
    {
        assert _currentValues.containsKey(key);

        return _currentValues.get(key);
    }
}
