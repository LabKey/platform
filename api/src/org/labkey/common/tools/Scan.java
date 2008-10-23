/*
 * Copyright (c) 2005-2008 Fred Hutchinson Cancer Research Center
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
package org.labkey.common.tools;

/**
 * User: mbellew
 * Date: Feb 8, 2005
 * Time: 1:56:36 PM
 */
public interface Scan
	{
	float[][] getSpectrum();

	int getNum();

	int getMsLevel();

	int getPeaksCount();

	String getPolarity();

	String getScanType();

	int getCentroided();

	int getDeisotoped();

	int getChargeDeconvoluted();

	String getRetentionTime();

	float getStartMz();

	float getEndMz();

	float getLowMz();

	float getHighMz();

	float getBasePeakMz();

	float getBasePeakIntensity();

	float getTotIonCurrent();

	float getPrecursorMz();

	int getPrecursorScanNum();

	int getPrecursorCharge();

	float getCollisionEnergy();

	float getIonisationEnergy();

	int getPrecision();

	double getDoubleRetentionTime();
	}
