package org.labkey.api.ontology;

import org.apache.commons.lang3.NotImplementedException;
import org.fhcrc.cpas.exp.xml.PropertyDescriptorType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.ColumnRenderProperties;
import org.labkey.api.data.Container;
import org.labkey.api.data.MutableColumnConceptProperties;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.query.ValidationException;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.data.xml.ColumnType;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

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

    /* handle concept lookup columns in a DataIterator based insert/update*/
    DataIteratorBuilder getConceptLookupDataIteratorBuilder(DataIteratorBuilder in, TableInfo target);

    /* handle concept lookup columns in a non-DataIterator based insert/update */
    Function<Map<String,Object>, Map<String,Object>> getConceptUpdateHandler(TableInfo t) throws ValidationException;

    static OntologyService get()
    {
        return ServiceRegistry.get().getService(OntologyService.class);
    }

    static void setInstance(OntologyService impl)
    {
        ServiceRegistry.get().registerService(OntologyService.class, impl);
    }

    void parseXml(ColumnType xmlCol, MutableColumnConceptProperties col);

    void writeXml(ColumnRenderProperties col, ColumnType colXml);

    void parseXml(PropertyDescriptorType xmlProp, MutableColumnConceptProperties domainProp);

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
