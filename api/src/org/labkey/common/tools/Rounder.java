/*
 * Copyright (c) 2006-2008 Fred Hutchinson Cancer Research Center
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

package org.labkey.common.tools;

/**
 * Contains static utility methods for rounding
 */
public class Rounder
{
    //utility array
    protected static final double factors[] =
            {1, 10, 100, 1000, 10000, 100000, 1000000, 10000000, 100000000, 1000000000};

    /**
     * Round the double <n> to <places> decimal places.  If <places> is negative or greater than 9,
     * round to 9 decimal places
     * @param n
     * @param places
     * @return
     */
    public static double round(double n, int places)
    {
        if (places < 0 || places > 9) places = 9;

        return Math.round(n * factors[places]) / factors[places];
    }
}
