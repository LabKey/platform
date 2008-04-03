package org.labkey.api.exp.api;

import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.Lsid;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.security.ACL;
import org.labkey.api.view.ActionURL;
import org.labkey.common.util.Pair;

import java.util.Map;

public class SampleSetDomainType extends DomainKind
{
    public SampleSetDomainType()
    {
    }

    public boolean isDomainType(String domainURI)
    {
        Lsid lsid = new Lsid(domainURI);
        return "SampleSet".equals(lsid.getNamespacePrefix());
    }

//    public String generateDomainURI(Container container, String name)
//    {
//        return ExperimentServiceImpl.get().generateLSID(container, ExpSampleSet.class, name);
//    }

    private ExpSampleSet getSampleSet(Domain domain)
    {
        return ExperimentService.get().getSampleSet(domain.getTypeURI());
    }

    public ActionURL urlShowData(Domain domain)
    {
        ExpSampleSet ss = getSampleSet(domain);
        if (ss == null)
        {
            return null;
        }
        return (ActionURL) ss.detailsURL();
    }

    public ActionURL urlEditDefinition(Domain domain)
    {
        return urlShowData(domain);
    }

    // return the "system" properties for this domain
    public DomainProperty[] getDomainProperties(String domainURI)
    {
        return new DomainProperty[0];
    }

    public String getTypeLabel(Domain domain)
    {
        return "Sample Set '" + domain.getName() + "'";
    }

    public SQLFragment sqlObjectIdsInDomain(Domain domain)
    {
        SQLFragment ret = new SQLFragment("SELECT exp.object.objectid FROM exp.object INNER JOIN exp.material ON exp.object.objecturi = exp.material.lsid WHERE exp.material.cpastype = ?");
        ret.add(domain.getTypeURI());
        return ret;
    }

    public Map.Entry<TableInfo, ColumnInfo> getTableInfo(User user, Domain domain, Container[] containerFilter)
    {
        SamplesSchema schema = new SamplesSchema(user, domain.getContainer());
        TableInfo table = schema.getSampleTable("lookup", ExperimentService.get().getSampleSet(domain.getTypeURI()));
        if (table == null)
            return null;
        return new Pair(table, table.getColumn("LSID"));
    }

    public boolean canEditDefinition(User user, Domain domain)
    {
        return domain.getContainer().hasPermission(user, ACL.PERM_UPDATE);
    }
}
