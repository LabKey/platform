/*
 * Copyright (c) 2008-2019 LabKey Corporation
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

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.util.SafeToRenderEnum;
import org.labkey.experiment.xar.AutoFileLSIDReplacer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static org.labkey.experiment.XarExporter.MATERIAL_PREFIX_PLACEHOLDER_SUFFIX;

/**
 * Various options for representing LSIDs when exporting XAR files.
 * User: jeckels
 * Date: Nov 21, 2005
 */
public enum LSIDRelativizer implements SafeToRenderEnum
{
    /** Keeps the original LSID from the source server */
    ABSOLUTE("Absolute")
    {
        @Override
        protected String relativize(Lsid lsid, RelativizedLSIDs lsids)
        {
            return lsid.toString();
        }
    },
    FOLDER_RELATIVE("Folder relative")
    {
        @Override
        protected String relativize(ExpObject o, RelativizedLSIDs lsids)
        {
            if (o instanceof ExpData data)
            {
                // Most DataClass data don't have a dataFileUrl, but some do -- like NucSequence imported from a genbank file
                if (data.getDataFileUrl() == null || data.getDataClass() != null)
                {
                    // If we don't have a URL for this data object, we can't use AutoFileLSID. Instead,
                    // try the next best option
                    return PARTIAL_FOLDER_RELATIVE.relativize(o, lsids);
                }
            }
            return super.relativize(o, lsids);
        }

        @Override
        protected String relativize(Lsid lsid, RelativizedLSIDs lsids)
        {
            String prefix = lsid.getNamespacePrefix();
            String suffix = lsid.getNamespaceSuffix();

            String sharedFolderSuffix = "Folder-" + ContainerManager.getSharedContainer().getRowId();
            String containerSubstitution = sharedFolderSuffix.equals(suffix) ? XarContext.SHARED_CONTAINER_ID_SUBSTITUTION : XarContext.CONTAINER_ID_SUBSTITUTION;

            if ("ExperimentRun".equals(prefix))
            {
                return lsids.uniquifyRelativizedLSID("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":ExperimentRun.Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ".${XarFileId}", lsid.getObjectId(), lsid.getVersion());
            }
            else if ("ProtocolApplication".equals(prefix))
            {
                return lsids.uniquifyRelativizedLSID("${RunLSIDBase}", lsid.getObjectId(), lsid.getVersion());
            }
            else if ("Recipe".equals(prefix))
            {
                String recipeName = suffix.substring(0, suffix.indexOf(":"));
                return lsids.uniquifyRelativizedLSID("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":Recipe." + recipeName + ":Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION, lsid.getObjectId(), lsid.getVersion());
            }
            else if (MATERIAL_PREFIX_PLACEHOLDER_SUFFIX.equals(lsid.getObjectId()))
            {
                /*
                 * old prefix: "xxx:Sample:123.sampleTypeName"
                 * new prefix: "xxx:Sample:Folder-123.345" (Sample:Folder-ContainerRowId.DBSeq)
                 * relative lsid: "xxx:Sample:Folder-yyy.xarJobId.345" (need to add xarJobId because DBSeq might not be unique in target folder)
                 */
                String id = "";
                int ind = suffix.indexOf(".");
                if (ind > 0)
                    id = suffix.substring(ind);
                return lsids.uniquifyRelativizedLSID("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":" + prefix + ".Folder-" + containerSubstitution+ ".${XarJobId}" + id, lsid.getObjectId(), lsid.getVersion());
            }
            else if ("Sample".equals(prefix) || "Material".equals(prefix))
            {
                String xarJobId = ".${XarJobId}"; // XarJobId is more concise than XarFileId
                return lsids.uniquifyRelativizedLSID("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":" + prefix + ".Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + xarJobId + lsids.getNextSampleId(), lsid.getObjectId(), lsid.getVersion());
            }
            else if ("Data".equals(prefix))  {
                // UNDONE: Now that "Data" prefix is used for DataClass, the AutoFileLSID is not a good default.
                // UNDONE: Can we be more restrictive about which LSIDs this is applied to?  Maybe only if the objectId part of the LSID includes a "/" (%2F) or something?
                // UNDONE: Maybe there is a better way to detect when we should use ${AutoFileLSID}?
                return AutoFileLSIDReplacer.AUTO_FILE_LSID_SUBSTITUTION;
            }
            else if (suffix != null && SUFFIX_PATTERN.matcher(suffix).matches())
            {
                String xarFileId = "";
                if ("SampleSet".equals(prefix) || "DataClass".equals(prefix))
                {
                    xarFileId = ".${XarJobId}"; // DBSeq lsid might collide in target folder, add XarJobId to guarantee uniqueness
                }

                return lsids.uniquifyRelativizedLSID("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":" + prefix + ".Folder-" + containerSubstitution + xarFileId, lsid.getObjectId(), lsid.getVersion());
            }

            return lsid.toString();
        }
    },
    PARTIAL_FOLDER_RELATIVE("Partial folder relative")
    {
        @Override
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
                return lsids.uniquifyRelativizedLSID("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":ExperimentRun.Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ".${XarFileId}", lsid.getObjectId(), lsid.getVersion());
            }
            else if ("ProtocolApplication".equals(prefix))
            {
                return lsids.uniquifyRelativizedLSID("${RunLSIDBase}", lsid.getObjectId(), lsid.getVersion());
            }
            else if ("Recipe".equals(prefix))
            {
                String recipeName = suffix.substring(0, suffix.indexOf(":"));
                return lsids.uniquifyRelativizedLSID("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":Recipe." + recipeName + ":Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION, lsid.getObjectId(), lsid.getVersion());
            }
            else if ("Sample".equals(prefix))
            {
                return lsids.uniquifyRelativizedLSID("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":Sample.Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ".${XarJobId}-" + lsids.getNextSampleId(), lsid.getObjectId(), lsid.getVersion());
            }
            else if ("Material".equals(prefix))
            {
                if (lsid.getNamespaceSuffix().startsWith("Folder-"))
                {
                    return stripFolderSuffix(lsid, lsids);
                }
                else
                {
                    return lsids.uniquifyRelativizedLSID("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":Material.Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ".${XarJobId}-" + lsids.getNextMaterialId(), lsid.getObjectId(), lsid.getVersion());
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
                    return lsids.uniquifyRelativizedLSID("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":Data.Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ".${XarJobId}-" + lsids.getNextDataId(), lsid.getObjectId(), lsid.getVersion());
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
                    return lsids.uniquifyRelativizedLSID("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":" + prefix + ".Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + "", lsid.getObjectId(), lsid.getVersion());
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
        return lsids.uniquifyRelativizedLSID("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":" + lsid.getNamespacePrefix() + ".Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + "" + suffix.substring(index), lsid.getObjectId(), lsid.getVersion());

    }

    public String getDescription()
    {
        return _description;
    }

    public static class RelativizedLSIDs
    {
        private final LSIDRelativizer _relativizer;

        private final Map<String, String> _originalToRelative = new HashMap<>();

        // Maintain a separate set of values so we can quickly determine if one is already in use instead of having
        // to traverse the whole map. See issue 39260
        private final Set<String> _relativized = new HashSet<>();

        // Also keep track of the next suffix to append for a given LSID prefix so we don't have to run through the full
        // sequence of values we already scanned the last time. See issue 39260
        private final Map<String, Integer> _nextExportVersion = new HashMap<>();

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
            return _originalToRelative.get(originalLSID);
        }

        private void putLSID(String originalLSID, String relativizedLSID)
        {
            assert !_originalToRelative.containsKey(originalLSID);
            // Maintain collections in both directions, as we need efficient lookups for both types of LSIDs. See issue 39260
            _originalToRelative.put(originalLSID, relativizedLSID);
            _relativized.add(relativizedLSID);
        }

        private String uniquifyRelativizedLSID(String prefix, String objectId, String version)
        {
            version = StringUtils.trimToNull(version);

            StringBuilder sb = new StringBuilder(prefix);
            sb.append(":");
            sb.append(Lsid.encodePart(objectId).replace("%23", "#"));
            if (version != null)
            {
                sb.append(":");
                sb.append(Lsid.encodePart(version));
            }
            String lsidWithoutUniquifier = sb.toString();

            int exportVersion = _nextExportVersion.getOrDefault(lsidWithoutUniquifier, 0);
            String candidate;
            do
            {
                candidate = exportVersion == 0 ? lsidWithoutUniquifier :
                        lsidWithoutUniquifier + (version == null ? ":" : "-") + "Export" + exportVersion;
                exportVersion++;
            }
            while (_relativized.contains(candidate));

            _nextExportVersion.put(lsidWithoutUniquifier, exportVersion);

            return candidate;
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
        public void testUniquificationPerf()
        {
            long startTime = System.currentTimeMillis();
            RelativizedLSIDs set = new RelativizedLSIDs(FOLDER_RELATIVE);
            for (int i = 0; i < 1_000_000; i++)
            {
                set.relativize("urn:lsid:localhost:Protocol.Folder-" + i + ":MS2.PreSearch");
            }
            long elapsedTime = System.currentTimeMillis() - startTime;
            // This takes ~6 seconds on a dev machine as of test creation in December 2019
            assertTrue("One minute timeout exceeded for generating LSIDs, time elapsed in milliseconds : " + elapsedTime, elapsedTime < 60_000);
        }

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

        @Test
        public void testAutoFileLSID()
        {
            RelativizedLSIDs set = new RelativizedLSIDs(FOLDER_RELATIVE);
            assertEquals(AutoFileLSIDReplacer.AUTO_FILE_LSID_SUBSTITUTION, set.relativize("urn:lsid:labkey.com:Data.Folder-18698:File1.txt"));
            assertEquals(AutoFileLSIDReplacer.AUTO_FILE_LSID_SUBSTITUTION, set.relativize("urn:lsid:labkey.com:Data.Folder-18698:File2.txt"));
        }
    }
}
