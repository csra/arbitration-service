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

import de.citec.csra.util.StringParser;
import java.util.logging.Level;
import java.util.logging.Logger;
import rsb.Event;
import rsb.InitializeException;
import rst.communicationpatterns.TaskStateType.TaskState;
import static rst.communicationpatterns.TaskStateType.TaskState.Origin.SUBMITTER;
import rst.communicationpatterns.TaskStateType.TaskState.State;
import static rst.communicationpatterns.TaskStateType.TaskState.State.ABORT;
import static rst.communicationpatterns.TaskStateType.TaskState.State.FAILED;
import static rst.communicationpatterns.TaskStateType.TaskState.State.INITIATED;
import static rst.communicationpatterns.TaskStateType.TaskState.State.REJECTED;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public abstract class AsyncTaskHandler<T, V> extends AbstractTaskHandler<T, V> {

	private final static Logger LOG = Logger.getLogger(AsyncTaskHandler.class.getName());


	public AsyncTaskHandler(String scope, Class<T> cls, Class<V> res) throws InitializeException {
		super(scope, cls, res);
	}

	public AsyncTaskHandler(String scope, Class<T> cls, Class<V> res, StringParser<T> p) throws InitializeException {
		super(scope, cls, res, p);
	}

	@Override
	public void internalNotify(Event event) {
		if (event.getData() instanceof TaskState) {
			TaskState t = (TaskState) event.getData();

//			only regard foreign task states that are initiated and not empty
			if (t.getOrigin().equals(SUBMITTER)) {
				T payload = deserialize(t.getPayload());
				LOG.log(Level.FINE, "Received task: state: ''{0}'', serial: ''{1}'', wire-schema: ''{2}'', payload: ''{3}''",
						new Object[]{
							t.getState(),
							t.getSerial(),
							t.getWireSchema().toStringUtf8(),
							payload.toString().replaceAll("\n", " ")
						});
				if (t.getState().equals(INITIATED)) {

					State init = initializeTask(t, payload);
					updateTask(t, init);
					if (init != REJECTED) {
						try {
							executeTask();
						} catch (Exception ex) {
							LOG.log(Level.WARNING, "Exception during task handling", ex);
							updateTask(t, FAILED);
						}
					}

				} else if (t.getState().equals(ABORT)) {
					State abort = abortTask(payload);
					updateTask(t, abort);
				}
			}
		} else {
			T input = null;
			if (getInClass().isInstance(event.getData())) {
				input = (T) event.getData();
				LOG.log(Level.FINE, "Received raw data instead of task: ''{0}''", input.toString().replaceAll("\n", " "));
			} else if (getParser() != null && event.getData() instanceof String) {
				try {
					input = getParser().getValue((String) event.getData());
					LOG.log(Level.FINE, "Received string representation instead of task: ''{0}''", input.toString().replaceAll("\n", " "));
				} catch (IllegalArgumentException ex) {
					LOG.log(Level.FINE, "String representation ''{0}'' could not be parsed: ''{1}''", new Object[]{((String) event.getData()).replaceAll("\n", " "), ex});
				}
			}

			if (input != null) {
				State init = initializeTask(null, input);
				if (init != REJECTED) {
					try {
						executeTask();
					} catch (Exception e) {
						e.printStackTrace();
						respond(e.getLocalizedMessage());
					}
				}
			} else {
				LOG.log(Level.FINE, "Received invalid input, ignoring: ''{0}''", event.getData().toString().replaceAll("\n", " "));
			}
		}
	}

	public abstract State initializeTask(TaskState task, T payload);
	public abstract void executeTask() throws Exception;

}
