/*
 * Copyright (c) 2008-2013 LabKey Corporation
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

package org.labkey.api.study.query;

import org.labkey.api.data.*;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExpSampleSet;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.exp.query.ExpMaterialTable;
import org.labkey.api.exp.query.ExpRunTable;
import org.labkey.api.exp.query.ExpSchema;
import org.labkey.api.query.*;
import org.labkey.api.study.assay.*;

import java.util.*;

/**
 * User: Karl Lum
 * Date: Jan 21, 2008
 */
public abstract class PlateBasedAssayRunDataTable extends FilteredTable<AssaySchema>
{
    public static final String RUN_ID_COLUMN_NAME = "RunId";

    public abstract PropertyDescriptor[] getExistingDataProperties(ExpProtocol protocol);
    public abstract String getInputMaterialPropertyName();
    public abstract String getDataRowLsidPrefix();

    public PlateBasedAssayRunDataTable(final AssaySchema schema, final ExpProtocol protocol)
    {
        super(new ProtocolFilteredObjectTable(schema, protocol.getLSID()), schema);

        final AssayProvider provider = AssayService.get().getProvider(protocol);
        List<FieldKey> visibleColumns = new ArrayList<>();

        // add any property columns
        addPropertyColumns(schema, protocol, provider, visibleColumns);

        // TODO - we should have a more reliable (and speedier) way of identifying just the data rows here
        SQLFragment dataRowClause = new SQLFragment("ObjectURI LIKE '%" + getDataRowLsidPrefix() + "%'");
        addCondition(dataRowClause, FieldKey.fromParts("ObjectURI"));

        ExprColumn runColumn = new ExprColumn(this, "Run", new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".RunID"), JdbcType.INTEGER);
        runColumn.setFk(new LookupForeignKey("RowID")
        {
            public TableInfo getLookupTableInfo()
            {
                ExpRunTable expRunTable = AssayService.get().createRunTable(protocol, provider, schema.getUser(), schema.getContainer());
                expRunTable.setContainerFilter(getContainerFilter());
                return expRunTable;
            }
        });
        addColumn(runColumn);

        ExprColumn runIdColumn = new ExprColumn(this, RUN_ID_COLUMN_NAME, new SQLFragment(ExprColumn.STR_TABLE_ALIAS + ".RunID"), JdbcType.INTEGER);
        ColumnInfo addedRunIdColumn = addColumn(runIdColumn);
        addedRunIdColumn.setHidden(true);

        Set<String> hiddenProperties = new HashSet<>();
        hiddenProperties.add(AbstractAssayProvider.PARTICIPANTID_PROPERTY_NAME);
        hiddenProperties.add(AbstractAssayProvider.PARTICIPANT_VISIT_RESOLVER_PROPERTY_NAME);
        Domain runDomain = provider.getRunDomain(protocol);
        for (DomainProperty prop : runDomain.getProperties())
        {
            if (!hiddenProperties.contains(prop.getName()))
                visibleColumns.add(FieldKey.fromParts("Run", prop.getName()));
        }
        Domain uploadSetDomain = provider.getBatchDomain(protocol);
        for (DomainProperty prop : uploadSetDomain.getProperties())
        {
            if (!hiddenProperties.contains(prop.getName()))
                visibleColumns.add(FieldKey.fromParts("Run", AssayService.BATCH_COLUMN_NAME, prop.getName()));
        }
        setDefaultVisibleColumns(visibleColumns);
    }

    protected void addPropertyColumns(final AssaySchema schema, final ExpProtocol protocol, final AssayProvider provider, List<FieldKey> visibleColumns)
    {
        // add material lookup columns to the view first, so they appear at the left:
        String sampleDomainURI = AbstractAssayProvider.getDomainURIForPrefix(protocol, AbstractPlateBasedAssayProvider.ASSAY_DOMAIN_SAMPLE_WELLGROUP);
        final ExpSampleSet sampleSet = ExperimentService.get().getSampleSet(sampleDomainURI);
        if (sampleSet != null)
        {
            for (DomainProperty pd : sampleSet.getType().getProperties())
            {
                visibleColumns.add(FieldKey.fromParts("Properties", getInputMaterialPropertyName(),
                        ExpMaterialTable.Column.Property.toString(), pd.getName()));
            }
        }
        // get all the properties from this plated-based protocol:
        PropertyDescriptor[] pds = getExistingDataProperties(protocol);

        // add object ID to this tableinfo and set it as a key field:
        ColumnInfo objectIdColumn = addWrapColumn(_rootTable.getColumn("ObjectId"));
        objectIdColumn.setKeyField(true);

        // add object ID again, this time as a lookup to a virtual property table that contains our selected NAB properties:
        ColumnInfo propertyLookupColumn = wrapColumn("Properties", _rootTable.getColumn("ObjectUri"));
        propertyLookupColumn.setKeyField(false);
        propertyLookupColumn.setIsUnselectable(true);
        QcAwarePropertyForeignKey fk = new QcAwarePropertyForeignKey(pds, this, schema)
        {
            @Override
            protected ColumnInfo constructColumnInfo(ColumnInfo parent, FieldKey name, PropertyDescriptor pd)
            {
                ColumnInfo result = super.constructColumnInfo(parent, name, pd);
                if (getInputMaterialPropertyName().equals(pd.getName()))
                {
                    result.setLabel("Specimen");
                    result.setFk(new LookupForeignKey("LSID")
                    {
                        public TableInfo getLookupTableInfo()
                        {
                            ExpMaterialTable materials = ExperimentService.get().createMaterialTable(ExpSchema.TableType.Materials.toString(), schema);
                            // Make sure we are filtering to the same set of containers
                            materials.setContainerFilter(getContainerFilter());
                            if (sampleSet != null)
                            {
                                materials.setSampleSet(sampleSet, true);
                            }
                            ColumnInfo propertyCol = materials.addColumn(ExpMaterialTable.Column.Property);
                            if (propertyCol.getFk() instanceof PropertyForeignKey)
                            {
                                ((PropertyForeignKey)propertyCol.getFk()).addDecorator(new SpecimenPropertyColumnDecorator(provider, protocol, schema));
                            }
                            propertyCol.setHidden(false);
                            materials.addColumn(ExpMaterialTable.Column.LSID).setHidden(true);
                            return materials;
                        }
                    });
                }
                return result;
            }
        };
        propertyLookupColumn.setFk(fk);
        addColumn(propertyLookupColumn);

        Set<String> hiddenCols = getHiddenColumns(protocol);
        for (PropertyDescriptor pd : fk.getDefaultHiddenProperties())
            hiddenCols.add(pd.getName());
        hiddenCols.add(getInputMaterialPropertyName());
        // run through the property columns, setting all to be visible by default:
        FieldKey dataKeyProp = FieldKey.fromParts(propertyLookupColumn.getName());
        for (PropertyDescriptor lookupCol : pds)
        {
            if (!hiddenCols.contains(lookupCol.getName()))
            {
                FieldKey key = new FieldKey(dataKeyProp, lookupCol.getName());
                visibleColumns.add(key);
            }
        }
    }

    protected Set<String> getHiddenColumns(ExpProtocol protocol)
    {
        return new HashSet<>();
    }
}
