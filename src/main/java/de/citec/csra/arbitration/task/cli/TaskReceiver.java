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
package de.citec.csra.arbitration.task.cli;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import rsb.Factory;
import rsb.InitializeException;
import rsb.Listener;
import rsb.ParticipantId;
import rsb.RSBException;
import rsb.Scope;
import rsb.converter.DefaultConverterRepository;
import rsb.converter.ProtocolBufferConverter;
import rsb.filter.OriginFilter;
import rsb.util.QueueAdapter;
import rst.communicationpatterns.TaskStateType.TaskState;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class TaskReceiver implements Runnable {

	static {
		DefaultConverterRepository.getDefaultConverterRepository().addConverter(new ProtocolBufferConverter<>(TaskState.getDefaultInstance()));
	}

	private final static Logger LOG = Logger.getLogger(TaskReceiver.class.getName());
	private final Listener listener;
	private BlockingQueue<TaskState> queue;
	private final Set<TaskListener> listeners = new HashSet<>();

	public TaskReceiver(Scope scope) throws InitializeException {
		this.listener = Factory.getInstance().createListener(scope);
	}

	public void activate() throws RSBException, InterruptedException {
		QueueAdapter<TaskState> qa = new QueueAdapter<>();
		this.queue = qa.getQueue();
		this.listener.addHandler(qa, true);
		this.listener.activate();
		LOG.log(Level.INFO, "Activated listener at ''{0}''", listener.getScope());
	}

	public void ignore(ParticipantId id) {
		this.listener.addFilter(new OriginFilter(id, true));
	}

	public void deactivate() throws InterruptedException, RSBException {
		if (this.listener.isActive()) {
			this.listener.deactivate();
			LOG.log(Level.INFO, "Deactivated listener at ''{0}''", listener.getScope());
		} else {
			LOG.log(Level.WARNING, "Listener at  ''{0}'' already inactive", listener.getScope());
		}
		this.listeners.clear();
	}

	public void addTaskListener(TaskListener l) {
		this.listeners.add(l);
	}

	public void removeTaskListener(TaskListener l) {
		this.listeners.remove(l);
	}

	@Override
	public void run() {
		try {
			while (this.listener.isActive()) {
				TaskState task = this.queue.take();
				switch (task.getState()) {
					case INITIATED:
//						skip initiated
						break;
					default:
						for (TaskListener ts : this.listeners) {
							ts.updated(task);
						}
						break;
				}
			}
		} catch (InterruptedException e) {
			LOG.log(Level.SEVERE, "Waiting for task update interrupted", e);
			Thread.currentThread().interrupt();
		}
	}
}
