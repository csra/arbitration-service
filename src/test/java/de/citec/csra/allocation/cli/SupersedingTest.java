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

import de.citec.csra.allocation.srv.AllocationServer;
import java.util.concurrent.TimeoutException;
import static org.junit.Assert.fail;
import org.junit.BeforeClass;
import org.junit.Test;
import rsb.InitializeException;
import rsb.RSBException;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Initiator.SYSTEM;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Policy.MAXIMUM;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Priority.*;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.ALLOCATED;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.RELEASED;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.REQUESTED;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.SCHEDULED;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class SupersedingTest {

	@BeforeClass
	public static void initServer() throws InterruptedException {
		new Thread(() -> {
			try {
				AllocationServer a = AllocationServer.getInstance();
				a.activate();
				a.listen();
			} catch (InterruptedException | RSBException ex) {
				fail("Exception in server thread: " + ex);
			}
		}).start();
		Thread.sleep(200);
	}

	@Test
	public void testSchedule() throws InitializeException, RSBException, InterruptedException, TimeoutException {
		AllocatableResource res = new AllocatableResource("Original", MAXIMUM, NORMAL, SYSTEM, 2000, 1000, "some-resource");
		res.startup();
		res.await(REQUESTED, 1000);
		res.await(SCHEDULED, 1000);
		res.await(ALLOCATED, 3000);
		res.await(RELEASED, 5000);
	}

	@Test
	public void testNonConflict() throws InitializeException, RSBException, InterruptedException, TimeoutException {
		AllocatableResource some = new AllocatableResource("Some", MAXIMUM, NORMAL, SYSTEM, 0, 5000, "some-resource");
		AllocatableResource other = new AllocatableResource("Other", MAXIMUM, NORMAL, SYSTEM, 0, 5000, "other-resource");
		
		some.startup();
		other.startup();
		
		some.await(REQUESTED, 1000);
		other.await(REQUESTED, 1000);
		
		some.await(SCHEDULED, 1000);
		other.await(SCHEDULED, 1000);
		
		some.await(ALLOCATED, 3000);
		other.await(ALLOCATED, 3000);
		
		some.await(RELEASED, 5000);
		other.await(RELEASED, 5000);
	}

//	@Test
//	public void testShortening() throws InitializeException, RSBException, InterruptedException {
//		SleepingSkill normal = new SleepingSkill("Normal", "some-resource", MAXIMUM, NORMAL);
//		SleepingSkill higher = new SleepingSkill("Higher", "some-resource", MAXIMUM, HIGH);
//		normal.activate();
//		higher.activate();
//
//		normal.schedule(2000, 4000);
//		long shouldShift = normal.getDuration();
//
//		Thread.sleep(500);
//		
//		higher.schedule(3000, 2000);
//		long shouldStay = higher.getDuration();
//
//		
//		assertTrue("normal termination", higher.await());
//		assertTrue("normal termination", normal.await());
//
//		long modified = normal.getDuration();
//		long stable = higher.getDuration();
//
//		assertTrue("normal priority duration shifted", shouldShift > modified);
//		assertTrue("higher priority duration stable", shouldStay == stable);
//		
//		
//		assertTrue("scheduled only once", higher.getScheduled() == 1);
//		assertTrue("allocated only once", higher.getAllocated() == 1);
//
//		assertTrue("not cancelled", higher.getCancelled() == 0);
//		assertTrue("not rejected", higher.getRejected() == 0);
//		assertTrue("not aborted", higher.getAborted() == 0);
//		
//		
//		assertTrue("scheduled twice", normal.getScheduled() == 2);
//		assertTrue("allocated only once", normal.getAllocated() == 1);
//
//		assertTrue("not cancelled", normal.getCancelled() == 0);
//		assertTrue("not rejected", normal.getRejected() == 0);
//		assertTrue("not aborted", normal.getAborted() == 0);
//
//	}

//	@Test
//	public void testCancelling() throws InitializeException, RSBException, InterruptedException {
//		SleepingSkill normal = new SleepingSkill("Normal", "some-resource", MAXIMUM, NORMAL);
//		SleepingSkill higher = new SleepingSkill("Higher", "some-resource", MAXIMUM, HIGH);
//		normal.activate();
//		higher.activate();
//
//		normal.schedule(0, 5000);
//		Thread.sleep(500);
//
//		higher.schedule(0, 1000);
//		long shouldStay = higher.getDuration();
//
//		assertTrue("normal termination", higher.await());
//		assertFalse("normal termination", normal.await());
//
//		long stable = higher.getDuration();
//		assertTrue("higher priority duration stable", shouldStay == stable);
//
//	}
}
