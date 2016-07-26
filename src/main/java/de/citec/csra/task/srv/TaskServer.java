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
	private final List<RSBTaskHandler> handlers;

	public TaskServer(RSBTaskHandler... handlers) throws InitializeException {
		this.handlers = Arrays.asList(handlers);
	}

	public void waitForShutdown() {
		while (true) {
			try {
				Thread.sleep(10);
			} catch (InterruptedException ex) {
//				Thread.currentThread().interrupt();
				LOG.log(Level.SEVERE, "Waiting interrupted", ex);
			}
		}
	}

	public void activate() throws RSBException, InterruptedException {
		for(RSBTaskHandler h : this.handlers){
			h.activate();
		}
		LOG.log(Level.INFO, "RSB Communication activated.");
	}

	public void deactivate() throws RSBException, InterruptedException {
		for(RSBTaskHandler h : this.handlers){
			h.deactivate();
		}
		LOG.log(Level.INFO, "RSB Communication deactivated.");
	}
}
