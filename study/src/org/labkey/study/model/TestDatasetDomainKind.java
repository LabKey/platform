package org.labkey.study.model;

import org.labkey.api.exp.property.Domain;

import java.util.Collections;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: newton
 * Date: Oct 11, 2010
 * Time: 3:45:55 PM
 */
public class TestDatasetDomainKind extends DatasetDomainKind
{

    public static String KIND_NAME = "TestDatasetDomainKind";

    @Override
    public String getKindName()
    {
        return KIND_NAME;
    }

    @Override
    public boolean isDomainType(String domainURI)
    {
        return domainURI.contains(KIND_NAME);
    }

    @Override
    public Set<String> getReservedPropertyNames(Domain domain)
    {
        return Collections.emptySet();
    }
}
