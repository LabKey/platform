/*
 * Copyright (c) 2018-2019 LabKey Corporation
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
package org.labkey.api.view.template;

import org.jetbrains.annotations.NotNull;

import java.util.List;

public interface Warnings
{
    static Warnings of(@NotNull List<String> collection)
    {
        return new Warnings() {
            @Override
            public void add(String warning)
            {
                collection.add(warning);
            }

            @Override
            public boolean isEmpty()
            {
                return collection.isEmpty();
            }

            @Override
            public List<String> getMessages()
            {
                return collection;
            }
        };
    }

    void add(String warning);
    boolean isEmpty();
    List<String> getMessages();
}
