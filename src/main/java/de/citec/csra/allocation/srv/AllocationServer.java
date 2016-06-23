/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.citec.csra.allocation.srv;

import java.util.concurrent.BlockingQueue;
import java.util.logging.Level;
import java.util.logging.Logger;
import rsb.Factory;
import rsb.Listener;
import rsb.RSBException;
import rsb.converter.DefaultConverterRepository;
import rsb.converter.ProtocolBufferConverter;
import rsb.filter.OriginFilter;
import rsb.util.QueueAdapter;
import rst.communicationpatterns.ResourceAllocationType.ResourceAllocation;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class AllocationServer {

	static {
		DefaultConverterRepository.getDefaultConverterRepository()
				.addConverter(new ProtocolBufferConverter<>(ResourceAllocation.getDefaultInstance()));
	}

	private final static Logger LOG = Logger.getLogger(AllocationServer.class.getName());
	private final static String SCOPEVAR = "SCOPE_ALLOCATION";
	private final static String FALLBACK = "/coordination/allocation/";

	private static AllocationServer instance;
	private static String scope;
	private final Listener listener;
	private final BlockingQueue<ResourceAllocation> queue;

	private AllocationServer() throws InterruptedException, RSBException {

		QueueAdapter<ResourceAllocation> qa = new QueueAdapter<>();

		this.listener = Factory.getInstance().createListener(getScope());
		this.listener.addFilter(new OriginFilter(NotificationService.getInstance().getID(), true));
		this.listener.addHandler(qa, true);
		this.queue = qa.getQueue();
	}

	public static String getScope() {
		if (scope == null) {
			if (System.getenv().containsKey(SCOPEVAR)) {
				scope = System.getenv(SCOPEVAR);
			} else {
				LOG.log(Level.WARNING, "using fallback scope ''{0}'', consider exporting ${1}", new String[]{FALLBACK, SCOPEVAR});
				scope = FALLBACK;
			}
		}
		return scope;
	}

	public static AllocationServer getInstance() throws InterruptedException, RSBException {
		if (instance == null) {
			instance = new AllocationServer();
		}
		return instance;
	}

	public void waitForShutdown() throws InterruptedException {
		LOG.log(Level.INFO, "Allocation service listening at ''{0}''.", this.listener.getScope());
		while (this.listener.isActive()) {
			ResourceAllocation a = this.queue.take();
			switch (a.getState()) {
				case REQUESTED:
					Allocations.getInstance().request(a);
					break;
				case CANCELLED:
				case ABORTED:
				case RELEASED:
					Allocations.getInstance().finalize(a);
					break;
				case ALLOCATED:
				case REJECTED:
				case SCHEDULED:
					LOG.log(Level.WARNING, "''{0}'' illegal request ''{1}'', ignoring ({2})", new String[]{a.getId(), a.getState().name(), a.toString().replaceAll("\n", " ")});
					break;
			}

		}
	}

	public void activate() throws RSBException {
		this.listener.activate();
	}

	public void deactivate() throws RSBException, InterruptedException {
		this.listener.deactivate();
	}
}
