/*
 * Copyright (c) 2004-2008 Fred Hutchinson Cancer Research Center
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

import java.util.ArrayList;


/**
 * User: mbellew
 * Date: May 24, 2004
 * Time: 9:09:24 PM
 */
// alternative to ArrayList<Float>
public class FloatArray
    {
    private static final int ARRAY_LEN = 1024;

	int _lenSegment = ARRAY_LEN;
    ArrayList _list = new ArrayList();
    float[] _arrayLast;
    int _lenLast = 0;
    int _size = 0;


    public FloatArray()
        {
        _list = new ArrayList();
        _arrayLast = new float[_lenSegment];
        _list.add(_arrayLast);
        }


	private FloatArray(float[] a)
	    {
	    _list = new ArrayList(1);
	    _arrayLast = a;
	    _list.add(a);
	    _size = a.length;
	    _lenSegment = _size;
	    _lenLast = _size;
	    }


    public void add(float f)
        {
        if (_arrayLast.length <= _lenLast)
            {
            _arrayLast = new float[_lenSegment];
            _list.add(_arrayLast);
            _lenLast = 0;
            }
        _arrayLast[_lenLast++] = f;
        _size++;
        }


    public float get(int i)
        {
        return ((float[]) _list.get(i / _lenSegment))[i % _lenSegment];
        }


    public int size()
        {
        return _size;
        }

    public void setSize(int newSize)
    {
        _size=newSize;
    }


    public float[] toArray(float[] dst)
		{
//		assert null == "not tested";
    	if (null == dst || dst.length < _size)
			dst = new float[_size];
		int end=0, i=0;
		float[] src;
		for (; i<_list.size()-1 ; i++)
			{
			src = (float[])_list.get(i);
			System.arraycopy(src, 0, dst, end, src.length);
			end += src.length;
			}
		src = (float[])_list.get(i);
		System.arraycopy(src, 0, dst, end, _lenLast);
		return dst;
		}


    public FloatRange getRange()
        {
        if (0 == _size)
            return new FloatRange(0F, 0F);
        float f = get(0);
        float min = f, max = f;
        for (int j = 0 ; j < _list.size() ; j++)
            {
            float[] array = (float[]) _list.get(j);
            int len = j == _list.size() - 1 ? _lenLast : _lenSegment;
            for (int i = 1 ; i < len ; i++)
                {
                f = array[i];
                if (f < min)
                    min = f;
                else if (f > max)
                    max = f;
                }
            }
        return new FloatRange(min, max);
        }


	public static FloatArray asFloatArray(float[] a)
		{
		return new FloatArray(a)
			{
			public void add(float f)
				{
				throw new java.lang.UnsupportedOperationException();
				}
			};
		}
    }
