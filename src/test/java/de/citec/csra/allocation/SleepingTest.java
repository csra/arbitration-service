/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.citec.csra.allocation;

import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import rsb.InitializeException;
import rsb.RSBException;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Policy;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation.Priority;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class SleepingTest {

	public static void main(String[] args) throws InitializeException, InterruptedException, RSBException, ExecutionException {
		ExecutableResource sl = new ExecutableResource("descr", "/dev/urandom", Policy.PRESERVE, Priority.NORMAL) {
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
	}

}
