/*
 * Copyright (c) 2006-2017 LabKey Corporation
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

package org.labkey.api.study;

import org.labkey.api.data.Container;
import org.labkey.api.security.SecurityPolicy;
import org.labkey.api.security.SecurableResource;
import org.labkey.api.security.MutableSecurityPolicy;
import org.labkey.api.security.User;

/**
 * User: Matthew
 * Date: Jun 6, 2006
 * Time: 2:57:59 PM
 */
public interface StudyEntity extends SecurableResource
{
    Container getContainer();

    void setContainer(Container container);

    String getLabel();

    void setLabel(String label);

    int getDisplayOrder();

    void setDisplayOrder(int displayOrder);

    boolean isShowByDefault();

    void setShowByDefault(boolean showByDefault);

    String getDisplayString();

    String getEntityId();

    void setEntityId(String entityId);

    SecurityPolicy getPolicy();

    void savePolicy(MutableSecurityPolicy policy, User user);

}
