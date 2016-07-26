/* 
 * Copyright (C) 2016 Bielefeld University, Patrick Holthaus
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package de.citec.csra.util;

import rst.geometry.SphericalDirectionFloatType.SphericalDirectionFloat;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class SphericalDirectionFloatParser implements StringParser<SphericalDirectionFloat>{

	@Override
	public SphericalDirectionFloat getValue(String val) throws IllegalArgumentException {
		String[] pt= val.split(",");
		if(pt.length != 2){
			throw new IllegalArgumentException("Illegal angle: " + val);
		}
		
		SphericalDirectionFloat angle = SphericalDirectionFloat.newBuilder().
				setAzimuth(Float.valueOf(pt[0])).
				setElevation(Float.valueOf(pt[1])).build();
		
		return angle;
	}

	@Override
	public Class<SphericalDirectionFloat> getTargetClass() {
		return SphericalDirectionFloat.class;
	}
	
}
