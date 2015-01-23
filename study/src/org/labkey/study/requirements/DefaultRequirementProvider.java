/*
 * Copyright (c) 2007-2015 LabKey Corporation
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

package org.labkey.study.requirements;

import org.labkey.api.data.*;
import org.labkey.api.query.FieldKey;
import org.labkey.api.security.*;
import org.labkey.api.util.GUID;
import org.labkey.study.StudySchema;

import java.util.*;
import java.lang.reflect.Array;
import java.sql.SQLException;

/**
 * User: brittp
 * Date: Jun 4, 2007
 * Time: 2:36:43 PM
 */
public abstract class DefaultRequirementProvider<R extends Requirement<R>, A extends RequirementActor<A>>
        implements RequirementProvider<R, A>
{
    private Class<R> _requirementClass;
    private Class<A> _actorClass;

    public DefaultRequirementProvider(Class<R> requirementClass, Class<A> actorClass)
    {
        _requirementClass = requirementClass;
        _actorClass = actorClass;
    }

    public R[] getDefaultRequirements(Container container, RequirementType type)
    {
        String ownerId = getDefaultRequirementPlaceholder(container, type, false);
        if (ownerId == null)
            return (R[]) Array.newInstance(_requirementClass, 0);
        else
            return getRequirements(container, ownerId);
    }

    public R[] getDefaultRequirements(Container container)
    {
        Set<R> defaultRequirements = new HashSet<>();
        for (RequirementType type : getRequirementTypes())
            defaultRequirements.addAll(Arrays.asList(getDefaultRequirements(container, type)));
        return defaultRequirements.toArray((R[]) Array.newInstance(_requirementClass, defaultRequirements.size()));
    }

    public R createDefaultRequirement(User user, R requirement, RequirementType type)
    {
        String owner = getDefaultRequirementPlaceholder(requirement.getContainer(), type, true);
        requirement = requirement.createMutable();
        requirement.setOwnerEntityId(owner);
        return requirement.persist(user, owner);
    }

    private synchronized String getDefaultRequirementPlaceholder(final Container container, RequirementType type, boolean createIfMissing)
    {
        PropertyManager.PropertyMap props = PropertyManager.getWritableProperties(container,
                "DefaultRequirement: " + getClass().getSimpleName(), true);
        String ownerEntityId = props.get(type.name());
        if (ownerEntityId == null && createIfMissing)
        {
            ownerEntityId = GUID.makeGUID();
            props.put(type.name(), ownerEntityId);
            props.save();
        }
        return ownerEntityId;
    }

    public R getRequirement(Container container, Object requirementPrimaryKey)
    {
        return new TableSelector(getRequirementTableInfo()).getObject(requirementPrimaryKey, _requirementClass);
    }

    public R[] getRequirements(Container container, String ownerEntityId)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        filter.addCondition(getOwnerEntityIdColumnName(), ownerEntityId);

        return new TableSelector(getRequirementTableInfo(), filter, getDefaultRequirementSort()).getArray(_requirementClass);
    }

    protected Sort getDefaultRequirementSort()
    {
        return new Sort("RowId");
    }

    public R[] getRequirements(Container container)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(container);
        return new TableSelector(getRequirementTableInfo(), filter, getDefaultRequirementSort()).getArray(_requirementClass);
    }

    public A[] getActors(Container c)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);

        return new TableSelector(getActorTableInfo(), filter, new Sort(getActorSortColumnName())).getArray(_actorClass);
    }

    public A getActor(Container c, Object primaryKey)
    {
        return new TableSelector(getActorTableInfo()).getObject(primaryKey, _actorClass);
    }

    @Override
    public List<A> getActorsByLabel(Container c, String label)
    {
        SimpleFilter filter = SimpleFilter.createContainerFilter(c);
        filter.addCondition(FieldKey.fromParts("Label"), label);
        return new TableSelector(getActorTableInfo(), filter, new Sort(getActorSortColumnName())).getArrayList(_actorClass);
    }

    public Collection<A> getActorsInUse(Container c)
    {
        Requirement[] requirements = getRequirements(c);
        Map<Object, A> actors = new HashMap<>();
        for (Requirement requirement : requirements)
        {
            A actor = getActor(c, requirement.getActorPrimaryKey());
            actors.put(actor.getPrimaryKey(), actor);
        }
        Requirement[] defaultRequirements = getDefaultRequirements(c);
        for (Requirement requirement : defaultRequirements)
        {
            A actor = getActor(c, requirement.getActorPrimaryKey());
            actors.put(actor.getPrimaryKey(), actor);
        }
        return actors.values();
    }

    public void purgeContainer(Container c)
    {
        R[] requirements = getRequirements(c);
        for (R requirement : requirements)
            requirement.delete();

        A[] actors = getActors(c);
        for (A actor : actors)
            actor.delete();
    }


    public void deleteRequirements(RequirementOwner owner)
    {
        R[] requirements = getRequirements(owner);
        DbScope scope = StudySchema.getInstance().getSchema().getScope();
        try (DbScope.Transaction transaction = scope.ensureTransaction())
        {
            for (R requirement : requirements)
                requirement.delete();

            transaction.commit();
        }
    }

    public R[] getRequirements(RequirementOwner owner)
    {
        return getRequirements(owner.getContainer(), owner.getEntityId());
    }

    public void generateDefaultRequirements(User user, RequirementOwner owner)
    {
        for (RequirementType type : getRequirementTypes())
        {
            R[] defaultRequirements = getDefaultRequirements(owner.getContainer(), type);
            for (R defaultRequirement : defaultRequirements)
            {
                List<R> requirements = generateRequirementsFromDefault(owner, defaultRequirement, type);
                for (R requirement : requirements)
                    createRequirement(user, owner, requirement);
            }
        }
    }

    public R createRequirement(User user, RequirementOwner owner, R requirement)
    {
        return createRequirement(user, owner, requirement, false);
    }


    public R createRequirement(User user, RequirementOwner owner, R requirement, boolean forceDuplicate)
    {
        // unless we're forcing, we'll double-check to see if there's already an incomplete requirement matching this one:
        if (!forceDuplicate)
        {
            for (R possibleMatch : getRequirements(owner))
            {
                if (requirement.isEqual(possibleMatch))
                    return null;
            }
        }
        return requirement.persist(user, owner.getEntityId());
    }

    protected List<R> generateRequirementsFromDefault(RequirementOwner owner, R defaultRequirement, RequirementType type)
    {
        return Collections.singletonList(defaultRequirement);
    }

    protected abstract TableInfo getRequirementTableInfo();

    protected abstract String getOwnerEntityIdColumnName();

    protected abstract TableInfo getActorTableInfo();

    protected abstract String getActorSortColumnName();
}
