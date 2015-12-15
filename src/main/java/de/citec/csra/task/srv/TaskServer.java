/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.citec.csra.task.srv;

import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import rsb.InitializeException;
import rsb.RSBException;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class TaskServer {

	private final static Logger LOG = Logger.getLogger(TaskServer.class.getName());
	private final List<TaskHandler> handlers;

	public TaskServer(TaskHandler... handlers) throws InitializeException {
		this.handlers = Arrays.asList(handlers);
	}

	public void waitForShutdown() {
		while (true) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException ex) {
				LOG.log(Level.SEVERE, "Waiting interrupted", ex);
			}
		}
	}

	public void activate() throws RSBException, InterruptedException {
		for(TaskHandler h : this.handlers){
			h.activate();
		}
		LOG.log(Level.INFO, "RSB Communication activated.");
	}

	public void deactivate() throws RSBException, InterruptedException {
		for(TaskHandler h : this.handlers){
			h.deactivate();
		}
		LOG.log(Level.INFO, "RSB Communication deactivated.");
	}
}
