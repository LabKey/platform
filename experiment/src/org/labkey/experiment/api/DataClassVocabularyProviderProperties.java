/*
 * Copyright (c) 2022-2026 LabKey Corporation
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
package org.labkey.experiment.api;

import org.labkey.api.exp.property.ConceptURIVocabularyDomainProvider;

public record DataClassVocabularyProviderProperties(String sourceColumnName /* the dataclass column that has attached vocabulary*/,
                                                    String sourceColumnLabel /* the label of the dataclass column */,
                                                    String vocabularyDomainName /* vocabulary property column field key */,
                                                    ConceptURIVocabularyDomainProvider conceptURIVocabularyDomainProvider /*the ConceptURIVocabularyDomainProvider that matches the column's conceptURI*/)
{
}
