/*
 * Copyright (c) 2006-2013 LabKey Corporation
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

package org.labkey.api.exp.xar;

import java.util.Map;
import java.util.HashMap;

/**
 * User: jeckels
 * Date: Jan 19, 2006
 */
public class MapReplacer implements Replacer
{
    private final Map<String, String> _replacements;

    public MapReplacer()
    {
        _replacements = new HashMap<>();
    }

    public MapReplacer(Map<String, String> replacements)
    {
        _replacements = new HashMap<>(replacements);
    }

    public String getReplacement(String original)
    {
        return _replacements.get(original);
    }

    public void addReplacement(String original, String replacement)
    {
        _replacements.put(original, replacement);
    }
}
