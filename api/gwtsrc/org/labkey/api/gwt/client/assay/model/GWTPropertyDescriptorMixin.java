/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.api.gwt.client.assay.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.labkey.api.gwt.client.util.StringProperty;

/**
 * Configures the fields that are not returned when serializing a GWTPropertyDescriptor.
 * Ideally we would just add the @JsonIgnore annotations to GWTPropertyDescriptor directly,
 * but the GWT compiler would need to have jackson on the classpath which isn't
 * necessary.
 */
@JsonIgnoreProperties({
        "setMeasure",
        "setDimension",
        "setExcludeFromShifting",
        "lookupDescription",
        "fileType",
        "updatedField",
        "newField",
        "renderUpdate"
})
public abstract class GWTPropertyDescriptorMixin
{
    GWTPropertyDescriptorMixin(@JsonProperty("PHI") StringProperty phi, @JsonProperty("URL") StringProperty url)
    { }
    @JsonProperty("PHI")
    abstract void setPHI(String phi); // rename property on deserialize
    @JsonProperty("URL")
    abstract void setURL(String url); // rename property on deserialize
}
