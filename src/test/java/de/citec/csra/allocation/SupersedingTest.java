/*
 * Copyright (C) 2016 Patrick Holthaus (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
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
package de.citec.csra.allocation;

import de.citec.csra.allocation.srv.AllocationServer;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.BeforeClass;
import org.junit.Test;
import rsb.InitializeException;
import rsb.RSBException;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Policy.MAXIMUM;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Priority.*;

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
				AllocationServer a = new AllocationServer();
				a.activate();
				a.waitForShutdown();
			} catch (InterruptedException | RSBException ex) {
				fail("Exception in server thread: " + ex);
			}
		}).start();
		Thread.sleep(200);
	}

	@Test
	public void testSchedule() throws InitializeException, RSBException, InterruptedException {
		SleepingSkill orig = new SleepingSkill("Original", "some-resource", MAXIMUM, NORMAL);
		orig.activate();
		orig.schedule(0, 1000);
		assertTrue("normal termination", orig.await());

		assertTrue("scheduled at least once", orig.getScheduled() >= 1);
		assertTrue("allocated at least once", orig.getAllocated() >= 1);

		assertTrue("not cancelled", orig.getCancelled() == 0);
		assertTrue("not rejected", orig.getRejected() == 0);
		assertTrue("not aborted", orig.getAborted() == 0);

	}

	@Test
	public void testNonConflict() throws InitializeException, RSBException, InterruptedException {
		SleepingSkill some = new SleepingSkill("Some", "some-resource", MAXIMUM, NORMAL);
		SleepingSkill other = new SleepingSkill("Other", "other-resource", MAXIMUM, NORMAL);
		some.activate();
		other.activate();

		some.schedule(0, 1000);
		other.schedule(0, 1000);

		assertTrue("normal termination", some.await());
		assertTrue("normal termination", other.await());

		assertTrue("scheduled only once", some.getScheduled() == 1);
		assertTrue("allocated only once", some.getAllocated() == 1);

		assertTrue("not cancelled", some.getCancelled() == 0);
		assertTrue("not rejected", some.getRejected() == 0);
		assertTrue("not aborted", some.getAborted() == 0);

		assertTrue("scheduled only once", other.getScheduled() == 1);
		assertTrue("allocated only once", other.getAllocated() == 1);

		assertTrue("not cancelled", other.getCancelled() == 0);
		assertTrue("not rejected", other.getRejected() == 0);
		assertTrue("not aborted", other.getAborted() == 0);
	}

	@Test
	public void testShortening() throws InitializeException, RSBException, InterruptedException {
		SleepingSkill normal = new SleepingSkill("Normal", "some-resource", MAXIMUM, NORMAL);
		SleepingSkill higher = new SleepingSkill("Higher", "some-resource", MAXIMUM, HIGH);
		normal.activate();
		higher.activate();

		normal.schedule(2000, 4000);
		long shouldShift = normal.getDuration();

		Thread.sleep(500);
		
		higher.schedule(3000, 2000);
		long shouldStay = higher.getDuration();

		
		assertTrue("normal termination", higher.await());
		assertTrue("normal termination", normal.await());

		long modified = normal.getDuration();
		long stable = higher.getDuration();

		assertTrue("normal priority duration shifted", shouldShift > modified);
		assertTrue("higher priority duration stable", shouldStay == stable);
		
		
		assertTrue("scheduled only once", higher.getScheduled() == 1);
		assertTrue("allocated only once", higher.getAllocated() == 1);

		assertTrue("not cancelled", higher.getCancelled() == 0);
		assertTrue("not rejected", higher.getRejected() == 0);
		assertTrue("not aborted", higher.getAborted() == 0);
		
		
		assertTrue("scheduled twice", normal.getScheduled() == 2);
		assertTrue("allocated only once", normal.getAllocated() == 1);

		assertTrue("not cancelled", normal.getCancelled() == 0);
		assertTrue("not rejected", normal.getRejected() == 0);
		assertTrue("not aborted", normal.getAborted() == 0);

	}

	@Test
	public void testCancelling() throws InitializeException, RSBException, InterruptedException {
		SleepingSkill normal = new SleepingSkill("Normal", "some-resource", MAXIMUM, NORMAL);
		SleepingSkill higher = new SleepingSkill("Higher", "some-resource", MAXIMUM, HIGH);
		normal.activate();
		higher.activate();

		normal.schedule(0, 5000);
		Thread.sleep(500);

		higher.schedule(0, 1000);
		long shouldStay = higher.getDuration();

		assertTrue("normal termination", higher.await());
		assertFalse("normal termination", normal.await());

		long stable = higher.getDuration();
		assertTrue("higher priority duration stable", shouldStay == stable);

	}
}
