package org.labkey.api.annotations;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

// Used to tag classes & methods that should be migrated soon, e.g., those that should be moved from one module to another
@Retention(RetentionPolicy.SOURCE)
public @interface Migrate
{
}
