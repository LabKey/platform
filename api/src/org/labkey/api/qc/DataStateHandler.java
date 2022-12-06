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

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;

import java.util.List;
import java.util.Map;

public interface DataStateHandler<FORM extends AbstractManageDataStatesForm>
{
    List<DataState> getStates(Container container);
    boolean isStateInUse(Container container, DataState state);
    boolean isBlankStatePublic(Container container);
    boolean isRequireCommentOnQCStateChange(Container container);

    /**
     * Check if a given state allows for changes based on things like if it is in-use, etc. and return the error
     * message to show to the user if that state change is not allowed.
     * @param container
     * @param state The QC state being changed
     * @param rowUpdates The map of row changes for this state
     * @return Error message to show the user if the change is not allowed
     */
    @Nullable String getStateChangeError(Container container, DataState state, Map<String, Object> rowUpdates);

    void updateState(Container container, FORM form, User user);
    String getHandlerType();
    static <T> boolean nullSafeEqual(T first, T second)
    {
        if (first == null && second == null)
            return true;
        if (first == null)
            return false;
        return first.equals(second);
    }
}
