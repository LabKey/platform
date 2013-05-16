/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

package org.labkey.api.exp.list;

import org.labkey.api.data.Container;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;

import java.io.IOException;
import java.sql.SQLException;

public interface ListItem
{
    public Object getKey();
    public void setKey(Object key);

    public String getEntityId();
    public void setEntityId(String entityId);

    public Object getProperty(DomainProperty property);
    public void setProperty(DomainProperty property, Object value);

    public void save(User user) throws SQLException, IOException, ValidationException;
    public void save(User user, boolean isBulkLoad) throws SQLException, IOException, ValidationException;
    public void delete(User user, Container c) throws SQLException;
    public void delete(User user, Container c, boolean isBulkLoad) throws SQLException;
}
