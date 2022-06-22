package org.labkey.experiment.api;

import org.labkey.api.exp.property.ConceptURIVocabularyDomainProvider;

public record DataClassVocabularyProviderProperties(String sourceColumnName /* the dataclass column that has attached vocabulary*/, String sourceColumnLabel /* the label of the dataclass column */, String vocabularyDomainName /* vocabulary property column field key */, ConceptURIVocabularyDomainProvider conceptURIVocabularyDomainProvider /*the ConceptURIVocabularyDomainProvider that matches the column's conceptURI*/)
{
}
