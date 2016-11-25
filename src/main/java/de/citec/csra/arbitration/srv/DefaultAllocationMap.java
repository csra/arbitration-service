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
package de.citec.csra.arbitration.srv;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 *
 * @author Patrick Holthaus
 * (<a href=mailto:patrick.holthaus@uni-bielefeld.de>patrick.holthaus@uni-bielefeld.de</a>)
 */
public class DefaultAllocationMap {

	private final Map<String, Set<String>> resources = new HashMap<>();

	private Properties resourceProperties;
	private Properties handlerProperties;
	private Properties scxmlProperties;
	private Properties durationProperties;

	private DefaultAllocationMap() {
	}
	private static DefaultAllocationMap instance;

	public static DefaultAllocationMap getInstance() {
		if (instance == null) {
			instance = new DefaultAllocationMap();
			instance.init();
		}
		return instance;
	}

	public Set<String> getResources(String key) {
		if (this.resources.containsKey(key)) {
			return new HashSet<>(this.resources.get(key));
		} else {
			return null;
		}
	}

	public String getHandler(String key) {
		return this.handlerProperties.getProperty(key);
	}
	
	public String getSCXML(String key) {
		return this.scxmlProperties.getProperty(key);
	}

	public Long getDuration(String key) {
		if (this.durationProperties.containsKey(key)) {
			return Long.valueOf(this.durationProperties.getProperty(key));
		} else {
			return null;
		}
	}

	void setResources(String key, Set<String> resources) {
		this.resources.put(key, resources);
		save();
	}

	void setHandler(String key, String handler) {
		this.handlerProperties.put(key, handler);
		save();
	}

	void setDuration(String key, Long duration) {
		this.durationProperties.put(key, duration.toString());
		save();
	}

	private void save() {

		try (FileOutputStream os = new FileOutputStream("src/main/resources/etc/coordination/resources.properties")) {
			
			for(Map.Entry<String, Set<String>> e : this.resources.entrySet()){
				StringBuilder b = new StringBuilder();
				for(String resource : e.getValue()){
					b.append(resource);
					b.append(":");
				}
				if(b.length() > 0){
					b.deleteCharAt(b.length() - 1);
				}
				this.resourceProperties.put(e.getKey(), b.toString());
			}
			resourceProperties.store(os, "auto-saved");
			os.close();
		} catch (IOException ex) {
			Logger.getLogger(DefaultAllocationMap.class.getName()).log(Level.SEVERE, null, ex);
		}
		try (FileOutputStream os = new FileOutputStream("src/main/resources/etc/coordination/handlers.properties")) {
			handlerProperties.store(os, "auto-saved");
			os.close();
		} catch (IOException ex) {
			Logger.getLogger(DefaultAllocationMap.class.getName()).log(Level.SEVERE, null, ex);
		}
		try (FileOutputStream os = new FileOutputStream("src/main/resources/etc/coordination/scxml.properties")) {
			handlerProperties.store(os, "auto-saved");
			os.close();
		} catch (IOException ex) {
			Logger.getLogger(DefaultAllocationMap.class.getName()).log(Level.SEVERE, null, ex);
		}
		try (FileOutputStream os = new FileOutputStream("src/main/resources/etc/coordination/durations.properties")) {
			durationProperties.store(os, "auto-saved");
			os.close();
		} catch (IOException ex) {
			Logger.getLogger(DefaultAllocationMap.class.getName()).log(Level.SEVERE, null, ex);
		}
	}

	private void init() {
		resourceProperties = new Properties();
		try (FileInputStream is = new FileInputStream("src/main/resources/etc/coordination/resources.properties")) {
			resourceProperties.load(is);
			for (String key : resourceProperties.stringPropertyNames()) {
				if (!resources.containsKey(key)) {
					resources.put(key, new HashSet<>());
				}
				Set<String> myResources = resources.get(key);
				String[] values = resourceProperties.getProperty(key).split(":");
				for (String value : values) {
					myResources.add(value);
				}
			}
		} catch (IOException ex) {
			Logger.getLogger(DefaultAllocationMap.class.getName()).log(Level.SEVERE, null, ex);
		}

		scxmlProperties = new Properties();
		try (FileInputStream is = new FileInputStream("src/main/resources/etc/coordination/scxml.properties")) {
			scxmlProperties.load(is);
		} catch (IOException ex) {
			Logger.getLogger(DefaultAllocationMap.class.getName()).log(Level.SEVERE, null, ex);
		}
		
		handlerProperties = new Properties();
		try (FileInputStream is = new FileInputStream("src/main/resources/etc/coordination/handlers.properties")) {
			handlerProperties.load(is);
		} catch (IOException ex) {
			Logger.getLogger(DefaultAllocationMap.class.getName()).log(Level.SEVERE, null, ex);
		}

		durationProperties = new Properties();
		try (FileInputStream is = new FileInputStream("src/main/resources/etc/coordination/durations.properties")) {
			durationProperties.load(is);
		} catch (IOException ex) {
			Logger.getLogger(DefaultAllocationMap.class.getName()).log(Level.SEVERE, null, ex);
		}
//		String pfx = System.getenv("prefix");
//FileInputStream is = new FileInputStream("etc/coordination/resources.rc");
//		try (InputStream is = getClass().getClassLoader().getResourceAsStream("etc/coordination/resources.rc")) {
//			BufferedReader br = new BufferedReader(new InputStreamReader(is));
//			String line;
//			while ((line = br.readLine()) != null) {
//				System.out.println("line: " + line);
//			}
//		} catch (IOException ex) {
//			Logger.getLogger(DefaultAllocationMap.class.getName()).log(Level.SEVERE, null, ex);
//		}
	}

}
