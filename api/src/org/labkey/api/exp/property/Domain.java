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

package org.labkey.api.exp.property;

import org.labkey.api.security.User;
import org.labkey.api.data.Container;
import org.labkey.api.view.ActionURL;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.DomainNotFoundException;

import java.sql.SQLException;

public interface Domain extends IPropertyType
{
    Container getContainer();
    DomainKind getDomainKind();
    String getName();
    String getDescription();
    int getTypeId();

    Container[] getInstanceContainers();
    Container[] getInstanceContainers(User user, int perm);

    void setDescription(String description);
    DomainProperty[] getProperties();
    DomainProperty getProperty(int id);
    DomainProperty getPropertyByName(String name);
    ActionURL urlEditDefinition(boolean allowFileLinkProperties, boolean allowAttachmentProperties);
    ActionURL urlShowData();

    DomainProperty addProperty();

    void delete(User user) throws DomainNotFoundException;
    void save(User user) throws ChangePropertyDescriptorException;
}
