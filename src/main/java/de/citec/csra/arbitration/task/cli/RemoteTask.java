/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.citec.csra.arbitration.task.cli;

import com.google.protobuf.ByteString;
import de.citec.csra.task.SerializationService;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Callable;
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
import rsb.util.QueueAdapter;
import rst.communicationpatterns.TaskStateType.TaskState;
import static rst.communicationpatterns.TaskStateType.TaskState.Origin.HANDLER;
import static rst.communicationpatterns.TaskStateType.TaskState.Origin.SUBMITTER;
import rst.communicationpatterns.TaskStateType.TaskState.State;
import static rst.communicationpatterns.TaskStateType.TaskState.State.ABORT;
import static rst.communicationpatterns.TaskStateType.TaskState.State.ABORTED;
import static rst.communicationpatterns.TaskStateType.TaskState.State.ACCEPTED;
import static rst.communicationpatterns.TaskStateType.TaskState.State.COMPLETED;
import static rst.communicationpatterns.TaskStateType.TaskState.State.INITIATED;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class RemoteTask<T, V> implements Callable<State> {

	static {
		DefaultConverterRepository.getDefaultConverterRepository().addConverter(new ProtocolBufferConverter<>(TaskState.getDefaultInstance()));
	}

	private static final ByteString UTF8 = ByteString.copyFromUtf8("utf-8-string");

	private final static Logger LOG = Logger.getLogger(RemoteTask.class.getName());
	private final Informer informer;
	private final Listener listener;
	private BlockingQueue<TaskState> queue;
	private final SerializationService<T> pack;
	private final SerializationService<V> unpack;
	private T payload;
	private long init = 2000;
	private long wait = 7000;
	private V result;

	public RemoteTask(String scope, Class<T> tsk, Class<V> res) throws InitializeException {
		this.informer = Factory.getInstance().createInformer(scope);
		this.listener = Factory.getInstance().createListener(scope);
		this.pack = new SerializationService<>(tsk);
		this.unpack = new SerializationService<>(res);
	}
	
	public void configure(T payload, long init, long wait){
		this.payload = payload;
		this.init = init;
		this.wait = wait;
	}

	private void activate() throws RSBException, InterruptedException {
		QueueAdapter<TaskState> qa = new QueueAdapter<>();
		this.queue = qa.getQueue();
		this.listener.addHandler(qa, true);
		this.informer.activate();
		this.listener.activate();
		LOG.log(Level.INFO, "Activated listener/informer pair at ''{0}''", listener.getScope());
	}

	private void deactivate() throws InterruptedException, RSBException {
		this.listener.deactivate();
		this.informer.deactivate();
	}

	@Override
	public State call() throws RSBException, InterruptedException {

		activate();
		State initResult = initializeTask(init);

		if (ACCEPTED.equals(initResult)) {
			boolean update = true;
			while (update) {
				State taskResult = waitTask(wait);
				if (taskResult != null) {
					switch (taskResult) {
						case RESULT_AVAILABLE:
						case UPDATE:
							update = true;
							break;
						default:
						case ABORTED:
						case FAILED:
						case ABORT_FAILED:
						case REJECTED:
						case COMPLETED:
							update = false;
							return taskResult;
					}
				}
			}
		}
		deactivate();
		return null;
	}

	public State initializeTask(long timeout) throws InterruptedException {
		updateTask(INITIATED);
		return nextState(timeout);
	}

	public State waitTask(long timeout) throws InterruptedException {
		return nextState(timeout);
	}

	public State abortTask(long timeout) throws InterruptedException {
		updateTask(ABORT);
		return nextState(timeout);
	}

	private void sendTask(TaskState t) {
		try {
			this.informer.send(t);
		} catch (RSBException ex) {
			LOG.log(Level.SEVERE, "Exception during task update", ex);
		}
	}

	private State nextState(long timeout) throws InterruptedException {
		long start = System.currentTimeMillis();
		long remaining = timeout;
		while (remaining > 0) {
			TaskState ts = this.queue.poll(remaining, TimeUnit.MILLISECONDS);
			if (ts.getOrigin().equals(HANDLER)) {
				remaining = System.currentTimeMillis() - start;
				this.result = this.unpack.deserialize(ts.getPayload());
				return ts.getState();
			}
		}
		return null;
	}

	private void updateTask(State state) {
		ByteString pl;
		ByteString sc;
		if (payload != null) {
			pl = pack.serialize(payload);
			sc = pack.getSchema();
		} else {
			pl = ByteString.EMPTY;
			sc = UTF8;
		}

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

		sendTask(TaskState.newBuilder().
				setOrigin(SUBMITTER).
				setState(state).
				setSerial(serial).
				setWireSchema(sc).
				setPayload(pl).
				build());
	}

	public V getResult() {
		return this.result;
	}

}
