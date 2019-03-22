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
import org.labkey.api.exp.property.DomainProperty;
import org.labkey.api.reports.model.ViewCategory;
import org.labkey.api.security.User;
import org.labkey.api.util.Pair;
import org.labkey.api.util.URLHelper;
import org.labkey.api.view.ActionURL;

import java.util.Date;
import java.util.List;

/**
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
    String getDefaultIconCls();         // the default display icon
    @Nullable
    URLHelper getDefaultThumbnailUrl(); // the default display thumbnail
    @Nullable
    URLHelper getIconUrl();             // the server relative path to the display icon
    @Nullable
    String getIconCls();                // css code for font icon
    @Nullable
    ViewCategory getCategory();         // an optional view category

    @Nullable String getSchemaName();
    @Nullable String getQueryName();

    boolean isVisible();                // specifies whether this view is hidden
    boolean showInDashboard();          // an optional visibility level
    boolean isShared();
    boolean isReadOnly();
    @Nullable
    String getAccess();                 // string description whether this view is public/private (or other)
    @Nullable ActionURL getAccessUrl(); // view access settings

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
    Date getContentModified();

    @Nullable
    ActionURL getRunUrl();              // the action to render or display this view
    @Nullable
    String    getRunTarget();           // Anchor target (e.g., "_blank") use when rendering run report href.

    @Nullable
    URLHelper getThumbnailUrl();        // the url to display a thumbnail image
    @Nullable
    ActionURL getDetailsUrl();          // the url to display extra details about a view
    int getDisplayOrder();              // display ordering in UI of report relative to other reports in its category or subcategory

    /**
     * Returns the list of additional properties that may be associated with this object, each
     * property is expressed as a key/value pair of a DomainProperty instance representing the property or tag
     * type and an Object representing the value.
     */
    List<Pair<DomainProperty, Object>> getTags();

    boolean isAllowCustomThumbnail();
}
