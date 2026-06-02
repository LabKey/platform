/*
 * Copyright (c) 2024-2026 LabKey Corporation
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
package org.labkey.api.assay.sample;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.exp.api.ExpMaterialRunInput;
import org.labkey.api.assay.sample.AssaySampleLookupContext.MaterialInput;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

public class MaterialInputRoleComparator implements Comparator<MaterialInput>
{
    private final Collection<String> _lineageRoles;

    public MaterialInputRoleComparator(Collection<String> lineageRoles)
    {
        _lineageRoles = lineageRoles;
    }

    @Override
    public int compare(MaterialInput first, MaterialInput second)
    {
        var firstRole = first.role();
        var secondRole = second.role();

        if (firstRole == null && secondRole == null)
            return 0;
        if (firstRole == null)
            return -1;
        if (secondRole == null)
            return 1;

        if (Objects.equals(firstRole, secondRole))
            return 0;

        var firstLineage = _lineageRoles.contains(firstRole);
        var secondLineage = _lineageRoles.contains(secondRole);

        if (firstLineage && secondLineage)
            return firstRole.compareTo(secondRole);

        // Always choose non-lookup based roles first, so they do not get evicted in the future.
        if (firstLineage)
            return 1;
        if (secondLineage)
            return -1;

        return firstRole.compareTo(secondRole);
    }

    public static final class TestCase extends Assert
    {
        @Test
        public void testSorting()
        {
            // Arrange
            var lineageRoles = new HashSet<String>();
            lineageRoles.add("Father");
            lineageRoles.add("Mother");
            lineageRoles.add("Parent");

            var father = new MaterialInput(1, "Father");
            var friend = new MaterialInput(1, "Friend");
            var mother = new MaterialInput(1, "Mother");
            var parent = new MaterialInput(1, "Parent");
            var noRole = new MaterialInput(1, null);
            var emptyRole = new MaterialInput(2, "");
            var defaultRole = new MaterialInput(2, ExpMaterialRunInput.DEFAULT_ROLE);

            // Act
            var inputs = new ArrayList<>(List.of(parent, emptyRole, father, noRole, defaultRole, mother, friend));
            inputs.sort(new MaterialInputRoleComparator(lineageRoles));

            // Assert
            assertEquals(List.of(noRole, emptyRole, friend, defaultRole, father, mother, parent), inputs);
        }
    }
}
