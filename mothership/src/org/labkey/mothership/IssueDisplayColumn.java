/*
 * Copyright (c) 2025-2026 LabKey Corporation
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
package org.labkey.mothership;

import org.labkey.api.data.ColumnInfo;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.data.DataColumn;
import org.labkey.api.data.RenderContext;
import org.labkey.api.issues.IssuesUrls;
import org.labkey.api.query.FieldKey;
import org.labkey.api.util.PageFlowUtil;
import org.labkey.api.view.ActionURL;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import java.util.Set;

public class IssueDisplayColumn extends DataColumn
{
    public IssueDisplayColumn(ColumnInfo col)
    {
        super(col);
    }

    @Override
    public String renderURL(RenderContext ctx)
    {
        Integer gitHubIssue = ctx.get(getGitHubIssueFieldKey(), Integer.class);
        if (gitHubIssue != null)
        {
            String repo = MothershipManager.get().getGitHubRepo();
            return String.format("https://github.com/LabKey/%s/issues/%d", URLEncoder.encode(repo, StandardCharsets.UTF_8), gitHubIssue);
        }
        Integer labkeyIssue = ctx.get(getLabKeyIssueFieldKey(), Integer.class);
        if (labkeyIssue != null)
        {
            String path = MothershipManager.get().getIssuesContainer();
            if (path != null)
            {
                ActionURL url = PageFlowUtil.urlProvider(IssuesUrls.class).getDetailsURL(ContainerManager.getForPath(path));
                url.addParameter("issueId", labkeyIssue);
                return url.getLocalURIString();
            }
        }

        return super.renderURL(ctx);
    }

    @Override
    public void addQueryFieldKeys(Set<FieldKey> keys)
    {
        super.addQueryFieldKeys(keys);
        keys.add(getGitHubIssueFieldKey());
        keys.add(getLabKeyIssueFieldKey());
    }

    private FieldKey getGitHubIssueFieldKey()
    {
        return FieldKey.fromString(getBoundColumn().getFieldKey().getParent(), "GitHubIssue");
    }

    private FieldKey getLabKeyIssueFieldKey()
    {
        return FieldKey.fromString(getBoundColumn().getFieldKey().getParent(), "LabKeyIssue");
    }
}
