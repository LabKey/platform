/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.api.gwt.client.util;

/**
 * User: brittp
* Date: Feb 5, 2007
* Time: 6:32:56 PM
*/
public class ColorGenerator
{
    private static final String[] SUBCOLOR_STRINGS = new String[]{"66", "AA", "EE", "88", "CC"};
    private int _r = 0;
    private int _g = 1;
    private int _b = 2;

    public ColorGenerator()
    {

    }

    public ColorGenerator(int startColor)
    {
        for (int i = 0; i < startColor; i++)
            advance();
    }

    public String next()
    {
        advance();
        return SUBCOLOR_STRINGS[_r % SUBCOLOR_STRINGS.length] +
               SUBCOLOR_STRINGS[_g % SUBCOLOR_STRINGS.length] +
               SUBCOLOR_STRINGS[_b % SUBCOLOR_STRINGS.length];
    }

    private void advance()
    {
        do
        {
            _r = (_r + 1) % SUBCOLOR_STRINGS.length;
            if (_r % 2 == 0)
            {
                _g = (_g + 1) % SUBCOLOR_STRINGS.length;
                if (_g % 3 == 0)
                    _b = (_b + 1) % SUBCOLOR_STRINGS.length;
            }
        }
        while (_r == _g && _g == _b);
    }
}
