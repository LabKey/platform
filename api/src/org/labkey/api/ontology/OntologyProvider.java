package org.labkey.api.ontology;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.util.Path;
import org.labkey.api.view.ActionURL;

import java.util.Collection;

public interface OntologyProvider
{
    @NotNull
    String getName();

    String getDescription();

    @NotNull
    ActionURL getCreateUrl();

    @Nullable
    default ActionURL getManageUrl(Ontology ont)
    {
        return null;
    }

    // TODO remove when there is a manage page
    @Nullable
    default ActionURL getImportUrl(Ontology ont)
    {
        return null;
    }

    @NotNull
    ActionURL getDeleteUrl(Ontology ont);

    // For browsing concepts these may be served by external API
    Collection<? extends ConceptPath> getRootPaths(Ontology ont);
    ConceptPath getPath(Path p);
    Collection<? extends ConceptPath> getChildPaths(ConceptPath c);
    Collection<? extends ConceptPath> getConceptPaths(Concept c);

    Concept getConcept(String code);
    Collection<Concept> findConcepts(String text);

    // to enable lookup and hierarchy queries core ontology tables must be populated
    void populateConcept(Concept c, boolean includeChildren);

    void delete(Ontology ont);

    /**
     * The number of concepts included within the Ontology
     * @return the concept count, or null if value isn't available/implemented
     */
    @Nullable Integer getOntologyConceptCount(Ontology ontology);
}
