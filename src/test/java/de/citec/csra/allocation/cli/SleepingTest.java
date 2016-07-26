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
import java.util.logging.Level;
import java.util.logging.Logger;
import rsb.InitializeException;
import rsb.RSBException;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Initiator;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Policy;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Priority;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class SleepingTest {

	public static void main(String[] args) throws InitializeException, InterruptedException, RSBException, ExecutionException {
		ExecutableResource sl = new ExecutableResource("descr", Policy.PRESERVE, Priority.NORMAL, Initiator.SYSTEM, "/dev/urandom") {
			@Override
			public Object execute(long slice) throws ExecutionException {
				try {
					Thread.sleep(slice);
					return "slept well";
				} catch (InterruptedException ex) {
					Logger.getLogger(SleepingTest.class.getName()).log(Level.SEVERE, "client interrupted", ex);
					Thread.currentThread().interrupt();
				}
				return "what a night 0_o";
			}
		};
		sl.schedule(0, 4000);
		System.out.println(sl.getFuture().get());
		sl.schedule(1000, 5000);
		System.out.println(sl.getFuture().get());
		sl.shutdown();
		RemoteAllocationService.getInstance().shutdown();
	}

}
