/*
 * Copyright (c) 2007-2008 LabKey Corporation
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

package org.labkey.study.assay;

import com.google.gwt.user.client.rpc.SerializableException;
import org.labkey.api.data.Container;
import org.labkey.api.data.RuntimeSQLException;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainEditorServiceBase;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.gwt.client.assay.AssayException;
import org.labkey.api.gwt.client.assay.AssayService;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.security.ACL;
import org.labkey.api.security.User;
import org.labkey.api.study.PlateService;
import org.labkey.api.study.PlateTemplate;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ViewContext;
import org.labkey.common.util.Pair;

import java.sql.SQLException;
import java.util.*;

/**
 * User: brittp
 * Date: Jun 22, 2007
 * Time: 10:01:10 AM
 */
public class AssayServiceImpl extends DomainEditorServiceBase implements AssayService
{
    public AssayServiceImpl(ViewContext context)
    {
        super(context);
    }

    public GWTProtocol getAssayDefinition(int rowId, boolean copy) throws SerializableException
    {
        ExpProtocol protocol = ExperimentService.get().getExpProtocol(rowId);
        if (protocol == null)
            return null;
        else
        {
            Pair<ExpProtocol, List<Domain>> assayInfo = null;
            AssayProvider provider = org.labkey.api.study.assay.AssayService.get().getProvider(protocol);
            if (copy)
                assayInfo = provider.getAssayTemplate(getUser(), getContainer(), protocol);
            else
                assayInfo = new Pair<ExpProtocol, List<Domain>>(protocol, provider.getDomains(protocol));
            return getAssayTemplate(provider, assayInfo, copy);
        }
    }

    // path -> containerid
    public List getContainers()
    {
        return super.getContainers();
    }

    public GWTProtocol getAssayTemplate(String providerName) throws SerializableException
    {
        AssayProvider provider = org.labkey.api.study.assay.AssayService.get().getProvider(providerName);
        Pair<ExpProtocol, List<Domain>> template = provider.getAssayTemplate(getUser(), getContainer());
        return getAssayTemplate(provider, template, false);
    }

    public GWTProtocol getAssayTemplate(AssayProvider provider, Pair<ExpProtocol, List<Domain>> template, boolean copy) throws SerializableException
    {
        List<GWTDomain> gwtDomains = new ArrayList<GWTDomain>();
        for (Domain domain : template.getValue())
        {
            GWTDomain gwtDomain = new GWTDomain();
            Set<String> requiredPropertyDescriptors = new HashSet<String>();
            if (!copy)
            {
                gwtDomain.setDomainId(domain.getTypeId());
            }
            gwtDomain.setDomainURI(domain.getTypeURI());
            gwtDomain.setDescription(domain.getDescription());
            gwtDomain.setName(domain.getName());
            gwtDomain.setAllowFileLinkProperties(provider.isFileLinkPropertyAllowed(template.getKey(), domain));
            gwtDomains.add(gwtDomain);
            List<GWTPropertyDescriptor> gwtProps = new ArrayList<GWTPropertyDescriptor>();
            for (DomainProperty prop : domain.getProperties())
            {
                GWTPropertyDescriptor gwtProp = new GWTPropertyDescriptor();
                if (!copy)
                {
                    gwtProp.setPropertyId(prop.getPropertyId());
                }
                gwtProp.setDescription(prop.getDescription());
                gwtProp.setFormat(prop.getFormatString());
                gwtProp.setLabel(prop.getLabel());
                gwtProp.setName(prop.getName());
                gwtProp.setPropertyURI(prop.getPropertyURI());
                gwtProp.setRangeURI(prop.getType().getTypeURI());
                gwtProp.setRequired(prop.isRequired());


                if (prop.getLookup() != null)
                {
                    gwtProp.setLookupContainer(prop.getLookup().getContainer() == null ? null : prop.getLookup().getContainer().getPath());
                    gwtProp.setLookupQuery(prop.getLookup().getQueryName());
                    gwtProp.setLookupSchema(prop.getLookup().getSchemaName());
                }
                gwtProps.add(gwtProp);
                if (provider.isRequiredDomainProperty(domain, prop.getName()))
                    requiredPropertyDescriptors.add(prop.getName());
            }
            gwtDomain.setPropertyDescriptors(gwtProps);
            gwtDomain.setRequiredPropertyDescriptors(requiredPropertyDescriptors);
        }

        ExpProtocol protocol = template.getKey();
        GWTProtocol result = new GWTProtocol();
        result.setSampleCount(protocol.getMaxInputMaterialPerInstance() != null ? protocol.getMaxInputMaterialPerInstance() : 0);
        result.setProtocolId(protocol.getRowId() > 0 ? protocol.getRowId() : null);
        result.setDomains(orderDomainList(gwtDomains, false));
        result.setName(protocol.getName());
        result.setProviderName(provider.getName());
        result.setDescription(protocol.getDescription());
        if (provider.isPlateBased())
        {
            PlateTemplate plateTemplate = provider.getPlateTemplate(getContainer(), protocol);
            if (plateTemplate != null)
                result.setSelectedPlateTemplate(plateTemplate.getName());
            setPlateTemplateList(provider, result);
        }
        return result;
    }

    private void setPlateTemplateList(AssayProvider provider, GWTProtocol protocol)
    {
        if (provider.isPlateBased())
        {
            List<String> plateTemplates = new ArrayList<String>();
            try
            {
                for (PlateTemplate template : PlateService.get().getPlateTemplates(getContainer()))
                    plateTemplates.add(template.getName());
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
            protocol.setAvailablePlateTemplates(plateTemplates);
        }
    }

    private List<GWTDomain> orderDomainList(List<GWTDomain> domains, final boolean asc)
    {
        Collections.sort(domains, new Comparator<GWTDomain>(){

            public int compare(GWTDomain dom1, GWTDomain dom2)
            {
                return (asc ? 1 : -1) * dom1.getName().compareTo(dom2.getName());
            }
        });
        return domains;
    }


    private void setPropertyDomainURIs(ExpProtocol protocol, Set<String> uris)
    {
        if (getContainer() == null)
        {
            throw new IllegalStateException("Must set container before setting domain URIs");
        }
        if (protocol.getLSID() == null)
        {
            throw new IllegalStateException("Must set LSID before setting domain URIs");
        }
        Map<String, ObjectProperty> props = new HashMap<String, ObjectProperty>(protocol.retrieveObjectProperties());
        // First prune out any domains of the same type that aren't in the new set
        for (String uri : new HashSet<String>(props.keySet()))
        {
            Lsid lsid = new Lsid(uri);
            if (lsid.getNamespacePrefix() != null && lsid.getNamespacePrefix().startsWith(ExpProtocol.ASSAY_DOMAIN_PREFIX) && !uris.contains(uri))
            {
                props.remove(uri);
            }
        }

        for (String uri : uris)
        {
            if (!props.containsKey(uri))
            {
                ObjectProperty prop = new ObjectProperty(protocol.getLSID(), protocol.getContainer().getId(), uri, uri);
                props.put(prop.getPropertyURI(), prop);
            }
        }
        protocol.storeObjectProperties(props);
    }

    public GWTProtocol saveChanges(GWTProtocol assay, boolean replaceIfExisting) throws AssayException
    {
        if (replaceIfExisting)
        {
            try
            {
                ExpProtocol protocol;
                if (assay.getProtocolId() == null)
                {
                    protocol = AssayManager.get().createAssayDefinition(getUser(), getContainer(), assay);
                    assay.setProtocolId(protocol.getRowId());

                    XarContext context = new XarContext("Domains", getContainer(), getUser());
                    context.addSubstitution("AssayName", assay.getName());
                    Set<String> domainURIs = new HashSet<String>();
                    for (GWTDomain domain : (List<GWTDomain>)assay.getDomains())
                    {
                        domain.setDomainURI(LsidUtils.resolveLsidFromTemplate(domain.getDomainURI(), context));
                        domain.setName(assay.getName() + " " + domain.getName());
                        DomainDescriptor dd = OntologyManager.ensureDomainDescriptor(domain.getDomainURI(), domain.getName(), getContainer());
                        dd.setDescription(domain.getDescription());
                        OntologyManager.updateDomainDescriptor(dd);
                        domainURIs.add(domain.getDomainURI());
                    }
                    setPropertyDomainURIs(protocol, domainURIs);
                }
                else
                {
                    protocol = ExperimentService.get().getExpProtocol(assay.getProtocolId());

                    //ensure that the user has edit perms in this container
                    if(!canUpdateProtocol(protocol))
                        throw new AssayException("You do not have sufficient permissions to update this Assay");

                    if (!protocol.getContainer().equals(getContainer()))
                        throw new AssayException("Assays can only be edited in the folder where they were created.  " +
                                "This assay was created in folder " + protocol.getContainer().getPath());
                    protocol.setName(assay.getName());
                    protocol.setProtocolDescription(assay.getDescription());
                    protocol.setMaxInputMaterialPerInstance(assay.getSampleCount());
                }

                AssayProvider provider = org.labkey.api.study.assay.AssayService.get().getProvider(protocol);
                if (provider.isPlateBased() && assay.getSelectedPlateTemplate() != null)
                {
                    PlateTemplate template = PlateService.get().getPlateTemplate(getContainer(), assay.getSelectedPlateTemplate());
                    if (template != null)
                        provider.setPlateTemplate(getContainer(), protocol, template);
                    else
                        throw new AssayException("The selected plate template could not be found.  Perhaps it was deleted by another user?");
                }

                protocol.save(getUser());

                StringBuilder errors = new StringBuilder();
                for (GWTDomain domain : (List<GWTDomain>)assay.getDomains())
                {
                    List<String> domainErrors = updateDomainDescriptor(domain, protocol.getName(), getContainer());
                    if (domainErrors != null)
                    {
                        for (String error : domainErrors)
                        {
                            errors.append(error).append("\n");
                        }
                    }
                }
                if (errors.length() > 0)
                    throw new AssayException(errors.toString());

                return assay;
            }
            catch (UnexpectedException e)
            {
                throw new AssayException(e);
            }
            catch (ExperimentException e)
            {
                throw new AssayException(e);
            }
            catch (SQLException e)
            {
                throw new RuntimeSQLException(e);
            }
        }
        else
            throw new AssayException("Only replaceIfExisting == true is supported.");
    }

    private List updateDomainDescriptor(GWTDomain domain, String protocolName, Container protocolContainer)
    {
        GWTDomain previous = getDomainDescriptor(domain.getDomainURI(), protocolContainer);
        for (GWTPropertyDescriptor prop : (List<GWTPropertyDescriptor>)domain.getPropertyDescriptors())
        {
            if (prop.getLookupQuery() != null)
            {
                prop.setLookupQuery(prop.getLookupQuery().replace(AbstractAssayProvider.ASSAY_NAME_SUBSTITUTION, protocolName));
            }
        }
        return updateDomainDescriptor(previous, domain);
    }

    public List updateDomainDescriptor(GWTDomain orig, GWTDomain update)
    {
        try
        {
            return super.updateDomainDescriptor(orig, update);
        }
        catch (ChangePropertyDescriptorException e)
        {
            throw UnexpectedException.wrap(e);
        }
    }

    public boolean canUpdate(User user, Domain domain)
    {
        return getContainer().hasPermission(getUser(), ACL.PERM_INSERT);
    }

    public boolean canUpdateProtocol(ExpProtocol protocol)
    {
        Container c = getContainer();
        User u = getUser();
        return c.hasPermission(u, ACL.PERM_UPDATE) || 
                (c.hasPermission(u, ACL.PERM_UPDATEOWN) && protocol.getCreatedBy().equals(u));
    }
}
