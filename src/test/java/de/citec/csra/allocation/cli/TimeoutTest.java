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
import static rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.State.CANCELLED;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class TimeoutTest {

	private static final long TIMEOUT = RemoteAllocationService.TIMEOUT + 1000;

	@BeforeClass
	public static void initServer() throws InterruptedException, RSBException {
		ParticipantConfig cfg = Factory.getInstance().getDefaultParticipantConfig();
		for (TransportConfig t : cfg.getTransports().values()) {
			t.setEnabled(t.getName().equalsIgnoreCase("INPROCESS"));
		}
		Factory.getInstance().setDefaultParticipantConfig(cfg);
		AllocationServer.getInstance().deactivate();
		Thread.sleep(200);
	}

	@Test
	public void testSchedule() throws InitializeException, RSBException, InterruptedException, TimeoutException {
		AllocatableResource res = new AllocatableResource("Original", MAXIMUM, NORMAL, SYSTEM, 0, 2 * TIMEOUT, "some-resource");
		res.startup();
		res.await(CANCELLED, TIMEOUT);
	}

	@Test
	public void testNonConflict() throws InitializeException, RSBException, InterruptedException, TimeoutException {
		AllocatableResource some = new AllocatableResource("Some", MAXIMUM, NORMAL, SYSTEM, 0, 2 * TIMEOUT, "some-resource");
		AllocatableResource other = new AllocatableResource("Other", MAXIMUM, NORMAL, SYSTEM, 0, 2 * TIMEOUT, "other-resource");

		some.startup();
		other.startup();

		some.await(CANCELLED, TIMEOUT);
		other.await(CANCELLED, TIMEOUT);
	}
}
