/*
 * Copyright (c) 2020-2026 LabKey Corporation
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
package org.labkey.api.ontology;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.URLHelper;

public interface Concept
{
    @JsonIgnore
    OntologyProvider getProvider();

    @JsonIgnore
    Ontology getOntology();

    String getCode();

    String getLabel();

    @Nullable String getDescription();

    @Nullable URLHelper getURL();

    @JsonProperty("ontology")
    default String getOntologyAbbreviation()
    {
        Ontology ontology = getOntology();
        return ontology == null ? null : getOntology().getAbbreviation();
    }
}
