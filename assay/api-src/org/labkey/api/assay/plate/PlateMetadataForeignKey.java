package org.labkey.api.assay.plate;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.assay.AssayProvider;
import org.labkey.api.data.AbstractForeignKey;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.TableInfo;
import org.labkey.api.data.VirtualTable;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.exp.OntologyManager;
import org.labkey.api.exp.PropertyColumn;
import org.labkey.api.exp.PropertyDescriptor;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.query.AliasedColumn;
import org.labkey.api.query.ExprColumn;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.PdLookupForeignKey;
import org.labkey.api.query.UserSchema;
import org.labkey.api.util.StringExpression;

import java.util.ArrayList;
import java.util.List;

/**
 * Creates a tree of virtual tables based upon the set of plate templates.
 *
 * Metadata
 *   - WellGroupType (aka "Layer", CONTROL, SPECIMEN, VIRUS, ...) => selects the well group name the row is in
 *     - WellGroupName
 *       - Property
 *
 * TODO: The WellGroupType column should return the WellGroupName that the well is in -- there can only be one
 */
public class PlateMetadataForeignKey extends AbstractForeignKey
{
    private final UserSchema _userSchema;
    private final AssayProvider _provider;
    private final ExpProtocol _protocol;
    private final List<? extends PlateTemplate> _templates;

    public PlateMetadataForeignKey(UserSchema schema,
                                   AssayProvider provider, ExpProtocol protocol, List<? extends PlateTemplate> templates)
    {
        super(schema, null);
        _userSchema = schema;
        _provider = provider;
        _protocol = protocol;
        _templates = templates;
    }

    @Override
    public TableInfo getLookupTableInfo()
    {
        return new WellGroupTypesLookupTable(_userSchema).init();
    }

    @Override
    public @Nullable ColumnInfo createLookupColumn(ColumnInfo lsidColumn, String displayField)
    {
        return _createLookupColumn(lsidColumn, getLookupTableInfo(), displayField);
    }

    @Override
    public StringExpression getURL(ColumnInfo parent)
    {
        return null;
    }

    public ColumnInfo _createLookupColumn(ColumnInfo lsidColumn, TableInfo table, String displayField)
    {
        if (table == null)
            return null;

        if (displayField == null)
        {
            displayField = _displayColumnName;
            if (displayField == null)
                displayField = table.getTitleColumn();
        }
        if (displayField == null)
            return null;

        var lookup = table.getColumn(displayField);
        if (null == lookup)
            return null;

        // We want to create a placeholder column here that DOES NOT generate any joins,
        // that's why we extend AbstractForeignKey instead of LookupForeignKey.
        // CONSIDER: we could consider adding a "really don't add any joins" flag to LookupForeignKey for this pattern
        SQLFragment sql = lsidColumn.getValueSql(ExprColumn.STR_TABLE_ALIAS);
        var col = new ExprColumn(lsidColumn.getParentTable(), new FieldKey(lsidColumn.getFieldKey(), displayField), sql, JdbcType.VARCHAR);
        col.setFk(lookup.getFk());
        col.setUserEditable(false);
        col.setReadOnly(true);
        col.setIsUnselectable(true);
        col.setDisplayColumnFactory(lookup.getDisplayColumnFactory());
        return col;
    }

    /**
     * Table with columns for each <code>WellGroup.Type</code>
     * with FK to a table with columns for each well group.
     */
    private class WellGroupTypesLookupTable extends VirtualTable<UserSchema>
    {
        WellGroupTypesLookupTable(UserSchema schema)
        {
            super(schema.getDbSchema(), "WellGroupType", schema);
        }

        protected TableInfo init()
        {
            for (WellGroup.Type type : WellGroup.Type.values())
            {
                addWellGroupTypeColumn(type);
            }
            return this;
        }

        void addWellGroupTypeColumn(WellGroup.Type type)
        {
            SQLFragment sql = new SQLFragment("'#ERROR'");
            var col = new ExprColumn(this, FieldKey.fromParts(type.toString()), sql, JdbcType.VARCHAR);
            col.setFk(new WellGroupTypeForeignKey(_userSchema, type));
            col.setUserEditable(false);
            col.setReadOnly(true);
            col.setIsUnselectable(true);
            addColumn(col);
        }
    }

    private class WellGroupTypeForeignKey extends AbstractForeignKey
    {
        private final @NotNull WellGroup.Type _type;
        private final @NotNull UserSchema _schema;
        private TableInfo _table;

        WellGroupTypeForeignKey(@NotNull UserSchema schema, @NotNull WellGroup.Type type)
        {
            super(schema, null);
            _schema = schema;
            _type = type;
        }

        @Override
        public StringExpression getURL(ColumnInfo parent)
        {
            return null;
        }

        @Override
        public @Nullable ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
        {
            return _createLookupColumn(parent, getLookupTableInfo(), displayField);
        }

        @Override
        public @Nullable TableInfo getLookupTableInfo()
        {
            if (_table == null)
            {
                _table = createLookupTableInfo();
            }
            return _table;
        }

        private TableInfo createLookupTableInfo()
        {
            return new WellGroupTableInfo(_schema, _type).init();
        }

    }

    private class WellGroupTableInfo extends VirtualTable<UserSchema>
    {
        private final WellGroup.Type _type;

        WellGroupTableInfo(@NotNull UserSchema schema, @NotNull WellGroup.Type type)
        {
            super(schema.getDbSchema(), "WellGroup", schema);
            _type = type;
        }

        protected TableInfo init()
        {
            for (PlateTemplate template : _templates)
            {
                var wellGroups = template.getWellGroups(_type);
                for (var wellGroup : wellGroups)
                {
                    var col = getColumn(wellGroup.getName(), false);
                    if (col == null)
                    {
                        col = addWellGroupColumn(wellGroup.getName());
                    }
//                    WellGroupPropertiesForeignKey fk = (WellGroupPropertiesForeignKey)col.getFk();
//                    fk.addPlateTemplate(template);
//                    fk.addWellGroup(wellGroup);
                }
            }
            return this;
        }

        private ColumnInfo addWellGroupColumn(String wellGroupName)
        {
            SQLFragment sql = new SQLFragment("'#ERROR'");
            var col = new ExprColumn(this, FieldKey.fromParts(wellGroupName), sql, JdbcType.VARCHAR);
            col.setFk(new WellGroupPropertiesForeignKey(getUserSchema(), _type, wellGroupName));
            col.setUserEditable(false);
            col.setReadOnly(true);
            col.setIsUnselectable(true);
//            applyDisplayColumn(col, 0, level.expType, null, null);
            addColumn(col);
            return col;
        }

    }

    private class WellGroupPropertiesForeignKey extends AbstractForeignKey
    {
        private final @NotNull UserSchema _schema;
        private final WellGroup.Type _type;
        private final String _wellGroupName;
//        private final ArrayList<PlateTemplate> _templatesWithWellGroup;
//        private final ArrayList<WellGroupTemplate> _wellGroupTemplates;
        private TableInfo _table;

        protected WellGroupPropertiesForeignKey(@NotNull UserSchema schema, WellGroup.Type type, String wellGroupName)
        {
            super(schema, null);
            _schema = schema;
            _type = type;
            _wellGroupName = wellGroupName;
//            _templatesWithWellGroup = new ArrayList<>(10);
//            _wellGroupTemplates = new ArrayList<>(10);
        }

        @Override
        public StringExpression getURL(ColumnInfo parent)
        {
            return null;
        }

        @Override
        public @Nullable ColumnInfo createLookupColumn(ColumnInfo parent, String displayField)
        {
            // TODO: displayField will be a property name within the well group
            PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(63428);
            return new PropertyColumn(pd, parent, getLookupContainer(), getLookupUser(), false);
        }

        @Override
        public @Nullable TableInfo getLookupTableInfo()
        {
            if (_table == null)
            {
                _table = createLookupTableInfo();
            }
            return _table;
        }

        private TableInfo createLookupTableInfo()
        {
            return new WellGroupPropertiesTable(_schema, _type, _wellGroupName).init();
        }
    }


    /**
     * This table is only used to display the set of available well group properties within customize view.
     * The <code>WellGroupPropertiesForeignKey.createLookupColumn</code> creates the actual PropertyColumn.
     */
    private class WellGroupPropertiesTable extends VirtualTable<UserSchema>
    {
        private final WellGroup.Type _type;
        private final String _wellGroupName;
        private final BaseColumnInfo _objectUriCol;

        public WellGroupPropertiesTable(@NotNull UserSchema schema, WellGroup.Type type, String wellGroupName)
        {
            super(schema.getDbSchema(), "WellGroupProperties", schema);
            _type = type;
            _wellGroupName = wellGroupName;

            SQLFragment sql = new SQLFragment("'#ERROR'");
            _objectUriCol = new ExprColumn(this, "objectUri", sql, JdbcType.VARCHAR);
            _objectUriCol.setHidden(true);
        }

        protected TableInfo init()
        {
//            for (WellGroupTemplate wellGroupTemplate : _wellGroupTemplates)
//            {
//                // TODO: Get the PropertyDescriptors used by the well groups
//            }

            PropertyDescriptor pd = OntologyManager.getPropertyDescriptor(63428);
            if (pd != null)
                addWellGroupPropertyColumn(pd);

            return this;
        }

        @Override
        protected ColumnInfo resolveColumn(String name)
        {
            if (name.equalsIgnoreCase("objectUri"))
                return _objectUriCol;

            return super.resolveColumn(name);
        }

        protected void addWellGroupPropertyColumn(PropertyDescriptor pd)
        {
            PropertyColumn col = new PropertyColumn(pd, _objectUriCol, _protocol.getContainer(), getUserSchema().getUser(), false);
            addColumn(col);
        }

    }


}
