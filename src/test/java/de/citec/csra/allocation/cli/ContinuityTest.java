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

import de.citec.csra.allocation.srv.AllocationServer;
import java.util.concurrent.ExecutionException;
import org.junit.AfterClass;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.BeforeClass;
import org.junit.Test;
import rsb.Factory;
import rsb.RSBException;
import rsb.config.ParticipantConfig;
import rsb.config.TransportConfig;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Initiator.SYSTEM;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Policy.MAXIMUM;
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Priority.NORMAL;

/**
 *
 * @author pholthau
 */
public class ContinuityTest {

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

	@AfterClass
	public static void shutdownServer() throws InterruptedException, RSBException {
		AllocationServer.getInstance().deactivate();
	}
	
	@Test
	public void testImmediateRelease() throws RSBException, InterruptedException, ExecutionException{
		long duration = 500;
		ExecutableResource<String> t = new ExecutableResource<String>("Blocker", MAXIMUM, NORMAL, SYSTEM, 0, duration, false, "some-resource") {
			@Override
			public String execute() throws ExecutionException, InterruptedException {
				return "computed result";
			}
			
			@Override
			public void timeChanged(long remaining) {
			}
			
		};
		long before = System.currentTimeMillis();
		t.startup();
		t.getFuture().get();
		long after = System.currentTimeMillis();
		
		assertTrue("block for allocated time", after - before < duration);
	}
	
	@Test
	public void testContinousAllocation() throws RSBException, InterruptedException, ExecutionException{
		long duration = 500;
		ExecutableResource<String> t = new ExecutableResource<String>("Blocker", MAXIMUM, NORMAL, SYSTEM, 0, duration, true, "some-resource") {
			@Override
			public String execute() throws ExecutionException, InterruptedException {
				return "computed result";
			}
			
			@Override
			public void timeChanged(long remaining) {
			}
			
		};
		long before = System.currentTimeMillis();
		t.startup();
		t.getFuture().get();
		long after = System.currentTimeMillis();
		
		assertTrue("block for allocated time", after - before >= duration);
	}
}
