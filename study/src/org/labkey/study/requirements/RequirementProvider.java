/*
 * Copyright (c) 2007-2013 LabKey Corporation
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

import org.labkey.api.security.User;
import org.labkey.api.data.Container;

import java.util.Collection;
import java.util.List;

/**
 * User: brittp
 * Date: Jun 4, 2007
 * Time: 2:37:53 PM
 */
public interface RequirementProvider<R extends Requirement<R>, 
                                     A extends RequirementActor<A>>
{
    R getRequirement(Container container, Object requirementPrimaryKey);

    A[] getActors(Container c);

    A getActor(Container c, Object primaryKey);

    List<A> getActorsByLabel(Container c, String label);

    Collection<A> getActorsInUse(Container c);
    
    R[] getDefaultRequirements(Container container, RequirementType type);

    void generateDefaultRequirements(User user, RequirementOwner owner);

    void purgeContainer(Container c);

    R createDefaultRequirement(User user, R requirement, RequirementType type);

    RequirementType[] getRequirementTypes();

    R createRequirement(User user, RequirementOwner owner, R requirement);

    R createRequirement(User user, RequirementOwner owner, R requirement, boolean forceDuplicate);

    void deleteRequirements(RequirementOwner owner);

    R[] getRequirements(RequirementOwner owner);
}
