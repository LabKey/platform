package org.labkey.api.data;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.query.FieldKey;

import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: matthewb
 * Date: 9/11/12
 * Time: 10:21 PM
 *
 * Should probably be included on DisplayColumnFactory.  However ther are a lot of implementations to up date.
 * ColumnInfo calls checkLocked(), but be careful about modifying shared instances.
 */
public interface RemappingDisplayColumnFactory extends DisplayColumnFactory
{
    public void remapFieldKeys(@Nullable FieldKey parent, @Nullable Map<FieldKey, FieldKey> remap);
}
