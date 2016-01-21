/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.citec.csra.util;

import rst.hri.HighlightTargetType.HighlightTarget;
import rst.timing.DurationType;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class HighlightTargetParser implements StringParser<HighlightTarget> {

	EnumParser<HighlightTarget.Modality> mods = new EnumParser<>(HighlightTarget.Modality.class);

	@Override
	public HighlightTarget getValue(String val) {

		String[] tgt = val.split(",");
		if (tgt.length < 3) {
			throw new IllegalArgumentException("Illegal highlight target: " + val);
		}

		HighlightTarget.Builder bld = HighlightTarget.newBuilder().setTargetId(tgt[0]);
		for (int i = 1; i < tgt.length - 1; i++) {
			bld.addModality(mods.getValue(tgt[i]));
		}
		bld.setDuration(DurationType.Duration.newBuilder().setTime(Long.valueOf(tgt[tgt.length - 1])).build());
		return bld.build();
	}

	@Override
	public Class<HighlightTarget> getTargetClass() {
		return HighlightTarget.class;
	}

}
