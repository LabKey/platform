package org.labkey.api.ontology;

import org.apache.commons.lang3.NotImplementedException;
import org.fhcrc.cpas.exp.xml.PropertyDescriptorType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnRenderProperties;
import org.labkey.api.data.Container;
import org.labkey.api.data.MutableColumnRenderProperties;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.data.xml.ColumnType;

import java.util.List;

/** public interface to ontology services, largely implemented by OntologyManager */
public interface OntologyService
{
    String conceptCodeConceptURI = "http://www.labkey.org/types#conceptCode";

    void registerProvider(OntologyProvider provider);

    default List<Ontology> getOntologies(Container c)
    {
        throw new NotImplementedException("todo");
    }

    Concept resolveCode(String code);

    /** Resolve a concept based on exact match of label or synonym
     *  CONSIDER: may also want a scoped version of this method (e.g. (Ontology o, String term) or (String path, String term)
     */
    //Concept resolveTerm(String term);

    DataIteratorBuilder getConceptLookupDataIteratorBuilder(DataIteratorBuilder in, TableInfo target);

    static OntologyService get()
    {
        return ServiceRegistry.get().getService(OntologyService.class);
    }

    static void setInstance(OntologyService impl)
    {
        ServiceRegistry.get().registerService(OntologyService.class, impl);
    }

    void parseXml(ColumnType xmlCol, MutableColumnRenderProperties col);

    void writeXml(ColumnRenderProperties col, ColumnType colXml);

    void parseXml(PropertyDescriptorType xmlProp, DomainProperty domainProp);

    void writeXml(DomainProperty domainProp, PropertyDescriptorType xProp);

    interface ConceptInfo
    {
        String getLabelColumn();
        String getImportColumn();
        String getSubClassOfPath();
    }

    interface OntologyAnnotations
    {
        @NotNull ConceptInfo getConceptInfo();
        @Nullable String getPrincipalConceptCode();
    }
}