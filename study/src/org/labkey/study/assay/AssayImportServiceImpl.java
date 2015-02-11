/*
 * Copyright (c) 2011-2015 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.labkey.study.assay;

import gwt.client.org.labkey.assay.designer.client.AssayImporterService;
import org.apache.commons.lang3.StringUtils;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.ChangePropertyDescriptorException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.PropertyType;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainImporterServiceBase;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.property.Type;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.gwt.client.ui.domain.GWTImportException;
import org.labkey.api.gwt.client.ui.domain.ImportStatus;
import org.labkey.api.gwt.client.ui.domain.InferencedColumn;
import org.labkey.api.iterator.CloseableIterator;
import org.labkey.api.reader.ColumnDescriptor;
import org.labkey.api.reader.DataLoader;
import org.labkey.api.study.actions.ImportAction;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.ViewContext;

import javax.servlet.http.HttpSession;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: klum
 * Date: Dec 10, 2010
 * Time: 2:33:54 PM
 */
public class AssayImportServiceImpl extends DomainImporterServiceBase implements AssayImporterService
{
    private boolean _deleteImportFile;

    public AssayImportServiceImpl(ViewContext context)
    {
        super(context);
        setNumSampleRows(20);
    }

    @Override
    protected void setImportFile(ViewContext context)
    {
        HttpSession session = getViewContext().getSession();
        Object o = session.getAttribute(ImportAction.ASSAY_IMPORT_FILE);

        //PipelineDataCollector.gsetFileCollection(getViewContext().getRequest().getSession(true), getContainer(), form.getProtocol(), maps);

        //if (o instanceof File)
        //    _importFile = (File)o;
    }

    @Override
    protected void deleteImportFile()
    {
        if (_deleteImportFile)
            super.deleteImportFile();
    }

    @Override
    public List<InferencedColumn> getInferenceColumns(String path, String file) throws GWTImportException
    {
        List<File> files = ImportAction.getFiles(getContainer(), path, new String[]{file});

        if (!files.isEmpty())
        {
            _importFile = files.get(0);
            return inferenceColumns();
        }
        throw new GWTImportException("Failed trying to infer columns for the file: " + file);
    }

    @Override
    public Boolean validateColumns(List<InferencedColumn> columns, String path, String file) throws GWTImportException
    {
        List<File> files = ImportAction.getFiles(getContainer(), path, new String[]{file});

        if (!files.isEmpty())
        {
            _importFile = files.get(0);
        }

        List<ColumnDescriptor> descriptors = new ArrayList<>();
        for (InferencedColumn col : columns)
        {
            GWTPropertyDescriptor prop = col.getPropertyDescriptor();
            Class javaType = null;
            Type type = Type.getTypeByXsdType(prop.getRangeURI());

            if (type != null)
                javaType = type.getJavaClass();

            if (javaType == null)
            {
                PropertyType propType = PropertyType.getFromURI(prop.getConceptURI(), prop.getRangeURI());
                if (propType != null)
                    javaType = propType.getJavaType();
            }

            if (javaType != null)
            {
                ColumnDescriptor cd = new ColumnDescriptor(prop.getName(), javaType);
                descriptors.add(cd);
            }
        }

        DataLoader loader = getDataLoader();
        loader.setColumns(descriptors.toArray(new ColumnDescriptor[descriptors.size()]));

        loader.setThrowOnErrors(true);

        // validate the entire document by scanning all rows using the dataloader
        CloseableIterator it = loader.iterator();
        try {
            while (it.hasNext())
            {
                it.next();
            }
        }
        catch (Exception e)
        {
            throw new GWTImportException(e.getMessage());
        }
        finally
        {
            try {it.close();} catch(IOException ioe){}
        }
        return true;
    }

    @Override
    public ImportStatus importData(GWTDomain gwtDomain, Map<String, String> mappedColumnNames) throws GWTImportException
    {
        ImportStatus status = new ImportStatus();

        Domain domain = PropertyService.get().getDomain(gwtDomain.getDomainId());
        if (domain != null)
        {
            boolean changed = false;

            // for any column mappings, we want to handle by setting the import alias for the domain property
            for (Map.Entry<String, String> entry : mappedColumnNames.entrySet())
            {
                if (!entry.getKey().equals(entry.getValue()))
                {
                    DomainProperty dp = domain.getPropertyByName(entry.getValue().toLowerCase());
                    if (dp != null)
                    {
                        Set<String> alias = new LinkedHashSet<>();
                        alias.addAll(dp.getImportAliasSet());

                        alias.add(entry.getKey());

                        dp.setImportAliasSet(alias);
                        changed = true;
                    }
                }
            }
            if (changed)
            {
                try {
                    domain.save(getUser());
                }
                catch (ChangePropertyDescriptorException e)
                {
                    throw new GWTImportException(e.getMessage());
                }
            }

            status.setComplete(true);
        }
        else
            throw new IllegalArgumentException("Attempt to import data into a non-existent domain");

        return status;
    }

    @Override
    public ImportStatus getStatus(String jobId) throws GWTImportException
    {
        throw new GWTImportException("Shouldn't be calling getStatus() -- assays import synchronously");
    }

    @Override
    public String cancelImport(String jobId) throws GWTImportException
    {
        return null;
    }

    @Override
    public GWTProtocol createProtocol(String providerName, String assayName, String containerID) throws GWTImportException
    {
        ViewContext context = new ViewContext(getViewContext());

        try {
            Container location = ContainerManager.getForId(containerID);
            if (location != null)
                context.setContainer(location);

            AssayServiceImpl svc = new AssayServiceImpl(context);

            GWTProtocol gwtProtocol = svc.getAssayTemplate(providerName);

            gwtProtocol.setName(assayName);
            gwtProtocol = svc.saveChanges(gwtProtocol, true);

            return gwtProtocol;
        }
        catch (Exception e)
        {
            throw new GWTImportException(e.getMessage());
        }
    }

    @Override
    public String getDomainImportURI(GWTProtocol gwtProtocol) throws GWTImportException
    {
        try {
            AssayProvider provider = AssayService.get().getProvider(gwtProtocol.getProviderName());
            if (provider != null)
            {
                ExpProtocol protocol = ExperimentService.get().getExpProtocol(gwtProtocol.getProtocolId());
                Domain domain = provider.getResultsDomain(protocol);

                Set<String> reservedNames = domain.getDomainKind().getReservedPropertyNames(domain);

                return domain.getTypeURI();
            }
            return null;
        }
        catch (Exception e)
        {
            throw new GWTImportException(e.getMessage());
        }
    }

    @Override
    public String getImportURL(GWTProtocol gwtProtocol, String directoryPath, String file) throws GWTImportException
    {
        ExpProtocol protocol = ExperimentService.get().getExpProtocol(gwtProtocol.getProtocolId());
        List<File> files = ImportAction.getFiles(getContainer(), directoryPath, new String[]{file});

        if (protocol != null && !files.isEmpty())
        {
            ActionURL url = PageFlowUtil.urlProvider(AssayUrls.class).getImportURL(getContainer(), protocol, StringUtils.trimToEmpty(directoryPath), new File[]{files.get(0)});
            return url.getLocalURIString();
        }
        return null;
    }

    @Override
    public String getDesignerURL(GWTProtocol gwtProtocol, String directoryPath, String file) throws GWTImportException
    {
        ExpProtocol protocol = ExperimentService.get().getExpProtocol(gwtProtocol.getProtocolId());
        List<File> files = ImportAction.getFiles(getContainer(), directoryPath, new String[]{file});

        if (protocol != null && !files.isEmpty())
        {
            ActionURL returnUrl = PageFlowUtil.urlProvider(AssayUrls.class).getImportURL(getContainer(), protocol, StringUtils.defaultString(directoryPath, ""), new File[]{files.get(0)});
            ActionURL url = PageFlowUtil.urlProvider(AssayUrls.class).getDesignerURL(getContainer(), protocol, false, returnUrl);

            return url == null ? null : url.getLocalURIString();
        }
        return null;
    }

    public String getTypeURI(String providerName, String assayName) throws GWTImportException
    {
        ViewContext context = getViewContext();

        try {

            AssayServiceImpl svc = new AssayServiceImpl(context);

            GWTProtocol gwtProtocol = svc.getAssayTemplate(providerName);

            gwtProtocol.setName(assayName);
            gwtProtocol = svc.saveChanges(gwtProtocol, true);

            AssayProvider provider = AssayService.get().getProvider(providerName);
            if (provider != null)
            {
                ExpProtocol protocol = ExperimentService.get().getExpProtocol(gwtProtocol.getProtocolId());
                Domain domain = provider.getResultsDomain(protocol);

                return domain.getTypeURI();
            }
            return null;
        }
        catch (Exception e)
        {
            throw new GWTImportException(e.getMessage());
        }
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
        Map<String, ObjectProperty> props = new HashMap<>(protocol.getObjectProperties());
        // First prune out any domains of the same type that aren't in the new set
        for (String uri : new HashSet<>(props.keySet()))
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
                ObjectProperty prop = new ObjectProperty(protocol.getLSID(), protocol.getContainer(), uri, uri);
                props.put(prop.getPropertyURI(), prop);
            }
        }
        protocol.setObjectProperties(props);
    }

    public String getImportURL(String providerName, String directoryPath, String assayName) throws GWTImportException
    {
        List<ExpProtocol> assays = AssayService.get().getAssayProtocols(getContainer());
        ExpProtocol assayProtocol = null;
        for (ExpProtocol protocol : assays)
        {
            String name = protocol.getName();

            if (name.equals(assayName))
                assayProtocol = protocol;
        }

        if (assayProtocol != null)
        {
            ActionURL url = PageFlowUtil.urlProvider(AssayUrls.class).getImportURL(getContainer(), assayProtocol, directoryPath, new File[]{_importFile});

            return url.getLocalURIString();
        }
        return null;
    }

    @Override
    public List<GWTPropertyDescriptor> getBaseColumns(String providerName) throws GWTImportException
    {
        AssayProvider provider = AssayService.get().getProvider(providerName);
        List<GWTPropertyDescriptor> baseColumns = new ArrayList<>();

        // don't import columns already included in the base results domain
        Pair<ExpProtocol, List<Pair<Domain, Map<DomainProperty, Object>>>> template = provider.getAssayTemplate(getViewContext().getUser(), getContainer());

        for (Pair<Domain, Map<DomainProperty, Object>> domainInfo : template.getValue())
        {
            String uri = domainInfo.getKey().getTypeURI();
            Lsid uriLSID = new Lsid(uri);

            if (uriLSID.getNamespacePrefix() != null && uriLSID.getNamespacePrefix().startsWith(ExpProtocol.ASSAY_DOMAIN_DATA))
            {
                for (DomainProperty prop : domainInfo.getKey().getProperties())
                {
                    baseColumns.add(DomainUtil.getPropertyDescriptor(prop));
                }
            }
        }
        return baseColumns;
    }

    @Override
    public List<Map<String, String>> getAssayLocations() throws GWTImportException
    {
        List<Map<String, String>> locations = new ArrayList<>();
        boolean isDefault = true;

        for (Pair<Container, String> entry : AssayService.get().getLocationOptions(getContainer(), getUser()))
        {
            locations.add(PageFlowUtil.map("id", entry.getKey().getId(), "label", entry.getValue(), "default", String.valueOf(isDefault)));
            isDefault = false;
        }
        return locations;
    }
}
