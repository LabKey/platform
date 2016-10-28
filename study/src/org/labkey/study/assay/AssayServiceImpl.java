/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.fhcrc.cpas.exp.xml.SimpleTypeNames;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.defaults.SetDefaultValuesAssayAction;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.ProtocolParameter;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainEditorServiceBase;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.assay.AssayException;
import org.labkey.api.gwt.client.assay.AssayService;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;
import org.labkey.api.gwt.client.model.GWTContainer;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.PlateService;
import org.labkey.api.study.PlateTemplate;
import org.labkey.api.study.Study;
import org.labkey.api.study.assay.AbstractAssayProvider;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayPublishService;
import org.labkey.api.study.assay.DetectionMethodAssayProvider;
import org.labkey.api.study.assay.PlateBasedAssayProvider;
import org.labkey.api.study.assay.SampleMetadataInputFormat;
import org.labkey.api.study.permissions.DesignAssayPermission;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.study.StudySchema;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

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

    public GWTProtocol getAssayDefinition(int rowId, boolean copy)
    {
        ExpProtocol protocol = ExperimentService.get().getExpProtocol(rowId);
        if (protocol == null)
            return null;
        else
        {
            Pair<ExpProtocol, List<Pair<Domain, Map<DomainProperty, Object>>>> assayInfo;
            AssayProvider provider = org.labkey.api.study.assay.AssayService.get().getProvider(protocol);
            if (copy)
                assayInfo = provider.getAssayTemplate(getUser(), getContainer(), protocol);
            else
                assayInfo = new Pair<>(protocol, provider.getDomains(protocol));
            return getAssayTemplate(provider, assayInfo, copy);
        }
    }

    public GWTProtocol getAssayTemplate(String providerName)
    {
        AssayProvider provider = org.labkey.api.study.assay.AssayService.get().getProvider(providerName);
        if (provider == null)
        {
            throw new NotFoundException("Could not find assay provider " + providerName);
        }
        Pair<ExpProtocol, List<Pair<Domain, Map<DomainProperty, Object>>>> template = provider.getAssayTemplate(getUser(), getContainer());
        return getAssayTemplate(provider, template, false);
    }

    public GWTProtocol getAssayTemplate(AssayProvider provider, Pair<ExpProtocol, List<Pair<Domain, Map<DomainProperty, Object>>>> template, boolean copy)
    {
        ExpProtocol protocol = template.getKey();
        List<GWTDomain<GWTPropertyDescriptor>> gwtDomains = new ArrayList<>();
        for (Pair<Domain, Map<DomainProperty, Object>> domainInfo : template.getValue())
        {
            Domain domain = domainInfo.getKey();
            GWTDomain<GWTPropertyDescriptor> gwtDomain = DomainUtil.getDomainDescriptor(getUser(), domain);
            if (provider.allowDefaultValues(domain))
                gwtDomain.setDefaultValueOptions(provider.getDefaultValueOptions(domain), provider.getDefaultValueDefault(domain));
            Set<String> mandatoryPropertyDescriptors = new HashSet<>();
            if (copy)
            {
                gwtDomain.setDomainId(0);
            }
            gwtDomain.setAllowFileLinkProperties(provider.isFileLinkPropertyAllowed(template.getKey(), domain));
            ActionURL setDefaultValuesAction = new ActionURL(SetDefaultValuesAssayAction.class, getContainer());
            setDefaultValuesAction.addParameter("providerName", provider.getName());
            gwtDomain.setDefaultValuesURL(setDefaultValuesAction.getLocalURIString());
            gwtDomains.add(gwtDomain);
            gwtDomain.setProvisioned(domain.isProvisioned());
            List<GWTPropertyDescriptor> gwtProps = new ArrayList<>();

            List<? extends DomainProperty> properties = domain.getProperties();
            Map<DomainProperty, Object> defaultValues = domainInfo.getValue();

            for (DomainProperty prop : properties)
            {
                GWTPropertyDescriptor gwtProp = getPropertyDescriptor(prop, copy);
                if (gwtProp.getDefaultValueType() == null)
                {
                    // we want to explicitly set these "special" properties NOT to remember the user's last entered
                    // value if it hasn't been set before:
                    if (AbstractAssayProvider.PARTICIPANTID_PROPERTY_NAME.equals(prop.getName()) ||
                        AbstractAssayProvider.SPECIMENID_PROPERTY_NAME.equals(prop.getName()) ||
                        AbstractAssayProvider.VISITID_PROPERTY_NAME.equals(prop.getName()) ||
                        AbstractAssayProvider.DATE_PROPERTY_NAME.equals(prop.getName()))
                    {
                        prop.setDefaultValueTypeEnum(DefaultValueType.FIXED_EDITABLE);
                    }
                    else
                        gwtProp.setDefaultValueType(gwtDomain.getDefaultDefaultValueType());
                }
                gwtProps.add(gwtProp);
                Object defaultValue = defaultValues.get(prop);
                if (AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equals(gwtProp.getName()) && defaultValue instanceof String)
                {
                    Container studyContainer = ContainerManager.getForId((String) defaultValue);
                    if (studyContainer != null)
                        gwtProp.setDefaultDisplayValue(studyContainer.getPath());
                }
                else
                    gwtProp.setDefaultDisplayValue(DomainUtil.getFormattedDefaultValue(getUser(), prop, defaultValue));

                gwtProp.setDefaultValue(ConvertUtils.convert(defaultValue));
                if (provider.isMandatoryDomainProperty(domain, prop.getName()))
                    mandatoryPropertyDescriptors.add(prop.getName());
            }
            gwtDomain.setFields(gwtProps);
            gwtDomain.setMandatoryFieldNames(mandatoryPropertyDescriptors);
        }

        GWTProtocol result = new GWTProtocol();
        result.setProtocolId(protocol.getRowId() > 0 ? protocol.getRowId() : null);
        result.setDomains(gwtDomains);
        result.setName(protocol.getName());
        result.setProviderName(provider.getName());
        result.setDescription(protocol.getDescription());
        Map<String, String> gwtProtocolParams = new HashMap<>();
        for (ProtocolParameter property : protocol.getProtocolParameters().values())
        {
            if (property.getXmlBeanValueType() != SimpleTypeNames.STRING)
            {
                throw new IllegalStateException("Did not expect non-string protocol parameter " + property.getOntologyEntryURI() + " (" + property.getValueType() + ")");
            }
            gwtProtocolParams.put(property.getOntologyEntryURI(), property.getStringValue());
        }
        result.setProtocolParameters(gwtProtocolParams);
        if (provider instanceof PlateBasedAssayProvider)
        {
            PlateTemplate plateTemplate = ((PlateBasedAssayProvider)provider).getPlateTemplate(getContainer(), protocol);
            if (plateTemplate != null)
                result.setSelectedPlateTemplate(plateTemplate.getName());
            setPlateTemplateList(provider, result);

            SampleMetadataInputFormat[] formats = ((PlateBasedAssayProvider) provider).getSupportedMetadataInputFormats();
            if (formats.length > 1)
            {
                Map<String, String> metadataFormats = new LinkedHashMap<>();
                StringBuilder sbHelp = new StringBuilder();
                String sep = "<b>";

                for (SampleMetadataInputFormat format : formats)
                {
                    metadataFormats.put(format.name(), format.getLabel());

                    sbHelp.append(sep).append(format.getLabel()).append(":</b> ").append(format.getDescription());
                    sep = "<br><br><b>";
                }

                result.setAvailableMetadataInputFormats(metadataFormats);
                result.setMetadataInputFormatHelp(sbHelp.toString());
            }
            result.setSelectedMetadataInputFormat(((PlateBasedAssayProvider)provider).getMetadataInputFormat(protocol).name());
        }
        if (provider instanceof DetectionMethodAssayProvider)
        {
            DetectionMethodAssayProvider dmProvider = (DetectionMethodAssayProvider)provider;
            String method = dmProvider.getSelectedDetectionMethod(getContainer(), protocol);
            if (method != null)
                result.setSelectedDetectionMethod(method);
            result.setAvailableDetectionMethods(dmProvider.getAvailableDetectionMethods());
        }

        List<File> typeScripts = provider.getValidationAndAnalysisScripts(protocol, AssayProvider.Scope.ASSAY_TYPE);
        if (!typeScripts.isEmpty())
        {
            List<String> scriptNames = new ArrayList<>();
            for (File script : typeScripts)
                scriptNames.add(script.getAbsolutePath());

            result.setModuleTransformScripts(scriptNames);
        }
        result.setSaveScriptFiles(provider.isSaveScriptFiles(protocol));
        result.setEditableResults(provider.isEditableResults(protocol));
        result.setEditableRuns(provider.isEditableRuns(protocol));
        result.setBackgroundUpload(provider.isBackgroundUpload(protocol));

        // data transform scripts
        List<File> transformScripts = provider.getValidationAndAnalysisScripts(protocol, AssayProvider.Scope.ASSAY_DEF);

        List<String> transformScriptStrings = new ArrayList<>();
        for (File transformScript : transformScripts)
        {
            transformScriptStrings.add(transformScript.getAbsolutePath());
        }
        result.setProtocolTransformScripts(transformScriptStrings);

        ObjectProperty autoCopyValue = protocol.getObjectProperties().get(AssayPublishService.AUTO_COPY_TARGET_PROPERTY_URI);
        if (autoCopyValue != null)
        {
            Container autoCopyTarget = ContainerManager.getForId(autoCopyValue.getStringValue());
            if (autoCopyTarget != null)
            {
                result.setAutoCopyTargetContainer(convertToGWTContainer(autoCopyTarget));
            }
        }

        result.setAllowTransformationScript(provider.createDataExchangeHandler() != null);

        boolean supportsFlag = provider.supportsFlagColumnType(ExpProtocol.AssayDomainTypes.Result);
        for (GWTDomain d : result.getDomains())
            if (d.getDomainURI().contains(":" + ExpProtocol.AssayDomainTypes.Result.getPrefix() + "."))
                d.setAllowFlagProperties(supportsFlag);

        return result;
    }

    private GWTContainer convertToGWTContainer(Container c)
    {
        GWTContainer parent;
        if (c.isRoot())
        {
            parent = null;
        }
        else
        {
            parent = convertToGWTContainer(c.getParent());
        }
        return new GWTContainer(c.getId(), c.getRowId(), parent, c.getName());
    }

    private GWTPropertyDescriptor getPropertyDescriptor(DomainProperty prop, boolean copy)
    {
        GWTPropertyDescriptor gwtProp = DomainUtil.getPropertyDescriptor(prop);
        if (copy)
            gwtProp.setPropertyId(0);

        return gwtProp;
    }

    private void setPlateTemplateList(AssayProvider provider, GWTProtocol protocol)
    {
        if (provider instanceof PlateBasedAssayProvider)
        {
            List<String> plateTemplates = new ArrayList<>();
            for (PlateTemplate template : PlateService.get().getPlateTemplates(getContainer()))
                plateTemplates.add(template.getName());
            protocol.setAvailablePlateTemplates(plateTemplates);
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

    public GWTProtocol saveChanges(GWTProtocol assay, boolean replaceIfExisting) throws AssayException
    {
        // Synchronize the whole method to prevent saving of new two assay designs with the same name at the same
        // time, which will lead to a SQLException on the UNIQUE constraint on protocol LSIDs
        synchronized (AssayServiceImpl.class)
        {
            if (replaceIfExisting)
            {
                DbSchema schema = StudySchema.getInstance().getSchema();
                try (DbScope.Transaction transaction = schema.getScope().ensureTransaction())
                {
                    ExpProtocol protocol;
                    if (assay.getProtocolId() == null)
                    {
                        // check for existing assay protocol with the given name before creating
                        if (AssayManager.get().getAssayProtocolByName(getContainer(), assay.getName()) != null)
                            throw new AssayException("Assay protocol already exists for this name.");

                        protocol = AssayManager.get().createAssayDefinition(getUser(), getContainer(), assay);
                        assay.setProtocolId(protocol.getRowId());

                        XarContext context = new XarContext("Domains", getContainer(), getUser());
                        context.addSubstitution("AssayName", PageFlowUtil.encode(assay.getName()));
                        Set<String> domainURIs = new HashSet<>();
                        for (GWTDomain domain : assay.getDomains())
                        {
                            domain.setDomainURI(LsidUtils.resolveLsidFromTemplate(domain.getDomainURI(), context));
                            domain.setName(assay.getName() + " " + domain.getName());
                            DomainDescriptor dd = OntologyManager.ensureDomainDescriptor(domain.getDomainURI(), domain.getName(), getContainer());
                            dd = dd.edit().setDescription(domain.getDescription()).build();
                            OntologyManager.updateDomainDescriptor(dd);
                            domainURIs.add(domain.getDomainURI());
                        }
                        setPropertyDomainURIs(protocol, domainURIs);
                    }
                    else
                    {
                        protocol = ExperimentService.get().getExpProtocol(assay.getProtocolId().intValue());

                        if (protocol == null)
                        {
                            throw new AssayException("Assay design has been deleted");
                        }

                        //ensure that the user has edit perms in this container
                        if (!canUpdateProtocols())
                            throw new AssayException("You do not have sufficient permissions to update this Assay");

                        if (!protocol.getContainer().equals(getContainer()))
                            throw new AssayException("Assays can only be edited in the folder where they were created.  " +
                                    "This assay was created in folder " + protocol.getContainer().getPath());
                        protocol.setName(assay.getName());
                        protocol.setProtocolDescription(assay.getDescription());
                    }

                    Map<String, ProtocolParameter> newParams = new HashMap<>(protocol.getProtocolParameters());
                    if (assay.getProtocolParameters() != null)
                    {
                        for (Map.Entry<String, String> entry : assay.getProtocolParameters().entrySet())
                        {
                            ProtocolParameter param = new ProtocolParameter();
                            String uri = entry.getKey();
                            param.setOntologyEntryURI(uri);
                            param.setValue(SimpleTypeNames.STRING, entry.getValue());
                            param.setName(uri.indexOf("#") != -1 ? uri.substring(uri.indexOf("#") + 1) : uri);
                            newParams.put(uri, param);
                        }
                    }
                    protocol.setProtocolParameters(newParams.values());

                    AssayProvider provider = org.labkey.api.study.assay.AssayService.get().getProvider(protocol);
                    if (provider instanceof PlateBasedAssayProvider && assay.getSelectedPlateTemplate() != null)
                    {
                        PlateBasedAssayProvider plateProvider = (PlateBasedAssayProvider)provider;
                        PlateTemplate template = PlateService.get().getPlateTemplate(getContainer(), assay.getSelectedPlateTemplate());
                        if (template != null)
                            plateProvider.setPlateTemplate(getContainer(), protocol, template);
                        else
                            throw new AssayException("The selected plate template could not be found.  Perhaps it was deleted by another user?");

                        String selectedFormat = assay.getSelectedMetadataInputFormat();
                        SampleMetadataInputFormat inputFormat = SampleMetadataInputFormat.valueOf(selectedFormat);
                        if (inputFormat != null)
                            ((PlateBasedAssayProvider)provider).setMetadataInputFormat(protocol, inputFormat);
                    }

                    // data transform scripts
                    List<File> transformScripts = new ArrayList<>();
                    for (String script : assay.getProtocolTransformScripts())
                    {
                        if (!StringUtils.isBlank(script))
                        {
                            transformScripts.add(new File(script));
                        }
                    }

                    if (provider instanceof DetectionMethodAssayProvider && assay.getSelectedDetectionMethod() != null)
                    {
                        DetectionMethodAssayProvider dmProvider = (DetectionMethodAssayProvider)provider;
                        String detectionMethod = assay.getSelectedDetectionMethod();
                        if (detectionMethod != null)
                            dmProvider.setSelectedDetectionMethod(getContainer(), protocol, detectionMethod);
                        else
                            throw new AssayException("The selected detection method could not be found.");
                    }

                    provider.setValidationAndAnalysisScripts(protocol, transformScripts);
//
//                    provider.setDetectionMethods(protocol, assay.getAvailableDetectionMethods());

                    provider.setSaveScriptFiles(protocol, assay.isSaveScriptFiles());
                    provider.setEditableResults(protocol, assay.isEditableResults());
                    provider.setEditableRuns(protocol, assay.isEditableRuns());
                    provider.setBackgroundUpload(protocol, assay.isBackgroundUpload());

                    Map<String, ObjectProperty> props = new HashMap<>(protocol.getObjectProperties());
                    String autoCopyTargetContainerId = null;
                    if (assay.getAutoCopyTargetContainer() != null)
                    {
                        Container container = ContainerManager.getForId(assay.getAutoCopyTargetContainer().getEntityId());
                        if (container == null)
                        {
                            throw new AssayException("No such auto-copy target container: " + assay.getAutoCopyTargetContainer().getPath());
                        }
                        autoCopyTargetContainerId = container.getId();
                    }
                    if (autoCopyTargetContainerId != null)
                    {
                        props.put(AssayPublishService.AUTO_COPY_TARGET_PROPERTY_URI, new ObjectProperty(protocol.getLSID(), protocol.getContainer(), AssayPublishService.AUTO_COPY_TARGET_PROPERTY_URI, autoCopyTargetContainerId));
                    }
                    else
                    {
                        props.remove(AssayPublishService.AUTO_COPY_TARGET_PROPERTY_URI);
                    }
                    protocol.setObjectProperties(props);

                    protocol.save(getUser());

                    StringBuilder errors = new StringBuilder();
                    for (GWTDomain<GWTPropertyDescriptor> domain : assay.getDomains())
                    {
                        List<String> domainErrors = updateDomainDescriptor(domain, protocol, provider);
                        for (String error : domainErrors)
                        {
                            errors.append(error).append("\n");
                        }

                        // Need to bail out inside of the loop because some errors may have left the DB connection in
                        // an unusable state.
                        if (errors.length() > 0)
                            throw new AssayException(errors.toString());
                    }

                    transaction.commit();
                    AssayManager.get().clearProtocolCache();
                    return getAssayDefinition(assay.getProtocolId(), false);
                }
                catch (UnexpectedException e)
                {
                    Throwable cause = e.getCause();
                    throw new AssayException(cause.getMessage());
                }
                catch (ExperimentException e)
                {
                    throw new AssayException(e.getMessage());
                }
            }
            else
                throw new AssayException("Only replaceIfExisting == true is supported.");
        }
    }

    private List<String> updateDomainDescriptor(GWTDomain<GWTPropertyDescriptor> domain, ExpProtocol protocol, AssayProvider provider)
    {
        GWTDomain<? extends GWTPropertyDescriptor> previous = getDomainDescriptor(domain.getDomainURI(), protocol.getContainer());
        for (GWTPropertyDescriptor prop : domain.getFields())
        {
            if (prop.getLookupQuery() != null)
            {
                prop.setLookupQuery(prop.getLookupQuery().replace(AbstractAssayProvider.ASSAY_NAME_SUBSTITUTION, protocol.getName()));
            }
        }
        provider.changeDomain(getUser(), protocol, previous, domain);
        return updateDomainDescriptor(previous, domain);
    }

    @Override
    public List<GWTContainer> getStudyContainers()
    {
        Set<Study> publishTargets = AssayPublishService.get().getValidPublishTargets(getUser(), ReadPermission.class);
        // Use a tree set so they're sorted nicely
        Set<Container> containers = new TreeSet<>();
        for (Study study : publishTargets)
        {
            containers.add(study.getContainer());
        }
        List<GWTContainer> result = new ArrayList<>();
        for (Container container : containers)
        {
            result.add(convertToGWTContainer(container));
        }
        return result;
    }

    public boolean canUpdateProtocols()
    {
        Container c = getContainer();
        User u = getUser();
        SecurityPolicy policy = c.getPolicy();
        return policy.hasPermission(u, DesignAssayPermission.class);
    }
}
