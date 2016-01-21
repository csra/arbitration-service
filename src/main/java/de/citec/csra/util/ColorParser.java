/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.citec.csra.util;

import rst.vision.HSVColorType.HSVColor;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class ColorParser implements StringParser<HSVColor> {

	@Override
	public HSVColor getValue(String val) throws IllegalArgumentException {
		String[] hsv= val.split(",");
		if(hsv.length != 3){
			throw new IllegalArgumentException("Illegal HSV value: " + val);
		}
		
		HSVColor color = HSVColor.newBuilder().
				setHue(Double.valueOf(hsv[0])).
				setSaturation(Double.valueOf(hsv[1])).
				setValue(Double.valueOf(hsv[2])).build();
		
		return color;
	}

	@Override
	public Class<HSVColor> getTargetClass() {
		return HSVColor.class;
	}
}
