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
import rsb.Factory;
import rsb.InitializeException;
import rsb.RSBException;
import rsb.config.ParticipantConfig;
import rsb.config.TransportConfig;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Initiator.SYSTEM;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Policy.MAXIMUM;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Priority.*;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.ALLOCATED;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.CANCELLED;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.REJECTED;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.RELEASED;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.REQUESTED;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.SCHEDULED;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class SupersedingTest {
	
	private static final long TIMEOUT = RemoteAllocationService.TIMEOUT + 1000;

	@BeforeClass
	public static void initServer() throws InterruptedException {
		ParticipantConfig cfg = Factory.getInstance().getDefaultParticipantConfig();
		for (TransportConfig t : cfg.getTransports().values()) {
			t.setEnabled(t.getName().equalsIgnoreCase("INPROCESS"));
		}
		Factory.getInstance().setDefaultParticipantConfig(cfg);
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
	public void testSingle() throws InitializeException, RSBException, InterruptedException, TimeoutException {
		AllocatableResource res = new AllocatableResource("Original", MAXIMUM, NORMAL, SYSTEM, 100, 500, "some-resource");
		res.startup();
		res.await(REQUESTED, TIMEOUT);
		res.await(SCHEDULED, TIMEOUT);
		res.await(ALLOCATED, TIMEOUT);
		res.await(RELEASED, TIMEOUT);
	}

	@Test
	public void testNonConflict() throws InitializeException, RSBException, InterruptedException, TimeoutException {
		AllocatableResource some = new AllocatableResource("Some", MAXIMUM, NORMAL, SYSTEM, 100, 500, "some-resource");
		AllocatableResource other = new AllocatableResource("Other", MAXIMUM, NORMAL, SYSTEM, 100, 500, "other-resource");

		some.startup();
		other.startup();

		some.await(REQUESTED, TIMEOUT);
		other.await(REQUESTED, TIMEOUT);

		some.await(SCHEDULED, TIMEOUT);
		other.await(SCHEDULED, TIMEOUT);

		some.await(ALLOCATED, TIMEOUT);
		other.await(ALLOCATED, TIMEOUT);

		some.await(RELEASED, TIMEOUT);
		other.await(RELEASED, TIMEOUT);
	}

	@Test
	public void testShortening() throws InitializeException, RSBException, InterruptedException, TimeoutException {
		AllocatableResource normal = new AllocatableResource("Higher", MAXIMUM, NORMAL, SYSTEM, 0, 1500, "some-resource");
		AllocatableResource higher = new AllocatableResource("Higher", MAXIMUM, HIGH, SYSTEM, 500, 500, "some-resource");
		normal.startup();
		higher.startup();

		normal.await(REQUESTED, TIMEOUT);
		higher.await(REQUESTED, TIMEOUT);

		normal.await(SCHEDULED, TIMEOUT);
		higher.await(SCHEDULED, TIMEOUT);

		normal.await(ALLOCATED, TIMEOUT);
		higher.await(ALLOCATED, TIMEOUT);

		normal.await(RELEASED, TIMEOUT);
		higher.await(RELEASED, TIMEOUT);
	}
	
	@Test
	public void testCancelling() throws InitializeException, RSBException, InterruptedException, TimeoutException {
		AllocatableResource normal = new AllocatableResource("Normal", MAXIMUM, NORMAL, SYSTEM, 500, 500, "some-resource");
		AllocatableResource normal2 = new AllocatableResource("Normal2", MAXIMUM, NORMAL, SYSTEM, 1500, 500, "some-resource");
		AllocatableResource higher = new AllocatableResource("Higher", MAXIMUM, HIGH, SYSTEM, 0, 2500, "some-resource");
		normal.startup();
		normal.await(REQUESTED, TIMEOUT);
		normal.await(SCHEDULED, TIMEOUT);
		
		higher.startup();
		higher.await(REQUESTED, TIMEOUT);
		higher.await(SCHEDULED, TIMEOUT);
		higher.await(ALLOCATED, TIMEOUT);

		normal.await(CANCELLED, TIMEOUT);
		
		normal2.startup();
		normal2.await(REJECTED, TIMEOUT);
		
		higher.await(RELEASED, TIMEOUT);
	}

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
