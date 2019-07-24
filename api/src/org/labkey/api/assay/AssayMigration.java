package org.labkey.api.assay;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Used to mark changes introduced to support migrating assay functionality from the study module to the assay module.
 * Once the migration is complete, these changes should be reviewed and (in most cases) removed. *
 */
@Retention(RetentionPolicy.SOURCE)
public @interface AssayMigration
{
}
