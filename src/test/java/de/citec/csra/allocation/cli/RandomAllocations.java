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
package de.citec.csra.allocation.cli;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import rsb.InitializeException;
import rsb.RSBException;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Initiator;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Policy;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Priority;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class RandomAllocations {

	public static void main(String[] args) throws InitializeException, InterruptedException, RSBException, ExecutionException {
		Random rnd = new Random();
		List<String> res = Arrays.asList(new String[]{"zero", "/one", "/two", "/three", "/four", "/five", "/six", "/seven", "/eight", "/nine"});

		for (int i = 0; i < 500; i++) {
			Policy pol = Policy.values()[rnd.nextInt(Policy.values().length)];
			Priority pri = Priority.values()[rnd.nextInt(Priority.values().length)];
			Initiator ini = Initiator.values()[rnd.nextInt(Initiator.values().length)];
			long sta = rnd.nextInt(60000);
			long len = 3000 + rnd.nextInt(5000);

			Collections.shuffle(res);
			List<String> all = res.subList(0, rnd.nextInt(res.size()/2));
			AllocatableResource a = new AllocatableResource("descr", pol, pri, ini, sta, len, all.stream().toArray(String[]::new));
			a.startup();
			Thread.sleep(250);
		}
	}
}
