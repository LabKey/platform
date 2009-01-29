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

package org.labkey.api.exp.api;

import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.security.User;

import java.util.List;

public interface ExpSampleSet extends ExpObject
{
    public String getMaterialLSIDPrefix();

    public PropertyDescriptor[] getPropertiesForType();

    public ExpMaterial[] getSamples();

    public Domain getType();

    public String getDescription();

    /**
     * Some sample sets shouldn't be updated through the standard import or derived samples
     * UI, as they don't have any properties. Study specimens are an example.
     */
    public boolean canImportMoreSamples();

    public boolean hasIdColumns();

    public PropertyDescriptor getIdCol1();
    public PropertyDescriptor getIdCol2();
    public PropertyDescriptor getIdCol3();
    public PropertyDescriptor getParentCol();

    void setDescription(String s);

    void setMaterialLSIDPrefix(String s);

    void insert(User user);

    List<PropertyDescriptor> getIdCols();

    void setIdCols(List<PropertyDescriptor> pds);
}
