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

import java.util.concurrent.ExecutionException;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import java.util.concurrent.TimeoutException;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.BeforeClass;
import org.junit.Test;
import rsb.InitializeException;
import rsb.RSBException;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Initiator.SYSTEM;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Policy.MAXIMUM;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Priority.HIGH;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Priority.NORMAL;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.ALLOCATED;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.RELEASED;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class ModificationTest {

	@BeforeClass
	public static void initServer() throws InterruptedException, RSBException {
		TestSetup.initServer();
	}

	@Test
	public void testModification() throws InitializeException, InterruptedException, RSBException, ExecutionException {

		AllocatableResource ar = new AllocatableResource("second class", MAXIMUM, NORMAL, SYSTEM, 500, 5000, MILLISECONDS, "some-res");
		AllocatableResource prio = new AllocatableResource("first class", MAXIMUM, HIGH, SYSTEM, 2500, 1000, MILLISECONDS, "some-res");

		ar.startup();
		prio.startup();

		try {
			ar.await(600, MILLISECONDS, ALLOCATED);
		} catch (TimeoutException ex) {
			fail(ex.getMessage());
		}

		Thread.sleep(100);
		prio.getRemote().shift(-1000, MILLISECONDS);

		assertTrue(ar.getRemote().isAlive());

		try {
			ar.await(1500, MILLISECONDS, RELEASED);
		} catch (TimeoutException ex) {
			fail(ex.getMessage());
		}

		try {
			prio.await(3000, MILLISECONDS, RELEASED);
		} catch (TimeoutException ex) {
			fail(ex.getMessage());
		}
	}
}
