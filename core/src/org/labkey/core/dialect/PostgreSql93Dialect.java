/*
 * Copyright (c) 2013-2017 LabKey Corporation
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

package org.labkey.core.dialect;

import org.jetbrains.annotations.NotNull;

import java.util.Set;

/*
* User: adam
* Date: Jun 14, 2013
* Time: 8:50:00 AM
*/
public class PostgreSql93Dialect extends PostgreSql92Dialect
{
    public PostgreSql93Dialect()
    {
    }

    public PostgreSql93Dialect(boolean standardConformingStrings)
    {
        super(standardConformingStrings);
    }

    @NotNull
    @Override
    protected Set<String> getReservedWords()
    {
        Set<String> words = super.getReservedWords();
        words.add("lateral");

        return words;
    }

//  Uncomment when it's time to deprecate 9.3
//    @Override
//    public void addAdminWarningMessages(Collection<String> messages)
//    {
//        messages.add("LabKey Server no longer supports " + getProductName() + " " + getProductVersion() + "; please upgrade. " + PostgreSqlDialectFactory.RECOMMENDED);
//    }
//
}
