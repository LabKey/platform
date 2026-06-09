/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

package org.labkey.specimen.requirements;

import org.junit.Test;
import org.labkey.api.data.Container;
import org.labkey.api.data.TableInfo;
import org.labkey.api.security.permissions.AbstractContainerScopingTest;
import org.labkey.api.specimen.SpecimenSchema;
import org.labkey.api.util.GUID;
import org.labkey.specimen.model.SpecimenRequestActor;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * User: brittp
 * Date: Jun 4, 2007
 * Time: 4:29:27 PM
 */
public class SpecimenRequestRequirementProvider extends DefaultRequirementProvider<SpecimenRequestRequirement, SpecimenRequestActor>
{
    private static final SpecimenRequestRequirementProvider INSTANCE = new SpecimenRequestRequirementProvider();

    public static SpecimenRequestRequirementProvider get()
    {
        return INSTANCE;
    }

    private SpecimenRequestRequirementProvider()
    {
        super(SpecimenRequestRequirement.class, SpecimenRequestActor.class);
    }

    @Override
    public RequirementType[] getRequirementTypes()
    {
        return SpecimenRequestRequirementType.values();
    }

    @Override
    protected String getOwnerEntityIdColumnName()
    {
        return "OwnerEntityId";
    }

    protected Object getPrimaryKeyValue(SpecimenRequestRequirement requirement)
    {
        return requirement.getRowId();
    }

    @Override
    protected String getActorSortColumnName()
    {
        return "SortOrder";
    }

    @Override
    protected TableInfo getActorTableInfo()
    {
        return SpecimenSchema.get().getTableInfoSampleRequestActor();
    }

    @Override
    protected TableInfo getRequirementTableInfo()
    {
        return SpecimenSchema.get().getTableInfoSampleRequestRequirement();
    }

    protected SpecimenRequestRequirement createMutable(SpecimenRequestRequirement requirement)
    {
        return requirement.createMutable();
    }

    @Override
    protected List<SpecimenRequestRequirement> generateRequirementsFromDefault(RequirementOwner owner, SpecimenRequestRequirement defaultRequirement, RequirementType type)
    {
        return ((SpecimenRequestRequirementType) type).generateRequirements((SpecimenRequest) owner, defaultRequirement);
    }

    public Set<Integer> getActorsInUseSet(Container container)
    {
        Collection<SpecimenRequestActor> actors = getActorsInUse(container);
        Set<Integer> ids = new HashSet<>();
        for (SpecimenRequestActor actor : actors)
            ids.add(actor.getRowId());
        return ids;
    }

    public static class ContainerScopingTestCase extends AbstractContainerScopingTest
    {
        @Test
        public void testGetActorContainerScoping()
        {
            Container folderA = createContainer("A");
            Container folderB = createContainer("B");
            SpecimenRequestRequirementProvider provider = SpecimenRequestRequirementProvider.get();

            SpecimenRequestActor actor = new SpecimenRequestActor();
            actor.setContainer(folderA);
            actor.setLabel("Scoping test actor");
            actor = actor.create(getAdmin());
            int rowId = actor.getRowId();

            assertNotNull("Actor should be visible from its own container", provider.getActor(folderA, rowId));
            assertNull("Actor must NOT be visible from another container", provider.getActor(folderB, rowId));
        }

        @Test
        public void testGetRequirementContainerScoping()
        {
            Container folderA = createContainer("A");
            Container folderB = createContainer("B");
            SpecimenRequestRequirementProvider provider = SpecimenRequestRequirementProvider.get();

            // A requirement references an actor through a NOT NULL foreign key, so create one in the same folder first
            SpecimenRequestActor actor = new SpecimenRequestActor();
            actor.setContainer(folderA);
            actor.setLabel("Requirement scoping test actor");
            actor = actor.create(getAdmin());

            SpecimenRequestRequirement requirement = new SpecimenRequestRequirement();
            requirement.setContainer(folderA);
            requirement.setRequestId(-1);       // no real request row is needed for a primary-key lookup
            requirement.setActorId(actor.getRowId());
            requirement.setDescription("Requirement scoping test");
            requirement = requirement.persist(getAdmin(), GUID.makeGUID());
            int rowId = requirement.getRowId();

            assertNotNull("Requirement should be visible from its own container", provider.getRequirement(folderA, rowId));
            assertNull("Requirement must NOT be visible from another container", provider.getRequirement(folderB, rowId));
        }
    }
}
