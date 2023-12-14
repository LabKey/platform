package org.labkey.api.view;

import org.jetbrains.annotations.Nullable;

import jakarta.servlet.http.HttpServletRequest;

public interface HasHttpRequest
{
    @Nullable
    HttpServletRequest getRequest();
}
