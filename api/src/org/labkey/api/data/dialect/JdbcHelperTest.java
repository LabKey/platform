/*
 * Copyright (c) 2011 LabKey Corporation
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

package org.labkey.api.data.dialect;

import org.jetbrains.annotations.NotNull;
import org.junit.Assert;

import javax.servlet.ServletException;
import java.util.Set;

/*
* User: adam
* Date: Jul 15, 2011
* Time: 1:49:06 PM
*/
public abstract class JdbcHelperTest extends Assert
{
    protected abstract @NotNull SqlDialect getDialect();
    protected abstract @NotNull Set<String> getGoodUrls();
    protected abstract @NotNull Set<String> getBadUrls();

    public void test()
    {
        SqlDialect dialect = getDialect();
        JdbcHelper helper = dialect.getJdbcHelper();

        try
        {
            for (String url : getGoodUrls())
                if (!helper.getDatabase(url).equals("database"))
                    fail("JdbcHelper test failed: database in " + url + " did not resolve to 'database'");
        }
        catch (Exception e)
        {
            fail("Exception running JdbcHelper test: " + e.getMessage());
        }

        for (String url : getBadUrls())
        {
            try
            {
                if (helper.getDatabase(url).equals("database"))
                    fail("JdbcHelper test failed: database in " + url + " should not have resolved to 'database'");
            }
            catch (ServletException e)
            {
                // Skip -- we expect to fail on these
            }
        }
    }
}
