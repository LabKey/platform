/*
 * Copyright (c) 2006-2008 LabKey Corporation
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

import org.labkey.api.exp.XarFormatException;

/**
 * User: jeckels
 * Date: Jan 13, 2006
 */
public interface Replacer
{
    public String getReplacement(String original) throws XarFormatException;

    public static class CompoundReplacer implements Replacer
    {
        private final Replacer _replacer1;
        private final Replacer _replacer2;

        public CompoundReplacer(Replacer replacer1, Replacer replacer2)
        {
            _replacer1 = replacer1;
            _replacer2 = replacer2;
        }

        public String getReplacement(String original) throws XarFormatException
        {
            String result = _replacer1.getReplacement(original);
            if (result != null)
            {
                return result;
            }
            return _replacer2.getReplacement(original);
        }
    }
}
