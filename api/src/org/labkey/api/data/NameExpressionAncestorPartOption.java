/*
 * Copyright (c) 2022-2026 LabKey Corporation
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
package org.labkey.api.data;

import org.labkey.api.exp.api.ExpLineageOptions;
import org.labkey.api.util.Pair;

import java.util.List;

/**
 * Used for name expression containing ancestor lookup parts, such as:
 * ${MaterialInputs/ParentSampleType1/..[MaterialInputs/GrandParentType]/..[DataInputs/GreatGrandParentType]/barcode}
 * For the above example, the params will be as follows:
 * options: The ExpLineageOptions configuration for this ancestor path (depth, etc)
 * parentType: Nullable, the sample type or dataclass name. If ancestorPaths is null, parentType must not be null
 * ancestorSearchType: <ExpLineageOptions.LineageExpType, ancestorDataTypeName>. nullable, used if querying for all ancestors
 * ancestorPaths: [{'Material', 'GrandParentType-LSID'}, {'Data', 'GreatGrandParentType-LSID'}], nullable
 * lookupColumn: barcode
 */
public record NameExpressionAncestorPartOption(ExpLineageOptions options, String parentType, Pair<ExpLineageOptions.LineageExpType, String> ancestorSearchType, List<Pair<ExpLineageOptions.LineageExpType, String>> ancestorPaths, String lookupColumn)
{
}
