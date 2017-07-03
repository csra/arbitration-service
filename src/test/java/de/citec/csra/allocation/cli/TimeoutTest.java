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

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import java.util.concurrent.TimeoutException;
import org.junit.BeforeClass;
import org.junit.Test;
import rsb.InitializeException;
import rsb.RSBException;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Initiator.SYSTEM;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Policy.MAXIMUM;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Priority.*;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.CANCELLED;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class TimeoutTest {

	private static final long TIMEOUT = 2500;

	@BeforeClass
	public static void initServer() throws InterruptedException, RSBException {
		TestSetup.shutdownServer();
	}

	@Test
	public void testTimeout() throws InitializeException, RSBException, InterruptedException, TimeoutException {
		AllocatableResource res = new AllocatableResource("Original", MAXIMUM, NORMAL, SYSTEM, 0, 200, MILLISECONDS, "unreachable-resource");
		res.startup();
		res.await(TIMEOUT, MILLISECONDS, CANCELLED);
	}
}
