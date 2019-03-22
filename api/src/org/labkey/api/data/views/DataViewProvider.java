/*
 * Copyright (c) 2012-2017 LabKey Corporation
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
package org.labkey.api.data.views;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.query.ValidationException;
import org.labkey.api.security.User;
import org.labkey.api.view.ViewContext;
import org.labkey.api.writer.ContainerUser;

import java.util.List;
import java.util.Map;

/**
 * User: klum
 * Date: Apr 2, 2012
 */
public interface DataViewProvider
{
    public interface Type
    {
        String getName();
        String getDescription();
        boolean isShowByDefault();
    }

    /**
     * Returns the provider type
     */
    Type getType();

    /**
     * Returns the list of viewInfos for this provider
     */
    List<DataViewInfo> getViews(ViewContext context) throws Exception;

    /**
     * Returns the interface used to edit/update properties of data view objects that this provider returns.
     *
     * @return an EditInfo instance, else null to indicate updates are not supported.
     */
    @Nullable
    EditInfo getEditInfo();

    boolean isVisible(Container container, User user);      // specifies whether this data type is visible

    /**
     * Perform any first time initialization of the provider before a request to return views, this
     * is generally called once per provider to perform any first time initialization.
     *
     * @param context
     */
    void initialize(ContainerUser context) throws Exception;

    public interface EditInfo
    {
        // the list of actions that are allowed
        enum Actions
        {
            update,
            delete
        }

        // a list of standard properties
        enum Property
        {
            name,
            viewName,
            description,
            visible,
            shared,
            category,

            author,
            refreshDate,
            status,
            
            customThumbnail,
            customThumbnailFileName,
            deleteCustomThumbnail,

            customIcon,
            customIconFileName,
            deleteCustomIcon
        }

        // a list of thumbnail types
        enum ThumbnailType
        {
            AUTO,   // auto-generated
            CUSTOM, // custom thumbnail provided by Data Views edit properties page
            NONE 
        }

        /**
         * Returns the array of properties that are editable for this view type.
         */
        String[] getEditableProperties(Container container, User user);

        /**
         * Validate the map of properties before update.
         *
         * @param id the unique identifier for the data object being updated.
         * @param props the map of properties that are changing
         *
         * @throws org.labkey.api.query.ValidationException
         */
        void validateProperties(Container container, User user, String id, Map<String, Object> props) throws ValidationException;

        /**
         * Update the data object with the map of new property values.
         *
         * @param id the unique identifier for the data object being updated.
         * @param props the map of properties to update
         *
         * @throws Exception
         */
        void updateProperties(ViewContext context, String id, Map<String, Object> props) throws Exception;

        /**
         * Return the array of actions that this edit provider supports
         * @param container
         * @param user
         * @return
         */
        Actions[] getAllowableActions(Container container, User user);

        /**
         * Deletes the view identified by the id
         * @param id the unique identifier for the data object being updated.
         * @throws ValidationException
         */
        void deleteView(Container container, User user, String id) throws ValidationException;
    }
}
