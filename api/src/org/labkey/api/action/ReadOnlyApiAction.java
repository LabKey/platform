/*
 * Copyright (c) 2018-2026 LabKey Corporation
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
package org.labkey.api.action;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.TransactionFilter;
import org.springframework.web.servlet.ModelAndView;

/**
 * NOTE: Even if your action is read-only, consider extending MutatingApiAction anyway if it does not need to support GET,
 * and most API actions _do_not_ need to support GET, because they are called from code.
 *
 * NOTE: Despite the name this does not enforce ReadOnly-ness, it is only a marker of intent.
 *
 * User: Dave
 * Date: Feb 8, 2008
 */
public abstract class ReadOnlyApiAction<FORM> extends BaseApiAction<FORM>
{

    @Override
    public ModelAndView handleRequest(@NotNull HttpServletRequest request, @NotNull HttpServletResponse response) throws Exception
    {
        request.setAttribute(TransactionFilter.READ_ONLY_ATTRIBUTE_NAME, true);
        return super.handleRequest(request, response);
    }

    @Override
    protected ModelAndView handleGet() throws Exception
    {
        return handlePost();
    }
}

