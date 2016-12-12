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

import de.citec.csra.allocation.cli.AllocatableResource;
import de.citec.csra.allocation.cli.RemoteAllocationService;
import java.util.concurrent.ExecutionException;
import rsb.InitializeException;
import rsb.RSBException;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Initiator;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Policy;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Priority;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.RELEASED;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class ModificationTest {

	public static void main(String[] args) throws InitializeException, InterruptedException, RSBException, ExecutionException {

		AllocatableResource ar = new AllocatableResource("second class", Policy.MAXIMUM, Priority.NORMAL, Initiator.SYSTEM, 1000, 5000, "some-res");
		AllocatableResource prio = new AllocatableResource("first class", Policy.MAXIMUM, Priority.HIGH, Initiator.SYSTEM, 10000, 1000, "some-res");

		ar.startup();
		prio.startup();

//		Thread.sleep(2000);
//		ar.shift(1000);
		Thread.sleep(2000);
		ar.getRemote().shift(-15000);
//		ar.shiftTo(System.currentTimeMillis() + 10000);
//		ar.shift(10000);
//		Thread.sleep(2000);
//		ar.extend(5000);
//		Thread.sleep(2000);
//		ar.extend(-3000);
		ar.await(RELEASED);

		ar.startup();

		RemoteAllocationService.getInstance().shutdown();
	}

}
