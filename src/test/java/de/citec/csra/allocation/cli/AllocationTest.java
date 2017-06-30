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

import static de.citec.csra.rst.util.IntervalUtils.buildRelativeRst;
import java.util.UUID;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import java.util.concurrent.TimeoutException;
import org.junit.AfterClass;
import static org.junit.Assert.fail;
import org.junit.BeforeClass;
import org.junit.Test;
import rsb.InitializeException;
import rsb.RSBException;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation;
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
public class AllocationTest {

	private static final long TIMEOUT = 5000;

	@BeforeClass
	public static void initServer() throws InterruptedException, RSBException {
		TestSetup.initServer();
	}

	@AfterClass
	public static void shutdownServer() throws InterruptedException, RSBException {
		TestSetup.shutdownServer();
	}

	@Test
	public void testExpiration() throws InitializeException, RSBException, InterruptedException, TimeoutException {
		AllocatableResource res = new AllocatableResource("Original", MAXIMUM, NORMAL, SYSTEM, -500, 100, MILLISECONDS, "some-resource");
		res.startup();

		res.await(TIMEOUT, MILLISECONDS, REQUESTED);
		res.await(TIMEOUT, MILLISECONDS, RELEASED);

		if (res.hasState(SCHEDULED)) {
			fail("should not be allcoated");
		}

		if (res.hasState(ALLOCATED)) {
			fail("should not be allcoated");
		}
	}

	@Test
	public void testSingle() throws InitializeException, RSBException, InterruptedException, TimeoutException {
		AllocatableResource res = new AllocatableResource("Original", MAXIMUM, NORMAL, SYSTEM, 100, 500, MILLISECONDS, "some-resource");
		res.startup();
		res.await(TIMEOUT, MILLISECONDS, REQUESTED);
		res.await(TIMEOUT, MILLISECONDS, SCHEDULED);
		res.await(TIMEOUT, MILLISECONDS, ALLOCATED);
		res.await(TIMEOUT, MILLISECONDS, RELEASED);
	}

	@Test
	public void testNonConflict() throws InitializeException, RSBException, InterruptedException, TimeoutException {
		AllocatableResource some = new AllocatableResource("Some", MAXIMUM, NORMAL, SYSTEM, 100, 500, "some-resource");
		AllocatableResource other = new AllocatableResource("Other", MAXIMUM, NORMAL, SYSTEM, 100, 500, "other-resource");

		some.startup();
		other.startup();

		some.await(TIMEOUT, MILLISECONDS, REQUESTED);
		other.await(TIMEOUT, MILLISECONDS, REQUESTED);

		some.await(TIMEOUT, MILLISECONDS, SCHEDULED);
		other.await(TIMEOUT, MILLISECONDS, SCHEDULED);

		some.await(TIMEOUT, MILLISECONDS, ALLOCATED);
		other.await(TIMEOUT, MILLISECONDS, ALLOCATED);

		some.await(TIMEOUT, MILLISECONDS, RELEASED);
		other.await(TIMEOUT, MILLISECONDS, RELEASED);
	}

	@Test
	public void testShortening() throws InitializeException, RSBException, InterruptedException, TimeoutException {
		AllocatableResource normal = new AllocatableResource("Higher", MAXIMUM, NORMAL, SYSTEM, 0, 1500, MILLISECONDS, "some-resource");
		AllocatableResource higher = new AllocatableResource("Higher", MAXIMUM, HIGH, SYSTEM, 500, 500, MILLISECONDS, "some-resource");
		normal.startup();
		higher.startup();

		normal.await(TIMEOUT, MILLISECONDS, REQUESTED);
		higher.await(TIMEOUT, MILLISECONDS, REQUESTED);

		normal.await(TIMEOUT, MILLISECONDS, SCHEDULED);
		higher.await(TIMEOUT, MILLISECONDS, SCHEDULED);

		normal.await(TIMEOUT, MILLISECONDS, ALLOCATED);
		higher.await(TIMEOUT, MILLISECONDS, ALLOCATED);

		normal.await(TIMEOUT, MILLISECONDS, RELEASED);
		higher.await(TIMEOUT, MILLISECONDS, RELEASED);
	}

	@Test
	public void testCancelling() throws InitializeException, RSBException, InterruptedException, TimeoutException {
		AllocatableResource normal = new AllocatableResource("Normal", MAXIMUM, NORMAL, SYSTEM, 500, 500, MILLISECONDS, "some-resource");
		AllocatableResource normal2 = new AllocatableResource("Normal2", MAXIMUM, NORMAL, SYSTEM, 1500, 500, MILLISECONDS, "some-resource");
		AllocatableResource higher = new AllocatableResource("Higher", MAXIMUM, HIGH, SYSTEM, 0, 2500, MILLISECONDS, "some-resource");

		normal.startup();
		normal.await(TIMEOUT, MILLISECONDS, REQUESTED);
		normal.await(TIMEOUT, MILLISECONDS, REQUESTED);

		higher.startup();
		higher.await(TIMEOUT, MILLISECONDS, REQUESTED);
		higher.await(TIMEOUT, MILLISECONDS, SCHEDULED);
		higher.await(TIMEOUT, MILLISECONDS, ALLOCATED);

		normal.await(TIMEOUT, MILLISECONDS, CANCELLED);

		normal2.startup();
		normal2.await(TIMEOUT, MILLISECONDS, REJECTED);

		higher.await(TIMEOUT, MILLISECONDS, RELEASED);
	}

	@Test
	public void testSameResource() throws RSBException, InterruptedException, TimeoutException {
		String id = UUID.randomUUID().toString();
		ResourceAllocation res = ResourceAllocation.newBuilder().
				setId(id).setState(REQUESTED).setDescription("Same").setPolicy(MAXIMUM).
				setPriority(NORMAL).setInitiator(SYSTEM).setSlot(buildRelativeRst(0, 2000, MILLISECONDS)).
				addResourceIds("some-resource").build();

		AllocatableResource ar = new AllocatableResource(res);
		AllocatableResource ar2 = new AllocatableResource(res);

		ar.startup();
		ar.await(TIMEOUT, MILLISECONDS, SCHEDULED);

		ar2.startup();

		ar.await(TIMEOUT, MILLISECONDS, ALLOCATED);
		ar2.await(TIMEOUT, MILLISECONDS, ALLOCATED);

		ar.await(TIMEOUT, MILLISECONDS, RELEASED);
		ar2.await(TIMEOUT, MILLISECONDS, RELEASED);
	}
}
