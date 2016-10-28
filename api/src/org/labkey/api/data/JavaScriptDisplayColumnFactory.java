/*
 * Copyright (c) 2013-2016 LabKey Corporation
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

import org.apache.commons.collections4.MultiValuedMap;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

/**
 * Factory for {@link JavaScriptDisplayColumn} to let them be wired up for a {@link ColumnInfo}.
 * User: adam
 * Date: 6/12/13
 */
public class JavaScriptDisplayColumnFactory implements DisplayColumnFactory
{
    private final @Nullable Collection<String> _dependencies;
    private final @Nullable String _javaScriptEvents;

    public JavaScriptDisplayColumnFactory(MultiValuedMap<String, String> properties)
    {
        _dependencies = properties.get("dependency");
        _javaScriptEvents = StringUtils.join(properties.get("javaScriptEvents"), " ");
    }

    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new JavaScriptDisplayColumn(colInfo, _dependencies, _javaScriptEvents);
    }
}
