package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.ImportContext;

import java.util.Set;

public interface ContainerType
{
    /**
     * An enumeration of the different types of data relevant to a container that may be stored in a different container
     * in its hierarchy, usually depending on the type of container.
     */
    enum DataType
    {
        assayLocationFilter,
        assayProtocols,
        assays,
        customQueryViews,
        dataspace,
        domainDefinitions,
        fileAdmin,
        fileRoot,
        folderManagement,
        inventory,
        list,
        navVisibility,
        permissions,
        pipelineRoot,
        properties,
        protocol,
        sharedDataTable,
        tabs,
        userSchema,
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
    Boolean canHaveChildren();

    /**
     * @param context the import/export context
     * @return Boolean indicating if the container  is to be included for import and/or export given the context
     */
    Boolean includeForImportExport(ImportContext context);

    /**
     * @return Boolean indicating if this container should be removed as part of a portal removal or not
     */
    Boolean shouldRemoveFromPortal();

    /**
     * @param includeTabs indicates whether tabs are to be included or not
     * @return Boolean indicating if this container's properties should be included when the container is a child
     */
    Boolean includePropertiesAsChild(boolean includeTabs);

    /**
     * @return indication of whether this container should show up in the folder navigation menu or not
     */
    Boolean isInFolderNav();


    /**
     * @return indication of whether this container can be converted to a tab or not (if, for example, a folder type is updated)
     */
    Boolean isConvertibleToTab();

    /**
     *
     * @param currentContainer The container that is to be deleted
     * @param container the container from which updates will be made
     * @return indication of whether the current container can be deleted from the given container
     */
    Boolean canDeleteFromContainer(Container currentContainer, Container container);

    /**
     *
     * @param currentContainer the container that is to be updated
     * @param container the container from which updates will be made
     * @return indication of whether the current container can be deleted from the given container
     */
    Boolean canUpdateFromContainer(Container currentContainer, Container container);

    /**
     * @return indication of whether this container should show up in folder management
     */
    Boolean canAdminFolder();

    /**
     * @return indication of whether a user needs to have admin permissions on this container to delete the container
     */
    Boolean requiresAdminToDelete();

    /**
     * @return indication of whether a user needs admin permissions to be able create a container of this type
     */
    Boolean requiresAdminToCreate();

    /**
     *
     * @return Boolean indicating if this container is included by default in SQL filters and thus does not need to be included when collecting child containers
     */
    Boolean isDuplicatedInContainerFilter();

    /**
     * This can be used when data relevant to this container is stored in this container and possibly also its parents
     * @return indication of whether this container's parent (also) has data relevant to this container
     */
    Boolean parentDataIsRelevant(DataType dataType);

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
     * You should not call this method if you don't want to know the answer.
     *
     * @param dataType the type of data we are interested in
     * @return an indication of whether this container holds the type of data indicated
     */
    Boolean isContainerFor(DataType dataType);

    /**
     * You should not call this method if you don't want to know the answer.
     *
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

