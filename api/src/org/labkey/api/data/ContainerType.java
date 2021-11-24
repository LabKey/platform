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
package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.ImportContext;
import org.labkey.api.security.permissions.Permission;

import java.io.Serializable;
import java.util.Set;

public interface ContainerType extends Serializable
{
    /**
     * An enumeration of the different types of data relevant to a container that may be stored in a different container
     * in its hierarchy, usually depending on the type of container.
     */
    enum DataType
    {
        assayData,
        customQueryViews,
        dataspace,
        domainDefinitions,
        fileAdmin,
        fileRoot,
        folderManagement,
        navVisibility,
        permissions,
        properties,
        protocol,
        search,
        tabParent, // this means the container can contain tabs
        sharedSchemaOwner  // this is used in several cases where certain kinds of UserSchema tables make records visible in both a parent and specific children
    }

    enum TitleContext
    {
        appBar,
        childInNav,
        importTarget,
        parentInNav // Used in conjunction with childInNav to show a parent / child path in the title header.
    }

    /**
     * @return the name of this type
     */
    String getName();

    /**
     * @return indication of whether this type can have child containers or not
     */
    boolean canHaveChildren();

    /**
     * @param context the import/export context
     * @return Boolean indicating if the container  is to be included for import and/or export given the context
     */
    boolean includeForImportExport(ImportContext context);

    /**
     * @return Boolean indicating if this container should be removed as part of a portal removal or not
     */
    boolean shouldRemoveFromPortal();

    /**
     * @param includeTabs indicates whether tabs are to be included or not
     * @return Boolean indicating if this container's properties should be included when the container is a child
     */
    boolean includePropertiesAsChild(boolean includeTabs);

    /**
     * @return indication of whether this container should show up in the folder navigation menu or not
     */
    boolean isInFolderNav();

    /**
     * @return Indication if containers of this type should be included in API responses.
     */
    boolean includeInAPIResponse();

    /**
     * @return indication of whether this container can be converted to a tab or not (if, for example, a folder type is updated)
     */
    boolean isConvertibleToTab();

    /**
     * In certain situations, it is permissible to insert/update/delete rows into both the parent container and another container (such as a child workbook) in a single batch,
     * if the rows specify that child workbook ID.  See issues 15301 and 32961
     * @param primaryContainer the primary container, typically where the request originated (i.e. an API request or UserSchema)
     * @param targetContainer the container which the row is attempting to act upon (insert/update/delete)
     * @return indication of whether the current container can be updated or deleted from the given container
     */
    boolean allowRowMutationFromContainer(Container primaryContainer, Container targetContainer);

    /**
     * @return The permission class needed to delete this container type
     */
    Class<? extends Permission> getPermissionNeededToDelete();

    /**
     * @return The permission class needed to create this container type
     */
    public Class<? extends Permission> getPermissionNeededToCreate();

    /**
     *
     * @return Boolean indicating if this container is included by default in SQL filters and thus does not need to be included when collecting child containers
     */
    boolean isDuplicatedInContainerFilter();

    /**
     * @param currentContainer the container whose noun is to be returned
     * @return String to use in messaging to a user the type of this container.
     */
    String getContainerNoun(Container currentContainer);

    /**
     * @return the title to display for the given container in the indicated context
     */
    String getTitleFor(TitleContext context, Container currentContainer);

    /**
     * @param dataType the type of data we are interested in
     * @param currentContainer the container the query is made relative to
     * @return the container in the given container's hierarchy in which the indicated data relevant to this container is contained
     */
    @Nullable
    Container getContainerFor(DataType dataType, Container currentContainer);

    /**
     * @return the set of containers within a container's hierarchy where assay protocols relevant to this container live
     * @param dataType the type of data we are interested in
     * @param currentContainer the container the query is made relative to
     */
    @NotNull
    Set<Container> getContainersFor(DataType dataType, Container currentContainer);
}

