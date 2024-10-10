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

package org.labkey.api.exp.xar;

import org.apache.commons.lang3.StringUtils;
import org.junit.Assert;
import org.junit.Test;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.exp.Lsid;
import org.labkey.api.exp.XarContext;
import org.labkey.api.exp.api.ExpData;
import org.labkey.api.exp.api.ExpMaterial;
import org.labkey.api.exp.api.ExpObject;
import org.labkey.api.exp.api.ExpProtocolApplication;
import org.labkey.api.exp.api.ExpRun;
import org.labkey.api.util.SafeToRenderEnum;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.labkey.api.exp.XarContext.XAR_JOB_ID_NAME_SUB;


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
        protected String relativize(Lsid lsid, RelativizedLSIDs lsids, boolean useXarJobId)
        {
            return lsid.toString();
        }
    },
    FOLDER_RELATIVE("Folder relative")
    {
        @Override
        protected String relativize(ExpObject o, RelativizedLSIDs lsids, boolean useXarJobId)
        {
            if (o instanceof ExpData data)
            {
                // Most DataClass data don't have a dataFileUrl, but some do -- like NucSequence imported from a genbank file
                if (data.getDataFileUrl() == null || data.getDataClass() != null)
                {
                    // If we don't have a URL for this data object, we can't use AutoFileLSID. Instead,
                    // try the next best option
                    return PARTIAL_FOLDER_RELATIVE.relativize(o, lsids, useXarJobId);
                }
            }
            return super.relativize(o, lsids, useXarJobId);
        }

        @Override
        protected String relativize(Lsid lsid, RelativizedLSIDs lsids, boolean useXarJobId)
        {
            String prefix = lsid.getNamespacePrefix();
            String suffix = lsid.getNamespaceSuffix();

            String sharedFolderSuffix = "Folder-" + ContainerManager.getSharedContainer().getRowId();
            String containerSubstitution = sharedFolderSuffix.equals(suffix) ? XarContext.SHARED_CONTAINER_ID_SUBSTITUTION : XarContext.CONTAINER_ID_SUBSTITUTION;

            if (ExpRun.DEFAULT_CPAS_TYPE.equals(prefix))
            {
                return lsids.uniquifyRelativizedLSID("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":ExperimentRun.Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ".${XarFileId}", lsid.getObjectId(), lsid.getVersion());
            }
            else if (ExpProtocolApplication.DEFAULT_CPAS_TYPE.equals(prefix))
            {
                return lsids.uniquifyRelativizedLSID("${RunLSIDBase}", lsid.getObjectId(), lsid.getVersion());
            }
            else if ("sfx".equals(lsid.getObjectId())) // else if (MATERIAL_PREFIX_PLACEHOLDER_SUFFIX.equals(lsid.getObjectId()))
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
            else if ("Sample".equals(prefix) || ExpMaterial.DEFAULT_CPAS_TYPE.equals(prefix))
            {
                String xarJobId = "." + XAR_JOB_ID_NAME_SUB; // XarJobId is more concise than XarFileId
                return lsids.uniquifyRelativizedLSID("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":" + prefix + ".Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + xarJobId + lsids.getNextSampleId(), lsid.getObjectId(), lsid.getVersion());
            }
            else if (ExpData.DEFAULT_CPAS_TYPE.equals(prefix))
            {
                // UNDONE: Now that "Data" prefix is used for DataClass, the AutoFileLSID is not a good default.
                // UNDONE: Can we be more restrictive about which LSIDs this is applied to?  Maybe only if the objectId part of the LSID includes a "/" (%2F) or something?
                // UNDONE: Maybe there is a better way to detect when we should use ${AutoFileLSID}?
                return "${AutoFileLSID}"; // AutoFileLSIDReplacer.AUTO_FILE_LSID_SUBSTITUTION;
            }
            else if (suffix != null && (SUFFIX_PATTERN.matcher(suffix).matches() || XAR_IMPORT_SUFFIX_PATTERN.matcher(suffix).matches()))
            {
                String xarFileId = "";
                if (useXarJobId || "SampleSet".equals(prefix) || "DataClass".equals(prefix))
                {
                    xarFileId = "." + XAR_JOB_ID_NAME_SUB; // DBSeq lsid might collide in target folder, add XarJobId to guarantee uniqueness
                }

                return lsids.uniquifyRelativizedLSID("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":" + prefix + ".Folder-" + containerSubstitution + xarFileId, lsid.getObjectId(), lsid.getVersion());
            }
            else if (suffix != null && FOLDER_UUID_PATTERN.matcher(suffix).matches() && useXarJobId)
            {
                Matcher guidMatcher = UUID_PATTERN.matcher(suffix);
                String guid = "";
                if (guidMatcher.find())
                    guid = guidMatcher.group(0);
                return lsids.uniquifyRelativizedLSID("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":" + prefix + ".Folder-" + containerSubstitution + "." + guid + "." + XAR_JOB_ID_NAME_SUB, lsid.getObjectId(), lsid.getVersion());
            }

            return lsid.toString();
        }
    },
    PARTIAL_FOLDER_RELATIVE("Partial folder relative")
    {
        @Override
        public String relativize(Lsid lsid, RelativizedLSIDs lsids, boolean useXarJobId)
        {
            String prefix = lsid.getNamespacePrefix();
            String suffix = lsid.getNamespaceSuffix();
            if (prefix == null)
            {
                return lsid.toString();
            }
            if (ExpRun.DEFAULT_CPAS_TYPE.equals(prefix))
            {
                return lsids.uniquifyRelativizedLSID("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":ExperimentRun.Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + ".${XarFileId}", lsid.getObjectId(), lsid.getVersion());
            }
            else if (ExpProtocolApplication.DEFAULT_CPAS_TYPE.equals(prefix))
            {
                return lsids.uniquifyRelativizedLSID("${RunLSIDBase}", lsid.getObjectId(), lsid.getVersion());
            }
            else if ("Sample".equals(prefix))
            {
                return lsids.uniquifyRelativizedLSID("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":Sample.Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + "." + XAR_JOB_ID_NAME_SUB + "-" + lsids.getNextSampleId(), lsid.getObjectId(), lsid.getVersion());
            }
            else if (ExpMaterial.DEFAULT_CPAS_TYPE.equals(prefix))
            {
                if (StringUtils.startsWith(lsid.getNamespaceSuffix(),"Folder-"))
                {
                    return stripFolderSuffix(lsid, lsids);
                }
                else
                {
                    return lsids.uniquifyRelativizedLSID("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":Material.Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + "." + XAR_JOB_ID_NAME_SUB + "-" + lsids.getNextMaterialId(), lsid.getObjectId(), lsid.getVersion());
                }
            }
            else if (ExpData.DEFAULT_CPAS_TYPE.equals(prefix))
            {
                if (StringUtils.startsWith(lsid.getNamespaceSuffix(),"Folder-"))
                {
                    return stripFolderSuffix(lsid, lsids);
                }
                else
                {
                    return lsids.uniquifyRelativizedLSID("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":Data.Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + "." + XAR_JOB_ID_NAME_SUB + "-" + lsids.getNextDataId(), lsid.getObjectId(), lsid.getVersion());
                }
            }
            else
            {
                if (StringUtils.startsWith(suffix,"Folder-"))
                {
                    return stripFolderSuffix(lsid, lsids);
                }
                else
                {
                    String jobId = useXarJobId ? "." + XAR_JOB_ID_NAME_SUB : "";
                    return lsids.uniquifyRelativizedLSID("urn:lsid:" + XarContext.LSID_AUTHORITY_SUBSTITUTION + ":" + prefix + ".Folder-" + XarContext.CONTAINER_ID_SUBSTITUTION + jobId, lsid.getObjectId(), lsid.getVersion());
                }
            }
        }
    };
    private final String _description;

    protected abstract String relativize(Lsid lsid, RelativizedLSIDs lsids, boolean useXarJobId);

    protected String relativize(ExpObject o, RelativizedLSIDs lsids, boolean useXarJobId)
    {
        return relativize(new Lsid(o.getLSID()), lsids, useXarJobId);
    }

    private static final Pattern SUFFIX_PATTERN = Pattern.compile("Folder-[0-9]+");
    private static final Pattern XAR_IMPORT_SUFFIX_PATTERN = Pattern.compile("Folder-[0-9]+.Xar-[0-9]+");
    private static final Pattern UUID_PATTERN = Pattern.compile("[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");
    private static final Pattern FOLDER_UUID_PATTERN = Pattern.compile("Folder-[0-9]+.[a-f0-9]{8}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{4}-[a-f0-9]{12}");

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

            result = _relativizer.relativize(o, this, false);
            putLSID(o.getLSID(), result);
            return result;
        }

        public String relativize(String s)
        {
            return relativize(s, false);
        }

        public String relativize(String s, boolean useXarJobId)
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

            result = _relativizer.relativize(new Lsid(s), this, useXarJobId);
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
            assertEquals("${AutoFileLSID}", set.relativize("urn:lsid:labkey.com:Data.Folder-18698:File1.txt")); // AutoFileLSIDReplacer.AUTO_FILE_LSID_SUBSTITUTION;
            assertEquals("${AutoFileLSID}", set.relativize("urn:lsid:labkey.com:Data.Folder-18698:File2.txt")); // AutoFileLSIDReplacer.AUTO_FILE_LSID_SUBSTITUTION;
        }
    }
}
