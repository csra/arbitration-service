/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
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
	
}
