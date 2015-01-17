/*
 * Copyright (c) 2013-2015 LabKey Corporation
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
package org.labkey.api.ldk.table;

import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.UserDefinedButtonConfig;
import org.labkey.api.security.User;
import org.labkey.api.view.NavTree;
import org.labkey.api.view.template.ClientDependency;

import java.util.Set;

/**
 * User: bimber
 * Date: 5/5/13
 * Time: 9:23 AM
 */
public interface ButtonConfigFactory
{
    public UserDefinedButtonConfig createBtn(TableInfo ti);

    public NavTree create(TableInfo ti);

    /**
     * @return true if the button is eligible to be added to the table's button bar. As this is invoked frequently,
     * it should be quick to return. If a detailed check is needed, use isVisible() which is invoked only at display
     * time
     */
    public boolean isAvailable(TableInfo ti);

    /**
     * @return true if the button should be visible in the table's button bar. This will be invoked only when
     * actually constructing
     */
    public boolean isVisible(TableInfo ti);

    public Set<ClientDependency> getClientDependencies(Container c, User u);
}
