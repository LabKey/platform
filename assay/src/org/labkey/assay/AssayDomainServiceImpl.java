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
import org.labkey.api.assay.plate.FilterCriteria;
import org.labkey.api.assay.plate.Plate;
import org.labkey.api.assay.plate.PlateBasedAssayProvider;
import org.labkey.api.assay.plate.PlateService;
import org.labkey.api.assay.security.DesignAssayPermission;
import org.labkey.api.assay.transform.AnalysisScript;
import org.labkey.api.assay.transform.DataTransformService;
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
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.exp.xar.XarConstants;
import org.labkey.api.gwt.client.DefaultValueType;
import org.labkey.api.gwt.client.assay.model.GWTProtocol;
import org.labkey.api.gwt.client.model.GWTContainer;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.labkey.api.gwt.client.model.GWTPropertyDescriptor;
import org.labkey.api.query.MetadataUnavailableException;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.ValidationException;
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
import org.labkey.api.writer.ContainerUser;
import org.labkey.assay.actions.SetDefaultValuesAssayAction;
import org.labkey.assay.plate.PlateManager;
import org.labkey.assay.query.AssayDbSchema;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class AssayDomainServiceImpl implements AssayDomainService, ContainerUser
{
    public static final Logger LOG = LogManager.getLogger(AssayDomainServiceImpl.class);
    private final User _user;
    private final Container _container;

    public AssayDomainServiceImpl(User user, Container container)
    {
        _user = user;
        _container = container;
    }

    @Override
    public User getUser()
    {
        return _user;
    }

    @Override
    public Container getContainer()
    {
        return _container;
    }

    @Override
    @Nullable
    public GWTProtocol getAssayDefinition(long rowId, boolean copy)
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
                    assayInfo = new Pair<>(protocol, provider.getDomainsAndDefaultValues(protocol));
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
            throw new NotFoundException("Could not find assay provider " + providerName);

        Pair<ExpProtocol, List<Pair<Domain, Map<DomainProperty, Object>>>> template = provider.getAssayTemplate(getUser(), getContainer());
        return getAssayTemplate(provider, template, false);
    }

    private List<GWTDomain<GWTPropertyDescriptor>> getDomains(
        AssayProvider provider,
        ExpProtocol protocol,
        List<Pair<Domain, Map<DomainProperty, Object>>> domainInfos,
        boolean copy
    )
    {
        List<GWTDomain<GWTPropertyDescriptor>> gwtDomains = new ArrayList<>();
        String resultsDomainPrefix = ":" + ExpProtocol.AssayDomainTypes.Result.getPrefix() + ".";
        List<FilterCriteria> allFilterCriteria = null;

        for (Pair<Domain, Map<DomainProperty, Object>> domainInfo : domainInfos)
        {
            Domain domain = domainInfo.getKey();
            GWTDomain<GWTPropertyDescriptor> gwtDomain = DomainUtil.getDomainDescriptor(getUser(), domain);
            boolean isResultsDomain = gwtDomain.getDomainURI().contains(resultsDomainPrefix);

            // If assay is new default value options and default may not have been available in getDomainDescriptor, so try again with provider.
            if (provider.allowDefaultValues(domain) && (gwtDomain.getDefaultValueOptions() == null || gwtDomain.getDefaultValueOptions().length == 0))
                gwtDomain.setDefaultValueOptions(provider.getDefaultValueOptions(domain), provider.getDefaultValueDefault(domain));

            if (copy)
                gwtDomain.setDomainId(0);

            gwtDomain.setAllowFileLinkProperties(provider.isFileLinkPropertyAllowed(protocol, domain));
            ActionURL setDefaultValuesAction = new ActionURL(SetDefaultValuesAssayAction.class, getContainer());
            setDefaultValuesAction.addParameter("providerName", provider.getName());
            gwtDomain.setDomainKindName("Assay");
            gwtDomain.setDefaultValuesURL(setDefaultValuesAction.getLocalURIString());
            gwtDomain.setProvisioned(domain.isProvisioned());

            List<GWTPropertyDescriptor> fields = new ArrayList<>();
            Set<String> mandatoryPropertyDescriptors = new CaseInsensitiveHashSet(domain.getDomainKind().getMandatoryPropertyNames(domain));

            Map<String, GWTPropertyDescriptor> domainFields = new HashMap<>();
            for (GWTPropertyDescriptor field : gwtDomain.getFields())
                domainFields.put(field.getName(), field);

            for (DomainProperty prop : domain.getProperties())
            {
                GWTPropertyDescriptor domainField = domainFields.get(prop.getName());
                GWTPropertyDescriptor gwtProp = new GWTPropertyDescriptor(domainField, copy);

                if (gwtProp.getDefaultValueType() == null)
                {
                    // Explicitly set these "special" properties NOT to remember the user's last entered
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

                if (AbstractAssayProvider.TARGET_STUDY_PROPERTY_NAME.equals(gwtProp.getName()))
                {
                    Object defaultValue = domainInfo.getValue().get(prop);
                    if (defaultValue instanceof String containerId)
                    {
                        Container studyContainer = ContainerManager.getForId(containerId);
                        if (studyContainer != null)
                            gwtProp.setDefaultDisplayValue(studyContainer.getPath());
                    }
                }

                if (provider.isMandatoryDomainProperty(domain, prop.getName()))
                    mandatoryPropertyDescriptors.add(prop.getName());

                if (isResultsDomain)
                {
                    if (allFilterCriteria == null)
                        allFilterCriteria = provider.getFilterCriteria(protocol);

                    List<FilterCriteria> fieldFilterCriteria = allFilterCriteria.stream()
                            .filter(criterion -> prop.getPropertyId() == criterion.referencePropertyId())
                            .toList();

                    gwtProp.setFilterCriteria(FilterCriteria.toGWTFilterCriteria(fieldFilterCriteria));
                }

                fields.add(gwtProp);
            }

            fields.addAll(gwtDomain.getCalculatedFields());
            gwtDomain.setFields(fields);
            gwtDomain.setMandatoryFieldNames(mandatoryPropertyDescriptors);

            gwtDomains.add(gwtDomain);
        }

        return gwtDomains;
    }

    private GWTProtocol getAssayTemplate(AssayProvider provider, Pair<ExpProtocol, List<Pair<Domain, Map<DomainProperty, Object>>>> template, boolean copy)
    {
        ExpProtocol protocol = template.getKey();

        GWTProtocol result = new GWTProtocol();
        result.setProtocolId(protocol.getRowId() > 0 ? protocol.getRowId() : null);
        result.setDomains(getDomains(provider, protocol, template.getValue(), copy));
        result.setName(protocol.getName());
        result.setProviderName(provider.getName());
        result.setDescription(protocol.getDescription());
        result.setStatus(protocol.getStatus() != null ? protocol.getStatus().name() : ExpProtocol.Status.Active.name());

        // Configure protocol parameters
        {
            Map<String, String> gwtProtocolParams = new HashMap<>();
            for (ProtocolParameter property : protocol.getProtocolParameters().values())
            {
                if (property.getXmlBeanValueType() != SimpleTypeNames.STRING)
                    throw new IllegalStateException("Did not expect non-string protocol parameter " + property.getOntologyEntryURI() + " (" + property.getValueType() + ")");

                gwtProtocolParams.put(property.getOntologyEntryURI(), property.getStringValue());
            }
            result.setProtocolParameters(gwtProtocolParams);
        }

        if (provider instanceof PlateBasedAssayProvider plateProvider)
        {
            Plate plateTemplate = plateProvider.getPlate(getContainer(), protocol);
            if (plateTemplate != null)
                result.setSelectedPlateTemplate(plateTemplate.getName());
            setPlateTemplateList(provider, result);

            SampleMetadataInputFormat[] formats = plateProvider.getSupportedMetadataInputFormats();
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
            result.setSelectedMetadataInputFormat(plateProvider.getMetadataInputFormat(protocol).name());
        }

        if (provider instanceof DetectionMethodAssayProvider dmProvider)
        {
            String method = dmProvider.getSelectedDetectionMethod(getContainer(), protocol);
            if (method != null)
                result.setSelectedDetectionMethod(method);
            result.setAvailableDetectionMethods(dmProvider.getAvailableDetectionMethods());
        }

        List<AnalysisScript> typeScripts = provider.getValidationAndAnalysisScripts(protocol, AssayProvider.Scope.ASSAY_TYPE);
        if (!typeScripts.isEmpty())
        {
            List<String> scriptNames = new ArrayList<>();
            for (AnalysisScript script : typeScripts)
                scriptNames.add(script.getScriptPath());

            result.setModuleTransformScripts(scriptNames);
        }
        result.setSaveScriptFiles(provider.isSaveScriptFiles(protocol));
        result.setEditableResults(provider.isEditableResults(protocol));
        result.setEditableRuns(provider.isEditableRuns(protocol));
        result.setBackgroundUpload(provider.isBackgroundUpload(protocol));
        result.setQcEnabled(provider.isQCEnabled(protocol));
        result.setPlateMetadata(provider.isPlateMetadataEnabled(protocol));

        // data transform scripts
        List<AnalysisScript> transformScripts = provider.getValidationAndAnalysisScripts(protocol, AssayProvider.Scope.ASSAY_DEF);

        List<Map<String, Object>> transformScriptStrings = new ArrayList<>();
        for (AnalysisScript transformScript : transformScripts)
        {
            transformScriptStrings.add(Map.of(
                    "scriptPath", transformScript.getScriptPath(),
                    "runOnEdit", transformScript.canExecute(DataTransformService.TransformOperation.UPDATE),
                    "runOnImport", transformScript.canExecute(DataTransformService.TransformOperation.INSERT)
            ));
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
        result.setAllowPlateMetadata(provider.supportsPlateMetadata(protocol));

        return result;
    }

    private GWTContainer convertToGWTContainer(Container c)
    {
        return new GWTContainer(c.getId(), c.getRowId(), c.getPath(), c.getName());
    }

    private void setPlateTemplateList(AssayProvider provider, GWTProtocol protocol)
    {
        if (provider instanceof PlateBasedAssayProvider)
        {
            List<String> plateTemplates = new ArrayList<>();
            for (Plate template : PlateService.get().getPlates(getContainer()))
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
    public GWTProtocol saveChanges(GWTProtocol assay, boolean replaceIfExisting) throws ValidationException
    {
        // Synchronize the whole method to prevent saving of new two assay designs with the same name at the same
        // time, which will lead to a SQLException on the UNIQUE constraint on protocol LSIDs
        synchronized (AssayDomainServiceImpl.class)
        {
            if (!replaceIfExisting)
                throw new ValidationException("Only replaceIfExisting == true is supported.");

            DbSchema schema = AssayDbSchema.getInstance().getSchema();
            try (DbScope.Transaction transaction = schema.getScope().ensureTransaction())
            {
                if (assay.getAutoLinkCategory() != null && assay.getAutoLinkCategory().length() > 200)
                    throw new ValidationException("Linked Dataset Category name must be shorter than 200 characters.");

                Map<String, Object> newProps = assay.getAuditRecordMap();
                Map<String, Object> oldProps = new LinkedHashMap<>();

                StringBuilder changeDetails = new StringBuilder();
                ExpProtocol protocol;
                boolean isNew = assay.getProtocolId() == null;
                boolean hasNameChange = false;
                AssayProvider assayProvider = AssayService.get().getProvider(assay.getProviderName());
                String oldAssayName = null;

                String reservedError = DomainUtil.validateReservedName(assay.getName(), "Assay Design");
                if (reservedError != null)
                    throw new ValidationException(reservedError);

                String nameError = DomainUtil.validateDomainName(assay.getName(), "Assay Design", false);
                if (nameError != null)
                    throw new ValidationException(nameError);

                // Issue 53831: add a specific check for assay name length since we append onto the name when creating the assay domains (ex. "<assay name> Batch Fields")
                // which makes that actual max less than the DB size of 200
                int assayNameLengthMax = 150;
                if (assay.getName().length() > assayNameLengthMax)
                    throw new ValidationException("Value is too long for assay design name, a maximum length of " + assayNameLengthMax + " is allowed. The supplied value, '" + StringUtils.abbreviateMiddle(assay.getName(), "...", 50) + "', was " + assay.getName().length() + " characters long.");

                if (isNew)
                {
                    // check for existing assay protocol with the given name before creating
                    if (AssayManager.get().getAssayProtocolByName(getContainer(), assay.getName()) != null)
                        throw new ValidationException("Assay protocol already exists for this name.");

                    XarContext context = new XarContext("Domains", getContainer(), getUser());
                    context.addSubstitution("AssayName", PageFlowUtil.encode(assay.getName()));

                    protocol = AssayManager.get().createAssayDefinition(getUser(), getContainer(), assay, context);
                    assay.setProtocolId(protocol.getRowId());

                    Set<String> domainURIs = new HashSet<>();
                    for (GWTDomain<GWTPropertyDescriptor> domain : assay.getDomains())
                    {
                        domain.setDomainURI(LsidUtils.resolveLsidFromTemplate(domain.getDomainURI(), context));
                        domain.setName(assay.getName() + " " + domain.getName());
                        GWTDomain<GWTPropertyDescriptor> gwtDomain = DomainUtil.getDomainDescriptor(getUser(), domain.getDomainURI(), getContainer(), true);
                        if (gwtDomain == null)
                        {
                            Domain newDomain = DomainUtil.createDomain(PropertyService.get().getDomainKind(domain.getDomainURI()).getKindName(), domain, null, getContainer(), getUser(), domain.getName(), null, false);
                            domainURIs.add(newDomain.getTypeURI());
                        }
                        else
                        {
                            GWTDomain<GWTPropertyDescriptor> previous = DomainUtil.getDomainDescriptor(getUser(), domain.getDomainURI(), protocol.getContainer());
                            updateDomainDescriptor(assayProvider, protocol, previous, domain, false, null, assay.getAuditUserComment(), oldProps, newProps);
                            domainURIs.add(domain.getDomainURI());
                        }
                    }

                    setPropertyDomainURIs(protocol, domainURIs, assayProvider);
                }
                else
                {
                    protocol = ExperimentService.get().getExpProtocol(assay.getProtocolId().intValue());
                    if (protocol == null)
                        throw new ValidationException("Assay design has been deleted");

                    oldProps = protocol.getAuditRecordMap(AssayService.get().getProvider(protocol));

                    // ensure that the user has edit perms in this container
                    if (!canUpdateProtocols())
                        throw new ValidationException("You do not have sufficient permissions to update this Assay");

                    if (!protocol.getContainer().equals(getContainer()))
                        throw new ValidationException("Assays can only be edited in the folder where they were created.  " +
                                "This assay was created in folder " + protocol.getContainer().getPath());

                    oldAssayName = protocol.getName();
                    hasNameChange = !assay.getName().equals(oldAssayName);
                    if (hasNameChange)
                    {
                        boolean casingOnlyChange = assay.getName().equalsIgnoreCase(oldAssayName);
                        if (!casingOnlyChange && AssayManager.get().getAssayProtocolByName(getContainer(), assay.getName()) != null)
                            throw new ValidationException("Another assay protocol already exists for this name.");

                        changeDetails.append("The name of the assay domain '" + oldAssayName + "' was changed to '" + assay.getName() + "'.");
                    }
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
                    ExperimentService.get().handleAssayNameChange(assay.getName(), oldAssayName, assayProvider,  protocol,getUser(), getContainer());

                AssayProvider provider = AssayService.get().getProvider(protocol);
                if (provider instanceof PlateBasedAssayProvider plateProvider && assay.getSelectedPlateTemplate() != null)
                {
                    Plate plate = PlateManager.get().getPlateByName(getContainer(), assay.getSelectedPlateTemplate());
                    if (plate == null)
                        throw new ValidationException("The selected plate could not be found.  Perhaps it was deleted by another user?");

                    plateProvider.setPlate(getContainer(), protocol, plate);
                    String selectedFormat = assay.getSelectedMetadataInputFormat();
                    SampleMetadataInputFormat inputFormat = SampleMetadataInputFormat.valueOf(selectedFormat);
                    if (inputFormat != null)
                        plateProvider.setMetadataInputFormat(protocol, inputFormat);
                }

                // data transform scripts
                List<AnalysisScript> transformScripts = new ArrayList<>();
                List<Map<String, Object>> submittedScripts = assay.getProtocolTransformScripts();
                if (!submittedScripts.isEmpty() && !canUpdateTransformationScript())
                    throw new ValidationException("You must be a platform developer or site admin to configure assay transformation scripts.");
                for (Map<String, Object> map : assay.getProtocolTransformScripts())
                {
                    String script = (String) map.get("scriptPath");
                    if (!StringUtils.isBlank(script))
                    {
                        Set<DataTransformService.TransformOperation> transformOperations = new HashSet<>();
                        if ((Boolean) map.get("runOnImport"))
                            transformOperations.add(DataTransformService.TransformOperation.INSERT);
                        if ((Boolean) map.get("runOnEdit"))
                            transformOperations.add(DataTransformService.TransformOperation.UPDATE);

                        transformScripts.add(new AnalysisScript(new File(script), transformOperations));
                    }
                }

                if (provider instanceof DetectionMethodAssayProvider dmProvider && assay.getSelectedDetectionMethod() != null)
                {
                    String detectionMethod = assay.getSelectedDetectionMethod();
                    if (detectionMethod == null)
                        throw new ValidationException("The selected detection method could not be found.");

                    if (!isNew)
                    {
                        String oldDetectionMethod = dmProvider.getSelectedDetectionMethod(getContainer(), protocol);
                        if (oldDetectionMethod != null && !StringUtils.isEmpty(oldDetectionMethod))
                            oldProps.put("DetectionMethod", oldDetectionMethod);
                    }
                    if (!StringUtils.isEmpty(detectionMethod))
                        newProps.put("DetectionMethod", detectionMethod);
                    dmProvider.setSelectedDetectionMethod(getContainer(), protocol, detectionMethod);
                }

                Pair<ValidationException, Pair<String, String>> scriptValidationResult = provider.setValidationAndAnalysisScripts(protocol, transformScripts);
                ValidationException scriptValidation = scriptValidationResult.first;
                Pair<String, String> transformChanges = scriptValidationResult.second;
                if (transformChanges != null)
                {
                    if (!isNew && !StringUtils.isEmpty(transformChanges.first))
                        oldProps.put("TransformScripts", transformChanges.first);
                    if (!StringUtils.isEmpty(transformChanges.second))
                        newProps.put("TransformScripts", transformChanges.second);
                }
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
                    throw new ValidationException("No such auto-link target container id: " + autoLinkTargetContainerId);
                }

                if (autoLinkTargetContainerId != null)
                    props.put(StudyPublishService.AUTO_LINK_TARGET_PROPERTY_URI, new ObjectProperty(protocol.getLSID(), protocol.getContainer(), StudyPublishService.AUTO_LINK_TARGET_PROPERTY_URI, autoLinkTargetContainerId));
                else
                    props.remove(StudyPublishService.AUTO_LINK_TARGET_PROPERTY_URI);

                String autoLinkCategory = assay.getAutoLinkCategory();
                if (autoLinkCategory != null)
                    props.put(StudyPublishService.AUTO_LINK_CATEGORY_PROPERTY_URI, new ObjectProperty(protocol.getLSID(), protocol.getContainer(), StudyPublishService.AUTO_LINK_CATEGORY_PROPERTY_URI, autoLinkCategory));
                else
                    props.remove(StudyPublishService.AUTO_LINK_CATEGORY_PROPERTY_URI);

                protocol.setObjectProperties(props);

                protocol.save(getUser());

                if (assay.getExcludedContainerIds() != null && (!isNew || !assay.getExcludedContainerIds().isEmpty()))
                {
                    Pair<Collection<String>, Collection<String>> exclusionChanges = ExperimentService.get().ensureDataTypeContainerExclusions(ExperimentService.DataTypeForExclusion.AssayDesign, assay.getExcludedContainerIds(), protocol.getRowId(), getUser());
                    if (!isNew)
                        oldProps.put("ContainerExclusions", exclusionChanges.first);
                    newProps.put("ContainerExclusions", exclusionChanges.second);
                }
                else
                    ExperimentService.get().ensureDataTypeContainerExclusionsNonAdmin(ExperimentService.DataTypeForExclusion.AssayDesign, protocol.getRowId(), getContainer(), getUser());

                for (GWTDomain<GWTPropertyDescriptor> domain : assay.getDomains())
                {
                    GWTDomain<GWTPropertyDescriptor> domainDescriptor = DomainUtil.getDomainDescriptor(getUser(), domain.getDomainURI(), protocol.getContainer());
                    boolean hasExistingCalcFields = domainDescriptor != null && !domainDescriptor.getCalculatedFields().isEmpty();

                    updateDomainDescriptor(provider, protocol, domainDescriptor, domain, hasNameChange, changeDetails.toString(), assay.getAuditUserComment(), oldProps, newProps);
                    QueryService.get().saveCalculatedFieldsMetadata(domainDescriptor.getSchemaName(), domainDescriptor.getQueryName(), null, domain.getCalculatedFields(), hasExistingCalcFields, getUser(), protocol.getContainer());
                }

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
            catch (ExperimentException | MetadataUnavailableException e)
            {
                throw new ValidationException(e.getMessage());
            }
        }
    }

    private void updateDomainDescriptor(
        AssayProvider provider,
        ExpProtocol protocol,
        GWTDomain<GWTPropertyDescriptor> original,
        GWTDomain<GWTPropertyDescriptor> update,
        boolean hasNameChange,
        String auditComment,
        @Nullable String auditUserComment,
        @Nullable Map<String, Object> oldRecordMap,
        @Nullable Map<String, Object> newRecordMap
    ) throws ValidationException
    {
        for (GWTPropertyDescriptor prop : update.getFields())
        {
            if (prop.getLookupQuery() != null)
                prop.setLookupQuery(prop.getLookupQuery().replace(AbstractAssayProvider.ASSAY_NAME_SUBSTITUTION, protocol.getName()));
        }

        // Before update
        provider.beforeDomainChange(getUser(), protocol, original, update);

        // Update
        ValidationException validationErrors = DomainUtil.updateDomainDescriptor(original, update, getContainer(), getUser(), hasNameChange, auditComment, auditUserComment, oldRecordMap, newRecordMap);
        if (validationErrors.hasErrors())
            throw validationErrors;

        // After update
        provider.afterDomainChange(getUser(), protocol, original, update);
    }

    private boolean canUpdateProtocols()
    {
        Container c = getContainer();
        User u = getUser();
        return c.hasPermission(u, DesignAssayPermission.class);
    }

    private boolean canUpdateTransformationScript()
    {
        Container c = getContainer();
        User u = getUser();
        return c.hasPermission(u, PlatformDeveloperPermission.class);
    }
}
