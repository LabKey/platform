/*
 * Copyright (c) 2004-2016 Fred Hutchinson Cancer Research Center
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
package org.labkey.api.collections;

import org.apache.commons.lang3.StringUtils;

import java.util.Arrays;
import java.util.LinkedHashSet;

public class CsvSet extends LinkedHashSet<String>
{
    public CsvSet(String str)
    {
        // Empty string results in empty set
        if (!StringUtils.isBlank(str))
            addAll(Arrays.asList(str.split(",\\s*")));
    }
} 
