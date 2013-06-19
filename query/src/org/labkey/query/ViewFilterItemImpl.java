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

package org.labkey.query;

import org.labkey.api.query.ViewOptions;

/**
 * User: klum
 * Date: Aug 21, 2009
 */
public class ViewFilterItemImpl implements ViewOptions.ViewFilterItem
{
    private String _type;
    private boolean _enabled;

    public ViewFilterItemImpl(String type, boolean enabled)
    {
        _type = type;
        _enabled = enabled;
    }

    public String getViewType()
    {
        return _type;
    }

    public boolean isEnabled()
    {
        return _enabled;
    }
}
