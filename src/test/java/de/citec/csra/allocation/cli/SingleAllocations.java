/*
 * Copyright (C) 2017 pholthau
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

import rsb.RSBException;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Initiator.HUMAN;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Initiator.SYSTEM;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Policy.MAXIMUM;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Priority.EMERGENCY;

/**
 *
 * @author pholthau
 */
public class SingleAllocations {
	public static void main(String[] args) throws RSBException, InterruptedException {
		AllocatableResource one = new AllocatableResource("one", MAXIMUM, EMERGENCY, HUMAN, 8000, 10000, "/some/res");
		AllocatableResource two = new AllocatableResource("two", MAXIMUM, EMERGENCY, SYSTEM, 4000, 8000, "/some/res");
//		AllocatableResource three = new AllocatableResource("tre", MAXIMUM, EMERGENCY, HUMAN, 1500, 12000, "/some/res");
//		AllocatableResource four = new AllocatableResource("fou", MAXIMUM, EMERGENCY, HUMAN, 15000, 4000, "/some/res");
//		AllocatableResource five = new AllocatableResource("fiv", MAXIMUM, EMERGENCY, HUMAN, 6000, 2000, "/some/res");
		
		one.startup();
		Thread.sleep(1000);
		two.startup();
//		three.startup();
//		Thread.sleep(2000);
//		four.startup();
//		Thread.sleep(2000);
//		five.startup();
		
		
	}
}
