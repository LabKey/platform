/*
 * Copyright (c) 2006-2015 LabKey Corporation
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

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;

import java.util.List;

/**
 * A collection of {@link ExpMaterial}, with a custom Domain for additional properties.
 * Material version of {@link ExpDataClass}
 */
public interface ExpSampleSet extends ExpObject
{
    String getMaterialLSIDPrefix();


    /** pass in a container to request a sample */
    @Deprecated
    List<? extends ExpMaterial> getSamples();
    List<? extends ExpMaterial> getSamples(Container c);

    /** pass in a container to request a sample */
    @Deprecated
    ExpMaterial getSample(String name);
    ExpMaterial getSample(Container c, String name);

    @NotNull
    Domain getType();

    String getDescription();

    /**
     * Some sample sets shouldn't be updated through the standard import or derived samples
     * UI, as they don't have any properties. Study specimens are an example.
     */
    boolean canImportMoreSamples();

    /** @return true if either using 'Name' as the Id column or uses at least one property for the unique id column. */
    boolean hasIdColumns();

    /** @return true if using 'Name' as the Id column.  getIdCol1(), getIdCol2() and getIdCol3() will all be null. */
    boolean hasNameAsIdCol();

    /** @return property that determines the first part of the sample set's sample's keys.  Will be null if using 'Name' as the Id column. */
    @Nullable
    DomainProperty getIdCol1();

    /** @return property that determines the second part of the sample set's sample's keys */
    @Nullable
    DomainProperty getIdCol2();

    /** @return property that determines the third part of the sample set's sample's keys */
    @Nullable
    DomainProperty getIdCol3();

    /** @return column that contains parent sample names */
    @Nullable
    DomainProperty getParentCol();

    void setDescription(String s);

    void setMaterialLSIDPrefix(String s);

    List<DomainProperty> getIdCols();
}
