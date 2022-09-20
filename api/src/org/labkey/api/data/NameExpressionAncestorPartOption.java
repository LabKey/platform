package org.labkey.api.data;

import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.util.Pair;

import java.util.List;

/**
 * Used for name expression containing ancestor lookup parts, such as:
 * ${MaterialInputs/ParentSampleType1/..[MaterialInputs/GrandParentType]/..[DataInputs/GreatGrandParentType]/barcode}
 * For the above example, the params will be as follows:
 * options: The ExpLineageOptions configuration for this ancestor path (depth, etc)
 * ancestorPaths: [{'Material', 'GrandParentType-LSID'}, {'Data', 'GreatGrandParentType-LSID'}]
 * lookupColumn: barcode
 */
public record NameExpressionAncestorPartOption(ExpLineageOptions options, List<Pair<ExpLineageOptions.LineageExpType, String>> ancestorPaths, String lookupColumn)
{
}
