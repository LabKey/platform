package org.labkey.api.data.views;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.data.Container;
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.api.view.ActionURL;

import java.util.Date;
import java.util.List;

/**
 * Created by IntelliJ IDEA.
 * User: klum
 * Date: Apr 2, 2012
 */

/**
 * Represents meta information about a data view object
 */
public interface DataViewInfo
{
    DataViewProvider.Type getDataType();
    String getId();                     // specifies the unique identifier for this object
    String getName();
    Container getContainer();

    String getType();                   // descriptive string for this view type
    @Nullable
    String getDescription();            // an optional description
    @Nullable
    String getIcon();                   // the server relative path to the display icon
    @Nullable
    ViewCategory getCategory();         // an optional view category

    boolean isVisible();                // specifies whether this view is hidden
    boolean isShared();
    @Nullable
    String getAccess();                 // string description whether this view is public/private (or other)

    @Nullable
    User getCreatedBy();
    @Nullable
    User getModifiedBy();
    @Nullable
    User getAuthor();

    @Nullable
    Date getCreated();
    @Nullable
    Date getModified();

    @Nullable
    ActionURL getRunUrl();              // the action to render or display this view
    @Nullable
    ActionURL getThumbnailUrl();        // the url to display a thumbnail image
    @Nullable
    ActionURL getDetailsUrl();          // the url to display extra details about a view

    /**
     * Returns the list of additional properties that may be associated with this object, each
     * property is expressed as a key/value pair of a DomainProperty instance representing the property or tag
     * type and an Object representing the value.
     */
    List<Pair<DomainProperty, Object>> getTags();
}
