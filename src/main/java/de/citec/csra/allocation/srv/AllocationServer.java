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
package de.citec.csra.allocation.srv;

import static de.citec.csra.rst.util.StringRepresentation.shortString;
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

	public void listen() throws InterruptedException {
		LOG.log(Level.INFO, "Allocation service listening at ''{0}''.", this.listener.getScope());
		Allocations.getInstance();
		while (this.listener.isActive()) {
			ResourceAllocation incoming = this.queue.take();
			LOG.log(Level.FINE, "Received client update ''{0}''.", shortString(incoming));
			Allocations.getInstance().handle(incoming);
		}
	}

	public void activate() throws RSBException {
		if (!this.listener.isActive()) {
			this.listener.activate();
		}
	}

	public void deactivate() throws RSBException, InterruptedException {
		if (this.listener.isActive()) {
			this.listener.deactivate();
		}
		instance = null;
	}
}
