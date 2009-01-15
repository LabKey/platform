/*
 * Copyright (c) 2009 LabKey Corporation
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
package org.labkey.experiment.api;

import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;

import java.util.Date;

/**
 * User: jeckels
 * Date: Jan 14, 2009
 */
public abstract class ExpIdentifiableEntityImpl<Type extends IdentifiableEntity> extends ExpIdentifiableBaseImpl<Type>
{
    public ExpIdentifiableEntityImpl(Type object)
    {
        super(object);
    }

    public Date getCreated()
    {
        return _object.getCreated();
    }

    public User getCreatedBy()
    {
        return UserManager.getUser(_object.getCreatedBy());
    }

    public Date getModified()
    {
        return _object.getModified();
    }

    public User getModifiedBy()
    {
        return UserManager.getUser(_object.getModifiedBy());
    }
}