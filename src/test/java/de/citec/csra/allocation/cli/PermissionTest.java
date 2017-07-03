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
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import rsb.InitializeException;
import rsb.RSBException;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Initiator.SYSTEM;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Policy.MAXIMUM;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Priority.*;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.ALLOCATED;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.RELEASED;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class PermissionTest {

	private static final long TIMEOUT = 3500;

	@BeforeClass
	public static void initServer() throws InterruptedException, RSBException {
		TestSetup.initServer();
	}
	
	@AfterClass
	public static void shutdownServer() throws InterruptedException, RSBException {
		TestSetup.shutdownServer();
	}

	@Test
	public void testPermission() throws InitializeException, RSBException, InterruptedException, TimeoutException {

		AllocatableResource some = new AllocatableResource("parent", MAXIMUM, NORMAL, SYSTEM, 200, 3000, MILLISECONDS, "some-resource", "parent");
		AllocatableResource other = new AllocatableResource("child", MAXIMUM, NORMAL, SYSTEM, 500, 2000, MILLISECONDS, "some-resource", "child");
		
		some.getRemote().generateToken();
		other.getRemote().setToken(some.getRemote().getToken());

		some.startup();
		other.startup();

		some.await(TIMEOUT, MILLISECONDS, ALLOCATED);
		other.await(TIMEOUT, MILLISECONDS, ALLOCATED);
		
		some.await(TIMEOUT, MILLISECONDS, RELEASED);
		other.await(TIMEOUT, MILLISECONDS, RELEASED);
	}
}
