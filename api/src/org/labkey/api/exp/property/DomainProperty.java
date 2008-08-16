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

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.security.User;
import org.labkey.api.view.ActionURL;

public interface DomainProperty
{
    int getPropertyId();
    Container getContainer();
    String getPropertyURI();
    String getRangeURI();
    String getName();
    String getDescription();
    String getFormatString();
    String getLabel();
    ActionURL detailsURL();
    
    Domain getDomain();
    IPropertyType getType();
    boolean isRequired();

    void delete();

    void setName(String name);
    void setDescription(String description);
    void setLabel(String caption);
    void setType(IPropertyType type);
    void setPropertyURI(String uri);
    void setRangeURI(String uri);
    void setFormat(String s);
    void setRequired(boolean b);

    void initColumn(User user, ColumnInfo column);

    SQLFragment getValueSQL();
    int getSqlType();
    int getScale();
    String getInputType();

    Lookup getLookup();

    void setLookup(Lookup lookup);

    @Deprecated
    PropertyDescriptor getPropertyDescriptor();

    IPropertyValidator[] getValidators();
    void addValidator(IPropertyValidator validator);
    void removeValidator(IPropertyValidator validator);
    void removeValidator(int validatorId);
}
