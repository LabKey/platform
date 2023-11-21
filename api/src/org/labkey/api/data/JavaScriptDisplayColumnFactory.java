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
import org.apache.logging.log4j.Logger;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.logging.LogHelper;

import java.util.Collection;

/**
 * Factory for {@link JavaScriptDisplayColumn} to let them be wired up for a {@link ColumnInfo}.
 */
public class JavaScriptDisplayColumnFactory implements DisplayColumnFactory
{
    private static final Logger LOG = LogHelper.getLogger(JavaScriptDisplayColumnFactory.class, "Warnings about unsupported property");
    private final @Nullable Collection<String> _dependencies;
    private final @Nullable String _onClickJavaScript;

    public JavaScriptDisplayColumnFactory(MultiValuedMap<String, String> properties)
    {
        _dependencies = properties.get("dependency");
        Collection<String> onClicks = properties.get("onclick");
        if (onClicks.size() > 1)
            LOG.error("More than one \"onclick\" element was specified; only the first one will be used.");
        _onClickJavaScript = !onClicks.isEmpty() ? onClicks.stream().findFirst().get() : null;
        if (properties.containsKey("javaScriptEvents"))
            LOG.error("The \"javaScriptEvents\" property is no longer supported! Use the \"onclick\" property instead.");
    }

    @Override
    public DisplayColumn createRenderer(ColumnInfo colInfo)
    {
        return new JavaScriptDisplayColumn(colInfo, _dependencies, _onClickJavaScript, null);
    }
}
