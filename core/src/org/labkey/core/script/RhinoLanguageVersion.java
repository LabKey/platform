/*
 * Copyright (c) 2026 LabKey Corporation
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
package org.labkey.core.script;

import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.mozilla.javascript.Context;

import java.util.Arrays;

/** The Rhino language levels an administrator can choose for server-side scripts. */
public enum RhinoLanguageVersion
{
    JS_1_8(Context.VERSION_1_8, "JavaScript 1.8", "Legacy behavior, for scripts that are incompatible with modern JavaScript"),
    ES6(Context.VERSION_ES6, "ES6", "Modern JavaScript, without the checks that can break older scripts"),
    ECMASCRIPT(Context.VERSION_ECMASCRIPT, "ECMAScript", "Latest JavaScript, enforcing const and strict mode checks");

    public static final RhinoLanguageVersion DEFAULT = JS_1_8;

    private final int _version;
    private final String _label;
    private final String _description;

    RhinoLanguageVersion(int version, String label, String description)
    {
        _version = version;
        _label = label;
        _description = description;
    }

    /** One of the Context.VERSION_* constants */
    public int getVersion()
    {
        return _version;
    }

    public String getLabel()
    {
        return _label;
    }

    public String getDescription()
    {
        return _description;
    }

    public static @Nullable RhinoLanguageVersion fromVersion(@Nullable String version)
    {
        String trimmed = StringUtils.trimToNull(version);
        return Arrays.stream(values())
            .filter(v -> String.valueOf(v._version).equals(trimmed))
            .findFirst()
            .orElse(null);
    }
}
