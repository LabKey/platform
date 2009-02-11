/*
 * Copyright (c) 2008-2009 LabKey Corporation
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

import org.fhcrc.cpas.exp.xml.DomainDescriptorType;
import org.fhcrc.cpas.exp.xml.PropertyDescriptorType;
import org.labkey.api.data.*;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.IAssayDomainType;
import org.labkey.api.exp.api.DataType;
import org.labkey.api.exp.api.ExpProtocol.AssayDomainTypes;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.User;
import org.labkey.api.study.actions.AssayDataDetailsAction;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.actions.UploadWizardAction;
import org.labkey.api.study.assay.RunDataTable;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.study.assay.AssayPublishService;
import org.labkey.api.study.TimepointType;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.util.FileUtil;
import org.labkey.api.view.*;
import org.labkey.api.services.ServiceRegistry;
import org.labkey.study.controllers.assay.AssayController;
import org.labkey.study.assay.xml.ProviderType;
import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import javax.script.ScriptEngineManager;
import java.io.File;
import java.io.FileFilter;
import java.util.*;
import java.sql.SQLException;

/**
 * User: kevink
 * Date: Dec 10, 2008 2:20:38 PM
 */
public class ModuleAssayProvider extends TsvAssayProvider
{
    private File baseDir;
    private File viewsDir;
    private String name;
    private Map<IAssayDomainType, DomainDescriptorType> domainsDescriptors = new HashMap<IAssayDomainType, DomainDescriptorType>();

    private FieldKey participantIdKey;
    private FieldKey visitIdKey;
    private FieldKey dateKey;
    private FieldKey specimenIdKey;

    public static final DataType RAW_DATA_TYPE = new DataType("RawAssayData");

    public ModuleAssayProvider(String name, File baseDir, ProviderType providerConfig)
    {
        super(name + "Protocol", name + "Run");
        this.name = name;
        this.baseDir = baseDir;
        this.viewsDir = new File(baseDir, "views");

        init(providerConfig);
    }

    protected void init(ProviderType providerConfig)
    {
        if (providerConfig == null)
            return;

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
    }

    public String getName()
    {
        return name;
    }

    public void addDomain(IAssayDomainType domainType, DomainDescriptorType xDomain)
    {
        domainsDescriptors.put(domainType, xDomain);
    }

    protected boolean hasDomain(IAssayDomainType domainType)
    {
        return domainsDescriptors.containsKey(domainType);
    }

    protected Domain createDomain(Container c, User user, IAssayDomainType domainType)
    {
        DomainDescriptorType xDomain = domainsDescriptors.get(domainType);
        if (xDomain != null)
        {
            return PropertyService.get().createDomain(c, xDomain);
        }
        return null;
    }

    @Override
    protected Domain createBatchDomain(Container c, User user)
    {
        Domain domain = createDomain(c, user, AssayDomainTypes.Batch);
        if (domain != null)
            return domain;
        return super.createBatchDomain(c, user);
    }

    @Override
    protected Domain createRunDomain(Container c, User user)
    {
        Domain domain = createDomain(c, user, AssayDomainTypes.Run);
        if (domain != null)
            return domain;
        return super.createRunDomain(c, user);
    }

    @Override
    protected Domain createDataDomain(Container c, User user)
    {
        Domain domain = createDomain(c, user, AssayDomainTypes.Data);
        if (domain != null)
            return domain;
        return super.createDataDomain(c, user);
    }

    @Override
    protected Map<String, Set<String>> getRequiredDomainProperties()
    {
        Map<String, Set<String>> required = new HashMap<String, Set<String>>();
        for (Map.Entry<IAssayDomainType, DomainDescriptorType> domainDescriptor : domainsDescriptors.entrySet())
        {
            IAssayDomainType domainType = domainDescriptor.getKey();
            DomainDescriptorType xDescriptor = domainDescriptor.getValue();
            PropertyDescriptorType[] xProperties = xDescriptor.getPropertyDescriptorArray();
            LinkedHashSet<String> properties = new LinkedHashSet<String>(xProperties.length);
            for (PropertyDescriptorType xProp : xProperties)
                properties.add(xProp.getName());

            required.put(domainType.getPrefix(), properties);
        }
        return required;
    }

    @Override
    public TableInfo createDataTable(UserSchema schema, String alias, ExpProtocol protocol)
    {
        RunDataTable table = (RunDataTable)super.createDataTable(schema, alias, protocol);
        if (table == null)
            return null;

        if (hasView(AssayDomainTypes.Data))
        {
            // XXX: consider adding a .getDataDetailsURL() to AbstractAssayProvider or TsvAssayProvider
            ActionURL dataDetailsURL = new ActionURL(AssayDataDetailsAction.class, schema.getContainer());
            dataDetailsURL.addParameter("rowId", protocol.getRowId());
            Map<String, String> params = new HashMap<String, String>();
            // map ObjectId to url parameter DataDetailsForm.dataRowId
            params.put("dataRowId", "ObjectId");
            table.addDetailsURL(new DetailsURL(dataDetailsURL, params));
        }
        return table;
    }

    /**
     * Get a single row from the data table as a Map.
     */
    protected Map<String, Object> getDataRow(User user, Container container, ExpProtocol protocol, Object objectId)
    {
        UserSchema schema = AssayService.get().createSchema(user, container);
        TableInfo table = createDataTable(schema, null, protocol);
        List<ColumnInfo> columns = new ArrayList<ColumnInfo>(QueryService.get().getColumns(table, table.getDefaultVisibleColumns()).values());
        SimpleFilter filter = new SimpleFilter("ObjectId", objectId);

        Map<String, Object>[] maps = null;
        try
        {
            maps = (Map<String, Object>[]) Table.select(table, columns, filter, null, Map.class);
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }

        if (maps == null || maps.length == 0)
            return null;
        return maps[0];
    }

    @Override
    public FieldKey getParticipantIDFieldKey()
    {
        if (participantIdKey != null)
            return participantIdKey;
        return super.getParticipantIDFieldKey();
    }

    @Override
    public FieldKey getVisitIDFieldKey(Container targetStudy)
    {
        if (AssayPublishService.get().getTimepointType(targetStudy) == TimepointType.VISIT)
        {
            if (visitIdKey != null)
                return visitIdKey;
        }
        else
        {
            if (dateKey != null)
                return dateKey;
        }
        return super.getVisitIDFieldKey(targetStudy);
    }

    @Override
    public FieldKey getSpecimenIDFieldKey()
    {
        if (specimenIdKey != null)
            return specimenIdKey;
        return super.getSpecimenIDFieldKey();
    }

    private File getViewFile(IAssayDomainType domainType)
    {
        return new File(viewsDir, domainType.getName().toLowerCase() + ".html");
    }

    // XXX: consider moving to TsvAssayProvider
    protected boolean hasView(IAssayDomainType domainType)
    {
        File viewFile = getViewFile(domainType);
        return viewFile.canRead();
    }

    // XXX: consider moving to TsvAssayProvider
    protected ModelAndView getView(IAssayDomainType domainType)
    {
        File viewFile = getViewFile(domainType);
        if (viewFile.canRead())
            return new HtmlView(PageFlowUtil.getFileContentsAsString(viewFile));
        return null;
    }

    @Override
    public ModelAndView createResultsView(ViewContext context, ExpProtocol protocol)
    {
        ModelAndView runDataView = getView(AssayDomainTypes.Run);
        if (runDataView == null)
            return null;

        return runDataView;
    }

    public static class AssayPageBean
    {
        public ExpProtocol expProtocol;
    }

    public static class DataDetailsBean extends AssayPageBean
    {
        public ExpData expData;
        public Object objectId;
    }

    @Override
    public ModelAndView createDataDetailsView(ViewContext context, ExpProtocol protocol, ExpData data, Object objectId)
    {
        ModelAndView dataDetailsView = getView(AssayDomainTypes.Data);
        if (dataDetailsView == null)
            return super.createDataDetailsView(context, protocol, data, objectId);

        DataDetailsBean bean = new DataDetailsBean();
        bean.expProtocol = protocol;
        bean.expData = data;
        bean.objectId = objectId;

        JspView<DataDetailsBean> view = new JspView<DataDetailsBean>("/org/labkey/study/assay/view/dataDetails.jsp", bean);
        view.setView("nested", dataDetailsView);
        return view;
    }

    @Override
    public Map<String, Class<? extends Controller>> getImportActions()
    {
        if (!hasUploadView())
            return super.getImportActions();
        return Collections.<String, Class<? extends Controller>>singletonMap(
                IMPORT_DATA_LINK_NAME, AssayController.ModuleAssayUploadAction.class);
    }

    private File getUploadViewFile()
    {
        return new File(viewsDir, "upload.html");
    }

    protected boolean hasUploadView()
    {
        File viewFile = getUploadViewFile();
        return viewFile.canRead();
    }

    protected ModelAndView getUploadView()
    {
        File viewFile = getUploadViewFile();
        if (viewFile.canRead())
            return new HtmlView(PageFlowUtil.getFileContentsAsString(viewFile));
        return null;
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

        JspView<AssayRunUploadForm> view = new JspView<AssayRunUploadForm>("/org/labkey/study/assay/view/moduleAssayUpload.jsp", form);
        view.setView("nested", uploadView);
        return view;
    }

    @Override
    public boolean hasUsefulDetailsPage()
    {
        return true;
    }

    @Override
    public List<File> getValidationAndAnalysisScripts(ExpProtocol protocol, Scope scope)
    {
        List<File> validationScripts = new ArrayList<File>();

        if (scope == Scope.ASSAY_TYPE || scope == Scope.ALL)
        {
            // lazily get the validation scripts
            File scriptDir = new File(baseDir, "scripts");

            if (scriptDir.canRead())
            {
                final ScriptEngineManager manager = ServiceRegistry.get().getService(ScriptEngineManager.class);

                File[] scripts = scriptDir.listFiles(new FileFilter(){
                    public boolean accept(File pathname)
                    {
                        String ext = FileUtil.getExtension(pathname);
                        return  (manager.getEngineByExtension(ext) != null);
                    }
                });
                validationScripts.addAll(Arrays.asList(scripts));
                Collections.sort(validationScripts, new Comparator<File>(){
                    public int compare(File o1, File o2)
                    {
                        return o1.getName().compareToIgnoreCase(o2.getName());
                    }
                });
            }
        }
        validationScripts.addAll(super.getValidationAndAnalysisScripts(protocol, scope));
        return validationScripts;
    }
}
