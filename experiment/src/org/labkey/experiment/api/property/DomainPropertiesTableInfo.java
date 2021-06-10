package org.labkey.experiment.api.property;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.Table;
import org.labkey.api.data.TableInfo;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.column.BuiltInColumnTypes;

import java.util.Map;

// CONSIDER: Filter to just Vocabulary domains?
public class DomainPropertiesTableInfo extends FilteredTable<PropertyUserSchema>
{
    public DomainPropertiesTableInfo(PropertyUserSchema schema, ContainerFilter cf)
    {
        super(ExperimentService.get().getTinfoPropertyDescriptor(), schema, cf);
        setName(PropertyUserSchema.TableType.DomainProperties.name());
        setDescription("Metadata table containing one row of metadata for each column in all domains.");
    }

    public DomainPropertiesTableInfo populateColumns()
    {
        // add exp.PropertyDomain lookup to domain
        TableInfo domainPropTable = OntologyManager.getTinfoPropertyDomain();
        MutableColumnInfo domainId = wrapColumnFromJoinedTable("DomainId", domainPropTable.getColumn("DomainId"));
        domainId.setFk(new QueryForeignKey.Builder(getUserSchema(), getContainerFilter()).to("Domains", "DomainId", "Name"));
        addColumn(domainId);

        // wrap all exp.PropertyDescriptor table columns
        wrapAllColumns(true);

        getMutableColumn("Project").setConceptURI(BuiltInColumnTypes.CONTAINERID_CONCEPT_URI);

        return this;
    }

    @Override
    public @NotNull SQLFragment getFromSQL(String alias)
    {
        TableInfo propDescTable = getRealTable();
        TableInfo domainPropTable = OntologyManager.getTinfoPropertyDomain();

        SQLFragment result = new SQLFragment();
        result.append("(SELECT dp.DomainId, pd.* FROM ");
        result.append(super.getFromSQL("pd")).append("\n");
        result.append("JOIN ").append(domainPropTable, "dp").append(" ON dp.PropertyId = pd.PropertyId\n");

//        SQLFragment datasetFilter = DatasetsTable.getDatasetFilter(getContainer()).getSQLFragment(getSqlDialect());
//        result.append(datasetFilter);
//        result.append(") AS DataSet ON DataSet.TypeURI = DomainDescriptor.DomainURI) ");

        // WHERE
        Map<FieldKey, ColumnInfo> columnMap = Table.createColumnMap(getFromTable(), getFromTable().getColumns());
        SQLFragment filterFrag = getFilter().getSQLFragment(_rootTable.getSqlDialect(), alias, columnMap);
        result.append("\n").append(filterFrag).append(") ").append(alias);

        return result;
    }
}

