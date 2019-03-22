/*
 * Copyright (c) 2017 LabKey Corporation
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
package org.labkey.api.security;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.labkey.api.attachments.AttachmentType;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.views.DataViewProvider;
import org.labkey.api.settings.AppProps;
import org.labkey.api.thumbnail.Thumbnail;
import org.labkey.api.thumbnail.ThumbnailProvider;
import org.labkey.api.thumbnail.ThumbnailService;
import org.labkey.api.view.ViewContext;

public class AvatarThumbnailProvider implements ThumbnailProvider
{
    static final String THUMBNAIL_PATH = AppProps.getInstance().getContextPath() + "/_images/defaultavatar.png";

    private final User _user;

    public AvatarThumbnailProvider(User user)
    {
        _user = user;
    }

    @Override
    public String getEntityId()
    {
        return _user.getEntityId();
    }

    @Override
    public String getContainerId()
    {
        // Note: a container is required for the AttachmentService
        return ContainerManager.getRoot().getId();
    }

    @Override
    public String getThumbnailCacheKey()
    {
        return "User: " + getEntityId();
    }

    @Override
    public @NotNull AttachmentType getAttachmentType()
    {
        return AvatarType.get();
    }

    @Nullable
    @Override
    public Thumbnail generateThumbnail(@Nullable ViewContext context)
    {
        return null;
    }

    @Override
    public String getStaticThumbnailPath()
    {
        return THUMBNAIL_PATH;
    }

    @Override
    public boolean supportsDynamicThumbnail()
    {
        return false;
    }

    @Override
    public void afterThumbnailDelete(ThumbnailService.ImageType imageType)
    {}

    @Override
    public void afterThumbnailSave(ThumbnailService.ImageType imageType, DataViewProvider.EditInfo.ThumbnailType thumbnailType)
    {}
}
