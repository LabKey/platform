/*
 * Copyright (c) 2011-2017 LabKey Corporation
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
package org.labkey.api.dataiterator;

/**
 * Adds the ability to rewind a {@link DataIterator} to before its first row of data.
 *
 * (MAB) I go back and forth between adding methods to DataIterator and having extended/marker interfaces.
 *
 * I'm going to go with the idea of having the 'purest' interface possible, and see how it goes.
 *
 * Why have the isScrollable() method on a ScrollableDataIterator interface? This is so that wrapper/pass-through
 * base classes can pass-through the scrollability of their input class if appropriate.
 */
public interface ScrollableDataIterator extends DataIterator
{
    boolean isScrollable();
    void beforeFirst();
}
