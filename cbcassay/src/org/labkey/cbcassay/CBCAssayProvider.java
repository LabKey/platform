/*
 * Copyright (c) 2008-2010 LabKey Corporation
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

package org.labkey.cbcassay;

import org.labkey.api.data.*;
import org.labkey.api.exp.*;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.IAssayDomainType;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.xar.LsidUtils;
import org.labkey.api.query.*;
import org.labkey.api.security.User;
import org.labkey.api.security.permissions.DeletePermission;
import org.labkey.api.security.permissions.Permission;
import org.labkey.api.study.actions.AssayRunUploadForm;
import org.labkey.api.study.actions.AssayResultDetailsAction;
import org.labkey.api.study.assay.*;
import org.labkey.api.study.query.ResultsQueryView;
import org.labkey.api.view.*;
import org.labkey.api.util.ExceptionUtil;
import org.labkey.api.util.Pair;
import org.labkey.api.pipeline.PipelineProvider;
import org.labkey.cbcassay.data.CBCDataProperty;
import org.labkey.cbcassay.data.CBCData;
import org.springframework.web.servlet.ModelAndView;

import java.io.File;
import java.sql.SQLException;
import java.util.*;

/**
 * User: kevink
 * Date: Nov 12, 2008 4:06:37 PM
 */
public class CBCAssayProvider extends AbstractTsvAssayProvider
{
    public static final String NAME = "CBC";
    public static final String SAMPLE_ID_NAME = "SampleId";
    public static final String SAMPLE_ID_CAPTION = "Sample Id";

    private static final String x10e3_cells_per_microliter = "x10e03 cells/\u00B5L";
    private static final String x10e6_cells_per_microliter = "x10e06 cells/\u00B5L";
    private static final String RESULT_DOMAIN_NAME = "Result Fields";

    private static class ResultDomainProperty
    {
        public String name, label, description;
        public PropertyType type;
        public Double min, max;
        public String units;

        public ResultDomainProperty(String name, String label, PropertyType type, String description)
        {
            this(name, label, type, description, null, null, null);
        }

        public ResultDomainProperty(String name, String label, PropertyType type, String description, Double min, Double max, String units)
        {
            this.name = name;
            this.label = label;
            this.description = description;
            this.type = type;
            this.min = min;
            this.max = max;
            this.units = units;
        }
    }

    private static ResultDomainProperty[] RESULT_DOMAIN_PROPERTIES = new ResultDomainProperty[] {
        new ResultDomainProperty(SAMPLE_ID_NAME, SAMPLE_ID_CAPTION, PropertyType.STRING, "Identifier"),
        new ResultDomainProperty("Sequence", null, PropertyType.STRING, null),
        new ResultDomainProperty(DATE_PROPERTY_NAME,  DATE_PROPERTY_CAPTION, PropertyType.DATE_TIME, "Date/Time"),

        new ResultDomainProperty("WBC", null, PropertyType.DOUBLE, null, 5.2d, 12.4d, x10e3_cells_per_microliter),
        new ResultDomainProperty("RBC", null, PropertyType.DOUBLE, null, 4.2d,  6.1d, x10e6_cells_per_microliter),
        new ResultDomainProperty("HGB", null, PropertyType.DOUBLE, null,  12d,   16d, "g/dL"),
        new ResultDomainProperty("HCT", null, PropertyType.DOUBLE, null,  34d,   44d, "%"),
        new ResultDomainProperty("MCV", null, PropertyType.DOUBLE, null,  66d,   77d, "fL"),
        new ResultDomainProperty("MCH", null, PropertyType.DOUBLE, null,  21d,   26d, "pg"),
        new ResultDomainProperty("PLT", null, PropertyType.DOUBLE, null, 130d,  400d, x10e3_cells_per_microliter),
        new ResultDomainProperty("MPV", null, PropertyType.DOUBLE, null, 7.2d, 11.1d, "fL"),

        new ResultDomainProperty("PercentNEUT",  "%NEUT",  PropertyType.DOUBLE, "%NEUT",   40d,  74d, "%"),
        new ResultDomainProperty("PercentLYMPH", "%LYMPH", PropertyType.DOUBLE, "%LYM",    19d,  48d, "%"),
        new ResultDomainProperty("PercentMONO",  "%MONO",  PropertyType.DOUBLE, "%MONO",  3.4d,   9d, "%"),
        new ResultDomainProperty("PercentEOS",   "%EOS",   PropertyType.DOUBLE, "%EOS",     0d,   7d, "%"),
        new ResultDomainProperty("PercentBASO",  "%BASO",  PropertyType.DOUBLE, "%BASO",    0d,   4d, "%"),
        new ResultDomainProperty("PercentLUC",   "%LUC",   PropertyType.DOUBLE, "%LUC",     0d, 1.5d, "%"),

        new ResultDomainProperty("AbsNEUT",  "#NEUT",  PropertyType.DOUBLE, "abs_neuts",  1.9d,   8d, x10e3_cells_per_microliter),
        new ResultDomainProperty("AbsLYMPH", "#LYMPH", PropertyType.DOUBLE, "abs_lymphs", 0.9d, 5.2d, x10e3_cells_per_microliter),
        new ResultDomainProperty("AbsMONO",  "#MONO",  PropertyType.DOUBLE, "abs_monos", 0.16d,   1d, x10e3_cells_per_microliter),
        new ResultDomainProperty("AbsEOS",   "#EOS",   PropertyType.DOUBLE, "abs_eos",      0d, 0.8d, x10e3_cells_per_microliter),
        new ResultDomainProperty("AbsBASO",  "#BASO",  PropertyType.DOUBLE, "abs_basos",    0d, 0.2d, x10e3_cells_per_microliter),
        new ResultDomainProperty("AbsLUC",   "#LUC",   PropertyType.DOUBLE, "abs_lucs",     0d, 0.4d, x10e3_cells_per_microliter),

        new ResultDomainProperty("PercentTotalLYMPH", "%Total LYMPH", PropertyType.DOUBLE, "Sum of %LYMPH and %LUC",  19d,  52d, "%"),
        new ResultDomainProperty("AbsTotalLYMPH",     "#Total LYMPH", PropertyType.DOUBLE, "Sum of #LYMPH and #LUC", 0.9d, 5.6d, x10e3_cells_per_microliter),
    };

    public CBCAssayProvider()
    {
        super("CBCAssayProtocol", "CBCAssayRun", CBCDataHandler.DATA_TYPE,
            new CBCAssayTableMetadata(
                FieldKey.fromParts("Properties"),
                FieldKey.fromParts("Run"),
                FieldKey.fromParts("ObjectId")));
    }

    private final static class CBCAssayTableMetadata extends AssayTableMetadata
    {
        public CBCAssayTableMetadata(FieldKey specimenDetailParentFieldKey, FieldKey runFieldKey, FieldKey resultRowIdFieldKey)
        {
            super(specimenDetailParentFieldKey, runFieldKey, resultRowIdFieldKey);
        }

        @Override
        public FieldKey getParticipantIDFieldKey()
        {
            return new FieldKey(getSpecimenDetailParentFieldKey(), SAMPLE_ID_NAME);
        }
    }

    public String getName()
    {
        return NAME;
    }

    public String getDescription()
    {
        return "Imports Complete Blood Count data files.";
    }

    public List<AssayDataCollector> getDataCollectors(Map<String, File> uploadedFiles, AssayRunUploadForm context)
    {
        List<AssayDataCollector> result = super.getDataCollectors(uploadedFiles, context);
        result.add(0, new TextAreaDataCollector());
        result.add(new PipelineDataCollector());
        return result;
    }

    public TableInfo createDataTable(final AssaySchema schema, ExpProtocol protocol)
    {
        RunDataTable table = new RunDataTable(schema, protocol) {
            @Override
            public boolean hasPermission(User user, Class<? extends Permission> perm)
            {
                if (getUpdateService() != null)
                    return DeletePermission.class.isAssignableFrom(perm) && getContainer().hasPermission(user, perm);
                return false;
            }

            @Override
            public QueryUpdateService getUpdateService()
            {
                return new DummyUpdateService();
            }
        };

        ActionURL showDetailsUrl = new ActionURL(AssayResultDetailsAction.class, schema.getContainer());
        showDetailsUrl.addParameter("rowId", protocol.getRowId());
        Map<String, String> params = new HashMap<String, String>();
        params.put("dataRowId", "ObjectId");
        table.addDetailsURL(new DetailsURL(showDetailsUrl, params));

        ActionURL updateUrl = new ActionURL(CBCAssayController.UpdateAction.class, null);
        table.setUpdateURL(new DetailsURL(updateUrl, "objectId", FieldKey.fromString("ObjectId")));

        return table;
    }

    @Override
    public List<Pair<Domain, Map<DomainProperty, Object>>> createDefaultDomains(Container c, User user)
    {
        List<Pair<Domain,Map<DomainProperty,Object>>> result = super.createDefaultDomains(c, user);

        String lsid = getPresubstitutionLsid(ExpProtocol.ASSAY_DOMAIN_DATA);
        Domain resultDomain = PropertyService.get().createDomain(c, lsid, RESULT_DOMAIN_NAME);
        resultDomain.setDescription("The user is prompted to enter data values for row of data associated with a run, typically done as uploading a file.  This is part of the second step of the upload process.");

        for (ResultDomainProperty ddp : RESULT_DOMAIN_PROPERTIES)
        {
            addProperty(resultDomain, ddp.name, ddp.label, ddp.type, ddp.description);
        }

        result.add(new Pair<Domain, Map<DomainProperty, Object>>(resultDomain, Collections.<DomainProperty, Object>emptyMap()));
        return result;
    }

    @Override
    protected Map<String, Set<String>> getRequiredDomainProperties()
    {
        Map<String, Set<String>> domainMap = super.getRequiredDomainProperties();

        HashSet<String> domainProperties = new HashSet<String>();
        for (ResultDomainProperty ddp : RESULT_DOMAIN_PROPERTIES)
            domainProperties.add(ddp.name);
        domainMap.put(RESULT_DOMAIN_NAME, domainProperties);

        return domainMap;
    }

    @Override
    public ExpProtocol createAssayDefinition(User user, Container container, String name, String description) throws ExperimentException
    {
        ExpProtocol protocol = super.createAssayDefinition(user, container, name, description);

//        String dataDomainUri = getDomainURIForPrefix(protocol, ExpProtocol.ASSAY_DOMAIN_DATA);
        XarContext context = new XarContext("Domains", container, user);
        context.addSubstitution("AssayName", name);
        String lsid = getPresubstitutionLsid(ExpProtocol.ASSAY_DOMAIN_DATA);
        String dataDomainUri = LsidUtils.resolveLsidFromTemplate(lsid, context);

        try
        {
            for (ResultDomainProperty ddp : RESULT_DOMAIN_PROPERTIES)
                setMinMaxUnits(container, user, dataDomainUri, ddp.name, ddp.min, ddp.max, ddp.units);
        }
        catch (SQLException e)
        {
            ExceptionUtil.logExceptionToMothership(HttpView.getRootContext().getRequest(), e);
            throw new ExperimentException(e);
        }

        return protocol;
    }

    private void setMinMaxUnits(Container c, User user, String domainUri, String name, Double min, Double max, String units) throws SQLException
    {
        if (min == null && max == null && units == null)
            return;

        final String uri = domainUri + "#" + name;

        PropertyDescriptor[] props = new PropertyDescriptor[] {
                CBCDataProperty.MinValue.getPropertyDescriptor(), CBCDataProperty.MaxValue.getPropertyDescriptor(), CBCDataProperty.Units.getPropertyDescriptor() };

        Map<String, Object> row = new HashMap<String, Object>();
        if (min != null)
            row.put(CBCDataProperty.MinValue.getPropertyDescriptor().getPropertyURI(), min);
        if (max != null)
            row.put(CBCDataProperty.MaxValue.getPropertyDescriptor().getPropertyURI(), max);
        if (units != null)
            row.put(CBCDataProperty.Units.getPropertyDescriptor().getPropertyURI(), units);

        Map<String, Object>[] rows = new Map[] { row };

        try
        {
            OntologyManager.ImportHelper helper = new OntologyManager.ImportHelper() {
                public String beforeImportObject(Map map) throws SQLException {
                    return uri;
                }

                public void afterBatchInsert(int currentRow) throws SQLException {
                }

                public void updateStatistics(int currentRow) throws SQLException {
                }
            };
            OntologyManager.insertTabDelimited(c, user, null, helper, props, Arrays.asList(rows), false);
        }
        catch (ValidationException e)
        {
            throw new RuntimeException(e);
        }
    }

    public HttpView getDataDescriptionView(AssayRunUploadForm form)
    {
        return new JspView<AssayRunUploadForm>("/org/labkey/study/assay/view/tsvDataDescription.jsp", form);
    }

//    @Override
//    public Map<String, Class<? extends Controller>> getImportActions()
//    {
//        return Collections.<String, Class<? extends Controller>>singletonMap(IMPORT_DATA_LINK_NAME, CBCUploadWizardAction.class);
//    }

//    protected PropertyType getDataRowIdType()
//    {
//        return PropertyType.INTEGER;
//    }

    public static ActionURL getResultUpdateUrl(ViewContext context)
    {
        // clone the current url to keep objectId parameter if present
        ActionURL url = context.cloneActionURL();
        url.setAction(CBCAssayController.UpdateAction.class);
        url.addParameter("returnURL", context.getActionURL().toString());
        return url;
    }

    public List<ParticipantVisitResolverType> getParticipantVisitResolverTypes()
    {
        return Collections.emptyList();
//        return Arrays.asList(
//                new StudyParticipantVisitResolverType(),
//                new ThawListResolverType());
    }

    @Override
    public boolean canCopyToStudy()
    {
        return true;
    }

    QuerySettings getResultsQuerySettings(ViewContext context, ExpProtocol protocol)
    {
        String name = AssayService.get().getResultsTableName(protocol);
        QuerySettings settings = new QuerySettings(context, name);
        settings.setSchemaName(AssaySchema.NAME);
        settings.setQueryName(name);
        return settings;
    }

    @Override
    public boolean hasCustomView(IAssayDomainType domainType, boolean details)
    {
        return details && domainType == ExpProtocol.AssayDomainTypes.Result;
    }

//    @Override
//    // XXX: tried to use the default ResultsQueryView and add column renderers to it instead
//    public QueryView createResultsView(ViewContext context, ExpProtocol protocol)
//    {
//        QuerySettings settings = getResultsQuerySettings(context, protocol);
//        return new CBCResultsQueryView(protocol, context, settings);
//    }

    @Override
    public ResultsQueryView createResultsQueryView(ViewContext context, ExpProtocol protocol)
    {
        QuerySettings settings = getResultsQuerySettings(context, protocol);
        return new CBCResultsQueryView(protocol, context, settings);
    }

    @Override
    public ModelAndView createResultDetailsView(ViewContext context, ExpProtocol protocol, ExpData data, Object dataRowId)
    {
        int objectId = -1;
        try
        {
            objectId = Integer.parseInt(String.valueOf(dataRowId));
        }
        catch (NumberFormatException ex) { }

        CBCData cbcData;
        try
        {
            cbcData = CBCData.fromObjectId(objectId, data, protocol, context.getUser());
        }
        catch (SQLException e)
        {
            throw new RuntimeSQLException(e);
        }
        if (cbcData == null || !cbcData.getContainer().equals(context.getContainer()))
            HttpView.throwNotFound("Data '" + dataRowId + "' does not exist.");

        return new JspView<CBCData>("/org/labkey/cbcassay/view/showDetails.jsp", cbcData);
    }

    public PipelineProvider getPipelineProvider()
    {
        return new AssayPipelineProvider(CBCAssayModule.class,
                new PipelineProvider.FileTypesEntryFilter(getDataType().getFileType()), this, "Import CBC");
    }
}
