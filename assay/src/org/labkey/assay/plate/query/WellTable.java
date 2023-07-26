package org.labkey.assay.plate.query;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.plate.Well;
import org.labkey.api.collections.CaseInsensitiveHashSet;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.TableInfo;
import org.labkey.api.dataiterator.DataIteratorBuilder;
import org.labkey.api.dataiterator.DataIteratorContext;
import org.labkey.api.dataiterator.DetailedAuditLogDataIterator;
import org.labkey.api.dataiterator.LoggingDataIterator;
import org.labkey.api.dataiterator.SimpleTranslator;
import org.labkey.api.dataiterator.StandardDataIteratorBuilder;
import org.labkey.api.dataiterator.TableInsertDataIteratorBuilder;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.property.Domain;
import org.labkey.api.exp.property.PropertyService;
import org.labkey.api.query.BatchValidationException;
import org.labkey.api.query.DefaultQueryUpdateService;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.PropertyForeignKey;
import org.labkey.api.query.QueryForeignKey;
import org.labkey.api.query.QueryUpdateService;
import org.labkey.api.query.SimpleUserSchema;
import org.labkey.api.query.UserSchema;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.assay.plate.PlateManager;
import org.labkey.assay.query.AssayDbSchema;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.labkey.api.data.UpdateableTableInfo.ObjectUriType.schemaColumn;

public class WellTable extends SimpleUserSchema.SimpleTable<UserSchema>
{
    public static final String NAME = "Well";
    private static final List<FieldKey> defaultVisibleColumns = new ArrayList<>();

    static
    {
        defaultVisibleColumns.add(FieldKey.fromParts("PlateId"));
        defaultVisibleColumns.add(FieldKey.fromParts("Row"));
        defaultVisibleColumns.add(FieldKey.fromParts("Col"));
    }

    public WellTable(PlateSchema schema, @Nullable ContainerFilter cf)
    {
        super(schema, AssayDbSchema.getInstance().getTableInfoWell(), cf);
    }

    @Override
    public void addColumns()
    {
        super.addColumns();
        addVocabularyDomains();
    }

    private void addVocabularyDomains()
    {
        // for now there is just a single domain supported
        Domain domain = PlateManager.get().getPlateMetadataDomain(getContainer(), getUserSchema().getUser());
        if (domain != null)
        {
            String colName = "PlateMetadata";
            var col = addVocabularyDomainColumns(domain, colName);
            if (col != null)
            {
                col.setLabel(domain.getName());
                col.setDescription("Properties from " + domain.getLabel(getContainer()));
            }
        }
    }

    private MutableColumnInfo addVocabularyDomainColumns(Domain domain, @NotNull String lookupColName)
    {
        var lsidColumn = _rootTable.getColumn(FieldKey.fromParts("lsid"));
        if (lsidColumn == null)
            return null;

        var colProperty = wrapColumn(lookupColName, lsidColumn);
        colProperty.setFk(new PropertyForeignKey(_userSchema, getContainerFilter(), domain));
        colProperty.setUserEditable(false);
        colProperty.setIsUnselectable(true);
        colProperty.setCalculated(true);

        return addColumn(colProperty);
    }

    @Override
    public MutableColumnInfo wrapColumn(ColumnInfo col)
    {
        var columnInfo = super.wrapColumn(col);

        // workaround for sample lookup not resolving correctly
        if (columnInfo.getName().equalsIgnoreCase("SampleId"))
        {
            columnInfo.setFk(QueryForeignKey.from(getUserSchema(), getContainerFilter())
                    .schema("exp", getContainer())
                    .to("Materials", "RowId", "Name"));
        }
        return columnInfo;
    }

    @Override
    public ObjectUriType getObjectUriType()
    {
        return schemaColumn;
    }

    @Override
    public @Nullable String getObjectURIColumnName()
    {
        return "lsid";
    }

    @Override
    public List<FieldKey> getDefaultVisibleColumns()
    {
        return defaultVisibleColumns;
    }

    @Override
    public QueryUpdateService getUpdateService()
    {
        return new WellUpdateService(this, AssayDbSchema.getInstance().getTableInfoWell());
    }

    protected static class WellUpdateService extends DefaultQueryUpdateService
    {
        public WellUpdateService(TableInfo queryTable, TableInfo dbTable)
        {
            super(queryTable, dbTable);
        }

        @Override
        public DataIteratorBuilder createImportDIB(User user, Container container, DataIteratorBuilder data, DataIteratorContext context)
        {
            final TableInfo wellTable = getQueryTable();

            SimpleTranslator lsidRemover = new SimpleTranslator(data.getDataIterator(context), context);
            lsidRemover.selectAll();
            if (lsidRemover.getColumnNameMap().containsKey("lsid"))
            {
                // remove any furnished lsid since we will be computing one
                lsidRemover.removeColumn(lsidRemover.getColumnNameMap().get("lsid"));
            }

            SimpleTranslator lsidGenerator = new SimpleTranslator(lsidRemover, context);
            lsidGenerator.setDebugName("lsidGenerator");
            lsidGenerator.selectAll();
            final Map<String, Integer> nameMap = lsidGenerator.getColumnNameMap();

            // consider enforcing this in the schema
            if (!nameMap.containsKey("row") || !nameMap.containsKey("col"))
            {
                context.getErrors().addRowError(new ValidationException("Row and Col are required fields"));
                return data;
            }

            // generate a value for the lsid
            lsidGenerator.addColumn(wellTable.getColumn("lsid"),
                    (Supplier) () -> {
                        Object row = lsidGenerator.get(nameMap.get("row"));
                        Object col = lsidGenerator.get(nameMap.get("col"));

                        Lsid lsid = PlateManager.get().getLsid(Well.class, container, true, true);
                        return String.format("%s-well-%s-%s", lsid.toString(), row, col);
                    });

            DataIteratorBuilder dib = StandardDataIteratorBuilder.forInsert(wellTable, lsidGenerator, container, user, context);
            dib = new TableInsertDataIteratorBuilder(dib, wellTable, container)
                    .setKeyColumns(new CaseInsensitiveHashSet("RowId", "Lsid"))
                    .setVocabularyProperties(PropertyService.get().findVocabularyProperties(container, nameMap.keySet()));
            dib = LoggingDataIterator.wrap(dib);
            dib = DetailedAuditLogDataIterator.getDataIteratorBuilder(getQueryTable(), dib, context.getInsertOption(), user, container);

            return dib;
        }

        @Override
        public List<Map<String, Object>> insertRows(User user, Container container, List<Map<String, Object>> rows, BatchValidationException errors, @Nullable Map<Enum, Object> configParameters, Map<String, Object> extraScriptContext)
        {
            return super._insertRowsUsingDIB(user, container, rows, getDataIteratorContext(errors, InsertOption.INSERT, configParameters), extraScriptContext);
        }
    }
}
