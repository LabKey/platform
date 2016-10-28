/*
 * Copyright (c) 2007-2016 LabKey Corporation
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

package org.labkey.api.study.actions;

import org.jetbrains.annotations.NotNull;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.api.ExpProtocol;
import org.labkey.api.exp.api.ExperimentService;
import org.labkey.api.security.permissions.ReadPermission;
import org.labkey.api.study.assay.AssayProvider;
import org.labkey.api.study.assay.AssayService;
import org.labkey.api.view.*;

/**
 * User: brittp
* Date: Jul 26, 2007
* Time: 7:02:45 PM
*/
public class ProtocolIdForm extends ViewForm
{
    private ExpProtocol _protocol;
    private AssayProvider _provider;

    /** If no rowId is specified, we attempt to find the assay definition based on name */
    private Integer _rowId;
    private String _assayName;
    private String _uploadAttemptID;
    private String _providerName;

    public String getUploadAttemptID()
    {
        return _uploadAttemptID;
    }

    public void setUploadAttemptID(String uploadAttemptId)
    {
        _uploadAttemptID = uploadAttemptId;
    }

    public String getProviderName()
    {
        return _providerName;
    }

    public void setProviderName(String providerName)
    {
        _providerName = providerName;
    }

    public Integer getRowId()
    {
        return _rowId;
    }

    public void setRowId(Integer rowId)
    {
        _rowId = rowId;
    }

    public String getAssayName()
    {
        return _assayName;
    }

    public void setAssayName(String assayName)
    {
        _assayName = assayName;
    }

    @NotNull
    public ExpProtocol getProtocol(boolean validateContainer)
    {
        if (_protocol == null)
        {
            ExpProtocol protocol = null;
            // Prefer RowId as the identifier
            if (getRowId() != null)
            {
                protocol = ExperimentService.get().getExpProtocol(getRowId().intValue());
            }
            else if (getAssayName() != null)
            {
                // Check for a single design that matches based on name
                for (ExpProtocol possibleMatch : AssayService.get().getAssayProtocols(getContainer()))
                {
                    if (possibleMatch.getName().equalsIgnoreCase(getAssayName()))
                    {
                        if (protocol != null)
                        {
                            throw new NotFoundException("More than one assay definition with the name '" + getAssayName() + "' is in scope");
                        }
                        protocol = possibleMatch;
                    }
                }
            }
            else
            {
                throw new NotFoundException("Assay ID not specified.");
            }

            if (protocol == null || (validateContainer && !protocol.getContainer().equals(getContainer()) &&
                                                          !protocol.getContainer().equals(getContainer().getProject()) &&
                                                          !protocol.getContainer().equals(ContainerManager.getSharedContainer()) &&
                                                          !(getContainer().isWorkbook() && protocol.getContainer().equals(getContainer().getParent()))))
            {
                if (protocol != null && protocol.getContainer().hasPermission(getUser(), ReadPermission.class))
                {
                    ActionURL url = getViewContext().cloneActionURL();
                    url.setContainer(protocol.getContainer());
                    throw new RedirectException(url);
                }
                throw new NotFoundException("Assay " + getRowId() + " does not exist in " + getContainer().getPath());
            }

            // even if we don't validate that the protocol is from the current or project container,
            // we still make sure that the current user can read from the protocol container:
            if (!protocol.getContainer().hasPermission(getViewContext().getUser(), ReadPermission.class))
                throw new UnauthorizedException();

            AssayProvider provider = AssayService.get().getProvider(protocol);
            if (provider == null)
            {
                // Throw a special subclass so callers who are interested in knowing when the design exists but the
                // provider doesn't can catch it.
                throw new ProviderNotFoundException("Could not find AssayProvider for assay design '" + protocol.getName() + "' (id " + protocol.getRowId() + ")", protocol);
            }
            
            _provider = provider;
            _protocol = protocol;
        }

        return _protocol;
    }

    /** @throws NotFoundException if we can't resolve the protocol */
    @NotNull
    public ExpProtocol getProtocol()
    {
        return getProtocol(true);
    }

    /** @throws NotFoundException if we can't resolve the provider */
    @NotNull
    public AssayProvider getProvider()
    {
        if (_provider == null)
        {
            getProtocol();
        }
        return _provider;
    }

    /** Special subclass that indicates the assay design was found but the provider has disappeared */
    public static class ProviderNotFoundException extends NotFoundException
    {
        private final ExpProtocol _protocol;

        public ProviderNotFoundException(String string, @NotNull ExpProtocol protocol)
        {
            super(string);
            _protocol = protocol;
        }

        public @NotNull ExpProtocol getProtocol()
        {
            return _protocol;
        }
    }
}

