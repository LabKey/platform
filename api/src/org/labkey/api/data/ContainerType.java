package org.labkey.api.data;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.admin.ImportContext;

import java.util.Set;

public interface ContainerType
{
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
     * @return indication of whether this is a container tab or not.
     */
    boolean isContainerTab();

    /**
     * @return indication of whether this container can be converted to a tab or not (if, for example, a folder type is updated)
     */
    Boolean isConvertibleToTab();

    /**
     * This can be used in conjunction with getChildTitle() to show a parent / child path in the title header.
     *
     * @return The container whose title to be used as part of the title of a PageTemplate (next to the folder icon).
     */
    @NotNull
    Container getTitleFolder();

    /**
     * @return String displayed in AppBar (???)
     */
    String getAppBarTitle();

    /**
     * @return the title to display when this container is a child and it is displayed along with its parent's title
     */
    String getChildTitle();

    /**
     * @return the title to display when importing (assay) data into the container.
     */
    String getImportTitle();

    /**
     * @return String to use in messaging to a user the type of this container
     */
    String getContainerNoun();

    /**
     * @param titleCase indicates if the noun should be cased for display in a title
     * @return String to use in messaging to a user the type of this container.
     */
    String getContainerNoun(boolean titleCase);

    /**
     * @param container the container from which updates will be made
     * @return indication of whether the current container can be deleted from the given container
     */
    Boolean canDeleteFromContainer(Container container);

    /**
     * @param container the container from which updates will be made
     * @return indication of whether the current container can be deleted from the given container
     */
    Boolean canUpdateFromContainer(Container container);

    /**
     * @return indication of whether this container should show up in folder management
     */
    Boolean canAdminFolder();

    /**
     * @return indication of whether a user needs to have admin permissions on this container to delete the container
     */
    Boolean requiresAdminToDelete();

    /**
     *
     * @return Boolean indicating if this container is included by default in SQL filters and thus does not need to be included when collecting child containers
     */
    Boolean isDuplicatedInContainerFilter();

    /**
     * This can be used when data relevant to this container is stored in this container and possibly also its parents
     * @return indication of whether this container's parent (also) has data relevant to this container
     */
    Boolean parentDataIsRelevant(Container.DataType dataType);

    /**
     * @param dataType the type of data we are interested in
     * @return an indication of whether this container holds the type of data indicated
     */
    Boolean isContainerFor(Container.DataType dataType);

    /**
     * @param dataType the type of data we are interested in
     * @return the container in this container's hierarchy in which the indicated data relevant to this container is contained
     */
    Container getContainerFor(Container.DataType dataType);

    /**
     * @return the set of containers within this container's hierarchy where assay protocols relevant to this container live
     * @param dataType
     */
    Set<Container> getContainersFor(Container.DataType dataType);
}

