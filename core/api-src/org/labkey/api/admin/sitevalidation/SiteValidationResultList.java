/*
 * Copyright (c) 2015 LabKey Corporation
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
package org.labkey.api.admin.sitevalidation;

import org.jetbrains.annotations.Nullable;
import org.labkey.api.admin.sitevalidation.SiteValidationResult.Level;
import org.labkey.api.util.HtmlString;
import org.labkey.api.view.ActionURL;

import java.util.ArrayList;
import java.util.List;

/**
 * Top level object to hold a list of SiteValidationResults, provide helper add/create methods, and filter results on level
 */
public class SiteValidationResultList
{
    private final List<SiteValidationResult> results = new ArrayList<>();

    // TODO: Add more HtmlString taking addResult() methods.

    public SiteValidationResult addResult(Level level, String message)
    {
        return addResult(level, message, null);
    }

    public SiteValidationResult addResult(Level level, String message, ActionURL link)
    {
        return addResult(level, HtmlString.of(message), link);
    }

    public SiteValidationResult addResult(Level level, HtmlString message, ActionURL link)
    {
        SiteValidationResult result = level.create(message, link);
        results.add(result);
        return result;
    }

    public SiteValidationResult addInfo(String message)
    {
        return addInfo(message, null);
    }

    public SiteValidationResult addInfo(String message, ActionURL link)
    {
        return addResult(Level.INFO, message, link);
    }

    public SiteValidationResult addWarn(String message)
    {
        return addWarn(message, null);
    }

    public SiteValidationResult addWarn(String message, ActionURL link)
    {
        return addWarn(HtmlString.of(message), link);
    }

    public SiteValidationResult addWarn(HtmlString message, ActionURL link)
    {
        return addResult(Level.WARN, message, link);
    }

    public SiteValidationResult addError(String message)
    {
        return addError(message, null);
    }

    public SiteValidationResult addError(String message, ActionURL link)
    {
        return addResult(Level.ERROR, message, link);
    }

    public List<SiteValidationResult> getResults()
    {
        return getResults(null);
    }

    public List<SiteValidationResult> getResults(@Nullable Level level)
    {
        if (null == level)
            return results;

        List<SiteValidationResult> filteredResults = new ArrayList<>();
        for (SiteValidationResult result : results)
        {
            if (level.equals(result.getLevel()))
                filteredResults.add(result);
        }
        return filteredResults;
    }

    public void addAll(SiteValidationResultList resultsToAdd)
    {
        this.results.addAll(resultsToAdd.getResults());
    }

    public boolean hasErrors()
    {
        return !results.isEmpty();
    }

    @Nullable
    public SiteValidationResultList nullIfEmpty()
    {
        return getResults().isEmpty() ? null : this;
    }
}
