/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.assay;

import org.apache.commons.beanutils.ConvertUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.fhcrc.cpas.exp.xml.SimpleTypeNames;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AbstractAssayProvider;
import org.labkey.api.assay.AssayDomainService;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.assay.AssayQCService;
import org.labkey.api.assay.AssayService;
import org.labkey.api.assay.DetectionMethodAssayProvider;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateBasedAssayProvider;
import org.labkey.api.assay.plate.PlateService;
import org.labkey.api.assay.security.DesignAssayPermission;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DbSchema;
import org.labkey.api.data.DbScope;
import org.labkey.api.exp.ExperimentException;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.ObjectProperty;
import org.labkey.api.exp.ProtocolParameter;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainKind;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.exp.xar.XarConstants;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.assay.AssayException;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;
import org.labkey.api.gwt.client.model.GWTContainer;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.PlatformDeveloperPermission;
import org.labkey.api.study.assay.SampleMetadataInputFormat;
import org.labkey.api.study.publish.StudyPublishService;
import org.labkey.api.util.HelpTopic;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.util.UnexpectedException;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.assay.actions.SetDefaultValuesAssayAction;
import org.labkey.assay.plate.PlateManager;
import org.labkey.assay.query.AssayDbSchema;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: brittp
 * Date: Jun 22, 2007
 * Time: 10:01:10 AM
 */
public class AssayDomainServiceImpl extends DomainEditorServiceBase implements AssayDomainService
{
    public static final Logger LOG = LogManager.getLogger(AssayDomainServiceImpl.class);

    public AssayDomainServiceImpl(ViewContext context)
    {
        super(context);
    }

    @Override
    @Nullable
    public GWTProtocol getAssayDefinition(int rowId, boolean copy)
    {
        ExpProtocol protocol = ExperimentService.get().getExpProtocol(rowId);
        if (protocol != null)
        {
            Pair<ExpProtocol, List<Pair<Domain, Map<DomainProperty, Object>>>> assayInfo;
            AssayProvider provider = AssayService.get().getProvider(protocol);
            if (provider != null)
            {
                if (copy)
                    assayInfo = provider.getAssayTemplate(getUser(), getContainer(), protocol);
                else
                    assayInfo = new Pair<>(protocol, provider.getDomains(protocol));
                return getAssayTemplate(provider, assayInfo, copy);
            }
        }

        return null;
    }

    @Override
    public GWTProtocol getAssayTemplate(String providerName)
    {
        AssayProvider provider = AssayService.get().getProvider(providerName);
        if (provider == null)
        {
            throw new NotFoundException("Could not find assay provider " + providerName);
        }
        Pair<ExpProtocol, List<Pair<Domain, Map<DomainProperty, Object>>>> template = provider.getAssayTemplate(getUser(), getContainer());
        return getAssayTemplate(provider, template, false);
    }

    private GWTProtocol getAssayTemplate(AssayProvider provider, Pair<ExpProtocol, List<Pair<Domain, Map<DomainProperty, Object>>>> template, boolean copy)
    {
        ExpProtocol protocol = template.getKey();
        List<GWTDomain<GWTPropertyDescriptor>> gwtDomains = new ArrayList<>();
        for (Pair<Domain, Map<DomainProperty, Object>> domainInfo : template.getValue())
        {
            Domain domain = domainInfo.getKey();
            GWTDomain<GWTPropertyDescriptor> gwtDomain = DomainUtil.getDomainDescriptor(getUser(), domain);

            // If assay is new default value options and default may not have been available in getDomainDescriptor, so try again with provider.
            if (provider.allowDefaultValues(domain) && (gwtDomain.getDefaultValueOptions() == null || gwtDomain.getDefaultValueOptions().length == 0))
                gwtDomain.setDefaultValueOptions(provider.getDefaultValueOptions(domain), provider.getDefaultValueDefault(domain));

            if (copy)
                gwtDomain.setDomainId(0);

            gwtDomain.setAllowFileLinkProperties(provider.isFileLinkPropertyAllowed(template.getKey(), domain));
            ActionURL setDefaultValuesAction = new ActionURL(SetDefaultValuesAssayAction.class, getContainer());
            setDefaultValuesAction.addParameter("providerName", provider.getName());
            gwtDomain.setDomainKindName("Assay");
            gwtDomain.setDefaultValuesURL(setDefaultValuesAction.getLocalURIString());
            gwtDomain.setProvisioned(domain.isProvisioned());
            gwtDomains.add(gwtDomain);

            DomainKind kind = domain.getDomainKind();

            List<GWTPropertyDescriptor> gwtProps = new ArrayList<>();
            List<? extends DomainProperty> properties = domain.getProperties();
            Map<DomainProperty, Object> defaultValues = domainInfo.getValue();
            Set<String> mandatoryPropertyDescriptors = new CaseInsensitiveHashSet(kind.getMandatoryPropertyNames(domain));

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
        result.setStatus(protocol.getStatus() != null ? protocol.getStatus().name() : ExpProtocol.Status.Active.name());
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
            Plate plateTemplate = ((PlateBasedAssayProvider)provider).getPlate(getContainer(), protocol);
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
        result.setQcEnabled(provider.isQCEnabled(protocol));
        result.setPlateMetadata(provider.isPlateMetadataEnabled(protocol));

        // data transform scripts
        List<File> transformScripts = provider.getValidationAndAnalysisScripts(protocol, AssayProvider.Scope.ASSAY_DEF);

        List<String> transformScriptStrings = new ArrayList<>();
        for (File transformScript : transformScripts)
        {
            transformScriptStrings.add(transformScript.getAbsolutePath());
        }
        result.setProtocolTransformScripts(transformScriptStrings);

        ObjectProperty autoLinkValue = protocol.getObjectProperties().get(StudyPublishService.AUTO_LINK_TARGET_PROPERTY_URI);
        if (autoLinkValue != null)
        {
            Container autoLinkTarget = ContainerManager.getForId(autoLinkValue.getStringValue());
            if (autoLinkTarget != null)
            {
                result.setAutoCopyTargetContainer(convertToGWTContainer(autoLinkTarget));
                result.setAutoCopyTargetContainerId(autoLinkTarget.getId());
            }
        }

        ObjectProperty autoLinkContainer = protocol.getObjectProperties().get(StudyPublishService.AUTO_LINK_CATEGORY_PROPERTY_URI);
        if (autoLinkContainer != null)
        {
            result.setAutoLinkCategory(autoLinkContainer.getStringValue());
        }

        result.setAllowTransformationScript((provider.createDataExchangeHandler() != null) && canUpdateTransformationScript());
        result.setAllowBackgroundUpload(provider.supportsBackgroundUpload());
        result.setAllowEditableResults(provider.supportsEditableResults());

        // if the provider supports QC and if there is a valid QC service registered
        result.setAllowQCStates(provider.supportsQC() && AssayQCService.getProvider().supportsQC());

        result.setAllowPlateMetadata(provider.supportsPlateMetadata());

        boolean supportsFlag = provider.supportsFlagColumnType(ExpProtocol.AssayDomainTypes.Result);
        for (GWTDomain d : result.getDomains())
            if (d.getDomainURI().contains(":" + ExpProtocol.AssayDomainTypes.Result.getPrefix() + "."))
                d.setAllowFlagProperties(supportsFlag);

        return result;
    }

    private GWTContainer convertToGWTContainer(Container c)
    {
        return new GWTContainer(c.getId(), c.getRowId(), c.getPath(), c.getName());
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
            for (Plate template : PlateService.get().getPlateTemplates(getContainer()))
                plateTemplates.add(template.getName());
            protocol.setAvailablePlateTemplates(plateTemplates);
        }
    }

    private void setPropertyDomainURIs(ExpProtocol protocol, Set<String> uris, AssayProvider assayProvider)
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
                assayProvider.ensurePropertyDomainName(protocol, prop);
                props.put(prop.getPropertyURI(), prop);
            }
        }
        protocol.setObjectProperties(props);
    }

    @Override
    public GWTProtocol saveChanges(GWTProtocol assay, boolean replaceIfExisting) throws AssayException, ValidationException
    {
        // Synchronize the whole method to prevent saving of new two assay designs with the same name at the same
        // time, which will lead to a SQLException on the UNIQUE constraint on protocol LSIDs
        synchronized (AssayDomainServiceImpl.class)
        {
            if (replaceIfExisting)
            {
                DbSchema schema = AssayDbSchema.getInstance().getSchema();
                try (DbScope.Transaction transaction = schema.getScope().ensureTransaction())
                {
                    if (assay.getAutoLinkCategory() != null && assay.getAutoLinkCategory().length() > 200)
                        throw new AssayException("Linked Dataset Category name must be shorter than 200 characters.");

                    ExpProtocol protocol;
                    boolean isNew = assay.getProtocolId() == null;
                    boolean hasNameChange = false;
                    AssayProvider assayProvider = AssayService.get().getProvider(assay.getProviderName());
                    String oldAssayName = null;
                    if (isNew)
                    {
                        // check for existing assay protocol with the given name before creating
                        if (AssayManager.get().getAssayProtocolByName(getContainer(), assay.getName()) != null)
                            throw new AssayException("Assay protocol already exists for this name.");

                        XarContext context = new XarContext("Domains", getContainer(), getUser());
                        context.addSubstitution("AssayName", PageFlowUtil.encode(assay.getName()));

                        protocol = AssayManager.get().createAssayDefinition(getUser(), getContainer(), assay, context);
                        assay.setProtocolId(protocol.getRowId());

                        Set<String> domainURIs = new HashSet<>();
                        for (GWTDomain domain : assay.getDomains())
                        {
                            domain.setDomainURI(LsidUtils.resolveLsidFromTemplate(domain.getDomainURI(), context));
                            domain.setName(assay.getName() + " " + domain.getName());
                            GWTDomain<GWTPropertyDescriptor> gwtDomain = DomainUtil.getDomainDescriptor(getUser(), domain.getDomainURI(), getContainer());
                            if (gwtDomain == null)
                            {
                                Domain newDomain = DomainUtil.createDomain(PropertyService.get().getDomainKind(domain.getDomainURI()).getKindName(), domain, null, getContainer(), getUser(), domain.getName(), null);
                                domainURIs.add(newDomain.getTypeURI());
                            }
                            else
                            {
                                ValidationException domainErrors = updateDomainDescriptor(domain, protocol, assayProvider, false);
                                if (domainErrors.hasErrors())
                                {
                                    throw domainErrors;
                                }
                                domainURIs.add(domain.getDomainURI());
                            }

                        }
                        setPropertyDomainURIs(protocol, domainURIs, assayProvider);
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
                        oldAssayName = protocol.getName();
                        hasNameChange = !assay.getName().equals(oldAssayName);
                        protocol.setName(assay.getName());
                        protocol.setProtocolDescription(assay.getDescription());
                        if (assay.getStatus() != null)
                            protocol.setStatus(ExpProtocol.Status.valueOf(assay.getStatus()));
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
                            if (hasNameChange && assayProvider.canRename() && XarConstants.APPLICATION_NAME_TEMPLATE_URI.equals(uri) && entry.getValue() != null)
                            {
                                String updatedName = entry.getValue().replace(oldAssayName, assay.getName());
                                param.setValue(SimpleTypeNames.STRING, updatedName);
                            }
                            param.setName(uri.contains("#") ? uri.substring(uri.indexOf("#") + 1) : uri);
                            newParams.put(uri, param);
                        }
                    }
                    protocol.setProtocolParameters(newParams.values());

                    if (hasNameChange)
                    {
                        if (AssayManager.get().getAssayProtocolByName(getContainer(), assay.getName()) != null)
                            throw new ValidationException("Another assay protocol already exists for this name.");
                        ExperimentService.get().handleAssayNameChange(assay.getName(), oldAssayName, assayProvider,  protocol,getUser(), getContainer());
                    }

                    AssayProvider provider = AssayService.get().getProvider(protocol);
                    if (provider instanceof PlateBasedAssayProvider && assay.getSelectedPlateTemplate() != null)
                    {
                        PlateBasedAssayProvider plateProvider = (PlateBasedAssayProvider)provider;
                        Plate plate = PlateManager.get().getPlateByName(getContainer(), assay.getSelectedPlateTemplate());
                        if (plate != null)
                            plateProvider.setPlate(getContainer(), protocol, plate);
                        else
                            throw new AssayException("The selected plate could not be found.  Perhaps it was deleted by another user?");

                        String selectedFormat = assay.getSelectedMetadataInputFormat();
                        SampleMetadataInputFormat inputFormat = SampleMetadataInputFormat.valueOf(selectedFormat);
                        if (inputFormat != null)
                            ((PlateBasedAssayProvider)provider).setMetadataInputFormat(protocol, inputFormat);
                    }

                    // data transform scripts
                    List<File> transformScripts = new ArrayList<>();
                    List<String> submittedScripts = assay.getProtocolTransformScripts();
                    if (!submittedScripts.isEmpty() && !canUpdateTransformationScript())
                        throw new AssayException("You must be a platform developer or site admin to configure assay transformation scripts.");
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

                    ValidationException scriptValidation = provider.setValidationAndAnalysisScripts(protocol, transformScripts);
                    if (scriptValidation.hasErrors())
                    {
                        for (var error : scriptValidation.getErrors())
                        {
                            if (error.getSeverity() == ValidationException.SEVERITY.ERROR)
                                throw scriptValidation;

                            // TODO: return warnings back to client
                            HelpTopic help = error.getHelp();
                            LOG.log(error.getSeverity().getLevel(), error.getMessage()
                                    + (help != null ? "\n  For more information: " + help.getHelpTopicHref() : ""));
                        }
                    }
//
//                    provider.setDetectionMethods(protocol, assay.getAvailableDetectionMethods());

                    provider.setSaveScriptFiles(protocol, assay.isSaveScriptFiles());
                    provider.setEditableResults(protocol, assay.isEditableResults());
                    provider.setEditableRuns(protocol, assay.isEditableRuns());
                    provider.setBackgroundUpload(protocol, assay.isBackgroundUpload());
                    provider.setQCEnabled(protocol, assay.isQcEnabled());
                    provider.setPlateMetadataEnabled(protocol, assay.isPlateMetadata());

                    Map<String, ObjectProperty> props = new HashMap<>(protocol.getObjectProperties());
                    // get the autoLinkTargetContainer from either the id on the assay object entityId
                    String autoLinkTargetContainerId = assay.getAutoCopyTargetContainer() != null ? assay.getAutoCopyTargetContainer().getEntityId() : assay.getAutoCopyTargetContainerId();
                    // verify that the autoLinkTargetContainerId is valid
                    if (autoLinkTargetContainerId != null && ContainerManager.getForId(autoLinkTargetContainerId) == null)
                    {
                        throw new AssayException("No such auto-link target container id: " + autoLinkTargetContainerId);
                    }

                    if (autoLinkTargetContainerId != null)
                    {
                        props.put(StudyPublishService.AUTO_LINK_TARGET_PROPERTY_URI, new ObjectProperty(protocol.getLSID(), protocol.getContainer(), StudyPublishService.AUTO_LINK_TARGET_PROPERTY_URI, autoLinkTargetContainerId));
                    }
                    else
                    {
                        props.remove(StudyPublishService.AUTO_LINK_TARGET_PROPERTY_URI);
                    }

                    String autoLinkCategory = assay.getAutoLinkCategory();
                    if (autoLinkCategory != null)
                    {
                        props.put(StudyPublishService.AUTO_LINK_CATEGORY_PROPERTY_URI, new ObjectProperty(protocol.getLSID(), protocol.getContainer(), StudyPublishService.AUTO_LINK_CATEGORY_PROPERTY_URI, autoLinkCategory));
                    }
                    else
                    {
                        props.remove(StudyPublishService.AUTO_LINK_CATEGORY_PROPERTY_URI);
                    }

                    protocol.setObjectProperties(props);

                    protocol.save(getUser());

                    StringBuilder errors = new StringBuilder();
                    for (GWTDomain<GWTPropertyDescriptor> domain : assay.getDomains())
                    {
                        ValidationException domainErrors = updateDomainDescriptor(domain, protocol, provider, hasNameChange);

                        // Need to bail out inside of the loop because some errors may have left the DB connection in
                        // an unusable state.
                        if (domainErrors.hasErrors())
                            throw domainErrors;
                    }

                    if (assay.getExcludedContainerIds() != null && (!isNew || !assay.getExcludedContainerIds().isEmpty()))
                        ExperimentService.get().ensureDataTypeContainerExclusions(ExperimentService.DataTypeForExclusion.AssayDesign, assay.getExcludedContainerIds(), protocol.getRowId(), getUser());

                    QueryService.get().updateLastModified();
                    transaction.commit();
                    AssayManager.get().clearProtocolCache();
                    return getAssayDefinition(assay.getProtocolId(), false);
                }
                catch (UnexpectedException e)
                {
                    Throwable cause = e.getCause();
                    throw new ValidationException(cause.getMessage());
                }
                catch (ExperimentException e)
                {
                    throw new ValidationException(e.getMessage());
                }
            }
            else
                throw new AssayException("Only replaceIfExisting == true is supported.");
        }
    }

    private ValidationException updateDomainDescriptor(GWTDomain<GWTPropertyDescriptor> domain, ExpProtocol protocol, AssayProvider provider, boolean hasNameChange)
    {
        GWTDomain<GWTPropertyDescriptor> previous = getDomainDescriptor(domain.getDomainURI(), protocol.getContainer());
        for (GWTPropertyDescriptor prop : domain.getFields())
        {
            if (prop.getLookupQuery() != null)
            {
                prop.setLookupQuery(prop.getLookupQuery().replace(AbstractAssayProvider.ASSAY_NAME_SUBSTITUTION, protocol.getName()));
            }
        }
        provider.changeDomain(getUser(), protocol, previous, domain);

        String auditComment = null;
        if (hasNameChange)
        {
            auditComment = "The name of the assay domain '" + previous.getName() + "' was changed to '" + domain.getName() + "'.";
        }

        return DomainUtil.updateDomainDescriptor(previous, domain, getContainer(), getUser(), hasNameChange, auditComment);
    }

    private boolean canUpdateProtocols()
    {
        Container c = getContainer();
        User u = getUser();
        SecurityPolicy policy = c.getPolicy();
        return policy.hasPermission(u, DesignAssayPermission.class);
    }

    private boolean canUpdateTransformationScript()
    {
        Container c = getContainer();
        User u = getUser();
        SecurityPolicy policy = c.getPolicy();
        return policy.hasPermission(u, PlatformDeveloperPermission.class);
    }
}
