/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package de.citec.csra.task.srv;

import de.citec.csra.util.StringParser;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import rsb.InitializeException;
import rst.communicationpatterns.TaskStateType.TaskState.State;
import static rst.communicationpatterns.TaskStateType.TaskState.State.ACCEPTED;
import static rst.communicationpatterns.TaskStateType.TaskState.State.REJECTED;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public abstract class MapTaskHandler<K, V, R> extends TaskHandler<String, R> {

	private Map<K, V> cfg = new HashMap<>();

	private final StringParser<K> keyp;
	private final StringParser<V> valuep;
	private final String ignore;
	private final String partSep;
	private final String keySep;
	private final String valSep;

	private final static Logger log = Logger.getLogger(MapTaskHandler.class.getName());

	public MapTaskHandler(String scope, String ignore, StringParser<K> k, StringParser<V> p, Class<R> r) throws InitializeException {
		this(scope, ignore, "%", ":", ";", k, p, r);
	}
	
	public MapTaskHandler(String scope, String ignore, String partSep, String keySep, String valSep, StringParser<K> k, StringParser<V> p, Class<R> ret) throws InitializeException {
		super(scope, String.class, ret);
		this.valuep = p;
		this.keyp = k;
		this.ignore = ignore;
		this.partSep = partSep;
		this.keySep = keySep;
		this.valSep = valSep;
	}

	@Override
	public State initializeTask(String payload) {
		try {
			Map<String, String> stringcfg = parsePayload(payload);
			this.cfg = parseConfiguration(stringcfg);
		} catch (Exception ex) {
			log.log(Level.WARNING, "configuration errorneous: '" + payload + "'.", ex);
			return REJECTED;
		}
		return ACCEPTED;
	}
	
	@Override
	public R handleTask(String payload) {
		return handleTask(this.cfg);
	}

	public abstract R handleTask(Map<K, V> cfg);

	private Map<String, String> parsePayload(String payload) {
		Map<String, String> stringcfg = new HashMap<>();
		String[] configs = payload.split(partSep);
		for (String config : configs) {
			String[] parts = config.split(keySep);
			if (parts.length != 2) {
				log.log(Level.WARNING, "invalid action specification: ''{0}'', ignoring.", config);
			} else {
				if (parts[0].length() < 1) {
					log.log(Level.WARNING, "invalid action specification: ''{0}'', ignoring.", parts[0]);
				} else {
					String[] locs = parts[1].split(valSep);
					if (locs.length < 1) {
						log.log(Level.WARNING, "value empty or invalid: ''{0}'', ignoring.", parts[1]);
					} else {
						for (String loc : locs) {
							if (loc.length() < 1) {
								log.log(Level.WARNING, "invalid value specification: ''{0}'', ignoring.", loc);
							} else {
								stringcfg.put(loc, parts[0]);
							}
						}
					}
				}
			}
		}
		return stringcfg;
	}

	private Map<K, V> parseConfiguration(Map<String, String> cfgs) throws Exception {
		Map<K, V> parsedcfg = new HashMap<>();
		for (Map.Entry<String, String> entry : cfgs.entrySet()) {
			K key = keyp.getValue(entry.getKey());
			V value = valuep.getValue(entry.getValue());
			parsedcfg.put(key, value);
		}
		return parsedcfg;
	}

	public boolean isIgnored(String label) {
		return Pattern.matches(ignore, label);
	}
}
