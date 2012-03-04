/*
 * Copyright (c) 2008-2012 LabKey Corporation
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
package org.labkey.api.gwt.client.ui;

/**
 * Interface for widgets that need a Save Finish Cancel button bar
 *
 * User: jgarms
 * Date: Jun 2, 2008
 * Time: 11:01:14 AM
 */
public interface Saveable<ObjectType>
{
    /**
     * @return the URL that should be considered the current URL. This is useful for apps that both create
     * new objects and edit existing objects. The new URL typically doesn't have a RowId or other identifier,
     * but the edit does. Thus, to return to the "same" page after saving a new object, you need to add
     * the RowId or otherwise change it.
     */
    String getCurrentURL();

    public interface SaveListener<ObjectType>
    {
        void saveSuccessful(ObjectType result, String designerUrl);
    }

    /**
     * Save button clicked
     */
    void save();

    /**
     * Save button clicked
     */
    void save(SaveListener<ObjectType> listener);

    /**
     * Cancel button clicked
     */
    void cancel();

    /**
     * Finish button clicked
     */
    void finish();

    boolean isDirty();
}
