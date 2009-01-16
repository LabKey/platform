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
import org.labkey.api.data.*;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.IAssayDomainType;
import org.labkey.api.exp.api.ExpProtocol.AssayDomainTypes;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.property.DomainUtil;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.DomainDescriptor;
import org.labkey.api.query.DetailsURL;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.QueryService;
import org.labkey.api.security.User;
import org.labkey.api.study.actions.AssayDataDetailsAction;
import org.labkey.api.study.assay.RunDataTable;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.*;
import org.labkey.api.gwt.client.model.GWTDomain;
import org.springframework.web.servlet.ModelAndView;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
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
//    private Map<IAssayDomainType, File> viewFiles = new HashMap<IAssayDomainType, File>();

    public ModuleAssayProvider(File baseDir, String name)
    {
        super(name + "Protocol", name + "Run");
        this.baseDir = baseDir;
        this.name = name;

        init();
    }

    protected void init()
    {
        viewsDir = new File(baseDir, "views");
        if (!viewsDir.isDirectory() || !viewsDir.canRead())
            viewsDir = null;
    }

    public String getName()
    {
        return name;
    }

    @Override
    public boolean canCopyToStudy()
    {
        return false;
    }

    @Override
    public boolean isPlateBased()
    {
        return false;
    }

    public void addDomain(IAssayDomainType domainType, DomainDescriptorType xDomain)
    {
        domainsDescriptors.put(domainType, xDomain);
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
    protected Domain createUploadSetDomain(Container c, User user)
    {
        Domain domain = createDomain(c, user, AssayDomainTypes.Batch);
        if (domain != null)
            return domain;
        return super.createUploadSetDomain(c, user);
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

    // XXX: consider moving to TsvAssayProvider
    protected boolean hasView(IAssayDomainType domainType)
    {
        if (viewsDir != null)
        {
            File viewFile = new File(viewsDir, domainType.getName().toLowerCase() + ".html");
            if (viewFile.canRead())
                return true;
        }
        return false;
    }

    // XXX: consider moving to TsvAssayProvider
    protected ModelAndView getView(IAssayDomainType domainType)
    {
        if (viewsDir != null)
        {
            File viewFile = new File(viewsDir, domainType.getName().toLowerCase() + ".html");
            if (viewFile.canRead())
                return new HtmlView(PageFlowUtil.getFileContentsAsString(viewFile));
        }
        return null;
    }

    @Override
    public ModelAndView createRunDataView(ViewContext context, ExpProtocol protocol)
    {
        ModelAndView runDataView = getView(AssayDomainTypes.Run);
        if (runDataView == null)
            return null;

        return runDataView;
    }

    public static class DataDetailsModel
    {
        public ExpProtocol expProtocol;
        public ExpData expData;
        public Object objectId;

//        public GWTDomain dataDomain;
//        public Map<String, Object> values;
    }

    @Override
    public ModelAndView createDataDetailsView(ViewContext context, ExpProtocol protocol, ExpData data, Object objectId)
    {
        ModelAndView dataDetailsView = getView(AssayDomainTypes.Data);
        if (dataDetailsView == null)
            return super.createDataDetailsView(context, protocol, data, objectId);

        DataDetailsModel model = new DataDetailsModel();
        model.expProtocol = protocol;
        model.expData = data;
        model.objectId = objectId;

//        String domainURI = getDomainURIForPrefix(protocol, AssayDomainTypes.Data.getPrefix());
////        model.dataDomain = PropertyService.get().getDomain(context.getContainer(), domainURI);
//        model.dataDomain = DomainUtil.getDomainDescriptor(domainURI, context.getContainer());
//        model.values = getDataRow(context.getUser(), context.getContainer(), protocol, objectId);
//        if (model.values == null)
//            HttpView.throwNotFound("Data values for '" + data.getRowId() + "' not found");

        JspView<DataDetailsModel> view = new JspView<DataDetailsModel>("/org/labkey/study/assay/view/dataDetails.jsp", model);
        view.setView("nested", dataDetailsView);
        return view;
    }

    @Override
    public boolean hasUsefulDetailsPage()
    {
        return true;
    }
}
