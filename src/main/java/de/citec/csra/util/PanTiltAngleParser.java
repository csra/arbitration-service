/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.citec.csra.util;

import rst.spatial.PanTiltAngleType.PanTiltAngle;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class PanTiltAngleParser implements StringParser<PanTiltAngle> {

	@Override
	public PanTiltAngle getValue(String val) throws IllegalArgumentException {
		String[] pt= val.split(",");
		if(pt.length != 2){
			throw new IllegalArgumentException("Illegal angle: " + val);
		}
		
		PanTiltAngle angle = PanTiltAngle.newBuilder().
				setPan(Float.valueOf(pt[0])).
				setTilt(Float.valueOf(pt[1])).build();
		
		return angle;
	}

	@Override
	public Class<PanTiltAngle> getTargetClass() {
		return PanTiltAngle.class;
	}
}
