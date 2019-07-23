/*
 * Copyright (c) 2019 LabKey Corporation
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
package org.labkey.api.qc;

import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.List;

public interface QCStateHandler<FORM extends AbstractManageQCStatesForm>
{
    List<QCState> getQCStates(Container container);
    boolean isQCStateInUse(Container container, QCState state);
    boolean isBlankQCStatePublic(Container container);
    void updateQcState(Container container, FORM form, User user);
    static <T> boolean nullSafeEqual(T first, T second)
    {
        if (first == null && second == null)
            return true;
        if (first == null)
            return false;
        return first.equals(second);
    }
}
