/*
 * Copyright (c) 2012-2016 LabKey Corporation
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
package org.labkey.api.laboratory;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.User;

import java.util.Collections;
import java.util.List;

/**
 * User: bimber
 * Date: 10/8/12
 * Time: 2:58 PM
 */
public abstract class AbstractDataProvider implements DataProvider
{
    @Override
    public String getKey()
    {
        return this.getClass().getName() + "||" +
                (getOwningModule() == null ? "" : getOwningModule().getName()) + "||" +
                getName()
                ;
    }

    public List<NavItem> getReportItems(Container c, User u)
    {
        return Collections.emptyList();
    }

    public List<TabbedReportItem> getTabbedReportItems(Container c, User u)
    {
        return Collections.emptyList();
    }

    public List<NavItem> getMiscItems(Container c, User u)
    {
        return Collections.emptyList();
    }

    protected ColumnInfo getSubjectColumn(TableInfo ti)
    {
        for (ColumnInfo ci : ti.getColumns())
        {
            if (LaboratoryService.PARTICIPANT_CONCEPT_URI.equalsIgnoreCase(ci.getConceptURI()))
                return ci;
        }

        return null;
    }
}
