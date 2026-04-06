package org.labkey.core.query;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.attachments.AttachmentService;
import org.labkey.api.collections.CsvSet;
import org.labkey.api.collections.LabKeyCollectors;
import org.labkey.api.data.BaseColumnInfo;
import org.labkey.api.data.ContainerFilter;
import org.labkey.api.data.CoreSchema;
import org.labkey.api.data.JdbcType;
import org.labkey.api.data.MutableColumnInfo;
import org.labkey.api.data.SQLFragment;
import org.labkey.api.data.dialect.SqlDialect;
import org.labkey.api.query.AliasManager;
import org.labkey.api.query.FieldKey;
import org.labkey.api.query.FilteredTable;
import org.labkey.api.query.UserIdQueryForeignKey;

import java.util.Objects;

public class DocumentsTable extends FilteredTable<CoreQuerySchema>
{
    public DocumentsTable(@NotNull CoreQuerySchema userSchema, ContainerFilter cf)
    {
        super(CoreSchema.getInstance().getTableInfoDocuments(), userSchema, cf);
        wrapAllColumns(true);
        getMutableColumnOrThrow("RowId").setHidden(true);
        getMutableColumnOrThrow("ModifiedBy").setHidden(true);
        getMutableColumnOrThrow("Modified").setHidden(true);
        MutableColumnInfo owner = getMutableColumnOrThrow("Owner");
        owner.setFk(new UserIdQueryForeignKey(_userSchema));
        getMutableColumnOrThrow("Parent").setHidden(true);
        getMutableColumnOrThrow("DocumentSize").setFormat("#,##0");
        getMutableColumnOrThrow("Document").setHidden(true);
        getMutableColumnOrThrow("LastIndexed").setHidden(true);
        addColumn(new BaseColumnInfo("ParentDescription", this, JdbcType.VARCHAR));
        BaseColumnInfo orphaned = new BaseColumnInfo("Orphaned", this, JdbcType.BOOLEAN);
        addColumn(orphaned);

        setDefaultVisibleColumns(
            new CsvSet("CreatedBy, Created, Container, DocumentName, DocumentSize, DocumentType, ParentType, ParentDescription")
                .stream()
                .map(FieldKey::fromParts)
                .toList()
        );
    }

    @Override
    public @NotNull SQLFragment getFromSQL(String alias)
    {
        SqlDialect dialect = getSqlDialect();
        AliasManager am = new AliasManager(dialect);
        SQLFragment parents = AttachmentService.get().getAttachmentParentTypes().stream()
            .map(type -> new SQLFragment("SELECT ? AS ParentType, EntityId, Description FROM (")
                .add(type.getUniqueName())
                .append(type.getSelectEntityIdAndDescriptionSql())
                .append(") ")
                .appendIdentifier(am.decideAlias("x")))
            .filter(Objects::nonNull)
            .collect(LabKeyCollectors.joining(new SQLFragment("\nUNION\n")));

        return new SQLFragment("(SELECT d.*, p.Description AS ParentDescription, ")
            .append(dialect.wrapBooleanExpression(new SQLFragment("EntityId IS NULL")))
            .append(" AS Orphaned FROM ")
            .append(super.getFromSQL("d")) // core.Documents with container filter applied
            .append(" LEFT JOIN (\n")
            .append(parents)
            .append("\n) p ON d.Parent = p.EntityId AND d.ParentType = p.ParentType) ")
            .append(alias);
    }
}
