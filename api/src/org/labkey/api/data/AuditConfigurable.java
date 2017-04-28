package org.labkey.api.data;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.gwt.client.AuditBehaviorType;
import org.labkey.api.query.FieldKey;

/**
 * Indicates that inserts, updates and deletes on a table can be auditable and supports setting
 * different AuditBehaviorType configurations.
 *
 * Created by klum on 4/26/2017.
 */
public interface AuditConfigurable extends TableInfo
{
    void setAuditBehavior(AuditBehaviorType type);
    AuditBehaviorType getAuditBehavior();

    /**
     * Returns the row primary key column to use for audit history details. Note, this must
     * be a single key as we don't support multiple column primary keys for audit details.
     */
    @Nullable
    FieldKey getAuditRowPk();
}
