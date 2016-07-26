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
