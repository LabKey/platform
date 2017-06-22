/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import org.apache.log4j.Logger;
import org.apache.xmlbeans.XmlError;
import org.apache.xmlbeans.XmlException;
import org.apache.xmlbeans.XmlOptions;
import org.fhcrc.cpas.exp.xml.DomainDescriptorType;
import org.fhcrc.cpas.exp.xml.PropertyDescriptorType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpDataRunInput;
import org.labkey.api.exp.api.ExpExperiment;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpProtocol.AssayDomainTypes;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.exp.api.IAssayDomainType;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.module.Module;
import org.labkey.api.module.ModuleHtmlView;
import org.labkey.api.module.ModuleLoader;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.api.qc.DataExchangeHandler;
import org.labkey.api.query.FieldKey;
import org.labkey.api.resource.FileResource;
import org.labkey.api.resource.Resource;
import org.labkey.api.security.User;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.api.study.TimepointType;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.actions.UploadWizardAction;
import org.labkey.api.study.assay.AssayDataType;
import org.labkey.api.study.assay.AssayPipelineProvider;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssaySaveHandler;
import org.labkey.api.study.assay.AssayTableMetadata;
import org.labkey.api.study.assay.AssayUrls;
import org.labkey.api.study.assay.TsvDataHandler;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.FileType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;
import org.labkey.api.view.HttpView;
import org.labkey.api.view.JspView;
import org.labkey.api.view.NotFoundException;
import org.labkey.api.view.ViewContext;
import org.labkey.api.view.WebPartView;
import org.labkey.study.StudyModule;
import org.labkey.study.assay.xml.DomainDocument;
import org.labkey.study.assay.xml.ProviderType;
import org.labkey.study.controllers.assay.AssayController;
import org.springframework.validation.BindException;
import org.springframework.web.servlet.ModelAndView;

import javax.script.ScriptEngineManager;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * User: kevink
 * Date: Dec 10, 2008 2:20:38 PM
 */
public class ModuleAssayProvider extends TsvAssayProvider
{
    private static final Logger LOG = Logger.getLogger(ModuleAssayProvider.class);
    private static final String DOMAINS_DIR_NAME = "domains";

    public static class ModuleAssayException extends RuntimeException
    {
        public ModuleAssayException(String message)
        {
            super(message);
        }

        public ModuleAssayException(String message, Throwable cause)
        {
            super(message, cause);
        }
    }

    private static final String UPLOAD_VIEW_FILENAME = "upload.html";
    private static final String BEGIN_VIEW_FILENAME = "begin.html";

    private static final String INVALID_SAVE_HANDLER_CLASS = "The specified saveHandler class specified does not exist";
    private static final String INVALID_SAVE_HANDLER_INTERFACE = "The specified saveHandler class does not implement the AssaySaveHandler interface";

    private final Resource basePath;
    private final String name;
    private String description;
    private Class saveHandlerClass;

    private FieldKey participantIdKey;
    private FieldKey visitIdKey;
    private FieldKey dateKey;
    private FieldKey specimenIdKey;

    private List<ScriptMetadata> _scriptMetadata = new ArrayList<>();

    private List<AssayDataType> _relatedDataTypes = new ArrayList<>();

    private Set<String> _missingScriptWarnings = new HashSet<>();

    public ModuleAssayProvider(String name, Module module, Resource basePath, ProviderType providerConfig)
    {
        super(name + "Protocol", name + "Run", module);
        this.name = name;
        this.basePath = basePath;

        init(providerConfig);
    }

    protected void init(ProviderType providerConfig)
    {
        if (providerConfig == null)
            return;

        description = providerConfig.isSetDescription() ? providerConfig.getDescription() : getName();

        ProviderType.FieldKeys fieldKeys = providerConfig.getFieldKeys();
        if (fieldKeys != null)
        {
            if (fieldKeys.isSetParticipantId())
                participantIdKey = FieldKey.fromString(fieldKeys.getParticipantId());
            if (fieldKeys.isSetVisitId())
                visitIdKey = FieldKey.fromString(fieldKeys.getVisitId());
            if (fieldKeys.isSetDate())
                dateKey = FieldKey.fromString(fieldKeys.getDate());
            if (fieldKeys.isSetSpecimenId())
                specimenIdKey = FieldKey.fromString(fieldKeys.getSpecimenId());
        }

        if (providerConfig.getInputDataFileSuffixArray().length > 0)
        {
            List<String> suffixes = Arrays.asList(providerConfig.getInputDataFileSuffixArray());
            _dataType = new AssayDataType(TsvDataHandler.NAMESPACE, new FileType(suffixes, suffixes.get(0)));
        }

        if (providerConfig.isSetPrimaryDataFileType())
        {
            // Prefer this over inputDataFileSuffix, which is deprecated
            _dataType = createAssayDataTypeFromXML(providerConfig.getPrimaryDataFileType(), ExpDataRunInput.DEFAULT_ROLE, TsvDataHandler.NAMESPACE);
        }

        for (org.labkey.study.assay.xml.AssayDataType assayDataType : providerConfig.getRelatedDataFileTypeArray())
        {
            _relatedDataTypes.add(createAssayDataTypeFromXML(assayDataType, null, RELATED_FILE_DATA_TYPE.getNamespacePrefix()));
        }

        // Remember the preferred order of the transform scripts
        if (providerConfig.isSetTransformScripts())
        {
            for (ProviderType.TransformScripts.TransformScript transformScript : providerConfig.getTransformScripts().getTransformScriptArray())
            {
                _scriptMetadata.add(new ScriptMetadata(transformScript.getFileName(), null, null));
            }
        }

        //required modules
        if (providerConfig.isSetRequiredModules() && providerConfig.getRequiredModules() != null)
        {
            for (String moduleName : providerConfig.getRequiredModules().getModuleNameArray())
            {
                Module m = ModuleLoader.getInstance().getModule(moduleName);
                if (m == null)
                {
                    LOG.error("unknown required module referenced in assay provider " + providerConfig.getName() + " assay definition: [" + moduleName + "]");
                }
                else
                {
                    _requiredModules.add(m);
                }
            }
        }

        if (providerConfig.isSetSaveHandler())
            setSaveHandlerClass(providerConfig.getSaveHandler());
    }

    private AssayDataType createAssayDataTypeFromXML(org.labkey.study.assay.xml.AssayDataType inputConfig, String defaultRole, String defaultNamespace)
    {
        String role = defaultRole;
        if (inputConfig.isSetRole())
        {
            role = inputConfig.getRole();
        }
        String namespacePrefix = defaultNamespace;
        if (inputConfig.isSetNamespacePrefix())
        {
            namespacePrefix = inputConfig.getNamespacePrefix();
        }
        List<String> suffixes = new ArrayList<>();
        String defaultSuffix = null;
        for (org.labkey.study.assay.xml.AssayDataType.FileSuffix fileSuffix : inputConfig.getFileSuffixArray())
        {
            String suffix = fileSuffix.getStringValue();
            suffixes.add(suffix);
            if (defaultSuffix == null && fileSuffix.getDefault())
            {
                defaultSuffix = suffix;
            }
        }
        if (defaultSuffix == null)
        {
            defaultSuffix = suffixes.get(0);
        }
        return new AssayDataType(namespacePrefix, new FileType(suffixes, defaultSuffix), role);
    }

    @Override
    public String toString()
    {
        return "Module assay provider: " + getName();
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public String getResourceName()
    {
        return basePath.getName();
    }

    @Override
    public String getDescription()
    {
        return description;
    }

    @Override
    public AssaySaveHandler getSaveHandler()
    {
        if (saveHandlerClass != null)
        {
            try
            {
                AssaySaveHandler saveHandler = (AssaySaveHandler) saveHandlerClass.newInstance();
                saveHandler.setProvider(this);
                return saveHandler;
            }
            catch(Exception e)
            {
                throw getWrappedModuleAssayException("Unable to create AssaySaveHandler of type " + saveHandlerClass.getName(), e);
            }
        }
        else
        {
            return super.getSaveHandler();
        }
    }

    @Override @NotNull
    public AssayTableMetadata getTableMetadata(@NotNull ExpProtocol protocol)
    {
        AssayTableMetadata metadata = super.getTableMetadata(protocol);
        return new AssayTableMetadata(this, protocol, metadata.getSpecimenDetailParentFieldKey(), metadata.getRunFieldKeyFromResults(), metadata.getResultRowIdFieldKey(), metadata.getDatasetRowIdPropertyName())
        {
            @Override
            public FieldKey getParticipantIDFieldKey()
            {
                if (participantIdKey != null)
                    return participantIdKey;
                return super.getParticipantIDFieldKey();
            }

            @Override
            public FieldKey getVisitIDFieldKey(TimepointType timepointType)
            {
                if (timepointType == TimepointType.VISIT)
                {
                    if (visitIdKey != null)
                        return visitIdKey;
                }
                else
                {
                    if (dateKey != null)
                        return dateKey;
                }
                return super.getVisitIDFieldKey(timepointType);
            }

            @Override
            public FieldKey getSpecimenIDFieldKey()
            {
                if (specimenIdKey != null)
                    return specimenIdKey;
                return super.getSpecimenIDFieldKey();
            }
        };
    }

    private DomainDescriptorType parseDomain(IAssayDomainType domainType) throws ModuleAssayException
    {
        // TODO: Shouldn't this use the cache? Looks like this isn't called too much... and unclear what we'd do if the domain definition changed midstream...
        Resource domainFile = getDeclaringModule().getModuleResolver().lookup(basePath.getPath().append(DOMAINS_DIR_NAME, domainType.getName().toLowerCase() + ".xml"));
        if (domainFile == null || !domainFile.exists())
            return null;

        try
        {
            DomainDocument doc = DomainDocument.Factory.parse(domainFile.getInputStream());
            DomainDescriptorType xDomain = doc.getDomain();
            ArrayList<XmlError> errors = new ArrayList<>();
            XmlOptions options = new XmlOptions().setErrorListener(errors);
            if (xDomain != null && xDomain.validate(options))
            {
                if (!xDomain.isSetName())
                    xDomain.setName(domainType.getName() + " Fields");

                if (!xDomain.isSetDomainURI())
                    xDomain.setDomainURI(domainType.getLsidTemplate());

                return xDomain;
            }

            if (errors.size() > 0)
            {
                StringBuilder sb = new StringBuilder();
                while (errors.size() > 0)
                {
                    XmlError error = errors.remove(0);
                    sb.append(error.toString());
                    if (errors.size() > 0)
                        sb.append("\n");
                }
                throw getWrappedModuleAssayException("Unable to parse " + domainFile + ": " + sb.toString());
            }
        }
        catch (IOException | XmlException e)
        {
            throw getWrappedModuleAssayException("Unable to parse " + domainFile + ": " + e.toString(), e);
        }

        return null;
    }

    /** @return a domain and its default values */
    protected Pair<Domain, Map<DomainProperty, Object>> createDomain(Container c, User user, IAssayDomainType domainType)
    {
        DomainDescriptorType xDomain = parseDomain(domainType);

        if (xDomain != null)
        {
            return PropertyService.get().createDomain(c, xDomain);
        }
        return null;
    }

    @Override
    protected Pair<Domain, Map<DomainProperty, Object>> createBatchDomain(Container c, User user)
    {
        Pair<Domain, Map<DomainProperty, Object>> result = createDomain(c, user, AssayDomainTypes.Batch);
        if (result != null)
            return result;
        return super.createBatchDomain(c, user);
    }

    @Override
    protected Pair<Domain, Map<DomainProperty, Object>> createRunDomain(Container c, User user)
    {
        Pair<Domain, Map<DomainProperty, Object>> result = createDomain(c, user, AssayDomainTypes.Run);
        if (result != null)
            return result;
        return super.createRunDomain(c, user);
    }

    @Override
    protected Pair<Domain, Map<DomainProperty, Object>> createResultDomain(Container c, User user)
    {
        Pair<Domain, Map<DomainProperty, Object>> result = createDomain(c, user, AssayDomainTypes.Result);
        if (result != null)
            return result;
        return super.createResultDomain(c, user);
    }

    @Override
    protected Map<String, Set<String>> getRequiredDomainProperties()
    {
        Map<IAssayDomainType, DomainDescriptorType> domainsDescriptors = new LinkedHashMap<>(AssayDomainTypes.values().length);
        for (IAssayDomainType domainType : AssayDomainTypes.values())
        {
            DomainDescriptorType xDomain = parseDomain(domainType);
            if (xDomain != null)
                domainsDescriptors.put(domainType, xDomain);
        }

        Map<String, Set<String>> required = new HashMap<>();
        for (Map.Entry<IAssayDomainType, DomainDescriptorType> domainDescriptor : domainsDescriptors.entrySet())
        {
            IAssayDomainType domainType = domainDescriptor.getKey();
            DomainDescriptorType xDescriptor = domainDescriptor.getValue();
            PropertyDescriptorType[] xProperties = xDescriptor.getPropertyDescriptorArray();
            LinkedHashSet<String> properties = new LinkedHashSet<>(xProperties.length);
            for (PropertyDescriptorType xProp : xProperties)
                properties.add(xProp.getName());

            required.put(domainType.getPrefix(), properties);
        }
        return required;
    }

    protected String getViewResourcePath(IAssayDomainType domainType, boolean details)
    {
        String viewName = domainType.getName().toLowerCase();
        if (!details)
        {
            // pluralize
            if (domainType.equals(AssayDomainTypes.Batch))
                viewName += "es";
            else
                viewName += "s";
        }
        return viewName + ".html";
    }

    protected boolean hasCustomView(String viewResourceName)
    {
        return ModuleHtmlView.exists(getDeclaringModule(), basePath.getPath().append("views", viewResourceName));
    }

    @Override
    public boolean hasCustomView(IAssayDomainType domainType, boolean details)
    {
        return hasCustomView(getViewResourcePath(domainType, details));
    }

    protected ModelAndView getCustomView(String viewResourceName)
    {
        ModuleHtmlView view = ModuleHtmlView.get(getDeclaringModule(), basePath.getPath().append("views", viewResourceName));
        if (view != null)
        {
            view.setFrame(WebPartView.FrameType.NONE);
            return view;
        }
        return null;
    }

    // XXX: consider moving to TsvAssayProvider
    protected ModelAndView getCustomView(IAssayDomainType domainType, boolean details)
    {
        return getCustomView(getViewResourcePath(domainType, details));
    }

    public static class AssayPageBean
    {
        public ModuleAssayProvider provider;
        public ExpProtocol expProtocol;
    }

    protected ModelAndView createListView(IAssayDomainType domainType, ExpProtocol protocol)
    {
        ModelAndView nestedView = getCustomView(domainType, false);
        if (nestedView == null)
            return null;

        AssayPageBean bean = new AssayPageBean();
        bean.provider = this;
        bean.expProtocol = protocol;

        JspView<AssayPageBean> view = new JspView<>("/org/labkey/study/assay/view/moduleAssayListView.jsp", bean);
        view.setView("nested", nestedView);
        return view;
    }

    @Override
    public ModelAndView createBeginView(ViewContext context, ExpProtocol protocol)
    {
        ModelAndView beginView = getCustomView(BEGIN_VIEW_FILENAME);
        if (beginView == null)
            return super.createBeginView(context, protocol);

        BatchDetailsBean bean = new BatchDetailsBean();
        bean.provider = this;
        bean.expProtocol = protocol;

        JspView<BatchDetailsBean> view = new JspView<>("/org/labkey/study/assay/view/begin.jsp", bean);
        view.setView("nested", beginView);
        return view;
    }

    @Override
    public ModelAndView createBatchesView(ViewContext context, ExpProtocol protocol)
    {
        return createListView(AssayDomainTypes.Batch, protocol);
    }

    public static class BatchDetailsBean extends AssayPageBean
    {
        public ExpExperiment expExperiment;
    }

    @Override
    public ModelAndView createBatchDetailsView(ViewContext context, ExpProtocol protocol, ExpExperiment batch)
    {
        ModelAndView batchDetailsView = getCustomView(AssayDomainTypes.Batch, true);
        if (batchDetailsView == null)
            return super.createBatchDetailsView(context, protocol, batch);

        BatchDetailsBean bean = new BatchDetailsBean();
        bean.provider = this;
        bean.expProtocol = protocol;
        bean.expExperiment = batch;

        JspView<BatchDetailsBean> view = new JspView<>("/org/labkey/study/assay/view/batchDetails.jsp", bean);
        view.setView("nested", batchDetailsView);
        return view;
    }

    @Override
    public ModelAndView createRunsView(ViewContext context, ExpProtocol protocol)
    {
        return createListView(AssayDomainTypes.Run, protocol);
    }

    public static class RunDetailsBean extends AssayPageBean
    {
        public ExpRun expRun;
    }

    @Override
    public ModelAndView createRunDetailsView(ViewContext context, ExpProtocol protocol, ExpRun run)
    {
        ModelAndView runDetailsView = getCustomView(AssayDomainTypes.Run, true);
        if (runDetailsView == null)
            return super.createRunDetailsView(context, protocol, run);

        RunDetailsBean bean = new RunDetailsBean();
        bean.provider = this;
        bean.expProtocol = protocol;
        bean.expRun = run;

        JspView<RunDetailsBean> view = new JspView<>("/org/labkey/study/assay/view/runDetails.jsp", bean);
        view.setView("nested", runDetailsView);
        return view;
    }

    @Override
    public ModelAndView createResultsView(ViewContext context, ExpProtocol protocol, BindException errors)
    {
        return createListView(AssayDomainTypes.Result, protocol);
    }

    public static class ResultDetailsBean extends AssayPageBean
    {
        public ExpData expData;
        public Integer objectId;
    }

    @Override
    public ModelAndView createResultDetailsView(ViewContext context, ExpProtocol protocol, ExpData data, Object objectId)
    {
        ModelAndView resultDetailsView = getCustomView(AssayDomainTypes.Result, true);
        if (resultDetailsView == null)
            return super.createResultDetailsView(context, protocol, data, objectId);

        ResultDetailsBean bean = new ResultDetailsBean();
        bean.provider = this;
        bean.expProtocol = protocol;
        bean.expData = data;
        if (objectId == null)
        {
            throw new NotFoundException();
        }
        if (objectId instanceof Number)
        {
            bean.objectId = ((Number)objectId).intValue();
        }
        else
        {
            try
            {
                bean.objectId = Integer.parseInt(objectId.toString());
            }
            catch (NumberFormatException e)
            {
                throw new NotFoundException();
            }
        }

        JspView<ResultDetailsBean> view = new JspView<>("/org/labkey/study/assay/view/resultDetails.jsp", bean);
        view.setView("nested", resultDetailsView);
        return view;
    }

    @Override
    public ActionURL getImportURL(Container container, ExpProtocol protocol)
    {
        if (!hasUploadView())
            return super.getImportURL(container, protocol);
        return PageFlowUtil.urlProvider(AssayUrls.class).getProtocolURL(container, protocol, AssayController.ModuleAssayUploadAction.class);
    }

    protected boolean hasUploadView()
    {
        return hasCustomView(UPLOAD_VIEW_FILENAME);
    }

    protected ModelAndView getUploadView()
    {
        return getCustomView(UPLOAD_VIEW_FILENAME);
    }

    public ModelAndView createUploadView(AssayRunUploadForm form)
    {
        ModelAndView uploadView = getUploadView();
        if (uploadView == null)
        {
            ActionURL url = form.getViewContext().cloneActionURL();
            url.setAction(UploadWizardAction.class);
            return HttpView.redirect(url);
        }

        JspView<AssayRunUploadForm> view = new JspView<>("/org/labkey/study/assay/view/moduleAssayUpload.jsp", form);
        view.setView("nested", uploadView);
        return view;
    }

    @Override
    public boolean hasUsefulDetailsPage()
    {
        return true;
    }

    @NotNull
    @Override
    public List<File> getValidationAndAnalysisScripts(ExpProtocol protocol, Scope scope)
    {
        // Start with the standard set
        List<File> result = new ArrayList<>(super.getValidationAndAnalysisScripts(protocol, scope));

        if (scope == Scope.ASSAY_TYPE || scope == Scope.ALL)
        {
            // lazily get the validation scripts defined in the module
            Resource scriptDir = getDeclaringModule().getModuleResolver().lookup(basePath.getPath().append("scripts"));

            if (scriptDir != null && scriptDir.exists())
            {
                final ScriptEngineManager manager = ServiceRegistry.get().getService(ScriptEngineManager.class);

                Collection<? extends Resource> scripts = scriptDir.list();
                List<File> moduleScriptFiles = new ArrayList<>(scripts.size());
                for (Resource r : scripts)
                {
                    if (r instanceof FileResource)
                    {
                        String ext = r.getPath().extension();
                        FileResource fileResource = (FileResource) r;
                        if (manager.getEngineByExtension(ext) != null)
                        {
                            moduleScriptFiles.add(fileResource.getFile());
                        }
                        else
                        {
                            String fileName = fileResource.getFile().getName();
                            // Prevent a later warning about the script file not existing
                            if (_missingScriptWarnings.add(fileName))
                            {
                                LOG.warn("Unable to use script file '" + fileName + "' specified in metadata for assay type '" + getName() + "' because the required script engine is not configured.");
                            }
                        }
                    }
                }

                // Put the scripts in the order specified by the config.xml file
                List<File> sortedModuleScripts = new ArrayList<>();
                for (ScriptMetadata scriptMetadata : _scriptMetadata)
                {
                    File matchingScript = findAndRemove(moduleScriptFiles, scriptMetadata.getFileName());
                    if (matchingScript != null)
                    {
                        sortedModuleScripts.add(matchingScript);
                    }
                }
                result.addAll(sortedModuleScripts);

                // Add any remaining module-provided files in alphabetical order
                moduleScriptFiles.sort(Comparator.comparing(File::getName, String.CASE_INSENSITIVE_ORDER));
                result.addAll(moduleScriptFiles);
            }
        }
        return result;
    }

    /** Finds a script from the list of available files, and removes it from the list */
    private File findAndRemove(List<File> scriptFiles, String fileName)
    {
        for (File scriptFile : scriptFiles)
        {
            if (scriptFile.getName().equalsIgnoreCase(fileName))
            {
                scriptFiles.remove(scriptFile);
                return scriptFile;
            }
        }
        // Only warn the first time we notice that there's a script that's in the config.xml file but not on disk
        if (_missingScriptWarnings.add(fileName))
        {
            LOG.warn("Unable to find a script file '" + fileName + "' specified in metadata for assay type '" + getName() + "'");
        }
        return null;
    }

    private ModuleAssayException getWrappedModuleAssayException(String message)
    {
        return getWrappedModuleAssayException(message, null);
    }

    private ModuleAssayException getWrappedModuleAssayException(String message, @Nullable Throwable cause)
    {
        ModuleAssayException wrapped = (cause != null) ?
                new ModuleAssayException(message, cause) :
                new ModuleAssayException(message);

        ExceptionUtil.decorateException(wrapped, ExceptionUtil.ExceptionInfo.SkipMothershipLogging, "true", true);
        return wrapped;
    }

    private void setSaveHandlerClass(String className)
    {
        if (!className.contains("."))
        {
            StringBuilder sbClass = new StringBuilder();
            sbClass.append("org.labkey.");
            sbClass.append(getDeclaringModule().getName());
            sbClass.append(".assay.");
            sbClass.append(basePath.getName());
            sbClass.append(".");
            sbClass.append(className);
            className = sbClass.toString();
        }

        try
        {
            saveHandlerClass = Class.forName(className);
            if (!AssaySaveHandler.class.isAssignableFrom(saveHandlerClass))
                throw getWrappedModuleAssayException(INVALID_SAVE_HANDLER_INTERFACE);

        }
        catch(ClassNotFoundException e)
        {
            throw getWrappedModuleAssayException(INVALID_SAVE_HANDLER_CLASS, e);
        }
    }

    @Override
    public DataExchangeHandler createDataExchangeHandler()
    {
        return new ModuleDataExchangeHandler();
    }

    public PipelineProvider getPipelineProvider()
    {
        AssayDataType dataType = getDataType();
        if (dataType == null)
            return null;

        return new ModuleAssayPipelineProvider(StudyModule.class,
                new PipelineProvider.FileTypesEntryFilter(dataType.getFileType()), this, "Import " + getName());
    }

    @Override
    public void registerLsidHandler()
    {
        throw new IllegalStateException("Shouldn't be registering an LSID handler on a ModuleAssayProvider!");
    }

    static class ModuleAssayPipelineProvider extends AssayPipelineProvider
    {
        public ModuleAssayPipelineProvider(Class<? extends Module> moduleClass, FileEntryFilter filter, AssayProvider assayProvider, String actionDescription)
        {
            super(moduleClass, filter, assayProvider, actionDescription);
        }

        @Override
        protected String getFilePropertiesId()
        {
            return super.getFilePropertiesId() + ':' + this.getName();
        }

        @Override
        public String toString()
        {
            return "ModuleAssayPipelineProvider " + getName();
        }
    }

    static class ScriptMetadata
    {
        private String _fileName;
        private String _name;
        private String _description;

        ScriptMetadata(String fileName, String name, String description)
        {
            _fileName = fileName;
            _name = name == null ? fileName : name;
            _description = description == null ? fileName : description;
        }

        public String getFileName()
        {
            return _fileName;
        }

        public String getName()
        {
            return _name;
        }

        public String getDescription()
        {
            return _description;
        }
    }

    @Override
    public ReRunSupport getReRunSupport()
    {
        return ReRunSupport.None;
    }

    @NotNull
    @Override
    public List<AssayDataType> getRelatedDataTypes()
    {
        return _relatedDataTypes;
    }
}
