/*
 * Copyright (c) 2008-2017 LabKey Corporation
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

import org.springframework.web.servlet.ModelAndView;

/**
 * Thrown to indicate that there was a problem performing an export, when the intent was to streaming the results back
 * to the client. Attempts to render a view with the error info back to the client, assuming a partial response
 * hasn't yet been committed.
 * User: jeckels
 * Date: Nov 11, 2008
 */
public class ExportException extends Exception
{
    private ModelAndView _errorView;

    public ExportException(ModelAndView errorView)
    {
        _errorView = errorView;
    }

    public ModelAndView getErrorView()
    {
        return _errorView;
    }
}