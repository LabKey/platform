/*
 * Copyright (c) 2005-2016 LabKey Corporation
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

package org.labkey.experiment;

import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.experiment.xar.AutoFileLSIDReplacer;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Various options for representing LSIDs when exporting XAR files.
 * User: jeckels
 * Date: Nov 21, 2005
 */
public enum LSIDRelativizer
{
    /** Keeps the original LSID from the source server */
    ABSOLUTE("Absolute")
    {
        protected String relativize(Lsid lsid, RelativizedLSIDs lsids)
        {
            return lsid.toString();
        }
    },
    FOLDER_RELATIVE("Folder relative")
    {
        protected String relativize(ExpObject o, RelativizedLSIDs lsids)
        {
            if (o instanceof ExpData)
            {
                ExpData data = (ExpData)o;
                if (data.getDataFileUrl() == null)
                {
                    // If we don't have a URL for this data object, we can't use AutoFileLSID. Instead,
                    // try the next best option
                    return PARTIAL_FOLDER_RELATIVE.relativize(o, lsids);
                }
            }
            return super.relativize(o, lsids);
        }

        protected String relativize(Lsid lsid, RelativizedLSIDs lsids)
        {
            String prefix = lsid.getNamespacePrefix();
            String suffix = lsid.getNamespaceSuffix();

            if ("ExperimentRun".equals(prefix))
            {
                return lsids.uniquifyRelativizedLSID("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":ExperimentRun.Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ".${XarFileId}", lsid.getObjectId(), lsid.getVersion(), null);
            }
            else if ("ProtocolApplication".equals(prefix))
            {
                return lsids.uniquifyRelativizedLSID("${RunLSIDBase}", lsid.getObjectId(), lsid.getVersion(), null);
            }
            else if ("Sample".equals(prefix) || "Material".equals(prefix))
            {
                return lsids.uniquifyRelativizedLSID("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":" + prefix + ".Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ".${XarFileId}-" + lsids.getNextSampleId(), lsid.getObjectId(), lsid.getVersion(), null);
            }
            else if ("Data".equals(prefix))
            {
                return AutoFileLSIDReplacer.AUTO_FILE_LSID_SUBSTITUTION;
            }
            else if (suffix != null && SUFFIX_PATTERN.matcher(suffix).matches())
            {
                return lsids.uniquifyRelativizedLSID("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":" + prefix + ".Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + "", lsid.getObjectId(), lsid.getVersion(), null);
            }

            return lsid.toString();
        }
    },
    PARTIAL_FOLDER_RELATIVE("Partial folder relative")
    {
        public String relativize(Lsid lsid, RelativizedLSIDs lsids)
        {
            String prefix = lsid.getNamespacePrefix();
            String suffix = lsid.getNamespaceSuffix();
            if (prefix == null)
            {
                return lsid.toString();
            }
            if ("ExperimentRun".equals(prefix))
            {
                return lsids.uniquifyRelativizedLSID("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":ExperimentRun.Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ".${XarFileId}", lsid.getObjectId(), lsid.getVersion(), null);
            }
            else if ("ProtocolApplication".equals(prefix))
            {
                return lsids.uniquifyRelativizedLSID("${RunLSIDBase}", lsid.getObjectId(), lsid.getVersion(), null);
            }
            else if ("Sample".equals(prefix))
            {
                return lsids.uniquifyRelativizedLSID("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":Sample.Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ".${XarFileId}-" + lsids.getNextSampleId(), lsid.getObjectId(), lsid.getVersion(), null);
            }
            else if ("Material".equals(prefix))
            {
                if (lsid.getNamespaceSuffix().startsWith("Folder-"))
                {
                    return stripFolderSuffix(lsid, lsids);
                }
                else
                {
                    return lsids.uniquifyRelativizedLSID("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":Material.Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ".${XarFileId}-" + lsids.getNextMaterialId(), lsid.getObjectId(), lsid.getVersion(), null);
                }
            }
            else if ("Data".equals(prefix))
            {
                if (lsid.getNamespaceSuffix().startsWith("Folder-"))
                {
                    return stripFolderSuffix(lsid, lsids);
                }
                else
                {
                    return lsids.uniquifyRelativizedLSID("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":Data.Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ".${XarFileId}-" + lsids.getNextDataId(), lsid.getObjectId(), lsid.getVersion(), null);
                }
            }
            else
            {
                if (suffix != null && suffix.startsWith("Folder-"))
                {
                    return stripFolderSuffix(lsid, lsids);
                }
                else
                {
                    return lsids.uniquifyRelativizedLSID("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":" + prefix + ".Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + "", lsid.getObjectId(), lsid.getVersion(), null);
                }
            }
        }
    };
    private final String _description;

    protected abstract String relativize(Lsid lsid, RelativizedLSIDs lsids);

    private static final Pattern SUFFIX_PATTERN = Pattern.compile("Folder-[0-9]+");

    LSIDRelativizer(String description)
    {
        _description = description;
    }

    private static String stripFolderSuffix(Lsid lsid, RelativizedLSIDs lsids)
    {
        String suffix = lsid.getNamespaceSuffix();
        int index = "Folder-".length();
        while (index < suffix.length() && Character.isDigit(suffix.charAt(index)))
        {
            index++;
        }
        return lsids.uniquifyRelativizedLSID("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":" + lsid.getNamespacePrefix() + ".Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + "" + suffix.substring(index), lsid.getObjectId(), lsid.getVersion(), null);

    }

    public String getDescription()
    {
        return _description;
    }

    public static class RelativizedLSIDs
    {
        private final LSIDRelativizer _relativizer;

        private Map<String, String> _lsids = new HashMap<>();

        private int _nextDataId = 1;
        private int _nextSampleId = 1;
        private int _nextMaterialId = 1;

        public RelativizedLSIDs(LSIDRelativizer relativizer)
        {
            _relativizer = relativizer;
        }

        public String relativize(ExpObject o)
        {
            if (o == null)
            {
                return null;
            }

            String result = getExistingLSID(o.getLSID());
            if (result != null)
            {
                return result;
            }

            result = _relativizer.relativize(o, this);
            putLSID(o.getLSID(), result);
            return result;
        }

        public String relativize(String s)
        {
            if (s == null)
            {
                return null;
            }

            String result = getExistingLSID(s);
            if (result != null)
            {
                return result;
            }

            result = _relativizer.relativize(new Lsid(s), this);
            putLSID(s, result);
            return result;
        }

        private String getExistingLSID(String originalLSID)
        {
            return _lsids.get(originalLSID);
        }

        private void putLSID(String originalLSID, String relativizedLSID)
        {
            assert !_lsids.containsKey(originalLSID);
            _lsids.put(originalLSID, relativizedLSID);
        }

        private String uniquifyRelativizedLSID(String prefix, String objectId, String version, Integer exportVersion)
        {
            String candidate;
            Integer newExportVersion = exportVersion;
            do
            {
                candidate = getNewLSIDCandidate(prefix, objectId, version, newExportVersion);
                newExportVersion = newExportVersion != null ? newExportVersion + 1 : 1;
            } while(_lsids.containsValue(candidate));

            return candidate;
        }

        private String getNewLSIDCandidate(String prefix, String objectId, String version, Integer exportVersion)
        {
            StringBuilder sb = new StringBuilder(prefix);
            sb.append(":");
            sb.append(Lsid.encodePart(objectId).replace("%23", "#"));
            if (version != null && !(version.length() == 0))
            {
                sb.append(":");
                sb.append(Lsid.encodePart(version));

                if (exportVersion != null)
                {
                    sb.append("-Export");
                    sb.append(exportVersion.toString());
                }
            }
            else if (exportVersion != null)
            {
                sb.append(":Export");
                sb.append(exportVersion.toString());
            }

            return sb.toString();
        }

        public int getNextDataId()
        {
            return _nextDataId++;
        }

        public int getNextSampleId()
        {
            return _nextSampleId++;
        }

        public int getNextMaterialId()
        {
            return _nextMaterialId++;
        }
    }

    protected String relativize(ExpObject o, RelativizedLSIDs lsids)
    {
        return relativize(new Lsid(o.getLSID()), lsids);
    }

    public static class TestCase extends Assert
    {
        @Test
        public void testOverlappingLSIDs()
        {
            RelativizedLSIDs set = new RelativizedLSIDs(FOLDER_RELATIVE);
            assertEquals("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":Protocol.Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ":MS2.PreSearch", set.relativize("urn:lsid:localhost:Protocol.Folder-1:MS2.PreSearch"));
            assertEquals("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":Protocol.Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ":MS2.PreSearch:Export1", set.relativize("urn:lsid:localhost:Protocol.Folder-2:MS2.PreSearch"));
            assertEquals("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":Protocol.Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ":MS2.PreSearch:Export2", set.relativize("urn:lsid:localhost:Protocol.Folder-3:MS2.PreSearch"));

            // Make sure they resolve to the same thing as they did the first time
            assertEquals("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":Protocol.Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ":MS2.PreSearch", set.relativize("urn:lsid:localhost:Protocol.Folder-1:MS2.PreSearch"));
            assertEquals("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":Protocol.Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ":MS2.PreSearch:Export1", set.relativize("urn:lsid:localhost:Protocol.Folder-2:MS2.PreSearch"));
            assertEquals("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":Protocol.Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ":MS2.PreSearch:Export2", set.relativize("urn:lsid:localhost:Protocol.Folder-3:MS2.PreSearch"));

            assertEquals("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":Protocol.Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ":MS2.PreSearch:v1", set.relativize("urn:lsid:localhost:Protocol.Folder-1:MS2.PreSearch:v1"));
            assertEquals("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":Protocol.Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ":MS2.PreSearch:v1-Export1", set.relativize("urn:lsid:localhost:Protocol.Folder-2:MS2.PreSearch:v1"));
            assertEquals("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":Protocol.Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ":MS2.PreSearch:v1-Export2", set.relativize("urn:lsid:localhost:Protocol.Folder-3:MS2.PreSearch:v1"));

            // Make sure they resolve to the same thing as they did the first time
            assertEquals("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":Protocol.Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ":MS2.PreSearch:v1", set.relativize("urn:lsid:localhost:Protocol.Folder-1:MS2.PreSearch:v1"));
            assertEquals("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":Protocol.Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ":MS2.PreSearch:v1-Export1", set.relativize("urn:lsid:localhost:Protocol.Folder-2:MS2.PreSearch:v1"));
            assertEquals("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":Protocol.Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ":MS2.PreSearch:v1-Export2", set.relativize("urn:lsid:localhost:Protocol.Folder-3:MS2.PreSearch:v1"));
        }

        @Test
        public void testSpecialCharacters()
        {
            RelativizedLSIDs set = new RelativizedLSIDs(FOLDER_RELATIVE);
            assertEquals("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":AssayDomain-Run.Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ":WithPercent#%25IDs", set.relativize("urn:lsid:labkey.com:AssayDomain-Run.Folder-18698:WithPercent#%25IDs"));
        }
    }
}
