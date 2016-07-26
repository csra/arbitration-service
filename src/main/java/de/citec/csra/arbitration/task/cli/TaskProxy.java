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
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import rsb.Factory;
import rsb.Informer;
import rsb.InitializeException;
import rsb.Listener;
import rsb.RSBException;
import rsb.converter.DefaultConverterRepository;
import rsb.converter.ProtocolBufferConverter;
import rsb.filter.OriginFilter;
import rsb.util.QueueAdapter;
import rst.communicationpatterns.TaskStateType.TaskState;
import static rst.communicationpatterns.TaskStateType.TaskState.Origin.SUBMITTER;
import rst.communicationpatterns.TaskStateType.TaskState.State;
import static rst.communicationpatterns.TaskStateType.TaskState.State.ABORT;
import static rst.communicationpatterns.TaskStateType.TaskState.State.ABORTED;
import static rst.communicationpatterns.TaskStateType.TaskState.State.INITIATED;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class TaskProxy implements Runnable {

	static {
		DefaultConverterRepository.getDefaultConverterRepository().addConverter(new ProtocolBufferConverter<>(TaskState.getDefaultInstance()));
	}

	private final static Logger LOG = Logger.getLogger(TaskProxy.class.getName());
	private final Informer informer;
	private final Listener listener;
	private BlockingQueue<TaskState> queue;
	private TaskState task;
	private long wait = 7000;
	private boolean alive;
	private final Set<TaskListener> listeners = new HashSet<>();

	public TaskProxy(String scope, TaskState original, long wait) throws InitializeException {
		System.out.println("scope: " + scope);
		this.informer = Factory.getInstance().createInformer(scope);
		this.listener = Factory.getInstance().createListener(scope);
		this.alive = true;
		this.task = original;
		this.wait = wait;
	}

	public void activate() throws RSBException, InterruptedException {
		QueueAdapter<TaskState> qa = new QueueAdapter<>();
		this.queue = qa.getQueue();
		this.listener.addHandler(qa, true);
		this.listener.addFilter(new OriginFilter(informer.getId(), true));
//				this.listener.addFilter(new OriginFilter(ArbitrationServer.getInstance().getID(), true));
		this.informer.activate();
		this.listener.activate();
		LOG.log(Level.INFO, "Activated listener/informer pair at ''{0}''", listener.getScope());
	}

	public void deactivate() throws InterruptedException, RSBException {
		this.listener.deactivate();
		this.informer.deactivate();
		this.listeners.clear();
		LOG.log(Level.INFO, "Deactivated listener/informer pair at ''{0}''", listener.getScope());
	}

	public void addTaskListener(TaskListener l) {
		this.listeners.add(l);
	}
	
	public void removeTaskListener(TaskListener l){
		this.listeners.remove(l);
	}

	@Override
	public void run() {
		try {
			updateTask(INITIATED);
			while (!Thread.currentThread().isInterrupted() && alive) {
//				this.task = next(wait);
				for (TaskListener ts : this.listeners) {
					ts.updated(task);
				}
				if (this.task != null) {
					switch (this.task.getState()) {
						case ABORT:
						case ABORTED:
						case FAILED:
						case REJECTED:
						case COMPLETED:
							this.alive = false;
							deactivate();
						default:
							break;
					}
				}
			}
		} catch (RSBException | InterruptedException e) {
			e.printStackTrace();
		}
	}

	private void sendTask(TaskState t) {
		if (this.alive) {
			try {
				this.informer.publish(t);
			} catch (RSBException ex) {
				LOG.log(Level.SEVERE, "Exception during task update", ex);
			}
		} else{
			LOG.log(Level.WARNING, "Not alive anymore");
		}
	}

	private TaskState next(long timeout) throws InterruptedException {
		while (timeout > 0) {
			TaskState ts = this.queue.poll(timeout, TimeUnit.MILLISECONDS);
			return ts;
		}
		return null;
	}

	public void updateTask(State state) {

		int serial = 0;
		switch (state) {
			case ABORT:
				serial = 2;
				break;
			default:
			case INITIATED:
				serial = 0;
				break;
		}

		sendTask(TaskState.newBuilder(this.task).
				setOrigin(SUBMITTER).
				setState(state).
				setSerial(serial).
				build());
	}

	public void udpateTask(TaskState task) {
		sendTask(task);
	}
}
